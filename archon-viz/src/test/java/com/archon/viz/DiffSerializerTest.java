package com.archon.viz;

import com.archon.core.analysis.ChangeImpactReport;
import com.archon.core.analysis.GraphDiff;
import com.archon.core.analysis.ImpactResult;
import com.archon.core.analysis.RiskSummary;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import com.archon.core.graph.Confidence;
import com.archon.core.graph.RiskLevel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiffSerializerTest {
    private static final String NODE_A = "java:com.example.A";
    private static final String NODE_B = "java:com.example.B";
    private static final String NODE_NEW = "java:com.example.NewClass";
    private static final String DOMAIN_SERVICE = "service";
    private static final String DOMAIN_PERSISTENCE = "persistence";
    @Test
    void testDiffSerializationIncludesAddedNodes() throws Exception {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("java:com.example.A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        builder.addNode(Node.builder()
            .id("java:com.example.B")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        builder.addEdge(Edge.builder()
            .source("java:com.example.A")
            .target("java:com.example.B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());
        DependencyGraph graph = builder.build();

        GraphDiff diff = new GraphDiff(
            Set.of("java:com.example.NewClass"), // added
            Set.of(), // removed
            Set.of(), // addedEdges
            Set.of(), // removedEdges
            List.of(), // newCycles
            List.of()  // fixedCycles
        );

        RiskSummary riskSummary = new RiskSummary(RiskLevel.LOW, 0, 0, 0, Map.of());
        ChangeImpactReport report = new ChangeImpactReport("base", "head",
            Set.of("java:com.example.NewClass"), diff, Map.of(), List.of(), riskSummary);

        DiffSerializer serializer = new DiffSerializer(report, graph, Map.of());
        String json = serializer.toJson();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("addedNodes"));
        assertEquals(1, root.get("addedNodes").size());
        assertTrue(root.get("addedNodes").get(0).asText().contains("NewClass"));
    }

    @Test
    void testDiffSerializationIncludesRemovedNodes() throws Exception {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("java:com.example.A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        DependencyGraph graph = builder.build();

        GraphDiff diff = new GraphDiff(
            Set.of(), // added
            Set.of("java:com.example.OldClass"), // removed
            Set.of(), // addedEdges
            Set.of(), // removedEdges
            List.of(), // newCycles
            List.of()  // fixedCycles
        );

        RiskSummary riskSummary = new RiskSummary(RiskLevel.LOW, 0, 0, 0, Map.of());
        ChangeImpactReport report = new ChangeImpactReport("base", "head",
            Set.of(), diff, Map.of(), List.of(), riskSummary);

        DiffSerializer serializer = new DiffSerializer(report, graph, Map.of());
        String json = serializer.toJson();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("removedNodes"));
        assertEquals(1, root.get("removedNodes").size());
        assertTrue(root.get("removedNodes").get(0).asText().contains("OldClass"));
    }

    @Test
    void testDiffSerializationIncludesAddedEdges() throws Exception {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("java:com.example.A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        builder.addNode(Node.builder()
            .id("java:com.example.B")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        DependencyGraph graph = builder.build();

        Edge addedEdge = Edge.builder()
            .source("java:com.example.A")
            .target("java:com.example.B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build();

        GraphDiff diff = new GraphDiff(
            Set.of(), // added
            Set.of(), // removed
            Set.of(addedEdge), // addedEdges
            Set.of(), // removedEdges
            List.of(), // newCycles
            List.of()  // fixedCycles
        );

        RiskSummary riskSummary = new RiskSummary(RiskLevel.LOW, 0, 0, 0, Map.of());
        ChangeImpactReport report = new ChangeImpactReport("base", "head",
            Set.of(), diff, Map.of(), List.of(), riskSummary);

        DiffSerializer serializer = new DiffSerializer(report, graph, Map.of());
        String json = serializer.toJson();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("addedEdges"));
        assertEquals(1, root.get("addedEdges").size());
        JsonNode edgeObj = root.get("addedEdges").get(0);
        assertEquals("java:com.example.A", edgeObj.get("source").asText());
        assertEquals("java:com.example.B", edgeObj.get("target").asText());
    }

    @Test
    void testDiffSerializationIncludesRemovedEdges() throws Exception {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("java:com.example.A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        builder.addNode(Node.builder()
            .id("java:com.example.B")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        DependencyGraph graph = builder.build();

        Edge removedEdge = Edge.builder()
            .source("java:com.example.A")
            .target("java:com.example.B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build();

        GraphDiff diff = new GraphDiff(
            Set.of(), // added
            Set.of(), // removed
            Set.of(), // addedEdges
            Set.of(removedEdge), // removedEdges
            List.of(), // newCycles
            List.of()  // fixedCycles
        );

        RiskSummary riskSummary = new RiskSummary(RiskLevel.LOW, 0, 0, 0, Map.of());
        ChangeImpactReport report = new ChangeImpactReport("base", "head",
            Set.of(), diff, Map.of(), List.of(), riskSummary);

        DiffSerializer serializer = new DiffSerializer(report, graph, Map.of());
        String json = serializer.toJson();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("removedEdges"));
        assertEquals(1, root.get("removedEdges").size());
        JsonNode edgeObj = root.get("removedEdges").get(0);
        assertEquals("java:com.example.A", edgeObj.get("source").asText());
        assertEquals("java:com.example.B", edgeObj.get("target").asText());
    }

    @Test
    void testDiffSerializationIncludesRiskSummary() throws Exception {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("java:com.example.A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        DependencyGraph graph = builder.build();

        GraphDiff diff = new GraphDiff(Set.of(), Set.of(), Set.of(), Set.of(), List.of(), List.of());

        Map<String, RiskLevel> perClassRisk = new HashMap<>();
        perClassRisk.put("java:com.example.A", RiskLevel.HIGH);
        perClassRisk.put("java:com.example.B", RiskLevel.MEDIUM);
        perClassRisk.put("java:com.example.C", RiskLevel.LOW);

        RiskSummary riskSummary = new RiskSummary(RiskLevel.HIGH, 2, 3, 1, perClassRisk);

        ChangeImpactReport report = new ChangeImpactReport("base", "head",
            Set.of(), diff, Map.of(), List.of(), riskSummary);

        DiffSerializer serializer = new DiffSerializer(report, graph, Map.of());
        String json = serializer.toJson();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("riskSummary"));
        assertEquals(RiskLevel.HIGH.name(), root.get("riskSummary").get("overallRisk").asText());
        assertEquals(2, root.get("riskSummary").get("newCycleCount").asInt());
        assertEquals(3, root.get("riskSummary").get("crossDomainEdgeChanges").asInt());
        assertEquals(1, root.get("riskSummary").get("criticalPathHits").asInt());
        assertEquals(3, root.get("riskSummary").get("perClassRiskCount").asInt());
    }

    @Test
    void testDiffSerializationWithEmptyDiff() throws Exception {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("java:com.example.A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        DependencyGraph graph = builder.build();

        GraphDiff diff = new GraphDiff(Set.of(), Set.of(), Set.of(), Set.of(), List.of(), List.of());
        RiskSummary riskSummary = new RiskSummary(RiskLevel.LOW, 0, 0, 0, Map.of());

        ChangeImpactReport report = new ChangeImpactReport("base", "head",
            Set.of(), diff, Map.of(), List.of(), riskSummary);

        DiffSerializer serializer = new DiffSerializer(report, graph, Map.of());
        String json = serializer.toJson();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertEquals(0, root.get("addedNodes").size());
        assertEquals(0, root.get("removedNodes").size());
        assertEquals(0, root.get("addedEdges").size());
        assertEquals(0, root.get("removedEdges").size());
    }

    @Test
    void testDiffSerializationIncludesBaseGraphData() throws Exception {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("java:com.example.A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        builder.addNode(Node.builder()
            .id("java:com.example.B")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        builder.addEdge(Edge.builder()
            .source("java:com.example.A")
            .target("java:com.example.B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());
        DependencyGraph graph = builder.build();

        GraphDiff diff = new GraphDiff(Set.of(), Set.of(), Set.of(), Set.of(), List.of(), List.of());
        RiskSummary riskSummary = new RiskSummary(RiskLevel.LOW, 0, 0, 0, Map.of());

        ChangeImpactReport report = new ChangeImpactReport("base", "head",
            Set.of(), diff, Map.of(), List.of(), riskSummary);

        DiffSerializer serializer = new DiffSerializer(report, graph, Map.of());
        String json = serializer.toJson();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("graph"));
        JsonNode graphObj = root.get("graph");
        assertTrue(graphObj.has("nodes"));
        assertTrue(graphObj.has("edges"));
        assertEquals(2, graphObj.get("nodes").size());
        assertEquals(1, graphObj.get("edges").size());
    }

    @Test
    void testDiffSerializationIncludesCycleInformation() throws Exception {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("java:com.example.A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        DependencyGraph graph = builder.build();

        List<String> newCycle = List.of("java:com.example.A", "java:com.example.B", "java:com.example.A");
        List<String> fixedCycle = List.of("java:com.example.C", "java:com.example.D", "java:com.example.C");

        GraphDiff diff = new GraphDiff(Set.of(), Set.of(), Set.of(), Set.of(),
            List.of(newCycle), List.of(fixedCycle));
        RiskSummary riskSummary = new RiskSummary(RiskLevel.LOW, 0, 0, 0, Map.of());

        ChangeImpactReport report = new ChangeImpactReport("base", "head",
            Set.of(), diff, Map.of(), List.of(), riskSummary);

        DiffSerializer serializer = new DiffSerializer(report, graph, Map.of());
        String json = serializer.toJson();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("newCycles"));
        assertTrue(root.has("fixedCycles"));
        assertEquals(1, root.get("newCycles").size());
        assertEquals(1, root.get("fixedCycles").size());
        assertEquals(3, root.get("newCycles").get(0).size());
        assertEquals(3, root.get("fixedCycles").get(0).size());
    }

    @Test
    void testDiffSerializationIncludesImpactedNodes() throws Exception {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("java:com.example.A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        DependencyGraph graph = builder.build();

        GraphDiff diff = new GraphDiff(Set.of(), Set.of(), Set.of(), Set.of(), List.of(), List.of());
        RiskSummary riskSummary = new RiskSummary(RiskLevel.LOW, 0, 0, 0, Map.of());

        List<ImpactResult.ImpactNode> impactedNodes = List.of(
            new ImpactResult.ImpactNode("java:com.example.A", "service", 1, RiskLevel.HIGH),
            new ImpactResult.ImpactNode("java:com.example.B", "persistence", 2, RiskLevel.MEDIUM)
        );

        ChangeImpactReport report = new ChangeImpactReport("base", "head",
            Set.of(), diff, Map.of(), impactedNodes, riskSummary);

        DiffSerializer serializer = new DiffSerializer(report, graph, Map.of());
        String json = serializer.toJson();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("impactedNodes"));
        assertEquals(2, root.get("impactedNodes").size());

        JsonNode firstImpact = root.get("impactedNodes").get(0);
        assertEquals("java:com.example.A", firstImpact.get("nodeId").asText());
        assertEquals("service", firstImpact.get("domain").asText());
        assertEquals(1, firstImpact.get("depth").asInt());
        assertEquals("HIGH", firstImpact.get("risk").asText());

        JsonNode secondImpact = root.get("impactedNodes").get(1);
        assertEquals("java:com.example.B", secondImpact.get("nodeId").asText());
        assertEquals("persistence", secondImpact.get("domain").asText());
        assertEquals(2, secondImpact.get("depth").asInt());
        assertEquals("MEDIUM", secondImpact.get("risk").asText());
    }

    @Test
    void testDiffSerializationIncludesChangedNodes() throws Exception {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("java:com.example.A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        builder.addNode(Node.builder()
            .id("java:com.example.B")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        builder.addEdge(Edge.builder()
            .source("java:com.example.A")
            .target("java:com.example.B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build());
        DependencyGraph graph = builder.build();

        GraphDiff diff = new GraphDiff(
            Set.of("java:com.example.NewClass"), // added
            Set.of("java:com.example.OldClass"), // removed
            Set.of(), // addedEdges
            Set.of(), // removedEdges
            List.of(), // newCycles
            List.of()  // fixedCycles
        );

        RiskSummary riskSummary = new RiskSummary(RiskLevel.LOW, 0, 0, 0, Map.of());
        ChangeImpactReport report = new ChangeImpactReport("base", "head",
            Set.of(), diff, Map.of(), List.of(), riskSummary);

        DiffSerializer serializer = new DiffSerializer(report, graph, Map.of());
        String json = serializer.toJson();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("changedNodes"));
        assertEquals(2, root.get("changedNodes").size());
        assertTrue(root.get("changedNodes").get(0).asText().contains("NewClass") ||
                   root.get("changedNodes").get(1).asText().contains("NewClass"));
        assertTrue(root.get("changedNodes").get(0).asText().contains("OldClass") ||
                   root.get("changedNodes").get(1).asText().contains("OldClass"));
    }

    @Test
    void testDiffSerializationIncludesDomainRisks() throws Exception {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("java:com.example.A")
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        DependencyGraph graph = builder.build();

        GraphDiff diff = new GraphDiff(Set.of(), Set.of(), Set.of(), Set.of(), List.of(), List.of());

        Map<String, RiskLevel> perClassRisk = Map.of(
            "java:com.example.A", RiskLevel.HIGH,
            "java:com.example.B", RiskLevel.LOW
        );
        Map<String, String> changedClassDomains = Map.of(
            "java:com.example.A", "service",
            "java:com.example.B", "persistence"
        );
        RiskSummary riskSummary = new RiskSummary(RiskLevel.HIGH, 0, 0, 0, perClassRisk);

        ChangeImpactReport report = new ChangeImpactReport("base", "head",
            Set.of(), diff, changedClassDomains, List.of(), riskSummary);

        DiffSerializer serializer = new DiffSerializer(report, graph, Map.of());
        String json = serializer.toJson();

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        assertTrue(root.has("riskSummary"));
        assertTrue(root.get("riskSummary").has("domainRisks"));
        assertEquals("HIGH", root.get("riskSummary").get("domainRisks").get("service").asText());
        assertEquals("LOW", root.get("riskSummary").get("domainRisks").get("persistence").asText());
    }

    @Test
    void testConstructorWithNullImpactReportThrowsException() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id(NODE_A)
            .type(NodeType.CLASS)
            .confidence(Confidence.HIGH)
            .build());
        DependencyGraph graph = builder.build();

        assertThrows(NullPointerException.class, () -> {
            new DiffSerializer(null, graph, Map.of());
        });
    }

    @Test
    void testConstructorWithNullHeadGraphThrowsException() {
        GraphDiff diff = new GraphDiff(Set.of(), Set.of(), Set.of(), Set.of(), List.of(), List.of());
        RiskSummary riskSummary = new RiskSummary(RiskLevel.LOW, 0, 0, 0, Map.of());
        ChangeImpactReport report = new ChangeImpactReport("base", "head",
            Set.of(), diff, Map.of(), List.of(), riskSummary);

        assertThrows(NullPointerException.class, () -> {
            new DiffSerializer(report, null, Map.of());
        });
    }
}
