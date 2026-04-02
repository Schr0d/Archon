package com.archon.core.analysis;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns domain labels to nodes based on package conventions and config overrides.
 * Three-tier resolution: config match (HIGH) -> package convention (MEDIUM) -> fallback (LOW).
 */
public class DomainDetector {

    public DomainResult assignDomains(DependencyGraph graph, Map<String, List<String>> domainMappings) {
        Map<String, String> domains = new LinkedHashMap<>();
        Map<String, Confidence> confidence = new LinkedHashMap<>();

        for (String nodeId : graph.getNodeIds()) {
            Resolution res = resolveDomain(nodeId, domainMappings);
            domains.put(nodeId, res.domain);
            confidence.put(nodeId, res.confidence);
        }

        return new DomainResult(domains, confidence);
    }

    private Resolution resolveDomain(String nodeId, Map<String, List<String>> domainMappings) {
        // Tier 1: config override - exact package prefix match
        for (Map.Entry<String, List<String>> entry : domainMappings.entrySet()) {
            for (String prefix : entry.getValue()) {
                if (nodeId.startsWith(prefix)) {
                    return new Resolution(entry.getKey(), Confidence.HIGH);
                }
            }
        }

        // Tier 2: package convention - extract meaningful segment
        String[] parts = nodeId.split("\\.");
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
