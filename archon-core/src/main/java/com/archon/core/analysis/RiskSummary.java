package com.archon.core.analysis;

import com.archon.core.graph.RiskLevel;

import java.util.Collections;
import java.util.Map;

/**
 * Summary of risk assessment across a change set.
 */
public class RiskSummary {
    private final RiskLevel overallRisk;
    private final int newCycleCount;
    private final int crossDomainEdgeChanges;
    private final int criticalPathHits;
    private final Map<String, RiskLevel> perClassRisk;

    public RiskSummary(RiskLevel overallRisk, int newCycleCount,
                       int crossDomainEdgeChanges, int criticalPathHits,
                       Map<String, RiskLevel> perClassRisk) {
        this.overallRisk = overallRisk;
        this.newCycleCount = newCycleCount;
        this.crossDomainEdgeChanges = crossDomainEdgeChanges;
        this.criticalPathHits = criticalPathHits;
        this.perClassRisk = Collections.unmodifiableMap(perClassRisk);
    }

    public RiskLevel getOverallRisk() { return overallRisk; }
    public int getNewCycleCount() { return newCycleCount; }
    public int getCrossDomainEdgeChanges() { return crossDomainEdgeChanges; }
    public int getCriticalPathHits() { return criticalPathHits; }
    public Map<String, RiskLevel> getPerClassRisk() { return perClassRisk; }
}
