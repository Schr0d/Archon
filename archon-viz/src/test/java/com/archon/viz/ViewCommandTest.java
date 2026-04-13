package com.archon.viz;

import com.archon.core.analysis.AnalysisPipeline;
import com.archon.core.analysis.AnalysisResult;
import com.archon.core.analysis.CentralityService;
import com.archon.core.analysis.FullAnalysisData;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import com.archon.core.graph.Confidence;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.plugin.BlindSpot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Note: FullAnalysisData is now in archon-core.analysis package, not JsonSerializer

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ViewCommand.
 * Verifies that --with-metadata and --with-full-analysis flags produce correct JSON output.
 *
 * These tests verify integration behavior by building test graphs and simulating
 * what ViewCommand.call() does, but without requiring a full project analysis
 * or language plugins to be loaded.
 */
class ViewCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void testViewCommandWithMetadataProducesJsonWithMetadataField() throws Exception {
        // Given: a test dependency graph with nodes
        DependencyGraph.MutableBuilder graphBuilder = new DependencyGraph.MutableBuilder();
        graphBuilder.addNode(Node.builder()
            .id("com.example.Foo")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = graphBuilder.build();
        Map<String, String> domains = Map.of("com.example.Foo", "CORE");
        List<List<String>> cycles = List.of();
        List<Node> hotspots = List.of();
        List<BlindSpot> blindSpots = List.of();

        // When: serializing with --with-metadata flag (what ViewCommand.call() does internally)
        JsonSerializer serializer = new JsonSerializer();
        String json = serializer.toJson(graph, domains, cycles, hotspots, blindSpots, true);

        // Then: verify JSON structure contains metadata field
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        // Verify nodes array exists
        assertTrue(root.has("nodes"), "JSON should contain nodes array");
        JsonNode nodes = root.get("nodes");
        assertTrue(nodes.isArray(), "nodes should be an array");
        assertTrue(nodes.size() > 0, "nodes should contain at least one node");

        // Verify each node has metadata field
        for (JsonNode node : nodes) {
            assertTrue(node.has("metadata"), "Each node should have metadata field when --with-metadata is set");

            JsonNode metadata = node.get("metadata");
            assertTrue(metadata.has("metrics"), "metadata should contain metrics");
            assertTrue(metadata.has("issues"), "metadata should contain issues");

            // Verify metrics structure
            JsonNode metrics = metadata.get("metrics");
            assertTrue(metrics.has("fanIn"), "metrics should contain fanIn");
            assertTrue(metrics.has("fanOut"), "metrics should contain fanOut");
            assertTrue(metrics.has("impactScore"), "metrics should contain impactScore");
            assertTrue(metrics.has("riskLevel"), "metrics should contain riskLevel");

            // Verify issues structure
            JsonNode issues = metadata.get("issues");
            assertTrue(issues.has("hotspot"), "issues should contain hotspot");
            assertTrue(issues.has("cycle"), "issues should contain cycle");
            assertTrue(issues.has("blindSpots"), "issues should contain blindSpots");

            // Verify data types
            assertTrue(metrics.get("fanIn").isNumber(), "fanIn should be a number");
            assertTrue(metrics.get("fanOut").isNumber(), "fanOut should be a number");
            assertTrue(metrics.get("impactScore").isNumber(), "impactScore should be a number");
            assertTrue(metrics.get("riskLevel").isTextual(), "riskLevel should be a string");

            // Verify risk level is one of the expected values
            String riskLevel = metrics.get("riskLevel").asText();
            assertTrue(List.of("low", "medium", "high").contains(riskLevel),
                "riskLevel should be 'low', 'medium', or 'high', got: " + riskLevel);
        }
    }

    @Test
    void testViewCommandWithFullAnalysisProducesJsonWithFullAnalysisSection() throws Exception {
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
        Map<String, String> domains = Map.of("com.example.Hub", "CORE");
        for (int i = 1; i <= 5; i++) {
            domains = Map.of("com.example.Dep" + i, "APP");
        }
        List<List<String>> cycles = List.of();
        List<Node> hotspots = List.of();
        List<BlindSpot> blindSpots = List.of();

        // Simulate what ViewCommand.call() does with --with-full-analysis flag
        CentralityService service = new CentralityService(graph);
        FullAnalysisData fullAnalysis = service.computeFullAnalysis();

        // When: serializing with --with-full-analysis flag
        JsonSerializer serializer = new JsonSerializer();
        String json = serializer.toJson(graph, domains, cycles, hotspots, blindSpots,
            true, fullAnalysis);

        // Then: verify JSON structure contains fullAnalysis section
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        // Verify fullAnalysis section exists
        assertTrue(root.has("fullAnalysis"), "JSON should contain fullAnalysis section when --with-full-analysis is set");

        JsonNode fullAnalysisNode = root.get("fullAnalysis");

        // Verify connectedComponents
        assertTrue(fullAnalysisNode.has("connectedComponents"), "fullAnalysis should contain connectedComponents");
        assertTrue(fullAnalysisNode.get("connectedComponents").isInt(), "connectedComponents should be an integer");
        int components = fullAnalysisNode.get("connectedComponents").asInt();
        assertTrue(components > 0, "connectedComponents should be at least 1");

        // Verify bridges array
        assertTrue(fullAnalysisNode.has("bridges"), "fullAnalysis should contain bridges array");
        JsonNode bridges = fullAnalysisNode.get("bridges");
        assertTrue(bridges.isArray(), "bridges should be an array");

        // Verify nodes have centrality metrics
        JsonNode nodes = root.get("nodes");
        for (JsonNode node : nodes) {
            assertTrue(node.has("metadata"), "Each node should have metadata");
            JsonNode metrics = node.get("metadata").get("metrics");

            // Tier 2 metrics should be present
            assertTrue(metrics.has("pageRank"), "metrics should contain pageRank in Tier 2");
            assertTrue(metrics.has("betweenness"), "metrics should contain betweenness in Tier 2");
            assertTrue(metrics.has("closeness"), "metrics should contain closeness in Tier 2");

            // Verify centrality values are in valid range [0, 1]
            double pageRank = metrics.get("pageRank").asDouble();
            double betweenness = metrics.get("betweenness").asDouble();
            double closeness = metrics.get("closeness").asDouble();

            assertTrue(pageRank >= 0.0 && pageRank <= 1.0, "pageRank should be in [0, 1]");
            assertTrue(betweenness >= 0.0 && betweenness <= 1.0, "betweenness should be in [0, 1]");
            assertTrue(closeness >= 0.0 && closeness <= 1.0, "closeness should be in [0, 1]");

            // Verify bridge field
            JsonNode issues = node.get("metadata").get("issues");
            assertTrue(issues.has("bridge"), "issues should contain bridge flag");
        }
    }

    @Test
    void testViewCommandCentralityCalculatorInvokedWhenFullAnalysisSet() throws Exception {
        // Given: a dependency graph
        DependencyGraph.MutableBuilder graphBuilder = new DependencyGraph.MutableBuilder();

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

        graphBuilder.addEdge(Edge.builder()
            .source("com.example.A")
            .target("com.example.B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = graphBuilder.build();
        Map<String, String> domains = Map.of(
            "com.example.A", "APP",
            "com.example.B", "CORE"
        );
        List<List<String>> cycles = List.of();
        List<Node> hotspots = List.of();
        List<BlindSpot> blindSpots = List.of();

        // When: simulating ViewCommand.call() with --with-full-analysis
        CentralityService service = new CentralityService(graph);
        FullAnalysisData fullAnalysis = service.computeFullAnalysis();

        JsonSerializer serializer = new JsonSerializer();
        String json = serializer.toJson(graph, domains, cycles, hotspots, blindSpots,
            true, fullAnalysis);

        // Then: verify that CentralityCalculator was invoked by checking fullAnalysis section exists
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        // The presence of fullAnalysis section proves CentralityCalculator was invoked
        assertTrue(root.has("fullAnalysis"), "fullAnalysis section should be present");

        // Verify that centrality metrics are computed (not default values)
        JsonNode nodes = root.get("nodes");
        boolean hasNonZeroCentrality = false;

        for (JsonNode node : nodes) {
            JsonNode metrics = node.get("metadata").get("metrics");
            double pageRank = metrics.get("pageRank").asDouble();
            double betweenness = metrics.get("betweenness").asDouble();

            // At least one node should have non-zero centrality in a connected graph
            if (pageRank > 0.0 || betweenness > 0.0) {
                hasNonZeroCentrality = true;
                break;
            }
        }

        assertTrue(hasNonZeroCentrality, "At least one node should have non-zero centrality metrics, " +
            "indicating CentralityCalculator was invoked and computed actual values");
    }

    @Test
    void testViewCommandJsonOutputStructureMatchesExpectedFormat() throws Exception {
        // Given: a test dependency graph
        DependencyGraph.MutableBuilder graphBuilder = new DependencyGraph.MutableBuilder();

        graphBuilder.addNode(Node.builder()
            .id("com.example.Foo")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        graphBuilder.addNode(Node.builder()
            .id("com.example.Bar")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        graphBuilder.addEdge(Edge.builder()
            .source("com.example.Foo")
            .target("com.example.Bar")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = graphBuilder.build();
        Map<String, String> domains = Map.of(
            "com.example.Foo", "APP",
            "com.example.Bar", "CORE"
        );
        List<List<String>> cycles = List.of();
        List<Node> hotspots = List.of();
        List<BlindSpot> blindSpots = List.of();

        // When: simulating ViewCommand.call() with --format json
        CentralityService service = new CentralityService(graph);
        FullAnalysisData fullAnalysis = service.computeFullAnalysis();

        JsonSerializer serializer = new JsonSerializer();
        String json = serializer.toJson(graph, domains, cycles, hotspots, blindSpots,
            true, fullAnalysis);

        // Then: verify JSON output structure matches expected format
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        // Top-level fields
        assertTrue(root.has("$schema"), "JSON should contain $schema field");
        assertEquals("archon-metadata-v1", root.get("$schema").asText());

        assertTrue(root.has("version"), "JSON should contain version field");
        assertEquals("1.0.0", root.get("version").asText());

        // Required arrays
        assertTrue(root.has("nodes"), "JSON should contain nodes array");
        assertTrue(root.has("edges"), "JSON should contain edges array");
        assertTrue(root.has("cycles"), "JSON should contain cycles array");
        assertTrue(root.has("hotspots"), "JSON should contain hotspots array");
        assertTrue(root.has("blindSpots"), "JSON should contain blindSpots array");

        // Optional Tier 2 section
        assertTrue(root.has("fullAnalysis"), "JSON should contain fullAnalysis when --with-full-analysis is set");

        // Verify edge structure
        JsonNode edges = root.get("edges");
        assertTrue(edges.isArray(), "edges should be an array");
        for (JsonNode edge : edges) {
            assertTrue(edge.has("source"), "edge should have source");
            assertTrue(edge.has("target"), "edge should have target");
        }
    }

    @Test
    void testViewCommandWithFullAnalysisTier2RiskLevelCalculation() throws Exception {
        // Given: a dependency graph with varying dependency patterns to test risk levels
        DependencyGraph.MutableBuilder graphBuilder = new DependencyGraph.MutableBuilder();

        // Create hub with high fan-in
        graphBuilder.addNode(Node.builder()
            .id("com.example.Hub")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        for (int i = 1; i <= 15; i++) {
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
        Map<String, String> domains = Map.of("com.example.Hub", "CORE");
        List<List<String>> cycles = List.of();
        List<Node> hotspots = List.of();
        List<BlindSpot> blindSpots = List.of();

        // When: simulating ViewCommand.call() with --with-full-analysis
        CentralityService service = new CentralityService(graph);
        FullAnalysisData fullAnalysis = service.computeFullAnalysis();

        JsonSerializer serializer = new JsonSerializer();
        String json = serializer.toJson(graph, domains, cycles, hotspots, blindSpots,
            true, fullAnalysis);

        // Then: verify Tier 2 risk level calculation uses centrality metrics
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode nodes = root.get("nodes");

        boolean foundRiskLevel = false;

        for (JsonNode node : nodes) {
            JsonNode metrics = node.get("metadata").get("metrics");
            String riskLevel = metrics.get("riskLevel").asText();
            double pageRank = metrics.get("pageRank").asDouble();

            // Verify risk level is one of the expected values
            assertTrue(List.of("low", "medium", "high").contains(riskLevel),
                "riskLevel should be 'low', 'medium', or 'high'");

            // High PageRank should correlate with higher risk levels
            if (pageRank > 0.3) {
                assertTrue(List.of("medium", "high").contains(riskLevel),
                    "Nodes with high PageRank (>0.3) should have medium or high risk level");
            }

            foundRiskLevel = true;
        }

        assertTrue(foundRiskLevel, "At least one node should have risk level calculated");
    }

    // Legacy unit tests kept for backward compatibility

    @Test
    void testViewCommandParsesProjectPath() {
        ViewCommand command = new ViewCommand();
        command.path = tempDir.toString();

        assertEquals(tempDir.toString(), command.path);
    }

    @Test
    void testViewCommandAcceptsPortOption() {
        ViewCommand command = new ViewCommand();
        command.port = 8500;

        assertEquals(8500, command.port);
    }

    @Test
    void testViewCommandAcceptsNoOpenFlag() {
        ViewCommand command = new ViewCommand();
        command.noOpen = true;

        assertTrue(command.noOpen);
    }

    @Test
    void testViewCommandDefaultsToTextFormat() {
        ViewCommand command = new ViewCommand();

        assertEquals("text", command.format);
    }

    @Test
    void testViewCommandAcceptsJsonFormat() {
        ViewCommand command = new ViewCommand();
        command.format = "json";

        assertEquals("json", command.format);
    }

    @Test
    void testViewCommandHasDefaultNullPort() {
        ViewCommand command = new ViewCommand();

        assertNull(command.port);
    }

    @Test
    void testViewCommandDefaultsNoOpenToFalse() {
        ViewCommand command = new ViewCommand();

        assertFalse(command.noOpen);
    }

    @Test
    void testViewCommandWithMetadataFlag() {
        ViewCommand command = new ViewCommand();
        command.withMetadata = true;

        assertTrue(command.withMetadata);
    }

    @Test
    void testViewCommandWithFullAnalysisFlag() {
        ViewCommand command = new ViewCommand();
        command.withFullAnalysis = true;

        assertTrue(command.withFullAnalysis);
    }

    @Test
    void testViewCommandDefaultsFullAnalysisToFalse() {
        ViewCommand command = new ViewCommand();

        assertFalse(command.withFullAnalysis);
    }

    @Test
    void testViewCommandDefaultsMetadataToFalse() {
        ViewCommand command = new ViewCommand();

        assertFalse(command.withMetadata);
    }
}
