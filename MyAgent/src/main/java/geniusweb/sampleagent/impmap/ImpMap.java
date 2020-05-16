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

	Reporter reporter;


	// Importance map
	public ImpMap(Profile profile, Reporter reporter) {
		this.domain = profile.getDomain();
		this.reporter = reporter;
		renewMaps();
	}


	public void update(SimpleLinearOrdering estimatedProfile) {
		renewMaps();

		/*List<Bid> sortedBids = estimatedProfile.getBids();

		for (String issue : issueImpMap.keySet()) {
			double issueImp = 0;
			List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
			int issueValueCount = currentIssueList.size();
			for (ImpUnit currentUnit : currentIssueList) {
				int count = 0;
				//her bir issue value
				int prevIndex = -1;
				double tmpImp = 0.0;
				for(int bidIndex = 0; bidIndex < sortedBids.size(); bidIndex++){
					Bid currentBid = sortedBids.get(bidIndex);
					if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
						if(prevIndex == -1){
							prevIndex = bidIndex;
						}
						else{
							tmpImp += bidIndex - prevIndex;
							prevIndex = bidIndex;
							count++;
						}
					}
				}
				issueImp += tmpImp/count;
			}
			issueImp /= issueValueCount;
			issueImpMap.put(issue, 1/issueImp);
		}

		/*double maxImp = 0;
		double minImp = Double.MAX_VALUE;
		for (String issue : issueImpMap.keySet()) {
			if(issueImpMap.get(issue) > maxImp)
				maxImp = issueImpMap.get(issue);
			if(issueImpMap.get(issue) < minImp)
				minImp = issueImpMap.get(issue);
		}

		for (String issue : issueImpMap.keySet()) {
			issueImpMap.put(issue, maxImp + minImp - issueImpMap.get(issue)) ;
		} /*

		for(Bid currentBid: sortedBids){
			// if bid is closer to worse, than it is not important
			double bidImportance = estimatedProfile.getUtility(currentBid).doubleValue();
			for (String issue : currentBid.getIssues()) {
				List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
				for (ImpUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue
							.equals(currentBid.getValue(issue))) {
						currentUnit.importanceWeight += bidImportance;
						currentUnit.count += 1;
						break;
					}
				}
			}
		}*/

		List<Bid> sortedBids = estimatedProfile.getBids();

		for(int bidIndex = sortedBids.size()-4; bidIndex < sortedBids.size(); bidIndex++){
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
		/*double bidImportance = 0.0;
		double sumIssueImp = 0.0;
		for (String issue : bid.getIssues()) {
				sumIssueImp += issueImpMap.get(issue);
		}

		for (String issue : bid.getIssues()){
			double maxIssueValueImpAvg = 0.0;
			List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
			for (ImpUnit currentUnit : currentIssueList) {
				if(maxIssueValueImpAvg < currentUnit.importanceWeight/ currentUnit.count)
					maxIssueValueImpAvg = currentUnit.importanceWeight / currentUnit.count;
			}
			for (ImpUnit currentUnit : currentIssueList) {
				if (currentUnit.valueOfIssue.equals(bid.getValue(issue))) {
					bidImportance += ((issueImpMap.get(issue) / sumIssueImp)) * ((currentUnit.importanceWeight / currentUnit.count) / maxIssueValueImpAvg);
					break;
				}
			}
		}
		return bidImportance;*/


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
					reporter.log(Level.INFO, "&&&issueImp: " + issueImpMap.get(issue));
					reporter.log(Level.INFO, "&&&sumIssueImp: " + sumIssueImp);
					reporter.log(Level.INFO, "&&&currentUnitImp: " + currentUnit.importanceWeight);
					reporter.log(Level.INFO, "&&&sumIssueValueImp: " + sumIssueValueImp);

					bidImportance += (issueImpMap.get(issue)/sumIssueImp) * (currentUnit.importanceWeight / sumIssueValueImp);
					break;
				}
			}
		}
		return bidImportance;

	}
}

