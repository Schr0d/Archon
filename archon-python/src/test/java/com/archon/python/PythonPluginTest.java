package com.archon.python;

import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.archon.core.graph.DependencyGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Set;

@DisplayName("PythonPlugin Tests")
class PythonPluginTest {

    @Test
    @DisplayName("PythonPlugin implements LanguagePlugin interface")
    void testPythonPluginImplementsLanguagePlugin() {
        PythonPlugin plugin = new PythonPlugin();
        assertTrue(plugin instanceof LanguagePlugin);
    }

    @Test
    @DisplayName("PythonPlugin supports py, pyi, pyw file extensions")
    void testFileExtensions() {
        PythonPlugin plugin = new PythonPlugin();
        Set<String> extensions = plugin.fileExtensions();

        assertTrue(extensions.contains("py"), "Should support .py files");
        assertTrue(extensions.contains("pyi"), "Should support .pyi files");
        assertTrue(extensions.contains("pyw"), "Should support .pyw files");
        assertEquals(3, extensions.size(), "Should have exactly 3 extensions");
    }

    @Test
    @DisplayName("PythonPlugin.parseFromContent returns ParseResult with namespace-prefixed modules")
    void testParseFromContentReturnsPrefixedModules() {
        PythonPlugin plugin = new PythonPlugin();
        ParseContext context = new ParseContext(
            Path.of("/project/src"),
            Set.of("py")
        );
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        String pyCode = """
            import os
            from pathlib import Path
            """;

        ParseResult result = plugin.parseFromContent(
            "src/utils/helpers.py",
            pyCode,
            context,
            builder
        );

        assertNotNull(result, "ParseResult should not be null");
        assertFalse(result.getSourceModules().isEmpty(),
            "Should return at least one source module");

        // Verify namespace prefix
        String moduleId = result.getSourceModules().iterator().next();
        assertTrue(moduleId.startsWith("py:"),
            "Module ID should be prefixed with 'py:': " + moduleId);
    }

    @Test
    @DisplayName("PythonPlugin handles parse errors gracefully")
    void testHandlesParseErrorsGracefully() {
        PythonPlugin plugin = new PythonPlugin();
        ParseContext context = new ParseContext(
            Path.of("/src"),
            Set.of("py")
        );
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        // Empty content should not throw
        ParseResult result = plugin.parseFromContent(
            "test.py",
            "",
            context,
            builder
        );

        assertNotNull(result, "ParseResult should not be null even for empty content");
    }

    @Test
    @DisplayName("PythonPlugin extracts module name correctly from file path")
    void testExtractModuleName() {
        PythonPlugin plugin = new PythonPlugin();
        ParseContext context = new ParseContext(
            Path.of("/project/src"),
            Set.of("py")
        );
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        ParseResult result = plugin.parseFromContent(
            "/project/src/utils/helpers.py",
            "import os",
            context,
            builder
        );

        Set<String> modules = result.getSourceModules();
        assertFalse(modules.isEmpty(), "Should have extracted module name");

        String module = modules.iterator().next();
        assertTrue(module.contains("utils/helpers") || module.contains("utils.helpers"),
            "Module should contain path: " + module);
    }

    @Test
    @DisplayName("PythonPlugin extracts module name from .pyi file")
    void testExtractModuleNameFromPyiFile() {
        PythonPlugin plugin = new PythonPlugin();
        ParseContext context = new ParseContext(
            Path.of("/project/src"),
            Set.of("pyi")
        );
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        ParseResult result = plugin.parseFromContent(
            "/project/src/utils/helpers.pyi",
            "from typing import List",
            context,
            builder
        );

        Set<String> modules = result.getSourceModules();
        assertFalse(modules.isEmpty(), "Should have extracted module name from .pyi file");

        String module = modules.iterator().next();
        assertTrue(module.contains("utils/helpers"),
            "Module should contain path (without .pyi extension): " + module);
    }

    @Test
    @DisplayName("PythonPlugin filters stdlib imports")
    void testFiltersStdlibImports() {
        PythonPlugin plugin = new PythonPlugin();
        ParseContext context = new ParseContext(
            Path.of("/project/src"),
            Set.of("py")
        );
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        String pyCode = """
            import os
            import sys
            from pathlib import Path
            """;

        ParseResult result = plugin.parseFromContent(
            "src/utils/helpers.py",
            pyCode,
            context,
            builder
        );

        // Should have source module
        assertFalse(result.getSourceModules().isEmpty(),
            "Should return source module");

        // Blind spots should report stdlib filtering
        // (This will be tested more specifically once blind spot reporting is implemented)
        assertNotNull(result, "ParseResult should exist");
    }
}
