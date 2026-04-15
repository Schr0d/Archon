package com.archon.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassDirectoryFinderTest {

    @Test
    void findsMavenTargetClasses(@TempDir Path projectRoot) throws IOException {
        Path targetClasses = projectRoot.resolve("target").resolve("classes");
        Files.createDirectories(targetClasses);

        List<Path> dirs = ClassDirectoryFinder.findClassDirectories(projectRoot);
        assertEquals(1, dirs.size());
        assertEquals(targetClasses.toAbsolutePath(), dirs.get(0).toAbsolutePath());
    }

    @Test
    void findsGradleBuildClasses(@TempDir Path projectRoot) throws IOException {
        Path buildClasses = projectRoot.resolve("build").resolve("classes").resolve("java").resolve("main");
        Files.createDirectories(buildClasses);

        List<Path> dirs = ClassDirectoryFinder.findClassDirectories(projectRoot);
        assertEquals(1, dirs.size());
        assertEquals(buildClasses.toAbsolutePath(), dirs.get(0).toAbsolutePath());
    }

    @Test
    void findsMultiModuleMavenLayout(@TempDir Path projectRoot) throws IOException {
        Path module1Classes = projectRoot.resolve("module-a").resolve("target").resolve("classes");
        Path module2Classes = projectRoot.resolve("module-b").resolve("target").resolve("classes");
        Files.createDirectories(module1Classes);
        Files.createDirectories(module2Classes);

        List<Path> dirs = ClassDirectoryFinder.findClassDirectories(projectRoot);
        assertEquals(2, dirs.size());
    }

    @Test
    void returnsEmptyWhenNoClassDirsExist(@TempDir Path projectRoot) {
        List<Path> dirs = ClassDirectoryFinder.findClassDirectories(projectRoot);
        assertTrue(dirs.isEmpty());
    }

    @Test
    void ignoresTestClassesDirectory(@TempDir Path projectRoot) throws IOException {
        Path testClasses = projectRoot.resolve("target").resolve("test-classes");
        Path mainClasses = projectRoot.resolve("target").resolve("classes");
        Files.createDirectories(testClasses);
        Files.createDirectories(mainClasses);

        List<Path> dirs = ClassDirectoryFinder.findClassDirectories(projectRoot);
        assertEquals(1, dirs.size());
        assertTrue(dirs.get(0).toString().endsWith("classes"));
        assertFalse(dirs.get(0).toString().contains("test-classes"));
    }
}