package com.archon.cli;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AnalyzeCommand command-line options.
 */
class AnalyzeCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void testQuietFlagCanBeSetToTrue() {
        // Given: AnalyzeCommand with quiet=true
        AnalyzeCommand command = new AnalyzeCommand();
        command.quiet = true;

        // Then: quiet should be true
        assertTrue(command.quiet, "quiet flag should be settable to true");
    }

    @Test
    void testQuietFlagDefaultsToFalse() {
        // Given: default AnalyzeCommand
        AnalyzeCommand command = new AnalyzeCommand();

        // Then: quiet should default to false
        assertFalse(command.quiet, "quiet flag should default to false");
    }

    @Test
    void testProjectPathCanBeSet() {
        // Given: AnalyzeCommand with project path
        String expectedPath = "/some/test/path";
        AnalyzeCommand command = new AnalyzeCommand();
        command.projectPath = expectedPath;

        // Then: projectPath should be set
        assertEquals(expectedPath, command.projectPath);
    }

    @Test
    void testJsonFlagCanBeSetToTrue() {
        // Given: AnalyzeCommand with json=true
        AnalyzeCommand command = new AnalyzeCommand();
        command.json = true;

        // Then: json should be true
        assertTrue(command.json, "json flag should be settable to true");
    }

    @Test
    void testJsonFlagDefaultsToFalse() {
        // Given: default AnalyzeCommand
        AnalyzeCommand command = new AnalyzeCommand();

        // Then: json should default to false
        assertFalse(command.json, "json flag should default to false");
    }

    @Test
    void testVerboseFlagCanBeSetToTrue() {
        // Given: AnalyzeCommand with verbose=true
        AnalyzeCommand command = new AnalyzeCommand();
        command.verbose = true;

        // Then: verbose should be true
        assertTrue(command.verbose, "verbose flag should be settable to true");
    }

    @Test
    void testVerboseFlagDefaultsToFalse() {
        // Given: default AnalyzeCommand
        AnalyzeCommand command = new AnalyzeCommand();

        // Then: verbose should default to false
        assertFalse(command.verbose, "verbose flag should default to false");
    }

    @Test
    void testWithMetadataFlagCanBeSetToTrue() {
        // Given: AnalyzeCommand with withMetadata=true
        AnalyzeCommand command = new AnalyzeCommand();
        command.withMetadata = true;

        // Then: withMetadata should be true
        assertTrue(command.withMetadata, "withMetadata flag should be settable to true");
    }

    @Test
    void testWithMetadataFlagDefaultsToFalse() {
        // Given: default AnalyzeCommand
        AnalyzeCommand command = new AnalyzeCommand();

        // Then: withMetadata should default to false
        assertFalse(command.withMetadata, "withMetadata flag should default to false");
    }

    @Test
    void testWithFullAnalysisFlagCanBeSetToTrue() {
        // Given: AnalyzeCommand with withFullAnalysis=true
        AnalyzeCommand command = new AnalyzeCommand();
        command.withFullAnalysis = true;

        // Then: withFullAnalysis should be true
        assertTrue(command.withFullAnalysis, "withFullAnalysis flag should be settable to true");
    }

    @Test
    void testWithFullAnalysisFlagDefaultsToFalse() {
        // Given: default AnalyzeCommand
        AnalyzeCommand command = new AnalyzeCommand();

        // Then: withFullAnalysis should default to false
        assertFalse(command.withFullAnalysis, "withFullAnalysis flag should default to false");
    }

    @Test
    void testFormatFieldDefaultsToNull() {
        // Given: default AnalyzeCommand
        AnalyzeCommand command = new AnalyzeCommand();

        // Then: format should default to null (text mode)
        assertNull(command.format, "format field should default to null");
    }

    @Test
    void testFormatFieldCanBeSetToAgent() {
        // Given: AnalyzeCommand with format=agent
        AnalyzeCommand command = new AnalyzeCommand();
        command.format = "agent";

        // Then: format should be "agent"
        assertEquals("agent", command.format, "format field should be settable to 'agent'");
    }

    // --- DX improvement tests ---

    @Test
    void testLanguagesFieldDefaultsToNull() {
        // Given: default AnalyzeCommand
        AnalyzeCommand command = new AnalyzeCommand();

        // Then: languages should default to null
        assertNull(command.languages, "languages field should default to null");
    }

    @Test
    void testLanguagesFieldCanBeSet() {
        // Given: AnalyzeCommand with languages set
        AnalyzeCommand command = new AnalyzeCommand();
        command.languages = "java,python";

        // Then: languages should be set
        assertEquals("java,python", command.languages, "languages field should be settable");
    }

    @Test
    void testExtensionLabelJava() {
        assertEquals("Java", AnalyzeCommand.extensionLabel("java"));
    }

    @Test
    void testExtensionLabelJs() {
        assertEquals("JavaScript", AnalyzeCommand.extensionLabel("js"));
    }

    @Test
    void testExtensionLabelTs() {
        assertEquals("TypeScript", AnalyzeCommand.extensionLabel("ts"));
    }

    @Test
    void testExtensionLabelPy() {
        assertEquals("Python", AnalyzeCommand.extensionLabel("py"));
    }

    @Test
    void testExtensionLabelVue() {
        assertEquals("Vue", AnalyzeCommand.extensionLabel("vue"));
    }

    @Test
    void testExtensionLabelUnknownReturnsRaw() {
        assertEquals("xyz", AnalyzeCommand.extensionLabel("xyz"));
    }

    // --- resolveTarget tests ---

    @Test
    void testResolveTarget_exactMatch() {
        AnalyzeCommand command = new AnalyzeCommand();
        DependencyGraph graph = new DependencyGraph.MutableBuilder()
            .addNode(Node.builder().id("java:com.example.Foo").type(NodeType.CLASS).build())
            .addNode(Node.builder().id("java:com.example.Bar").type(NodeType.CLASS).build())
            .build();

        assertEquals("java:com.example.Foo", command.resolveTarget(graph, "java:com.example.Foo"));
    }

    @Test
    void testResolveTarget_suffixMatch() {
        AnalyzeCommand command = new AnalyzeCommand();
        DependencyGraph graph = new DependencyGraph.MutableBuilder()
            .addNode(Node.builder().id("java:com.example.FooService").type(NodeType.CLASS).build())
            .addNode(Node.builder().id("java:com.example.FooController").type(NodeType.CLASS).build())
            .build();

        assertEquals("java:com.example.FooService", command.resolveTarget(graph, "FooService"));
    }

    @Test
    void testResolveTarget_notFound() {
        AnalyzeCommand command = new AnalyzeCommand();
        DependencyGraph graph = new DependencyGraph.MutableBuilder()
            .addNode(Node.builder().id("java:com.example.Foo").type(NodeType.CLASS).build())
            .build();

        assertNull(command.resolveTarget(graph, "NonExistent"));
    }

    @Test
    void testStripNamespacePrefix_javaPrefix() {
        AnalyzeCommand command = new AnalyzeCommand();
        assertEquals("com.example.Foo", command.stripNamespacePrefix("java:com.example.Foo"));
    }

    @Test
    void testStripNamespacePrefix_noPrefix() {
        AnalyzeCommand command = new AnalyzeCommand();
        assertEquals("com.example.Foo", command.stripNamespacePrefix("com.example.Foo"));
    }
}
