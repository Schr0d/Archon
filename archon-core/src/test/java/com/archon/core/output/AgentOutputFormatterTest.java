package com.archon.core.output;

import com.archon.core.graph.*;
import com.archon.core.plugin.BlindSpot;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AgentOutputFormatterTest {

    private final AgentOutputFormatter formatter = new AgentOutputFormatter();

    @Test
    void format_basicGraph_producesStructuredOutput() {
        DependencyGraph graph = buildTestGraph();
        Map<String, String> domainMap = Map.of(
            "com.example.doc.DocController", "doc",
            "com.example.doc.DocService", "doc",
            "com.example.doc.DocRepository", "doc",
            "com.example.auth.UserService", "auth"
        );
        List<List<String>> cycles = List.of(
            List.of("com.example.doc.DocService", "com.example.doc.DocRepository")
        );
        List<Node> hotspots = List.of(
            graph.getNode("com.example.doc.DocController").orElseThrow()
        );
        List<BlindSpot> blindSpots = List.of(
            new BlindSpot("Spring @Autowired", "DocController", "47 edges not tracked")
        );

        String result = formatter.format(graph, domainMap, cycles, hotspots, blindSpots, "my-project/");

        // Verify header
        assertTrue(result.contains("Archon Analysis: my-project/"));
        assertTrue(result.contains("Graph: 4 nodes"));
        assertTrue(result.contains(" e/n)"));

        // Verify hotspots section exists
        assertTrue(result.contains("HOTSPOTS"));

        // Verify cycles
        assertTrue(result.contains("CYCLES"));
        assertTrue(result.contains("DocService -> DocRepository -> DocService"));

        // Verify domain coupling
        assertTrue(result.contains("DOMAIN COUPLING"));

        // Verify blind spots
        assertTrue(result.contains("BLIND SPOTS"));
        assertTrue(result.contains("Spring @Autowired"));

        // Verify footer
        assertTrue(result.contains("Run `archon diff` before committing"));
    }

    @Test
    void format_emptyGraph_noExceptions() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        Map<String, String> domainMap = Map.of();
        List<List<String>> cycles = List.of();
        List<Node> hotspots = List.of();
        List<BlindSpot> blindSpots = List.of();

        String result = formatter.format(graph, domainMap, cycles, hotspots, blindSpots, ".");

        assertTrue(result.contains("Archon Analysis:"));
        assertTrue(result.contains("0 nodes, 0 edges"));
        assertTrue(result.contains("HOTSPOTS: none"));
        assertTrue(result.contains("CYCLES: none detected"));
        assertTrue(result.contains("BLIND SPOTS: none"));
    }

    @Test
    void format_noAnsiCodes() {
        DependencyGraph graph = buildTestGraph();
        String result = formatter.format(graph, Map.of(), List.of(), List.of(), List.of(), ".");

        assertFalse(result.contains("\u001B["), "Output should not contain ANSI escape codes");
    }

    @Test
    void format_languageDetection_javaNodes() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("com.example.Service")
            .type(NodeType.CLASS)
            .sourcePath("src/main/java/com/example/Service.java")
            .build());
        DependencyGraph graph = builder.build();

        String result = formatter.format(graph, Map.of(), List.of(), List.of(), List.of(), ".");

        assertTrue(result.contains("Java (1)"));
    }

    @Test
    void format_languageDetection_mixedTypes() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("src/components/Header.vue")
            .type(NodeType.MODULE)
            .sourcePath("src/components/Header.vue")
            .build());
        builder.addNode(Node.builder()
            .id("src/utils/api.ts")
            .type(NodeType.MODULE)
            .sourcePath("src/utils/api.ts")
            .build());
        builder.addNode(Node.builder()
            .id("src/legacy/code.js")
            .type(NodeType.MODULE)
            .sourcePath("src/legacy/code.js")
            .build());
        DependencyGraph graph = builder.build();

        String result = formatter.format(graph, Map.of(), List.of(), List.of(), List.of(), ".");

        assertTrue(result.contains("Vue (1)"));
        assertTrue(result.contains("TypeScript (1)"));
        assertTrue(result.contains("JavaScript (1)"));
    }

    @Test
    void format_domainCoupling_crossDomainEdges() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).sourcePath("A.java").build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).sourcePath("B.java").build());
        builder.addEdge(Edge.builder().source("A").target("B").type(EdgeType.IMPORTS).build());
        DependencyGraph graph = builder.build();

        Map<String, String> domainMap = Map.of("A", "domain1", "B", "domain2");

        String result = formatter.format(graph, domainMap, List.of(), List.of(), List.of(), ".");

        assertTrue(result.contains("DOMAIN COUPLING"));
        assertTrue(result.contains("1 edges"));
    }

    @Test
    void format_blindSpots_groupedByType() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        List<BlindSpot> blindSpots = List.of(
            new BlindSpot("dynamic_import", "file1.js", "Dynamic import"),
            new BlindSpot("dynamic_import", "file2.js", "Dynamic import"),
            new BlindSpot("reflection", "Service.java", "Reflection call")
        );

        String result = formatter.format(graph, Map.of(), List.of(), List.of(), blindSpots, ".");

        assertTrue(result.contains("2 dynamic_import"));
        assertTrue(result.contains("1 reflection"));
    }

    @Test
    void format_hotspots_showsDependents() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        // Central node
        builder.addNode(Node.builder().id("Central").type(NodeType.CLASS).build());
        // Dependent nodes
        for (int i = 1; i <= 3; i++) {
            builder.addNode(Node.builder().id("Dep" + i).type(NodeType.CLASS).build());
            builder.addEdge(Edge.builder().source("Dep" + i).target("Central").type(EdgeType.IMPORTS).build());
        }
        DependencyGraph graph = builder.build();

        List<Node> hotspots = List.of(graph.getNode("Central").orElseThrow());

        String result = formatter.format(graph, Map.of(), List.of(), hotspots, List.of(), ".");

        assertTrue(result.contains("Central"));
        assertTrue(result.contains("<- Dep1, Dep2, Dep3"));
    }

    @Test
    void format_cycles_withNodeCount() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).build());
        builder.addEdge(Edge.builder().source("A").target("B").type(EdgeType.IMPORTS).build());
        builder.addEdge(Edge.builder().source("B").target("A").type(EdgeType.IMPORTS).build());
        DependencyGraph graph = builder.build();

        List<List<String>> cycles = List.of(List.of("A", "B"));

        String result = formatter.format(graph, Map.of(), cycles, List.of(), List.of(), ".");

        assertTrue(result.contains("A -> B -> A (2 nodes)"));
    }

    @Test
    void format_edgeNodeRatio_computedCorrectly() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).build());
        builder.addNode(Node.builder().id("C").type(NodeType.CLASS).build());
        builder.addEdge(Edge.builder().source("A").target("B").type(EdgeType.IMPORTS).build());
        builder.addEdge(Edge.builder().source("B").target("C").type(EdgeType.IMPORTS).build());
        builder.addEdge(Edge.builder().source("C").target("A").type(EdgeType.IMPORTS).build());
        DependencyGraph graph = builder.build();

        // 3 nodes, 3 edges -> ratio 1.00
        String result = formatter.format(graph, Map.of(), List.of(), List.of(), List.of(), ".");

        assertTrue(result.contains("3 nodes, 3 edges (1.00 e/n)"));
    }

    @Test
    void format_nullDomainMap_noException() {
        DependencyGraph graph = buildTestGraph();

        String result = formatter.format(graph, null, List.of(), List.of(), List.of(), ".");

        assertTrue(result.contains("Archon Analysis:"));
        assertFalse(result.contains("DOMAIN COUPLING"));
    }

    @Test
    void format_outputUnder200Lines() {
        // Build a moderately sized graph
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        for (int i = 0; i < 50; i++) {
            builder.addNode(Node.builder()
                .id("com.example.mod" + (i / 10) + ".Class" + i)
                .type(NodeType.CLASS)
                .sourcePath("src/mod" + (i / 10) + "/Class" + i + ".java")
                .build());
        }
        // Add edges
        for (int i = 1; i < 50; i++) {
            builder.addEdge(Edge.builder()
                .source("com.example.mod" + (i / 10) + ".Class" + i)
                .target("com.example.mod" + ((i - 1) / 10) + ".Class" + (i - 1))
                .type(EdgeType.IMPORTS)
                .build());
        }
        DependencyGraph graph = builder.build();

        // Build hotspots from graph nodes sorted by in-degree
        List<Node> hotspots = graph.getNodeIds().stream()
            .map(id -> graph.getNode(id).orElseThrow())
            .sorted(Comparator.comparingInt(Node::getInDegree).reversed())
            .limit(5)
            .collect(java.util.stream.Collectors.toList());

        List<BlindSpot> blindSpots = List.of(
            new BlindSpot("reflection", "Class0", "reflection call"),
            new BlindSpot("reflection", "Class1", "reflection call")
        );

        String result = formatter.format(graph, Map.of(), List.of(), hotspots, blindSpots, ".");

        long lineCount = result.lines().count();
        assertTrue(lineCount <= 200,
            "Output should be under 200 lines for a 50-node project, was: " + lineCount);
    }

    // Helper to build a simple test graph
    private DependencyGraph buildTestGraph() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder()
            .id("com.example.doc.DocController")
            .type(NodeType.CLASS)
            .sourcePath("src/main/java/com/example/doc/DocController.java")
            .build());
        builder.addNode(Node.builder()
            .id("com.example.doc.DocService")
            .type(NodeType.CLASS)
            .sourcePath("src/main/java/com/example/doc/DocService.java")
            .build());
        builder.addNode(Node.builder()
            .id("com.example.doc.DocRepository")
            .type(NodeType.CLASS)
            .sourcePath("src/main/java/com/example/doc/DocRepository.java")
            .build());
        builder.addNode(Node.builder()
            .id("com.example.auth.UserService")
            .type(NodeType.CLASS)
            .sourcePath("src/main/java/com/example/auth/UserService.java")
            .build());

        builder.addEdge(Edge.builder()
            .source("com.example.doc.DocController")
            .target("com.example.doc.DocService")
            .type(EdgeType.IMPORTS)
            .build());
        builder.addEdge(Edge.builder()
            .source("com.example.doc.DocService")
            .target("com.example.doc.DocRepository")
            .type(EdgeType.IMPORTS)
            .build());
        builder.addEdge(Edge.builder()
            .source("com.example.doc.DocRepository")
            .target("com.example.doc.DocService")
            .type(EdgeType.IMPORTS)
            .build());
        builder.addEdge(Edge.builder()
            .source("com.example.doc.DocController")
            .target("com.example.auth.UserService")
            .type(EdgeType.IMPORTS)
            .build());

        return builder.build();
    }
}
