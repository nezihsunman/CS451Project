package negotiator.group3.linearorder;

import geniusweb.issuevalue.Bid;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OppSimpleLinearOrdering {

    private final List<Bid> bids; // worst bid first, best bid last.

    public OppSimpleLinearOrdering() {
        this.bids = new ArrayList<>();
    }

    public List<Bid> getSortedBids() {
        return this.bids;
    }

    public BigDecimal getUtility(Bid bid) {
        /*if (!bidsInOrder.contains(bid)) {
            return BigDecimal.ZERO;
        }
        // (order/size)^2
        return new BigDecimal(bidsInOrder.indexOf(bid)+1).divide(new BigDecimal((bidsInOrder.size())), 8, RoundingMode.HALF_UP).pow(2);*/

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

    // if a bid is not changing at first, it means it is important for opponent,
    // bid is going to be conceded after a while thus importance decreases
    public void updateBid(Bid bid) {
        if(!contains(bid))
            //add at the beginning of the array if not offered in past
            this.bids.add(0, bid);
    }
}
