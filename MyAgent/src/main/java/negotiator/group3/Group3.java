package negotiator.group3;

import geniusweb.actions.*;
import geniusweb.bidspace.AllBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.party.inform.*;
import geniusweb.profile.FullOrdering;
import geniusweb.profile.PartialOrdering;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import negotiator.group3.impmap.SimilarityMap;
import negotiator.group3.impmap.OppSimilarityMap;
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

    private int numFirstBids;
    private int numLastBids;
    private int oppNumFirstBids;

    protected ProfileInterface profileInterface;
    private PartyId partyId;
    private Progress progress;
    double time = 0.0;

    private AllBidsList allPossibleBids;
    private BigInteger allPossibleBidsSize;
    private SimpleLinearOrdering ourLinearPartialOrdering = null;
    private OppSimpleLinearOrdering oppLinearPartialOrdering = null;
    private SimilarityMap ourSimilarityMap = null;
    private OppSimilarityMap oppSimilarityMap = null;

    private Bid lastReceivedBid = null;
    private double utilityLowerBound = 0.9; // TODO Check Acceptance Lower Bound Graph

    // Initially no loss
    private BigDecimal lostElicitScore = new BigDecimal("0.00");

    // If no reservation ratio is assigned by the system then it is equal to 0 by default
    private double reservationUtility = 0.0;
    private Bid reservationBid = null;

    //Set default as 0.01 by Genius Framework
    private BigDecimal elicitationCost = new BigDecimal("0.01");

    /*DEBUG*/
    private HashMap<Bid, String> offeredOffers = new HashMap<>(); //TODO REMOVE AFTER TESTS

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
        return "AhBuNe Agent";
    } //TODO Change in POM

    @Override
    public void notifyChange(Inform info) {
        try {
            if (info instanceof Settings) {
                Settings settings = (Settings) info;
                init(settings);
                getReporter().log(Level.INFO, "<AhBuNe>: " + "init");
            } else if (info instanceof ActionDone) {
                Action lastReceivedAction = ((ActionDone) info).getAction();
                if (lastReceivedAction instanceof Offer) {
                    getReporter().log(Level.INFO, "<AhBuNe>: " + " Offer Came:" + ((Offer) lastReceivedAction).getBid()); //TODO REMOVE
                    lastReceivedBid = ((Offer) lastReceivedAction).getBid();
                } else if (lastReceivedAction instanceof Comparison) {
                    ourLinearPartialOrdering = ourLinearPartialOrdering.with(((Comparison) lastReceivedAction).getBid(), ((Comparison) lastReceivedAction).getWorse());
                    myTurn();
                    getReporter().log(Level.INFO, "<AhBuNe>: " + "lastReceivedAction");
                }
            } else if (info instanceof YourTurn) {
                if (progress instanceof ProgressRounds) {
                    progress = ((ProgressRounds) progress).advance();
                }
                getReporter().log(Level.INFO, "<AhBuNe>: " + progress);
                myTurn();
            } else if (info instanceof Finished) {
                getReporter().log(Level.INFO, "<AhBuNe>: " + " Offered Offers: " + offeredOffers); //TODO REMOVE
            }
        } catch (Exception exception) {
            throw new RuntimeException("Failed to handle info", exception);
        }
    }

    private void init(Settings settings) throws IOException, DeploymentException {
        this.partyId = settings.getID();
        this.progress = settings.getProgress();
        this.profileInterface = ProfileConnectionFactory.create(settings.getProfile().getURI(), getReporter());

        if (profileInterface.getProfile() instanceof FullOrdering) {
            throw new UnsupportedOperationException("Only <DefaultPartialOrdering> is supported");
        } else if (profileInterface.getProfile() instanceof PartialOrdering) {
            PartialOrdering partialProfile = (PartialOrdering) profileInterface.getProfile();
            this.allPossibleBids = new AllBidsList(partialProfile.getDomain());
            this.allPossibleBidsSize = allPossibleBids.size();
            this.ourSimilarityMap = new SimilarityMap(partialProfile, getReporter()); //TODO REMOVE REPORTER
            this.oppSimilarityMap = new OppSimilarityMap(partialProfile, getReporter()); //TODO REMOVE REPORTER
            this.ourLinearPartialOrdering = new SimpleLinearOrdering(profileInterface.getProfile());
            this.oppLinearPartialOrdering = new OppSimpleLinearOrdering();
            this.ourSimilarityMap.update(ourLinearPartialOrdering);
            getReservationRatio();

            getReporter().log(Level.INFO, "<AhBuNe>: Init finished");

        } else {
            throw new UnsupportedOperationException("Only <DefaultPartialOrdering> is supported");
        }

    }

    private Action selectAction() throws IOException {
        if (this.lastReceivedBid == null) {
            getReporter().log(Level.INFO, "<AhBuNe>: Selecting first offer");
            return makeAnOffer();
        }
        //getReporter().log(Level.INFO, "<AhBuNe>: Entering Strategy selection");
        //getReporter().log(Level.INFO, "<AhBuNe>: Strategy selected");
        //TODO: ELICITATION
        if (doWeElicitate()) {
            lostElicitScore.add(elicitationCost);
            return new ElicitComparison(partyId, lastReceivedBid, ourLinearPartialOrdering.getBids());
        }
        //if not Accepted return null
        if (doWeEndTheNegotiation()) {
            return new EndNegotiation(partyId);
        } else if (doWeAccept(lastReceivedBid)) {
            return new Accept(partyId, lastReceivedBid);
        }
        return makeAnOffer();

    }

    private void myTurn() throws IOException {
        time = progress.get(System.currentTimeMillis());
        strategySelection();
        Action action = this.selectAction();
        getReporter().log(Level.INFO, "<AhBuNe>: Action: " + action);
        getConnection().send(action);
    }

    private boolean doWeEndTheNegotiation() {
        if (reservationUtility > 0.85) {
            /* getReporter().log(Level.INFO, "---" + me + " Negotiation is ended with the method doWeNeedNegotiation");*/
            return true;
        }
        return false;
    }


    private Bid randomBidGenerator() {
        return allPossibleBids.get(random.nextInt(allPossibleBids.size().intValue()));
    }

    private Action makeAnOffer() throws IOException {
        //reporter.log(Level.INFO, "<AhBuNe>: numLastBids: " + this.numLastBids);
        Bid oppMaxbid = null;
        if(oppLinearPartialOrdering.getBids().size() != 0){
         oppMaxbid = oppLinearPartialOrdering.getBids().get(oppLinearPartialOrdering.getBids().size() - 1);
        }
        //TODO CHECK IF OPP > ME
        Bid ourOffer = ourSimilarityMap.findBidCompatibleWithSimilarity(this.numFirstBids, this.numLastBids, this.utilityLowerBound, oppMaxbid);
        offeredOffers.put(ourOffer, "Fantasy"); // TODO RM
        reporter.log(Level.INFO, "partyId: " + partyId);
        return new Offer(partyId, ourOffer);
    }



    private boolean doWeAccept(Bid bid) {
       /* double oppUtilityValue = estimatedUtilityValue(lastReceivedBid, true);
        double ourUtilityValue = estimatedUtilityValue(lastReceivedBid, false);
        if (ourUtilityValue > utilityLowerBound && oppUtilityValue < ourUtilityValue) {
            return new Accept(partyId, lastReceivedBid);
        }*/
       getReporter().log(Level.INFO, "TIME: " + this.time);
        for(double utilityTest = utilityLowerBound; utilityTest <= 0.95; utilityTest += 0.05){
            if(oppSimilarityMap.isCompromised(bid, this.oppNumFirstBids, utilityTest)){
                //getReporter().log(Level.INFO, "HEYO offer has OPP utility: " + utilityTest);
                if (this.ourSimilarityMap.isCompatibleWithSimilarity(bid, numFirstBids, numLastBids, utilityTest, "ACCEPT")){
                    //getReporter().log(Level.INFO, "HEYO I accept the offer for, MY utility: " + utilityTest);
                    return true;
                }
                break;
            }
        }
        return false;
    }

    private boolean doWeElicitate() {
        return false;
    }


    private void strategySelection() {
        this.utilityLowerBound = getUtilityLowerBound(this.time, lostElicitScore.doubleValue()); int knownBidNum = this.ourLinearPartialOrdering.getBids().size();
        int oppKnownBidNum = this.oppLinearPartialOrdering.getBids().size();
        this.numFirstBids = getNumFirst(this.utilityLowerBound, knownBidNum);
        this.numLastBids = getNumLast(this.utilityLowerBound, getUtilityLowerBound(1.0, lostElicitScore.doubleValue()), knownBidNum);
        if(lastReceivedBid != null){
            this.oppNumFirstBids = getOppNumFirst(this.utilityLowerBound, oppKnownBidNum);
            this.oppLinearPartialOrdering.updateBid(lastReceivedBid);
            this.oppSimilarityMap.update(oppLinearPartialOrdering);
        }
    }

    public double estimatedUtilityValue(Bid bid, boolean isOpponent) {
        int knownBidNum = this.ourLinearPartialOrdering.getBids().size();
        int oppKnownBidNum = this.oppLinearPartialOrdering.getBids().size();
        // TODO 5 Check
        double estimatedUtilityValue = 0.0;
        for (int i = 50; i <= 100; i += 5) {
            double utilityTest = (double) i / 100;
            int numFirstBids = getNumFirst(utilityTest, knownBidNum);
            int numLastBids = getNumLast(utilityTest, getUtilityLowerBound(1.0, this.lostElicitScore.doubleValue()), knownBidNum);
            int oppNumFirstBids = getOppNumFirst(utilityTest, oppKnownBidNum);
            if (isOpponent) {
                if (!this.oppSimilarityMap.isCompromised(bid, oppNumFirstBids, utilityTest)) {
                    estimatedUtilityValue = utilityTest;
                }
            } else {
                if (this.ourSimilarityMap.isCompatibleWithSimilarity(bid, numFirstBids, numLastBids, utilityTest, "ACCEPT")) {//TODO RM callType
                    estimatedUtilityValue = utilityTest;
                }
            }
        }

        //if(isOpponent) getReporter().log(Level.INFO, "<AhBuNe>: OPP ESTIMATE: " + estimatedUtilityValue);
        //else getReporter().log(Level.INFO, "<AhBuNe>: OUR ESTIMATE: " + estimatedUtilityValue);



        return estimatedUtilityValue;
    }

    private void getReservationRatio() throws IOException {
        try {
            this.reservationBid = this.profileInterface.getProfile().getReservationBid();
            this.reservationUtility = estimatedUtilityValue(reservationBid, false);
        } catch (Exception e) {
            this.reservationUtility = 0;
        }
    }

    double getUtilityLowerBound(double time, double lostElicitScore) {
        return ((-pow(time / 2, 2) + 0.95) + lostElicitScore);
    }

    int getNumFirst(double utilityLowerBound, int knownBidNum) {
        return ((int) (knownBidNum * (1 - utilityLowerBound)) + 1);
    }

    int getNumLast(double utilityLowerBound, double minUtilityLowerBound, int knownBidNum) {
        return ((int) (knownBidNum * (1 - minUtilityLowerBound)) - (int) (knownBidNum * (1 - utilityLowerBound)) + 1);
    }

    int getOppNumFirst(double utilityLowerBound, int oppKnownBidNum) {
        return ((int) (oppKnownBidNum * (1 - utilityLowerBound)) + 1);
    }
}
