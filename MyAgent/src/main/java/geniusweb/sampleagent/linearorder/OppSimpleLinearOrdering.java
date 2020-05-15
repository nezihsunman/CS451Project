package geniusweb.sampleagent.linearorder;

import geniusweb.issuevalue.Bid;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class OppSimpleLinearOrdering {

    private List<Bid> bidsInOrder; // worst (proposed after) bid first, best bid last.

    OppSimpleLinearOrdering() {
        this.bidsInOrder = new ArrayList<>();
    }

    public List<Bid> getSortedBids() {
        return this.bidsInOrder;
    }

    public BigDecimal getUtility(Bid bid) {
        if (bidsInOrder.size() < 2 || !bidsInOrder.contains(bid)) {
            return BigDecimal.ZERO;
        }
        // using 8 decimals, we have to pick something here (order/size)^2
        return new BigDecimal(bidsInOrder.indexOf(bid)).divide(new BigDecimal((bidsInOrder.size() - 1)), 8, RoundingMode.HALF_UP).pow(2);
    }


    public boolean contains(Bid bid) {
        return bidsInOrder.contains(bid);
    }


    // if a bid is not changing at first, it means it is important for opponent,
    // bid is going to be concaded after a while thus importance decreases
    public void updateBid(Bid bid) {
        if(!contains(bid))
            //add at the beginning of the array if not proposed previously
            this.bidsInOrder.add(0, bid);
    }
}
