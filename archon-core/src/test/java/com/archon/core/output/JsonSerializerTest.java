package com.archon.core.output;

import com.archon.core.graph.*;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsonSerializer.
 */
class JsonSerializerTest {

    @Test
    void testEmptyGraph() {
        // Arrange
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        JsonSerializer serializer = new JsonSerializer();

        // Act
        String json = serializer.toJson(graph);

        // Assert
        assertNotNull(json);
        assertTrue(json.contains("nodes"));
        assertTrue(json.contains("edges"));
        assertTrue(json.contains("stats"));
        assertTrue(json.contains("nodeCount"));
        assertTrue(json.contains("edgeCount"));
    }

    @Test
    void testGraphWithNodes() {
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
        assertTrue(json.contains("com.example.ClassA"));
        assertTrue(json.contains("CLASS"));
        assertTrue(json.contains("HIGH"));
        assertTrue(json.contains("com.example.ClassB"));
        assertTrue(json.contains("MEDIUM"));
        assertTrue(json.contains("src/main/java/com/example/ClassB.java"));
    }

    @Test
    void testGraphWithEdges() {
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
        assertTrue(json.contains("com.example.Source"));
        assertTrue(json.contains("com.example.Target"));
        assertTrue(json.contains("IMPORTS"));
        assertTrue(json.contains("HIGH"));
        assertTrue(json.contains("dynamic"));
    }

    @Test
    void testGraphWithDomains() {
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
        assertTrue(json.contains("com.example.service.ServiceA"));
        assertTrue(json.contains("service"));
        assertTrue(json.contains("com.example.controller.ControllerA"));
        assertTrue(json.contains("controller"));
    }
}
