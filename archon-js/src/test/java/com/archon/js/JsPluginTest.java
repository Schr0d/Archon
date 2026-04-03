package com.archon.js;

import com.archon.core.analysis.DomainStrategy;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.archon.core.graph.DependencyGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsPlugin LanguagePlugin implementation.
 */
@DisplayName("JsPlugin Tests")
class JsPluginTest {

    @Test
    @DisplayName("JsPlugin implements LanguagePlugin interface")
    void testJsPluginImplementsLanguagePlugin() {
        JsPlugin plugin = new JsPlugin();
        assertTrue(plugin instanceof LanguagePlugin);
    }

    @Test
    @DisplayName("JsPlugin supports js, jsx, ts, tsx file extensions")
    void testFileExtensionsIncludesJsAndTs() {
        JsPlugin plugin = new JsPlugin();
        Set<String> extensions = plugin.fileExtensions();

        assertTrue(extensions.contains("js"), "Should support .js files");
        assertTrue(extensions.contains("ts"), "Should support .ts files");
        assertTrue(extensions.contains("jsx"), "Should support .jsx files");
        assertTrue(extensions.contains("tsx"), "Should support .tsx files");
        assertEquals(4, extensions.size(), "Should have exactly 4 extensions");
    }

    @Test
    @DisplayName("JsPlugin returns JsDomainStrategy")
    void testGetDomainStrategyReturnsJsDomainStrategy() {
        JsPlugin plugin = new JsPlugin();
        var strategy = plugin.getDomainStrategy();

        assertTrue(strategy.isPresent(), "DomainStrategy should be present");
        assertTrue(strategy.get() instanceof JsDomainStrategy,
            "Should return JsDomainStrategy instance");
    }

    @Test
    @DisplayName("JsPlugin.parseFromContent returns ParseResult with namespace-prefixed modules")
    void testParseFromContentReturnsPrefixedModules() {
        JsPlugin plugin = new JsPlugin();
        ParseContext context = new ParseContext(
            Path.of("/src"),
            Set.of("ts")
        );
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        String tsCode = """
            export interface ButtonProps {
                label: string;
            }

            export function Button(props: ButtonProps) {
                return <button>{props.label}</button>;
            }
            """;

        ParseResult result = plugin.parseFromContent(
            "src/components/Button.tsx",
            tsCode,
            context,
            builder
        );

        assertNotNull(result, "ParseResult should not be null");
        assertFalse(result.getSourceModules().isEmpty(),
            "Should return at least one source module");

        // Verify namespace prefix
        String moduleId = result.getSourceModules().iterator().next();
        assertTrue(moduleId.startsWith("js:"),
            "Module ID should be prefixed with 'js:': " + moduleId);
    }

    @Test
    @DisplayName("JsPlugin handles parse errors gracefully")
    void testHandlesParseErrorsGracefully() {
        JsPlugin plugin = new JsPlugin();
        ParseContext context = new ParseContext(
            Path.of("/src"),
            Set.of("js")
        );
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        // Empty content should not throw
        ParseResult result = plugin.parseFromContent(
            "test.js",
            "",
            context,
            builder
        );

        assertNotNull(result, "ParseResult should not be null even for empty content");
    }

    @Test
    @DisplayName("JsPlugin.extractModuleName correctly extracts module names")
    void testExtractModuleNameCorrectlyExtractsModuleNames() {
        JsPlugin plugin = new JsPlugin();
        ParseContext context = new ParseContext(
            Path.of("/project/src"),
            Set.of("tsx")
        );
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        ParseResult result = plugin.parseFromContent(
            "/project/src/components/Header.tsx",
            "export const Header = () => <h1>Header</h1>;",
            context,
            builder
        );

        Set<String> modules = result.getSourceModules();
        assertFalse(modules.isEmpty(), "Should have extracted module name");

        String module = modules.iterator().next();
        assertTrue(module.contains("components/Header"),
            "Module should contain path: " + module);
    }

    @Test
    @DisplayName("JsPlugin reports blind spot for stub implementation")
    void testReportsBlindSpotForStubImplementation() {
        JsPlugin plugin = new JsPlugin();
        ParseContext context = new ParseContext(
            Path.of("/src"),
            Set.of("ts")
        );
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        ParseResult result = plugin.parseFromContent(
            "test.ts",
            "export const test = 1;",
            context,
            builder
        );

        // Should report a blind spot about stub implementation
        assertFalse(result.getBlindSpots().isEmpty(),
            "Should report blind spot for stub implementation");

        com.archon.core.plugin.BlindSpot blindSpot = result.getBlindSpots().get(0);
        assertEquals("StubImplementation", blindSpot.getType(),
            "Should be StubImplementation type");
        assertTrue(blindSpot.getDescription().contains("JsPlugin"),
            "Should mention JsPlugin in description");
    }
}
