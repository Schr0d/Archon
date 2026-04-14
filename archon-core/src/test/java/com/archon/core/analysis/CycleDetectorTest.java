package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
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

    private DependencyGraph buildGraph(Node[] nodes, Edge[] edges) {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        for (Node n : nodes) {
            builder.addNode(n);
        }
        for (Edge e : edges) {
            builder.addEdge(e);
        }
        return builder.build();
    }

    @Test
    void detectCycles_noCycle_returnsEmptyList() {
        DependencyGraph graph = buildGraph(
            new Node[]{node("A"), node("B")},
            new Edge[]{edge("A", "B")}
        );

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertTrue(cycles.isEmpty());
    }

    @Test
    void detectCycles_simpleCycle_detectsAB() {
        DependencyGraph graph = buildGraph(
            new Node[]{node("A"), node("B")},
            new Edge[]{edge("A", "B"), edge("B", "A")}
        );

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertEquals(1, cycles.size());
        assertEquals(2, cycles.get(0).size());
    }

    @Test
    void detectCycles_longCycle_detectsABC() {
        DependencyGraph graph = buildGraph(
            new Node[]{node("A"), node("B"), node("C")},
            new Edge[]{edge("A", "B"), edge("B", "C"), edge("C", "A")}
        );

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertEquals(1, cycles.size());
        assertEquals(3, cycles.get(0).size());
    }

    @Test
    void detectCycles_multipleIndependentCycles_detectsBoth() {
        DependencyGraph graph = buildGraph(
            new Node[]{node("A"), node("B"), node("C"), node("D")},
            new Edge[]{edge("A", "B"), edge("B", "A"), edge("C", "D"), edge("D", "C")}
        );

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertEquals(2, cycles.size());
    }

    @Test
    void detectCycles_emptyGraph_returnsEmptyList() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertTrue(cycles.isEmpty());
    }

    @Test
    void detectCycles_diamondNoCycle_returnsEmptyList() {
        DependencyGraph graph = buildGraph(
            new Node[]{node("A"), node("B"), node("C"), node("D")},
            new Edge[]{edge("A", "B"), edge("A", "C"), edge("B", "D"), edge("C", "D")}
        );

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertTrue(cycles.isEmpty());
    }
}
