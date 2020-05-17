package geniusweb.sampleagent;

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
import geniusweb.sampleagent.impmap.ImpMap;
import geniusweb.sampleagent.impmap.OppImpMap;
import geniusweb.sampleagent.linearorder.OppSimpleLinearOrdering;
import geniusweb.sampleagent.linearorder.SimpleLinearOrdering;
import tudelft.utilities.logging.Reporter;

import javax.websocket.DeploymentException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Level;

import static java.lang.Math.min;
import static java.lang.Math.pow;

public class AhNeCeAgent extends DefaultParty {

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
    private List<Bid> elicitBidList = new ArrayList<>();
    private SimpleLinearOrdering ourEstimatedProfile = null;
    private OppSimpleLinearOrdering opponentEstimatedProfile = null;
    private ImpMap impMap = null;
    private OppImpMap oppImpMap = null;

    private double acceptanceLowerBound = 1;
    private BigInteger allBidSize = new BigInteger("0");
    // Initially equals to 0
    private BigDecimal lostElicitScore = new BigDecimal("0.00");

    // If no reservation rasio is assigned by the system then it is equal to 0 by default
    private BigDecimal reservationImportanceRatio = new BigDecimal("0.00");

    //Set default as 0.1
    private BigDecimal elicitationCost = new BigDecimal("0.1");
    private BigDecimal elicitBoundRatio = new BigDecimal("0.0");

    //Going to be used when elicitation system is developed in more detail
    private BigDecimal exploredBidRatio = new BigDecimal("0.00");

    public AhNeCeAgent() { }

    public AhNeCeAgent(Reporter reporter) {
        super(reporter); // for debugging
    }

    @Override
    public Capabilities getCapabilities() {
        return new Capabilities(new HashSet<>(Arrays.asList("SHAOP")));
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
                getReporter().log(Level.INFO, "---"+me+" Setting initialization is done");
            } else if (info instanceof ActionDone) {
                Action lastReceivedAction = ((ActionDone) info).getAction();
                if (lastReceivedAction instanceof Offer) {
                    getReporter().log(Level.INFO, "---"+me+"   Offer came:" + ((Offer) lastReceivedAction).getBid());
                    lastReceivedBid = ((Offer) lastReceivedAction).getBid();
                } else if (lastReceivedAction instanceof Comparison) {
                    ourEstimatedProfile = ourEstimatedProfile.with(((Comparison) lastReceivedAction).getBid(), ((Comparison) lastReceivedAction).getWorse());
                    getReporter().log(Level.INFO, "---"+me+" Comparison done for bid: "+ ((Comparison) lastReceivedAction).getBid());
                    myTurn();
                }
            } else if (info instanceof YourTurn) {
                getReporter().log(Level.INFO, "---"+me+"  Your Turn info has received with bid: " + lastReceivedBid);
                myTurn();
                if (progress instanceof ProgressRounds) {
                    progress = ((ProgressRounds) progress).advance();
                }
            } else if (info instanceof Finished) {
                getReporter().log(Level.INFO, "Final outcome:" + info);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle info", e);
        }
    }

    private void init(Settings settings) throws IOException, DeploymentException {
        this.me = settings.getID();
        this.progress = settings.getProgress();
        this.profileint = ProfileConnectionFactory.create(settings.getProfile().getURI(), getReporter());

        if(profileint.getProfile()  instanceof LinearAdditive){
            throw new UnsupportedOperationException(
                    "Only DefaultPartialOrdering supported");
        }

        else if(profileint.getProfile() instanceof PartialOrdering){

            getReporter().log(Level.INFO, "---"+me+" Partial profile has received");

            PartialOrdering partialprofile = (PartialOrdering) profileint.getProfile();

            this.allbids = new AllBidsList(partialprofile.getDomain());
            this.allBidSize = allbids.size();
            this.impMap = new ImpMap(partialprofile);
            this.opponentEstimatedProfile = new OppSimpleLinearOrdering();
            this.oppImpMap = new OppImpMap(partialprofile);
            this.ourEstimatedProfile = new SimpleLinearOrdering(profileint.getProfile());
            orderedbids = ourEstimatedProfile.getBids();
            exploredBidRatio = BigDecimal.valueOf(orderedbids.size()).divide(new BigDecimal(allBidSize), 8, RoundingMode.HALF_UP);

            getReporter().log(Level.INFO, "Ordered Bids Before Elicitation: " + orderedbids);
            checkElicitation();
            getReporter().log(Level.INFO, "---"+me+" Elicitation check ended");

            this.reservationImportanceRatio = this.getReservationRatio();
        }
        else{
            throw new UnsupportedOperationException("Only DefaultPartialOrdering supported");
        }

    }

    private void myTurn() throws IOException {
        getReporter().log(Level.INFO, "---"+me+" entered into myTurn");
        time = progress.get(System.currentTimeMillis());
        prevReceivedBid = counterOffer;
        counterOffer = lastReceivedBid;
        Action action = null;
        checkElicitation();
        getReporter().log(Level.INFO, "---"+me+" Elicitation check ended");

        if(elicitBidList != null && elicitBidList.size() >= 0){
            // returns null action if elicitation is done
            action = doElicitation();
            getReporter().log(Level.INFO, "---"+me+" doElicitation method is finished");
        }
        else {
            if (counterOffer != null) {
                strategySelection();
                getReporter().log(Level.INFO, "---"+me+"  Strategy selection is finished");

                getReporter().log(Level.INFO, "---"+me+" doWeEndTheNegotiation?");
                //if not Accepted return null
                //For now we dont end the negotiation
                action = doWeEndTheNegotiation(action);

                getReporter().log(Level.INFO, "---"+me+" doWeAcceptTheOfferedBid?");
                //if not Accepted return null
                action = doWeAccept();
            }
        }
        if (action == null){
            getReporter().log(Level.INFO, "---"+me+" Selecting an offer");
            action = makeAnOffer();
            getReporter().log(Level.INFO, "---"+me+" offer is selected");
        }
        getConnection().send(action);
        getReporter().log(Level.INFO, "---"+me+" action is sent as: " + action);
    }

    private Action doWeEndTheNegotiation(Action action) {
        //TODO Will be changed after tests if necessary
        if(false)
            return new EndNegotiation(me);
        return null;
    }

    private Action makeAnOffer() throws IOException {
        while(true){
            Bid randomBid = randomBidGenerator();
            if(impMap.getImportance(randomBid) > 0.9){
                 // TODO  -->  if oppImpMap.getImportance(randomBid) < 0.6 + (1-acceptanceLowerBound)
                 // After learning time restrictions, its problematic for now
                ourOffer = randomBid;
                break;
            }
        }
        return new Offer(me, ourOffer);
    }

    private Action doWeAccept() {
        if (this.impMap.getImportance(lastReceivedBid) > acceptanceLowerBound){
            getReporter().log(Level.INFO, "---"+ me +" I am going to accept if the offer is better for me");
        }
        if (this.impMap.getImportance(lastReceivedBid) > acceptanceLowerBound
                && oppImpMap.getImportance(lastReceivedBid) < impMap.getImportance(lastReceivedBid)){
            getReporter().log(Level.INFO, "---"+ me +" I accept the offer");
            return new Accept(me, lastReceivedBid);
        }
        return null;
    }

    private Action doElicitation() throws IOException {
        Action action = null;
        if(elicitBidList.size() == 0){
            getReporter().log(Level.INFO, "---"+me+" No elicitation bids left");
            orderedbids = ourEstimatedProfile.getBids();
            this.impMap.update(ourEstimatedProfile);
            getReporter().log(Level.INFO, "Ordered Bids After Elicitation: " + orderedbids);
            exploredBidRatio = BigDecimal.valueOf(orderedbids.size()).divide(new BigDecimal(allBidSize), 8, RoundingMode.HALF_UP);
            elicitBidList = null;
            action = null;
        }
        else{
            getReporter().log(Level.INFO, "---"+me+" Sending elicitation request");
            action = new ElicitComparison(me, (Bid) elicitBidList.get(0), ourEstimatedProfile.getBids());
            elicitBidList.remove(0);
        }
        return action;
    }

    private Bid randomBidGenerator() throws IOException {
        AllBidsList bidspace = new AllBidsList( profileint.getProfile().getDomain());
        long i = random.nextInt(bidspace.size().intValue());
        Bid bid = bidspace.get(BigInteger.valueOf(i));
        return bid;
    }

    private void strategySelection(){

        // 6.6 means lower bound is set to 0.8 in time 1
        this.acceptanceLowerBound = 1 - (pow(min(0, 2 * (0.5 - this.time)), 2) / 6.6);

        this.opponentEstimatedProfile.updateBid(counterOffer);
        this.oppImpMap.update(opponentEstimatedProfile);

        getReporter().log(Level.INFO, "----> Time :"+time+"  Acceptance Lower Bound:" + acceptanceLowerBound);
        getReporter().log(Level.INFO, "----> Bid importance for opponent :"+ oppImpMap.getImportance(counterOffer));
        getReporter().log(Level.INFO, "----> Bid importance for me :"+ impMap.getImportance(counterOffer));
    }

    private void checkElicitation() throws IOException {

        getReporter().log(Level.INFO, "---"+me+" Checking if we do elicitation");
        getReporter().log(Level.INFO, "---"+me+" Lost Elicit Score: "+ lostElicitScore);
        getReporter().log(Level.INFO, "---"+me+" Elicit Cost: "+ elicitationCost);
        getReporter().log(Level.INFO, "---"+me+" Lost elicitBoundRatio: "+ elicitBoundRatio);
        int elicitNumber = elicitBoundRatio.subtract(lostElicitScore).divide(elicitationCost, 8, RoundingMode.HALF_UP).intValue();
        if(elicitNumber > 0){
            elicitBidList = new ArrayList<>();
            getReporter().log(Level.INFO, "---"+me+" We will do elicitation for n times:" + elicitNumber);
            for(int i = 0; i< elicitNumber; i++){
                elicitBidList.add(randomBidGenerator());
                //TODO calculate with respect to impMap (Select the  minimum existing issue value to send to elicitation)
                lostElicitScore = lostElicitScore.add(elicitationCost);
            }
            getReporter().log(Level.INFO, "---"+me+": Elicitation list is assigned");
        }
        getReporter().log(Level.INFO, "---"+me+": Elicitation list is not assigned");
    }

    private BigDecimal getReservationRatio() throws IOException {
        try{
            /*Bid resBid = this.profileint.getProfile().getReservationBid();
            if (resBid != null) {
                resValue = this.impMap.getImportance(resBid);
            }*/
            // For now, the reservation bid is not used in CS451 Project
            return BigDecimal.valueOf(0.0);
        } catch (Exception e) {
            return BigDecimal.valueOf(0.0);
        }
    }
}
