package com.archon.viz;

import com.archon.core.analysis.*;
import com.archon.core.graph.*;
import com.archon.core.plugin.BlindSpot;
import org.junit.jupiter.api.Test;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TerminalRendererTest {
    @Test
    void testRenderProducesStructuredTreeOutput() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder()
            .addNode(Node.builder().id("java:com.example.UserService").type(NodeType.CLASS).build())
            .addNode(Node.builder().id("java:com.example.UserRepository").type(NodeType.CLASS).build())
            .build();

        Map<String, String> domains = Map.of(
            "java:com.example.UserService", "service",
            "java:com.example.UserRepository", "persistence"
        );

        AnalysisResult result = new AnalysisResult(graph, domains, List.of(), List.of(), List.of(), new Thresholds(5, 3, 4, 10));
        StringWriter output = new StringWriter();
        TerminalRenderer renderer = new TerminalRenderer(result, new PrintWriter(output));

        renderer.render();

        String rendered = output.toString();
        assertTrue(rendered.contains("archon: analyzed 2 nodes"));
        assertTrue(rendered.contains("domain: service"));
        assertTrue(rendered.contains("domain: persistence"));
        assertTrue(rendered.contains("UserService"));
        assertTrue(rendered.contains("UserRepository"));
    }

    @Test
    void testRenderWithHotspotsShowsHotspotCount() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder()
            .addNode(Node.builder().id("java:com.example.A").type(NodeType.CLASS).build())
            .build();

        List<Node> hotspots = List.of(Node.builder().id("java:com.example.A").type(NodeType.CLASS).build());
        AnalysisResult result = new AnalysisResult(graph, Map.of(), List.of(), hotspots, List.of(), new Thresholds(5, 3, 4, 10));

        StringWriter output = new StringWriter();
        TerminalRenderer renderer = new TerminalRenderer(result, new PrintWriter(output));

        renderer.render();

        assertTrue(output.toString().contains("1 hotspots"));
    }

    @Test
    void testRenderWithBlindSpotsShowsBlindSpotCount() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder()
            .addNode(Node.builder().id("java:com.example.A").type(NodeType.CLASS).build())
            .build();

        List<BlindSpot> blindSpots = List.of(new BlindSpot("Dynamic reflection", "java:com.example.A", "Reflection call detected"));
        AnalysisResult result = new AnalysisResult(graph, Map.of(), List.of(), List.of(), blindSpots, new Thresholds(5, 3, 4, 10));

        StringWriter output = new StringWriter();
        TerminalRenderer renderer = new TerminalRenderer(result, new PrintWriter(output));

        renderer.render();

        assertTrue(output.toString().contains("1 blind spots"));
    }

    @Test
    void testShortIdRemovesNamespacePrefix() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder()
            .addNode(Node.builder().id("java:com.example.UserService").type(NodeType.CLASS).build())
            .build();

        AnalysisResult result = new AnalysisResult(graph, Map.of(), List.of(), List.of(), List.of(), new Thresholds(5, 3, 4, 10));
        StringWriter output = new StringWriter();
        TerminalRenderer renderer = new TerminalRenderer(result, new PrintWriter(output));

        renderer.render();

        assertTrue(output.toString().contains("UserService"));
        assertFalse(output.toString().contains("java:com.example.UserService"));
    }
}
