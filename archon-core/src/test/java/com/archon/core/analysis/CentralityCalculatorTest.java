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

    @Test
    void testFindBridgesDiamondTopology() {
        // Diamond: A->B, A->C, B->D, C->D (no bridges)
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
            .source("A")
            .target("C")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        builder.addEdge(Edge.builder()
            .source("B")
            .target("D")
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

        var bridges = calculator.findBridges();

        // Diamond should have no bridges
        assertTrue(bridges.isEmpty(), "Diamond topology should have no bridges");
    }

    @Test
    void testFindBridgesMultipleBridges() {
        // A->B, B->C, C->D, D->E with an alternate path A->C->E (creates multiple bridges)
        // Actually: A->B->C->D->E (linear chain is all bridges)
        // Let's create: A->B->C->D and A->E (where A->B and A->E are bridges to different components)
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("C").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("D").type(NodeType.CLASS).confidence(Confidence.HIGH).build());

        // Linear chain A->B->C->D (all bridges)
        builder.addEdge(Edge.builder().source("A").target("B").type(EdgeType.IMPORTS).confidence(Confidence.HIGH).build());
        builder.addEdge(Edge.builder().source("B").target("C").type(EdgeType.IMPORTS).confidence(Confidence.HIGH).build());
        builder.addEdge(Edge.builder().source("C").target("D").type(EdgeType.IMPORTS).confidence(Confidence.HIGH).build());

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        var bridges = calculator.findBridges();

        // All three edges should be bridges in a linear chain
        assertEquals(3, bridges.size(), "Linear chain should have 3 bridges");
        assertTrue(bridges.contains("A->B"));
        assertTrue(bridges.contains("B->C"));
        assertTrue(bridges.contains("C->D"));
    }

    @Test
    void testPageRankWithInvalidDampingFactor() {
        // A -> B
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).confidence(Confidence.HIGH).build());

        builder.addEdge(Edge.builder()
            .source("A")
            .target("B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        // Test with invalid damping factors - algorithm should still work but may produce unexpected results
        // Negative damping factor should be handled (defaults to treating as valid)
        Map<String, Double> result1 = calculator.computePageRank(-0.5, 30, 1e-6);
        assertNotNull(result1);
        assertFalse(result1.isEmpty());

        // Damping factor > 1.0 should also be handled
        Map<String, Double> result2 = calculator.computePageRank(1.5, 30, 1e-6);
        assertNotNull(result2);
        assertFalse(result2.isEmpty());
    }

    @Test
    void testPageRankWithZeroIterations() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).confidence(Confidence.HIGH).build());

        builder.addEdge(Edge.builder()
            .source("A")
            .target("B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        // Zero iterations means no computation - should return initial equal distribution
        Map<String, Double> result = calculator.computePageRank(0.85, 0, 1e-6);
        assertEquals(2, result.size());
        assertEquals(0.5, result.get("A"), 0.001);
        assertEquals(0.5, result.get("B"), 0.001);
    }

    @Test
    void testClosenessDisconnectedNodes() {
        // A -> B, C (isolated)
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

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        Map<String, Double> closeness = calculator.computeCloseness();

        // Isolated node C should have 0 closeness (cannot reach other nodes)
        assertEquals(0.0, closeness.get("C"), 0.001, "Isolated node should have 0 closeness");

        // A and B each reach only 1 other node, so closeness is 0 (need > 1 reachable nodes for positive closeness)
        // Standard closeness formula requires reachableCount > 1 for non-zero closeness
        assertEquals(0.0, closeness.get("A"), 0.001, "A should have 0 closeness (only reaches B)");
        assertEquals(0.0, closeness.get("B"), 0.001, "B should have 0 closeness (only reaches A)");
    }

    @Test
    void testFindBridgesDeepGraph() {
        // Create a linear chain of 1500 nodes (deeper than default stack)
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        // Build chain: N0 -> N1 -> N2 -> ... -> N1499
        String prev = null;
        for (int i = 0; i < 1500; i++) {
            String nodeId = "N" + i;
            builder.addNode(Node.builder()
                .id(nodeId)
                .type(NodeType.CLASS)
                .confidence(Confidence.HIGH)
                .build());

            if (prev != null) {
                builder.addEdge(Edge.builder()
                    .source(prev)
                    .target(nodeId)
                    .type(EdgeType.IMPORTS)
                    .confidence(Confidence.HIGH)
                    .build());
            }
            prev = nodeId;
        }

        DependencyGraph graph = builder.build();
        CentralityCalculator calculator = new CentralityCalculator(graph);

        // Should not throw StackOverflowError
        var bridges = calculator.findBridges();

        // All edges in a linear chain are bridges
        assertEquals(1499, bridges.size());
    }
}
