package com.archon.cli;

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
}
