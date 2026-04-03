package com.archon.java;

import com.archon.core.analysis.DomainStrategy;
import com.archon.core.graph.DependencyGraph;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Domain assignment strategy for Java packages.
 *
 * <p>Uses the third segment of the package name as the domain identifier:
 * <ul>
 *   <li>com.example.service.FooService → domain: "service"</li>
 *   <li>com.example.repository.UserRepository → domain: "repository"</li>
 *   <li>Single-segment names (Foo) → domain: "default"</li>
 * </ul>
 *
 * <p>Also handles namespace prefixes by stripping the "java:" prefix if present.
 */
public class JavaDomainStrategy implements DomainStrategy {

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
     * Extracts domain identifier from a Java fully-qualified class name.
     *
     * <p>Strategy: Third segment of package name (index 2).
     * Single-segment names → "default".
     * "java:" prefix is stripped before processing.
     *
     * @param className the fully-qualified class name (e.g., "com.example.service.FooService")
     * @return the domain identifier (e.g., "service")
     */
    private String extractDomain(String className) {
        // Remove leading "java:" prefix if present
        String cleanName = className.startsWith("java:") ?
            className.substring(5) : className;

        // Split by package segments
        String[] segments = cleanName.split("\\.");

        // Third segment is the domain (index 2)
        if (segments.length >= 3) {
            return segments[2];
        }

        // Single segment or insufficient structure
        return "default";
    }
}
