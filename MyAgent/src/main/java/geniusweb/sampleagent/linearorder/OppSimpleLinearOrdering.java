package geniusweb.sampleagent.linearorder;

import geniusweb.issuevalue.Bid;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class OppSimpleLinearOrdering {

    private List<Bid> bidsInOrder; // worst (proposed after) bid first, best bid last.

    public OppSimpleLinearOrdering() {
        this.bidsInOrder = new ArrayList<>();
    }

    public List<Bid> getSortedBids() {
        return this.bidsInOrder;
    }

    public BigDecimal getUtility(Bid bid) {
        if (!bidsInOrder.contains(bid)) {
            return BigDecimal.ZERO;
        }
        // (order/size)^2
        return new BigDecimal(bidsInOrder.indexOf(bid)+1).divide(new BigDecimal((bidsInOrder.size())), 8, RoundingMode.HALF_UP).pow(2);
    }

    public boolean contains(Bid bid) {
        return bidsInOrder.contains(bid);
    }

    // if a bid is not changing at first, it means it is important for opponent,
    // bid is going to be conceded after a while thus importance decreases
    public void updateBid(Bid bid) {
        if(!contains(bid))
            //add at the beginning of the array if not offered in past
            this.bidsInOrder.add(0, bid);
    }
}
