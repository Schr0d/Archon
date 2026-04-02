package com.archon.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArchonConfigTest {

    @Test
    void defaults_returnsDefaultValues() {
        ArchonConfig config = ArchonConfig.defaults();

        assertTrue(config.isNoCycle());
        assertEquals(2, config.getMaxCrossDomain());
        assertEquals(3, config.getMaxCallDepth());
        assertTrue(config.getForbidCoreEntityLeakage().isEmpty());
        assertTrue(config.getDomains().isEmpty());
        assertTrue(config.getCriticalPaths().isEmpty());
        assertTrue(config.getIgnore().isEmpty());
    }

    @Test
    void loadOrDefault_missingFile_returnsDefaults(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("nonexistent.yml");
        ArchonConfig config = ArchonConfig.loadOrDefault(missing);

        assertTrue(config.isNoCycle());
        assertEquals(2, config.getMaxCrossDomain());
    }

    @Test
    void loadOrDefault_validYaml_mergesWithDefaults(@TempDir Path tempDir) throws Exception {
        String yaml = """
            version: 1
            rules:
              no_cycle: false
              max_cross_domain: 5
            domains:
              auth: ["com.example.auth"]
            critical_paths:
              - auth
            """;
        Path file = tempDir.resolve(".archon.yml");
        Files.writeString(file, yaml);

        ArchonConfig config = ArchonConfig.loadOrDefault(file);

        assertFalse(config.isNoCycle());
        assertEquals(5, config.getMaxCrossDomain());
        assertEquals(3, config.getMaxCallDepth()); // default preserved
        assertEquals(1, config.getDomains().size());
        assertTrue(config.getDomains().containsKey("auth"));
        assertEquals(1, config.getCriticalPaths().size());
        assertEquals("auth", config.getCriticalPaths().get(0));
    }

    @Test
    void load_invalidYaml_throwsException(@TempDir Path tempDir) throws Exception {
        String yaml = "rules: [invalid\n  broken: {";
        Path file = tempDir.resolve(".archon.yml");
        Files.writeString(file, yaml);

        assertThrows(RuntimeException.class, () -> ArchonConfig.load(file));
    }

    @Test
    void loadOrDefault_validYaml_parsesIgnorePatterns(@TempDir Path tempDir) throws Exception {
        String yaml = """
            version: 1
            ignore:
              - "**/generated/**"
              - "**/*.generated.java"
            """;
        Path file = tempDir.resolve(".archon.yml");
        Files.writeString(file, yaml);

        ArchonConfig config = ArchonConfig.loadOrDefault(file);

        assertEquals(2, config.getIgnore().size());
        assertEquals("**/generated/**", config.getIgnore().get(0));
    }

    @Test
    void loadOrDefault_validYaml_parsesForbidCoreEntityLeakage(@TempDir Path tempDir) throws Exception {
        String yaml = """
            version: 1
            rules:
              forbid_core_entity_leakage:
                - com.example.core.EntityA
                - com.example.core.EntityB
            """;
        Path file = tempDir.resolve(".archon.yml");
        Files.writeString(file, yaml);

        ArchonConfig config = ArchonConfig.loadOrDefault(file);

        assertEquals(2, config.getForbidCoreEntityLeakage().size());
        assertEquals("com.example.core.EntityA", config.getForbidCoreEntityLeakage().get(0));
    }
}