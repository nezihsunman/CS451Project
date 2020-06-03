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
	HashMap<String, List<ImpUnit>> issueValueImpMap;
	HashMap<String,Double> issueImpMap;

	// Importance map
	public ImpMap(Profile profile) {
		this.domain = profile.getDomain();
		renewMaps();
	}

	public void update(SimpleLinearOrdering estimatedProfile) {
		renewMaps();

		List<Bid> sortedBids = estimatedProfile.getBids();

		for(int bidIndex = 0; bidIndex < sortedBids.size(); bidIndex++){
			Bid currentBid = sortedBids.get(bidIndex);
			Bid nextBid = null;
			if(bidIndex < sortedBids.size() - 1)
				nextBid = sortedBids.get(bidIndex + 1);
			// If bid is send by opponent closer to the start time, then importance is set to high
			double bidImportance = estimatedProfile.getUtility(currentBid).doubleValue();
			for (String issue : currentBid.getIssues()) {

				if(nextBid != null){
					if(currentBid.getValue(issue).equals(nextBid.getValue(issue)))
						issueImpMap.put(issue, issueImpMap.get(issue) + bidImportance);
				}
				else
					issueImpMap.put(issue, issueImpMap.get(issue) + bidImportance);

				List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
				for (ImpUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
						currentUnit.importanceWeight += bidImportance;
						currentUnit.count += 1;
						break;
					}
				}
			}
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

	public double getImportance(Bid bid) {

		double bidImportance = 0.0;
		double sumIssueImp = 0.0;
		for (String issue : bid.getIssues()) {
			sumIssueImp += issueImpMap.get(issue);
		}
		for (String issue : bid.getIssues()){
			List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
			for (ImpUnit currentUnit : currentIssueList) {
				if (currentUnit.valueOfIssue.equals(bid.getValue(issue))) {
					bidImportance += (issueImpMap.get(issue)/sumIssueImp) * currentUnit.importanceWeight;
					break;
				}
			}
		}
		return bidImportance;
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

