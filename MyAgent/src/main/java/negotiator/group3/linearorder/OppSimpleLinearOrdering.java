package negotiator.group3.linearorder;

import geniusweb.issuevalue.Bid;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class OppSimpleLinearOrdering {

    private HashMap<Bid, Integer> numberOfOfferedBid = new HashMap<>();

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
        return new BigDecimal(bidsInOrder.indexOf(bid) + 1).divide(new BigDecimal((bidsInOrder.size())), 8, RoundingMode.HALF_UP);
    }

    public boolean contains(Bid bid) {
        return bidsInOrder.contains(bid);
    }

    // if a bid is not changing at first, it means it is important for opponent,
    // bid is going to be conceded after a while thus importance decreases
    public void updateBid(Bid bid) {
        if (numberOfOfferedBid.containsKey(bid)) {
            numberOfOfferedBid.put(bid, numberOfOfferedBid.get(bid) + 1);
        } else {
            numberOfOfferedBid.put(bid, 1);
        }
        if (!contains(bid))
            //add at the beginning of the array if not offered in past
            this.bidsInOrder.add(0, bid);
    }

    private LinkedHashMap<Bid, Integer> sortByValue(HashMap<Bid, Integer> hm) {
        List<Map.Entry<Bid, Integer>> list =
                new LinkedList<Map.Entry<Bid, Integer>>(hm.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<Bid, Integer>>() {
            public int compare(Map.Entry<Bid, Integer> o1,
                               Map.Entry<Bid, Integer> o2) {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });
        LinkedHashMap<Bid, Integer> temp = new LinkedHashMap<Bid, Integer>();
        for (Map.Entry<Bid, Integer> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }

    public List<Bid> getSortedOfferedList() {

        Set<Map.Entry<Bid, Integer>> sortedIssueMapSet = this.sortByValue(this.numberOfOfferedBid).entrySet();
        ArrayList<Map.Entry<Bid, Integer>> sortedIssueArrList = new ArrayList<Map.Entry<Bid, Integer>>(sortedIssueMapSet);
        List<Bid> bidInorderOpponent = new ArrayList<>();
        for (int i = 0; i < sortedIssueArrList.size(); i++) {
            bidInorderOpponent.add(i, sortedIssueArrList.get(i).getKey());
        }
        return bidInorderOpponent;
    }
}
