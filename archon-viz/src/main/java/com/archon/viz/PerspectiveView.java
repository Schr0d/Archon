package com.archon.viz;

import java.util.List;

/**
 * Represents a view of the dependency graph at a specific perspective level.
 * Used for fractal zoom visualization in the web interface.
 */
public record PerspectiveView(
    String focusId,           // The node/domain currently in focus (null for top-level domain view)
    int depth,                // Current depth level (1=domains, 2=classes, 3=methods)
    List<NodeGroup> groups,   // Grouped nodes by domain or other grouping
    List<EdgeView> edges,     // Edges between visible nodes
    List<String> children     // IDs of nodes/domains that can be drilled into
) {
    public PerspectiveView {
        if (depth < 1) {
            throw new IllegalArgumentException("Depth must be at least 1");
        }
        if (groups == null) {
            groups = List.of();
        }
        if (edges == null) {
            edges = List.of();
        }
        if (children == null) {
            children = List.of();
        }
    }
}