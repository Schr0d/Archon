package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compares two DependencyGraph instances and produces a GraphDiff.
 * Uses a custom edge comparator that considers source+target+type
 * (not Edge.equals() which only compares source+target).
 */
public class GraphDiffer {

    /**
     * Edge key that includes type for proper diff comparison.
     */
    private static String edgeKey(Edge edge) {
        return edge.getSource() + "->" + edge.getTarget() + "[" + edge.getType() + "]";
    }

    /**
     * Normalize a cycle by rotating to the lexicographically smallest element.
     * [C, A, B] and [A, B, C] both normalize to [A, B, C].
     */
    static List<String> normalizeCycle(List<String> cycle) {
        if (cycle.size() <= 1) return cycle;

        // Find the index of the minimum element
        int minIdx = 0;
        for (int i = 1; i < cycle.size(); i++) {
            if (cycle.get(i).compareTo(cycle.get(minIdx)) < 0) {
                minIdx = i;
            }
        }

        // Rotate
        List<String> normalized = new ArrayList<>();
        for (int i = 0; i < cycle.size(); i++) {
            normalized.add(cycle.get((minIdx + i) % cycle.size()));
        }
        return normalized;
    }

    private static String cycleKey(List<String> cycle) {
        return normalizeCycle(cycle).stream().collect(Collectors.joining(","));
    }

    public GraphDiff diff(DependencyGraph baseGraph, DependencyGraph headGraph) {
        // Diff nodes
        Set<String> headNodes = headGraph.getNodeIds();
        Set<String> baseNodes = baseGraph.getNodeIds();

        Set<String> addedNodes = new LinkedHashSet<>(headNodes);
        addedNodes.removeAll(baseNodes);

        Set<String> removedNodes = new LinkedHashSet<>(baseNodes);
        removedNodes.removeAll(headNodes);

        // Diff edges using custom key (source+target+type)
        Set<String> headEdgeKeys = new HashSet<>();
        Set<Edge> headEdges = new LinkedHashSet<>();
        for (String source : headGraph.getNodeIds()) {
            for (String target : headGraph.getDependencies(source)) {
                headGraph.getEdge(source, target).ifPresent(edge -> {
                    headEdgeKeys.add(edgeKey(edge));
                    headEdges.add(edge);
                });
            }
        }

        Set<String> baseEdgeKeys = new HashSet<>();
        Set<Edge> baseEdges = new LinkedHashSet<>();
        for (String source : baseGraph.getNodeIds()) {
            for (String target : baseGraph.getDependencies(source)) {
                baseGraph.getEdge(source, target).ifPresent(edge -> {
                    baseEdgeKeys.add(edgeKey(edge));
                    baseEdges.add(edge);
                });
            }
        }

        Set<Edge> addedEdges = new LinkedHashSet<>();
        for (Edge edge : headEdges) {
            if (!baseEdgeKeys.contains(edgeKey(edge))) {
                addedEdges.add(edge);
            }
        }

        Set<Edge> removedEdges = new LinkedHashSet<>();
        for (Edge edge : baseEdges) {
            if (!headEdgeKeys.contains(edgeKey(edge))) {
                removedEdges.add(edge);
            }
        }

        // Diff cycles
        CycleDetector cycleDetector = new CycleDetector();
        List<List<String>> baseCycles = cycleDetector.detectCycles(baseGraph);
        List<List<String>> headCycles = cycleDetector.detectCycles(headGraph);

        Set<String> baseCycleKeys = baseCycles.stream()
            .map(GraphDiffer::cycleKey).collect(Collectors.toSet());
        Set<String> headCycleKeys = headCycles.stream()
            .map(GraphDiffer::cycleKey).collect(Collectors.toSet());

        List<List<String>> newCycles = new ArrayList<>();
        for (List<String> cycle : headCycles) {
            if (!baseCycleKeys.contains(cycleKey(cycle))) {
                newCycles.add(normalizeCycle(cycle));
            }
        }

        List<List<String>> fixedCycles = new ArrayList<>();
        for (List<String> cycle : baseCycles) {
            if (!headCycleKeys.contains(cycleKey(cycle))) {
                fixedCycles.add(normalizeCycle(cycle));
            }
        }

        return new GraphDiff(addedNodes, removedNodes, addedEdges, removedEdges, newCycles, fixedCycles);
    }
}
