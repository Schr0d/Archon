package com.archon.js;

import com.archon.core.plugin.BlindSpot;
import com.archon.core.plugin.Confidence;
import com.archon.core.plugin.DependencyDeclaration;
import com.archon.core.plugin.EdgeType;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ModuleDeclaration;
import com.archon.core.plugin.NodeType;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JsPlugin using dependency-cruiser subprocess pattern.
 *
 * <p>Uses a testable subclass that overrides {@code runDependencyCruiser}
 * to provide mock JSON data instead of spawning a real subprocess.
 */
@DisplayName("JsPlugin Tests")
class JsPluginTest {

    /** Testable subclass that injects mock dependency-cruiser output. */
    static class TestableJsPlugin extends JsPlugin {
        private String mockJson;
        private Exception mockException;
        private boolean npxAvailable = true;
        private int runCount = 0;

        void setMockJson(String json) {
            this.mockJson = json;
            this.mockException = null;
        }

        void setMockException(Exception e) {
            this.mockException = e;
            this.mockJson = null;
        }

        void setNpxAvailable(boolean available) {
            this.npxAvailable = available;
        }

        int getRunCount() {
            return runCount;
        }

        @Override
        protected String runDependencyCruiser(Path sourceRoot) throws Exception {
            runCount++;
            if (mockException != null) {
                throw mockException;
            }
            if (mockJson != null) {
                return mockJson;
            }
            return "{\"modules\":[]}";
        }

        @Override
        protected boolean isNpxAvailable() {
            return npxAvailable;
        }
    }

    @TempDir
    Path tempDir;

    private TestableJsPlugin plugin;

    @BeforeEach
    void setUp() {
        plugin = new TestableJsPlugin();
    }

    // ---- fileExtensions ----

    @Test
    @DisplayName("fileExtensions returns all supported JS/TS extensions including mjs and cjs")
    void testFileExtensions() {
        Set<String> exts = plugin.fileExtensions();
        assertAll(
            () -> assertTrue(exts.contains("js"), "should contain js"),
            () -> assertTrue(exts.contains("ts"), "should contain ts"),
            () -> assertTrue(exts.contains("tsx"), "should contain tsx"),
            () -> assertTrue(exts.contains("vue"), "should contain vue"),
            () -> assertTrue(exts.contains("jsx"), "should contain jsx"),
            () -> assertTrue(exts.contains("mjs"), "should contain mjs"),
            () -> assertTrue(exts.contains("cjs"), "should contain cjs"),
            () -> assertEquals(7, exts.size(), "should have exactly 7 extensions")
        );
    }

    // ---- JsPlugin implements LanguagePlugin ----

    @Test
    @DisplayName("JsPlugin implements LanguagePlugin interface")
    void testImplementsLanguagePlugin() {
        assertTrue(plugin instanceof LanguagePlugin);
    }

    // ---- Cache mechanism: subprocess runs once ----

    @Test
    @DisplayName("Subprocess runs only once on first parseFromContent call")
    void testSubprocessRunsOnce() {
        String json = buildJson(
            buildModule("src/App.js", buildDep("src/utils.js", false))
        );
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        plugin.parseFromContent(sourceRoot.resolve("src/App.js").toString(), "irrelevant", ctx);
        plugin.parseFromContent(sourceRoot.resolve("src/utils.js").toString(), "irrelevant", ctx);
        plugin.parseFromContent(sourceRoot.resolve("src/App.js").toString(), "irrelevant", ctx);

        assertEquals(1, plugin.getRunCount(), "Subprocess should only run once");
    }

    @Test
    @DisplayName("reset clears cache, causing next call to re-run subprocess")
    void testResetClearsCache() {
        String json = buildJson(
            buildModule("src/App.js", buildDep("src/utils.js", false))
        );
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        plugin.parseFromContent(sourceRoot.resolve("src/App.js").toString(), "", ctx);
        assertEquals(1, plugin.getRunCount());

        plugin.reset();

        plugin.parseFromContent(sourceRoot.resolve("src/App.js").toString(), "", ctx);
        assertEquals(2, plugin.getRunCount(), "After reset, subprocess should run again");
    }

    // ---- JSON parsing: basic happy path ----

    @Test
    @DisplayName("Parse basic module with one dependency")
    void testBasicDependency() throws Exception {
        String json = buildJson(
            buildModule("src/App.js",
                buildDep("src/utils.js", false)
            )
        );
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "content", ctx
        );

        assertFalse(result.getSourceModules().isEmpty());
        assertEquals("js:src/App.js", result.getSourceModules().iterator().next());

        assertEquals(1, result.getModuleDeclarations().size());
        ModuleDeclaration modDecl = result.getModuleDeclarations().get(0);
        assertEquals("js:src/App.js", modDecl.id());
        assertEquals(NodeType.MODULE, modDecl.type());
        assertEquals(Confidence.HIGH, modDecl.confidence());

        assertEquals(1, result.getDeclarations().size());
        DependencyDeclaration depDecl = result.getDeclarations().get(0);
        assertEquals("js:src/App.js", depDecl.sourceId());
        assertEquals("js:src/utils.js", depDecl.targetId());
        assertEquals(EdgeType.IMPORTS, depDecl.edgeType());
        assertEquals(Confidence.HIGH, depDecl.confidence());
        assertFalse(depDecl.dynamic());

        assertTrue(result.getParseErrors().isEmpty());
        assertTrue(result.getBlindSpots().isEmpty());
    }

    // ---- Node_modules filtering ----

    @Test
    @DisplayName("Dependencies into node_modules are filtered out")
    void testNodeModulesFiltered() {
        String json = buildJson(
            buildModule("src/App.js",
                buildDep("src/utils.js", false),
                buildDep("node_modules/vue/dist/vue.js", false)
            )
        );
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );

        // Should only have the local dependency, not node_modules
        assertEquals(1, result.getDeclarations().size());
        assertEquals("js:src/utils.js", result.getDeclarations().get(0).targetId());
    }

    @Test
    @DisplayName("Node_modules in nested path is filtered")
    void testNestedNodeModulesFiltered() {
        String json = buildJson(
            buildModule("src/App.js",
                buildDep("packages/foo/node_modules/bar/index.js", false)
            )
        );
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );

        assertEquals(0, result.getDeclarations().size(), "Nested node_modules should be filtered");
    }

    // ---- Builtin filtering ----

    @Test
    @DisplayName("Node.js builtin modules with coreModule flag are filtered out")
    void testBuiltinsFilteredViaCoreModuleFlag() {
        String json = """
        {
          "modules": [
            {
              "source": "src/App.js",
              "dependencies": [
                {"resolved": "src/utils.js", "module": "es6", "coreModule": false, "couldNotResolve": false},
                {"resolved": "fs", "module": "fs", "coreModule": true, "couldNotResolve": false},
                {"resolved": "path", "module": "path", "coreModule": true, "couldNotResolve": false},
                {"resolved": "http", "module": "http", "coreModule": true, "couldNotResolve": false}
              ]
            }
          ]
        }
        """;
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );

        // Only src/utils.js should survive
        assertEquals(1, result.getDeclarations().size());
        assertEquals("js:src/utils.js", result.getDeclarations().get(0).targetId());
    }

    @Test
    @DisplayName("Core modules as top-level entries in modules array are skipped")
    void testCoreModulesAsTopLevelEntriesSkipped() {
        String json = """
        {
          "modules": [
            {
              "source": "src/App.js",
              "dependencies": [
                {"resolved": "src/utils.js", "module": "es6", "coreModule": false, "couldNotResolve": false}
              ]
            },
            {
              "source": "fs",
              "coreModule": true,
              "followable": false,
              "dependencies": []
            }
          ]
        }
        """;
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        // fs module should not appear as a source module
        ParseResult fsResult = plugin.parseFromContent(
            sourceRoot.resolve("fs").toString(), "", ctx
        );
        assertTrue(fsResult.getSourceModules().isEmpty(),
            "Core module should not be cached as a project module");
    }

    @Test
    @DisplayName("Node.js builtin modules are filtered out by path fallback")
    void testBuiltinsFilteredByPath() {
        String json = buildJson(
            buildModule("src/App.js",
                buildDep("src/utils.js", false),
                buildDep("fs", false),
                buildDep("path", false),
                buildDep("http", false),
                buildDep("os", false)
            )
        );
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );

        // Only src/utils.js should survive
        assertEquals(1, result.getDeclarations().size());
        assertEquals("js:src/utils.js", result.getDeclarations().get(0).targetId());
    }

    // ---- Blind spots: unresolved modules ----

    @Test
    @DisplayName("Unresolved modules are reported as blind spots")
    void testUnresolvedBlindSpots() {
        String json = """
        {
          "modules": [
            {
              "source": "src/App.js",
              "dependencies": [
                {"resolved": "src/utils.js", "module": "es6", "circular": false},
                {"module": "es6", "couldNotResolve": true, "resolved": null}
              ]
            }
          ]
        }
        """;
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );

        assertEquals(1, result.getDeclarations().size(), "Should have one resolved dep");
        assertEquals(1, result.getBlindSpots().size(), "Should have one blind spot");

        BlindSpot bs = result.getBlindSpots().get(0);
        assertEquals("UnresolvedModule", bs.getType());
        assertTrue(bs.getDescription().contains("es6"), "Description should mention module: " + bs.getDescription());
    }

    // ---- File not in cache returns empty ----

    @Test
    @DisplayName("File not present in dependency-cruiser output returns empty ParseResult")
    void testFileNotInCache() {
        String json = buildJson(
            buildModule("src/App.js", buildDep("src/utils.js", false))
        );
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/Unknown.js").toString(), "", ctx
        );

        assertTrue(result.getSourceModules().isEmpty());
        assertTrue(result.getModuleDeclarations().isEmpty());
        assertTrue(result.getDeclarations().isEmpty());
        assertTrue(result.getBlindSpots().isEmpty());
        assertTrue(result.getParseErrors().isEmpty());
    }

    // ---- Empty project: no modules ----

    @Test
    @DisplayName("Empty project with no modules returns empty results")
    void testEmptyProject() {
        plugin.setMockJson("{\"modules\":[]}");

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );

        assertTrue(result.getSourceModules().isEmpty());
        assertTrue(result.getDeclarations().isEmpty());
        assertTrue(result.getParseErrors().isEmpty());
    }

    // ---- Vue files ----

    @Test
    @DisplayName("Vue files are processed correctly with .vue extension")
    void testVueFile() {
        String json = buildJson(
            buildModule("src/views/doc/DocList.vue",
                buildDep("src/api/request.js", false),
                buildDep("src/components/DocCard.vue", false)
            )
        );
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("vue"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/views/doc/DocList.vue").toString(), "<template></template>", ctx
        );

        assertEquals(1, result.getSourceModules().size());
        assertEquals("js:src/views/doc/DocList.vue", result.getSourceModules().iterator().next());
        assertEquals(2, result.getDeclarations().size());
    }

    // ---- Multiple dependencies ----

    @Test
    @DisplayName("Module with multiple dependencies produces correct declarations")
    void testMultipleDependencies() {
        String json = buildJson(
            buildModule("src/App.js",
                buildDep("src/utils.js", false),
                buildDep("src/components/Header.vue", false),
                buildDep("src/api/request.js", false)
            )
        );
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );

        assertEquals(3, result.getDeclarations().size());
        Set<String> targets = result.getDeclarations().stream()
            .map(DependencyDeclaration::targetId)
            .collect(java.util.stream.Collectors.toSet());
        assertTrue(targets.contains("js:src/utils.js"));
        assertTrue(targets.contains("js:src/components/Header.vue"));
        assertTrue(targets.contains("js:src/api/request.js"));
    }

    // ---- Error handling: Node.js not found ----

    @Test
    @DisplayName("Node.js not found produces helpful error message")
    void testNodeNotFound() {
        plugin.setNpxAvailable(false);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );

        assertEquals(1, result.getParseErrors().size());
        String error = result.getParseErrors().get(0);
        assertTrue(error.contains("Node.js not found"), "Error should mention Node.js: " + error);
        assertTrue(error.contains("https://nodejs.org"), "Error should include install URL: " + error);
    }

    // ---- Error handling: non-zero exit code ----

    @Test
    @DisplayName("Subprocess failure produces error with exit code")
    void testSubprocessFailure() {
        plugin.setMockException(new JsPlugin.SubprocessException(
            "dependency-cruiser exited with code 1: some error text"
        ));

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );

        assertEquals(1, result.getParseErrors().size());
        assertTrue(result.getParseErrors().get(0).contains("exited with code 1"));
    }

    // ---- Error handling: bad JSON ----

    @Test
    @DisplayName("Malformed JSON produces parse error")
    void testBadJson() {
        plugin.setMockException(new RuntimeException(
            "JSON parse error: Unexpected token at position 0"
        ));

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );

        assertFalse(result.getParseErrors().isEmpty(), "Should report parse error for bad JSON");
    }

    // ---- Error caching: error persists across calls ----

    @Test
    @DisplayName("Subprocess error is cached and returned for all subsequent calls")
    void testErrorCaching() {
        plugin.setMockException(new JsPlugin.SubprocessException("some error"));

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        // First call triggers the subprocess
        ParseResult r1 = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );
        // Second call should use cached error, not re-run
        ParseResult r2 = plugin.parseFromContent(
            sourceRoot.resolve("src/utils.js").toString(), "", ctx
        );

        assertEquals(1, plugin.getRunCount(), "Should only run once (error cached)");
        assertFalse(r1.getParseErrors().isEmpty());
        assertFalse(r2.getParseErrors().isEmpty());
    }

    // ---- Error cleared by reset ----

    @Test
    @DisplayName("reset clears error cache allowing retry")
    void testResetClearsError() {
        plugin.setMockException(new JsPlugin.SubprocessException("some error"));

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        plugin.parseFromContent(sourceRoot.resolve("src/App.js").toString(), "", ctx);
        assertEquals(1, plugin.getRunCount());

        // Reset and provide good data
        plugin.reset();
        plugin.setMockJson(buildJson(
            buildModule("src/App.js", buildDep("src/utils.js", false))
        ));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );
        assertEquals(2, plugin.getRunCount(), "Should run again after reset");
        assertTrue(result.getParseErrors().isEmpty());
        assertEquals(1, result.getDeclarations().size());
    }

    // ---- Windows path normalization ----

    @Test
    @DisplayName("Windows backslash paths are normalized to forward slashes")
    void testWindowsPathNormalization() {
        String json = """
        {
          "modules": [
            {
              "source": "src\\\\components\\\\Header.vue",
              "dependencies": [
                {"resolved": "src\\\\utils\\\\helpers.js", "module": "es6", "circular": false}
              ]
            }
          ]
        }
        """;
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("vue"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/components/Header.vue").toString(), "", ctx
        );

        // Backslashes in JSON source/resolve should be normalized to forward slashes
        assertFalse(result.getSourceModules().isEmpty(),
            "Should find module after backslash normalization");
        assertEquals("js:src/components/Header.vue", result.getSourceModules().iterator().next());
        assertEquals(1, result.getDeclarations().size(), "Should have one dependency after normalization");
        assertEquals("js:src/utils/helpers.js", result.getDeclarations().get(0).targetId(),
            "Dependency target should use forward slashes");
    }

    // ---- Multiple modules in one project ----

    @Test
    @DisplayName("Multiple modules parsed from single subprocess run")
    void testMultipleModulesFromSingleRun() {
        String json = buildJson(
            buildModule("src/App.js",
                buildDep("src/utils.js", false),
                buildDep("src/api/request.js", false)
            ),
            buildModule("src/utils.js",
                buildDep("src/config.js", false)
            ),
            buildModule("src/api/request.js",
                buildDep("src/config.js", false),
                buildDep("node_modules/axios/index.js", false)
            ),
            buildModule("src/config.js")
        );
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        // Parse each file - should all come from the single cached run
        ParseResult appResult = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );
        ParseResult utilsResult = plugin.parseFromContent(
            sourceRoot.resolve("src/utils.js").toString(), "", ctx
        );
        ParseResult requestResult = plugin.parseFromContent(
            sourceRoot.resolve("src/api/request.js").toString(), "", ctx
        );
        ParseResult configResult = plugin.parseFromContent(
            sourceRoot.resolve("src/config.js").toString(), "", ctx
        );

        assertEquals(1, plugin.getRunCount(), "Should only run subprocess once");

        // App has 2 deps
        assertEquals(2, appResult.getDeclarations().size());
        // Utils has 1 dep
        assertEquals(1, utilsResult.getDeclarations().size());
        // Request has 1 dep (node_modules/axios filtered out)
        assertEquals(1, requestResult.getDeclarations().size());
        // Config has 0 deps
        assertEquals(0, configResult.getDeclarations().size());
    }

    // ---- Circular dependency flag is accepted ----

    @Test
    @DisplayName("Circular dependencies are reported normally (not filtered)")
    void testCircularDependencies() {
        String json = """
        {
          "modules": [
            {
              "source": "src/A.js",
              "dependencies": [
                {"resolved": "src/B.js", "module": "es6", "circular": true}
              ]
            }
          ]
        }
        """;
        plugin.setMockJson(json);

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/A.js").toString(), "", ctx
        );

        assertEquals(1, result.getDeclarations().size(), "Circular dep should still be reported");
        assertEquals("js:src/B.js", result.getDeclarations().get(0).targetId());
    }

    // ---- JSON parsing directly via parseDependencyCruiserJson ----

    @Test
    @DisplayName("parseDependencyCruiserJson handles empty JSON object")
    void testParseEmptyJsonObject() {
        JsPlugin p = new JsPlugin();
        var result = p.parseDependencyCruiserJson("{}", Path.of("/src"));
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseDependencyCruiserJson handles null modules array")
    void testParseNullModules() {
        JsPlugin p = new JsPlugin();
        var result = p.parseDependencyCruiserJson("{\"modules\":null}", Path.of("/src"));
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("parseDependencyCruiserJson handles module with no dependencies")
    void testParseModuleWithNoDependencies() {
        JsPlugin p = new JsPlugin();
        var result = p.parseDependencyCruiserJson(
            "{\"modules\":[{\"source\":\"src/App.js\"}]}",
            Path.of("/src")
        );
        assertTrue(result.containsKey("src/App.js"));
        assertTrue(result.get("src/App.js").resolvedDependencies.isEmpty());
        assertTrue(result.get("src/App.js").unresolvedDependencies.isEmpty());
    }

    // ---- toRelativePath ----

    @Test
    @DisplayName("toRelativePath converts absolute path to relative")
    void testToRelativePath() {
        JsPlugin p = new JsPlugin();
        Path root = Path.of("/project/src");
        String result = p.toRelativePath("/project/src/components/Header.vue", root);
        assertEquals("components/Header.vue", result);
    }

    @Test
    @DisplayName("toRelativePath handles path not under source root")
    void testToRelativePathNotUnderRoot() {
        JsPlugin p = new JsPlugin();
        Path root = Path.of("/project/src");
        String result = p.toRelativePath("/other/path/File.js", root);
        assertEquals("/other/path/File.js", result);
    }

    // ---- Helper methods for building test JSON ----

    // ---- Diff content-mode (regex fallback when cache is populated) ----

    @Test
    @DisplayName("Provided content is parsed with regex when cache is already populated")
    void testDiffContentModeUsesRegexFallback() throws Exception {
        // First: populate cache via dependency-cruiser (analyze mode)
        plugin.setMockJson(buildJson(
            buildModule("src/App.js", buildDep("src/utils.js", false))
        ));

        Path sourceRoot = tempDir;
        Path srcDir = sourceRoot.resolve("src");
        Files.createDirectories(srcDir);
        // Create the actual files so resolveRelativeImport can find them
        Files.writeString(srcDir.resolve("utils.js"), "// utils");
        Files.writeString(srcDir.resolve("config.js"), "// config");

        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        // First call populates cache
        ParseResult analyzeResult = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );
        assertEquals(1, analyzeResult.getDeclarations().size());

        // Second call WITH content (diff base graph): should parse content via regex
        String baseContent = "import { foo } from './utils'\nimport { bar } from './config'\n";
        ParseResult diffResult = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), baseContent, ctx
        );

        // Should have found 2 import declarations from the base content
        assertEquals(2, diffResult.getDeclarations().size(),
            "Should parse 2 imports from provided base content");
        assertTrue(diffResult.getDeclarations().stream()
            .anyMatch(d -> d.targetId().equals("js:src/utils.js")),
            "Should resolve ./utils to js:src/utils.js");
        assertTrue(diffResult.getDeclarations().stream()
            .anyMatch(d -> d.targetId().equals("js:src/config.js")),
            "Should resolve ./config to js:src/config.js");
    }

    @Test
    @DisplayName("Empty content still uses cache when populated")
    void testEmptyContentUsesCache() throws Exception {
        plugin.setMockJson(buildJson(
            buildModule("src/App.js", buildDep("src/utils.js", false))
        ));

        Path sourceRoot = tempDir;
        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));

        // Populate cache
        plugin.parseFromContent(sourceRoot.resolve("src/App.js").toString(), "", ctx);

        // Call with empty content should still use cache
        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), "", ctx
        );
        assertEquals(1, result.getDeclarations().size());
        assertEquals("js:src/App.js", result.getModuleDeclarations().get(0).id());
    }

    @Test
    @DisplayName("Content-mode skips non-relative imports")
    void testContentModeSkipsNonRelativeImports() throws Exception {
        plugin.setMockJson(buildJson(
            buildModule("src/App.js", buildDep("src/utils.js", false))
        ));

        Path sourceRoot = tempDir;
        Path srcDir = sourceRoot.resolve("src");
        Files.createDirectories(srcDir);
        Files.writeString(srcDir.resolve("utils.js"), "// utils");

        ParseContext ctx = new ParseContext(sourceRoot, Set.of("js"));
        plugin.parseFromContent(sourceRoot.resolve("src/App.js").toString(), "", ctx);

        String baseContent = "import React from 'react'\nimport { foo } from './utils'\n";
        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/App.js").toString(), baseContent, ctx
        );

        // Only the relative import should produce a dependency declaration
        assertEquals(1, result.getDeclarations().size());
        assertEquals("js:src/utils.js", result.getDeclarations().get(0).targetId());

        // react should be a blind spot (non-relative, not a builtin)
        assertEquals(1, result.getBlindSpots().size());
        assertTrue(result.getBlindSpots().get(0).getDescription().contains("react"));
    }

    @Test
    @DisplayName("Content-mode extracts script section from Vue files")
    void testContentModeVueScriptExtraction() throws Exception {
        plugin.setMockJson(buildJson(
            buildModule("src/components/Header.vue", buildDep("src/utils/api.ts", false))
        ));

        Path sourceRoot = tempDir;
        Path compDir = sourceRoot.resolve("src/components");
        Path utilsDir = sourceRoot.resolve("src/utils");
        Files.createDirectories(compDir);
        Files.createDirectories(utilsDir);
        Files.writeString(utilsDir.resolve("api.ts"), "// api");

        ParseContext ctx = new ParseContext(sourceRoot, Set.of("vue", "ts"));
        plugin.parseFromContent(sourceRoot.resolve("src/components/Header.vue").toString(), "", ctx);

        String vueContent = "<template><div>{{ msg }}</div></template>\n" +
            "<script setup>\n" +
            "import { fetchApi } from '../utils/api'\n" +
            "</script>\n";
        ParseResult result = plugin.parseFromContent(
            sourceRoot.resolve("src/components/Header.vue").toString(), vueContent, ctx
        );

        // Should extract the import from <script setup>
        assertEquals(1, result.getDeclarations().size());
        assertEquals("js:src/utils/api.ts", result.getDeclarations().get(0).targetId());
    }

    private static String buildJson(String... modules) {
        StringBuilder sb = new StringBuilder("{\"modules\":[");
        for (int i = 0; i < modules.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(modules[i]);
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String buildModule(String source, String... deps) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"source\":\"").append(source).append("\"");
        if (deps.length > 0) {
            sb.append(",\"dependencies\":[");
            for (int i = 0; i < deps.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(deps[i]);
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String buildDep(String resolved, boolean circular) {
        return "{\"resolved\":\"" + resolved + "\",\"module\":\"es6\",\"circular\":" + circular + "}";
    }
}
