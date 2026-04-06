package com.archon.viz;

import com.archon.core.graph.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PerspectiveBuilderTest {
    @Test
    void testLevel1DomainMapGroupsByDomain() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder()
            .addNode(Node.builder().id("java:com.example.UserService").type(NodeType.CLASS).build())
            .addNode(Node.builder().id("java:com.example.UserRepository").type(NodeType.CLASS).build())
            .addNode(Node.builder().id("java:com.example.auth.AuthService").type(NodeType.CLASS).build())
            .build();

        Map<String, String> domains = Map.of(
            "java:com.example.UserService", "service",
            "java:com.example.UserRepository", "persistence",
            "java:com.example.auth.AuthService", "auth"
        );

        PerspectiveBuilder builder = new PerspectiveBuilder(graph, domains);
        PerspectiveView view = builder.buildPerspective();

        assertEquals(1, view.depth());
        assertFalse(view.groups().isEmpty());
        assertEquals(3, view.groups().size());
    }

    @Test
    void testLevel2ClassViewShowsAllNodes() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder()
            .addNode(Node.builder().id("java:com.example.UserService").type(NodeType.CLASS).build())
            .addNode(Node.builder().id("java:com.example.UserRepository").type(NodeType.CLASS).build())
            .build();

        Map<String, String> domains = Map.of(
            "java:com.example.UserService", "service",
            "java:com.example.UserRepository", "service"
        );

        PerspectiveBuilder builder = new PerspectiveBuilder(graph, domains);
        PerspectiveView view = builder.buildFocusPerspective("service", 2);

        assertEquals("service", view.focusId());
        assertEquals(2, view.depth());
        assertEquals(1, view.groups().size());
        assertEquals(2, view.groups().get(0).nodes().size());
    }

    @Test
    void testFocusOnSpecificNodeShowsChildren() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder()
            .addNode(Node.builder().id("java:com.example.A").type(NodeType.CLASS).build())
            .addNode(Node.builder().id("java:com.example.B").type(NodeType.CLASS).build())
            .addNode(Node.builder().id("java:com.example.C").type(NodeType.CLASS).build())
            .addEdge(Edge.builder()
                .source("java:com.example.A")
                .target("java:com.example.B")
                .type(EdgeType.IMPORTS)
                .build())
            .addEdge(Edge.builder()
                .source("java:com.example.A")
                .target("java:com.example.C")
                .type(EdgeType.IMPORTS)
                .build())
            .build();

        Map<String, String> domains = Map.of(
            "java:com.example.A", "test",
            "java:com.example.B", "test",
            "java:com.example.C", "test"
        );

        PerspectiveBuilder builder = new PerspectiveBuilder(graph, domains);
        PerspectiveView view = builder.buildFocusPerspective("java:com.example.A", 2);

        assertEquals("java:com.example.A", view.focusId());
        assertTrue(view.groups().get(0).nodes().stream().anyMatch(n -> n.id().equals("java:com.example.B")));
        assertTrue(view.groups().get(0).nodes().stream().anyMatch(n -> n.id().equals("java:com.example.C")));
    }
}