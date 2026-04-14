package com.archon.core.plugin;

import com.archon.core.graph.DependencyGraph;
import java.util.List;
import java.util.Set;

/**
 * Output from LanguagePlugin.parseFromContent().
 * Contains the built graph, source modules discovered, blind spots, and any errors.
 * Plugins must call builder.build() and include the graph in ParseResult.
 */
public class ParseResult {
    private final DependencyGraph graph;
    private final Set<String> sourceModules;
    private final List<BlindSpot> blindSpots;
    private final List<String> parseErrors;
    private final List<ModuleDeclaration> moduleDeclarations;
    private final List<DependencyDeclaration> declarations;

    public ParseResult(
        DependencyGraph graph,
        Set<String> sourceModules,
        List<BlindSpot> blindSpots
    ) {
        this(graph, sourceModules, blindSpots, List.of());
    }

    public ParseResult(
        DependencyGraph graph,
        Set<String> sourceModules,
        List<BlindSpot> blindSpots,
        List<String> parseErrors
    ) {
        this(graph, sourceModules, blindSpots, parseErrors, List.of(), List.of());
    }

    public ParseResult(
        DependencyGraph graph,
        Set<String> sourceModules,
        List<BlindSpot> blindSpots,
        List<String> parseErrors,
        List<ModuleDeclaration> moduleDeclarations,
        List<DependencyDeclaration> declarations
    ) {
        this.graph = graph;
        this.sourceModules = Set.copyOf(sourceModules);
        this.blindSpots = List.copyOf(blindSpots);
        this.parseErrors = List.copyOf(parseErrors);
        this.moduleDeclarations = List.copyOf(moduleDeclarations);
        this.declarations = List.copyOf(declarations);
    }

    public DependencyGraph getGraph() {
        return graph;
    }

    public Set<String> getSourceModules() {
        return sourceModules;
    }

    public List<BlindSpot> getBlindSpots() {
        return blindSpots;
    }

    public List<String> getParseErrors() {
        return parseErrors;
    }

    public List<ModuleDeclaration> getModuleDeclarations() {
        return moduleDeclarations;
    }

    public List<DependencyDeclaration> getDeclarations() {
        return declarations;
    }

    public boolean hasErrors() {
        return !parseErrors.isEmpty();
    }
}
