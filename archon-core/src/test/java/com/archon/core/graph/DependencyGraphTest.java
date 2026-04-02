package com.archon.core.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DependencyGraphTest {

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
}
