package negotiator.ahbuneagent;

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
import negotiator.ahbuneagent.impmap.SimilarityMap;
import negotiator.ahbuneagent.impmap.OppSimilarityMap;
import negotiator.ahbuneagent.linearorder.OppSimpleLinearOrdering;
import negotiator.ahbuneagent.linearorder.SimpleLinearOrdering;
import tudelft.utilities.logging.Reporter;

import javax.websocket.DeploymentException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;

import static java.lang.Math.*;

public class AhBuNeAgent extends DefaultParty {

    private final Random random = new Random();

    private int ourNumFirstBids;
    private int ourNumLastBids;
    private int oppNumFirstBids;
    private int ourKnownBidNum = 0;
    private int oppKnownBidNum = 0;


    protected ProfileInterface profileInterface;
    private PartyId partyId;
    private Progress progress;
    private double time = 0.0;

    private AllBidsList allPossibleBids;
    private BigInteger allPossibleBidsSize;
    private SimpleLinearOrdering ourLinearPartialOrdering = null;
    private OppSimpleLinearOrdering oppLinearPartialOrdering = null;
    private SimilarityMap ourSimilarityMap = null;
    private OppSimilarityMap oppSimilarityMap = null;

    private Bid lastReceivedBid = null;
    private double utilityLowerBound = 0.9; // TODO Check Acceptance Lower Bound Graph
    private final double ourMaxCompromise = 0.1;

    // Initially no loss
    private double lostElicitScore = 0.00;
    //Set default as 0.01 by Genius Framework
    private double elicitationCost = 0.01;
    private final BigDecimal maxElicitationLost = new BigDecimal("0.05");
    private int leftElicitationNumber = 0;
    Bid elicitationBid = null;
    ArrayList<Map.Entry<Bid, Integer>> mostCompromisedBids = new ArrayList<>();
    ArrayList<Bid> oppElicitatedBid = new ArrayList<>();
    // If no reservation ratio is assigned by the system then it is equal to 0 by default
    private Bid reservationBid = null;

    /*DEBUG*/
    private HashMap<Bid, String> offeredOffers = new HashMap<>(); //TODO REMOVE AFTER TESTS

    public AhBuNeAgent() {
    }

    public AhBuNeAgent(Reporter reporter) {
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
                //getReporter().log(Level.INFO, "<AhBuNe>: " + "init");
            } else if (info instanceof ActionDone) {
                Action lastReceivedAction = ((ActionDone) info).getAction();
                if (lastReceivedAction instanceof Offer) {
                    getReporter().log(Level.INFO, "<AhBuNe>: " + " Offer Came:" + ((Offer) lastReceivedAction).getBid()); //TODO REMOVE
                    lastReceivedBid = ((Offer) lastReceivedAction).getBid();
                } else if (lastReceivedAction instanceof Comparison) {
                    ourLinearPartialOrdering = ourLinearPartialOrdering.with(((Comparison) lastReceivedAction).getBid(), ((Comparison) lastReceivedAction).getWorse());
                    myTurn();
                    getReporter().log(Level.INFO, "<AhBuNe>: " + "KNOWN BIDS: " + ourLinearPartialOrdering.getBids().size());
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
            this.ourSimilarityMap = new SimilarityMap(partialProfile); //TODO REMOVE REPORTER
            this.oppSimilarityMap = new OppSimilarityMap(partialProfile); //TODO REMOVE REPORTER
            this.ourLinearPartialOrdering = new SimpleLinearOrdering(profileInterface.getProfile());
            this.oppLinearPartialOrdering = new OppSimpleLinearOrdering();
            this.ourSimilarityMap.update(ourLinearPartialOrdering);
            getReservationRatio();
            getElicitationCost(settings);
        } else {
            throw new UnsupportedOperationException("Only <DefaultPartialOrdering> is supported");
        }

    }

    private Action selectAction() {
        //getReporter().log(Level.INFO, "<AhBuNe>: Entering Strategy selection");
        //getReporter().log(Level.INFO, "<AhBuNe>: Strategy selected");
        if (doWeMakeElicitation()) {
            lostElicitScore += elicitationCost;
            leftElicitationNumber -= 1;
            return new ElicitComparison(partyId, elicitationBid, ourLinearPartialOrdering.getBids());
        }

        if (lastReceivedBid == null) {
            getReporter().log(Level.INFO, "<AhBuNe>: Selecting first offer");
            return makeAnOffer();
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
        Action action = selectAction();
        getReporter().log(Level.INFO, "<AhBuNe>: Action Selected: " + action);
        getConnection().send(action);
    }

    private boolean doWeEndTheNegotiation() {
        //TODO check
        if (reservationBid != null && ourSimilarityMap.isCompatibleWithSimilarity(reservationBid, ourNumFirstBids, ourNumLastBids, 0.9 - time * 0.1)) {
            /* getReporter().log(Level.INFO, "---" + me + " Negotiation is ended with the method doWeNeedNegotiation");*/
            return true;
        }
        return false;
    }

    //TODO RM
    private Bid randomBidGenerator() {
        return allPossibleBids.get(random.nextInt(allPossibleBids.size().intValue()));
    }

    //TODO elicitate opp max bid
    private Bid elicitationRandomBidGenerator() {
        Bid foundBid = allPossibleBids.get(random.nextInt(allPossibleBids.size().intValue()));
        while (ourLinearPartialOrdering.getBids().contains(foundBid)) {
            foundBid = allPossibleBids.get(random.nextInt(allPossibleBids.size().intValue()));
        }
        return foundBid;
    }

    private Action makeAnOffer() {
        getReporter().log(Level.INFO, "<AhBuNe>: MakeAnOffer()");
        //reporter.log(Level.INFO, "<AhBuNe>: numLastBids: " + this.numLastBids);
        if(time > 0.96){
            for(int i = ourLinearPartialOrdering.getKnownBidsSize()-1; i >= 0; i--){
                Bid testBid = ourLinearPartialOrdering.getBidByIndex(i);
                if(oppElicitatedBid.contains(testBid) && doWeAccept(testBid)){
                    reporter.log(Level.INFO, "IMKANSIZI BASARDIK");
                    return new Offer(partyId, testBid);
                }
            }
        }
        Bid oppMaxBid = oppLinearPartialOrdering.getMaxBid();
        Bid ourOffer = ourSimilarityMap.findBidCompatibleWithSimilarity(ourNumFirstBids, ourNumLastBids, utilityLowerBound, oppMaxBid);
        if(lastReceivedBid != null){
            if(ourSimilarityMap.isCompatibleWithSimilarity(lastReceivedBid, ourNumFirstBids, ourNumLastBids, 0.9)){
                return new Offer(partyId, lastReceivedBid);
            }
            if(ourSimilarityMap.isCompatibleWithSimilarity(oppMaxBid, ourNumFirstBids, ourNumLastBids, 0.9)){
                return new Offer(partyId, oppMaxBid);
            }
            while(oppLinearPartialOrdering.isAvailable() && !oppSimilarityMap.isCompromised(ourOffer, oppNumFirstBids, utilityLowerBound)){
                // getReporter().log(Level.INFO, "<AhBuNe>: utilityLowerBound: "+utilityLowerBound+ " this.oppNumFirstBids: "+ oppNumFirstBids+"  Finding offer that opp compromises -- OPP MAX BID: " + oppMaxbid + " Our Max Bid: " + this.ourLinearPartialOrdering.getBids().get(ourLinearPartialOrdering.getBids().size()-1));
                ourOffer = ourSimilarityMap.findBidCompatibleWithSimilarity(ourNumFirstBids, ourNumLastBids, utilityLowerBound, oppMaxBid);
            }
        }
        offeredOffers.put(ourOffer, "Fantasy"); // TODO RM
        reporter.log(Level.INFO, "partyId: " + partyId);
        getReporter().log(Level.INFO, "<AhBuNe>: Offer Found: " + ourOffer);
        return new Offer(partyId, ourOffer);
    }


    private boolean doWeAccept(Bid bid) {
        if(ourSimilarityMap.isCompatibleWithSimilarity(bid, ourNumFirstBids, ourNumLastBids, 0.9)){
            return true;
        }

        double startUtilitySearch = utilityLowerBound;

        if(time >= 0.98){
            startUtilitySearch = utilityLowerBound - ourMaxCompromise;
        }

        getReporter().log(Level.INFO, "TIME: " + time);

        if(oppLinearPartialOrdering.isAvailable()){
            for (int i = (int)(startUtilitySearch*100); i <= 95; i += 5) {
                double utilityTest = (double) i / 100.0;
                if (oppSimilarityMap.isCompromised(bid, oppNumFirstBids, utilityTest)) {
                    //getReporter().log(Level.INFO, "HEYO offer has OPP utility: " + utilityTest);
                    if (ourSimilarityMap.isCompatibleWithSimilarity(bid, ourNumFirstBids, ourNumLastBids, utilityTest)) {
                        //getReporter().log(Level.INFO, "HEYO I accept the offer for, MY utility: " + utilityTest);
                        return true;
                    }
                    break;
                }
            }
        }
        return false;
    }

    private boolean doWeMakeElicitation() {
        if (leftElicitationNumber == 0) {
            //reporter.log(Level.INFO, "<AhBuNe>: NO ELICITATION");
            return false;
        }
        if (allPossibleBidsSize.intValue() <= 100) {
            //reporter.log(Level.INFO, "<AhBuNe>: ELICITATE allBidsSize < 100");
            if (ourLinearPartialOrdering.getKnownBidsSize() < allPossibleBidsSize.intValue() * 0.1) {
                elicitationBid = elicitationRandomBidGenerator();
                return true;
            }
        } else if (ourLinearPartialOrdering.getKnownBidsSize() < 10) {
            //reporter.log(Level.INFO, "<AhBuNe>: ELICITATE allBidsSize > 100");
            elicitationBid = elicitationRandomBidGenerator();
            return true;
        }
        else if(time > 0.98 && oppLinearPartialOrdering.isAvailable()){
            reporter.log(Level.INFO, "OPP ELICIT: ");
            if(mostCompromisedBids.size() > 0){
                elicitationBid = mostCompromisedBids.remove(mostCompromisedBids.size()-1).getKey();
                oppElicitatedBid.add(elicitationBid);
                return true;
            }else {
                LinkedHashMap<Bid,Integer> mostCompromisedBidsHash = oppSimilarityMap.mostCompromisedBids();
                Set<Map.Entry<Bid, Integer>> sortedCompromiseMapSet = mostCompromisedBidsHash.entrySet();
                mostCompromisedBids = new ArrayList<>(sortedCompromiseMapSet);
                this.elicitationBid = this.mostCompromisedBids.remove(this.mostCompromisedBids.size()-1).getKey();
                oppElicitatedBid.add(elicitationBid);
                return true;
            }

        }
        return false;
    }

    private void strategySelection() {
        utilityLowerBound = getUtilityLowerBound(time, lostElicitScore);
        ourKnownBidNum = ourLinearPartialOrdering.getKnownBidsSize();
        oppKnownBidNum = oppLinearPartialOrdering.getKnownBidsSize();
        ourNumFirstBids = getNumFirst(utilityLowerBound, ourKnownBidNum);
        ourNumLastBids = getNumLast(utilityLowerBound, getUtilityLowerBound(1.0, lostElicitScore), ourKnownBidNum);
        if (lastReceivedBid != null) {
            oppLinearPartialOrdering.updateBid(lastReceivedBid);
            oppSimilarityMap.update(oppLinearPartialOrdering);
            oppNumFirstBids = getOppNumFirst(utilityLowerBound, oppKnownBidNum);
        }
    }

    private void getElicitationCost(Settings settings) {
        try {
            elicitationCost = Double.parseDouble(settings.getParameters().get("elicitationcost").toString());
            //getReporter().log(Level.INFO, "AAAAAAAAAAAAsssss" + elicitationCost + "Elicit number" + leftElicitationNumber);
            leftElicitationNumber = (int) (maxElicitationLost.doubleValue() / elicitationCost);
            //getReporter().log(Level.INFO, "sssss" + elicitationCost + "Elicit number" + leftElicitationNumber);
            reporter.log(Level.INFO, "leftElicitationNumber: "+ leftElicitationNumber);
        } catch (Exception e) {
            elicitationCost = 0.01;
            leftElicitationNumber = (int) (maxElicitationLost.doubleValue() / elicitationCost);
            reporter.log(Level.INFO, "catch leftElicitationNumber: "+ leftElicitationNumber);
        }
    }

    private void getReservationRatio() {
        try {
            reservationBid = profileInterface.getProfile().getReservationBid();
        } catch (Exception e) {
            reservationBid = null;
        }
    }

    double getUtilityLowerBound(double time, double lostElicitScore) {
        return ((-pow((1.6 * (time - 0.5)) / 2, 2) + 0.85) + lostElicitScore);
    }

    int getNumFirst(double utilityLowerBound, int knownBidNum) {
        return ((int) (knownBidNum * (1 - utilityLowerBound)) + 1);
    }

    int getNumLast(double utilityLowerBound, double minUtilityLowerBound, int ourKnownBidNum) {
        return ((int) (ourKnownBidNum * (1 - minUtilityLowerBound)) - (int) (ourKnownBidNum * (1 - utilityLowerBound)) + 1);
    }

    int getOppNumFirst(double utilityLowerBound, int oppKnownBidNum) {
        return ((int) (oppKnownBidNum * (1 - utilityLowerBound)) + 1);
    }
}
