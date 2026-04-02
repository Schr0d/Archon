package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CycleDetectorTest {

    private Node node(String fqn) {
        return Node.builder().id(fqn).type(NodeType.CLASS).build();
    }

    private Edge edge(String from, String to) {
        return Edge.builder().source(from).target(to).type(EdgeType.IMPORTS).build();
    }

    @Test
    void detectCycles_noCycle_returnsEmptyList() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .addNode(node("B"))
            .addEdge(edge("A", "B"))
            .build();

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertTrue(cycles.isEmpty());
    }

    @Test
    void detectCycles_simpleCycle_detectsAB() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .addNode(node("B"))
            .addEdge(edge("A", "B"))
            .addEdge(edge("B", "A"))
            .build();

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertEquals(1, cycles.size());
        assertEquals(2, cycles.get(0).size());
    }

    @Test
    void detectCycles_longCycle_detectsABC() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .addNode(node("B"))
            .addNode(node("C"))
            .addEdge(edge("A", "B"))
            .addEdge(edge("B", "C"))
            .addEdge(edge("C", "A"))
            .build();

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertEquals(1, cycles.size());
        assertEquals(3, cycles.get(0).size());
    }

    @Test
    void detectCycles_multipleIndependentCycles_detectsBoth() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .addNode(node("B"))
            .addNode(node("C"))
            .addNode(node("D"))
            .addEdge(edge("A", "B"))
            .addEdge(edge("B", "A"))
            .addEdge(edge("C", "D"))
            .addEdge(edge("D", "C"))
            .build();

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertEquals(2, cycles.size());
    }

    @Test
    void detectCycles_emptyGraph_returnsEmptyList() {
        DependencyGraph graph = GraphBuilder.builder().build();

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertTrue(cycles.isEmpty());
    }

    @Test
    void detectCycles_diamondNoCycle_returnsEmptyList() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .addNode(node("B"))
            .addNode(node("C"))
            .addNode(node("D"))
            .addEdge(edge("A", "B"))
            .addEdge(edge("A", "C"))
            .addEdge(edge("B", "D"))
            .addEdge(edge("C", "D"))
            .build();

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertTrue(cycles.isEmpty());
    }
}