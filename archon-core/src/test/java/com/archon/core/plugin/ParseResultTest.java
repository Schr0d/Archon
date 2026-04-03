package com.archon.core.plugin;

import com.archon.core.graph.DependencyGraph;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;

class ParseResultTest {
    @Test
    void testConstructorWithGraph() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        Set<String> modules = Set.of("com.example.Foo", "com.example.Bar");
        List<BlindSpot> blindSpots = List.of();

        ParseResult result = new ParseResult(graph, modules, blindSpots);

        assertEquals(graph, result.getGraph());
        assertEquals(modules, result.getSourceModules());
        assertEquals(blindSpots, result.getBlindSpots());
    }

    @Test
    void testConstructorWithErrors() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        Set<String> modules = Set.of("com.example.Foo");
        List<BlindSpot> blindSpots = List.of();
        List<String> errors = List.of("Syntax error in Bar.java");

        ParseResult result = new ParseResult(graph, modules, blindSpots, errors);

        assertEquals(errors, result.getParseErrors());
        assertTrue(result.hasErrors());
    }

    @Test
    void testGetSourceModulesNotSourceClasses() {
        // Verify naming is language-agnostic (sourceModules, not sourceClasses)
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        Set<String> modules = Set.of("src/components/Header.tsx");

        ParseResult result = new ParseResult(graph, modules, List.of());

        assertEquals(modules, result.getSourceModules());
        // No getSourceClasses() method
    }
}
