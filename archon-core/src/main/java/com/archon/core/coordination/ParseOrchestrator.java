package com.archon.core.coordination;

import com.archon.core.analysis.DomainStrategy;
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
 * <h3>Two-Phase Construction:</h3>
 * <ol>
 *   <li><strong>Phase 1 (Nodes):</strong> All plugins add all their nodes to the builder.
 *       Each node ID is prefixed with the language namespace (e.g., "java:").</li>
 *   <li><strong>Phase 2 (Edges):</strong> All plugins add their edges.
 *       Edges reference prefixed node IDs; orchestrator strips prefixes before final build.</li>
 * </ol>
 *
 * <p>This two-phase approach prevents edge loss when Plugin A adds an edge
 * to a node that Plugin B hasn't added yet. All nodes exist before any edges are added.
 *
 * <h3>Namespace Handling:</h3>
 * <ul>
 *   <li>Plugins add nodes with prefix: builder.addNode("java:com.example.Foo", ...)</li>
 *   <li>Plugins add edges with prefix: builder.addEdge("java:Foo", "IMPORTS", "java:Bar")</li>
 *   <li>Orchestrator strips prefixes before building final graph</li>
 * </ul>
 *
 * @see LanguagePlugin for plugin contract
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
     * Each plugin adds nodes with language-prefixed IDs (e.g., "java:com.example.Foo").
     * After all plugins complete, namespace prefixes are stripped to create a unified graph.
     *
     * @param sourceFiles List of source file paths to parse
     * @param context Parse context with source root and extensions
     * @return Unified ParseResult from all plugins
     */
    public ParseResult parse(List<Path> sourceFiles, ParseContext context) {
        // Create a temporary builder for collecting prefixed nodes/edges
        DependencyGraph.MutableBuilder prefixedBuilder = new DependencyGraph.MutableBuilder();

        // Track which plugin handles which extension
        Map<String, LanguagePlugin> extensionToPlugin = buildExtensionMap();

        // Partition files by extension
        Map<String, List<Path>> filesByExtension = partitionFilesByExtension(sourceFiles);

        // Track all results from all plugins
        Set<String> allSourceModules = new HashSet<>();
        List<BlindSpot> allBlindSpots = new ArrayList<>();
        List<String> allErrors = new ArrayList<>();

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
                    String content = Files.readString(file);
                    ParseResult result = plugin.parseFromContent(
                        file.toString(),
                        content,
                        context,
                        prefixedBuilder
                    );
                    allSourceModules.addAll(result.getSourceModules());
                    allBlindSpots.addAll(result.getBlindSpots());
                    allErrors.addAll(result.getParseErrors());
                } catch (IOException e) {
                    allErrors.add("Failed to read " + file + ": " + e.getMessage());
                }
            }
        }

        // Strip namespace prefixes and rebuild graph
        DependencyGraph finalGraph = stripNamespacePrefixesAndBuild(prefixedBuilder);

        return new ParseResult(finalGraph, allSourceModules, allBlindSpots, allErrors);
    }

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
     * Strip language namespace prefixes from all node IDs and edge references.
     * Converts "java:com.example.Foo" -> "com.example.Foo".
     *
     * <p>This is necessary because plugins add prefixed node IDs to avoid collisions
     * during two-phase construction. After all plugins have added their nodes and edges,
     * we strip the prefixes to create a unified graph.
     *
     * <p>Since MutableBuilder doesn't support renaming, we extract all nodes/edges,
     * strip the prefixes, and rebuild the graph.
     */
    private DependencyGraph stripNamespacePrefixesAndBuild(DependencyGraph.MutableBuilder prefixedBuilder) {
        // First, build the prefixed graph to extract its contents
        DependencyGraph prefixedGraph = prefixedBuilder.build();

        // Create mapping from prefixed IDs to unprefixed IDs
        Map<String, String> idMapping = new HashMap<>();
        for (String nodeId : prefixedGraph.getNodeIds()) {
            String unprefixedId = stripNamespacePrefix(nodeId);
            idMapping.put(nodeId, unprefixedId);
        }

        // Build new graph with stripped IDs
        DependencyGraph.MutableBuilder finalBuilder = new DependencyGraph.MutableBuilder();

        // Add nodes with stripped IDs
        for (String prefixedId : prefixedGraph.getNodeIds()) {
            Node prefixedNode = prefixedGraph.getNode(prefixedId).orElseThrow();
            String unprefixedId = idMapping.get(prefixedId);

            // Create new node with stripped ID
            Node.Builder nodeBuilder = Node.builder()
                .id(unprefixedId)
                .type(prefixedNode.getType())
                .sourcePath(prefixedNode.getSourcePath().orElse(null))
                .confidence(prefixedNode.getConfidence());

            prefixedNode.getDomain().ifPresent(nodeBuilder::domain);
            prefixedNode.getTags().forEach(nodeBuilder::addTag);

            finalBuilder.addNode(nodeBuilder.build());
        }

        // Add edges with stripped source/target IDs
        for (Edge prefixedEdge : prefixedGraph.getAllEdges()) {
            String unprefixedSource = idMapping.get(prefixedEdge.getSource());
            String unprefixedTarget = idMapping.get(prefixedEdge.getTarget());

            if (unprefixedSource != null && unprefixedTarget != null) {
                Edge edge = Edge.builder()
                    .source(unprefixedSource)
                    .target(unprefixedTarget)
                    .type(prefixedEdge.getType())
                    .confidence(prefixedEdge.getConfidence())
                    .dynamic(prefixedEdge.isDynamic())
                    .evidence(prefixedEdge.getEvidence())
                    .build();

                finalBuilder.addEdge(edge);
            }
        }

        return finalBuilder.build();
    }

    /**
     * Strips the language namespace prefix from a node ID.
     * Examples:
     * <ul>
     *   <li>"java:com.example.Foo" -> "com.example.Foo"</li>
     *   <li>"js:src/components/Header" -> "src/components/Header"</li>
     *   <li>"com.example.Bar" -> "com.example.Bar" (no prefix, unchanged)</li>
     * </ul>
     */
    private String stripNamespacePrefix(String nodeId) {
        int colonIndex = nodeId.indexOf(':');
        if (colonIndex > 0 && colonIndex < nodeId.length() - 1) {
            return nodeId.substring(colonIndex + 1);
        }
        return nodeId;
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
