package com.archon.core.coordination;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.plugin.*;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DeclarationGraphBuilder — the shared utility that converts
 * ModuleDeclaration and DependencyDeclaration lists into a DependencyGraph.
 */
class DeclarationGraphBuilderTest {

    @Test
    void buildEmptyDeclarationsReturnsEmptyGraph() {
        DeclarationGraphBuilder.BuildResult result = DeclarationGraphBuilder.build(
            Collections.emptyList(), Collections.emptyList()
        );
        assertEquals(0, result.graph().nodeCount());
        assertEquals(0, result.graph().edgeCount());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void buildNodesFromModuleDeclarations() {
        List<ModuleDeclaration> modules = List.of(
            new ModuleDeclaration("java:com.example.Foo", NodeType.CLASS, "Foo.java", Confidence.HIGH),
            new ModuleDeclaration("java:com.example.Bar", NodeType.CLASS, "Bar.java", Confidence.HIGH)
        );

        DeclarationGraphBuilder.BuildResult result = DeclarationGraphBuilder.build(
            modules, Collections.emptyList()
        );

        DependencyGraph graph = result.graph();
        assertEquals(2, graph.nodeCount());
        assertTrue(graph.containsNode("com.example.Foo"));
        assertTrue(graph.containsNode("com.example.Bar"));
        assertEquals(0, graph.edgeCount());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void buildEdgesFromDependencyDeclarations() {
        List<ModuleDeclaration> modules = List.of(
            new ModuleDeclaration("java:A", NodeType.CLASS, "A.java", Confidence.HIGH),
            new ModuleDeclaration("java:B", NodeType.CLASS, "B.java", Confidence.HIGH)
        );
        List<DependencyDeclaration> deps = List.of(
            new DependencyDeclaration("java:A", "java:B", EdgeType.IMPORTS, Confidence.HIGH, "import B", false)
        );

        DeclarationGraphBuilder.BuildResult result = DeclarationGraphBuilder.build(modules, deps);

        DependencyGraph graph = result.graph();
        assertEquals(2, graph.nodeCount());
        assertEquals(1, graph.edgeCount());
        assertTrue(graph.getEdge("A", "B").isPresent());
        assertEquals(com.archon.core.graph.EdgeType.IMPORTS, graph.getEdge("A", "B").get().getType());
    }

    @Test
    void buildDeduplicatesNodesById() {
        List<ModuleDeclaration> modules = List.of(
            new ModuleDeclaration("java:A", NodeType.CLASS, "A1.java", Confidence.HIGH),
            new ModuleDeclaration("java:A", NodeType.CLASS, "A2.java", Confidence.MEDIUM)
        );

        DeclarationGraphBuilder.BuildResult result = DeclarationGraphBuilder.build(
            modules, Collections.emptyList()
        );

        // Only first declaration should win
        assertEquals(1, result.graph().nodeCount());
        assertEquals("A1.java", result.graph().getNode("A").get().getSourcePath().orElse(null));
    }

    @Test
    void buildSkipsEdgesToMissingTargetsAndCollectsWarnings() {
        List<ModuleDeclaration> modules = List.of(
            new ModuleDeclaration("java:A", NodeType.CLASS, "A.java", Confidence.HIGH)
        );
        List<DependencyDeclaration> deps = List.of(
            new DependencyDeclaration("java:A", "java:Nonexistent", EdgeType.CALLS, Confidence.HIGH, null, false),
            new DependencyDeclaration("java:Ghost", "java:A", EdgeType.IMPORTS, Confidence.HIGH, null, false)
        );

        DeclarationGraphBuilder.BuildResult result = DeclarationGraphBuilder.build(modules, deps);

        DependencyGraph graph = result.graph();
        assertEquals(0, graph.edgeCount());
        assertEquals(2, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("Nonexistent"));
        assertTrue(result.warnings().get(1).contains("Ghost"));
    }

    @Test
    void buildStripsNamespacePrefixes() {
        List<ModuleDeclaration> modules = List.of(
            new ModuleDeclaration("java:com.example.Foo", NodeType.CLASS, "Foo.java", Confidence.HIGH),
            new ModuleDeclaration("py:os", NodeType.MODULE, "os.py", Confidence.HIGH)
        );

        DeclarationGraphBuilder.BuildResult result = DeclarationGraphBuilder.build(
            modules, Collections.emptyList()
        );

        DependencyGraph graph = result.graph();
        assertTrue(graph.containsNode("com.example.Foo"));
        assertTrue(graph.containsNode("os"));
        // Prefixed IDs should NOT exist
        assertFalse(graph.containsNode("java:com.example.Foo"));
        assertFalse(graph.containsNode("py:os"));
    }

    @Test
    void buildPreservesNodeMetadata() {
        List<ModuleDeclaration> modules = List.of(
            new ModuleDeclaration("java:Foo", NodeType.INTERFACE, "Foo.java", Confidence.MEDIUM)
        );

        DeclarationGraphBuilder.BuildResult result = DeclarationGraphBuilder.build(
            modules, Collections.emptyList()
        );

        DependencyGraph graph = result.graph();
        var node = graph.getNode("Foo").orElseThrow();
        assertEquals(com.archon.core.graph.NodeType.INTERFACE, node.getType());
        assertEquals("Foo.java", node.getSourcePath().orElse(null));
        assertEquals(com.archon.core.graph.Confidence.MEDIUM, node.getConfidence());
    }

    @Test
    void buildPreservesEdgeMetadata() {
        List<ModuleDeclaration> modules = List.of(
            new ModuleDeclaration("java:A", NodeType.CLASS, "A.java", Confidence.HIGH),
            new ModuleDeclaration("java:B", NodeType.CLASS, "B.java", Confidence.HIGH)
        );
        List<DependencyDeclaration> deps = List.of(
            new DependencyDeclaration("java:A", "java:B", EdgeType.EXTENDS, Confidence.HIGH, "class A extends B", true)
        );

        DeclarationGraphBuilder.BuildResult result = DeclarationGraphBuilder.build(modules, deps);

        var edge = result.graph().getEdge("A", "B").orElseThrow();
        assertEquals(com.archon.core.graph.EdgeType.EXTENDS, edge.getType());
        assertEquals("class A extends B", edge.getEvidence());
        assertTrue(edge.isDynamic());
    }
}
