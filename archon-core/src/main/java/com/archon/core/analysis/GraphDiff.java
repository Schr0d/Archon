package com.archon.core.analysis;

import com.archon.core.graph.Edge;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Result of comparing two DependencyGraph instances.
 * Contains sets of added/removed nodes and edges, plus cycle changes.
 */
public class GraphDiff {
    private final Set<String> addedNodes;
    private final Set<String> removedNodes;
    private final Set<Edge> addedEdges;
    private final Set<Edge> removedEdges;
    private final List<List<String>> newCycles;
    private final List<List<String>> fixedCycles;

    public GraphDiff(Set<String> addedNodes, Set<String> removedNodes,
                     Set<Edge> addedEdges, Set<Edge> removedEdges,
                     List<List<String>> newCycles, List<List<String>> fixedCycles) {
        this.addedNodes = Collections.unmodifiableSet(addedNodes);
        this.removedNodes = Collections.unmodifiableSet(removedNodes);
        this.addedEdges = Collections.unmodifiableSet(addedEdges);
        this.removedEdges = Collections.unmodifiableSet(removedEdges);
        this.newCycles = Collections.unmodifiableList(newCycles);
        this.fixedCycles = Collections.unmodifiableList(fixedCycles);
    }

    public Set<String> getAddedNodes() {
        return addedNodes;
    }

    public Set<String> getRemovedNodes() {
        return removedNodes;
    }

    public Set<Edge> getAddedEdges() {
        return addedEdges;
    }

    public Set<Edge> getRemovedEdges() {
        return removedEdges;
    }

    public List<List<String>> getNewCycles() {
        return newCycles;
    }

    public List<List<String>> getFixedCycles() {
        return fixedCycles;
    }

    public boolean isEmpty() {
        return addedNodes.isEmpty() && removedNodes.isEmpty()
            && addedEdges.isEmpty() && removedEdges.isEmpty()
            && newCycles.isEmpty() && fixedCycles.isEmpty();
    }
}
