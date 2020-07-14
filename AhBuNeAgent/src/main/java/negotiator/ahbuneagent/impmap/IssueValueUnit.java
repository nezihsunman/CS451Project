package negotiator.ahbuneagent.impmap;

import geniusweb.issuevalue.Value;

import java.util.ArrayList;
import java.util.List;

public class IssueValueUnit {
	public Value valueOfIssue;
	public List<Double> importanceList = new ArrayList<>();

	public IssueValueUnit(Value value) {
		this.valueOfIssue = value;
	}
}
