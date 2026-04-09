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
        return computePageRank(0.85, 30, 1e-6);
    }

    /**
     * Computes betweenness centrality for all nodes.
     * Measures how often a node lies on the shortest path between other nodes.
     * Uses Brandes' algorithm for efficient computation.
     *
     * @return map of node ID to betweenness score (0-1 range, normalized)
     */
    public Map<String, Double> computeBetweenness() {
        if (nodeCount == 0) {
            return Map.of();
        }

        Map<String, Double> betweenness = new HashMap<>();
        for (String nodeId : graph.getNodeIds()) {
            betweenness.put(nodeId, 0.0);
        }

        // For each source node, compute shortest paths and accumulate betweenness
        for (String source : graph.getNodeIds()) {
            Map<String, List<String>> predecessors = new HashMap<>();
            Map<String, Integer> distance = new HashMap<>();
            Map<String, Double> sigma = new HashMap<>(); // number of shortest paths

            for (String nodeId : graph.getNodeIds()) {
                predecessors.put(nodeId, new LinkedList<>());
                distance.put(nodeId, -1); // -1 means unvisited
                sigma.put(nodeId, 0.0);
            }

            distance.put(source, 0);
            sigma.put(source, 1.0);

            LinkedList<String> queue = new LinkedList<>();
            queue.add(source);

            List<String> orderedNodes = new LinkedList<>();

            // BFS to find shortest paths (using undirected graph: both dependencies and dependents)
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                orderedNodes.add(current);

                // Follow both forward and reverse edges for undirected traversal
                Set<String> neighbors = new HashSet<>();
                neighbors.addAll(graph.getDependencies(current));
                neighbors.addAll(graph.getDependents(current));

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
            for (String nodeId : graph.getNodeIds()) {
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
            for (String nodeId : graph.getNodeIds()) {
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
     * Computes closeness centrality for all nodes.
     * Measures the average distance from a node to all other reachable nodes.
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

            // BFS to compute distances to all reachable nodes (using undirected graph)
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

                // Follow both forward and reverse edges for undirected traversal
                Set<String> neighbors = new HashSet<>();
                neighbors.addAll(graph.getDependencies(current));
                neighbors.addAll(graph.getDependents(current));

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

                    for (String neighbor : graph.getDependencies(current)) {
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.add(neighbor);
                        }
                    }

                    for (String dependent : graph.getDependents(current)) {
                        if (!visited.contains(dependent)) {
                            visited.add(dependent);
                            queue.add(dependent);
                        }
                    }
                }
            }
        }

        return components;
    }

    /**
     * Identifies bridge edges in the graph.
     * A bridge is an edge whose removal increases the number of connected components.
     *
     * @return set of edge descriptions in format "source->target"
     */
    public Set<String> findBridges() {
        Set<String> bridges = new HashSet<>();
        if (nodeCount == 0) {
            return bridges;
        }

        int baseComponents = computeConnectedComponents();

        // Check each edge
        for (var edge : graph.getAllEdges()) {
            String source = edge.getSource();
            String target = edge.getTarget();

            // Temporarily remove edge and count components
            // Note: This is a simple heuristic; for production use Tarjan's algorithm
            // would be more efficient (O(V + E) vs O(E * (V + E)))

            // Since our graph is immutable, we simulate edge removal by checking
            // if there's an alternative path between source and target
            if (!hasAlternativePath(source, target, edge)) {
                bridges.add(source + "->" + target);
            }
        }

        return bridges;
    }

    /**
     * Checks if there's an alternative path between source and target
     * that doesn't use the specified edge.
     */
    private boolean hasAlternativePath(String source, String target, Edge excludedEdge) {
        // BFS to find path excluding the specified edge
        Set<String> visited = new HashSet<>();
        LinkedList<String> queue = new LinkedList<>();
        queue.add(source);
        visited.add(source);

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();

            // Use both forward and reverse edges for undirected traversal
            Set<String> neighbors = new HashSet<>();
            neighbors.addAll(graph.getDependencies(current));
            neighbors.addAll(graph.getDependents(current));

            for (String neighbor : neighbors) {
                // Skip the excluded edge (in either direction)
                if ((current.equals(excludedEdge.getSource()) && neighbor.equals(excludedEdge.getTarget())) ||
                    (current.equals(excludedEdge.getTarget()) && neighbor.equals(excludedEdge.getSource()))) {
                    continue;
                }

                if (neighbor.equals(target)) {
                    return true; // Found alternative path
                }

                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return false;
    }
}
