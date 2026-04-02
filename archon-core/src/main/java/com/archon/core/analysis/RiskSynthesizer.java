package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.RiskLevel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Synthesizes risk across a change set by reusing RiskScorer per changed class.
 * Overall risk = max of per-class risks, bumped to VERY_HIGH if any new cycles exist.
 */
public class RiskSynthesizer {

    private final RiskScorer riskScorer = new RiskScorer();

    /**
     * Synthesize risk for a set of changed classes.
     *
     * @param headGraph       the head (current) dependency graph
     * @param domainMap       node-to-domain mapping
     * @param changedClasses  set of changed class FQCNs
     * @param graphDiff       the graph diff result
     * @param criticalPaths   list of critical path name fragments (from config)
     * @return RiskSummary with overall and per-class risk
     */
    public RiskSummary synthesize(DependencyGraph headGraph,
                                   Map<String, String> domainMap,
                                   Set<String> changedClasses,
                                   GraphDiff graphDiff,
                                   List<String> criticalPaths) {
        Map<String, RiskLevel> perClassRisk = new LinkedHashMap<>();

        for (String changedClass : changedClasses) {
            if (!headGraph.containsNode(changedClass)) continue;

            int inDegree = headGraph.getDependents(changedClass).size();
            String classDomain = domainMap.getOrDefault(changedClass, "");

            // Count cross-domain dependencies
            int crossDomainCount = 0;
            for (String dep : headGraph.getDependencies(changedClass)) {
                String depDomain = domainMap.getOrDefault(dep, "");
                if (!classDomain.isEmpty() && !depDomain.isEmpty()
                    && !classDomain.equals(depDomain)) {
                    crossDomainCount++;
                }
            }

            boolean onCriticalPath = isOnCriticalPath(changedClass, criticalPaths);

            // Check if this class is in any new cycle
            boolean inNewCycle = isInAnyCycle(changedClass, graphDiff.getNewCycles());

            RiskLevel risk = riskScorer.computeRisk(
                inDegree, crossDomainCount, 0, onCriticalPath, inNewCycle, false);
            perClassRisk.put(changedClass, risk);
        }

        // Count cross-domain edge changes
        int crossDomainEdgeChanges = countCrossDomainEdgeChanges(
            graphDiff.getAddedEdges(), graphDiff.getRemovedEdges(), domainMap);

        // Count critical path hits
        int criticalPathHits = countCriticalPathHits(changedClasses, criticalPaths);

        // Overall risk = max of per-class, bumped to VERY_HIGH if new cycles
        RiskLevel overallRisk = perClassRisk.values().stream()
            .max(Enum::compareTo)
            .orElse(RiskLevel.LOW);

        if (!graphDiff.getNewCycles().isEmpty()) {
            overallRisk = RiskLevel.VERY_HIGH;
        }

        return new RiskSummary(overallRisk, graphDiff.getNewCycles().size(),
            crossDomainEdgeChanges, criticalPathHits, perClassRisk);
    }

    private boolean isOnCriticalPath(String fqcn, List<String> criticalPaths) {
        for (String pattern : criticalPaths) {
            if (fqcn.toLowerCase().contains(pattern.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean isInAnyCycle(String fqcn, List<List<String>> cycles) {
        for (List<String> cycle : cycles) {
            if (cycle.contains(fqcn)) return true;
        }
        return false;
    }

    private int countCrossDomainEdgeChanges(Set<Edge> added, Set<Edge> removed,
                                             Map<String, String> domainMap) {
        int count = 0;
        for (Edge edge : added) {
            String srcDomain = domainMap.getOrDefault(edge.getSource(), "");
            String tgtDomain = domainMap.getOrDefault(edge.getTarget(), "");
            if (!srcDomain.isEmpty() && !tgtDomain.isEmpty() && !srcDomain.equals(tgtDomain)) {
                count++;
            }
        }
        for (Edge edge : removed) {
            String srcDomain = domainMap.getOrDefault(edge.getSource(), "");
            String tgtDomain = domainMap.getOrDefault(edge.getTarget(), "");
            if (!srcDomain.isEmpty() && !tgtDomain.isEmpty() && !srcDomain.equals(tgtDomain)) {
                count++;
            }
        }
        return count;
    }

    private int countCriticalPathHits(Set<String> changedClasses, List<String> criticalPaths) {
        int count = 0;
        for (String fqcn : changedClasses) {
            if (isOnCriticalPath(fqcn, criticalPaths)) count++;
        }
        return count;
    }
}
