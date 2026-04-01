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
                throw new IllegalArgumentException("Edge target node not found: " + tgt);
            }
            if (src.equals(tgt)) {
                throw new IllegalArgumentException("Self-referencing edges not allowed: " + src);
            }

            forwardAdj.computeIfAbsent(src, k -> new LinkedHashSet<>()).add(tgt);
            reverseAdj.computeIfAbsent(tgt, k -> new LinkedHashSet<>()).add(src);
            edges.computeIfAbsent(src, k -> new LinkedHashMap<>()).put(tgt, edge);
            return this;
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
