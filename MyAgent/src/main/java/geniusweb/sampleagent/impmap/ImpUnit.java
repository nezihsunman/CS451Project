package geniusweb.sampleagent.impmap;

import geniusweb.issuevalue.Value;

import java.util.Comparator;

/**
 * importance unit . Contains importance of a {@link Value} of some issue. The
 * values in this class are hard referenced and changed by the {@link ImpMap}.
 */
public class ImpUnit {
	public Value valueOfIssue;
	public int count = 0;
	public int importanceWeight = 0; // counts #occurences of this value.

	public ImpUnit(Value value) {
		this.valueOfIssue = value;
	}

	public String toString() {
		return String.format("%s %f", valueOfIssue, importanceWeight);
	}

	// Overriding the comparator interface
	static class meanWeightSumComparator implements Comparator<ImpUnit> {
		public int compare(ImpUnit o1, ImpUnit o2) {
			if (o1.importanceWeight < o2.importanceWeight) {
				return 1;
			} else if (o1.importanceWeight > o2.importanceWeight) {
				return -1;
			}
			return 0;
		}
	}

}
