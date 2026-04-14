package com.archon.core.coordination;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.DependencyGraph.MutableBuilder;
import com.archon.core.plugin.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests edge-skip behavior in ParseOrchestrator when target nodes are missing.
 *
 * <p>When a DependencyDeclaration references a target ID that is not in the node map,
 * the orchestrator should skip the edge and log a warning to stderr.
 * Other valid edges should still be present in the graph.
 */
class EdgeSkipWarningTest {

    @Test
    void testMissingTargetEdgeSkipped(@TempDir Path tempDir) throws IOException {
        // Capture stderr to verify warning messages
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(baos));

        try {
            DeclarationPlugin plugin = new DeclarationPlugin(
                "java",
                List.of(
                    new ModuleDeclaration("java:A", NodeType.CLASS, "A.java", Confidence.HIGH),
                    new ModuleDeclaration("java:B", NodeType.CLASS, "B.java", Confidence.HIGH)
                ),
                List.of(
                    // Valid edge: A -> B
                    new DependencyDeclaration("java:A", "java:B", EdgeType.IMPORTS, Confidence.HIGH, null, false),
                    // Invalid edge: A -> Nonexistent (target not in node map)
                    new DependencyDeclaration("java:A", "java:Nonexistent", EdgeType.CALLS, Confidence.HIGH, null, false)
                ),
                Set.of("A", "B")
            );

            Path javaFile = tempDir.resolve("Test.java");
            Files.writeString(javaFile, "// test");

            ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));
            ParseResult result = orchestrator.parse(
                List.of(javaFile),
                new ParseContext(tempDir, Set.of("java"))
            );

            DependencyGraph graph = result.getGraph();

            // Valid edge should exist
            assertEquals(1, graph.edgeCount(), "Only valid edge should be in graph");
            assertTrue(graph.getEdge("A", "B").isPresent(),
                "Valid edge A -> B should exist");

            // Invalid edge should NOT exist
            assertFalse(graph.getEdge("A", "Nonexistent").isPresent(),
                "Edge to nonexistent target should not exist");

            // Warning should have been logged to stderr
            String stderr = baos.toString();
            assertTrue(stderr.contains("Nonexistent"),
                "Warning should mention the missing target 'Nonexistent'");
            assertTrue(stderr.contains("skipping edge"),
                "Warning should say 'skipping edge'");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testMissingSourceEdgeSkipped(@TempDir Path tempDir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(baos));

        try {
            DeclarationPlugin plugin = new DeclarationPlugin(
                "java",
                List.of(
                    new ModuleDeclaration("java:A", NodeType.CLASS, "A.java", Confidence.HIGH)
                ),
                List.of(
                    // Invalid: source doesn't exist (but target does via being same as A)
                    new DependencyDeclaration("java:Ghost", "java:A", EdgeType.IMPORTS, Confidence.HIGH, null, false)
                ),
                Set.of("A")
            );

            Path javaFile = tempDir.resolve("Test.java");
            Files.writeString(javaFile, "// test");

            ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));
            ParseResult result = orchestrator.parse(
                List.of(javaFile),
                new ParseContext(tempDir, Set.of("java"))
            );

            DependencyGraph graph = result.getGraph();

            // Node should exist but no edges
            assertTrue(graph.containsNode("A"), "Node A should exist");
            assertEquals(0, graph.edgeCount(), "Edge with missing source should be skipped");

            // Warning should mention missing source
            String stderr = baos.toString();
            assertTrue(stderr.contains("Ghost"),
                "Warning should mention the missing source 'Ghost'");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testMultipleInvalidEdgesPartialGraphBuilt(@TempDir Path tempDir) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(baos));

        try {
            DeclarationPlugin plugin = new DeclarationPlugin(
                "java",
                List.of(
                    new ModuleDeclaration("java:A", NodeType.CLASS, "A.java", Confidence.HIGH),
                    new ModuleDeclaration("java:B", NodeType.CLASS, "B.java", Confidence.HIGH),
                    new ModuleDeclaration("java:C", NodeType.CLASS, "C.java", Confidence.HIGH)
                ),
                List.of(
                    // Valid edges
                    new DependencyDeclaration("java:A", "java:B", EdgeType.IMPORTS, Confidence.HIGH, null, false),
                    new DependencyDeclaration("java:B", "java:C", EdgeType.CALLS, Confidence.HIGH, null, false),
                    // Invalid edges
                    new DependencyDeclaration("java:A", "java:Missing1", EdgeType.USES, Confidence.LOW, null, true),
                    new DependencyDeclaration("java:Missing2", "java:C", EdgeType.IMPORTS, Confidence.LOW, null, false),
                    new DependencyDeclaration("java:B", "java:Missing3", EdgeType.EXTENDS, Confidence.MEDIUM, null, false)
                ),
                Set.of("A", "B", "C")
            );

            Path javaFile = tempDir.resolve("Test.java");
            Files.writeString(javaFile, "// test");

            ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));
            ParseResult result = orchestrator.parse(
                List.of(javaFile),
                new ParseContext(tempDir, Set.of("java"))
            );

            DependencyGraph graph = result.getGraph();

            // Only valid edges should be in the graph
            assertEquals(2, graph.edgeCount(), "Should have 2 valid edges out of 5 total");
            assertTrue(graph.getEdge("A", "B").isPresent());
            assertTrue(graph.getEdge("B", "C").isPresent());

            // All 3 nodes should exist
            assertEquals(3, graph.nodeCount());
            assertTrue(graph.containsNode("A"));
            assertTrue(graph.containsNode("B"));
            assertTrue(graph.containsNode("C"));

            // Warnings for 3 invalid edges
            String stderr = baos.toString();
            assertTrue(stderr.contains("Missing1"), "Should warn about Missing1");
            assertTrue(stderr.contains("Missing2"), "Should warn about Missing2");
            assertTrue(stderr.contains("Missing3"), "Should warn about Missing3");
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void testAllEdgesInvalidGraphStillHasNodes(@TempDir Path tempDir) throws IOException {
        DeclarationPlugin plugin = new DeclarationPlugin(
            "java",
            List.of(
                new ModuleDeclaration("java:X", NodeType.CLASS, "X.java", Confidence.HIGH),
                new ModuleDeclaration("java:Y", NodeType.CLASS, "Y.java", Confidence.HIGH)
            ),
            List.of(
                // Both edges target nonexistent nodes
                new DependencyDeclaration("java:X", "java:Ghost1", EdgeType.IMPORTS, Confidence.HIGH, null, false),
                new DependencyDeclaration("java:Y", "java:Ghost2", EdgeType.CALLS, Confidence.HIGH, null, false)
            ),
            Set.of("X", "Y")
        );

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, "// test");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));
        ParseResult result = orchestrator.parse(
            List.of(javaFile),
            new ParseContext(tempDir, Set.of("java"))
        );

        DependencyGraph graph = result.getGraph();

        // Nodes should still exist
        assertEquals(2, graph.nodeCount(), "All declared nodes should be in graph");
        assertTrue(graph.containsNode("X"));
        assertTrue(graph.containsNode("Y"));

        // No edges should exist
        assertEquals(0, graph.edgeCount(), "All edges with missing targets should be skipped");
    }

    /**
     * Simple declaration-based test plugin.
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
}
