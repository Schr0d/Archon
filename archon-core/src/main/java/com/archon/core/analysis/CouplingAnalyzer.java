package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Identifies coupling hotspots by counting in-degree from reverse adjacency.
 * Returns nodes with in-degree above threshold, sorted by in-degree descending.
 */
public class CouplingAnalyzer {

    public List<Node> findHotspots(DependencyGraph graph, int threshold) {
        return graph.getNodeIds().stream()
            .map(graph::getNode)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(n -> n.getInDegree() > threshold)
            .sorted(Comparator.comparingInt(Node::getInDegree).reversed())
            .collect(Collectors.toList());
    }
}
