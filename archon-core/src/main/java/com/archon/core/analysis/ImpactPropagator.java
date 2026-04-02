package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.RiskLevel;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * BFS-based impact propagation from a target node.
 * Traverses reverse adjacency (dependents) to compute blast radius.
 * Depth-limited with cross-domain tracking.
 */
public class ImpactPropagator {
    private final LayerClassifier layerClassifier;

    public ImpactPropagator() {
        this.layerClassifier = new LayerClassifier();
    }

    public ImpactPropagator(LayerClassifier layerClassifier) {
        this.layerClassifier = layerClassifier != null ? layerClassifier : new LayerClassifier();
    }

    /**
     * Propagate impact from a target node through its dependents.
     *
     * @param graph     the dependency graph
     * @param target    the starting node id (the changed node)
     * @param maxDepth  maximum traversal depth (default: 3)
     * @param domainMap node id to domain name mapping
     * @return impact result with all reached nodes and metrics
     * @throws IllegalArgumentException if target node is not in the graph
     */
    public ImpactResult propagate(DependencyGraph graph, String target, int maxDepth,
                                  Map<String, String> domainMap) {
        if (!graph.containsNode(target)) {
            throw new IllegalArgumentException(
                "Target node not found: " + target + ". Available: " + graph.getNodeIds());
        }

        List<ImpactResult.ImpactNode> impactedNodes = new java.util.ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        Map<String, Integer> depths = new HashMap<>();

        String targetDomain = domainMap.getOrDefault(target, "");
        for (String dependent : graph.getDependents(target)) {
            if (!visited.contains(dependent)) {
                visited.add(dependent);
                queue.add(dependent);
                depths.put(dependent, 1);
            }
        }

        int maxDepthReached = 0;
        int crossDomainEdges = 0;

        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depths.get(current);

            if (currentDepth > maxDepth) {
                continue;
            }

            maxDepthReached = Math.max(maxDepthReached, currentDepth);
            String currentDomain = domainMap.getOrDefault(current, "");

            Set<String> deps = graph.getDependencies(current);
            for (String dep : deps) {
                String depDomain = domainMap.getOrDefault(dep, "");
                if (!currentDomain.isEmpty() && !depDomain.isEmpty()
                    && !currentDomain.equals(depDomain)) {
                    crossDomainEdges++;
                    break;
                }
            }

            RiskLevel risk = currentDepth >= 3 ? RiskLevel.HIGH
                           : currentDepth >= 2 ? RiskLevel.MEDIUM
                           : RiskLevel.LOW;

            impactedNodes.add(new ImpactResult.ImpactNode(
                current, currentDomain, currentDepth, risk, layerClassifier.classify(current)));

            if (currentDepth < maxDepth) {
                for (String dependent : graph.getDependents(current)) {
                    if (!visited.contains(dependent)) {
                        visited.add(dependent);
                        queue.add(dependent);
                        depths.put(dependent, currentDepth + 1);
                    }
                }
            }
        }

        return new ImpactResult(target, impactedNodes, maxDepthReached, crossDomainEdges);
    }
}
