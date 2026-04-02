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

class CouplingAnalyzerTest {

    private Node node(String fqn) {
        return Node.builder().id(fqn).type(NodeType.CLASS).build();
    }

    private Edge edge(String from, String to) {
        return Edge.builder().source(from).target(to).type(EdgeType.IMPORTS).build();
    }

    @Test
    void findHotspots_emptyGraph_returnsEmptyList() {
        DependencyGraph graph = GraphBuilder.builder().build();

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 5);

        assertTrue(hotspots.isEmpty());
    }

    @Test
    void findHotspots_singleHotspot_returnsIt() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .addNode(node("B")).addNode(node("C")).addNode(node("D"))
            .addNode(node("E")).addNode(node("F"))
            .addEdge(edge("B", "A")).addEdge(edge("C", "A"))
            .addEdge(edge("D", "A")).addEdge(edge("E", "A"))
            .addEdge(edge("F", "A"))
            .build();

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 4);

        assertEquals(1, hotspots.size());
        assertEquals("A", hotspots.get(0).getId());
        assertEquals(5, hotspots.get(0).getInDegree());
    }

    @Test
    void findHotspots_sortedByInDegreeDescending() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B"))
            .addNode(node("C1")).addNode(node("C2")).addNode(node("C3"))
            .addNode(node("D1")).addNode(node("D2")).addNode(node("D3"))
            .addNode(node("D4")).addNode(node("D5"))
            .addEdge(edge("C1", "A")).addEdge(edge("C2", "A")).addEdge(edge("C3", "A"))
            .addEdge(edge("D1", "B")).addEdge(edge("D2", "B"))
            .addEdge(edge("D3", "B")).addEdge(edge("D4", "B")).addEdge(edge("D5", "B"))
            .build();

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 2);

        assertEquals(2, hotspots.size());
        assertEquals("B", hotspots.get(0).getId());
        assertEquals("A", hotspots.get(1).getId());
    }

    @Test
    void findHotspots_belowThreshold_filteredOut() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B")).addNode(node("C"))
            .addEdge(edge("B", "A")).addEdge(edge("C", "A"))
            .build();

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 5);

        assertTrue(hotspots.isEmpty());
    }

    @Test
    void findHotspots_allNodesAreHotspots() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B"))
            .addNode(node("X1")).addNode(node("X2")).addNode(node("X3")).addNode(node("X4"))
            .addNode(node("Y1")).addNode(node("Y2")).addNode(node("Y3")).addNode(node("Y4"))
            .addEdge(edge("X1", "A")).addEdge(edge("X2", "A")).addEdge(edge("X3", "A"))
            .addEdge(edge("X4", "A")).addEdge(edge("B", "A")).addEdge(edge("Y1", "A"))
            .addEdge(edge("Y1", "B")).addEdge(edge("Y2", "B")).addEdge(edge("Y3", "B"))
            .addEdge(edge("Y4", "B")).addEdge(edge("A", "B")).addEdge(edge("X1", "B"))
            .build();

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 5);

        assertEquals(2, hotspots.size());
    }
}