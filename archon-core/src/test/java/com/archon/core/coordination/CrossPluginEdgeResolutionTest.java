package com.archon.core.coordination;

import com.archon.core.graph.DependencyGraph;
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
}
