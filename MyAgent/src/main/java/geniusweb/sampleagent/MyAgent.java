package geniusweb.sampleagent;

import geniusweb.actions.*;
import geniusweb.bidspace.AllBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.party.Capabilities;
import geniusweb.party.DefaultParty;
import geniusweb.party.inform.*;
import geniusweb.profile.DefaultPartialOrdering;
import geniusweb.profile.PartialOrdering;
import geniusweb.profile.utilityspace.LinearAdditive;
import geniusweb.profileconnection.ProfileConnectionFactory;
import geniusweb.profileconnection.ProfileInterface;
import geniusweb.progress.Progress;
import geniusweb.progress.ProgressRounds;
import tudelft.utilities.logging.Reporter;

import javax.websocket.DeploymentException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.logging.Level;

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

    private static final BigDecimal N09 = new BigDecimal("0.9");
    private final Random random = new Random();
    protected ProfileInterface profileint;
    private Bid lastReceivedBid = null; // we ignore all others
    private PartyId me;
    private Progress progress;
    private SimpleLinearOrdering estimatedProfile = null;
    private AllBidsList allbids; // all bids in domain.
    private ImpMap impMap = null;
    private ImpMap opponentImpMap = null;
    private BigDecimal reservationImportanceRatio;

    public MyAgent() {
    }

    public MyAgent(Reporter reporter) {
        super(reporter); // for debugging
    }

    @Override
    public void notifyChange(Inform info) {
        getReporter().log(Level.INFO,
                "Entered to notify change" );
        try {
            if (info instanceof Settings) {
                getReporter().log(Level.INFO,
                        "Entered to settings notify" );
                Settings settings = (Settings) info;
                init(settings);
                getReporter().log(Level.INFO,
                        "Exit settings" );
            } else if (info instanceof ActionDone) {
                Action otheract = ((ActionDone) info).getAction();
                if (otheract instanceof Offer) {
                    lastReceivedBid = ((Offer) otheract).getBid();
                } else if (otheract instanceof Comparison) {
                    estimatedProfile = estimatedProfile.with(((Comparison) otheract).getBid(), ((Comparison) otheract).getWorse());
                    myTurn();
                }
            } else if (info instanceof YourTurn) {
                myTurn();
            } else if (info instanceof Finished) {
                getReporter().log(Level.INFO, "Final ourcome:" + info);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to handle info", e);
        }
    }

    @Override
    public Capabilities getCapabilities() {
        return new Capabilities(new HashSet<>(Arrays.asList("SHAOP")));
    }

    @Override
    public String getDescription() {
        return "Communicates with COB party to figure out which bids are good. Accepts bids with utility > 0.9. Offers random bids. Requires partial profile";
    }

    /**
     * Called when it's (still) our turn and we should take some action. Also
     * Updates the progress if necessary.
     */
    private void myTurn() throws IOException {
        Action action = null;
        if (estimatedProfile == null) {
            estimatedProfile = new SimpleLinearOrdering(profileint.getProfile());
        }

        if (lastReceivedBid != null) {
            // then we do the action now, no need to ask user
            if (estimatedProfile.contains(lastReceivedBid)) {
                if (isGood(lastReceivedBid)) {
                    action = new Accept(me, lastReceivedBid);
                }
            } else {
                // we did not yet assess the received bid
                action = new ElicitComparison(me, lastReceivedBid, estimatedProfile.getBids());
            }
            if (progress instanceof ProgressRounds) {
                progress = ((ProgressRounds) progress).advance();
            }
        }
        // Otherwise just offer a Random bid
        // TODO can't we do better than random?
        if (action == null)
            action = randomBid();
        getConnection().send(action);
    }

    private Offer randomBid() throws IOException {
        AllBidsList bidspace = new AllBidsList( profileint.getProfile().getDomain());
        long i = random.nextInt(bidspace.size().intValue());
        Bid bid = bidspace.get(BigInteger.valueOf(i));

        return new Offer(me, bid);
    }

    private boolean isGood(Bid bid) {
        if (bid == null) {
            return false;
        }

        return estimatedProfile.getUtility(bid).compareTo(N09) >= 0;
    }

    private void init(Settings settings) throws IOException, DeploymentException {
        this.me = settings.getID();
        this.progress = settings.getProgress();
        this.profileint = ProfileConnectionFactory.create(settings.getProfile().getURI(), getReporter());

        if(profileint.getProfile()  instanceof LinearAdditive){
            getReporter().log(Level.INFO,
                    "Entered the linear additive profile" );
            LinearAdditive linearprofile = (LinearAdditive) profileint.getProfile();
            getReporter().log(Level.INFO,
                    "Entered two" );
            allbids = new AllBidsList(linearprofile.getDomain());
            getReporter().log(Level.INFO,
                    "Entered three" );
            this.impMap = new ImpMap(linearprofile);
            getReporter().log(Level.INFO,
                    "Entered four" );
            this.opponentImpMap = new ImpMap(linearprofile);
            getReporter().log(Level.INFO,
                    "Entered five" );
            //List<Bid> orderedbids = sortAllBids(allbids, linearprofile);
            //getReporter().log(Level.INFO,
            //        "min bid: " + orderedbids.get(0)+ "max bid: "+orderedbids.get(orderedbids.size()-1));
            //this.impMap.self_update(orderedbids); // TODO questionmark
            this.reservationImportanceRatio = linearprofile.getUtility(linearprofile.getReservationBid());
            getReporter().log(Level.INFO,
                    "Exit the linear profile" );
        }
        else if(profileint.getProfile() instanceof PartialOrdering){
            getReporter().log(Level.INFO,
                "Entered the partial profile" );

            PartialOrdering partialprofile = (PartialOrdering) profileint.getProfile();
            allbids = new AllBidsList(partialprofile.getDomain());
            this.impMap = new ImpMap(partialprofile);
            this.opponentImpMap = new ImpMap(partialprofile);
            List<Bid> orderedbids = new SimpleLinearOrdering(profileint.getProfile()).getBids();
            this.impMap.self_update(orderedbids);
            this.reservationImportanceRatio = this.getReservationRatio();
            //TODO elimizdeki bid sayısı belli bir orandan düşükse elimizde var olan bid sayısı
            //kadar BİLİNMEYEN özellikler üzerinden random bidler ile elicitation yap
        }

        getReporter().log(Level.INFO,
                "reservation ratio: " + this.reservationImportanceRatio);
        getReporter().log(Level.INFO,
                "Party " + me + " has finished initialization");
    }

    private List<Bid> sortAllBids(AllBidsList allbids, LinearAdditive linearprofile){
        List<Bid> orderedBids = new ArrayList<Bid>();
        for(BigInteger i = BigInteger.valueOf(0); i.compareTo(allbids.size()) != 0; i.add(BigInteger.valueOf(1))){
            orderedBids.add(allbids.get(i));
        }
        Collections.sort(orderedBids, new Comparator<Bid>() {
            @Override
            public int compare(Bid bid, Bid bid2) {
                return linearprofile.getUtility(bid).compareTo(linearprofile.getUtility(bid2));
            }
        });
        return orderedBids;
    }
    private BigDecimal getReservationRatio() throws IOException {
        //TODO implement
       /*double medianBidRatio = (this.MEDIAN_IMPORTANCE - this.MIN_IMPORTANCE)
                / (this.MAX_IMPORTANCE - this.MIN_IMPORTANCE);
        Bid resBid = this.profileint.getProfile().getReservationBid();
        double resValue = 0.1;
        if (resBid != null) {
            resValue = this.impMap.getImportance(resBid);
        }
        return resValue * medianBidRatio / 0.5;*/
        return BigDecimal.valueOf(0.8);
    }

}
