package geniusweb.sampleagent.impmap;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.profile.Profile;

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
	HashMap<String, List<ImpUnit>> issueValueImpMap = new HashMap<>();
	HashMap<String,Integer> issueImpMap = new HashMap<>();

	// Importance map
	public ImpMap(Profile profile) {
		super();
		this.domain = profile.getDomain();
		// Create empty my import map and opponent's value map
		for (String issue : domain.getIssues()) {
			issueImpMap.put(issue, 0);
			ValueSet values = domain.getValues(issue);
			List<ImpUnit> issueImpUnit = new ArrayList<>();
			for (Value value : values) {
				issueImpUnit.add(new ImpUnit(value));
			}
			issueValueImpMap.put(issue, issueImpUnit);
		}
	}


	public void update(List<Bid> bidOrdering) {
		int currentWeight = 0;
		for (Bid bid : bidOrdering) {
			currentWeight += 1;
			for (String issue : bid.getIssues()) {
				List<ImpUnit> currentIssueList = issueValueImpMap.get(issue);
				for (ImpUnit currentUnit : currentIssueList) {
					if (currentUnit.valueOfIssue.toString()
							.equals(bid.getValue(issue).toString())) {
						currentUnit.weightSum += currentWeight;
						currentUnit.count += 1;
						break;
					}
				}
			}
		}
		// Calculate weights
		for (List<ImpUnit> impUnitList : issueValueImpMap.values()) {
			for (ImpUnit currentUnit : impUnitList) {
				if (currentUnit.count == 0) {
					currentUnit.meanWeightSum = 0.0;
				} else {
					currentUnit.meanWeightSum = (double) currentUnit.weightSum
							/ (double) currentUnit.count;
				}
			}
		}
		// Sort
		for (List<ImpUnit> impUnitList : issueValueImpMap.values()) {
			impUnitList.sort(new impUnit.meanWeightSumComparator());
		}
		// Find the minimum
		double minMeanWeightSum = Double.POSITIVE_INFINITY;
		for (Map.Entry<String, List<impUnit>> entry : issueValueImpMap.entrySet()) {
			double tempMeanWeightSum = entry.getValue()
					.get(entry.getValue().size() - 1).meanWeightSum;
			if (tempMeanWeightSum < minMeanWeightSum) {
				minMeanWeightSum = tempMeanWeightSum;
			}
		}
		// Minus all values
		for (List<ImpUnit> impUnitList : issueValueImpMap.values()) {
			for (ImpUnit currentUnit : impUnitList) {
				currentUnit.meanWeightSum -= minMeanWeightSum;
			}
		}
	}


	public double getImportance(Bid bid) {
		double bidImportance = 0.0;
		for (String issue : bid.getIssues()) {
			Value value = bid.getValue(issue);
			double valueImportance = 0.0;
			for (ImpUnit i : issueValueImpMap.get(issue)) {
				if (i.valueOfIssue.equals(value)) {
					valueImportance = issueImpMap.get(issue) * i.importanceWeight;
					break;
				}
			}
			bidImportance += valueImportance;
		}
		return bidImportance;
	}
}

