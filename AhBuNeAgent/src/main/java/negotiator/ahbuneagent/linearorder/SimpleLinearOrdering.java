package negotiator.ahbuneagent.linearorder;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.profile.DefaultPartialOrdering;
import geniusweb.profile.Profile;
import geniusweb.profile.utilityspace.UtilitySpace;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/*A simple list of bids, but all bids are fully ordered (better or worse than
  other bids in the list).*/
public class SimpleLinearOrdering implements UtilitySpace {

    private final Domain domain;
    private final List<Bid> bids; // worst bid first, best bid last.

    public SimpleLinearOrdering(Profile profile) {
        this(profile.getDomain(), getSortedBids(profile));
    }

    SimpleLinearOrdering(Domain domain, List<Bid> bids) {
        this.domain = domain;
        this.bids = bids;
    }

    public Bid getMinBid(){
        if(bids.size() > 0){
            return bids.get(0);
        }
        return null;
    }

    public Bid getMaxBid(){
        if(bids.size() > 0){
            return bids.get(bids.size() - 1);
        }
        return null;
    }

    public Bid getBidByIndex(int index) {
        if(index < bids.size()){
            return bids.get(index);
        }
        return null;
    }

    public int getKnownBidsSize(){
        return bids.size();
    }

    //a list of bids in the profile sorted from low to high utility.
    private static List<Bid> getSortedBids(Profile profile) {
        if (!(profile instanceof DefaultPartialOrdering)) {
            throw new UnsupportedOperationException("Only DefaultPartialOrdering supported");
        }
        DefaultPartialOrdering prof = (DefaultPartialOrdering) profile;
        List<Bid> bidslist = prof.getBids();
        // NOTE sort defaults to ascending order
        bidslist.sort((b1, b2) -> prof.isPreferredOrEqual(b1, b2) ? 1 : -1);

        return bidslist;
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Domain getDomain() {
        return domain;
    }

    @Override
    public Bid getReservationBid() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BigDecimal getUtility(Bid bid) {
        if (!bids.contains(bid)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(bids.indexOf(bid) + 1);
    }

    public boolean contains(Bid bid) {
        return bids.contains(bid);
    }

    public List<Bid> getBids() {
        return Collections.unmodifiableList(bids);
    }

    /*SimpleLinearOrdering, updated with the given comparison. Thee
    bid will be inserted after the first bid that is not worse than
    bid.*/
    public SimpleLinearOrdering with(Bid bid, List<Bid> worseBids) {
        int n = 0;
        while (n < bids.size() && worseBids.contains(bids.get(n))) {
            n++;
        }
        LinkedList<Bid> newbids = new LinkedList<Bid>(bids);
        newbids.add(n, bid);

        return new SimpleLinearOrdering(domain, newbids);
    }
}
