package com.archon.core.analysis;

import com.archon.core.graph.Confidence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Result of domain detection: maps each node to its detected domain and confidence.
 */
public class DomainResult {
    private final Map<String, String> domains;
    private final Map<String, Confidence> confidence;

    public DomainResult(Map<String, String> domains, Map<String, Confidence> confidence) {
        this.domains = Collections.unmodifiableMap(new LinkedHashMap<>(domains));
        this.confidence = Collections.unmodifiableMap(new LinkedHashMap<>(confidence));
    }

    public Map<String, String> getDomains() {
        return domains;
    }

    public Optional<String> getDomain(String nodeId) {
        return Optional.ofNullable(domains.get(nodeId));
    }

    public Confidence getConfidence(String nodeId) {
        return confidence.getOrDefault(nodeId, Confidence.LOW);
    }

    public int size() {
        return domains.size();
    }
}