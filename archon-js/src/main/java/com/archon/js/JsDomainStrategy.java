package com.archon.js;

import com.archon.core.analysis.DomainStrategy;
import com.archon.core.graph.DependencyGraph;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Domain assignment strategy for JavaScript/TypeScript projects.
 *
 * <p>Uses package.json workspaces to detect monorepo domains:
 * <ul>
 *   <li>packages/ui/src/components/Button -> domain: "ui"</li>
 *   <li>packages/app/src/pages/Home -> domain: "app"</li>
 * </ul>
 *
 * <p>For non-monorepo projects, uses the top-level directory:
 * <ul>
 *   <li>src/components/Header -> domain: "src"</li>
 *   <li>lib/utils/format -> domain: "lib"</li>
 * </ul>
 *
 * <p>Handles both namespaced (js:src/...) and non-namespaced module IDs.
 */
public class JsDomainStrategy implements DomainStrategy {

    @Override
    public Optional<Map<String, String>> assignDomains(
        DependencyGraph graph,
        Set<String> sourceModules
    ) {
        Map<String, String> domains = new HashMap<>();

        for (String module : sourceModules) {
            // Strip "js:" prefix if present
            String cleanModule = module.startsWith("js:") ?
                module.substring(3) : module;

            String domain = extractDomain(cleanModule);
            domains.put(module, domain);
        }

        return Optional.of(domains);
    }

    /**
     * Extract domain from module path.
     * Uses package.json workspace convention (packages/*) or top-level directory.
     */
    private String extractDomain(String modulePath) {
        String[] segments = modulePath.split("/");

        // Check for monorepo workspace pattern (packages/*)
        if (segments.length > 0 && "packages".equals(segments[0])) {
            // Second segment is the workspace name
            if (segments.length > 1) {
                return segments[1];
            }
        }

        // For non-monorepo, use the first segment as domain
        if (segments.length > 0) {
            return segments[0];
        }

        // Fallback
        return "default";
    }
}
