package com.archon.core.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable dependency graph built from adjacency lists.
 * Construct via GraphBuilder; graph is frozen after build().
 *
 * Forward adjacency: who I depend on (outgoing edges)
 * Reverse adjacency: who depends on me (incoming edges)
 */
public class DependencyGraph {
    private final Map<String, Node> nodes;
    private final Map<String, Set<String>> forwardAdj;      // nodeId -> set of dependency nodeIds
    private final Map<String, Set<String>> reverseAdj;      // nodeId -> set of dependent nodeIds
    private final Map<String, Map<String, Edge>> edges;     // sourceId -> (targetId -> edge)
    private final boolean frozen;

    DependencyGraph(Map<String, Node> nodes,
                    Map<String, Set<String>> forwardAdj,
                    Map<String, Set<String>> reverseAdj,
                    Map<String, Map<String, Edge>> edges) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.forwardAdj = freezeAdj(forwardAdj);
        this.reverseAdj = freezeAdj(reverseAdj);
        this.edges = freezeEdges(edges);
        this.frozen = true;
    }

    // --- Lookups ---

    public Optional<Node> getNode(String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    public Set<String> getNodeIds() {
        return Collections.unmodifiableSet(nodes.keySet());
    }

    public int nodeCount() {
        return nodes.size();
    }

    /**
     * Returns the set of nodes that `id` depends on (outgoing edges).
     */
    public Set<String> getDependencies(String id) {
        return forwardAdj.getOrDefault(id, Collections.emptySet());
    }

    /**
     * Returns the set of nodes that depend on `id` (incoming edges).
     */
    public Set<String> getDependents(String id) {
        return reverseAdj.getOrDefault(id, Collections.emptySet());
    }

    public Optional<Edge> getEdge(String source, String target) {
        Map<String, Edge> targets = edges.get(source);
        if (targets == null) return Optional.empty();
        return Optional.ofNullable(targets.get(target));
    }

    public int edgeCount() {
        return edges.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * Returns all edges in the graph.
     */
    public Set<Edge> getAllEdges() {
        Set<Edge> result = new LinkedHashSet<>();
        for (Map<String, Edge> targets : edges.values()) {
            result.addAll(targets.values());
        }
        return result;
    }

    public boolean containsNode(String id) {
        return nodes.containsKey(id);
    }

    // --- Internal helpers ---

    private static Map<String, Set<String>> freezeAdj(Map<String, Set<String>> adj) {
        Map<String, Set<String>> frozen = new LinkedHashMap<>();
        adj.forEach((k, v) -> frozen.put(k, Collections.unmodifiableSet(new LinkedHashSet<>(v))));
        return Collections.unmodifiableMap(frozen);
    }

    private static Map<String, Map<String, Edge>> freezeEdges(Map<String, Map<String, Edge>> edges) {
        Map<String, Map<String, Edge>> frozen = new LinkedHashMap<>();
        edges.forEach((src, targets) ->
            frozen.put(src, Collections.unmodifiableMap(new LinkedHashMap<>(targets))));
        return Collections.unmodifiableMap(frozen);
    }

    /**
     * Strips language namespace prefixes from all node IDs and edge references.
     * <p>
     * Converts "java:com.example.Foo" -&gt; "com.example.Foo".
     * This is used by ParseOrchestrator and DiffCommand to unify multi-plugin graphs
     * where each plugin adds nodes with language-prefixed IDs (e.g., "java:", "js:").
     *
     * @param prefixedBuilder a builder containing nodes with prefixed IDs
     * @return a new graph with unprefixed node IDs
     */
    public static DependencyGraph stripNamespacePrefixesAndBuild(MutableBuilder prefixedBuilder) {
        DependencyGraph prefixedGraph = prefixedBuilder.build();

        Map<String, String> idMapping = new LinkedHashMap<>();
        Map<String, String> unprefixedToPrefixed = new LinkedHashMap<>(); // Track collisions

        for (String nodeId : prefixedGraph.getNodeIds()) {
            String unprefixedId = stripNamespacePrefix(nodeId);

            // Detect namespace collisions (Fix #1: throw exception instead of silent data loss)
            String existingPrefixed = unprefixedToPrefixed.get(unprefixedId);
            if (existingPrefixed != null) {
                throw new IllegalStateException(
                    "Namespace collision: both '" + existingPrefixed + "' and '" + nodeId +
                    "' map to '" + unprefixedId + "'. Use disambiguation strategy or rename nodes."
                );
            }

            idMapping.put(nodeId, unprefixedId);
            unprefixedToPrefixed.put(unprefixedId, nodeId);
        }

        MutableBuilder finalBuilder = new MutableBuilder();

        for (String prefixedId : prefixedGraph.getNodeIds()) {
            String unprefixedId = idMapping.get(prefixedId);
            if (unprefixedId == null) {
                continue; // Skip nodes that were involved in collisions
            }

            Node prefixedNode = prefixedGraph.getNode(prefixedId).orElseThrow();

            Node.Builder nodeBuilder = Node.builder()
                .id(unprefixedId)
                .type(prefixedNode.getType())
                .sourcePath(prefixedNode.getSourcePath().orElse(null))
                .confidence(prefixedNode.getConfidence());

            prefixedNode.getDomain().ifPresent(nodeBuilder::domain);
            prefixedNode.getTags().forEach(nodeBuilder::addTag);

            finalBuilder.addNode(nodeBuilder.build());
        }

        for (Edge prefixedEdge : prefixedGraph.getAllEdges()) {
            String unprefixedSource = idMapping.get(prefixedEdge.getSource());
            String unprefixedTarget = idMapping.get(prefixedEdge.getTarget());

            if (unprefixedSource != null && unprefixedTarget != null) {
                Edge edge = Edge.builder()
                    .source(unprefixedSource)
                    .target(unprefixedTarget)
                    .type(prefixedEdge.getType())
                    .confidence(prefixedEdge.getConfidence())
                    .dynamic(prefixedEdge.isDynamic())
                    .evidence(prefixedEdge.getEvidence())
                    .build();

                finalBuilder.addEdge(edge);
            }
        }

        return finalBuilder.build();
    }

    /**
     * Strips the language namespace prefix from a node ID.
     * <p>
     * Examples:
     * <ul>
     *   <li>"java:com.example.Foo" -&gt; "com.example.Foo"</li>
     *   <li>"js:src/components/Header" -&gt; "src/components/Header"</li>
     *   <li>"com.example.Bar" -&gt; "com.example.Bar" (no prefix, unchanged)</li>
     * </ul>
     *
     * @param nodeId the node ID, possibly with a language prefix
     * @return the node ID without the language prefix
     */
    private static String stripNamespacePrefix(String nodeId) {
        int colonIndex = nodeId.indexOf(':');

        // Reject malformed prefixes (Fix #4: empty prefix handling)
        if (colonIndex == 0) {
            throw new IllegalArgumentException("Invalid node ID: empty prefix in '" + nodeId + "'");
        }
        if (colonIndex == nodeId.length() - 1) {
            throw new IllegalArgumentException("Invalid node ID: trailing colon in '" + nodeId + "'");
        }

        if (colonIndex > 0) {
            return nodeId.substring(colonIndex + 1);
        }
        return nodeId;
    }

    /**
     * Mutable builder for constructing DependencyGraph instances.
     * Graph is frozen (immutable) after build().
     */
    public static class MutableBuilder {
        private final Map<String, Node> nodes = new LinkedHashMap<>();
        private final Map<String, Set<String>> forwardAdj = new LinkedHashMap<>();
        private final Map<String, Set<String>> reverseAdj = new LinkedHashMap<>();
        private final Map<String, Map<String, Edge>> edges = new LinkedHashMap<>();

        public MutableBuilder addNode(Node node) {
            nodes.put(node.getId(), node);
            forwardAdj.putIfAbsent(node.getId(), new LinkedHashSet<>());
            reverseAdj.putIfAbsent(node.getId(), new LinkedHashSet<>());
            return this;
        }

        public MutableBuilder addEdge(Edge edge) {
            String src = edge.getSource();
            String tgt = edge.getTarget();

            if (!nodes.containsKey(src)) {
                throw new IllegalArgumentException("Edge source node not found: " + src);
            }
            if (!nodes.containsKey(tgt)) {
                return this; // skip edges to missing targets (external classes)
            }
            if (src.equals(tgt)) {
                return this; // skip self-referencing edges
            }

            forwardAdj.computeIfAbsent(src, k -> new LinkedHashSet<>()).add(tgt);
            reverseAdj.computeIfAbsent(tgt, k -> new LinkedHashSet<>()).add(src);
            edges.computeIfAbsent(src, k -> new LinkedHashMap<>()).put(tgt, edge);
            return this;
        }

        /**
         * Returns the set of node IDs currently in the builder.
         * Useful for checking edge target existence before adding edges.
         */
        public Set<String> knownNodeIds() {
            return Collections.unmodifiableSet(nodes.keySet());
        }

        public DependencyGraph build() {
            // Compute in/out degrees
            nodes.values().forEach(node -> {
                node.setOutDegree(forwardAdj.getOrDefault(node.getId(), Set.of()).size());
                node.setInDegree(reverseAdj.getOrDefault(node.getId(), Set.of()).size());
            });
            return new DependencyGraph(nodes, forwardAdj, reverseAdj, edges);
        }
    }
}
