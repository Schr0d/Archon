package com.archon.core.coordination;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.DependencyGraph.MutableBuilder;
import com.archon.core.plugin.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests cross-plugin edge resolution in ParseOrchestrator.
 *
 * <p>Verifies that edges between nodes declared by different plugins
 * (different language namespaces) are correctly resolved in the unified graph.
 */
class CrossPluginEdgeResolutionTest {

    @Test
    void testCrossLanguageEdge(@TempDir Path tempDir) throws IOException {
        // Java plugin declares "java:A" and "java:B"
        DeclarationPlugin javaPlugin = new DeclarationPlugin(
            "java",
            List.of(
                new ModuleDeclaration("java:A", NodeType.CLASS, "A.java", Confidence.HIGH),
                new ModuleDeclaration("java:B", NodeType.CLASS, "B.java", Confidence.HIGH)
            ),
            List.of(
                new DependencyDeclaration("java:A", "java:B", EdgeType.IMPORTS, Confidence.HIGH, null, false)
            ),
            Set.of("A", "B")
        );

        // Python plugin declares "py:C" and an edge from "java:A" to "py:C"
        DeclarationPlugin pyPlugin = new DeclarationPlugin(
            "py",
            List.of(
                new ModuleDeclaration("py:C", NodeType.MODULE, "c.py", Confidence.HIGH)
            ),
            List.of(
                new DependencyDeclaration("java:A", "py:C", EdgeType.USES, Confidence.MEDIUM, "rpc call", true)
            ),
            Set.of("C")
        );

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, "// java file");
        Path pyFile = tempDir.resolve("test.py");
        Files.writeString(pyFile, "# python file");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(javaPlugin, pyPlugin));
        ParseResult result = orchestrator.parse(
            List.of(javaFile, pyFile),
            new ParseContext(tempDir, Set.of("java", "py"))
        );

        DependencyGraph graph = result.getGraph();

        // All nodes should exist with prefixes stripped
        assertEquals(3, graph.nodeCount(), "Should have 3 nodes across plugins");
        assertTrue(graph.containsNode("A"), "A should exist");
        assertTrue(graph.containsNode("B"), "B should exist");
        assertTrue(graph.containsNode("C"), "C should exist");

        // Cross-plugin edge from A (java) to C (py)
        assertTrue(graph.getEdge("A", "C").isPresent(),
            "Cross-plugin edge A -> C should exist");
        assertEquals(com.archon.core.graph.EdgeType.USES,
            graph.getEdge("A", "C").orElseThrow().getType());
        assertTrue(graph.getEdge("A", "C").orElseThrow().isDynamic());

        // Intra-plugin edge from A to B
        assertTrue(graph.getEdge("A", "B").isPresent(),
            "Intra-plugin edge A -> B should exist");

        // Total edges
        assertEquals(2, graph.edgeCount(), "Should have 2 edges total");
    }

    @Test
    void testEdgeToNonexistentTargetSkipped(@TempDir Path tempDir) throws IOException {
        // Java plugin declares only "java:A", but adds an edge to "py:Nonexistent"
        DeclarationPlugin javaPlugin = new DeclarationPlugin(
            "java",
            List.of(
                new ModuleDeclaration("java:A", NodeType.CLASS, "A.java", Confidence.HIGH)
            ),
            List.of(
                new DependencyDeclaration("java:A", "py:Nonexistent",
                    EdgeType.IMPORTS, Confidence.HIGH, null, false)
            ),
            Set.of("A")
        );

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, "// java file");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(javaPlugin));
        ParseResult result = orchestrator.parse(
            List.of(javaFile),
            new ParseContext(tempDir, Set.of("java"))
        );

        DependencyGraph graph = result.getGraph();

        // Node A should exist
        assertTrue(graph.containsNode("A"), "A should exist");

        // Edge to nonexistent should be skipped
        assertEquals(0, graph.edgeCount(), "Edge to nonexistent target should be skipped");
    }

    @Test
    void testMixedDeclarationAndLegacyPlugins(@TempDir Path tempDir) throws IOException {
        // Declaration-based plugin
        DeclarationPlugin declPlugin = new DeclarationPlugin(
            "java",
            List.of(
                new ModuleDeclaration("java:JavaService", NodeType.SERVICE, "JavaService.java", Confidence.HIGH)
            ),
            List.of(),
            Set.of("JavaService")
        );

        // Legacy graph-returning plugin
        LegacyPlugin legacyPlugin = new LegacyPlugin(
            "js",
            List.of("js:JsModule"),
            List.of()
        );

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, "// java");
        Path jsFile = tempDir.resolve("test.js");
        Files.writeString(jsFile, "// js");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(declPlugin, legacyPlugin));
        ParseResult result = orchestrator.parse(
            List.of(javaFile, jsFile),
            new ParseContext(tempDir, Set.of("java", "js"))
        );

        DependencyGraph graph = result.getGraph();

        // Both nodes should exist regardless of which path was used
        assertEquals(2, graph.nodeCount(), "Should have nodes from both plugin paths");
        assertTrue(graph.containsNode("JavaService"), "Declaration-based node should exist");
        assertTrue(graph.containsNode("JsModule"), "Legacy graph node should exist");
    }

    /**
     * Declaration-based test plugin.
     */
    static class DeclarationPlugin implements LanguagePlugin {
        private final String extension;
        private final List<ModuleDeclaration> moduleDeclarations;
        private final List<DependencyDeclaration> dependencyDeclarations;
        private final Set<String> sourceModules;

        DeclarationPlugin(
            String extension,
            List<ModuleDeclaration> moduleDeclarations,
            List<DependencyDeclaration> dependencyDeclarations,
            Set<String> sourceModules
        ) {
            this.extension = extension;
            this.moduleDeclarations = moduleDeclarations;
            this.dependencyDeclarations = dependencyDeclarations;
            this.sourceModules = sourceModules;
        }

        @Override
        public Set<String> fileExtensions() {
            return Set.of(extension);
        }

        @Override
        public ParseResult parseFromContent(String filePath, String content, ParseContext context) {
            DependencyGraph emptyGraph = new DependencyGraph.MutableBuilder().build();
            return new ParseResult(
                emptyGraph, sourceModules, List.of(), List.of(),
                moduleDeclarations, dependencyDeclarations
            );
        }
    }

    /**
     * Legacy test plugin that returns a graph instead of declarations.
     */
    static class LegacyPlugin implements LanguagePlugin {
        private final String extension;
        private final List<String> nodeIds;
        private final List<String[]> edges; // [source, target]

        LegacyPlugin(String extension, List<String> nodeIds, List<String[]> edges) {
            this.extension = extension;
            this.nodeIds = nodeIds;
            this.edges = edges;
        }

        @Override
        public Set<String> fileExtensions() {
            return Set.of(extension);
        }

        @Override
        public ParseResult parseFromContent(String filePath, String content, ParseContext context) {
            MutableBuilder builder = new MutableBuilder();

            for (String nodeId : nodeIds) {
                builder.addNode(com.archon.core.graph.Node.builder()
                    .id(nodeId)
                    .type(com.archon.core.graph.NodeType.MODULE)
                    .sourcePath(filePath)
                    .build());
            }

            for (String[] edge : edges) {
                builder.addEdge(com.archon.core.graph.Edge.builder()
                    .source(edge[0])
                    .target(edge[1])
                    .type(com.archon.core.graph.EdgeType.IMPORTS)
                    .build());
            }

            // Return ParseResult WITHOUT declarations — triggers legacy path
            return new ParseResult(builder.build(), Set.of(), List.of());
        }
    }
}
