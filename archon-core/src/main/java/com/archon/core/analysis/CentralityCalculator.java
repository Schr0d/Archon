package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes centrality metrics for dependency graphs.
 * Provides PageRank, betweenness, and closeness centrality calculations
 * for Tier 2 full analysis.
 */
public class CentralityCalculator {

    private static final double DEFAULT_DAMPING_FACTOR = 0.85;
    private static final int DEFAULT_ITERATIONS = 30;
    private static final double DEFAULT_CONVERGENCE_THRESHOLD = 1e-6;

    private final DependencyGraph graph;
    private final int nodeCount;

    public CentralityCalculator(DependencyGraph graph) {
        this.graph = graph;
        this.nodeCount = graph.nodeCount();
    }

    /**
     * Computes PageRank scores for all nodes.
     * Uses iterative power method with damping factor.
     *
     * @param dampingFactor probability of continuing random walk (typically 0.85)
     * @param iterations number of iterations (typically 20-50)
     * @param convergenceThreshold minimum change to continue iterating
     * @return map of node ID to PageRank score (0-1 range, sum = 1.0)
     */
    public Map<String, Double> computePageRank(
            double dampingFactor,
            int iterations,
            double convergenceThreshold) {

        if (nodeCount == 0) {
            return Map.of();
        }

        // Initialize equal scores
        Map<String, Double> pageRank = new HashMap<>();
        double initialScore = 1.0 / nodeCount;
        for (String nodeId : graph.getNodeIds()) {
            pageRank.put(nodeId, initialScore);
        }

        // Power iteration
        for (int iter = 0; iter < iterations; iter++) {
            Map<String, Double> newPageRank = new HashMap<>();
            double maxChange = 0.0;
            double totalScore = 0.0;

            for (String nodeId : graph.getNodeIds()) {
                double score = (1 - dampingFactor) / nodeCount;

                // Add contribution from incoming edges (nodes that depend on this)
                for (String dependent : graph.getDependents(nodeId)) {
                    int outDegree = graph.getDependencies(dependent).size();
                    if (outDegree > 0) {
                        score += dampingFactor * pageRank.get(dependent) / outDegree;
                    }
                }

                newPageRank.put(nodeId, score);
                totalScore += score;

                double change = Math.abs(score - pageRank.get(nodeId));
                maxChange = Math.max(maxChange, change);
            }

            // Handle disconnected nodes: normalize to sum = 1.0
            if (totalScore > 0) {
                double normalizationFactor = 1.0 / totalScore;
                for (String nodeId : graph.getNodeIds()) {
                    newPageRank.put(nodeId, newPageRank.get(nodeId) * normalizationFactor);
                }
            }

            pageRank = newPageRank;

            if (maxChange < convergenceThreshold) {
                break;
            }
        }

        return pageRank;
    }

    /**
     * Computes PageRank with default parameters.
     */
    public Map<String, Double> computePageRank() {
        return computePageRank(DEFAULT_DAMPING_FACTOR, DEFAULT_ITERATIONS, DEFAULT_CONVERGENCE_THRESHOLD);
    }

    /**
     * Computes betweenness centrality for all nodes (DIRECTED).
     * Measures how often a node lies on the shortest path between other nodes,
     * following dependency direction (A->B means A depends on B).
     * Uses Brandes' algorithm for efficient computation.
     *
     * Time complexity: O(V * (V + E)) where V = nodes, E = edges
     * Space complexity: O(V)
     *
     * @return map of node ID to betweenness score (0-1 range, normalized)
     */
    public Map<String, Double> computeBetweenness() {
        if (nodeCount == 0) {
            return Map.of();
        }

        // Cache node IDs to avoid repeated calls
        List<String> nodeIds = new java.util.ArrayList<>(graph.getNodeIds());

        Map<String, Double> betweenness = new HashMap<>();
        for (String nodeId : nodeIds) {
            betweenness.put(nodeId, 0.0);
        }

        // For each source node, compute shortest paths and accumulate betweenness
        for (String source : nodeIds) {
            Map<String, List<String>> predecessors = new HashMap<>();
            Map<String, Integer> distance = new HashMap<>();
            Map<String, Double> sigma = new HashMap<>(); // number of shortest paths

            for (String nodeId : nodeIds) {
                predecessors.put(nodeId, new LinkedList<>());
                distance.put(nodeId, -1); // -1 means unvisited
                sigma.put(nodeId, 0.0);
            }

            distance.put(source, 0);
            sigma.put(source, 1.0);

            LinkedList<String> queue = new LinkedList<>();
            queue.add(source);

            List<String> orderedNodes = new LinkedList<>();

            // BFS to find shortest paths (using directed graph: only follow dependencies)
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                orderedNodes.add(current);

                // Only follow forward dependencies (A->B means A depends on B)
                // For betweenness in dependency graphs, we care about downstream impact
                Set<String> neighbors = graph.getDependencies(current);

                for (String neighbor : neighbors) {
                    if (distance.get(neighbor) < 0) {
                        queue.add(neighbor);
                        distance.put(neighbor, distance.get(current) + 1);
                    }

                    if (distance.get(neighbor) == distance.get(current) + 1) {
                        sigma.put(neighbor, sigma.get(neighbor) + sigma.get(current));
                        predecessors.get(neighbor).add(current);
                    }
                }
            }

            // Accumulate betweenness using dependency values
            Map<String, Double> delta = new HashMap<>();
            for (String nodeId : nodeIds) {
                delta.put(nodeId, 0.0);
            }

            // Process in reverse order of discovery
            for (int i = orderedNodes.size() - 1; i >= 0; i--) {
                String node = orderedNodes.get(i);
                if (!node.equals(source)) {
                    for (String predecessor : predecessors.get(node)) {
                        double contribution = (sigma.get(predecessor) / sigma.get(node)) * (1.0 + delta.get(node));
                        delta.put(predecessor, delta.get(predecessor) + contribution);
                    }
                }
            }

            // Add to total betweenness (exclude source)
            for (String nodeId : nodeIds) {
                if (!nodeId.equals(source)) {
                    betweenness.put(nodeId, betweenness.get(nodeId) + delta.get(nodeId));
                }
            }
        }

        // Normalize to 0-1 range
        double maxBetweenness = betweenness.values().stream().max(Double::compare).orElse(1.0);
        if (maxBetweenness > 0) {
            Map<String, Double> normalized = new HashMap<>();
            for (Map.Entry<String, Double> entry : betweenness.entrySet()) {
                normalized.put(entry.getKey(), entry.getValue() / maxBetweenness);
            }
            return normalized;
        }

        return betweenness;
    }

    /**
     * Computes closeness centrality for all nodes (DIRECTED).
     * Measures the average distance from a node to all other reachable nodes,
     * following dependency direction. Nodes that can quickly reach many dependencies
     * score higher.
     *
     * Time complexity: O(V * (V + E)) where V = nodes, E = edges
     * Space complexity: O(V)
     *
     * @return map of node ID to closeness score (0-1 range, normalized)
     */
    public Map<String, Double> computeCloseness() {
        if (nodeCount == 0) {
            return Map.of();
        }

        Map<String, Double> closeness = new HashMap<>();

        for (String source : graph.getNodeIds()) {
            int reachableCount = 0;
            int totalDistance = 0;

            // BFS to compute distances to all reachable nodes (using directed graph)
            Map<String, Integer> distance = new HashMap<>();
            for (String nodeId : graph.getNodeIds()) {
                distance.put(nodeId, -1);
            }
            distance.put(source, 0);

            LinkedList<String> queue = new LinkedList<>();
            queue.add(source);

            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                int dist = distance.get(current);

                // Only follow forward dependencies
                Set<String> neighbors = graph.getDependencies(current);

                for (String neighbor : neighbors) {
                    if (distance.get(neighbor) < 0) {
                        distance.put(neighbor, dist + 1);
                        totalDistance += dist + 1;
                        reachableCount++;
                        queue.add(neighbor);
                    }
                }
            }

            // Standard closeness formula: (reachable_nodes - 1) / total_distance
            // Normalize to 0-1 by dividing by (node_count - 1)
            if (reachableCount > 1 && totalDistance > 0) {
                double rawCloseness = (double) (reachableCount - 1) / totalDistance;
                // Normalize by maximum possible closeness (which is 1.0 for complete graph)
                closeness.put(source, Math.min(1.0, rawCloseness * (nodeCount - 1)));
            } else {
                closeness.put(source, 0.0);
            }
        }

        return closeness;
    }

    /**
     * Computes the number of weakly connected components in the graph.
     *
     * @return number of connected components
     */
    public int computeConnectedComponents() {
        if (nodeCount == 0) {
            return 0;
        }

        Set<String> visited = new HashSet<>();
        int components = 0;

        for (String startNode : graph.getNodeIds()) {
            if (!visited.contains(startNode)) {
                components++;
                // BFS to mark all nodes in this component
                LinkedList<String> queue = new LinkedList<>();
                queue.add(startNode);
                visited.add(startNode);

                while (!queue.isEmpty()) {
                    String current = queue.removeFirst();

                    for (String neighbor : getUndirectedNeighbors(current)) {
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }
                }
            }
        }

        return components;
    }

    /**
     * Identifies bridge edges in the graph using Tarjan's algorithm (iterative).
     * A bridge is an edge whose removal increases the number of connected components.
     *
     * Time complexity: O(V + E)
     * Space complexity: O(V) for explicit stack
     *
     * @return set of edge descriptions in format "source->target"
     */
    public Set<String> findBridges() {
        Set<String> bridges = new HashSet<>();
        if (nodeCount == 0) {
            return bridges;
        }

        // Tarjan's bridge finding algorithm for undirected graphs (iterative)
        Map<String, Integer> disc = new HashMap<>();  // Discovery time
        Map<String, Integer> low = new HashMap<>();    // Lowest discovery time reachable
        Map<String, String> parent = new HashMap<>();  // Parent in DFS tree
        Set<String> visited = new HashSet<>();

        int[] time = {0};

        // Run DFS from each unvisited node (handles disconnected graphs)
        for (String startNode : graph.getNodeIds()) {
            if (!visited.contains(startNode)) {
                // Iterative DFS using explicit stack
                // Stack frame: [node, edgeFrom, state, neighborIndex]
                java.util.Stack<Object[]> stack = new java.util.Stack<>();
                stack.push(new Object[]{startNode, null, 0, -1});

                while (!stack.isEmpty()) {
                    Object[] frame = stack.peek();
                    String u = (String) frame[0];
                    String edgeFrom = (String) frame[1];
                    int state = (Integer) frame[2];

                    if (state == 0) {
                        // First time visiting this node
                        if (visited.contains(u)) {
                            stack.pop();
                            continue;
                        }

                        visited.add(u);
                        disc.put(u, time[0]);
                        low.put(u, time[0]);
                        time[0]++;

                        // Get all neighbors (both dependencies and dependents for undirected graph)
                        Set<String> neighbors = getUndirectedNeighbors(u);

                        // Update frame to state 1 (processing neighbors)
                        frame[2] = 1;

                        // Push neighbors onto stack
                        for (String v : neighbors) {
                            if (!visited.contains(v)) {
                                parent.put(v, u);
                                stack.push(new Object[]{v, u, 0, -1});
                            } else if (!v.equals(edgeFrom)) {
                                // Update low value for back edge
                                low.put(u, Math.min(low.get(u), disc.get(v)));
                            }
                        }
                    } else {
                        // State 1: All neighbors processed, update low value and check bridge
                        stack.pop();

                        // Check bridge condition for edge from parent to u
                        String p = parent.get(u);
                        if (p != null) {
                            // Update parent's low value
                            low.put(p, Math.min(low.get(p), low.get(u)));

                            // If the lowest vertex reachable from subtree rooted at u
                            // is below p in DFS tree, then p-u is a bridge
                            if (low.get(u) > disc.get(p)) {
                                bridges.add(p + "->" + u);
                            }
                        }
                    }
                }
            }
        }

        return bridges;
    }

    /**
     * Get all neighbors (dependencies + dependents) for undirected traversal.
     * Used by connected components and bridge detection where direction doesn't matter.
     */
    private Set<String> getUndirectedNeighbors(String node) {
        Set<String> neighbors = new HashSet<>();
        neighbors.addAll(graph.getDependencies(node));
        neighbors.addAll(graph.getDependents(node));
        return neighbors;
    }
}
