package com.archon.viz;

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

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for archon-viz JsonSerializer with metadata support.
 */
class JsonSerializerTest {

    @Test
    void testToJsonWithMetadataFlag() throws Exception {
        // Given: a graph with nodes and metadata
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("com.example.Foo")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        Map<String, String> domains = Map.of("com.example.Foo", "CORE");
        List<List<String>> cycles = List.of();
        List<Node> hotspots = List.of();
        List<BlindSpot> blindSpots = List.of();

        // When: serializing with withMetadata=true
        JsonSerializer serializer = new JsonSerializer();
        String json = serializer.toJson(graph, domains, cycles, hotspots, blindSpots, true);

        // Then: metadata should be present
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("nodes"));
        JsonNode node = root.get("nodes").get(0);
        assertTrue(node.has("metadata"));
        assertTrue(node.get("metadata").has("metrics"));
        assertTrue(node.get("metadata").has("issues"));
    }

    @Test
    void testToJsonWithoutMetadataFlag() throws Exception {
        // Given: a graph with nodes
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("com.example.Foo")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        Map<String, String> domains = Map.of("com.example.Foo", "CORE");
        List<List<String>> cycles = List.of();
        List<Node> hotspots = List.of();
        List<BlindSpot> blindSpots = List.of();

        // When: serializing with withMetadata=false
        JsonSerializer serializer = new JsonSerializer();
        String json = serializer.toJson(graph, domains, cycles, hotspots, blindSpots, false);

        // Then: metadata should NOT be present
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode node = root.get("nodes").get(0);
        assertFalse(node.has("metadata"), "metadata should not be present when withMetadata=false");
    }

    @Test
    void testToJsonWithFullAnalysisTier2() throws Exception {
        // Given: a graph with full analysis data
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("com.example.Foo")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();
        Map<String, String> domains = Map.of("com.example.Foo", "CORE");
        List<List<String>> cycles = List.of();
        List<Node> hotspots = List.of();
        List<BlindSpot> blindSpots = List.of();

        JsonSerializer.FullAnalysisData fullAnalysis = new JsonSerializer.FullAnalysisData(
            Map.of("com.example.Foo", 0.087),  // pageRank
            Map.of("com.example.Foo", 0.034),  // betweenness
            Map.of("com.example.Foo", 0.125),  // closeness
            1,  // connectedComponents
            new HashSet<>()  // bridges
        );

        // When: serializing with Tier 2 full analysis
        JsonSerializer serializer = new JsonSerializer();
        String json = serializer.toJson(graph, domains, cycles, hotspots, blindSpots, true, fullAnalysis);

        // Then: Tier 2 metadata should be present
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("fullAnalysis"));
        assertEquals(1, root.get("fullAnalysis").get("connectedComponents").asInt());

        JsonNode node = root.get("nodes").get(0);
        JsonNode metrics = node.get("metadata").get("metrics");

        assertTrue(metrics.has("pageRank"));
        assertEquals(0.087, metrics.get("pageRank").asDouble(), 0.001);

        assertTrue(metrics.has("betweenness"));
        assertEquals(0.034, metrics.get("betweenness").asDouble(), 0.001);

        assertTrue(metrics.has("closeness"));
        assertEquals(0.125, metrics.get("closeness").asDouble(), 0.001);
    }

    @Test
    void testSchemaVersionIncluded() throws Exception {
        // Given: a simple graph
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("com.example.Foo")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();

        // When: serializing
        JsonSerializer serializer = new JsonSerializer();
        String json = serializer.toJson(graph, Map.of(), List.of(), List.of(), List.of());

        // Then: schema and version should be present
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("$schema"));
        assertEquals("archon-metadata-v1", root.get("$schema").asText());

        assertTrue(root.has("version"));
        assertEquals("1.0.0", root.get("version").asText());
    }

    @Test
    void testFullAnalysisDataConstructorAndGetters() {
        // Given: FullAnalysisData parameters
        Map<String, Double> pageRank = Map.of("A", 0.5);
        Map<String, Double> betweenness = Map.of("A", 0.3);
        Map<String, Double> closeness = Map.of("A", 0.7);
        int components = 1;
        Set<String> bridges = new HashSet<>();

        // When: constructing FullAnalysisData
        JsonSerializer.FullAnalysisData data = new JsonSerializer.FullAnalysisData(
            pageRank, betweenness, closeness, components, bridges
        );

        // Then: getters should return correct values
        assertEquals(pageRank, data.getPageRank());
        assertEquals(betweenness, data.getBetweenness());
        assertEquals(closeness, data.getCloseness());
        assertEquals(components, data.getConnectedComponents());
        assertEquals(bridges, data.getBridges());
    }

    @Test
    void testCalculateRiskLevelThresholds() throws Exception {
        // Given: a graph with varying fanIn/fanOut
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        // Create 25 nodes that all depend on H (fanIn = 25 > RISK_HIGH_FAN_THRESHOLD)
        for (int i = 0; i < 25; i++) {
            String nodeId = "Dep" + i;
            builder.addNode(Node.builder()
                .id(nodeId)
                .type(NodeType.CLASS)
                .confidence(Confidence.HIGH)
                .build());

            builder.addNode(Node.builder()
                .id("H")
                .type(NodeType.CLASS)
                .confidence(Confidence.HIGH)
                .build());

            // Each node depends on H
            builder.addEdge(Edge.builder()
                .source(nodeId)
                .target("H")
                .type(EdgeType.IMPORTS)
                .confidence(Confidence.HIGH)
                .build());
        }

        // Create node M with 15 dependents (fanIn = 15, between thresholds)
        for (int i = 0; i < 15; i++) {
            String nodeId = "Med" + i;
            builder.addNode(Node.builder()
                .id(nodeId)
                .type(NodeType.CLASS)
                .confidence(Confidence.HIGH)
                .build());

            builder.addNode(Node.builder()
                .id("M")
                .type(NodeType.CLASS)
                .confidence(Confidence.HIGH)
                .build());

            builder.addEdge(Edge.builder()
                .source(nodeId)
                .target("M")
                .type(EdgeType.IMPORTS)
                .confidence(Confidence.HIGH)
                .build());
        }

        // Create node L with 5 dependents (fanIn = 5 < RISK_MEDIUM_FAN_THRESHOLD)
        for (int i = 0; i < 5; i++) {
            String nodeId = "Low" + i;
            builder.addNode(Node.builder()
                .id(nodeId)
                .type(NodeType.CLASS)
                .confidence(Confidence.HIGH)
                .build());

            builder.addNode(Node.builder()
                .id("L")
                .type(NodeType.CLASS)
                .confidence(Confidence.HIGH)
                .build());

            builder.addEdge(Edge.builder()
                .source(nodeId)
                .target("L")
                .type(EdgeType.IMPORTS)
                .confidence(Confidence.HIGH)
                .build());
        }

        DependencyGraph graph = builder.build();

        // When: calculating risk levels via JSON serialization with metadata
        JsonSerializer serializer = new JsonSerializer();
        String json = serializer.toJson(graph, Map.of(), List.of(), List.of(), List.of(), true);

        // Then: risk levels should match thresholds
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        // Find each node and check its risk level
        JsonNode nodes = root.get("nodes");
        boolean foundHigh = false;
        boolean foundMedium = false;
        boolean foundLow = false;

        for (JsonNode node : nodes) {
            String id = node.get("id").asText();
            if (id.equals("H")) {
                String risk = node.get("metadata").get("metrics").get("riskLevel").asText();
                assertEquals("high", risk, "H with fanIn=25 should be high risk");
                foundHigh = true;
            } else if (id.equals("M")) {
                String risk = node.get("metadata").get("metrics").get("riskLevel").asText();
                assertEquals("medium", risk, "M with fanIn=15 should be medium risk");
                foundMedium = true;
            } else if (id.equals("L")) {
                String risk = node.get("metadata").get("metrics").get("riskLevel").asText();
                assertEquals("low", risk, "L with fanIn=5 should be low risk");
                foundLow = true;
            }
        }

        assertTrue(foundHigh && foundMedium && foundLow, "All risk levels should be found");
    }

    @Test
    void testCalculateImpactScore() throws Exception {
        // Given: a graph with varying dependency counts
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        // Node with high fanIn + fanOut (total = 50)
        builder.addNode(Node.builder().id("High").type(NodeType.CLASS).confidence(Confidence.HIGH).build());
        for (int i = 0; i < 25; i++) {
            builder.addNode(Node.builder().id("DepIn" + i).type(NodeType.CLASS).confidence(Confidence.HIGH).build());
            builder.addEdge(Edge.builder()
                .source("DepIn" + i)
                .target("High")
                .type(EdgeType.IMPORTS)
                .confidence(Confidence.HIGH)
                .build());
        }
        for (int i = 0; i < 25; i++) {
            builder.addNode(Node.builder().id("DepOut" + i).type(NodeType.CLASS).confidence(Confidence.HIGH).build());
            builder.addEdge(Edge.builder()
                .source("High")
                .target("DepOut" + i)
                .type(EdgeType.IMPORTS)
                .confidence(Confidence.HIGH)
                .build());
        }

        DependencyGraph graph = builder.build();

        // When: serializing with metadata
        JsonSerializer serializer = new JsonSerializer();
        String json = serializer.toJson(graph, Map.of(), List.of(), List.of(), List.of(), true);

        // Then: impact score should be higher for node with more dependencies
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        JsonNode nodes = root.get("nodes");

        for (JsonNode node : nodes) {
            if (node.get("id").asText().equals("High")) {
                double impactScore = node.get("metadata").get("metrics").get("impactScore").asDouble();
                assertTrue(impactScore > 0.0, "Impact score should be positive");
                assertTrue(impactScore <= 1.0, "Impact score should be <= 1.0");
            }
        }
    }

    @Test
    void testValidateFullAnalysisDataMissingNodes() throws Exception {
        // Given: graph with 3 nodes, but PageRank only has 2
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("com.example.A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        builder.addNode(Node.builder()
            .id("com.example.B")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        builder.addNode(Node.builder()
            .id("com.example.C")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();

        // FullAnalysisData missing node C
        JsonSerializer.FullAnalysisData fullAnalysis = new JsonSerializer.FullAnalysisData(
            Map.of("com.example.A", 0.5, "com.example.B", 0.3),  // Missing C
            Map.of("com.example.A", 0.2, "com.example.B", 0.1, "com.example.C", 0.0),
            Map.of("com.example.A", 0.4, "com.example.B", 0.2, "com.example.C", 0.1),
            1,
            new HashSet<>()
        );

        // When/Then: should throw IllegalStateException with detailed error message
        JsonSerializer serializer = new JsonSerializer();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            serializer.toJson(graph, Map.of(), List.of(), List.of(), List.of(), true, fullAnalysis);
        });

        // Verify error message contains expected details (node count and node ID)
        assertTrue(exception.getMessage().contains("PageRank missing 1 nodes"),
            "Error message should indicate PageRank is missing 1 node");
        assertTrue(exception.getMessage().contains("com.example.C"),
            "Error message should include the missing node ID");
    }
}
