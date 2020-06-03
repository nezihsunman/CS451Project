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

    private double acceptanceLowerBound = 1;
    private BigInteger allBidSize = new BigInteger("0");
    // Initially equals to 0
    private BigDecimal lostElicitScore = new BigDecimal("0.00");

    // If no reservation ratio is assigned by the system then it is equal to 0 by default
    private double reservationImportanceRatio = 0.0;

    //Set default as 0.1
    private BigDecimal elicitationCost = new BigDecimal("0.1");
    private boolean doWeElicitate = false;

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
                getReporter().log(Level.INFO, "---" + me + " Setting initialization is done");
            } else if (info instanceof ActionDone) {
                Action lastReceivedAction = ((ActionDone) info).getAction();
                if (lastReceivedAction instanceof Offer) {
                    getReporter().log(Level.INFO, "---" + me + "   Offer came:" + ((Offer) lastReceivedAction).getBid());
                    lastReceivedBid = ((Offer) lastReceivedAction).getBid();
                } else if (lastReceivedAction instanceof Comparison) {
                    ourEstimatedProfile = ourEstimatedProfile.with(((Comparison) lastReceivedAction).getBid(), ((Comparison) lastReceivedAction).getWorse());
                    getReporter().log(Level.INFO, "---" + me + " Comparison done for bid: " + ((Comparison) lastReceivedAction).getBid());
                    myTurn();
                }
            } else if (info instanceof YourTurn) {
                getReporter().log(Level.INFO, "---" + me + "  Your Turn info has received with bid: " + lastReceivedBid);
                if (progress instanceof ProgressRounds) {
                    progress = ((ProgressRounds) progress).advance();
                }
                myTurn();

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

        if (profileint.getProfile() instanceof LinearAdditive) {
            throw new UnsupportedOperationException(
                    "Only DefaultPartialOrdering supported");
        } else if (profileint.getProfile() instanceof PartialOrdering) {

            getReporter().log(Level.INFO, "---" + me + " Partial profile has received");

            PartialOrdering partialprofile = (PartialOrdering) profileint.getProfile();

            this.allbids = new AllBidsList(partialprofile.getDomain());
            this.allBidSize = allbids.size();
            this.impMap = new ImpMap(partialprofile);
            this.opponentEstimatedProfile = new OppSimpleLinearOrdering();
            this.oppImpMap = new OppImpMap(partialprofile);
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
        getReporter().log(Level.INFO, "---" + me + " entered into myTurn");
        time = progress.get(System.currentTimeMillis());
        prevReceivedBid = counterOffer;
        counterOffer = lastReceivedBid;
        Action action = null;
        getReporter().log(Level.INFO, "---" + me + " Elicitation check ended");
        if (elicitBid != null && counterOffer != null && prevReceivedBid != null && doWeElicitateCheck()) {
            doWeElicitate = true;
            lostElicitScore.add(elicitationCost);
        }
        if (elicitBid != null && counterOffer != null && this.doWeElicitate) {

            action = new ElicitComparison(me, counterOffer, ourEstimatedProfile.getBids());
            elicitBid = null;
            this.doWeElicitate = false;
            getReporter().log(Level.INFO, "---" + me + " Elicit bit is sent to elicitation");
        } else {
            if (counterOffer != null) {
                strategySelection();
                getReporter().log(Level.INFO, "---" + me + "  Strategy selection is finished");

                getReporter().log(Level.INFO, "---" + me + " doWeEndTheNegotiation?");
                //if not Accepted return null
                action = doWeEndTheNegotiation();

                if (action == null) {
                    getReporter().log(Level.INFO, "---" + me + " doWeAcceptTheOfferedBid?");
                    //if not Accepted return null
                    action = doWeAccept();
                }
            }
        }
        if (action == null) {
            getReporter().log(Level.INFO, "---" + me + " Selecting an offer");
            action = makeAnOffer();
            getReporter().log(Level.INFO, "---" + me + " offer is selected");
        }
        getConnection().send(action);
        getReporter().log(Level.INFO, "---" + me + " action is sent as: " + action);
    }

    private Action doWeEndTheNegotiation() {
        if (reservationImportanceRatio > 0.85) {
            getReporter().log(Level.INFO, "---" + me + " Negotiation is ended with the method doWeNeedNegotiation");
            return new EndNegotiation(me);
        }
        return null;
    }

    private Bid randomBidGenerator() {
        Random rand = new Random();
        return allbids.get(rand.nextInt(allbids.size().intValue()));
    }

    private Action makeAnOffer() throws IOException {
        ourOffer = null;
        double bidImportanceLowerBound = 0.9;
        while (true) {
            for (int i = 0; i < allBidSize.intValue(); i++) {
                Bid testBid = randomBidGenerator();
                if (impMap.getImportance(testBid) > bidImportanceLowerBound) {
                    ourOffer = testBid;
                    if (impMap.getImportance(testBid) > oppImpMap.getImportance(testBid) && oppImpMap.getImportance(testBid) > 0.5) {
                        break;
                    }
                }
            }
            if (ourOffer != null) break;
            bidImportanceLowerBound -= 0.05;
        }
        getReporter().log(Level.INFO, "---" + me + " New Offer Found: OppImp:" + oppImpMap.getImportance(ourOffer) + "ImpMap: " + impMap.getImportance(ourOffer));
        return new Offer(me, ourOffer);
    }

    private Action doWeAccept() {
        if (this.impMap.getImportance(lastReceivedBid) > acceptanceLowerBound) {
            getReporter().log(Level.INFO, "---" + me + " I am going to accept if the offer is better for me");
        }
        if (this.impMap.getImportance(lastReceivedBid) > acceptanceLowerBound
                && oppImpMap.getImportance(lastReceivedBid) * min(1, acceptanceLowerBound + 0.075) < impMap.getImportance(lastReceivedBid)) {
            getReporter().log(Level.INFO, "---" + me + " I accept the offer");
            return new Accept(me, lastReceivedBid);
        }
        return null;
    }


    private void strategySelection() throws IOException {

        // 6.6 means lower bound is set to 0.8 in time 1
        this.acceptanceLowerBound = (1 - (pow(min(0, 2 * (0.5 - this.time)), 2) / 6.6)) + lostElicitScore.doubleValue();
        getReporter().log(Level.INFO, "----> Time :" + time + "  Acceptance Lower Bound:" + this.acceptanceLowerBound + "Max " + max(0, 2 * (0.5 - this.time)) + "pow: " + pow(min(0, 2 * (0.5 - this.time)), 2));
        this.opponentEstimatedProfile.updateBid(counterOffer);
        this.oppImpMap.update(opponentEstimatedProfile);

        if (elicitBid == null) {
            this.impMap.update(ourEstimatedProfile);
            getReservationRatio();
            elicitBid = this.impMap.leastKnownBidGenerator(allbids);
        }

        getReporter().log(Level.INFO, "----> Time :" + time + "  Acceptance Lower Bound:" + acceptanceLowerBound);
        getReporter().log(Level.INFO, "----> Bid importance for opponent :" + oppImpMap.getImportance(counterOffer));
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
