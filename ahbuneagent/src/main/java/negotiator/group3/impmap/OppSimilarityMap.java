package negotiator.group3.impmap;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.profile.Profile;
import negotiator.group3.linearorder.OppSimpleLinearOrdering;
import tudelft.utilities.logging.Reporter;

import java.util.*;
import java.util.logging.Level;

public class OppSimilarityMap {

    private Domain domain;
    private HashMap<String, List<OppIssueValueUnit>> oppIssueValueImpMap;
    private HashMap<String,Double> issueImpMap;
    private OppSimpleLinearOrdering oppEstimatedProfile;
    private Bid maxImpBid;
    private HashMap<String, List<Value>> availableValues;
    private List<String> issueList = new ArrayList<>();
    private Reporter reporter;


    // Importance map
    public OppSimilarityMap(Profile profile, Reporter reporter) {
        this.domain = profile.getDomain();
        for (String issue : domain.getIssues()) {
            issueList.add(issue);
        }
        this.reporter = reporter;
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

        if(changeRest > domain.getIssues().size()){
            changeRest = domain.getIssues().size();
        }

        int changedIssue= 0;

        //reporter.log( Level.INFO, "OPP ISSUE LIST SIZE: "+ issueList.size());

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
        //reporter.log( Level.INFO, "OPP COMPROMISE CHECK: "+ bid + " MAX BID: " + maxImpBid);
        if(changedIssue <= changeRest){
            return false;
        }
        return true;
    }

    public void update(OppSimpleLinearOrdering estimatedProfile) {
        renewMaps();

        this.oppEstimatedProfile = estimatedProfile;

        //reporter.log( Level.INFO, " Given Bids:  "+ estimatedProfile.getBids() );


        List<Bid> sortedBids = estimatedProfile.getBids();

        this.maxImpBid = sortedBids.get(sortedBids.size() - 1);

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
        issueImpMap = new HashMap<>();
        // Create empty importance map
        for (String issue : domain.getIssues()) {
            issueImpMap.put(issue, 0.0);
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
        // Create empty maps
        for (String issue : domain.getIssues()) {
            availableValues.put(issue, new ArrayList<>());
        }
    }

    public LinkedHashMap<Bid,Integer> mostCompromisedBids(){
        List<Bid> orderedBids =  oppEstimatedProfile.getBids();
        Bid maxUtilBid = orderedBids.get(orderedBids.size() - 1);
        int maxCompromiseCount = 0;
        HashMap<Bid,Integer> listOfOpponentCompremesid = new HashMap<>();
        for(int i = 0; i < orderedBids.size(); i++){
            Bid testBid = orderedBids.get(i);
            int compromiseCount = 0;
            for(String issue : issueImpMap.keySet()){
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
        List<Map.Entry<Bid, Integer> > list =
                new LinkedList<Map.Entry<Bid, Integer> >(hm.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<Bid, Integer> >() {
            public int compare(Map.Entry<Bid, Integer> o1,
                               Map.Entry<Bid, Integer> o2)
            {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });
        LinkedHashMap<Bid, Integer> temp = new LinkedHashMap<Bid, Integer>();
        for (Map.Entry<Bid, Integer> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }
}
