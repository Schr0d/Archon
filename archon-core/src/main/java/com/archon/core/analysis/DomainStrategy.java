package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * Strategy interface for language-specific domain assignment.
 * Plugins return Optional.empty() to use fallback pivot detection.
 *
 * <p>Implementations must map module IDs to domain names based on
 * language-specific conventions (Java packages, JS workspaces, etc.).
 */
@FunctionalInterface
public interface DomainStrategy {
    /**
     * Assign domains to modules based on language-specific heuristics.
     *
     * @param graph The dependency graph (may be used for analysis)
     * @param sourceModules All module IDs discovered during parsing
     * @return Map of module ID to domain name, or Optional.empty() to use fallback
     */
    Optional<Map<String, String>> assignDomains(
        DependencyGraph graph,
        Set<String> sourceModules
    );
}
