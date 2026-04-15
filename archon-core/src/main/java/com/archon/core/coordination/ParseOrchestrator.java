package com.archon.core.coordination;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.plugin.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import com.archon.core.coordination.PostProcessResult;

/**
 * Orchestrates multiple LanguagePlugin implementations to build a unified graph.
 *
 * <p>Plugins return {@link ModuleDeclaration} and {@link DependencyDeclaration} records.
 * The orchestrator collects all declarations from all files and builds the graph
 * in a single pass:
 * <ol>
 *   <li>Create a node for each unique ModuleDeclaration.id (dedup by ID, keep first seen)</li>
 *   <li>For each DependencyDeclaration, create an edge (skip if target not in node map, log warning)</li>
 *   <li>Strip namespace prefixes before building</li>
 * </ol>
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
     * Each plugin returns a ParseResult containing declarations. The orchestrator
     * builds a unified graph from all declarations, strips namespace prefixes,
     * and returns the result.
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
                    long maxFileSize = ParseContext.MAX_FILE_SIZE;
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

                    // Collect declarations from all plugins
                    allModuleDeclarations.addAll(result.getModuleDeclarations());
                    allDependencyDeclarations.addAll(result.getDeclarations());
                } catch (IOException e) {
                    allErrors.add("Failed to read " + file + ": " + e.getMessage());
                } catch (Exception e) {
                    // Catch plugin crashes to preserve results from other plugins
                    allErrors.add("Plugin " + plugin.getClass().getSimpleName() +
                        " failed on " + file + ": " + e.getMessage());
                }
            }
        }

        // Build the final graph from declarations
        DependencyGraph finalGraph;

        if (!allModuleDeclarations.isEmpty() || !allDependencyDeclarations.isEmpty()) {
            DeclarationGraphBuilder.BuildResult result = DeclarationGraphBuilder.build(
                allModuleDeclarations, allDependencyDeclarations
            );
            finalGraph = result.graph();
            allErrors.addAll(result.warnings());
        } else {
            // No results at all — return empty graph
            finalGraph = new DependencyGraph.MutableBuilder().build();
        }

        // Post-processing hook: let plugins add edges based on the full declaration set
        List<BlindSpot> postProcessBlindSpots = new ArrayList<>();
        List<DependencyDeclaration> postProcessDeclarations = new ArrayList<>();
        for (LanguagePlugin plugin : plugins) {
            try {
                PostProcessResult ppResult = plugin.postProcess(allModuleDeclarations, context);
                postProcessDeclarations.addAll(ppResult.declarations());
                postProcessBlindSpots.addAll(ppResult.blindSpots());
            } catch (Exception e) {
                allErrors.add("Post-processor " + plugin.getClass().getSimpleName() +
                    " failed: " + e.getMessage());
            }
        }
        allBlindSpots.addAll(postProcessBlindSpots);
        if (!postProcessDeclarations.isEmpty()) {
            allDependencyDeclarations.addAll(postProcessDeclarations);
            DeclarationGraphBuilder.BuildResult ppBuildResult = DeclarationGraphBuilder.build(
                allModuleDeclarations, allDependencyDeclarations
            );
            finalGraph = ppBuildResult.graph();
            allErrors.addAll(ppBuildResult.warnings());
        }

        return new ParseResult(
            finalGraph, allSourceModules, allBlindSpots, allErrors,
            allModuleDeclarations, allDependencyDeclarations
        );
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
