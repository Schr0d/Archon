package com.archon.python;

import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.archon.core.plugin.ModuleDeclaration;
import com.archon.core.plugin.DependencyDeclaration;
import com.archon.core.plugin.NodeType;
import com.archon.core.plugin.EdgeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
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
    @DisplayName("declarations populated from plugin parse")
    void testDeclarationsPopulated() {
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

        // Module declarations should be populated
        assertFalse(result.getModuleDeclarations().isEmpty(),
            "Should have module declarations");

        // Find the source node declaration
        boolean foundSource = result.getModuleDeclarations().stream()
            .anyMatch(md -> md.id().startsWith("py:"));
        assertTrue(foundSource, "Declarations should contain a py:-prefixed module");
    }

    // === src/ layout tests ===

    @Test
    @DisplayName("parseFromContent handles src/ layout: node IDs match import targets")
    void testSrcLayoutNodeIdsMatchImportTargets() throws Exception {
        PythonPlugin plugin = new PythonPlugin();

        // Create temp project with src/ layout
        Path tempRoot = Files.createTempDirectory("archon-python-test");
        try {
            Path pkgDir = tempRoot.resolve("src").resolve("mypkg");
            Files.createDirectories(pkgDir);
            Files.writeString(pkgDir.resolve("__init__.py"), "");
            Files.writeString(pkgDir.resolve("utils.py"), "from mypkg.helper import foo");

            Path helperFile = tempRoot.resolve("src").resolve("mypkg").resolve("helper.py");
            Files.writeString(helperFile, "def foo(): pass");

            ParseContext context = new ParseContext(tempRoot, Set.of("py"));

            // Parse utils.py
            Path utilsFile = pkgDir.resolve("utils.py");
            ParseResult result = plugin.parseFromContent(
                utilsFile.toAbsolutePath().toString(),
                Files.readString(utilsFile),
                context
            );

            // Module ID should NOT have src/ prefix
            String moduleId = result.getSourceModules().iterator().next();
            assertTrue(moduleId.equals("py:mypkg/utils"),
                "Module ID should be py:mypkg/utils, not py:src/mypkg/utils. Got: " + moduleId);

            // Dependency target should also be py:mypkg/helper
            List<DependencyDeclaration> deps = result.getDeclarations();
            assertFalse(deps.isEmpty(), "Should have a dependency declaration");
            assertEquals("py:mypkg/helper", deps.get(0).targetId(),
                "Target ID should be py:mypkg/helper");
        } finally {
            // Cleanup
            Files.walk(tempRoot)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (Exception e) {} });
        }
    }

    @Test
    @DisplayName("parseFromContent flat layout: no src/ prefix in node IDs")
    void testFlatLayoutNodeIds() throws Exception {
        PythonPlugin plugin = new PythonPlugin();

        Path tempRoot = Files.createTempDirectory("archon-python-test");
        try {
            Path pkgDir = tempRoot.resolve("mypkg");
            Files.createDirectories(pkgDir);
            Files.writeString(pkgDir.resolve("__init__.py"), "");
            Files.writeString(pkgDir.resolve("utils.py"), "from mypkg.helper import foo");

            ParseContext context = new ParseContext(tempRoot, Set.of("py"));

            Path utilsFile = pkgDir.resolve("utils.py");
            ParseResult result = plugin.parseFromContent(
                utilsFile.toAbsolutePath().toString(),
                Files.readString(utilsFile),
                context
            );

            String moduleId = result.getSourceModules().iterator().next();
            assertEquals("py:mypkg/utils", moduleId,
                "Module ID should be py:mypkg/utils for flat layout");

            List<DependencyDeclaration> deps = result.getDeclarations();
            assertFalse(deps.isEmpty(), "Should have a dependency declaration");
            assertEquals("py:mypkg/helper", deps.get(0).targetId(),
                "Target ID should be py:mypkg/helper");
        } finally {
            Files.walk(tempRoot)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (Exception e) {} });
        }
    }

    @Test
    @DisplayName("parseFromContent standalone file: no package, uses project root")
    void testStandaloneFileNoPackage() throws Exception {
        PythonPlugin plugin = new PythonPlugin();

        Path tempRoot = Files.createTempDirectory("archon-python-test");
        try {
            Path mainFile = tempRoot.resolve("main.py");
            Files.writeString(mainFile, "import requests");

            ParseContext context = new ParseContext(tempRoot, Set.of("py"));

            ParseResult result = plugin.parseFromContent(
                mainFile.toAbsolutePath().toString(),
                Files.readString(mainFile),
                context
            );

            String moduleId = result.getSourceModules().iterator().next();
            assertEquals("py:main", moduleId,
                "Standalone file should have module ID py:main");
        } finally {
            Files.walk(tempRoot)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (Exception e) {} });
        }
    }

    @Test
    @DisplayName("parseFromContent src/ layout with relative from-imports resolves correctly")
    void testSrcLayoutRelativeFromImports() throws Exception {
        PythonPlugin plugin = new PythonPlugin();

        Path tempRoot = Files.createTempDirectory("archon-python-test");
        try {
            Path pkgDir = tempRoot.resolve("src").resolve("mypkg");
            Path subDir = pkgDir.resolve("sub");
            Files.createDirectories(subDir);
            Files.writeString(pkgDir.resolve("__init__.py"), "");
            Files.writeString(subDir.resolve("__init__.py"), "");
            // Use dotted relative imports that the extractor can parse (from ..foo import bar)
            Files.writeString(subDir.resolve("utils.py"), "from ..helper import something");

            Path helperFile = pkgDir.resolve("helper.py");
            Files.writeString(helperFile, "def something(): pass");

            ParseContext context = new ParseContext(tempRoot, Set.of("py"));

            Path utilsFile = subDir.resolve("utils.py");
            ParseResult result = plugin.parseFromContent(
                utilsFile.toAbsolutePath().toString(),
                Files.readString(utilsFile),
                context
            );

            // Module ID should be py:mypkg/sub/utils (no src/ prefix)
            String moduleId = result.getSourceModules().iterator().next();
            assertEquals("py:mypkg/sub/utils", moduleId,
                "Module ID should not include src/ prefix. Got: " + moduleId);

            // Relative import should resolve to correct target
            List<DependencyDeclaration> deps = result.getDeclarations();
            assertFalse(deps.isEmpty(),
                "Should have dependency declaration for relative import");

            String targetId = deps.get(0).targetId();
            assertEquals("py:mypkg/helper", targetId,
                "Should resolve ..helper to py:mypkg/helper. Got: " + targetId);
        } finally {
            Files.walk(tempRoot)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.delete(p); } catch (Exception e) {} });
        }
    }
}
