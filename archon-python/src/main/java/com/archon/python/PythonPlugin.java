package com.archon.python;

import com.archon.core.analysis.DomainStrategy;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import com.archon.core.plugin.BlindSpot;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * LanguagePlugin implementation for Python.
 *
 * <p>Uses regex-based parsing for Python import statements. Supports:
 * <ul>
 *   <li>Simple imports: {@code import foo}</li>
 *   <li>Imports with aliases: {@code import foo as bar}</li>
 *   <li>From imports: {@code from foo import bar}</li>
 *   <li>Relative imports: {@code from . import sibling}, {@code from .. import parent}</li>
 *   <li>Type stub files: .pyi files</li>
 * </ul>
 *
 * <p>Filters standard library modules from the dependency graph.
 *
 * <p>Node IDs use "py:" namespace prefix (e.g., "py:src.utils.helpers").
 */
public class PythonPlugin implements LanguagePlugin {

    private static final String NAMESPACE = "py";
    private static final Set<String> EXTENSIONS = Set.of("py", "pyi", "pyw");

    // Maximum file size to parse (1MB) - prevents OOM on malformed files
    private static final int MAX_FILE_SIZE = 1024 * 1024;

    private final PythonDomainStrategy domainStrategy;
    private final PythonModuleResolver moduleResolver;

    public PythonPlugin() {
        this.domainStrategy = new PythonDomainStrategy();
        this.moduleResolver = new PythonModuleResolver();
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    @Override
    public Optional<DomainStrategy> getDomainStrategy() {
        return Optional.of(domainStrategy);
    }

    @Override
    public ParseResult parseFromContent(
        String filePath,
        String content,
        ParseContext context,
        DependencyGraph.MutableBuilder builder
    ) {
        List<String> parseErrors = new ArrayList<>();
        List<BlindSpot> blindSpots = new ArrayList<>();
        Set<String> sourceModules = new HashSet<>();

        // Check file size to prevent OOM on malformed inputs
        if (content.length() > MAX_FILE_SIZE) {
            parseErrors.add(filePath + ":0 - File too large to parse (" +
                (content.length() / 1024) + " KB, max " + (MAX_FILE_SIZE / 1024) + " KB)");
            return new ParseResult(
                GraphBuilder.builder().build(),
                sourceModules,
                blindSpots,
                parseErrors
            );
        }

        try {
            // Extract module name from file path
            String moduleName = extractModuleName(filePath, context.getSourceRoot());
            String prefixedId = NAMESPACE + ":" + moduleName;
            sourceModules.add(prefixedId);

            // Create a node for this module
            Node node = Node.builder()
                .id(prefixedId)
                .type(NodeType.MODULE)
                .sourcePath(filePath)
                .build();
            builder.addNode(node);

            // Extract imports using PythonImportExtractor
            Set<String> imports = PythonImportExtractor.extractImports(content);

            // Add edges for each import
            for (String importModule : imports) {
                String targetModuleName = importModule;

                // Check if it's a relative import (starts with dots)
                if (importModule.startsWith(".")) {
                    // Resolve relative import
                    String currentPackage = extractPackage(filePath, context.getSourceRoot());
                    Optional<String> resolved = moduleResolver.resolve(
                        importModule,
                        currentPackage,
                        context.getSourceRoot().toString()
                    );

                    if (resolved.isPresent()) {
                        targetModuleName = resolved.get();
                    } else {
                        // Relative import resolution failed — report as blind spot
                        blindSpots.add(new BlindSpot(
                            "RelativeImportNotFound",
                            moduleName,
                            "Could not resolve relative import: " + importModule
                        ));
                        continue; // Skip adding edge
                    }
                }

                // Filter out stdlib modules
                if (PythonStdlib.isStdlib(targetModuleName)) {
                    // Stdlib filtering — skip edge, optionally report as info
                    continue;
                }

                // Add namespace prefix to target
                String targetId = NAMESPACE + ":" + targetModuleName;

                Edge edge = Edge.builder()
                    .source(prefixedId)
                    .target(targetId)
                    .type(EdgeType.IMPORTS)
                    .build();

                builder.addEdge(edge);
            }

        } catch (Exception e) {
            parseErrors.add(filePath + ":0 - Failed to parse: " + e.getMessage());
        }

        // Return result with empty graph (ParseOrchestrator combines all results)
        return new ParseResult(
            GraphBuilder.builder().build(),
            sourceModules,
            blindSpots,
            parseErrors
        );
    }

    /**
     * Extract module name from file path.
     * Converts: /src/utils/helpers.py → src/utils/helpers
     *
     * @param filePath the full path to the Python file
     * @param sourceRoot the source root directory
     * @return the module name
     */
    private String extractModuleName(String filePath, Path sourceRoot) {
        try {
            Path absolutePath = Path.of(filePath).toAbsolutePath();
            Path relativePath = sourceRoot.toAbsolutePath().relativize(absolutePath);
            String moduleName = relativePath.toString()
                .replace(".py", "")
                .replace(".pyi", "")
                .replace(".pyw", "")
                .replace("\\", "/");

            // Ensure we don't return an empty module name
            if (moduleName == null || moduleName.trim().isEmpty()) {
                throw new IllegalArgumentException("Module name is empty after relativization");
            }

            return moduleName;
        } catch (Exception e) {
            // Fallback to filename only
            String fileName = Path.of(filePath).getFileName().toString();
            String moduleName = fileName.replaceAll("\\.(py|pyi|pyw)$", "");

            // Final fallback: use a default name if still empty
            if (moduleName == null || moduleName.trim().isEmpty()) {
                moduleName = "unknown_module";
            }

            return moduleName;
        }
    }

    /**
     * Extract package name from file path for relative import resolution.
     *
     * @param filePath the full path to the Python file
     * @param sourceRoot the source root directory
     * @return the package name (e.g., "src.utils" or "" for root)
     */
    private String extractPackage(String filePath, Path sourceRoot) {
        try {
            Path absolutePath = Path.of(filePath).toAbsolutePath();
            Path relativePath = sourceRoot.toAbsolutePath().relativize(absolutePath);

            // Remove filename and extension, keep directory path
            Path dirPath = relativePath.getParent();
            if (dirPath == null) {
                return ""; // Root level
            }

            // Convert path separators to dots for package name
            return dirPath.toString().replace("/", ".");
        } catch (Exception e) {
            return ""; // Fallback to empty
        }
    }

    /**
     * Reset the internal state. Should be called before parsing a new project.
     */
    @Override
    public void reset() {
        moduleResolver.reset();
    }
}
