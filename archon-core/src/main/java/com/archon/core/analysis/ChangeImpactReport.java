package com.archon.core.analysis;

import com.archon.core.graph.Edge;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Combined diff + impact + risk report for a branch change.
 */
public class ChangeImpactReport {
    private final String baseRef;
    private final String headRef;
    private final Set<String> changedClasses;
    private final GraphDiff graphDiff;
    private final Map<String, String> changedClassDomains;
    private final List<ImpactResult.ImpactNode> impactedNodes;
    private final RiskSummary riskSummary;

    public ChangeImpactReport(String baseRef, String headRef,
                               Set<String> changedClasses,
                               GraphDiff graphDiff,
                               Map<String, String> changedClassDomains,
                               List<ImpactResult.ImpactNode> impactedNodes,
                               RiskSummary riskSummary) {
        this.baseRef = baseRef;
        this.headRef = headRef;
        this.changedClasses = Collections.unmodifiableSet(changedClasses);
        this.graphDiff = graphDiff;
        this.changedClassDomains = Collections.unmodifiableMap(changedClassDomains);
        this.impactedNodes = Collections.unmodifiableList(impactedNodes);
        this.riskSummary = riskSummary;
    }

    public String getBaseRef() { return baseRef; }
    public String getHeadRef() { return headRef; }
    public Set<String> getChangedClasses() { return changedClasses; }
    public GraphDiff getGraphDiff() { return graphDiff; }
    public Map<String, String> getChangedClassDomains() { return changedClassDomains; }
    public List<ImpactResult.ImpactNode> getImpactedNodes() { return impactedNodes; }
    public RiskSummary getRiskSummary() { return riskSummary; }
}
