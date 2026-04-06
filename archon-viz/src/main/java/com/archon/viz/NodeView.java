package com.archon.viz;

/**
 * A simplified view of a dependency graph node for visualization.
 * Contains essential information needed for rendering in the web interface.
 */
public record NodeView(
    String id,                  // Unique identifier (e.g., "java:com.example.UserService")
    String type,                // Node type: CLASS, MODULE, PACKAGE, etc.
    String domain,              // Domain grouping: service, persistence, auth, etc.
    int dependentCount,         // Number of nodes that depend on this node (incoming edges)
    int dependencyCount         // Number of nodes this node depends on (outgoing edges)
) {
    public NodeView {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Node ID cannot be null or blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Node type cannot be null or blank");
        }
        if (domain == null) {
            domain = "ungrouped";
        }
        if (dependentCount < 0) {
            throw new IllegalArgumentException("Dependent count cannot be negative");
        }
        if (dependencyCount < 0) {
            throw new IllegalArgumentException("Dependency count cannot be negative");
        }
    }
}