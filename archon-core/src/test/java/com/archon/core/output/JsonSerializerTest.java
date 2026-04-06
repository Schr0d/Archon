package com.archon.core.output;

import com.archon.core.graph.*;
import com.archon.core.plugin.BlindSpot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsonSerializer.
 */
class JsonSerializerTest {

    @Test
    void testEmptyGraph() throws Exception {
        // Arrange
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        JsonSerializer serializer = new JsonSerializer();

        // Act
        String json = serializer.toJson(graph);

        // Assert
        assertNotNull(json);
        JsonNode root = new ObjectMapper().readTree(json);
        assertTrue(root.has("nodes"));
        assertTrue(root.has("edges"));
        assertTrue(root.has("stats"));
        assertTrue(root.get("stats").has("nodeCount"));
        assertTrue(root.get("stats").has("edgeCount"));
    }

    @Test
    void testGraphWithNodes() throws Exception {
        // Arrange
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        Node node1 = Node.builder()
            .id("com.example.ClassA")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build();

        Node node2 = Node.builder()
            .id("com.example.ClassB")
            .type(NodeType.CLASS)
            .confidence(Confidence.MEDIUM)
            .sourcePath("src/main/java/com/example/ClassB.java")
            .build();

        builder.addNode(node1);
        builder.addNode(node2);

        DependencyGraph graph = builder.build();
        JsonSerializer serializer = new JsonSerializer();

        // Act
        String json = serializer.toJson(graph);

        // Assert
        assertNotNull(json);
        JsonNode root = new ObjectMapper().readTree(json);
        JsonNode nodesArray = root.get("nodes");
        assertEquals(2, nodesArray.size());

        // Verify first node
        JsonNode node1Json = nodesArray.get(0);
        assertEquals("com.example.ClassA", node1Json.get("id").asText());
        assertEquals("CLASS", node1Json.get("type").asText());
        assertEquals("HIGH", node1Json.get("confidence").asText());

        // Verify second node
        JsonNode node2Json = nodesArray.get(1);
        assertEquals("com.example.ClassB", node2Json.get("id").asText());
        assertEquals("MEDIUM", node2Json.get("confidence").asText());
        assertEquals("src/main/java/com/example/ClassB.java", node2Json.get("sourcePath").asText());
    }

    @Test
    void testGraphWithEdges() throws Exception {
        // Arrange
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        Node source = Node.builder()
            .id("com.example.Source")
            .type(NodeType.CLASS)
            .build();

        Node target = Node.builder()
            .id("com.example.Target")
            .type(NodeType.CLASS)
            .build();

        Edge edge = Edge.builder()
            .source("com.example.Source")
            .target("com.example.Target")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .dynamic(false)
            .build();

        builder.addNode(source);
        builder.addNode(target);
        builder.addEdge(edge);

        DependencyGraph graph = builder.build();
        JsonSerializer serializer = new JsonSerializer();

        // Act
        String json = serializer.toJson(graph);

        // Assert
        assertNotNull(json);
        JsonNode root = new ObjectMapper().readTree(json);
        JsonNode edgesArray = root.get("edges");
        assertEquals(1, edgesArray.size());

        JsonNode edgeJson = edgesArray.get(0);
        assertEquals("com.example.Source", edgeJson.get("source").asText());
        assertEquals("com.example.Target", edgeJson.get("target").asText());
        assertEquals("IMPORTS", edgeJson.get("type").asText());
        assertEquals("HIGH", edgeJson.get("confidence").asText());
        assertFalse(edgeJson.get("dynamic").asBoolean());
    }

    @Test
    void testGraphWithDomains() throws Exception {
        // Arrange
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        Node node1 = Node.builder()
            .id("com.example.service.ServiceA")
            .type(NodeType.SERVICE)
            .domain("service")
            .build();

        Node node2 = Node.builder()
            .id("com.example.controller.ControllerA")
            .type(NodeType.CONTROLLER)
            .domain("controller")
            .build();

        builder.addNode(node1);
        builder.addNode(node2);

        DependencyGraph graph = builder.build();
        JsonSerializer serializer = new JsonSerializer();

        // Act
        String json = serializer.toJson(graph);

        // Assert
        assertNotNull(json);
        JsonNode root = new ObjectMapper().readTree(json);
        JsonNode nodesArray = root.get("nodes");
        assertEquals(2, nodesArray.size());

        // Verify first node with domain
        JsonNode node1Json = nodesArray.get(0);
        assertEquals("com.example.service.ServiceA", node1Json.get("id").asText());
        assertTrue(node1Json.has("domain"));
        assertEquals("service", node1Json.get("domain").asText());

        // Verify second node with domain
        JsonNode node2Json = nodesArray.get(1);
        assertEquals("com.example.controller.ControllerA", node2Json.get("id").asText());
        assertTrue(node2Json.has("domain"));
        assertEquals("controller", node2Json.get("domain").asText());
    }

    @Test
    void testFullAnalysisResultSerialization() throws Exception {
        // Build test data
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        Node foo = Node.builder().id("com.example.Foo").type(NodeType.CLASS).build();
        Node bar = Node.builder().id("com.example.Bar").type(NodeType.CLASS).build();
        builder.addNode(foo);
        builder.addNode(bar);
        builder.addEdge(Edge.builder()
            .source("com.example.Foo")
            .target("com.example.Bar")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());

        DependencyGraph graph = builder.build();

        Map<String, String> domains = Map.of("com.example.Foo", "persistence", "com.example.Bar", "api");
        List<List<String>> cycles = List.of(List.of("A", "B", "A"));
        List<Node> hotspots = List.of(graph.getNode("com.example.Foo").orElseThrow());
        List<BlindSpot> blindSpots = List.of(
            new BlindSpot("reflection", "com.example.ConfigFactory", "Dynamic class loading")
        );

        JsonSerializer serializer = new JsonSerializer();
        String json = serializer.toJson(graph, domains, cycles, hotspots, blindSpots);

        assertNotNull(json);

        // Parse and verify structure
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        // Verify graph section
        assertTrue(root.has("graph"));

        // Verify domains
        assertTrue(root.has("domains"));
        JsonNode domainsObj = root.get("domains");
        assertTrue(domainsObj.hasNonNull("persistence"));
        assertTrue(domainsObj.hasNonNull("api"));

        // Verify cycles
        assertTrue(root.has("cycles"));
        JsonNode cyclesArray = root.get("cycles");
        assertEquals(1, cyclesArray.size());

        // Verify hotspots
        assertTrue(root.has("hotspots"));
        JsonNode hotspotsArray = root.get("hotspots");
        assertEquals(1, hotspotsArray.size());
        assertEquals("com.example.Foo", hotspotsArray.get(0).asText());

        // Verify blindSpots
        assertTrue(root.has("blindSpots"));
        JsonNode blindSpotsArray = root.get("blindSpots");
        assertEquals(1, blindSpotsArray.size());
        assertEquals("reflection", blindSpotsArray.get(0).get("type").asText());
    }

    @Test
    void testEmptyCollections() throws Exception {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        JsonSerializer serializer = new JsonSerializer();
        String json = serializer.toJson(
            graph,
            Map.of(),
            List.of(),
            List.of(),
            List.of()
        );

        assertNotNull(json);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("domains"));
        assertTrue(root.has("cycles"));
        assertTrue(root.has("hotspots"));
        assertTrue(root.has("blindSpots"));

        assertEquals(0, root.get("domains").size());
        assertEquals(0, root.get("cycles").size());
        assertEquals(0, root.get("hotspots").size());
        assertEquals(0, root.get("blindSpots").size());
    }
}
