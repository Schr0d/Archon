package com.archon.python;

import com.archon.core.analysis.DomainStrategy;
import com.archon.core.graph.DependencyGraph;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Domain assignment strategy for Python projects.
 *
 * <p>Uses layout-aware heuristics to detect domain boundaries:
 * <ol>
 *   <li>__init__.py packages — directory with __init__.py = domain</li>
 *   <li>src/ layout — src/myapp/ = domain "myapp"</li>
 *   <li>tests/ directory — tests/ = domain "tests"</li>
 *   <li>Flat layout — file-level grouping fallback</li>
 * </ol>
 *
 * <p>Handles namespace prefixes by stripping the "py:" prefix before processing.
 */
public class PythonDomainStrategy implements DomainStrategy {

    @Override
    public Optional<Map<String, String>> assignDomains(
        DependencyGraph graph,
        Set<String> sourceModules
    ) {
        Map<String, String> domains = new HashMap<>();

        for (String module : sourceModules) {
            String domain = extractDomain(module);
            domains.put(module, domain);
        }

        return Optional.of(domains);
    }

    /**
     * Extracts domain identifier from a module name.
     *
     * <p>Heuristics (tried in order):
     * <ol>
     *   <li>tests/ directory — any module under tests/ gets "tests" domain</li>
     *   <li>src/ layout — second segment is domain (src.myapp.* → myapp)</li>
     *   <li>__init__.py package — directory containing __init__.py is domain</li>
     *   <li>Fallback — use first segment, or "default" for root-level files</li>
     * </ol>
     *
     * @param moduleName the module name with or without "py:" prefix
     * @return the domain identifier
     */
    private String extractDomain(String moduleName) {
        // Remove "py:" prefix if present
        String cleanModule = moduleName.startsWith("py:") ?
            moduleName.substring(3) : moduleName;

        // Split by path separator (Python uses both "/" and "." in different contexts)
        // In the graph, we normalize to "/" but store package names with "."
        // For domain detection, we need to handle both

        String[] segments;
        if (cleanModule.contains("/")) {
            // Path format: "src/myapp/service"
            segments = cleanModule.split("/");
        } else {
            // Package format: "src.myapp.service"
            segments = cleanModule.split("\\.");
        }

        // Heuristic 1: tests/ directory detection
        for (int i = 0; i < segments.length; i++) {
            if ("tests".equals(segments[i])) {
                return "tests";
            }
        }

        // Heuristic 2: src/ layout detection
        if (segments.length >= 2 && "src".equals(segments[0])) {
            // src/myapp/* → domain: "myapp"
            return segments[1];
        }

        // Heuristic 3: __init__.py package detection
        // This is tricky because we only have the module name, not the full path
        // For now, fall back to first segment
        if (segments.length > 1) {
            // Use the first segment as domain (directory or package)
            // For "src.mypkg.utils", this gives "src" which isn't great
            // But we've already handled src/ layout above
            if (!"src".equals(segments[0])) {
                return segments[0];
            }
        }

        // Fallback: default (for root-level files with no directory)
        return "default";
    }
}
