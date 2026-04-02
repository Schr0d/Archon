package com.archon.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModuleDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectModules_mavenMultiModule_returnsSourceRoots() throws IOException {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, "<project><modules><module>ruoyi-system</module><module>ruoyi-framework</module></modules></project>");
        createSourceDir(tempDir, "ruoyi-system/src/main/java");
        createSourceDir(tempDir, "ruoyi-framework/src/main/java");

        ModuleDetector detector = new ModuleDetector();
        List<ModuleDetector.SourceRoot> roots = detector.detectModules(tempDir);

        assertEquals(2, roots.size());
        assertTrue(roots.stream().anyMatch(r -> r.getModuleName().equals("ruoyi-system")));
        assertTrue(roots.stream().anyMatch(r -> r.getModuleName().equals("ruoyi-framework")));
        roots.forEach(r -> assertTrue(Files.isDirectory(r.getPath())));
    }

    @Test
    void detectModules_noBuildFile_treatsAsSingleModule() throws IOException {
        createSourceDir(tempDir, "src/main/java");

        ModuleDetector detector = new ModuleDetector();
        List<ModuleDetector.SourceRoot> roots = detector.detectModules(tempDir);

        assertEquals(1, roots.size());
        assertEquals("", roots.get(0).getModuleName());
        assertEquals(tempDir.resolve("src/main/java"), roots.get(0).getPath());
    }

    @Test
    void detectModules_noSourceDir_returnsEmpty() {
        ModuleDetector detector = new ModuleDetector();
        List<ModuleDetector.SourceRoot> roots = detector.detectModules(tempDir);

        assertTrue(roots.isEmpty());
    }

    @Test
    void detectModules_mavenModule_missingSourceDir_skipped() throws IOException {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, "<project><modules><module>module-a</module><module>module-b</module></modules></project>");
        createSourceDir(tempDir, "module-a/src/main/java");

        ModuleDetector detector = new ModuleDetector();
        List<ModuleDetector.SourceRoot> roots = detector.detectModules(tempDir);

        assertEquals(1, roots.size());
        assertEquals("module-a", roots.get(0).getModuleName());
    }

    private void createSourceDir(Path root, String relativePath) throws IOException {
        Files.createDirectories(root.resolve(relativePath));
    }
}
