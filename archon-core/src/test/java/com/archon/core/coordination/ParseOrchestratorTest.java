package com.archon.core.coordination;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.plugin.*;

import com.archon.core.coordination.PostProcessResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ParseOrchestratorTest {

    @Test
    void testTwoPhaseConstructionPreventsEdgeLoss(@TempDir Path tempDir) throws IOException {
        // Create test plugins that add prefixed nodes and edges
        TestPlugin pluginA = new TestPlugin("java", Set.of("com.java.Bar"));
        TestPlugin pluginB = new TestPlugin("js", Set.of("com.js.Foo"));

        // Simulate cross-plugin edge: java:com.java.Bar -> js:com.js.Foo
        pluginA.addCrossPluginEdge("js:com.js.Foo");

        // Create temp files
        Path javaFile = tempDir.resolve("Bar.java");
        Path jsFile = tempDir.resolve("Foo.js");
        Files.writeString(javaFile, "// test java file");
        Files.writeString(jsFile, "// test js file");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(pluginA, pluginB));
        ParseResult result = orchestrator.parse(
            List.of(javaFile, jsFile),
            new ParseContext(tempDir, Set.of("java", "js"))
        );

        DependencyGraph graph = result.getGraph();
        // Both nodes should exist even though edges cross plugins
        assertTrue(graph.containsNode("com.java.Bar"), "Java node should exist after prefix stripping");
        assertTrue(graph.containsNode("com.js.Foo"), "JS node should exist after prefix stripping");

        // Edge should exist (two-phase construction prevents loss)
        assertEquals(1, graph.edgeCount(), "Should have exactly 1 edge");
        assertTrue(graph.getEdge("com.java.Bar", "com.js.Foo").isPresent(),
            "Cross-plugin edge should exist after prefix stripping");
    }

    @Test
    void testNamespacePrefixStripping(@TempDir Path tempDir) throws IOException {
        TestPlugin plugin = new TestPlugin("java", Set.of("com.java.Foo", "com.java.Bar"));
        // Add an edge between the two nodes
        plugin.addCrossPluginEdge("java:com.java.Bar");

        // Create temp file
        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, "// test java file");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));

        ParseResult result = orchestrator.parse(
            List.of(testFile),
            new ParseContext(tempDir, Set.of("java"))
        );

        DependencyGraph graph = result.getGraph();
        // Prefix should be stripped from nodes
        assertTrue(graph.containsNode("com.java.Foo"), "Node should exist without prefix");
        assertTrue(graph.containsNode("com.java.Bar"), "Node should exist without prefix");
        assertFalse(graph.containsNode("java:com.java.Foo"), "Node should NOT exist with prefix");
        assertFalse(graph.containsNode("java:com.java.Bar"), "Node should NOT exist with prefix");

        // Prefix should be stripped from edge references
        // Note: TestPlugin adds edges from alphabetically first module to others
        // So edge is from "com.java.Bar" to "com.java.Foo"
        assertTrue(graph.getEdge("com.java.Bar", "com.java.Foo").isPresent(),
            "Edge should exist with stripped node IDs");
    }

    @Test
    void testEmptyPluginList() {
        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of());
        ParseResult result = orchestrator.parse(
            List.of(),
            new ParseContext(Path.of("/src"), Set.of())
        );

        assertEquals(0, result.getGraph().nodeCount());
        assertEquals(0, result.getGraph().edgeCount());
    }

    @Test
    void testMultipleFilesSamePlugin(@TempDir Path tempDir) throws IOException {
        TestPlugin plugin = new TestPlugin("java", Set.of("com.foo.A", "com.bar.B"));

        // Create temp files
        Path fileA = tempDir.resolve("A.java");
        Path fileB = tempDir.resolve("B.java");
        Files.writeString(fileA, "// file A");
        Files.writeString(fileB, "// file B");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));
        ParseResult result = orchestrator.parse(
            List.of(fileA, fileB),
            new ParseContext(tempDir, Set.of("java"))
        );

        DependencyGraph graph = result.getGraph();
        assertEquals(2, graph.nodeCount());
        assertTrue(graph.containsNode("com.foo.A"));
        assertTrue(graph.containsNode("com.bar.B"));
    }

    @Test
    void testPartialResultPreservationWhenPluginCrashes(@TempDir Path tempDir) throws IOException {
        // A good plugin (java) and a crashing plugin (js)
        TestPlugin goodPlugin = new TestPlugin("java", Set.of("com.example.Good"));
        CrashingPlugin crashingPlugin = new CrashingPlugin("js");

        // Create temp files for both plugins
        Path javaFile = tempDir.resolve("Good.java");
        Path jsFile = tempDir.resolve("Bad.js");
        Files.writeString(javaFile, "// good java file");
        Files.writeString(jsFile, "// bad js file");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(goodPlugin, crashingPlugin));
        ParseResult result = orchestrator.parse(
            List.of(javaFile, jsFile),
            new ParseContext(tempDir, Set.of("java", "js"))
        );

        DependencyGraph graph = result.getGraph();
        // The good plugin's node should still be present
        assertTrue(graph.containsNode("com.example.Good"),
            "Good plugin results should be preserved even when another plugin crashes");
        // The error from the crashing plugin should be recorded
        assertFalse(result.getParseErrors().isEmpty(),
            "Error from crashed plugin should be recorded");
        assertTrue(result.getParseErrors().stream()
                .anyMatch(e -> e.contains("CrashingPlugin") && e.contains("Bad.js")),
            "Error message should mention the crashing plugin and file");
    }

    /**
     * A plugin that always throws RuntimeException when parsing.
     * Used to test partial result preservation.
     */
    static class CrashingPlugin implements LanguagePlugin {
        private final String ext;

        CrashingPlugin(String ext) {
            this.ext = ext;
        }

        @Override
        public Set<String> fileExtensions() {
            return Set.of(ext);
        }

        @Override
        public ParseResult parseFromContent(String filePath, String content, ParseContext context) {
            throw new RuntimeException("Simulated crash in CrashingPlugin");
        }
    }

    /**
     * Test plugin that returns declarations with prefixed node IDs.
     * Simulates language plugins that namespace their nodes.
     */
    static class TestPlugin implements LanguagePlugin {
        private final String prefix;
        private final Set<String> unprefixedModules;
        private final List<String> crossPluginEdges = new ArrayList<>();

        TestPlugin(String prefix, Set<String> unprefixedModules) {
            this.prefix = prefix;
            this.unprefixedModules = unprefixedModules;
        }

        void addCrossPluginEdge(String targetNodeId) {
            this.crossPluginEdges.add(targetNodeId);
        }

        @Override
        public Set<String> fileExtensions() {
            return Set.of(prefix);
        }

        @Override
        public ParseResult parseFromContent(
            String filePath,
            String content,
            ParseContext context
        ) {
            List<ModuleDeclaration> moduleDecls = new ArrayList<>();
            List<DependencyDeclaration> depDecls = new ArrayList<>();

            // Phase 1: Add all module declarations with prefix
            for (String module : unprefixedModules) {
                String prefixedId = prefix + ":" + module;
                moduleDecls.add(new ModuleDeclaration(
                    prefixedId,
                    com.archon.core.plugin.NodeType.CLASS,
                    filePath,
                    com.archon.core.plugin.Confidence.HIGH
                ));
            }

            // Phase 2: Add edges from first module to all others
            if (unprefixedModules.size() > 1) {
                List<String> sortedModules = new ArrayList<>(unprefixedModules);
                Collections.sort(sortedModules);
                String first = sortedModules.get(0);
                String prefixedSource = prefix + ":" + first;

                for (String target : unprefixedModules) {
                    if (!target.equals(first)) {
                        String prefixedTarget = prefix + ":" + target;
                        depDecls.add(new DependencyDeclaration(
                            prefixedSource, prefixedTarget,
                            com.archon.core.plugin.EdgeType.IMPORTS,
                            com.archon.core.plugin.Confidence.HIGH,
                            null, false
                        ));
                    }
                }
            }

            // Add cross-plugin edges if any
            if (!unprefixedModules.isEmpty() && !crossPluginEdges.isEmpty()) {
                List<String> sortedModules = new ArrayList<>(unprefixedModules);
                Collections.sort(sortedModules);
                String first = sortedModules.get(0);
                String prefixedSource = prefix + ":" + first;

                for (String prefixedTarget : crossPluginEdges) {
                    depDecls.add(new DependencyDeclaration(
                        prefixedSource, prefixedTarget,
                        com.archon.core.plugin.EdgeType.IMPORTS,
                        com.archon.core.plugin.Confidence.HIGH,
                        null, false
                    ));
                }
            }

            return new ParseResult(
                unprefixedModules, List.of(), List.of(),
                moduleDecls, depDecls
            );
        }
    }

    @Test
    void testPostProcessAddsEdgesToGraph(@TempDir Path tempDir) throws IOException {
        // Plugin knows about two classes but postProcess adds a SPRING_DI edge between them
        PostProcessingPlugin plugin = new PostProcessingPlugin(
            "java",
            Set.of("com.example.Controller", "com.example.ServiceImpl"),
            List.of(new DependencyDeclaration(
                "java:com.example.Controller",
                "java:com.example.ServiceImpl",
                EdgeType.SPRING_DI,
                Confidence.HIGH,
                "@Autowired", false
            ))
        );

        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, "// test");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));
        ParseResult result = orchestrator.parse(
            List.of(testFile),
            new ParseContext(tempDir, Set.of("java"))
        );

        DependencyGraph graph = result.getGraph();
        assertTrue(graph.getEdge("com.example.Controller", "com.example.ServiceImpl").isPresent(),
            "SPRING_DI edge from postProcess should be in graph");
        assertEquals(com.archon.core.graph.EdgeType.SPRING_DI,
            graph.getEdge("com.example.Controller", "com.example.ServiceImpl").get().getType());
    }

    @Test
    void testPostProcessEmptyDoesNotRebuildGraph(@TempDir Path tempDir) throws IOException {
        // Plugin with empty postProcess (default) should not cause issues
        TestPlugin plugin = new TestPlugin("java", Set.of("com.example.Foo"));

        Path testFile = tempDir.resolve("Test.java");
        Files.writeString(testFile, "// test");

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));
        ParseResult result = orchestrator.parse(
            List.of(testFile),
            new ParseContext(tempDir, Set.of("java"))
        );

        assertTrue(result.getGraph().containsNode("com.example.Foo"));
        assertEquals(0, result.getGraph().edgeCount());
    }

    /**
     * Test plugin that returns post-process declarations.
     */
    static class PostProcessingPlugin implements LanguagePlugin {
        private final String prefix;
        private final Set<String> modules;
        private final List<DependencyDeclaration> postProcessDeclarations;

        PostProcessingPlugin(String prefix, Set<String> modules,
                             List<DependencyDeclaration> postProcessDeclarations) {
            this.prefix = prefix;
            this.modules = modules;
            this.postProcessDeclarations = postProcessDeclarations;
        }

        @Override public Set<String> fileExtensions() { return Set.of(prefix); }

        @Override
        public ParseResult parseFromContent(String filePath, String content, ParseContext context) {
            List<ModuleDeclaration> moduleDecls = new ArrayList<>();
            for (String module : modules) {
                moduleDecls.add(new ModuleDeclaration(
                    prefix + ":" + module, NodeType.CLASS, filePath, Confidence.HIGH
                ));
            }
            return new ParseResult(modules, List.of(), List.of(), moduleDecls, List.of());
        }

        @Override
        public PostProcessResult postProcess(List<ModuleDeclaration> allModules, ParseContext context) {
            return new PostProcessResult(postProcessDeclarations, List.of());
        }
    }
}
