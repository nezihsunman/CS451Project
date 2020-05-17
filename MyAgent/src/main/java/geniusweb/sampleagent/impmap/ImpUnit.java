package geniusweb.sampleagent.impmap;

import geniusweb.issuevalue.Value;

public class ImpUnit {
	public Value valueOfIssue;
	public double importanceWeight = 0;

	public ImpUnit(Value value) {
		this.valueOfIssue = value;
	}

	public String toString() {
		return String.format("%s %f", valueOfIssue, importanceWeight);
	}

}
