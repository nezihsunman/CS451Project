package geniusweb.sampleagent.impmap;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.profile.Profile;
import geniusweb.sampleagent.linearorder.SimpleLinearOrdering;
import tudelft.utilities.logging.Reporter;

import java.lang.annotation.Repeatable;
import java.lang.reflect.Array;
import java.util.*;
import java.util.logging.Level;

import static java.lang.Math.pow;

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

		for(int bidIndex = 0; bidIndex < sortedBids.size(); bidIndex++){
			Bid currentBid = sortedBids.get(bidIndex);
			Bid nextBid = null;
			if(bidIndex < sortedBids.size() - 1)
				nextBid = sortedBids.get(bidIndex + 1);
			// if bid is send by oppenent closer to the start time, then importance is high
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
}

