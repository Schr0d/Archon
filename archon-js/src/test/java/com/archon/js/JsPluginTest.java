package com.archon.js;

import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.archon.core.plugin.ModuleDeclaration;
import com.archon.core.plugin.DependencyDeclaration;
import com.archon.core.plugin.NodeType;
import com.archon.core.plugin.Confidence;
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
    @DisplayName("JsPlugin supports js, jsx, ts, tsx, vue file extensions")
    void testFileExtensionsIncludesJsAndTs() {
        JsPlugin plugin = new JsPlugin();
        Set<String> extensions = plugin.fileExtensions();

        assertTrue(extensions.contains("js"), "Should support .js files");
        assertTrue(extensions.contains("ts"), "Should support .ts files");
        assertTrue(extensions.contains("jsx"), "Should support .jsx files");
        assertTrue(extensions.contains("tsx"), "Should support .tsx files");
        assertTrue(extensions.contains("vue"), "Should support .vue files");
        assertEquals(5, extensions.size(), "Should have exactly 5 extensions");
    }

    @Test
    @DisplayName("JsPlugin.parseFromContent returns ParseResult with namespace-prefixed modules")
    void testParseFromContentReturnsPrefixedModules() {
        JsPlugin plugin = new JsPlugin();
        ParseContext context = new ParseContext(
            Path.of("/src"),
            Set.of("ts")
        );

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
            context
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

        // Empty content should not throw
        ParseResult result = plugin.parseFromContent(
            "test.js",
            "",
            context
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

        ParseResult result = plugin.parseFromContent(
            "/project/src/components/Header.tsx",
            "export const Header = () => <h1>Header</h1>;",
            context
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

        ParseResult result = plugin.parseFromContent(
            "test.ts",
            "export const test = 1;",
            context
        );

        // Should NOT report blind spots for simple exports (actual implementation)
        assertTrue(result.getBlindSpots().isEmpty(),
            "Should not report blind spots for simple exports");

        // Should have parsed the source module
        assertFalse(result.getSourceModules().isEmpty(),
            "Should have source modules");
        assertTrue(result.getSourceModules().iterator().next().contains("test"),
            "Source module should contain 'test'");
    }

    @Test
    @DisplayName("JsPlugin returns module declarations alongside graph")
    void testReturnsModuleDeclarations() {
        JsPlugin plugin = new JsPlugin();
        ParseContext context = new ParseContext(
            Path.of("/src"),
            Set.of("ts")
        );

        ParseResult result = plugin.parseFromContent(
            "src/components/Button.tsx",
            "export const Button = () => <button />;",
            context
        );

        // Verify module declarations are populated
        assertFalse(result.getModuleDeclarations().isEmpty(),
            "Should return at least one module declaration");

        ModuleDeclaration decl = result.getModuleDeclarations().get(0);
        assertTrue(decl.id().startsWith("js:"),
            "Declaration ID should be prefixed: " + decl.id());
        assertEquals(NodeType.MODULE, decl.type(),
            "Declaration type should be MODULE");
        assertEquals(Confidence.HIGH, decl.confidence(),
            "Declaration confidence should be HIGH");
        assertEquals("src/components/Button.tsx", decl.sourcePath(),
            "Declaration sourcePath should match file path");
    }

    @Test
    @DisplayName("JsPlugin returns dependency declarations for imports")
    void testReturnsDependencyDeclarations() {
        JsPlugin plugin = new JsPlugin();
        ParseContext context = new ParseContext(
            Path.of("/src"),
            Set.of("ts")
        );

        String code = """
            import { utils } from './utils';
            export const Button = () => <button />;
            """;

        ParseResult result = plugin.parseFromContent(
            "src/components/Button.tsx",
            code,
            context
        );

        // Verify dependency declarations are populated
        assertFalse(result.getDeclarations().isEmpty(),
            "Should return at least one dependency declaration");

        DependencyDeclaration decl = result.getDeclarations().get(0);
        assertTrue(decl.sourceId().startsWith("js:"),
            "Source ID should be prefixed: " + decl.sourceId());
        assertTrue(decl.targetId().startsWith("js:"),
            "Target ID should be prefixed: " + decl.targetId());
        assertEquals(com.archon.core.plugin.EdgeType.IMPORTS, decl.edgeType(),
            "Edge type should be IMPORTS");
        assertEquals(Confidence.HIGH, decl.confidence(),
            "Confidence should be HIGH");
        assertFalse(decl.dynamic(),
            "Static import should not be dynamic");
    }
}
