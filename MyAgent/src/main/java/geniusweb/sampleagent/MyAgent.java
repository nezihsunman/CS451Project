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

import static java.lang.Math.pow;

/**
 * A simple implementation of a SHAOP party that can handle only bilateral
 * negotiations (1 other party). It will ignore all other parties except the one
 * that has the turn right before us. It estimates the utilities of bids by
 * assigning a linear increasing utility from the orderings that have been
 * created.
 * <p>
 * <b>Requirement<b> the initial {@link PartialOrdering} must contain at least
 * the bids with lowest utility and highest utility, and the proper comparison
 * info for these two bids.
 */
public class MyAgent extends DefaultParty {

    private final Random random = new Random();

    private Bid lastReceivedBid = null;
    private Bid prevReceivedBid = null;
    private Bid counterOffer = null;
    private Bid ourOffer = null;

    protected ProfileInterface profileint;
    private PartyId me;
    private Progress progress;
    double time = 0.0;


    private AllBidsList allbids; // all bids in domain.
    private List<Bid> orderedbids;
    private List<Bid> elicitBidList = new ArrayList<>();
    private SimpleLinearOrdering ourEstimatedProfile = null;
    private OppSimpleLinearOrdering opponentEstimatedProfile = null;
    private ImpMap impMap = null;
    private OppImpMap oppImpMap = null;


    //between 0 and 1
    private double timeImportanceConstant = 1;

    private BigInteger allBidSize = new BigInteger("0");
    private BigDecimal lostElicitScore = new BigDecimal("0.00");
    private BigDecimal defaultScore = new BigDecimal("0.00");
    //Set default as 0.1
    private BigDecimal elicitationCost = new BigDecimal("0.1");
    private BigDecimal elicitBoundRatio = new BigDecimal("0.2");
    private BigDecimal exploredBidRatio = new BigDecimal("0.00");
    //TODO given bid boundlarını hesaba kat

    public MyAgent() { }

    public MyAgent(Reporter reporter) {
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
                getReporter().log(Level.INFO, "--- Setting is initializing");
                Settings settings = (Settings) info;
                init(settings);
                getReporter().log(Level.INFO, "---"+me+" Setting initialization is done");
            } else if (info instanceof ActionDone) {
                Action lastReceivedAction = ((ActionDone) info).getAction();
                if (lastReceivedAction instanceof Offer) {
                    getReporter().log(Level.INFO, "---"+me+"   Offer came:" + ((Offer) lastReceivedAction).getBid());
                    lastReceivedBid = ((Offer) lastReceivedAction).getBid();
                } else if (lastReceivedAction instanceof Comparison) {
                    //TODO after preventing same bid elicitation delete this if
                    if(!ourEstimatedProfile.contains(((Comparison) lastReceivedAction).getBid()));
                        ourEstimatedProfile = ourEstimatedProfile.with(((Comparison) lastReceivedAction).getBid(), ((Comparison) lastReceivedAction).getWorse());
                    getReporter().log(Level.INFO, "---"+me+" Comparison done for bid: "+ ((Comparison) lastReceivedAction).getBid());
                    getReporter().log(Level.INFO, "---"+me+" Entering myTurn after comparison");
                    myTurn();
                    getReporter().log(Level.INFO, "---"+me+" Exit myTurn after comparison");
                }
            } else if (info instanceof YourTurn) {
                getReporter().log(Level.INFO, "---"+me+"  Your Turn info has received with bid: " + lastReceivedBid);
                myTurn();
                getReporter().log(Level.INFO, "---"+me+" Exit myTurn after Your Turn info");
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
            this.impMap = new ImpMap(partialprofile, getReporter());
            this.opponentEstimatedProfile = new OppSimpleLinearOrdering();
            this.oppImpMap = new OppImpMap(partialprofile);
            this.ourEstimatedProfile = new SimpleLinearOrdering(profileint.getProfile());
            orderedbids = ourEstimatedProfile.getBids();
            exploredBidRatio = BigDecimal.valueOf(orderedbids.size()).divide(new BigDecimal(allBidSize), 8, RoundingMode.HALF_UP);

            getReporter().log(Level.INFO,
                    "Ordered Bids Before Elcitation: " + orderedbids);

            getReporter().log(Level.INFO, "---"+me+" Elicitation check started");
            checkElicitation();
            getReporter().log(Level.INFO, "---"+me+" Elicitation check ended");


            //this.reservationImportanceRatio = this.getReservationRatio();
        }
        else{
            throw new UnsupportedOperationException(
                    "Only DefaultPartialOrdering supported");
        }

        //TODO get elicitation cost as parameter

    }

    private void myTurn() throws IOException {
        getReporter().log(Level.INFO, "---"+me+" myTurn method is called");
        time = progress.get(System.currentTimeMillis());
        prevReceivedBid = counterOffer;
        counterOffer = lastReceivedBid;
        Action action = null;


        getReporter().log(Level.INFO, "---"+me+" Elicitation check started");
        checkElicitation();
        getReporter().log(Level.INFO, "---"+me+" Elicitation check ended");

        if(elicitBidList != null && elicitBidList.size() >= 0){
            // returns null action if elicity is done
            getReporter().log(Level.INFO, "---"+me+" doElicitation started");
            action = doElicitation();
            getReporter().log(Level.INFO, "---"+me+" doElicitation ended");
        }

        else {

            if (counterOffer != null) {

                getReporter().log(Level.INFO, "---"+me+" Entered to strategy selection");
                strategySelection();
                getReporter().log(Level.INFO, "---"+me+" Exit the strategy selection");



                getReporter().log(Level.INFO, "---"+me+" doWeEndTheNegotiation?");
                //if not Accepted return null
                action = doWeEndTheNegotiation();

                getReporter().log(Level.INFO, "---"+me+" doWeAcceptTheOfferedBid?");
                //if not Accepted return null
                action = doWeAccept();

                if (progress instanceof ProgressRounds) {
                    progress = ((ProgressRounds) progress).advance();
                }

            }

        }


        // TODO can't we do better than random?
        if (action == null){

            getReporter().log(Level.INFO, "---"+me+" action was null, making an offer?");
            action = makeAnOffer();
            getReporter().log(Level.INFO, "---"+me+" offer is set?");

            // TODO offer strategy
        }

        getReporter().log(Level.INFO, "---"+me+" action is selected as: "+action);
        getConnection().send(action);
    }

    private Action doWeEndTheNegotiation() {
        //TODO end check
        if(false)
            return new EndNegotiation(me);
        return null;
    }

    private Action makeAnOffer() {

        int num = (int)((ourEstimatedProfile.getBids().size()-1) * (1-time));
        //TODO offer strategy
        return new Offer(me, ourEstimatedProfile.getBids().get(num));
    }

    //TODO COMPLETE
    private Action doWeAccept() {

        if (false)
                return new Accept(me, lastReceivedBid);
        return null;
    }

    private Action doElicitation() throws IOException {

        Action action = null;
        if(elicitBidList.size() == 0){
            getReporter().log(Level.INFO, "---"+me+" elicitationBidList size is 0");
            orderedbids = ourEstimatedProfile.getBids();
            this.impMap.update(ourEstimatedProfile);

            getReporter().log(Level.INFO, "Ordered Bids After Elicitation: " + orderedbids);

            exploredBidRatio = BigDecimal.valueOf(orderedbids.size()).divide(new BigDecimal(allBidSize), 8, RoundingMode.HALF_UP);
            elicitBidList = null;
            getReporter().log(Level.INFO, "---"+me+": Elicitation list is assigned as null");
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
        //TODO calculate w/r to impMap
        AllBidsList bidspace = new AllBidsList( profileint.getProfile().getDomain());
        long i = random.nextInt(bidspace.size().intValue());
        Bid bid = bidspace.get(BigInteger.valueOf(i));

        return bid;
    }

    private void strategySelection(){

        this.timeImportanceConstant = pow(2 * (0.5 - this.time), 2);

        this.opponentEstimatedProfile.updateBid(counterOffer);
        this.oppImpMap.update(opponentEstimatedProfile);



        getReporter().log(Level.INFO, "----> Time :"+time+"  impconst:" + timeImportanceConstant);
        getReporter().log(Level.INFO, "----> "+time*100+"  Bid importance for opponent :"+ oppImpMap.getImportance(counterOffer));
        getReporter().log(Level.INFO, "---->"+time*100+"  Bid importance for me :"+ impMap.getImportance(counterOffer));
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
                lostElicitScore = lostElicitScore.add(elicitationCost);
                //TODO add arguments

                //TODO elimizdeki bid sayısı belli bir orandan düşükse elimizde var olan bid sayısı
                //kadar BİLİNMEYEN özellikler üzerinden random bidler ile elicitation yap
            }
            getReporter().log(Level.INFO, "---"+me+": Elicitation list is assigned");
        }
        getReporter().log(Level.INFO, "---"+me+": Elicitation list is not assigned");



    }

    private BigDecimal getReservationRatio() throws IOException {
        try{
            //TODO implement
            /*double medianBidRatio = (this.MEDIAN_IMPORTANCE - this.MIN_IMPORTANCE)
                / (this.MAX_IMPORTANCE - this.MIN_IMPORTANCE);
            Bid resBid = this.profileint.getProfile().getReservationBid();
            double resValue = 0.1;
            if (resBid != null) {
                resValue = this.impMap.getImportance(resBid);
            }
            return resValue * medianBidRatio / 0.5;*/
            return BigDecimal.valueOf(0.8); // TODO for now
        } catch (Exception e) {
            return BigDecimal.valueOf(0.8);
        }




    }

}
