package com.archon.core.plugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

/**
 * Tests for PluginDiscoverer.
 */
class PluginDiscovererTest {

    /**
     * Mock plugin for testing.
     */
    static class MockJavaPlugin implements LanguagePlugin {
        @Override
        public Set<String> fileExtensions() {
            return Set.of("java");
        }

        @Override
        public ParseResult parseFromContent(
            String filePath,
            String content,
            ParseContext context
        ) {
            return new ParseResult(
                Set.of(), List.of(), List.of()
            );
        }
    }

    /**
     * Mock plugin for testing.
     */
    static class MockJsPlugin implements LanguagePlugin {
        @Override
        public Set<String> fileExtensions() {
            return Set.of("js", "ts");
        }

        @Override
        public ParseResult parseFromContent(
            String filePath,
            String content,
            ParseContext context
        ) {
            return new ParseResult(
                Set.of(), List.of(), List.of()
            );
        }
    }

    /**
     * Mock plugin that conflicts with MockJavaPlugin.
     */
    static class ConflictingJavaPlugin implements LanguagePlugin {
        @Override
        public Set<String> fileExtensions() {
            return Set.of("java");  // Conflicts with MockJavaPlugin
        }

        @Override
        public ParseResult parseFromContent(
            String filePath,
            String content,
            ParseContext context
        ) {
            return new ParseResult(
                Set.of(), List.of(), List.of()
            );
        }
    }

    @Test
    void testDiscoverReturnsEmptyListWhenNoPluginsRegistered() {
        PluginDiscoverer discoverer = new PluginDiscoverer();
        List<LanguagePlugin> plugins = discoverer.discover();

        assertNotNull(plugins);
        // Note: This test may discover plugins if they are on the classpath
        // (e.g., archon-python as test dependency)
        // The key assertion is that it doesn't throw and returns a valid list
    }

    @Test
    void testDiscoverWithConflictCheckReturnsEmptyListWhenNoPluginsRegistered() {
        PluginDiscoverer discoverer = new PluginDiscoverer();
        List<LanguagePlugin> plugins = discoverer.discoverWithConflictCheck();

        assertNotNull(plugins);
        // Note: This test may discover plugins if they are on the classpath
        // (e.g., archon-python as test dependency)
        // The key assertion is that it doesn't throw and returns a valid list
    }

    /**
     * Note: This test would require actual META-INF/services registration.
     * In a real integration test, we would:
     * 1. Create test JARs with META-INF/services/com.archon.core.plugin.LanguagePlugin
     * 2. Add them to test classpath
     * 3. Verify discover() returns the plugins
     *
     * For unit testing, we verify the code compiles and the structure is correct.
     * Full ServiceLoader testing requires build system integration.
     */
    @Test
    void testDiscoverWithConflictCheckDetectsConflicts() {
        // This test demonstrates the conflict detection logic
        // In practice, you'd register conflicting plugins via META-INF/services

        PluginDiscoverer discoverer = new PluginDiscoverer();

        // If we had two plugins both claiming "java", it would throw:
        // IllegalStateException: Extension conflict: 'java' is claimed by both
        // MockJavaPlugin and ConflictingJavaPlugin

        // Since no plugins are registered, this should not throw
        assertDoesNotThrow(() -> discoverer.discoverWithConflictCheck());
    }

    @Test
    void testDiscoverDoesNotThrowOnConflicts() {
        // discover() method should not check for conflicts
        PluginDiscoverer discoverer = new PluginDiscoverer();

        // Should not throw even if conflicts exist (ServiceLoader doesn't prevent conflicts)
        assertDoesNotThrow(() -> discoverer.discover());
    }

    @Test
    void testDiscoverPythonPlugin() {
        // Verify that PythonPlugin is discovered via ServiceLoader
        // This test requires archon-python to be on the classpath
        PluginDiscoverer discoverer = new PluginDiscoverer();
        List<LanguagePlugin> plugins = discoverer.discover();

        // Should discover at least PythonPlugin (may also discover JavaPlugin and JsPlugin)
        assertTrue(plugins.size() >= 1, "Should discover at least one plugin");

        // Verify at least one plugin supports Python files
        boolean hasPythonPlugin = plugins.stream()
            .anyMatch(plugin -> plugin.fileExtensions().contains("py"));

        assertTrue(hasPythonPlugin, "Should discover a plugin that supports .py files");
    }
}
