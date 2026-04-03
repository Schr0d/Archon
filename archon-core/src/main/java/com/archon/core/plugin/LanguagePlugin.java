package com.archon.core.plugin;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.analysis.DomainStrategy;
import java.util.Optional;
import java.util.Set;

/**
 * Service Provider Interface for language-specific dependency parsing.
 *
 * <p>Implementations are discovered via ServiceLoader (META-INF/services).
 * Each plugin handles a specific language or file type (Java, JavaScript, etc.).
 *
 * <h3>Contract Requirements:</h3>
 * <ul>
 *   <li>fileExtensions() must return non-empty set of supported extensions</li>
 *   <li>parseFromContent() must add nodes with namespace prefix (e.g., "java:")</li>
 *   <li>parseFromContent() must handle syntax errors gracefully (return ParseErrors)</li>
 *   <li>getDomainStrategy() may return Optional.empty() for fallback detection</li>
 * </ul>
 *
 * <h3>Namespace Prefixing:</h3>
 * All node IDs added to MutableBuilder must be prefixed with the language identifier
 * followed by colon (e.g., "java:com.example.Foo", "js:src/components/Header").
 * ParseOrchestrator strips these prefixes before adding to the final graph.
 *
 * @see PluginDiscoverer for ServiceLoader discovery
 * @see ParseOrchestrator for multi-plugin coordination
 */
public interface LanguagePlugin {

    /**
     * Returns file extensions this plugin handles.
     * Must include the leading dot (e.g., "java", "js", "ts", "tsx").
     *
     * @return non-empty set of file extensions
     */
    Set<String> fileExtensions();

    /**
     * Returns domain assignment strategy for this language.
     * Optional.empty() indicates the plugin has no domain concept —
     * ParseOrchestrator will use fallback pivot detection.
     *
     * @return domain strategy, or empty for fallback behavior
     */
    Optional<DomainStrategy> getDomainStrategy();

    /**
     * Parse a single source file and add its nodes/edges to the builder.
     *
     * <p>This method is called for each file matching the plugin's extensions.
     * Implementations must:
     * <ul>
     *   <li>Add namespace-prefixed nodes: builder.addNode("java:com.example.Foo", ...)</li>
     *   <li>Add namespace-prefixed edges: builder.addEdge("java:Foo", "IMPORTS", "java:Bar")</li>
     *   <li>Report dynamic patterns as BlindSpots (reflection, computed imports)</li>
     *   <li>Handle syntax errors by adding to parseErrors, not throwing</li>
     * </ul>
     *
     * @param filePath Full path to the source file
     * @param content File content as string
     * @param context Parse context with source root and extensions
     * @param builder Mutable builder for accumulating graph nodes/edges
     * @return ParseResult with built graph, source modules, blind spots, errors
     */
    ParseResult parseFromContent(
        String filePath,
        String content,
        ParseContext context,
        DependencyGraph.MutableBuilder builder
    );
}
