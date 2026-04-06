package com.archon.viz;

import java.util.List;

/**
 * Represents a group of related nodes in the dependency graph.
 * Used to organize nodes by domain, package, or other grouping criteria.
 */
public record NodeGroup(
    String label,         // Human-readable label for the group (e.g., "service", "persistence")
    List<NodeView> nodes  // Nodes belonging to this group
) {
    public NodeGroup {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Group label cannot be null or blank");
        }
        if (nodes == null) {
            nodes = List.of();
        }
    }
}