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
 * Tests declaration-based graph construction in ParseOrchestrator.
 *
 * <p>These tests use plugins that return ModuleDeclaration and DependencyDeclaration
 * records instead of building graphs directly.
 */
class ParseOrchestratorDeclarationTest {

    @Test
    void testSinglePluginDeclarationGraph(@TempDir Path tempDir) throws IOException {
        // Plugin returns two module declarations and one dependency declaration
        DeclarationPlugin plugin = new DeclarationPlugin(
            "java",
            List.of(
                new ModuleDeclaration("java:com.example.Foo", NodeType.CLASS, "Foo.java", Confidence.HIGH),
                new ModuleDeclaration("java:com.example.Bar", NodeType.CLASS, "Bar.java", Confidence.HIGH)
            ),
            List.of(
                new DependencyDeclaration("java:com.example.Foo", "java:com.example.Bar",
                    EdgeType.IMPORTS, Confidence.HIGH, "import com.example.Bar", false)
            ),
            Set.of("com.example.Foo", "com.example.Bar")
        );

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, "// test file");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));
        ParseResult result = orchestrator.parse(
            List.of(javaFile),
            new ParseContext(tempDir, Set.of("java"))
        );

        DependencyGraph graph = result.getGraph();

        // Verify nodes exist with prefix stripped
        assertEquals(2, graph.nodeCount(), "Should have 2 nodes");
        assertTrue(graph.containsNode("com.example.Foo"), "Foo node should exist");
        assertTrue(graph.containsNode("com.example.Bar"), "Bar node should exist");

        // Verify edge exists with stripped IDs
        assertEquals(1, graph.edgeCount(), "Should have 1 edge");
        assertTrue(graph.getEdge("com.example.Foo", "com.example.Bar").isPresent(),
            "Edge from Foo to Bar should exist");

        // Verify declarations are passed through
        assertEquals(2, result.getModuleDeclarations().size());
        assertEquals(1, result.getDeclarations().size());
    }

    @Test
    void testNamespacePrefixStripping(@TempDir Path tempDir) throws IOException {
        DeclarationPlugin plugin = new DeclarationPlugin(
            "py",
            List.of(
                new ModuleDeclaration("py:myapp.models", NodeType.MODULE, "models.py", Confidence.HIGH),
                new ModuleDeclaration("py:myapp.views", NodeType.MODULE, "views.py", Confidence.HIGH)
            ),
            List.of(
                new DependencyDeclaration("py:myapp.views", "py:myapp.models",
                    EdgeType.IMPORTS, Confidence.HIGH, "from myapp import models", false)
            ),
            Set.of("myapp.models", "myapp.views")
        );

        Path pyFile = tempDir.resolve("test.py");
        Files.writeString(pyFile, "# test file");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));
        ParseResult result = orchestrator.parse(
            List.of(pyFile),
            new ParseContext(tempDir, Set.of("py"))
        );

        DependencyGraph graph = result.getGraph();

        // Prefixes should be stripped
        assertFalse(graph.containsNode("py:myapp.models"), "Should not have prefixed node");
        assertFalse(graph.containsNode("py:myapp.views"), "Should not have prefixed node");
        assertTrue(graph.containsNode("myapp.models"), "Should have unprefixed node");
        assertTrue(graph.containsNode("myapp.views"), "Should have unprefixed node");

        // Edge should reference unprefixed IDs
        assertTrue(graph.getEdge("myapp.views", "myapp.models").isPresent(),
            "Edge should use unprefixed IDs");
    }

    @Test
    void testNodeDeduplication(@TempDir Path tempDir) throws IOException {
        // Two files return declarations for the same module ID — first seen wins
        ModuleDeclaration sharedDecl = new ModuleDeclaration(
            "java:com.shared.Util", NodeType.CLASS, "Util.java", Confidence.HIGH
        );

        DeclarationPlugin plugin = new DeclarationPlugin(
            "java",
            List.of(sharedDecl, sharedDecl), // same declaration returned twice
            List.of(),
            Set.of("com.shared.Util")
        );

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, "// test");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));
        ParseResult result = orchestrator.parse(
            List.of(javaFile),
            new ParseContext(tempDir, Set.of("java"))
        );

        // Should deduplicate: only one node for "com.shared.Util"
        assertEquals(1, result.getGraph().nodeCount(), "Duplicate declarations should be deduped");
    }

    @Test
    void testEdgeTypesPreserved(@TempDir Path tempDir) throws IOException {
        DeclarationPlugin plugin = new DeclarationPlugin(
            "java",
            List.of(
                new ModuleDeclaration("java:A", NodeType.CLASS, "A.java", Confidence.HIGH),
                new ModuleDeclaration("java:B", NodeType.CLASS, "B.java", Confidence.HIGH),
                new ModuleDeclaration("java:C", NodeType.CLASS, "C.java", Confidence.HIGH)
            ),
            List.of(
                new DependencyDeclaration("java:A", "java:B", EdgeType.IMPORTS, Confidence.HIGH, null, false),
                new DependencyDeclaration("java:A", "java:C", EdgeType.CALLS, Confidence.MEDIUM, "A.callC()", false),
                new DependencyDeclaration("java:B", "java:C", EdgeType.EXTENDS, Confidence.HIGH, null, false)
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

        // Verify edge types are preserved through mapping
        assertEquals(com.archon.core.graph.EdgeType.IMPORTS,
            graph.getEdge("A", "B").orElseThrow().getType());
        assertEquals(com.archon.core.graph.EdgeType.CALLS,
            graph.getEdge("A", "C").orElseThrow().getType());
        assertEquals(com.archon.core.graph.EdgeType.EXTENDS,
            graph.getEdge("B", "C").orElseThrow().getType());

        // Verify confidence is preserved
        assertEquals(com.archon.core.graph.Confidence.HIGH,
            graph.getEdge("A", "B").orElseThrow().getConfidence());
        assertEquals(com.archon.core.graph.Confidence.MEDIUM,
            graph.getEdge("A", "C").orElseThrow().getConfidence());

        // Verify evidence is preserved
        assertNull(graph.getEdge("A", "B").orElseThrow().getEvidence());
        assertEquals("A.callC()", graph.getEdge("A", "C").orElseThrow().getEvidence());
    }

    @Test
    void testDynamicFlagPreserved(@TempDir Path tempDir) throws IOException {
        DeclarationPlugin plugin = new DeclarationPlugin(
            "java",
            List.of(
                new ModuleDeclaration("java:A", NodeType.CLASS, "A.java", Confidence.HIGH),
                new ModuleDeclaration("java:B", NodeType.CLASS, "B.java", Confidence.HIGH)
            ),
            List.of(
                new DependencyDeclaration("java:A", "java:B", EdgeType.USES,
                    Confidence.LOW, "Class.forName(\"B\")", true)
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
        assertTrue(graph.getEdge("A", "B").orElseThrow().isDynamic(),
            "Dynamic flag should be preserved");
        assertEquals(com.archon.core.graph.Confidence.LOW,
            graph.getEdge("A", "B").orElseThrow().getConfidence());
    }

    @Test
    void testNodeTypeMapping(@TempDir Path tempDir) throws IOException {
        DeclarationPlugin plugin = new DeclarationPlugin(
            "java",
            List.of(
                new ModuleDeclaration("java:MyClass", NodeType.CLASS, "MyClass.java", Confidence.HIGH),
                new ModuleDeclaration("java:MyModule", NodeType.MODULE, "MyModule.js", Confidence.HIGH),
                new ModuleDeclaration("java:MyPackage", NodeType.PACKAGE, null, Confidence.MEDIUM),
                new ModuleDeclaration("java:MyService", NodeType.SERVICE, "MyService.java", Confidence.HIGH),
                new ModuleDeclaration("java:MyController", NodeType.CONTROLLER, "MyController.java", Confidence.HIGH)
            ),
            List.of(),
            Set.of()
        );

        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, "// test");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));
        ParseResult result = orchestrator.parse(
            List.of(javaFile),
            new ParseContext(tempDir, Set.of("java"))
        );

        DependencyGraph graph = result.getGraph();

        assertEquals(com.archon.core.graph.NodeType.CLASS,
            graph.getNode("MyClass").orElseThrow().getType());
        assertEquals(com.archon.core.graph.NodeType.MODULE,
            graph.getNode("MyModule").orElseThrow().getType());
        assertEquals(com.archon.core.graph.NodeType.PACKAGE,
            graph.getNode("MyPackage").orElseThrow().getType());
        assertEquals(com.archon.core.graph.NodeType.SERVICE,
            graph.getNode("MyService").orElseThrow().getType());
        assertEquals(com.archon.core.graph.NodeType.CONTROLLER,
            graph.getNode("MyController").orElseThrow().getType());
    }

    /**
     * Simple declaration-based test plugin.
     * Returns preconfigured ModuleDeclaration and DependencyDeclaration records.
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
            // Return empty graph + declarations
            DependencyGraph emptyGraph = new DependencyGraph.MutableBuilder().build();
            return new ParseResult(
                emptyGraph, sourceModules, List.of(), List.of(),
                moduleDeclarations, dependencyDeclarations
            );
        }
    }
}
