package com.archon.core.plugin;

import com.archon.core.graph.DependencyGraph;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;

class ParseResultTest {
    @Test
    void testFullConstructorWithGraph() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        Set<String> modules = Set.of("com.example.Foo", "com.example.Bar");
        List<BlindSpot> blindSpots = List.of();

        ParseResult result = new ParseResult(graph, modules, blindSpots, List.of(), List.of(), List.of());

        assertEquals(graph, result.getGraph());
        assertEquals(modules, result.getSourceModules());
        assertEquals(blindSpots, result.getBlindSpots());
    }

    @Test
    void testConstructorWithErrors() {
        Set<String> modules = Set.of("com.example.Foo");
        List<String> errors = List.of("Syntax error in Bar.java");

        ParseResult result = new ParseResult(modules, List.of(), errors);

        assertEquals(errors, result.getParseErrors());
        assertTrue(result.hasErrors());
    }

    @Test
    void testGetSourceModulesNotSourceClasses() {
        // Verify naming is language-agnostic (sourceModules, not sourceClasses)
        Set<String> modules = Set.of("src/components/Header.tsx");

        ParseResult result = new ParseResult(modules, List.of(), List.of());

        assertEquals(modules, result.getSourceModules());
    }

    @Test
    void testPluginConstructorDefaultsDeclarationsToEmpty() {
        ParseResult result = new ParseResult(Set.of(), List.of(), List.of());

        assertNotNull(result.getModuleDeclarations());
        assertNotNull(result.getDeclarations());
        assertTrue(result.getModuleDeclarations().isEmpty());
        assertTrue(result.getDeclarations().isEmpty());
    }

    @Test
    void testFullConstructorWithDeclarations() {
        ModuleDeclaration modDecl = new ModuleDeclaration(
            "java:com.example.Foo", NodeType.CLASS, "src/Foo.java", Confidence.HIGH
        );
        DependencyDeclaration depDecl = new DependencyDeclaration(
            "java:com.example.Foo", "java:com.example.Bar",
            EdgeType.IMPORTS, Confidence.HIGH, "import Bar;", false
        );

        ParseResult result = new ParseResult(
            Set.of("java:com.example.Foo"),
            List.of(),
            List.of(),
            List.of(modDecl),
            List.of(depDecl)
        );

        assertEquals(1, result.getModuleDeclarations().size());
        assertEquals(modDecl, result.getModuleDeclarations().get(0));
        assertEquals(1, result.getDeclarations().size());
        assertEquals(depDecl, result.getDeclarations().get(0));
    }

    @Test
    void testDeclarationsAreDefensivelyCopied() {
        ModuleDeclaration modDecl = new ModuleDeclaration(
            "java:com.example.Foo", NodeType.CLASS, "src/Foo.java", Confidence.HIGH
        );

        List<ModuleDeclaration> modList = new java.util.ArrayList<>();
        modList.add(modDecl);

        ParseResult result = new ParseResult(
            Set.of(), List.of(), List.of(), modList, List.of()
        );

        // Mutating original list should not affect ParseResult
        modList.clear();

        assertEquals(1, result.getModuleDeclarations().size());
    }

    @Test
    void testPluginConstructorReturnsNullGraph() {
        ParseResult result = new ParseResult(Set.of("A"), List.of(), List.of());

        assertNull(result.getGraph(), "Plugin-constructed ParseResult should return null graph");
        assertTrue(result.getSourceModules().contains("A"));
        assertTrue(result.getModuleDeclarations().isEmpty());
        assertTrue(result.getDeclarations().isEmpty());
    }

    @Test
    void testPlugin5ArgConstructorReturnsNullGraph() {
        ModuleDeclaration modDecl = new ModuleDeclaration(
            "java:com.example.Foo", NodeType.CLASS, "Foo.java", Confidence.HIGH
        );
        DependencyDeclaration depDecl = new DependencyDeclaration(
            "java:com.example.Foo", "java:com.example.Bar",
            EdgeType.IMPORTS, Confidence.HIGH, null, false
        );

        ParseResult result = new ParseResult(
            Set.of("com.example.Foo"), List.of(), List.of(),
            List.of(modDecl), List.of(depDecl)
        );

        assertNull(result.getGraph(), "5-arg plugin constructor should return null graph");
        assertEquals(1, result.getModuleDeclarations().size());
        assertEquals(1, result.getDeclarations().size());
    }
}
