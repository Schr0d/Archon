package com.archon.core.coordination;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.Node;
import com.archon.core.plugin.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates multiple LanguagePlugin implementations to build a unified graph.
 *
 * <h3>Declaration-based construction (primary path):</h3>
 * <p>Plugins return {@link ModuleDeclaration} and {@link DependencyDeclaration} records.
 * The orchestrator collects all declarations from all files and builds the graph
 * in a single pass:
 * <ol>
 *   <li>Create a node for each unique ModuleDeclaration.id (dedup by ID, keep first seen)</li>
 *   <li>For each DependencyDeclaration, create an edge (skip if target not in node map, log warning)</li>
 *   <li>Strip namespace prefixes before building</li>
 * </ol>
 *
 * <h3>Backward-compatible path (legacy):</h3>
 * <p>If a plugin's ParseResult has empty declarations, the orchestrator falls back to
 * merging the plugin's returned graph into the shared builder. This allows incremental
 * migration of plugins from the old graph-returning approach to the new declaration approach.
 *
 * <h3>Namespace Handling:</h3>
 * <ul>
 *   <li>Plugins add declarations with prefixed IDs: "java:com.example.Foo"</li>
 *   <li>Orchestrator strips prefixes before building final graph</li>
 * </ul>
 *
 * @see LanguagePlugin for plugin contract
 * @see ModuleDeclaration for node declarations
 * @see DependencyDeclaration for edge declarations
 */
public class ParseOrchestrator {

    private final List<LanguagePlugin> plugins;

    /**
     * Creates a new orchestrator with the given plugins.
     *
     * @param plugins list of language plugins to coordinate (must not be null)
     */
    public ParseOrchestrator(List<LanguagePlugin> plugins) {
        this.plugins = List.copyOf(Objects.requireNonNull(plugins, "plugins must not be null"));
    }

    /**
     * Parse source tree using all registered plugins.
     *
     * <p>Files are partitioned by extension and routed to the appropriate plugin.
     * Each plugin returns a ParseResult that may contain declarations (new path)
     * or a graph (legacy path). The orchestrator builds a unified graph from
     * all declarations, strips namespace prefixes, and returns the result.
     *
     * @param sourceFiles List of source file paths to parse
     * @param context Parse context with source root and extensions
     * @return Unified ParseResult from all plugins
     */
    public ParseResult parse(List<Path> sourceFiles, ParseContext context) {
        // Track which plugin handles which extension
        Map<String, LanguagePlugin> extensionToPlugin = buildExtensionMap();

        // Partition files by extension
        Map<String, List<Path>> filesByExtension = partitionFilesByExtension(sourceFiles);

        // Collect all declarations and results from all plugins
        Set<String> allSourceModules = new LinkedHashSet<>();
        List<BlindSpot> allBlindSpots = new ArrayList<>();
        List<String> allErrors = new ArrayList<>();
        List<ModuleDeclaration> allModuleDeclarations = new ArrayList<>();
        List<DependencyDeclaration> allDependencyDeclarations = new ArrayList<>();

        // Legacy fallback: collect graphs from plugins that don't return declarations
        DependencyGraph.MutableBuilder legacyPrefixedBuilder = new DependencyGraph.MutableBuilder();
        boolean hasLegacyResults = false;

        // Parse files with their respective plugins
        for (Map.Entry<String, List<Path>> entry : filesByExtension.entrySet()) {
            String ext = entry.getKey();
            LanguagePlugin plugin = extensionToPlugin.get(ext);

            if (plugin == null) {
                // Warn on unclaimed files
                System.err.println("Warning: No plugin found for '." + ext +
                    "' files; skipping " + entry.getValue().size() + " files.");
                continue;
            }

            for (Path file : entry.getValue()) {
                try {
                    // Check file size BEFORE reading to prevent OOM
                    long fileSize = Files.size(file);
                    long maxFileSize = 1024 * 1024; // 1MB limit
                    if (fileSize > maxFileSize) {
                        allErrors.add("Skipped " + file + ": file too large (" +
                            (fileSize / 1024) + " KB, max " + (maxFileSize / 1024) + " KB)");
                        continue;
                    }

                    String content = Files.readString(file);
                    ParseResult result = plugin.parseFromContent(
                        file.toString(),
                        content,
                        context
                    );

                    allSourceModules.addAll(result.getSourceModules());
                    allBlindSpots.addAll(result.getBlindSpots());
                    allErrors.addAll(result.getParseErrors());

                    // Check if plugin uses the new declaration path
                    if (!result.getModuleDeclarations().isEmpty()
                        || !result.getDeclarations().isEmpty()) {
                        // New path: collect declarations
                        allModuleDeclarations.addAll(result.getModuleDeclarations());
                        allDependencyDeclarations.addAll(result.getDeclarations());
                    } else {
                        // Legacy path: merge the returned graph
                        mergeGraphIntoBuilder(result.getGraph(), legacyPrefixedBuilder);
                        hasLegacyResults = true;
                    }
                } catch (IOException e) {
                    allErrors.add("Failed to read " + file + ": " + e.getMessage());
                }
            }
        }

        // Build the final graph
        DependencyGraph finalGraph;

        if (!allModuleDeclarations.isEmpty() || !allDependencyDeclarations.isEmpty()) {
            // New declaration-based path
            finalGraph = buildGraphFromDeclarations(
                allModuleDeclarations, allDependencyDeclarations,
                hasLegacyResults ? legacyPrefixedBuilder : null
            );
        } else if (hasLegacyResults) {
            // Legacy-only path: strip namespace prefixes from merged graphs
            finalGraph = DependencyGraph.stripNamespacePrefixesAndBuild(legacyPrefixedBuilder);
        } else {
            // No results at all — return empty graph
            finalGraph = new DependencyGraph.MutableBuilder().build();
        }

        return new ParseResult(
            finalGraph, allSourceModules, allBlindSpots, allErrors,
            allModuleDeclarations, allDependencyDeclarations
        );
    }

    /**
     * Builds a DependencyGraph from collected ModuleDeclaration and DependencyDeclaration records.
     *
     * <p>This method:
     * <ol>
     *   <li>Creates a node for each unique ModuleDeclaration.id (dedup by ID, keeps first seen)</li>
     *   <li>Creates edges from DependencyDeclarations (skips edges to missing targets, logs warning)</li>
     *   <li>If a legacy builder is provided, merges those nodes/edges first</li>
     *   <li>Strips namespace prefixes from the final graph</li>
     * </ol>
     *
     * @param moduleDeclarations collected module declarations from all plugins
     * @param dependencyDeclarations collected dependency declarations from all plugins
     * @param legacyBuilder optional legacy builder with graph-returned data (may be null)
     * @return unified graph with namespace prefixes stripped
     */
    private DependencyGraph buildGraphFromDeclarations(
        List<ModuleDeclaration> moduleDeclarations,
        List<DependencyDeclaration> dependencyDeclarations,
        DependencyGraph.MutableBuilder legacyBuilder
    ) {
        DependencyGraph.MutableBuilder prefixedBuilder = new DependencyGraph.MutableBuilder();

        // If there are legacy results, merge them first
        if (legacyBuilder != null) {
            DependencyGraph legacyGraph = legacyBuilder.build();
            mergeGraphIntoBuilder(legacyGraph, prefixedBuilder);
        }

        // Phase 1: Build node map from declarations (dedup by ID, keep first seen)
        Set<String> seenIds = new HashSet<>();
        for (ModuleDeclaration decl : moduleDeclarations) {
            if (seenIds.add(decl.id())) {
                Node node = Node.builder()
                    .id(decl.id())
                    .type(mapNodeType(decl.type()))
                    .sourcePath(decl.sourcePath())
                    .confidence(mapConfidence(decl.confidence()))
                    .build();
                prefixedBuilder.addNode(node);
            }
        }

        // Phase 2: Build edges from declarations
        Set<String> knownNodeIds = new HashSet<>(prefixedBuilder.knownNodeIds());
        for (DependencyDeclaration decl : dependencyDeclarations) {
            if (!knownNodeIds.contains(decl.sourceId())) {
                System.err.println("Warning: Edge source '" + decl.sourceId() +
                    "' not in node map; skipping edge.");
                continue;
            }
            if (!knownNodeIds.contains(decl.targetId())) {
                System.err.println("Warning: Edge target '" + decl.targetId() +
                    "' not in node map; skipping edge from '" + decl.sourceId() + "'.");
                continue;
            }
            Edge edge = Edge.builder()
                .source(decl.sourceId())
                .target(decl.targetId())
                .type(mapEdgeType(decl.edgeType()))
                .confidence(mapConfidence(decl.confidence()))
                .evidence(decl.evidence())
                .dynamic(decl.dynamic())
                .build();
            prefixedBuilder.addEdge(edge);
        }

        // Strip namespace prefixes and build final graph
        return DependencyGraph.stripNamespacePrefixesAndBuild(prefixedBuilder);
    }

    // --- Enum mapping functions: plugin enums -> graph enums ---

    private static com.archon.core.graph.NodeType mapNodeType(NodeType pluginNodeType) {
        return com.archon.core.graph.NodeType.valueOf(pluginNodeType.name());
    }

    private static com.archon.core.graph.EdgeType mapEdgeType(EdgeType pluginEdgeType) {
        return com.archon.core.graph.EdgeType.valueOf(pluginEdgeType.name());
    }

    private static com.archon.core.graph.Confidence mapConfidence(Confidence pluginConfidence) {
        return com.archon.core.graph.Confidence.valueOf(pluginConfidence.name());
    }

    // --- Legacy helpers ---

    /**
     * Merges all nodes and edges from a source graph into the target builder.
     * Used for backward compatibility with plugins that return graphs instead of declarations.
     */
    private void mergeGraphIntoBuilder(DependencyGraph source, DependencyGraph.MutableBuilder target) {
        for (String nodeId : source.getNodeIds()) {
            source.getNode(nodeId).ifPresent(target::addNode);
        }
        for (Edge edge : source.getAllEdges()) {
            target.addEdge(edge);
        }
    }

    // --- File routing helpers ---

    /**
     * Builds a mapping from file extension to the plugin that handles it.
     */
    private Map<String, LanguagePlugin> buildExtensionMap() {
        Map<String, LanguagePlugin> map = new HashMap<>();
        for (LanguagePlugin plugin : plugins) {
            for (String ext : plugin.fileExtensions()) {
                if (map.put(ext, plugin) != null) {
                    throw new IllegalStateException(
                        "Multiple plugins registered for extension '." + ext + "'");
                }
            }
        }
        return map;
    }

    /**
     * Partitions source files by their file extension.
     */
    private Map<String, List<Path>> partitionFilesByExtension(List<Path> sourceFiles) {
        return sourceFiles.stream()
            .filter(Files::exists)
            .collect(Collectors.groupingBy(
                file -> getExtension(file).orElse(""),
                LinkedHashMap::new,
                Collectors.toList()
            ));
    }

    /**
     * Extracts the file extension from a path.
     *
     * @param file the file path
     * @return the extension without the dot, or empty if no extension
     */
    private Optional<String> getExtension(Path file) {
        String name = file.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? Optional.of(name.substring(dotIndex + 1)) : Optional.empty();
    }
}
