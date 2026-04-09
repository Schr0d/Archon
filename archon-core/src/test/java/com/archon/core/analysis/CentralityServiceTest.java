package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import com.archon.core.graph.Confidence;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CentralityService.
 * Verifies that the service correctly wraps CentralityCalculator and returns FullAnalysisData.
 */
class CentralityServiceTest {

    @Test
    void testComputeFullAnalysisHappyPath() {
        // Given: a dependency graph with edges for centrality calculation
        DependencyGraph.MutableBuilder graphBuilder = new DependencyGraph.MutableBuilder();

        // Create a hub node and dependent nodes
        graphBuilder.addNode(Node.builder()
            .id("com.example.Hub")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        for (int i = 1; i <= 5; i++) {
            String nodeId = "com.example.Dep" + i;
            graphBuilder.addNode(Node.builder()
                .id(nodeId)
                .type(NodeType.CLASS)
                .confidence(Confidence.HIGH)
                .build());

            graphBuilder.addEdge(Edge.builder()
                .source(nodeId)
                .target("com.example.Hub")
                .type(EdgeType.IMPORTS)
                .confidence(Confidence.HIGH)
                .build());
        }

        DependencyGraph graph = graphBuilder.build();
        CentralityService service = new CentralityService(graph);

        // When: computing full analysis
        FullAnalysisData fullAnalysis = service.computeFullAnalysis();

        // Then: verify all metric types are present and non-null
        assertNotNull(fullAnalysis, "FullAnalysisData should not be null");
        assertNotNull(fullAnalysis.getPageRank(), "PageRank should not be null");
        assertNotNull(fullAnalysis.getBetweenness(), "Betweenness should not be null");
        assertNotNull(fullAnalysis.getCloseness(), "Closeness should not be null");
        assertNotNull(fullAnalysis.getBridges(), "Bridges should not be null");

        // Verify metrics contain expected nodes
        assertTrue(fullAnalysis.getPageRank().containsKey("com.example.Hub"), "PageRank should contain Hub");
        assertTrue(fullAnalysis.getBetweenness().containsKey("com.example.Hub"), "Betweenness should contain Hub");
        assertTrue(fullAnalysis.getCloseness().containsKey("com.example.Hub"), "Closeness should contain Hub");

        // Verify connected components
        assertTrue(fullAnalysis.getConnectedComponents() > 0, "Should have at least one connected component");
    }

    @Test
    void testComputeFullAnalysisEmptyGraph() {
        // Given: an empty graph
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        CentralityService service = new CentralityService(graph);

        // When: computing full analysis on empty graph
        FullAnalysisData fullAnalysis = service.computeFullAnalysis();

        // Then: should handle gracefully with empty collections
        assertNotNull(fullAnalysis, "FullAnalysisData should not be null even for empty graph");
        assertTrue(fullAnalysis.getPageRank().isEmpty(), "PageRank should be empty");
        assertTrue(fullAnalysis.getBetweenness().isEmpty(), "Betweenness should be empty");
        assertTrue(fullAnalysis.getCloseness().isEmpty(), "Closeness should be empty");
        assertTrue(fullAnalysis.getBridges().isEmpty(), "Bridges should be empty");
        assertEquals(0, fullAnalysis.getConnectedComponents(), "Empty graph has zero components");
    }

    @Test
    void testComputeFullAnalysisWrapsCentralityCalculatorCorrectly() {
        // Given: a simple graph with known centrality properties
        DependencyGraph.MutableBuilder graphBuilder = new DependencyGraph.MutableBuilder();

        // A -> B -> C chain
        graphBuilder.addNode(Node.builder()
            .id("com.example.A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        graphBuilder.addNode(Node.builder()
            .id("com.example.B")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        graphBuilder.addNode(Node.builder()
            .id("com.example.C")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        graphBuilder.addEdge(Edge.builder()
            .source("com.example.A")
            .target("com.example.B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        graphBuilder.addEdge(Edge.builder()
            .source("com.example.B")
            .target("com.example.C")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = graphBuilder.build();

        // Create service and compute
        CentralityService service = new CentralityService(graph);
        FullAnalysisData fullAnalysis = service.computeFullAnalysis();

        // Also compute directly via CentralityCalculator to verify wrapping
        CentralityCalculator calculator = new CentralityCalculator(graph);

        // Then: verify service returns same results as direct calculator usage
        Map<String, Double> servicePageRank = fullAnalysis.getPageRank();
        Map<String, Double> calculatorPageRank = calculator.computePageRank();

        assertEquals(calculatorPageRank.size(), servicePageRank.size(),
            "Service should wrap calculator correctly (same number of nodes)");

        for (String nodeId : servicePageRank.keySet()) {
            assertTrue(calculatorPageRank.containsKey(nodeId),
                "Service pageRank should contain same nodes as calculator");
            assertEquals(calculatorPageRank.get(nodeId), servicePageRank.get(nodeId),
                "Service pageRank values should match calculator values");
        }
    }
}
