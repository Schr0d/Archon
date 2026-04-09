package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.Node;
import com.archon.core.graph.Confidence;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CentralityCalculator.
 */
class CentralityCalculatorTest {

    @Test
    void testPageRankEmptyGraph() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        Map<String, Double> pageRank = calculator.computePageRank();

        assertTrue(pageRank.isEmpty());
    }

    @Test
    void testPageRankSingleNode() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        Map<String, Double> pageRank = calculator.computePageRank();

        assertEquals(1.0, pageRank.get("A"), 0.001);
    }

    @Test
    void testPageRankLinearChain() {
        // A -> B -> C
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("C").type(NodeType.CLASS).confidence(Confidence.HIGH).build());

        builder.addEdge(Edge.builder()
            .source("A")
            .target("B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        builder.addEdge(Edge.builder()
            .source("B")
            .target("C")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        Map<String, Double> pageRank = calculator.computePageRank();

        // A should have lowest PageRank (no incoming edges)
        // C should have highest PageRank (pulled from B and A indirectly)
        assertTrue(pageRank.get("C") > pageRank.get("A"));
        assertTrue(pageRank.get("B") > pageRank.get("A"));
    }

    @Test
    void testPageRankConverges() {
        // A -> B, A -> C (B and C both depend on A)
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("C").type(NodeType.CLASS).confidence(Confidence.HIGH).build());

        builder.addEdge(Edge.builder()
            .source("B")
            .target("A")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        builder.addEdge(Edge.builder()
            .source("C")
            .target("A")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        Map<String, Double> pageRank = calculator.computePageRank();

        // A should have highest PageRank (two incoming edges)
        assertTrue(pageRank.get("A") > pageRank.get("B"));
        assertTrue(pageRank.get("A") > pageRank.get("C"));

        // B and C should have equal PageRank (symmetric positions)
        assertEquals(pageRank.get("B"), pageRank.get("C"), 0.001);
    }

    @Test
    void testBetweennessEmptyGraph() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        Map<String, Double> betweenness = calculator.computeBetweenness();

        assertTrue(betweenness.isEmpty());
    }

    @Test
    void testBetweennessStarTopology() {
        // B -> A <- C (A is central)
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("C").type(NodeType.CLASS).confidence(Confidence.HIGH).build());

        builder.addEdge(Edge.builder()
            .source("B")
            .target("A")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        builder.addEdge(Edge.builder()
            .source("C")
            .target("A")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        Map<String, Double> betweenness = calculator.computeBetweenness();

        // A should have highest betweenness (on all shortest paths)
        assertTrue(betweenness.get("A") > betweenness.get("B"));
        assertTrue(betweenness.get("A") > betweenness.get("C"));
    }

    @Test
    void testClosenessEmptyGraph() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        Map<String, Double> closeness = calculator.computeCloseness();

        assertTrue(closeness.isEmpty());
    }

    @Test
    void testClosenessSingleNode() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        Map<String, Double> closeness = calculator.computeCloseness();

        // Single node should have 0 closeness (no other nodes to reach)
        assertEquals(0.0, closeness.get("A"), 0.001);
    }

    @Test
    void testClosenessCentralNode() {
        // B -> A <- C (A is most central)
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("C").type(NodeType.CLASS).confidence(Confidence.HIGH).build());

        builder.addEdge(Edge.builder()
            .source("B")
            .target("A")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        builder.addEdge(Edge.builder()
            .source("C")
            .target("A")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        Map<String, Double> closeness = calculator.computeCloseness();

        // A should have highest closeness (closest to all other nodes)
        assertTrue(closeness.get("A") > closeness.get("B"));
        assertTrue(closeness.get("A") > closeness.get("C"));
    }

    @Test
    void testConnectedComponentsEmptyGraph() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        int components = calculator.computeConnectedComponents();

        assertEquals(0, components);
    }

    @Test
    void testConnectedComponentsSingleComponent() {
        // A -> B -> C (all connected)
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("C").type(NodeType.CLASS).confidence(Confidence.HIGH).build());

        builder.addEdge(Edge.builder()
            .source("A")
            .target("B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        builder.addEdge(Edge.builder()
            .source("B")
            .target("C")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        int components = calculator.computeConnectedComponents();

        assertEquals(1, components);
    }

    @Test
    void testConnectedComponentsDisconnected() {
        // A -> B and C -> D (two separate components)
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("C").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("D").type(NodeType.CLASS).confidence(Confidence.HIGH).build());

        builder.addEdge(Edge.builder()
            .source("A")
            .target("B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        builder.addEdge(Edge.builder()
            .source("C")
            .target("D")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        int components = calculator.computeConnectedComponents();

        assertEquals(2, components);
    }

    @Test
    void testFindBridgesEmptyGraph() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        var bridges = calculator.findBridges();

        assertTrue(bridges.isEmpty());
    }

    @Test
    void testFindBridgesLinearChain() {
        // A -> B -> C (each edge is a bridge)
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("C").type(NodeType.CLASS).confidence(Confidence.HIGH).build());

        builder.addEdge(Edge.builder()
            .source("A")
            .target("B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        builder.addEdge(Edge.builder()
            .source("B")
            .target("C")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        var bridges = calculator.findBridges();

        // Both edges should be bridges
        assertTrue(bridges.contains("A->B"));
        assertTrue(bridges.contains("B->C"));
    }

    @Test
    void testFindBridgesWithCycle() {
        // A -> B -> C -> A (cycle, no bridges)
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("C").type(NodeType.CLASS).confidence(Confidence.HIGH).build());

        builder.addEdge(Edge.builder()
            .source("A")
            .target("B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        builder.addEdge(Edge.builder()
            .source("B")
            .target("C")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        builder.addEdge(Edge.builder()
            .source("C")
            .target("A")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        var bridges = calculator.findBridges();

        // No bridges in a cycle
        assertTrue(bridges.isEmpty());
    }
}
