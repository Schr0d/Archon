package com.archon.core.config;

import com.archon.core.graph.DependencyGraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.archon.core.analysis.Thresholds;

/**
 * Evaluates architecture rules against a dependency graph.
 * Consumes pre-computed analysis results (cycles, domain map).
 */
public class RuleValidator {

    public List<RuleViolation> validate(DependencyGraph graph, ArchonConfig config,
                                         Map<String, String> domainMap,
                                         List<List<String>> cycles) {
        List<RuleViolation> violations = new ArrayList<>();

        if (config.isNoCycle()) {
            checkNoCycle(cycles, violations);
        }

        checkMaxCrossDomain(graph, config, domainMap, violations, config.getMaxCrossDomain());
        checkMaxCallDepth(graph, config, violations);
        checkForbidCoreEntityLeakage(graph, config, domainMap, violations);

        return violations;
    }

    /**
     * Overload that uses adaptive thresholds from ThresholdCalculator.
     */
    public List<RuleViolation> validate(DependencyGraph graph, ArchonConfig config,
                                         Map<String, String> domainMap,
                                         List<List<String>> cycles,
                                         Thresholds thresholds) {
        List<RuleViolation> violations = new ArrayList<>();

        if (config.isNoCycle()) {
            checkNoCycle(cycles, violations);
        }

        checkMaxCrossDomain(graph, config, domainMap, violations, thresholds.getCrossDomainMax());
        checkMaxCallDepth(graph, config, violations);
        checkForbidCoreEntityLeakage(graph, config, domainMap, violations);

        return violations;
    }

    private void checkNoCycle(List<List<String>> cycles, List<RuleViolation> violations) {
        for (List<String> cycle : cycles) {
            violations.add(new RuleViolation(
                "no_cycle",
                "ERROR",
                "Cycle detected: " + String.join(" -> ", cycle) + " -> " + cycle.get(0)
            ));
        }
    }

    private void checkMaxCrossDomain(DependencyGraph graph, ArchonConfig config,
                                      Map<String, String> domainMap,
                                      List<RuleViolation> violations,
                                      int maxCrossDomain) {
        for (String nodeId : graph.getNodeIds()) {
            Set<String> crossDomains = new HashSet<>();
            for (String dependent : graph.getDependents(nodeId)) {
                String depDomain = domainMap.get(dependent);
                String nodeDomain = domainMap.get(nodeId);
                if (depDomain != null && nodeDomain != null && !depDomain.equals(nodeDomain)) {
                    crossDomains.add(depDomain);
                }
            }
            if (crossDomains.size() > maxCrossDomain) {
                violations.add(new RuleViolation(
                    "max_cross_domain",
                    "WARNING",
                    nodeId + " has dependents from " + crossDomains.size()
                        + " domains (max: " + maxCrossDomain + "): "
                        + String.join(", ", crossDomains)
                ));
            }
        }
    }

    private void checkMaxCallDepth(DependencyGraph graph, ArchonConfig config,
                                    List<RuleViolation> violations) {
        int maxDepth = config.getMaxCallDepth();
        for (String nodeId : graph.getNodeIds()) {
            int depth = computeMaxDepth(graph, nodeId, new HashSet<>());
            if (depth > maxDepth) {
                violations.add(new RuleViolation(
                    "max_call_depth",
                    "WARNING",
                    nodeId + " has a dependency chain of depth " + depth
                        + " (max: " + maxDepth + ")"
                ));
            }
        }
    }

    private int computeMaxDepth(DependencyGraph graph, String nodeId, Set<String> visited) {
        if (visited.contains(nodeId)) {
            return 0;
        }
        visited.add(nodeId);
        int maxChildDepth = 0;
        for (String dependent : graph.getDependents(nodeId)) {
            maxChildDepth = Math.max(maxChildDepth, computeMaxDepth(graph, dependent, visited));
        }
        visited.remove(nodeId);
        return 1 + maxChildDepth;
    }

    private void checkForbidCoreEntityLeakage(DependencyGraph graph, ArchonConfig config,
                                               Map<String, String> domainMap,
                                               List<RuleViolation> violations) {
        for (String entity : config.getForbidCoreEntityLeakage()) {
            if (!graph.containsNode(entity)) {
                continue;
            }
            String entityDomain = domainMap.get(entity);
            if (entityDomain == null) {
                continue;
            }
            Set<String> leakDomains = new HashSet<>();
            for (String dependent : graph.getDependents(entity)) {
                String depDomain = domainMap.get(dependent);
                if (depDomain != null && !depDomain.equals(entityDomain)) {
                    leakDomains.add(depDomain);
                }
            }
            if (!leakDomains.isEmpty()) {
                violations.add(new RuleViolation(
                    "forbid_core_entity_leakage",
                    "ERROR",
                    entity + " leaks to domains: " + String.join(", ", leakDomains)
                ));
            }
        }
    }
}
