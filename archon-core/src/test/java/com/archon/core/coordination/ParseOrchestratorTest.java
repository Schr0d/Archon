package com.archon.core.coordination;

import com.archon.core.analysis.DomainStrategy;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import com.archon.core.plugin.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ParseOrchestratorTest {

    @Test
    void testTwoPhaseConstructionPreventsEdgeLoss(@TempDir Path tempDir) throws IOException {
        // Create test plugins that add prefixed nodes and edges
        TestPlugin pluginA = new TestPlugin("java", Set.of("com.java.Bar"));
        TestPlugin pluginB = new TestPlugin("js", Set.of("com.js.Foo"));

        // Simulate cross-plugin edge: java:com.java.Bar -> js:com.js.Foo
        pluginA.addCrossPluginEdge("js:com.js.Foo");

        // Create temp files
        Path javaFile = tempDir.resolve("Bar.java");
        Path jsFile = tempDir.resolve("Foo.js");
        Files.writeString(javaFile, "// test java file");
        Files.writeString(jsFile, "// test js file");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(pluginA, pluginB));
        ParseResult result = orchestrator.parse(
            List.of(javaFile, jsFile),
            new ParseContext(tempDir, Set.of("java", "js"))
        );

        DependencyGraph graph = result.getGraph();
        // Both nodes should exist even though edges cross plugins
        assertTrue(graph.containsNode("com.java.Bar"), "Java node should exist after prefix stripping");
        assertTrue(graph.containsNode("com.js.Foo"), "JS node should exist after prefix stripping");

        // Edge should exist (two-phase construction prevents loss)
        assertEquals(1, graph.edgeCount(), "Should have exactly 1 edge");
        assertTrue(graph.getEdge("com.java.Bar", "com.js.Foo").isPresent(),
            "Cross-plugin edge should exist after prefix stripping");
    }

    @Test
    void testNamespacePrefixStripping(@TempDir Path tempDir) throws IOException {
        TestPlugin plugin = new TestPlugin("java", Set.of("com.java.Foo", "com.java.Bar"));
        // Add an edge between the two nodes
        plugin.addCrossPluginEdge("java:com.java.Bar");

        // Create temp file
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, "// test java file");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));

        ParseResult result = orchestrator.parse(
            List.of(testFile),
            new ParseContext(tempDir, Set.of("java"))
        );

        DependencyGraph graph = result.getGraph();
        // Prefix should be stripped from nodes
        assertTrue(graph.containsNode("com.java.Foo"), "Node should exist without prefix");
        assertTrue(graph.containsNode("com.java.Bar"), "Node should exist without prefix");
        assertFalse(graph.containsNode("java:com.java.Foo"), "Node should NOT exist with prefix");
        assertFalse(graph.containsNode("java:com.java.Bar"), "Node should NOT exist with prefix");

        // Prefix should be stripped from edge references
        // Note: TestPlugin adds edges from alphabetically first module to others
        // So edge is from "com.java.Bar" to "com.java.Foo"
        assertTrue(graph.getEdge("com.java.Bar", "com.java.Foo").isPresent(),
            "Edge should exist with stripped node IDs");
    }

    @Test
    void testEmptyPluginList() {
        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of());
        ParseResult result = orchestrator.parse(
            List.of(),
            new ParseContext(Path.of("/src"), Set.of())
        );

        assertEquals(0, result.getGraph().nodeCount());
        assertEquals(0, result.getGraph().edgeCount());
    }

    @Test
    void testMultipleFilesSamePlugin(@TempDir Path tempDir) throws IOException {
        TestPlugin plugin = new TestPlugin("java", Set.of("com.foo.A", "com.bar.B"));

        // Create temp files
        Path fileA = tempDir.resolve("A.java");
        Path fileB = tempDir.resolve("B.java");
        Files.writeString(fileA, "// file A");
        Files.writeString(fileB, "// file B");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));
        ParseResult result = orchestrator.parse(
            List.of(fileA, fileB),
            new ParseContext(tempDir, Set.of("java"))
        );

        DependencyGraph graph = result.getGraph();
        assertEquals(2, graph.nodeCount());
        assertTrue(graph.containsNode("com.foo.A"));
        assertTrue(graph.containsNode("com.bar.B"));
    }

    /**
     * Test plugin that adds prefixed nodes and edges.
     * Simulates language plugins that namespace their nodes.
     */
    static class TestPlugin implements LanguagePlugin {
        private final String prefix;
        private final Set<String> unprefixedModules;
        private final List<String> crossPluginEdges = new ArrayList<>();

        TestPlugin(String prefix, Set<String> unprefixedModules) {
            this.prefix = prefix;
            this.unprefixedModules = unprefixedModules;
        }

        void addCrossPluginEdge(String targetNodeId) {
            this.crossPluginEdges.add(targetNodeId);
        }

        @Override
        public Set<String> fileExtensions() {
            return Set.of(prefix);
        }

        @Override
        public Optional<DomainStrategy> getDomainStrategy() {
            return Optional.empty();
        }

        @Override
        public ParseResult parseFromContent(
            String filePath,
            String content,
            ParseContext context,
            DependencyGraph.MutableBuilder builder
        ) {
            // Phase 1: Add all nodes with prefix
            for (String module : unprefixedModules) {
                String prefixedId = prefix + ":" + module;
                Node node = Node.builder()
                    .id(prefixedId)
                    .type(NodeType.CLASS)
                    .sourcePath(filePath)
                    .build();
                builder.addNode(node);
            }

            // Phase 2: Add all edges with prefix
            // For simplicity, add edges from first module to all other modules
            if (unprefixedModules.size() > 1) {
                // Sort modules to ensure deterministic ordering
                List<String> sortedModules = new ArrayList<>(unprefixedModules);
                Collections.sort(sortedModules);
                String first = sortedModules.get(0);
                String prefixedSource = prefix + ":" + first;

                for (String target : unprefixedModules) {
                    if (!target.equals(first)) {
                        String prefixedTarget = prefix + ":" + target;
                        builder.addNode(
                            Node.builder()
                                .id(prefixedTarget)
                                .type(NodeType.CLASS)
                                .sourcePath(filePath)
                                .build()
                        );
                        builder.addEdge(
                            com.archon.core.graph.Edge.builder()
                                .source(prefixedSource)
                                .target(prefixedTarget)
                                .type(com.archon.core.graph.EdgeType.IMPORTS)
                                .build()
                        );
                    }
                }
            }

            // Add cross-plugin edges if any
            if (!unprefixedModules.isEmpty() && !crossPluginEdges.isEmpty()) {
                // Sort modules to ensure deterministic ordering
                List<String> sortedModules = new ArrayList<>(unprefixedModules);
                Collections.sort(sortedModules);
                String first = sortedModules.get(0);
                String prefixedSource = prefix + ":" + first;

                for (String prefixedTarget : crossPluginEdges) {
                    // Add the target node if it doesn't exist (for testing)
                    builder.addNode(
                        Node.builder()
                            .id(prefixedTarget)
                            .type(NodeType.CLASS)
                            .sourcePath(filePath)
                            .build()
                    );
                    builder.addEdge(
                        com.archon.core.graph.Edge.builder()
                            .source(prefixedSource)
                            .target(prefixedTarget)
                            .type(com.archon.core.graph.EdgeType.IMPORTS)
                            .build()
                    );
                }
            }

            return new ParseResult(builder.build(), unprefixedModules, List.of());
        }
    }
}
