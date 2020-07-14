package negotiator.group3.impmap;

import geniusweb.actions.Accept;
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

public class SimilarityMap {

	private Domain domain;
	private HashMap<String, List<IssueValueUnit>> issueValueImpMap;
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
	public SimilarityMap(Profile profile, Reporter reporter) {
		this.domain = profile.getDomain();
		for (String issue : domain.getIssues()) {
			issueList.add(issue);
		}
		this.reporter = reporter;
		renewMaps();
	}

	private void createConditionLists(int numFirstBids, int numLastBids){
		renewLists();

		List<Bid> sortedBids = estimatedProfile.getBids();

		int firstStartIndex = (sortedBids.size()-1) - numFirstBids;

		if(firstStartIndex <= 0){
			firstStartIndex = 1;
		}

		for(int bidIndex = firstStartIndex; bidIndex < sortedBids.size(); bidIndex++){
			Bid currentBid = sortedBids.get(bidIndex);
			for (String issue : currentBid.getIssues()) {
				List<IssueValueUnit> currentIssueList = issueValueImpMap.get(issue);
				for (IssueValueUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
						if(!availableValues.get(issue).contains(currentBid.getValue(issue))){
							availableValues.get(issue).add(currentBid.getValue(issue));
						}
						break;
					}
				}
			}
		}

		if(numLastBids >= sortedBids.size()){
			numLastBids = sortedBids.size() - 1;
		}

		for(int bidIndex = 0; bidIndex < numLastBids; bidIndex++){
			Bid currentBid = sortedBids.get(bidIndex);
			for (String issue : currentBid.getIssues()) {
				List<IssueValueUnit> currentIssueList = issueValueImpMap.get(issue);
				for (IssueValueUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
						if(!forbiddenValues.get(issue).contains(currentBid.getValue(issue)) && !availableValues.get(issue).contains(currentBid.getValue(issue))){
							forbiddenValues.get(issue).add(currentBid.getValue(issue));
						}
						break;
					}
				}
			}
		}
	}

	public boolean isCompatibleWithSimilarity(Bid bid, int numFirstBids, int numLastBids, double minUtility, String callType){
		createConditionLists(numFirstBids, numLastBids);

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

			if(!maxImpBid.getValue(issue).equals(bid.getValue(issue))){
				if (allAvailablesForbidden == false && forbiddenValues.get(issue).contains(bid.getValue(issue))){
					return false;
				}
				if(!availableIssueValueList.contains(bid.getValue(issue))){
					changedNotAvailable++;
				}
				else if(i < (sortedIssueArrList.size() + 1)/2){
					changedIssueWorst++;
				}
				else{
					changedIssueBest++;
				}
			}
		}
		int changeRestBest = changeRest / 2;
		int changeRestWorst = (changeRest / 2) + (changeRest % 2);

		if(callType.equals("ACCEPT")){
			//reporter.log( Level.INFO, "CHECKED ACCEPTION Bid: "+ bid + " MAX BID: " + maxImpBid);
			//reporter.log( Level.INFO, "changedIssueBest: "+ changedIssueBest + " changedIssueWorst: "+ changedIssueWorst + " changedNotAvailable: "+ changedNotAvailable);
		}

		changedIssueBest += changedNotAvailable;
		changedIssueWorst += changedNotAvailable;

		//TODO check if logic is true
		int exceedBestBidNum =  changedIssueBest - changeRestBest;
		if(exceedBestBidNum > 0){
			int equivalentWorstBidNum = exceedBestBidNum * 2;
			changedIssueBest -= exceedBestBidNum;
			changedIssueWorst += equivalentWorstBidNum;
		}

		int exceedWorstBidNum =  changedIssueWorst - changeRestWorst;
		if(exceedWorstBidNum > 0){
			int equivalentBestBidNum = (exceedWorstBidNum + 1) / 2;
			changedIssueWorst -= exceedWorstBidNum;
			changedIssueBest += equivalentBestBidNum;
		}

		if(changedIssueBest <= changeRestBest && changedIssueWorst <= changeRestWorst){
			if(callType.equals("OFFER")){
				//reporter.log( Level.INFO, "OFFERED Bid: "+ bid + " MAX BID: " + maxImpBid);
				//reporter.log( Level.INFO, "changedIssueBest: "+ changedIssueBest + " changedIssueWorst: "+ changedIssueWorst + " changedNotAvailable: "+ changedNotAvailable);
			}
			return true;
		}
		return false;
	}

	public Bid findBidCompatibleWithSimilarity(int numFirstBids, int numLastBids, double minUtility, Bid oppMaxbid){
		//reporter.log(Level.INFO, "<AhBuNe>: Trying to find bid to offer");
		createConditionLists(numFirstBids, numLastBids);
		//reporter.log(Level.INFO, "<AhBuNe>: Condition Lists Created");

		double issueChangeLoss = 1.0 / domain.getIssues().size();
		int changeRest = (int)((1 - minUtility) / issueChangeLoss) + 1;

		if(changeRest > domain.getIssues().size()){
			changeRest = domain.getIssues().size();
		}

		int changeRestBest = changeRest / 2;
		int changeRestWorst = (changeRest / 2) + (changeRest % 2);

		Set<Map.Entry<String, Double>> sortedIssueMapSet = sortedIssueImpMap.entrySet();
		ArrayList<Map.Entry<String, Double>> sortedIssueArrList = new ArrayList<Map.Entry<String, Double>>(sortedIssueMapSet);
		HashMap<String, Value> createdBid = new HashMap<>();
		for(int i = 0; i< sortedIssueMapSet.size(); i++){
			String issue = sortedIssueArrList.get(i).getKey();
			createdBid.put(issue, this.maxImpBid.getValue(issue));
		}

		//reporter.log(Level.INFO, "<AhBuNe>: Initial Values Assigned");
		int selectOppValueCount = 0;
		while(!(changeRestWorst == 0 && changeRestBest == 0)){
			//reporter.log(Level.INFO, "<AhBuNe>: changeRestWorst: " + changeRestWorst + " changeRestBest: " + changeRestBest);
			//reporter.log(Level.INFO, "<AhBuNe>: While");
			int notAvailableChance = Math.min(changeRestWorst, changeRestBest);
			int bestIssueStartIndex = (sortedIssueArrList.size() + 1)/2;
			int randIssue = random.nextInt(sortedIssueArrList.size());
			if((randIssue < bestIssueStartIndex && changeRestWorst != 0) ||(randIssue >= bestIssueStartIndex && changeRestBest != 0)){
				String issue = sortedIssueArrList.get(randIssue).getKey();
				boolean allAvailablesForbidden = true;
				for(Value issueValue: this.availableValues.get(issue)){
					if(!this.forbiddenValues.get(issue).contains(issueValue)){
						allAvailablesForbidden = false;
					}
				}
				List<Value> availableIssueValueList = availableValues.get(issue);
				List<Value> forbiddenIssueValueList = forbiddenValues.get(issue);
				List<IssueValueUnit> allIssueValues = issueValueImpMap.get(issue);
				//reporter.log(Level.INFO, "<AhBuNe>: allIssueValues.size(): " + allIssueValues.size());

				Value randomIssueValue;
				int randIssueValueIndex = random.nextInt(allIssueValues.size());
				if (selectOppValueCount < 1000 && oppMaxbid != null){
					randomIssueValue = oppMaxbid.getValue(issue);
					selectOppValueCount++;
				}
				else{
					randomIssueValue = allIssueValues.get(randIssueValueIndex).valueOfIssue;
				}
				if(allAvailablesForbidden == false){
					while(forbiddenIssueValueList.contains(randomIssueValue)){
						//reporter.log(Level.INFO, "<AhBuNe>: ForbiddenList: " + forbiddenIssueValueList);
						//reporter.log(Level.INFO, "<AhBuNe>: Available List: " + availableIssueValueList);
						randIssueValueIndex = random.nextInt(allIssueValues.size());
						randomIssueValue = allIssueValues.get(randIssueValueIndex).valueOfIssue;
					}
					//reporter.log(Level.INFO, "<AhBuNe>: SELECTED VALUE: "+ randomIssueValue);
				}

				boolean selectValue = false;
				//reporter.log(Level.INFO, "<AhBuNe>: bestIssueStartIndex: " + bestIssueStartIndex + " randIssue: " + randIssue + " changeRestWorst: " + changeRestWorst);

				if(!availableIssueValueList.contains(randomIssueValue)){
					//reporter.log(Level.INFO, "<AhBuNe>: not available ");
					if(notAvailableChance != 0){
						//reporter.log(Level.INFO, "<AhBuNe>: not available chance is used");
						changeRestWorst --;
						changeRestBest --;
						selectValue = true;
					}
				}
				else if(randIssue < bestIssueStartIndex){
					//reporter.log(Level.INFO, "<AhBuNe>: randIssue < bestIssueStartIndex ");
					if(changeRestWorst != 0){
						changeRestWorst--;
						selectValue = true;
					}
				}
				else if(changeRestBest != 0){
					changeRestBest--;
					selectValue = true;
				}
				if(selectValue){
					createdBid.put(issue, randomIssueValue);
					//reporter.log(Level.INFO, "<AhBuNe>: VALUE SELECTED: " + selectValue);
				}
			}
		}
		// TODO CHECK THE FAILURE
		//reporter.log(Level.INFO, "<AhBuNe>: Offered Bid: " + createdBid);
		return new Bid(createdBid);
	}



	public void update(SimpleLinearOrdering estimatedProfile) {
		renewMaps();

		this.estimatedProfile = estimatedProfile;

		//reporter.log( Level.INFO, " Given Bids:  "+ estimatedProfile.getBids() );


		List<Bid> sortedBids = estimatedProfile.getBids();

		this.maxImpBid = sortedBids.get(sortedBids.size() - 1);
		this.minImpBid = sortedBids.get(0);

		for(int bidIndex = 0; bidIndex < sortedBids.size(); bidIndex++){
			Bid currentBid = sortedBids.get(bidIndex);
			double bidImportance = estimatedProfile.getUtility(currentBid).doubleValue();
			for (String issue : currentBid.getIssues()) {
				List<IssueValueUnit> currentIssueList = issueValueImpMap.get(issue);
				for (IssueValueUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
						currentUnit.importanceList.add(bidImportance);
						break;
					}
				}
			}
		}

		for (String issue : issueImpMap.keySet()) {
			List<Double> issueValAvgList = new ArrayList<>();
			List<IssueValueUnit> currentIssueList = issueValueImpMap.get(issue);
			for (IssueValueUnit currentUnit : currentIssueList) {
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
			List<IssueValueUnit> issueIssueValueUnit = new ArrayList<>();
			for (Value value : values) {
				issueIssueValueUnit.add(new IssueValueUnit(value));
			}
			issueValueImpMap.put(issue, issueIssueValueUnit);
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
			List<IssueValueUnit> currentIssueList = issueValueImpMap.get(issue);
			Value leastKnownIssueValue = null;
			int minCount = Integer.MAX_VALUE;
			for (IssueValueUnit currentUnit : currentIssueList) {
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
		List<Map.Entry<String, Double> > list = new LinkedList<>(hm.entrySet());
		Collections.sort(list, Comparator.comparing(Map.Entry::getValue));
		LinkedHashMap<String, Double> temp = new LinkedHashMap<>();
		for (Map.Entry<String, Double> aa : list) {
			temp.put(aa.getKey(), aa.getValue());
		}
		return temp;
	}

}

