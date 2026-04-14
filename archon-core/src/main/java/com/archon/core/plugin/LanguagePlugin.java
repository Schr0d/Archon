package com.archon.core.plugin;

import com.archon.core.graph.DependencyGraph;
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
     * Parse a single source file and return declarations.
     *
     * <p>This method is called for each file matching the plugin's extensions.
     * Implementations must:
     * <ul>
     *   <li>Return ModuleDeclarations with namespace-prefixed IDs (e.g., "java:com.example.Foo")</li>
     *   <li>Return DependencyDeclarations with namespace-prefixed source/target IDs</li>
     *   <li>Report dynamic patterns as BlindSpots (reflection, computed imports)</li>
     *   <li>Handle syntax errors by adding to parseErrors, not throwing</li>
     * </ul>
     *
     * @param filePath Full path to the source file
     * @param content File content as string
     * @param context Parse context with source root and extensions
     * @return ParseResult with graph, source modules, blind spots, errors, declarations
     */
    ParseResult parseFromContent(
        String filePath,
        String content,
        ParseContext context
    );

    /**
     * Reset any internal state between parse runs.
     * <p>
     * This method is called before parsing a new project to ensure
     * plugins don't carry polluted state from previous runs.
     * <p>
     * Default implementation does nothing. Plugins that maintain
     * internal state should override this method.
     */
    default void reset() {
        // No-op by default; plugins with state should override
    }
}
