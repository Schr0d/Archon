package com.archon.core.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DependencyGraphTest {

    @Test
    void addEdge_targetNodeMissing_edgeSkipped() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(Node.builder().id("A").type(NodeType.CLASS).build())
            .addEdge(Edge.builder().source("A").target("Missing").type(EdgeType.IMPORTS).build())
            .build();

        assertEquals(1, graph.nodeCount());
        assertEquals(0, graph.edgeCount());
    }

    @Test
    void addEdge_sourceNodeMissing_throws() {
        GraphBuilder builder = GraphBuilder.builder();

        assertThrows(IllegalArgumentException.class, () ->
            builder.addEdge(Edge.builder().source("Missing").target("A").type(EdgeType.IMPORTS).build())
        );
    }

    @Test
    void stripNamespacePrefixesAndBuild_noCollision() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder().id("java:com.example.Foo").type(NodeType.CLASS).build());
        builder.addNode(Node.builder().id("js:src/components/Header").type(NodeType.MODULE).build());

        DependencyGraph result = DependencyGraph.stripNamespacePrefixesAndBuild(builder);

        assertEquals(2, result.nodeCount());
        assertTrue(result.containsNode("com.example.Foo"));
        assertTrue(result.containsNode("src/components/Header"));
    }

    @Test
    void stripNamespacePrefixesAndBuild_withCollision_throwsException() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        // Create actual collision: both map to same unprefixed ID after normalization
        builder.addNode(Node.builder().id("java:src/main/Main").type(NodeType.CLASS).build());
        builder.addNode(Node.builder().id("js:src/main/Main").type(NodeType.CLASS).build());

        // Collision: both map to "src/main/Main"
        // Fix #1: Now throws exception instead of silently dropping
        assertThrows(IllegalStateException.class, () ->
            DependencyGraph.stripNamespacePrefixesAndBuild(builder)
        );
    }

    @Test
    void stripNamespacePrefix_emptyPrefix_throwsException() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder().id(":invalid").type(NodeType.CLASS).build());

        // Fix #4: Empty prefix throws exception
        assertThrows(IllegalArgumentException.class, () ->
            DependencyGraph.stripNamespacePrefixesAndBuild(builder)
        );
    }

    @Test
    void stripNamespacePrefix_trailingColon_throwsException() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder().id("test:").type(NodeType.CLASS).build());

        // Fix #4: Trailing colon throws exception
        assertThrows(IllegalArgumentException.class, () ->
            DependencyGraph.stripNamespacePrefixesAndBuild(builder)
        );
    }
}
