package com.archon.js;

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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * LanguagePlugin implementation for JavaScript/TypeScript.
 *
 * <p>Uses regex-based parsing for ES modules. Supports:
 * <ul>
 *   <li>ES Modules (import/export)</li>
 *   <li>TypeScript (.ts, .tsx)</li>
 *   <li>CommonJS (require/module.exports) - reported as blind spot</li>
 *   <li>Dynamic imports - reported as blind spot</li>
 * </ul>
 *
 * <p>Node IDs use "js:" namespace prefix.
 */
public class JsPlugin implements LanguagePlugin {

    private static final String NAMESPACE = "js";
    private static final Set<String> EXTENSIONS = Set.of("js", "jsx", "ts", "tsx", "vue");

    private final JsAstVisitor astVisitor;
    private final VueFileExtractor vueExtractor;

    public JsPlugin() {
        this.astVisitor = new JsAstVisitor();
        this.vueExtractor = new VueFileExtractor();
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    // Maximum file size to parse (1MB) - prevents OOM on malformed files
    private static final int MAX_FILE_SIZE = 1024 * 1024;

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

            // Handle Vue files specially - extract script section first
            String contentToParse = content;
            if (filePath.endsWith(".vue")) {
                VueFileExtractor.ExtractionResult extraction = vueExtractor.extractScript(content);
                if (extraction == null) {
                    // No script block in Vue file - report as blind spot
                    blindSpots.add(new BlindSpot(
                        "VueNoScript",
                        moduleName,
                        "Vue file has no <script> block - cannot extract dependencies"
                    ));
                    return new ParseResult(
                        GraphBuilder.builder().build(),
                        sourceModules,
                        blindSpots,
                        parseErrors
                    );
                }
                contentToParse = extraction.scriptContent();
                // Add blind spot for Vue SFC format
                if (extraction.isSetup()) {
                    blindSpots.add(new BlindSpot(
                        "VueSetup",
                        moduleName,
                        "Vue <script setup> - partial parsing, template dependencies not analyzed"
                    ));
                }
            }

            // Use JsAstVisitor to extract dependencies
            JsAstVisitor.VisitResult visitResult = astVisitor.extractDependencies(
                contentToParse,
                filePath,
                context.getSourceRoot()
            );

            // Add edges for each import
            for (JsAstVisitor.ImportInfo importInfo : visitResult.imports()) {
                String targetId = importInfo.resolvedPath();

                // Edge to external dependency - builder will skip if target node doesn't exist
                Edge edge = Edge.builder()
                    .source(prefixedId)
                    .target(targetId)
                    .type(EdgeType.IMPORTS)
                    .build();

                builder.addEdge(edge);
            }

            // Collect blind spots from visitor
            blindSpots.addAll(visitResult.blindSpots());

        } catch (Exception e) {
            parseErrors.add(filePath + ":0 - Failed to parse: " + e.getMessage());
            // Don't print stack trace in production
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
     * Converts: /src/components/Header.tsx -> src/components/Header
     */
    private String extractModuleName(String filePath, Path sourceRoot) {
        try {
            Path absolutePath = Path.of(filePath).toAbsolutePath();
            Path relativePath = sourceRoot.toAbsolutePath().relativize(absolutePath);
            String moduleName = relativePath.toString()
                .replace(".js", "")
                .replace(".jsx", "")
                .replace(".ts", "")
                .replace(".tsx", "")
                .replace(".vue", "")
                .replace("\\", "/");
            return moduleName;
        } catch (Exception e) {
            // Fallback to filename only
            String fileName = Path.of(filePath).getFileName().toString();
            return fileName.replaceAll("\\.(js|jsx|ts|tsx|vue)$", "");
        }
    }

    /**
     * Reset any internal state. Should be called before parsing a new project.
     */
    public void reset() {
        // No internal state to reset in this implementation
    }
}
