package com.archon.core.analysis;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assigns domain labels to nodes based on package conventions and config overrides.
 * Three-tier resolution: config match (HIGH) -> pivot detection (MEDIUM) -> fallback (LOW).
 */
public class DomainDetector {

    public DomainResult assignDomains(DependencyGraph graph, Map<String, List<String>> domainMappings) {
        Map<String, String> domains = new LinkedHashMap<>();
        Map<String, Confidence> confidence = new LinkedHashMap<>();

        int pivotDepth = findPivotDepth(graph.getNodeIds());

        for (String nodeId : graph.getNodeIds()) {
            Resolution res = resolveDomain(nodeId, domainMappings, pivotDepth);
            domains.put(nodeId, res.domain);
            confidence.put(nodeId, res.confidence);
        }

        return new DomainResult(domains, confidence);
    }

    int findPivotDepth(Set<String> nodeIds) {
        int maxDepth = nodeIds.stream()
            .mapToInt(id -> id.split("\\.").length)
            .max().orElse(0);

        for (int depth = 2; depth < maxDepth; depth++) {
            Set<String> segments = new LinkedHashSet<>();
            for (String nodeId : nodeIds) {
                String[] parts = nodeId.split("\\.");
                if (parts.length > depth) {
                    segments.add(parts[depth]);
                }
            }
            if (segments.size() >= 3 && segments.size() <= 10) {
                return depth;
            }
        }
        return -1; // no pivot found
    }

    private Resolution resolveDomain(String nodeId, Map<String, List<String>> domainMappings,
                                     int pivotDepth) {
        // Tier 1: config override - exact package prefix match
        for (Map.Entry<String, List<String>> entry : domainMappings.entrySet()) {
            for (String prefix : entry.getValue()) {
                if (nodeId.startsWith(prefix)) {
                    return new Resolution(entry.getKey(), Confidence.HIGH);
                }
            }
        }

        // Tier 2: pivot detection — use auto-detected depth
        String[] parts = nodeId.split("\\.");
        if (pivotDepth >= 0 && parts.length > pivotDepth) {
            return new Resolution(parts[pivotDepth], Confidence.MEDIUM);
        }

        // Tier 2 fallback: old behavior (3rd segment for 4+ deep packages)
        if (parts.length >= 4) {
            return new Resolution(parts[2], Confidence.MEDIUM);
        }

        // Tier 3: top-level fallback
        if (parts.length >= 2) {
            return new Resolution(parts[0], Confidence.LOW);
        }

        return new Resolution("unknown", Confidence.LOW);
    }

    private record Resolution(String domain, Confidence confidence) {}
}
