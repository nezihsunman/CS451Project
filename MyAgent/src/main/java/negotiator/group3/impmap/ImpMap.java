package negotiator.group3.impmap;

import geniusweb.bidspace.AllBidsList;
import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.profile.Profile;
import negotiator.group3.linearorder.SimpleLinearOrdering;

import java.io.IOException;
import java.util.*;

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


	// Importance map
	public ImpMap(Profile profile) {
		this.domain = profile.getDomain();
		for (String issue : domain.getIssues()) {
			issueList.add(issue);
		}
		renewMaps();
	}

	public boolean isCompatibleWithSimilarity(Bid bid, int numFirstBids, int numLastBids, double minUtility){
		renewLists();

		List<Bid> sortedBids = estimatedProfile.getBids();

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

		for(int bidIndex = 0; bidIndex < numLastBids; bidIndex++){
			Bid currentBid = sortedBids.get(bidIndex);
			for (String issue : currentBid.getIssues()) {
				List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
				for (ImpUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
						if(!forbiddenValues.get(issue).contains(currentBid.getValue(issue))){
							forbiddenValues.get(issue).add(currentBid.getValue(issue));
						}
						break;
					}
				}
			}
		}

		double issueChangeLoss = 1 / (2 * domain.getIssues().size());
		int changeRest = (int)((1 - minUtility) / issueChangeLoss);

		int changedBid = 0;

		for (String issue : bid.getIssues()) {
			if(!availableValues.get(issue).contains(bid.getValue(issue)) || forbiddenValues.get(issue).contains(bid.getValue(issue))){
				return false;
			}
			else if(!maxImpBid.getValue(issue).equals(bid.getValue(issue))){
				changedBid++;
			}
		}

		if(changedBid >= changeRest){
			return false;
		}
		return true;
	}

	public Bid foundCompatibleWithSimilarity(int numFirstBids, int numLastBids, double minUtility){
		renewLists();

		List<Bid> sortedBids = estimatedProfile.getBids();

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

		for(int bidIndex = 0; bidIndex < numLastBids; bidIndex++){
			Bid currentBid = sortedBids.get(bidIndex);
			for (String issue : currentBid.getIssues()) {
				List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
				for (ImpUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
						if(!forbiddenValues.get(issue).contains(currentBid.getValue(issue))){
							forbiddenValues.get(issue).add(currentBid.getValue(issue));
						}
						break;
					}
				}
			}
		}

		double issueChangeLoss = 1 / (2 * domain.getIssues().size());
		int changeRest = (int)((1 - minUtility) / issueChangeLoss);
		if(changeRest > domain.getIssues().size()){
			changeRest = domain.getIssues().size();
		}
		randomFoundValueList = new ArrayList<>();

		for(int i = 0; i< changeRest; i++){
			while(true){
				int randNum = random.nextInt(domain.getIssues().size());
				if(!randomFoundValueList.contains(randNum) /*&& availableValues.get(issueList.get(randNum)).size() > 1*/){
					randomFoundValueList.add(randNum);
					break;
				}
			}
		}

		HashMap<String, Value> generatedBidMap = new HashMap<>();

		for(int i = 0; i< domain.getIssues().size(); i++){
			String currentIssue = this.issueList.get(i);
			if(!randomFoundValueList.contains(i)){
				generatedBidMap.put(currentIssue, maxImpBid.getValue(currentIssue));
			}
			else{
				boolean allAvailablesForbidden = true;
				for(Value issueValue: this.availableValues.get(currentIssue)){
					if(!this.forbiddenValues.get(currentIssue).contains(issueValue)){
						allAvailablesForbidden = false;
					}
				}
				List<Value> availableIssueValueList = availableValues.get(currentIssue);
				int randNum = random.nextInt(availableIssueValueList.size());
				if(allAvailablesForbidden == false){
					while (true){
						randNum = random.nextInt(availableIssueValueList.size());
						if(!this.forbiddenValues.get(currentIssue).contains(availableIssueValueList.get(randNum))){
							break;
						}
					}
				}
				generatedBidMap.put(currentIssue, availableIssueValueList.get(randNum));
			}
		}

		return new Bid(generatedBidMap);
	}

	public void update(SimpleLinearOrdering estimatedProfile) {
		renewMaps();

		this.estimatedProfile = estimatedProfile;


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
						currentUnit.importanceWeight += bidImportance;
						currentUnit.count += 1;
						currentUnit.importanceList.add(bidImportance);
						break;
					}
				}
			}
		}

		for (String issue : issueImpMap.keySet()) {
			double issueImp = 0.0;
			List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
			for (ImpUnit currentUnit : currentIssueList) {
				if(currentUnit.count != 0){
					double issueValueImp = 0.0;
					double issueValueAvg = currentUnit.importanceWeight / currentUnit.count;
					for (double IssueUnitImp : currentUnit.importanceList) {
						issueValueImp += Math.pow((IssueUnitImp - issueValueAvg), 2);
					}
					issueValueImp /= currentUnit.count;
					issueImp += issueValueImp;
				}
			}

			if(issueImp != 0){
				issueImp = 1 / Math.sqrt(issueImp);
			}
			issueImpMap.put(issue, issueImp);
		}

		for (String issue : issueImpMap.keySet()) {
			List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
			double maxIssueValue = 0.0;
			for (ImpUnit currentUnit : currentIssueList) {
				if (currentUnit.importanceWeight > maxIssueValue) {
					maxIssueValue = currentUnit.importanceWeight;
				}
			}
			for (ImpUnit currentUnit : currentIssueList) {
				currentUnit.importanceWeight /= maxIssueValue;
			}
		}

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
		// Create empty importance map
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
				if (currentUnit.count < minCount) {
					minCount = currentUnit.count;
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


}

