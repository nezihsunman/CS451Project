package geniusweb.sampleagent.impmap;

import geniusweb.issuevalue.Bid;
import geniusweb.issuevalue.Domain;
import geniusweb.issuevalue.Value;
import geniusweb.issuevalue.ValueSet;
import geniusweb.profile.Profile;
import geniusweb.sampleagent.linearorder.OppSimpleLinearOrdering;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class OppImpMap {

        private Domain domain;
        HashMap<String, List<OppImpUnit>> issueValueImpMap;
        HashMap<String,Double> issueImpMap;

        // Importance map
        public OppImpMap(Profile profile) {
            this.domain = profile.getDomain();
            renewMaps();
        }


        public void update(OppSimpleLinearOrdering opponentEstimatedProfile) {
            renewMaps();
            List<Bid> opponentSortedBids = opponentEstimatedProfile.getSortedBids();
            for(int bidIndex = 0; bidIndex < opponentSortedBids.size(); bidIndex++){
                Bid currentBid = opponentSortedBids.get(bidIndex);
                Bid nextBid = null;
                if(bidIndex < opponentSortedBids.size() - 1)
                    nextBid = opponentSortedBids.get(bidIndex + 1);
                // if bid is send by opponent closer to the start time, then importance is high
                double bidImportance = opponentEstimatedProfile.getUtility(currentBid).doubleValue();
                for (String issue : currentBid.getIssues()) {

                    if(nextBid != null){
                        if(currentBid.getValue(issue).equals(nextBid.getValue(issue)))
                            issueImpMap.put(issue, issueImpMap.get(issue) + bidImportance);
                    }
                    else
                        issueImpMap.put(issue, issueImpMap.get(issue) + bidImportance);

                    List<OppImpUnit> currentIssueList = issueValueImpMap.get(issue);
                    for (OppImpUnit currentUnit : currentIssueList) {
                        if (currentUnit.valueOfIssue.equals(currentBid.getValue(issue))) {
                                currentUnit.importanceWeight += bidImportance;
                            break;
                        }
                    }
                }
            }
            for (String issue : issueImpMap.keySet()) {
                List<OppImpUnit> currentIssueList = issueValueImpMap.get(issue);
                double maxIssueValue = 0.0;
                for (OppImpUnit currentUnit : currentIssueList) {
                    if (currentUnit.importanceWeight > maxIssueValue) {
                        maxIssueValue = currentUnit.importanceWeight;
                    }
                }
                for (OppImpUnit currentUnit : currentIssueList) {
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
                List<OppImpUnit> issueImpUnit = new ArrayList<>();
                for (Value value : values) {
                    issueImpUnit.add(new OppImpUnit(value));
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
                List<OppImpUnit> currentIssueList = issueValueImpMap.get(issue);
                for (OppImpUnit currentUnit : currentIssueList) {
                    if (currentUnit.valueOfIssue.equals(bid.getValue(issue))) {
                        bidImportance += (issueImpMap.get(issue)/sumIssueImp) * currentUnit.importanceWeight;
                        break;
                    }
                }
            }
            return bidImportance;
        }
}
