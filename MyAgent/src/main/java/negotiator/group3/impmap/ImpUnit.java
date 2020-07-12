package negotiator.group3.impmap;

import geniusweb.issuevalue.Value;

import java.util.ArrayList;
import java.util.List;

public class ImpUnit {
	public Value valueOfIssue;
	public double importanceWeight = 0;
	public int count = 0;

	public List<Double> importanceList = new ArrayList<>();

	public ImpUnit(Value value) {
		this.valueOfIssue = value;
	}

	public String toString() {
		return String.format("%s %f", valueOfIssue, importanceWeight);
	}

}
