package negotiator.group3.impmap;

import geniusweb.bidspace.AllBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.profile.Profile;
import negotiator.group3.linearorder.SimpleLinearOrdering;
import tudelft.utilities.logging.Reporter;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class ImpMap {

	private Domain domain;
	private HashMap<String, List<ImpUnit>> issueValueImpMap;
	private HashMap<String,Double> issueImpMap;
	private SimpleLinearOrdering estimatedProfile;
	private Bid maxImpBid;
	private Bid minImpBid;
	private HashMap<String, List<Value>> availableValues;
	private HashMap<String, List<Value>> forbiddenValues;
	private List<Integer> randomFoundValueList;
	private final Random random = new Random();
	private List<String> issueList = new ArrayList<>();
	private LinkedHashMap<String, Double> sortedIssueImpMap;
	private Reporter reporter;


	// Importance map
	public ImpMap(Profile profile, Reporter reporter) {
		this.domain = profile.getDomain();
		for (String issue : domain.getIssues()) {
			issueList.add(issue);
		}
		this.reporter = reporter;
		renewMaps();
	}

	public boolean isCompatibleWithSimilarity(Bid bid, int numFirstBids, int numLastBids, double minUtility, String callType){
		renewLists();

		List<Bid> sortedBids = estimatedProfile.getBids();

		// JUST TO TEST
		/*numFirstBids = sortedBids.size()-1;*/

		int firstStartIndex = (sortedBids.size()-1) - numFirstBids;

		if(firstStartIndex < 0){
			firstStartIndex = 0;
		}

		for(int bidIndex = firstStartIndex; bidIndex < sortedBids.size(); bidIndex++){
			Bid currentBid = sortedBids.get(bidIndex);
			for (String issue : currentBid.getIssues()) {
				List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
				for (ImpUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
						if(!availableValues.get(issue).contains(currentBid.getValue(issue))){
							availableValues.get(issue).add(currentBid.getValue(issue));
						}
						break;
					}
				}
			}
		}

		// JUST TO TEST
		/*numLastBids = 1;*/

		if(numLastBids > sortedBids.size()){
			numFirstBids = sortedBids.size();
		}

		for(int bidIndex = 0; bidIndex < numLastBids; bidIndex++){
			Bid currentBid = sortedBids.get(bidIndex);
			for (String issue : currentBid.getIssues()) {
				List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
				for (ImpUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
						if(!forbiddenValues.get(issue).contains(currentBid.getValue(issue)) && !availableValues.get(issue).contains(currentBid.getValue(issue))){
							forbiddenValues.get(issue).add(currentBid.getValue(issue));
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

			boolean allAvailablesForbidden = true;
			for(Value issueValue: this.availableValues.get(issue)){
				if(!this.forbiddenValues.get(issue).contains(issueValue)){
					allAvailablesForbidden = false;
				}
			}

			List<Value> availableIssueValueList = availableValues.get(issue);

			if(allAvailablesForbidden == false){
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
					if (forbiddenValues.get(issue).contains(bid.getValue(issue))){
						return false;
					}
					else if(!maxImpBid.getValue(issue).equals(bid.getValue(issue))){
						if(!availableIssueValueList.contains(bid.getValue(issue))){
							changedNotAvailable++;
						}
						else{
							changedIssueBest++;
						}
					}
				}
			}

			else{
				if(!maxImpBid.getValue(issue).equals(bid.getValue(issue))){
					if(!availableValues.get(issue).contains(bid.getValue(issue))){
						changedNotAvailable++;
					}
					else if(i < (sortedIssueArrList.size() + 1)/2){
						changedIssueWorst++;
					}
					else{
						changedIssueBest ++;
					}
				}
			}

		}
		int changeRestBest = changeRest / 2;
		int changeRestWorst = (changeRest / 2) + (changeRest % 2);

		if(callType.equals("ACCEPT")){
			reporter.log( Level.INFO, "CHECKED ACCEPTION Bid: "+ bid + " MAX BID: " + maxImpBid);
			reporter.log( Level.INFO, "changedIssueBest: "+ changedIssueBest + " changedIssueWorst: "+ changedIssueWorst + " changedNotAvailable: "+ changedNotAvailable);
		}

		if((changedIssueBest + changedNotAvailable) <= changeRestBest){
			if((changedIssueWorst + /* 2 * */ changedNotAvailable + changedIssueBest) <= (changeRestBest + changeRestWorst)){
				if(callType.equals("OFFER")){
					reporter.log( Level.INFO, "OFFERED Bid: "+ bid + " MAX BID: " + maxImpBid);
					reporter.log( Level.INFO, "changedIssueBest: "+ changedIssueBest + " changedIssueWorst: "+ changedIssueWorst + " changedNotAvailable: "+ changedNotAvailable);
				}
				return true;

			}
		}
		return false;
	}


	public Bid foundCompatibleWithSimilarity(int numFirstBids, int numLastBids, double minUtility){
		renewLists();

		List<Bid> sortedBids = estimatedProfile.getBids();

		// JUST TO TEST
		/*numFirstBids = sortedBids.size()-1;*/

		for(int bidIndex = (sortedBids.size()-1) - numFirstBids; bidIndex < sortedBids.size(); bidIndex++){
			Bid currentBid = sortedBids.get(bidIndex);
			for (String issue : currentBid.getIssues()) {
				List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
				for (ImpUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
						if(!availableValues.get(issue).contains(currentBid.getValue(issue))){
							availableValues.get(issue).add(currentBid.getValue(issue));
						}
						break;
					}
				}
			}
		}

		// JUST TO TEST
		/*numLastBids = 1;*/

		for(int bidIndex = 0; bidIndex < numLastBids; bidIndex++){
			Bid currentBid = sortedBids.get(bidIndex);
			for (String issue : currentBid.getIssues()) {
				List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
				for (ImpUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
						if(!forbiddenValues.get(issue).contains(currentBid.getValue(issue)) && !availableValues.get(issue).contains(currentBid.getValue(issue))){
							forbiddenValues.get(issue).add(currentBid.getValue(issue));
						}
						break;
					}
				}
			}
		}

		double issueChangeLoss = 1.0 / domain.getIssues().size();
		int changeRest = (int)((1 - minUtility) / issueChangeLoss) + 1;

		if(changeRest > domain.getIssues().size()){
			changeRest = domain.getIssues().size();
		}

		Set<Map.Entry<String, Double>> sortedIssueMapSet = sortedIssueImpMap.entrySet();
		ArrayList<Map.Entry<String, Double>> sortedIssueArrList = new ArrayList<Map.Entry<String, Double>>(sortedIssueMapSet);

		HashMap<String, Value> generatedBidMap = new HashMap<>();


		/*reporter.log( Level.INFO, "KNOWN BIDS:"+ estimatedProfile.getBids().size());*/

		ArrayList<Integer> changeList = new ArrayList<>();

		for(int i = 0; i < changeRest; i++){
			int randNum = random.nextInt((sortedIssueArrList.size() + 1)/2);
			if(i % 2 == 0 ){
				changeList.add(randNum);
			}
			else{
				changeList.add(sortedIssueArrList.size() - 1 - randNum);
			}
		}

		for (int i = 0; i < sortedIssueArrList.size(); i++) {
			String issue = sortedIssueArrList.get(i).getKey();
			/*reporter.log( Level.INFO, "Sorted Issues "+ i + " " + issue + " Dev: "+ sortedIssueArrList.get(i).getValue() );*/

			if(!changeList.contains(i)){
				generatedBidMap.put(issue, maxImpBid.getValue(issue));
			}
			else{
				boolean allAvailablesForbidden = true;
				for(Value issueValue: this.availableValues.get(issue)){
					if(!this.forbiddenValues.get(issue).contains(issueValue)){
						allAvailablesForbidden = false;
					}
				}

				List<Value> availableIssueValueList = availableValues.get(issue);
				int randNum = random.nextInt(availableIssueValueList.size());
				if(allAvailablesForbidden == false){
					while (true){
						randNum = random.nextInt(availableIssueValueList.size());
						if(i < (sortedIssueArrList.size() + 1)/2){
							break;
						}
						else if(!this.forbiddenValues.get(issue).contains(availableIssueValueList.get(randNum))){
							break;
						}
					}
				}
				generatedBidMap.put(issue, availableIssueValueList.get(randNum));

			}
		}

		/*reporter.log( Level.INFO, "availableValues "+ availableValues);
		reporter.log( Level.INFO, "forbiddenValues "+ forbiddenValues);*/

		return new Bid(generatedBidMap);
	}

	public void update(SimpleLinearOrdering estimatedProfile) {
		renewMaps();

		this.estimatedProfile = estimatedProfile;

		reporter.log( Level.INFO, " Given Bids:  "+ estimatedProfile.getBids() );


		List<Bid> sortedBids = estimatedProfile.getBids();

		this.maxImpBid = sortedBids.get(sortedBids.size() - 1);
		this.minImpBid = sortedBids.get(0);

		for(int bidIndex = 0; bidIndex < sortedBids.size(); bidIndex++){
			Bid currentBid = sortedBids.get(bidIndex);
			double bidImportance = estimatedProfile.getUtility(currentBid).doubleValue();
			for (String issue : currentBid.getIssues()) {
				List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
				for (ImpUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
						currentUnit.importanceList.add(bidImportance);
						break;
					}
				}
			}
		}

		for (String issue : issueImpMap.keySet()) {
			List<Double> issueValAvgList = new ArrayList<>();
			List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
			for (ImpUnit currentUnit : currentIssueList) {
				if(currentUnit.importanceList.size() != 0){
					double issueValueAvg = 0.0;
					for (double IssueUnitImp : currentUnit.importanceList) {
						issueValueAvg += IssueUnitImp;
					}
					issueValueAvg /= currentUnit.importanceList.size();
					issueValAvgList.add(issueValueAvg);
				}
			}


			/*reporter.log( Level.INFO, issue + " VAL AVG List "+ issueValAvgList );
			reporter.log( Level.INFO, issue + " STDEV "+ stdev(issueValAvgList));*/

			issueImpMap.put(issue, stdev(issueValAvgList));
		}

		sortedIssueImpMap = sortByValue(issueImpMap);

	}

	private double stdev (List<Double> arr)
	{
		double sum = 0.0;
		for(int i = 0; i< arr.size(); i++){
			sum += arr.get(i);
		}
		double mean = sum / arr.size();
		sum = 0;
		for (int i = 0; i < arr.size(); i++)
		{
			double val = arr.get(i);
			sum += Math.pow(val - mean, 2);
		}
		double meanOfDiffs = sum / (double) (arr.size());
		return Math.sqrt(meanOfDiffs);
	}

	private void renewMaps(){
		issueValueImpMap = new HashMap<>();
		issueImpMap = new HashMap<>();
		// Create empty importance map
		for (String issue : domain.getIssues()) {
			issueImpMap.put(issue, 0.0);
			ValueSet values = domain.getValues(issue);
			List<ImpUnit> issueImpUnit = new ArrayList<>();
			for (Value value : values) {
				issueImpUnit.add(new ImpUnit(value));
			}
			issueValueImpMap.put(issue, issueImpUnit);
		}
	}

	private void renewLists(){
		availableValues = new HashMap<>();
		forbiddenValues = new HashMap<>();
		// Create empty maps
		for (String issue : domain.getIssues()) {
			availableValues.put(issue, new ArrayList<>());
			forbiddenValues.put(issue, new ArrayList<>());
		}
	}

	public Bid leastKnownBidGenerator(AllBidsList allbids) throws IOException {

		HashMap<String, Value> leastKnownBidValues= new HashMap<>();
		for (String issue : issueImpMap.keySet()) {
			List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
			Value leastKnownIssueValue = null;
			int minCount = Integer.MAX_VALUE;
			for (ImpUnit currentUnit : currentIssueList) {
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

