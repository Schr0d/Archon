package com.archon.core.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DependencyGraphTest {

    @Test
    void addEdge_targetNodeMissing_edgeSkipped() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).build());
        builder.addEdge(Edge.builder().source("A").target("Missing").type(EdgeType.IMPORTS).build());
        DependencyGraph graph = builder.build();

        assertEquals(1, graph.nodeCount());
        assertEquals(0, graph.edgeCount());
    }

    @Test
    void addEdge_sourceNodeMissing_throws() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

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

    @Test
    void mergeInto_mergesNodesAndEdges() {
        DependencyGraph.MutableBuilder sourceBuilder = new DependencyGraph.MutableBuilder();
        sourceBuilder.addNode(Node.builder().id("A").type(NodeType.CLASS).build());
        sourceBuilder.addNode(Node.builder().id("B").type(NodeType.CLASS).build());
        sourceBuilder.addEdge(Edge.builder().source("A").target("B").type(EdgeType.IMPORTS).build());
        DependencyGraph source = sourceBuilder.build();

        DependencyGraph.MutableBuilder target = new DependencyGraph.MutableBuilder();
        target.addNode(Node.builder().id("C").type(NodeType.CLASS).build());

        DependencyGraph.mergeInto(source, target);

        DependencyGraph result = target.build();
        assertEquals(3, result.nodeCount());
        assertTrue(result.containsNode("A"));
        assertTrue(result.containsNode("B"));
        assertTrue(result.containsNode("C"));
        assertEquals(1, result.edgeCount());
        assertTrue(result.getEdge("A", "B").isPresent());
    }

    @Test
    void knownNodeIds_returnsCurrentNodeIds() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).build());

        assertEquals(1, builder.knownNodeIds().size());
        assertTrue(builder.knownNodeIds().contains("A"));

        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).build());
        assertEquals(2, builder.knownNodeIds().size());
        assertTrue(builder.knownNodeIds().contains("B"));
    }

    @Test
    void knownNodeIds_isUnmodifiable() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).build());

        assertThrows(UnsupportedOperationException.class,
            () -> builder.knownNodeIds().add("X"));
    }
}
