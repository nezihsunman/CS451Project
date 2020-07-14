package negotiator.ahbuneagent.linearorder;

import geniusweb.issuevalue.Bid;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OppSimpleLinearOrdering {

    private List<Bid> bids; // worst bid first, best bid last.

    public OppSimpleLinearOrdering() {
        this.bids = new ArrayList<>();
    }

    public BigDecimal getUtility(Bid bid) {
        if (!bids.contains(bid)) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(bids.indexOf(bid) + 1);
    }

    public Bid getMaxBid(){
        if(bids.size() > 0){
            return bids.get(bids.size() - 1);
        }
        return null;
    }

    public int getKnownBidsSize(){
        return bids.size();
    }

    public boolean isAvailable(){
        if(bids.size() < 6){
            return false;
        }
        return true;
    }

    public boolean contains(Bid bid) {
        return bids.contains(bid);
    }

    public List<Bid> getBids() {
        return Collections.unmodifiableList(bids);
    }
    public Bid getBidByIndex(int index) {
        if(index < bids.size()){
            return bids.get(index);
        }
        return null;
    }

    // if a bid is not changing at first, it means it is important for opponent,
    // bids are going to be conceded after a while due to importance decreases
    public void updateBid(Bid bid) {
        if(!contains(bid))
            //add the bid at the beginning of the array if not offered before
            this.bids.add(0, bid);
    }
}
