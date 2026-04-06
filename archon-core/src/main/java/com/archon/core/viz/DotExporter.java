package com.archon.core.viz;

import com.archon.core.graph.DependencyGraph;

import java.util.Map;

/**
 * Exports dependency graph to Graphviz DOT format.
 */
public class DotExporter {

    /**
     * Export a dependency graph to DOT format.
     *
     * @param graph The dependency graph to export
     * @return DOT format string
     */
    public String export(DependencyGraph graph) {
        return export(graph, Map.of());
    }

    /**
     * Export a dependency graph to DOT format with domain coloring.
     *
     * @param graph The dependency graph to export
     * @param domainMap Map of node IDs to domain names for coloring
     * @return DOT format string
     */
    public String export(DependencyGraph graph, Map<String, String> domainMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph dependencies {\n");
        sb.append("  rankdir=LR;\n");
        sb.append("  node [shape=box style=filled];\n");

        // Export nodes
        for (String nodeId : graph.getNodeIds()) {
            String domain = domainMap.getOrDefault(nodeId, "");
            String label = getNodeLabel(nodeId);
            String color = domain.isEmpty() ? "white" : hashColor(domain);
            sb.append("  \"").append(nodeId).append("\" ")
                .append("[label=\"").append(label).append("\" ")
                .append("fillcolor=\"").append(color).append("\"];\n");
        }

        // Export edges
        for (String source : graph.getNodeIds()) {
            for (String target : graph.getDependencies(source)) {
                sb.append("  \"").append(source).append("\" -> \"").append(target).append("\";\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Export a dependency graph to DOT format with domain grouping using subgraphs.
     *
     * @param graph The dependency graph to export
     * @param domainMap Map of node IDs to domain names
     * @return DOT format string with subgraph clustering
     */
    public String exportWithSubgraphs(DependencyGraph graph, Map<String, String> domainMap) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph dependencies {\n");
        sb.append("  rankdir=LR;\n");
        sb.append("  node [shape=box style=filled];\n");

        // Group nodes by domain
        java.util.Map<String, java.util.List<String>> domains = new java.util.LinkedHashMap<>();
        for (String nodeId : graph.getNodeIds()) {
            String domain = domainMap.getOrDefault(nodeId, "ungrouped");
            domains.computeIfAbsent(domain, k -> new java.util.ArrayList<>()).add(nodeId);
        }

        // Export each domain as a subgraph
        for (java.util.Map.Entry<String, java.util.List<String>> entry : domains.entrySet()) {
            String domain = entry.getKey();
            String color = hashColor(domain);
            sb.append("  subgraph \"cluster_").append(domain).append("\" {\n");
            sb.append("    label=\"").append(domain).append("\";\n");
            sb.append("    style=filled;\n");
            sb.append("    color=").append(color).append(";\n");
            sb.append("    fillcolor=").append(color).append(";\n");
            sb.append("    penwidth=2;\n");

            for (String nodeId : entry.getValue()) {
                String label = getNodeLabel(nodeId);
                sb.append("    \"").append(nodeId).append("\" ")
                    .append("[label=\"").append(label).append("\"];\n");
            }

            sb.append("  }\n");
        }

        // Export edges (outside subgraphs)
        for (String source : graph.getNodeIds()) {
            for (String target : graph.getDependencies(source)) {
                sb.append("  \"").append(source).append("\" -> \"").append(target).append("\";\n");
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Extract a short label from the node ID.
     * For fully-qualified class names, uses just the class name.
     * For modules, uses the full path.
     */
    private String getNodeLabel(String nodeId) {
        int lastDot = nodeId.lastIndexOf('.');
        if (lastDot > 0 && Character.isUpperCase(nodeId.charAt(lastDot + 1))) {
            // Looks like a Java class name - use just the class name
            return nodeId.substring(lastDot + 1);
        }
        // For modules or other identifiers, use last path segment
        int lastSlash = nodeId.lastIndexOf('/');
        if (lastSlash > 0) {
            return nodeId.substring(lastSlash + 1);
        }
        return nodeId;
    }

    /**
     * Generate a consistent color hash from a string.
     * Returns a hex color code with pastel colors (high lightness).
     */
    private String hashColor(String domain) {
        int hash = domain.hashCode();
        // Pastel colors: high base values with smaller variance
        int r = 180 + Math.abs(hash % 60);
        int g = 180 + Math.abs((hash >> 8) % 60);
        int b = 180 + Math.abs((hash >> 16) % 60);
        // Use String.format to ensure 2-digit hex values with leading zeros
        return String.format("#%02x%02x%02x", r, g, b);
    }
}
