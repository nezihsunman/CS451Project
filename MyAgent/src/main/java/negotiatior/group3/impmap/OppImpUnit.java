package negotiatior.group3.impmap;

import geniusweb.issuevalue.Value;

public class OppImpUnit {
	public Value valueOfIssue;
	public double importanceWeight = 0;

	public OppImpUnit(Value value) {
		this.valueOfIssue = value;
	}

	public String toString() {
		return String.format("%s %d", valueOfIssue, importanceWeight);
	}
}
