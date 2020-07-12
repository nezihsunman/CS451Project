package negotiator.group3.impmap;

import geniusweb.bidspace.AllBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.profile.Profile;
import negotiator.group3.linearorder.OppSimpleLinearOrdering;
import tudelft.utilities.logging.Reporter;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class OppImpMap {

    private Domain domain;
    private HashMap<String, List<OppImpUnit>> issueValueImpMap;
    private HashMap<String,Double> issueImpMap;
    private OppSimpleLinearOrdering estimatedProfile;
    private Bid maxImpBid;
    private HashMap<String, List<Value>> availableValues;
    private List<Integer> randomFoundValueList;
    private final Random random = new Random();
    private List<String> issueList = new ArrayList<>();
    private LinkedHashMap<String, Double> sortedIssueImpMap;
    private Reporter reporter;


    // Importance map
    public OppImpMap(Profile profile, Reporter reporter) {
        this.domain = profile.getDomain();
        for (String issue : domain.getIssues()) {
            issueList.add(issue);
        }
        this.reporter = reporter;
        renewMaps();
    }

    public boolean isCompromised(Bid bid, int numFirstBids, double minUtility){
        renewLists();

        List<Bid> sortedBids = estimatedProfile.getBids();

        if(sortedBids.size() == 0){
            return false;
        }

        // JUST TO TEST
        /*numFirstBids = sortedBids.size()-1;*/

        int firstStartIndex = (sortedBids.size()-1) - numFirstBids;

        if(firstStartIndex < 0){
            firstStartIndex = 0;
        }

        for(int bidIndex = firstStartIndex; bidIndex < sortedBids.size(); bidIndex++){
            Bid currentBid = sortedBids.get(bidIndex);
            for (String issue : currentBid.getIssues()) {
                List<OppImpUnit> currentIssueList = issueValueImpMap.get(issue);
                for (OppImpUnit currentUnit : currentIssueList) {
                    if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
                        if(!availableValues.get(issue).contains(currentBid.getValue(issue))){
                            availableValues.get(issue).add(currentBid.getValue(issue));
                        }
                        break;
                    }
                }
            }
        }

		/*reporter.log( Level.INFO, "availableValues:"+ availableValues);
		reporter.log( Level.INFO, "forbiddenValues:"+ forbiddenValues);*/

        double issueChangeLoss = 1.0 / domain.getIssues().size();
        int changeRest = (int)((1 - minUtility) / issueChangeLoss) + 1;

        if(changeRest > domain.getIssues().size()){
            changeRest = domain.getIssues().size();
        }

        int changedIssueBest = 0;
        int changedIssueWorst = 0;
        int changedNotAvailable = 0;


        Set<Map.Entry<String, Double>> sortedIssueMapSet = sortedIssueImpMap.entrySet();
        ArrayList<Map.Entry<String, Double>> sortedIssueArrList = new ArrayList<Map.Entry<String, Double>>(sortedIssueMapSet);

        for (int i = 0; i < sortedIssueArrList.size(); i++) {
            String issue = sortedIssueArrList.get(i).getKey();
            List<Value> availableIssueValueList = availableValues.get(issue);
            if(i < (sortedIssueArrList.size() + 1)/2){
                if(!maxImpBid.getValue(issue).equals(bid.getValue(issue))){
                    if(!availableIssueValueList.contains(bid.getValue(issue))){
                        changedNotAvailable++;
                    }
                    else{
                        changedIssueWorst++;
                    }
                }
            }
            else{
                if(!maxImpBid.getValue(issue).equals(bid.getValue(issue))){
                    if(!availableIssueValueList.contains(bid.getValue(issue))){
                        changedNotAvailable++;
                    }
                    else{
                        changedIssueBest++;
                    }
                }
            }
        }
        int changeRestBest = changeRest / 2;
        int changeRestWorst = (changeRest / 2) + (changeRest % 2);

        reporter.log( Level.INFO, "OPP Bid: "+ bid + " OPP MAX BID: " + maxImpBid);
        reporter.log( Level.INFO, "OPP changeRestWorst: "+ changeRestWorst + " OPP changeRestBest: " + changeRestBest);
        reporter.log( Level.INFO, "OPP changedIssueBest: "+ changedIssueBest + " OPP changedIssueWorst: "+ changedIssueWorst + " OPP changedNotAvailable: "+ changedNotAvailable);

        if((changedIssueBest + changedNotAvailable) <= changeRestBest){
            if((changedIssueWorst +  2 *  changedNotAvailable + changedIssueBest) <= (changeRestBest + changeRestWorst)){
                reporter.log( Level.INFO, "OPP NOT COMPROMISED");
                return false;
            }
        }
        reporter.log( Level.INFO, "OPP COMPROMISED");
        return true;
    }

    public void update(OppSimpleLinearOrdering estimatedProfile) {

        renewMaps();

        this.estimatedProfile = estimatedProfile;

        if(estimatedProfile.getBids().size() == 0){
            return;
        }

        /*reporter.log( Level.INFO, " Given Bids:  "+ estimatedProfile.getBids() );*/


        List<Bid> sortedBids = estimatedProfile.getBids();

        for(int bidIndex = 0; bidIndex < sortedBids.size(); bidIndex++){
            Bid currentBid = sortedBids.get(bidIndex);
            double bidImportance = estimatedProfile.getUtility(currentBid).doubleValue();
            for (String issue : currentBid.getIssues()) {
                List<OppImpUnit> currentIssueList = issueValueImpMap.get(issue);
                for (OppImpUnit currentUnit : currentIssueList) {
                    if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
                        currentUnit.importanceList.add(bidImportance);
                        break;
                    }
                }
            }
        }

        HashMap<String, Value> maxBidMap = new HashMap<>();

        this.maxImpBid = sortedBids.get(sortedBids.size() - 1);

        /* Generate max bid by occurance (not working well)*/
        /*
        for (String issue : issueImpMap.keySet()) {
            List<OppImpUnit> currentIssueList = issueValueImpMap.get(issue);
            Value maxCountValue = estimatedProfile.getBids().get(estimatedProfile.getBids().size()-1).getValue(issue);
            int maxcount = 0;
            for (OppImpUnit currentUnit : currentIssueList) {
                List<Double> importanceListFiltered = currentUnit.importanceList.stream()
                        .filter(u -> u <= (sortedBids.size() - 1) * 0.25).collect(Collectors.toList());
                if(importanceListFiltered.size() > maxcount){
                    maxcount = importanceListFiltered.size();
                    maxCountValue = currentUnit.valueOfIssue;
                }
            }
            maxBidMap.put(issue, maxCountValue);
        }

        this.maxImpBid = new Bid(maxBidMap);
        */

        for (String issue : issueImpMap.keySet()) {
            List<Double> issueValAvgList = new ArrayList<>();
            List<OppImpUnit> currentIssueList = issueValueImpMap.get(issue);
            double issueImp = 0.0;
            for (OppImpUnit currentUnit : currentIssueList) {
                if(currentUnit.importanceList.size() != 0){
                    for (int i = 0; i < currentUnit.importanceList.size(); i++) {
                        double issueUnitImp = currentUnit.importanceList.get(i);
                        if(i != currentUnit.importanceList.size() - 1){
                            double nextIssueUnitImp = currentUnit.importanceList.get(i + 1);
                            if(nextIssueUnitImp != issueUnitImp){
                                issueImp -= 1;
                            }
                        }
                    }
                }
            }
            issueImpMap.put(issue, issueImp);
        }

        sortedIssueImpMap = sortByValue(issueImpMap);

    }

    private void renewMaps(){
        issueValueImpMap = new HashMap<>();
        issueImpMap = new HashMap<>();
        // Create empty importance map
        for (String issue : domain.getIssues()) {
            issueImpMap.put(issue, 0.0);
            ValueSet values = domain.getValues(issue);
            List<OppImpUnit> issueOppImpUnit = new ArrayList<>();
            for (Value value : values) {
                issueOppImpUnit.add(new OppImpUnit(value));
            }
            issueValueImpMap.put(issue, issueOppImpUnit);
        }
    }

    private void renewLists(){
        availableValues = new HashMap<>();
        // Create empty map
        for (String issue : domain.getIssues()) {
            availableValues.put(issue, new ArrayList<>());
        }
    }

    public Bid leastKnownBidGenerator(AllBidsList allbids) throws IOException {

        HashMap<String, Value> leastKnownBidValues= new HashMap<>();
        for (String issue : issueImpMap.keySet()) {
            List<OppImpUnit> currentIssueList = issueValueImpMap.get(issue);
            Value leastKnownIssueValue = null;
            int minCount = Integer.MAX_VALUE;
            for (OppImpUnit currentUnit : currentIssueList) {
                if (currentUnit.importanceList.size() < minCount) {
                    minCount = currentUnit.importanceList.size();
                    leastKnownIssueValue = currentUnit.valueOfIssue;
                }
            }
            leastKnownBidValues.put(issue, leastKnownIssueValue);
        }

        for (Bid bid : allbids) {
            boolean flag = true;
            for (String issue : issueImpMap.keySet()) {
                if (!bid.getValue(issue).equals(leastKnownBidValues.get(issue))){
                    flag = false;
                    break;
                }
            }
            if(flag == true) {
                return bid;
            }
        }
        return null;
    }

    private LinkedHashMap<String, Double> sortByValue(HashMap<String, Double> hm)
    {
        List<Map.Entry<String, Double> > list =
                new LinkedList<Map.Entry<String, Double> >(hm.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Double> >() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2)
            {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });
        LinkedHashMap<String, Double> temp = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }
}
