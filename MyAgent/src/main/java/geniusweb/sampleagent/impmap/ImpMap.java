package geniusweb.sampleagent.impmap;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.profile.Profile;
import geniusweb.sampleagent.linearorder.SimpleLinearOrdering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Importance map. One is created for each party. The key (String) is the issue
 * and value is list of Importances of the values.
 *
 */
@SuppressWarnings("serial")
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

		for (String issue : issueImpMap.keySet()) {
			double issueImp = 0;
			List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
			for (ImpUnit currentUnit : currentIssueList) {
				//her bir issue value
				int prevIndex = -1;
				for(Bid currentBid: sortedBids){
					if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
						if(prevIndex == -1){
							prevIndex = sortedBids.indexOf(currentBid);
						}
						else{
							issueImp += sortedBids.indexOf(currentBid) - prevIndex;
							prevIndex = sortedBids.indexOf(currentBid);
						}
					}
				}
			}
			issueImpMap.put(issue, issueImp);
		}

		double maxImp = 0;
		for (String issue : issueImpMap.keySet()) {
			if(issueImpMap.get(issue) > maxImp)
				maxImp = issueImpMap.get(issue);
		}
		for (String issue : issueImpMap.keySet()) {
			issueImpMap.put(issue, maxImp - issueImpMap.get(issue)) ;
		}

		for(Bid currentBid: sortedBids){
			// if bid is closer to worse, than it is not important
			double bidImportance = estimatedProfile.getUtility(currentBid).doubleValue();
			for (String issue : currentBid.getIssues()) {
				List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
				for (ImpUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue
							.equals(currentBid.getValue(issue))) {
						currentUnit.importanceWeight += bidImportance;
						break;
					}
				}
			}
		}
	}

	private void renewMaps(){
		issueValueImpMap = new HashMap<>();
		issueImpMap = new HashMap<>();
		// Create empty my import map and opponent's value map
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
			double sumIssueValueImp = 0.0;
			List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
			for (ImpUnit currentUnit : currentIssueList) {
				sumIssueValueImp += currentUnit.importanceWeight;
			}
			for (ImpUnit currentUnit : currentIssueList) {
				if (currentUnit.valueOfIssue.equals(bid.getValue(issue))) {
					bidImportance += (issueImpMap.get(issue)/sumIssueImp) * (currentUnit.importanceWeight / sumIssueValueImp);
					break;
				}
			}
		}
		return bidImportance;
	}
}

