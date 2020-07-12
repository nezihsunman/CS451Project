package negotiator.group3;

import geniusweb.actions.*;
import geniusweb.bidspace.AllBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.party.inform.*;
import geniusweb.profile.PartialOrdering;
import geniusweb.profile.utilityspace.LinearAdditive;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import negotiator.group3.impmap.ImpMap;
import negotiator.group3.impmap.OppImpMap;
import negotiator.group3.impmap.OppImpMapV2;
import negotiator.group3.linearorder.OppSimpleLinearOrdering;
import negotiator.group3.linearorder.SimpleLinearOrdering;
import tudelft.utilities.logging.Reporter;

import javax.websocket.DeploymentException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;

import static java.lang.Math.*;

public class Group3 extends DefaultParty {

    private final Random random = new Random();

    private Bid lastReceivedBid = null;
    private Bid prevReceivedBid = null;
    private Bid counterOffer = null;
    private Bid ourOffer = null;

    protected ProfileInterface profileint;
    private PartyId me;
    private Progress progress;
    double time = 0.0;


    private AllBidsList allbids; // all bids possible in domain.
    private List<Bid> orderedbids;
    private Bid elicitBid = null;
    private SimpleLinearOrdering ourEstimatedProfile = null;
    private OppSimpleLinearOrdering opponentEstimatedProfile = null;
    private ImpMap impMap = null;
    private OppImpMap oppImpMap = null;

    private OppImpMapV2 oppImpMapV2 = null;

    private double acceptanceLowerBound = 1;
    private BigInteger allBidSize = new BigInteger("0");
    // Initially equals to 0
    private BigDecimal lostElicitScore = new BigDecimal("0.00");

    // If no reservation ratio is assigned by the system then it is equal to 0 by default
    private double reservationImportanceRatio = 0.0;

    //Set default as 0.1
    private BigDecimal elicitationCost = new BigDecimal("0.1");
    private boolean doWeElicitate = false;

    private HashMap<Bid, String> offerRed = new HashMap<Bid, String>();

    public Group3() {
    }

    public Group3(Reporter reporter) {
        super(reporter); // for debugging
    }

    @Override
    public Capabilities getCapabilities() {
        return new Capabilities(new HashSet<>(Arrays.asList("SHAOP", "SAOP")));
    }

    @Override
    public String getDescription() {
        return "AhNeCe Agent";
    }

    @Override
    public void notifyChange(Inform info) {
        try {
            if (info instanceof Settings) {
                Settings settings = (Settings) info;
                init(settings);
            } else if (info instanceof ActionDone) {
                Action lastReceivedAction = ((ActionDone) info).getAction();
                if (lastReceivedAction instanceof Offer) {
                    lastReceivedBid = ((Offer) lastReceivedAction).getBid();
                } else if (lastReceivedAction instanceof Comparison) {
                    ourEstimatedProfile = ourEstimatedProfile.with(((Comparison) lastReceivedAction).getBid(), ((Comparison) lastReceivedAction).getWorse());
                    myTurn();
                }
            } else if (info instanceof YourTurn) {
                if (progress instanceof ProgressRounds) {
                    progress = ((ProgressRounds) progress).advance();
                }
                myTurn();

            } else if (info instanceof Finished) {
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle info", e);
        }
    }

    private void init(Settings settings) throws IOException, DeploymentException {
        this.me = settings.getID();
        this.progress = settings.getProgress();
        this.profileint = ProfileConnectionFactory.create(settings.getProfile().getURI(), getReporter());

        if (profileint.getProfile() instanceof LinearAdditive) {
            throw new UnsupportedOperationException(
                    "Only DefaultPartialOrdering supported");
        } else if (profileint.getProfile() instanceof PartialOrdering) {

            PartialOrdering partialprofile = (PartialOrdering) profileint.getProfile();

            this.allbids = new AllBidsList(partialprofile.getDomain());
            this.allBidSize = allbids.size();
            this.impMap = new ImpMap(partialprofile);
            this.opponentEstimatedProfile = new OppSimpleLinearOrdering();
            this.oppImpMap = new OppImpMap(partialprofile);

            this.oppImpMapV2 = new OppImpMapV2(partialprofile);

            this.ourEstimatedProfile = new SimpleLinearOrdering(profileint.getProfile());
            orderedbids = ourEstimatedProfile.getBids();
            elicitBid = impMap.leastKnownBidGenerator(allbids);
            getReservationRatio();
            this.impMap.update(ourEstimatedProfile);

        } else {
            throw new UnsupportedOperationException("Only DefaultPartialOrdering supported");
        }

    }

    private void myTurn() throws IOException {
        time = progress.get(System.currentTimeMillis());
        prevReceivedBid = counterOffer;
        counterOffer = lastReceivedBid;
        Action action = null;
        if (elicitBid != null && counterOffer != null && prevReceivedBid != null && doWeElicitateCheck()) {
            doWeElicitate = true;
            lostElicitScore.add(elicitationCost);
        }
        if (elicitBid != null && counterOffer != null && this.doWeElicitate) {

            action = new ElicitComparison(me, counterOffer, ourEstimatedProfile.getBids());
            elicitBid = null;
            this.doWeElicitate = false;
        } else {
            if (counterOffer != null) {
                strategySelection();
                //if not Accepted return null
                action = doWeEndTheNegotiation();

                if (action == null) {
                    //if not Accepted return null
                    action = doWeAccept();
                }
            }
        }
        if (action == null) {
            action = makeAnOffer(time);
        }
        getConnection().send(action);
    }

    private Action doWeEndTheNegotiation() {
        if (reservationImportanceRatio > 0.85) {
            return new EndNegotiation(me);
        }
        return null;
    }

    private Bid randomBidGenerator() {
        Random rand = new Random();
        return allbids.get(rand.nextInt(allbids.size().intValue()));
    }

    private Action makeAnOffer(double time) throws IOException {
        Bid maxBid = ourEstimatedProfile.getBids().get(ourEstimatedProfile.getBids().size() - 1);

        ourOffer = null;

        BigDecimal bidImportanceLowerBound = BigDecimal.valueOf(0.8);
        BigDecimal bidImportanceHigherBound = BigDecimal.valueOf(1);
        BigDecimal time1 = BigDecimal.valueOf(time);
        BigDecimal time2 = time1.multiply(time1);
        BigDecimal time3 = time2.multiply(time1);
        if (time <= 0.01) {
            bidImportanceHigherBound = BigDecimal.valueOf(0.91);
            bidImportanceLowerBound = BigDecimal.valueOf(0.87);
            getReporter().log(Level.INFO, "---" + me + "Thresh hold first offer:" + bidImportanceLowerBound + "high" + bidImportanceHigherBound);
        } else if (time <= 0.2) {
            bidImportanceHigherBound = time2.multiply(BigDecimal.valueOf(-8.8571)).add(time1.multiply(BigDecimal.valueOf(1.871))).add(BigDecimal.valueOf(0.9017));

            bidImportanceLowerBound = bidImportanceHigherBound.subtract(BigDecimal.valueOf(0.05));
        } else if (time <= 0.4) {
            BigDecimal function = time2.multiply(BigDecimal.valueOf(-0.0107)).add(time1.multiply(BigDecimal.valueOf(0.0733)));

            bidImportanceHigherBound = function.add(BigDecimal.valueOf(0.868));

            bidImportanceLowerBound = function.add(BigDecimal.valueOf(0.83));

        } else if (time <= 0.6) {
            BigDecimal function1 = time2.multiply(BigDecimal.valueOf(0.315)).add(time1.multiply(BigDecimal.valueOf(-0.7767)));
            BigDecimal function = time3.multiply(BigDecimal.valueOf(-0.0383)).add(function1);
            bidImportanceHigherBound = function.add(BigDecimal.valueOf(1.5));

            bidImportanceLowerBound = function.add(BigDecimal.valueOf(1.2));

        } else if (time <= 0.8) {
            BigDecimal function = (time3.multiply(BigDecimal.valueOf(-0.0383))).add(time2.multiply(BigDecimal.valueOf(0.315))).add(time1.multiply(BigDecimal.valueOf(-0.7767)));
            bidImportanceHigherBound = function.add(BigDecimal.valueOf(1.32));
            bidImportanceLowerBound = function.add(BigDecimal.valueOf(1.29));

        } else if (time <= 1) {
            BigDecimal function = (time3.multiply(BigDecimal.valueOf(0.0033))).add(time2.multiply(BigDecimal.valueOf(-0.0357))).add(time1.multiply(BigDecimal.valueOf(0.111)));
            bidImportanceHigherBound = function.add(BigDecimal.valueOf(0.842));

            bidImportanceLowerBound = function.add(BigDecimal.valueOf(0.79));

        }
        getReporter().log(Level.INFO, "---" + me + "BidImportanceHigherBound : " + bidImportanceHigherBound + "Lover Bound " + bidImportanceLowerBound);
        while (true) {
            for (int i = 0; i < allBidSize.intValue(); i++) {
                Bid testBid = randomBidGenerator();
                double impMapImportanceForMe = impMap.getImportance(testBid);
                double similarityRatio = checkSimilarity(testBid);
                if ((time >= 0.9 || !maxBid.equals(testBid)) && impMapImportanceForMe >= bidImportanceLowerBound.doubleValue() && impMapImportanceForMe <= bidImportanceHigherBound.doubleValue() && (similarityRatio >= 0.48 || impMapImportanceForMe >= 0.94)) {
                    ourOffer = testBid;
                    if (oppImpMap.getImportance(testBid) < 0.55) {
                        break;
                    }
                }
            }
            if (bidImportanceLowerBound.doubleValue() >= 0.89) {
                bidImportanceLowerBound = bidImportanceLowerBound.subtract(BigDecimal.valueOf(0.04));
            }
            if (ourOffer != null) break;

        }
        offerRed.put(ourOffer, "offered");
        getReporter().log(Level.INFO, "Time: " + time);
        getReporter().log(Level.INFO, "---" + me + " Similarity Case New Offer and OppImpMapV1: " + oppImpMap.getImportance(ourOffer) + "OppImpMapV2: " + oppImpMapV2.getImportance(ourOffer) + "ImpMap: " + impMap.getImportance(ourOffer));
        getReporter().log(Level.INFO, "Offered Bid Size" + offerRed.size());
        return new Offer(me, ourOffer);
    }

    private double checkSimilarity(Bid bid) {
        List<Bid> sortedBids = ourEstimatedProfile.getBids();
        List<Double> similarityList = new ArrayList<>();
        double numberOfStep = 3;
        for (int i = 1; i <= numberOfStep; i++) {
            double numberOfIssue = 0;
            double similarity = 0;
            Bid compare = sortedBids.get(sortedBids.size() - i);
            for (String issue : bid.getIssues()) {
                numberOfIssue++;
                if (compare.getValue(issue).equals(bid.getValue(issue))) {
                    similarity++;
                }
            }
            similarity /= numberOfIssue;
            similarityList.add(similarity);
        }
        double totalAverage = 0;
        for (int i = 0; i < similarityList.size(); i++) {
            double average = similarityList.get(i);
            totalAverage += average;
        }
        totalAverage /= similarityList.size();
        return totalAverage;
    }

    private int checkSimilarityForAcceptanceAccordingToMaxBid(Bid maxBid) {

        Set<Map.Entry<String, Double>> sortedIssueMapSet = impMap.getSortedIssueImpMap().entrySet();
        ArrayList<Map.Entry<String, Double>> sortedIssueArrList = new ArrayList<Map.Entry<String, Double>>(sortedIssueMapSet);

        String bestImportantIssue = sortedIssueArrList.get(sortedIssueArrList.size() - 1).getKey();
        String worstIssue = sortedIssueArrList.get(0).getKey();
        String secondWorstIssue = sortedIssueArrList.get(1).getKey();
        int numberOfIssue = 0;
        int similarity = 0;
        for (String issue : lastReceivedBid.getIssues()) {
            numberOfIssue++;
            if (lastReceivedBid.getValue(issue).equals(maxBid.getValue(issue))) {
                similarity++;
            }
        }
        if (numberOfIssue > 4 && (numberOfIssue - similarity) == 2) {
            if (!lastReceivedBid.getValue(worstIssue).equals(maxBid.getValue(worstIssue)) &&
                    !lastReceivedBid.getValue(secondWorstIssue).equals(maxBid.getValue(secondWorstIssue))) {
                return 1;
            }
        }
        if (!lastReceivedBid.getValue(bestImportantIssue).equals(maxBid.getValue(bestImportantIssue))) {
            similarity = 0;
        }

        return (numberOfIssue - similarity);
    }

    private Action doWeAccept() {
        Bid maxBid = ourEstimatedProfile.getBids().get(ourEstimatedProfile.getBids().size() - 1);

        if (lastReceivedBid.equals(maxBid)) {
            getReporter().log(Level.INFO, "---" + me + " I accept first bid offer");
            return new Accept(me, lastReceivedBid);
        } else if (ourEstimatedProfile.getBids().size() > 6 && lastReceivedBid.equals(ourEstimatedProfile.getBids().get(ourEstimatedProfile.getBids().size() - 2))) {
            getReporter().log(Level.INFO, "---" + me + " I accept second bid offer");
            return new Accept(me, lastReceivedBid);
        } else if (oppImpMap.getImportance(lastReceivedBid) < 0.70 && checkSimilarityForAcceptanceAccordingToMaxBid(maxBid) == 1) {
            getReporter().log(Level.INFO, "---" + me + " I accept first bid offer due to checkSimilarityForAcceptanceAccordingToMaxBid similarity" + oppImpMap.getImportance(lastReceivedBid));
            return new Accept(me, lastReceivedBid);
        }
        if (this.impMap.getImportance(lastReceivedBid) > acceptanceLowerBound && checkSimilarity(lastReceivedBid) >= 0.50
                && oppImpMap.getImportance(lastReceivedBid) < (impMap.getImportance(lastReceivedBid) - 0.15)) {
            getReporter().log(Level.INFO, "---" + me + " I accept the offer" + "similarity: " + checkSimilarity(lastReceivedBid));
            return new Accept(me, lastReceivedBid);
        }
        return null;
    }


    private void strategySelection() throws IOException {

        this.acceptanceLowerBound = (-0.1541 * time * time) - (0.091 * time) + 0.9995 + lostElicitScore.doubleValue();
        this.opponentEstimatedProfile.updateBid(counterOffer);
        this.oppImpMap.update(opponentEstimatedProfile);

        this.oppImpMapV2.update(opponentEstimatedProfile);

        if (elicitBid == null) {
            this.impMap.update(ourEstimatedProfile);
            getReservationRatio();
            elicitBid = this.impMap.leastKnownBidGenerator(allbids);
        }
        getReporter().log(Level.INFO, "----> Bid importance for opponent V1 :" + oppImpMap.getImportance(counterOffer) + "Bid imp for V2: " + oppImpMapV2.getImportance(counterOffer));
        getReporter().log(Level.INFO, "----> Bid importance for me :" + impMap.getImportance(counterOffer));
    }

    private boolean doWeElicitateCheck() throws IOException {

        if (!prevReceivedBid.equals(counterOffer)) return false;
        if (lostElicitScore.doubleValue() + elicitationCost.doubleValue() > 0.075) return false;
        int issueCount = 0;
        int similarIssueCount = 0;
        for (String issue : this.profileint.getProfile().getDomain().getIssues()) {
            issueCount++;
            if (counterOffer.getValue(issue).equals(elicitBid.getValue(issue))) {
                similarIssueCount++;
            }
        }
        double similarityRatio = (double) similarIssueCount / issueCount;
        if (similarityRatio > 0.6) return true;

        return false;
    }

    private void getReservationRatio() throws IOException {
        try {
            Bid resBid = this.profileint.getProfile().getReservationBid();
            if (resBid != null) {
                this.reservationImportanceRatio = this.impMap.getImportance(resBid);
            } else
                this.reservationImportanceRatio = 0;
        } catch (Exception e) {
            this.reservationImportanceRatio = 0;
        }
    }
}
