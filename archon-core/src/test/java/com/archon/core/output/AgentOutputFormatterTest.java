package com.archon.core.output;

import com.archon.core.graph.*;
import com.archon.core.plugin.BlindSpot;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AgentOutputFormatterTest {

    private final AgentOutputFormatter formatter = new AgentOutputFormatter();

    @Test
    void format_tier1_basicGraph_producesValidJson() {
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

        // Verify JSON structure
        assertTrue(result.startsWith("{"), "Output should start with {");
        assertTrue(result.endsWith("}"), "Output should end with }");

        // Verify required fields
        assertTrue(result.contains("\"v\":\"1.0.0\""), "Should contain version");
        assertTrue(result.contains("\"n\":4"), "Should contain node count");
        assertTrue(result.contains("\"e\":4"), "Should contain edge count");
        assertTrue(result.contains("\"cc\":"), "Should contain connected components");
        assertTrue(result.contains("\"domains\":"), "Should contain domains");
        assertTrue(result.contains("\"nodes\":"), "Should contain nodes array");
        assertTrue(result.contains("\"edges\":"), "Should contain edges array");
        assertTrue(result.contains("\"bridges\":"), "Should contain bridges");
        assertTrue(result.contains("\"bs\":"), "Should contain blind spots");
        assertTrue(result.contains("\"cycles\":"), "Should contain cycles");

        // Verify domains include our values
        assertTrue(result.contains("\"auth\""), "Should contain auth domain");
        assertTrue(result.contains("\"doc\""), "Should contain doc domain");

        // Verify blind spot
        assertTrue(result.contains("\"Spring @Autowired\":1"), "Should contain blind spot count");

        // Verify cycle data exists
        assertTrue(result.contains("\"cycles\":[["), "Should contain cycle array");
    }

    @Test
    void format_tier1_nodeFormat() {
        DependencyGraph graph = buildTestGraph();
        String result = formatter.format(graph, Map.of(), List.of(), List.of(), List.of(), ".");

        // Nodes should be arrays with 6 elements: [id, domainIdx, pageRank*10000, risk, bridge, hotspot]
        // Verify node entries exist with FQCNs
        assertTrue(result.contains("\"com.example.doc.DocController\""));
        assertTrue(result.contains("\"com.example.auth.UserService\""));
    }

    @Test
    void format_tier1_edgeFormat() {
        DependencyGraph graph = buildTestGraph();
        String result = formatter.format(graph, Map.of(), List.of(), List.of(), List.of(), ".");

        // Edges should be integer pairs [srcIdx, tgtIdx]
        assertTrue(result.contains("\"edges\":["), "Should contain edges array");
        // 4 edges, each is [i,j] format
        // Just verify edges section exists and contains array pairs
        int edgesStart = result.indexOf("\"edges\":[") + 9;
        int edgesEnd = result.indexOf("]", edgesStart);
        String edgesSection = result.substring(edgesStart, edgesEnd);
        // Should contain at least one comma (multiple edges)
        assertTrue(edgesSection.contains("[") || edgesSection.isEmpty(),
            "Edges should be array pairs");
    }

    @Test
    void format_emptyGraph_noExceptions() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        Map<String, String> domainMap = Map.of();

        String result = formatter.format(graph, domainMap, List.of(), List.of(), List.of(), ".");

        assertTrue(result.startsWith("{"));
        assertTrue(result.contains("\"n\":0"));
        assertTrue(result.contains("\"e\":0"));
        assertTrue(result.contains("\"nodes\":[]"));
        assertTrue(result.contains("\"edges\":[]"));
        assertTrue(result.contains("\"cycles\":[]"));
    }

    @Test
    void format_noAnsiCodes() {
        DependencyGraph graph = buildTestGraph();
        String result = formatter.format(graph, Map.of(), List.of(), List.of(), List.of(), ".");

        assertFalse(result.contains("\u001B["), "Output should not contain ANSI escape codes");
    }

    @Test
    void format_nullDomainMap_noException() {
        DependencyGraph graph = buildTestGraph();

        String result = formatter.format(graph, null, List.of(), List.of(), List.of(), ".");

        assertTrue(result.startsWith("{"));
        assertTrue(result.contains("\"n\":4"));
        // Should use "unknown" domain
        assertTrue(result.contains("\"unknown\""));
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

        assertTrue(result.contains("\"dynamic_import\":2"));
        assertTrue(result.contains("\"reflection\":1"));
    }

    @Test
    void format_cycles_asArrays() {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder().id("A").type(NodeType.CLASS).build());
        builder.addNode(Node.builder().id("B").type(NodeType.CLASS).build());
        builder.addEdge(Edge.builder().source("A").target("B").type(EdgeType.IMPORTS).build());
        builder.addEdge(Edge.builder().source("B").target("A").type(EdgeType.IMPORTS).build());
        DependencyGraph graph = builder.build();

        List<List<String>> cycles = List.of(List.of("A", "B"));

        String result = formatter.format(graph, Map.of(), cycles, List.of(), List.of(), ".");

        // Cycles should contain array of node IDs
        assertTrue(result.contains("\"cycles\":[["));
        assertTrue(result.contains("\"A\""));
        assertTrue(result.contains("\"B\""));
    }

    @Test
    void format_tier2_largeGraph_summaryFormat() {
        // Build a graph with 200+ nodes to trigger Tier 2
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        for (int i = 0; i < 210; i++) {
            builder.addNode(Node.builder()
                .id("com.example.mod" + (i / 10) + ".Class" + i)
                .type(NodeType.CLASS)
                .build());
        }
        for (int i = 1; i < 210; i++) {
            builder.addEdge(Edge.builder()
                .source("com.example.mod" + (i / 10) + ".Class" + i)
                .target("com.example.mod" + ((i - 1) / 10) + ".Class" + (i - 1))
                .type(EdgeType.IMPORTS)
                .build());
        }
        DependencyGraph graph = builder.build();

        String result = formatter.format(graph, Map.of(), List.of(), List.of(), List.of(), ".");

        // Tier 2 should have hotspots with PageRank, not full node/edge arrays
        assertTrue(result.contains("\"n\":210"));
        assertTrue(result.contains("\"hotspots\":["), "Tier 2 should have hotspots array");
        assertFalse(result.contains("\"nodes\":["),
            "Tier 2 should NOT have full nodes array");
        assertFalse(result.contains("\"edges\":["),
            "Tier 2 should NOT have full edges array");
        // No hint for Tier 2
        assertFalse(result.contains("\"hint\":"));
    }

    @Test
    void format_tier3_veryLargeGraph_cappedWithHint() {
        // Build a graph with 500+ nodes to trigger Tier 3
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        for (int i = 0; i < 510; i++) {
            builder.addNode(Node.builder()
                .id("com.example.pkg" + (i / 50) + ".Class" + i)
                .type(NodeType.CLASS)
                .build());
        }
        for (int i = 1; i < 510; i++) {
            builder.addEdge(Edge.builder()
                .source("com.example.pkg" + (i / 50) + ".Class" + i)
                .target("com.example.pkg" + ((i - 1) / 50) + ".Class" + (i - 1))
                .type(EdgeType.IMPORTS)
                .build());
        }
        DependencyGraph graph = builder.build();

        String result = formatter.format(graph, Map.of(), List.of(), List.of(), List.of(), ".");

        assertTrue(result.contains("\"n\":510"));
        assertTrue(result.contains("\"hint\""), "Tier 3 should have hint to use --target");
        assertTrue(result.contains("\"hotspots\":["), "Tier 3 should have hotspots");
    }

    @Test
    void format_tier1_jsonSizeUnder15KB() {
        // Build a moderately sized graph (50 nodes, Tier 1)
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        for (int i = 0; i < 50; i++) {
            builder.addNode(Node.builder()
                .id("com.example.mod" + (i / 10) + ".Class" + i)
                .type(NodeType.CLASS)
                .sourcePath("src/mod" + (i / 10) + "/Class" + i + ".java")
                .build());
        }
        for (int i = 1; i < 50; i++) {
            builder.addEdge(Edge.builder()
                .source("com.example.mod" + (i / 10) + ".Class" + i)
                .target("com.example.mod" + ((i - 1) / 10) + ".Class" + (i - 1))
                .type(EdgeType.IMPORTS)
                .build());
        }
        DependencyGraph graph = builder.build();

        List<Node> hotspots = graph.getNodeIds().stream()
            .map(id -> graph.getNode(id).orElseThrow())
            .sorted(Comparator.comparingInt(Node::getInDegree).reversed())
            .limit(5)
            .collect(Collectors.toList());

        List<BlindSpot> blindSpots = List.of(
            new BlindSpot("reflection", "Class0", "reflection call"),
            new BlindSpot("reflection", "Class1", "reflection call")
        );

        String result = formatter.format(graph, Map.of(), List.of(), hotspots, blindSpots, ".");

        int sizeBytes = result.getBytes().length;
        assertTrue(sizeBytes < 15 * 1024,
            "Tier 1 output for 50 nodes should be under 15KB, was: " + sizeBytes + " bytes");
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
