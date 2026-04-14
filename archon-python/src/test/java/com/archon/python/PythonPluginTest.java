package com.archon.python;

import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.archon.core.plugin.ModuleDeclaration;
import com.archon.core.plugin.DependencyDeclaration;
import com.archon.core.plugin.NodeType;
import com.archon.core.plugin.EdgeType;
import com.archon.core.graph.DependencyGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
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

        String pyCode = """
            import os
            from pathlib import Path
            """;

        ParseResult result = plugin.parseFromContent(
            "src/utils/helpers.py",
            pyCode,
            context
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

        // Empty content should not throw
        ParseResult result = plugin.parseFromContent(
            "test.py",
            "",
            context
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

        ParseResult result = plugin.parseFromContent(
            "/project/src/utils/helpers.py",
            "import os",
            context
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

        ParseResult result = plugin.parseFromContent(
            "/project/src/utils/helpers.pyi",
            "from typing import List",
            context
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

        String pyCode = """
            import os
            import sys
            from pathlib import Path
            """;

        ParseResult result = plugin.parseFromContent(
            "src/utils/helpers.py",
            pyCode,
            context
        );

        // Should have source module
        assertFalse(result.getSourceModules().isEmpty(),
            "Should return source module");

        // Blind spots should report stdlib filtering
        // (This will be tested more specifically once blind spot reporting is implemented)
        assertNotNull(result, "ParseResult should exist");
    }

    // === Declaration tests ===

    @Test
    @DisplayName("parseFromContent returns ModuleDeclaration with py: prefix")
    void testModuleDeclarationsReturned() {
        PythonPlugin plugin = new PythonPlugin();
        ParseContext context = new ParseContext(
            Path.of("/project/src"),
            Set.of("py")
        );

        ParseResult result = plugin.parseFromContent(
            "/project/src/utils/helpers.py",
            "import requests",
            context
        );

        List<ModuleDeclaration> modDecls = result.getModuleDeclarations();
        assertFalse(modDecls.isEmpty(), "Should return at least one module declaration");

        ModuleDeclaration decl = modDecls.get(0);
        assertTrue(decl.id().startsWith("py:"),
            "Module declaration ID should be prefixed with 'py:': " + decl.id());
        assertEquals(NodeType.MODULE, decl.type(),
            "Module declaration type should be MODULE");
        assertEquals("/project/src/utils/helpers.py", decl.sourcePath(),
            "Source path should match the input file path");
    }

    @Test
    @DisplayName("parseFromContent returns DependencyDeclaration for non-stdlib imports")
    void testDependencyDeclarationsReturned() {
        PythonPlugin plugin = new PythonPlugin();
        ParseContext context = new ParseContext(
            Path.of("/project/src"),
            Set.of("py")
        );

        String pyCode = """
            import requests
            from flask import Flask
            """;

        ParseResult result = plugin.parseFromContent(
            "/project/src/app.py",
            pyCode,
            context
        );

        List<DependencyDeclaration> depDecls = result.getDeclarations();
        assertFalse(depDecls.isEmpty(),
            "Should return dependency declarations for non-stdlib imports");
        // Should have 2 declarations: requests and flask
        assertEquals(2, depDecls.size(),
            "Should have 2 dependency declarations (requests + flask)");

        for (DependencyDeclaration decl : depDecls) {
            assertTrue(decl.sourceId().startsWith("py:"),
                "Source ID should be prefixed: " + decl.sourceId());
            assertTrue(decl.targetId().startsWith("py:"),
                "Target ID should be prefixed: " + decl.targetId());
            assertEquals(EdgeType.IMPORTS, decl.edgeType(),
                "Edge type should be IMPORTS");
            assertFalse(decl.dynamic(),
                "Regex-parsed imports should not be dynamic");
            assertNotNull(decl.evidence(),
                "Evidence should not be null");
        }
    }

    @Test
    @DisplayName("parseFromContent excludes stdlib imports from declarations")
    void testStdlibExcludedFromDeclarations() {
        PythonPlugin plugin = new PythonPlugin();
        ParseContext context = new ParseContext(
            Path.of("/project/src"),
            Set.of("py")
        );

        String pyCode = """
            import os
            import requests
            from pathlib import Path
            """;

        ParseResult result = plugin.parseFromContent(
            "/project/src/app.py",
            pyCode,
            context
        );

        List<DependencyDeclaration> depDecls = result.getDeclarations();
        // Only 'requests' should be in declarations; os and pathlib are stdlib
        assertEquals(1, depDecls.size(),
            "Should have 1 dependency declaration (only requests, not os/pathlib)");
        assertTrue(depDecls.get(0).targetId().contains("requests"),
            "The only declaration should be for requests: " + depDecls.get(0).targetId());
    }

    @Test
    @DisplayName("parseFromContent returns empty declarations for empty content")
    void testEmptyDeclarationsForEmptyContent() {
        PythonPlugin plugin = new PythonPlugin();
        ParseContext context = new ParseContext(
            Path.of("/project/src"),
            Set.of("py")
        );

        ParseResult result = plugin.parseFromContent(
            "empty.py",
            "",
            context
        );

        // Empty content should still produce a module declaration for the file itself
        List<ModuleDeclaration> modDecls = result.getModuleDeclarations();
        assertEquals(1, modDecls.size(),
            "Empty file should still have one module declaration");

        // No dependency declarations for empty file
        List<DependencyDeclaration> depDecls = result.getDeclarations();
        assertTrue(depDecls.isEmpty(),
            "Empty file should have no dependency declarations");
    }

    @Test
    @DisplayName("backward-compat graph still populated from builder")
    void testBackwardCompatGraphPopulated() {
        PythonPlugin plugin = new PythonPlugin();
        ParseContext context = new ParseContext(
            Path.of("/project/src"),
            Set.of("py")
        );

        ParseResult result = plugin.parseFromContent(
            "/project/src/app.py",
            "import requests",
            context
        );

        DependencyGraph graph = result.getGraph();
        assertNotNull(graph, "Graph should not be null");

        // Graph should contain the source node
        assertFalse(graph.getNodeIds().isEmpty(),
            "Graph should contain at least the source module node");

        // Find the source node
        boolean foundSource = graph.getNodeIds().stream()
            .anyMatch(id -> id.startsWith("py:"));
        assertTrue(foundSource, "Graph should contain a py:-prefixed node");
    }
}
