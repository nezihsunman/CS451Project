package negotiator.ahbuneagent.impmap;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.profile.Profile;
import negotiator.ahbuneagent.linearorder.OppSimpleLinearOrdering;
import java.util.*;

public class OppSimilarityMap {

    private Domain domain;
    private HashMap<String, List<OppIssueValueUnit>> oppIssueValueImpMap;
    private OppSimpleLinearOrdering oppEstimatedProfile;
    private Bid maxImpBid;
    private HashMap<String, List<Value>> availableValues;
    private List<String> issueList = new ArrayList<>();

    public OppSimilarityMap(Profile profile) {
        this.domain = profile.getDomain();
        for (String issue : domain.getIssues()) {
            issueList.add(issue);
        }
        renewMaps();
    }

    private void createConditionLists(int numFirstBids){
        renewLists();
        List<Bid> sortedBids = oppEstimatedProfile.getBids();
        int firstStartIndex = (sortedBids.size()-1) - numFirstBids;
        if(firstStartIndex <= 0){
            firstStartIndex = 0;
        }
        for(int bidIndex = firstStartIndex; bidIndex < sortedBids.size(); bidIndex++){
            Bid currentBid = sortedBids.get(bidIndex);
            for (String issue : currentBid.getIssues()) {
                List<OppIssueValueUnit> currentIssueList = oppIssueValueImpMap.get(issue);
                for (OppIssueValueUnit currentUnit : currentIssueList) {
                    if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
                        if(!availableValues.get(issue).contains(currentBid.getValue(issue))){
                            availableValues.get(issue).add(currentBid.getValue(issue));
                        }
                        break;
                    }
                }
            }
        }
    }

    public boolean isCompromised(Bid bid, int numFirstBids,  double minUtility){
        createConditionLists(numFirstBids);
        double issueChangeLoss = 1.0 / domain.getIssues().size();
        int changeRest = (int)((1 - minUtility) / issueChangeLoss) + 1;
        if(changeRest > issueList.size()){
            changeRest = issueList.size();
        }
        int changedIssue= 0;
        for (int i = 0; i < issueList.size(); i++) {
            String issue = issueList.get(i);
            List<Value> availableIssueValueList = availableValues.get(issue);
            if(!maxImpBid.getValue(issue).equals(bid.getValue(issue))){
                if(!availableIssueValueList.contains(bid.getValue(issue))){
                    changedIssue += 2;
                }
                else{
                    changedIssue++;
                }
            }
        }
        if(changedIssue <= changeRest){
            return false;
        }
        return true;
    }

    public void update(OppSimpleLinearOrdering estimatedProfile) {
        renewMaps();
        this.oppEstimatedProfile = estimatedProfile;
        List<Bid> sortedBids = estimatedProfile.getBids();
        this.maxImpBid = estimatedProfile.getMaxBid();
        for(int bidIndex = 0; bidIndex < sortedBids.size(); bidIndex++){
            Bid currentBid = sortedBids.get(bidIndex);
            double bidImportance = estimatedProfile.getUtility(currentBid).doubleValue();
            for (String issue : currentBid.getIssues()) {
                List<OppIssueValueUnit> currentIssueList = oppIssueValueImpMap.get(issue);
                for (OppIssueValueUnit currentUnit : currentIssueList) {
                    if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
                        currentUnit.importanceList.add(bidImportance);
                        break;
                    }
                }
            }
        }
    }

    private void renewMaps(){
        oppIssueValueImpMap = new HashMap<>();
        for (String issue : domain.getIssues()) {
            ValueSet values = domain.getValues(issue);
            List<OppIssueValueUnit> issueIssueValueUnit = new ArrayList<>();
            for (Value value : values) {
                issueIssueValueUnit.add(new OppIssueValueUnit(value));
            }
            oppIssueValueImpMap.put(issue, issueIssueValueUnit);
        }
    }

    private void renewLists(){
        availableValues = new HashMap<>();
        for (String issue : domain.getIssues()) {
            availableValues.put(issue, new ArrayList<>());
        }
    }

    public LinkedHashMap<Bid,Integer> mostCompromisedBids(){
        List<Bid> orderedBids =  oppEstimatedProfile.getBids();
        Bid maxUtilBid = orderedBids.get(orderedBids.size() - 1);
        HashMap<Bid,Integer> listOfOpponentCompremesid = new HashMap<>();
        for(int i = 0; i < orderedBids.size(); i++){
            Bid testBid = orderedBids.get(i);
            int compromiseCount = 0;
            for(String issue : domain.getIssues()){
                if(!maxUtilBid.getValue(issue).equals(testBid.getValue(issue))){
                    compromiseCount ++;
                }
            }
            listOfOpponentCompremesid.put(testBid,compromiseCount);
        }
        LinkedHashMap<Bid,Integer> sorted = sortByValueBid(listOfOpponentCompremesid);
        return sorted;
    }

    private LinkedHashMap<Bid, Integer> sortByValueBid(HashMap<Bid, Integer> hm)
    {
        List<Map.Entry<Bid, Integer>> list = new LinkedList<>(hm.entrySet());
        Collections.sort(list, Comparator.comparing(Map.Entry::getValue));
        LinkedHashMap<Bid, Integer> temp = new LinkedHashMap<>();
        for (Map.Entry<Bid, Integer> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }
}
