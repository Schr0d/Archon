package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CouplingAnalyzerTest {

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
    void findHotspots_emptyGraph_returnsEmptyList() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 5);

        assertTrue(hotspots.isEmpty());
    }

    @Test
    void findHotspots_singleHotspot_returnsIt() {
        DependencyGraph graph = buildGraph(
            new Node[]{node("A"), node("B"), node("C"), node("D"), node("E"), node("F")},
            new Edge[]{edge("B", "A"), edge("C", "A"), edge("D", "A"), edge("E", "A"), edge("F", "A")}
        );

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 4);

        assertEquals(1, hotspots.size());
        assertEquals("A", hotspots.get(0).getId());
        assertEquals(5, hotspots.get(0).getInDegree());
    }

    @Test
    void findHotspots_sortedByInDegreeDescending() {
        DependencyGraph graph = buildGraph(
            new Node[]{node("A"), node("B"), node("C1"), node("C2"), node("C3"),
                       node("D1"), node("D2"), node("D3"), node("D4"), node("D5")},
            new Edge[]{edge("C1", "A"), edge("C2", "A"), edge("C3", "A"),
                       edge("D1", "B"), edge("D2", "B"), edge("D3", "B"), edge("D4", "B"), edge("D5", "B")}
        );

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 2);

        assertEquals(2, hotspots.size());
        assertEquals("B", hotspots.get(0).getId());
        assertEquals("A", hotspots.get(1).getId());
    }

    @Test
    void findHotspots_belowThreshold_filteredOut() {
        DependencyGraph graph = buildGraph(
            new Node[]{node("A"), node("B"), node("C")},
            new Edge[]{edge("B", "A"), edge("C", "A")}
        );

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 5);

        assertTrue(hotspots.isEmpty());
    }

    @Test
    void findHotspots_allNodesAreHotspots() {
        DependencyGraph graph = buildGraph(
            new Node[]{node("A"), node("B"), node("X1"), node("X2"), node("X3"), node("X4"),
                       node("Y1"), node("Y2"), node("Y3"), node("Y4")},
            new Edge[]{edge("X1", "A"), edge("X2", "A"), edge("X3", "A"),
                       edge("X4", "A"), edge("B", "A"), edge("Y1", "A"),
                       edge("Y1", "B"), edge("Y2", "B"), edge("Y3", "B"),
                       edge("Y4", "B"), edge("A", "B"), edge("X1", "B")}
        );

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 5);

        assertEquals(2, hotspots.size());
    }
}
