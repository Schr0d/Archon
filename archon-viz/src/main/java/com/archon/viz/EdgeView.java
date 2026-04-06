package com.archon.viz;

/**
 * A simplified view of a dependency edge for visualization.
 * Contains essential information needed for rendering connections between nodes.
 */
public record EdgeView(
    String source,             // Source node ID
    String target,             // Target node ID
    String confidence,         // Confidence level: HIGH, MEDIUM, LOW (parser-based graphs are always HIGH)
    int dependencyCount        // Number of dependencies (always 1 for individual edges)
) {
    public EdgeView {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Source cannot be null or blank");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Target cannot be null or blank");
        }
        if (confidence == null || confidence.isBlank()) {
            confidence = "HIGH";
        }
        if (dependencyCount < 1) {
            throw new IllegalArgumentException("Dependency count must be at least 1");
        }
    }
}