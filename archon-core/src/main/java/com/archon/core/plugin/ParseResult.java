package com.archon.core.plugin;

import com.archon.core.graph.DependencyGraph;
import java.util.List;
import java.util.Set;

/**
 * Output from LanguagePlugin.parseFromContent().
 *
 * <p>Plugins populate declarations (ModuleDeclaration + DependencyDeclaration),
 * source modules, blind spots, and errors. The graph field is set by
 * ParseOrchestrator after assembling all declarations into a unified graph.
 *
 * <p>Plugin-constructed instances have a null graph. Orchestrator-constructed
 * instances include the assembled graph.
 */
public class ParseResult {
    private final DependencyGraph graph;
    private final Set<String> sourceModules;
    private final List<BlindSpot> blindSpots;
    private final List<String> parseErrors;
    private final List<ModuleDeclaration> moduleDeclarations;
    private final List<DependencyDeclaration> declarations;

    /**
     * Plugin constructor — declarations only, no graph.
     * Used by LanguagePlugin implementations.
     */
    public ParseResult(
        Set<String> sourceModules,
        List<BlindSpot> blindSpots,
        List<String> parseErrors
    ) {
        this(null, sourceModules, blindSpots, parseErrors, List.of(), List.of());
    }

    /**
     * Plugin constructor — declarations only, no graph.
     * Used by LanguagePlugin implementations.
     */
    public ParseResult(
        Set<String> sourceModules,
        List<BlindSpot> blindSpots,
        List<String> parseErrors,
        List<ModuleDeclaration> moduleDeclarations,
        List<DependencyDeclaration> declarations
    ) {
        this(null, sourceModules, blindSpots, parseErrors, moduleDeclarations, declarations);
    }

    /**
     * Full constructor — includes assembled graph.
     * Used by ParseOrchestrator after building graph from declarations.
     */
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

    /**
     * Returns the assembled dependency graph, or null if this result
     * was produced by a plugin (before orchestrator assembly).
     */
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
