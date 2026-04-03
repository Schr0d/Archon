package com.archon.js;

import com.archon.core.analysis.DomainStrategy;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
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
import java.util.Optional;
import java.util.Set;

/**
 * LanguagePlugin implementation for JavaScript/TypeScript.
 *
 * <p>Uses Google Closure Compiler for parsing. Supports:
 * <ul>
 *   <li>ES Modules (import/export)</li>
 *   <li>TypeScript (.ts, .tsx)</li>
 *   <li>CommonJS (require/module.exports) - reported as blind spot</li>
 *   <li>Dynamic imports - reported as blind spot</li>
 * </ul>
 *
 * <p>Node IDs use "js:" namespace prefix.
 *
 * <p>TODO: Integrate Closure Compiler parsing via JsAstVisitor (Task 3.3)
 */
public class JsPlugin implements LanguagePlugin {

    private static final String NAMESPACE = "js";
    private static final Set<String> EXTENSIONS = Set.of("js", "jsx", "ts", "tsx");

    private final JsDomainStrategy domainStrategy;

    public JsPlugin() {
        this.domainStrategy = new JsDomainStrategy();
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

        try {
            // TODO: Integrate Closure Compiler parsing via JsAstVisitor (Task 3.3)
            // For now, create a minimal stub to satisfy the SPI contract

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

            // TODO: Extract actual imports via JsAstVisitor
            // For now, just report that we need full implementation
            if (!content.trim().isEmpty()) {
                // Add a blind spot about stub implementation
                blindSpots.add(new BlindSpot(
                    "StubImplementation",
                    moduleName,
                    "JsPlugin parsing not yet implemented - see Task 3.3 (JsAstVisitor)"
                ));
            }

        } catch (Exception e) {
            parseErrors.add(filePath + ":0 - Failed to parse: " + e.getMessage());
        }

        // Return empty graph for now (ParseOrchestrator will combine results)
        return new ParseResult(
            com.archon.core.graph.GraphBuilder.builder().build(),
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
                .replace("\\", "/");
            return moduleName;
        } catch (Exception e) {
            // Fallback to filename only
            String fileName = Path.of(filePath).getFileName().toString();
            return fileName.replaceAll("\\.(js|jsx|ts|tsx)$", "");
        }
    }

    /**
     * Reset any internal state. Should be called before parsing a new project.
     */
    public void reset() {
        // No internal state to reset in this implementation
    }
}
