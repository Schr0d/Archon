package com.archon.python;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@DisplayName("PythonModuleResolver Tests")
class PythonModuleResolverTest {

    @Test
    @DisplayName("resolve resolves single-dot relative import")
    void testResolveSingleDotRelative() {
        PythonModuleResolver resolver = new PythonModuleResolver();

        // Current file: /project/src/service/handler.py
        // Current package: src.service
        // Import: from . import sibling
        // Expected: src.service.sibling

        Optional<String> resolved = resolver.resolve(
            ".sibling",
            "src.service",
            "/project/src"
        );

        assertEquals(Optional.of("src.service.sibling"), resolved,
            "Should resolve to src.service.sibling");
    }

    @Test
    @DisplayName("resolve resolves double-dot relative import")
    void testResolveDoubleDotRelative() {
        PythonModuleResolver resolver = new PythonModuleResolver();

        // Current file: /project/src/service/handler.py
        // Current package: src.service
        // Import: from .. import utils
        // Expected: src.utils

        Optional<String> resolved = resolver.resolve(
            "..utils",
            "src.service",
            "/project/src"
        );

        assertEquals(Optional.of("src.utils"), resolved,
            "Should resolve to src.utils");
    }

    @Test
    @DisplayName("resolve resolves triple-dot relative import")
    void testResolveTripleDotRelative() {
        PythonModuleResolver resolver = new PythonModuleResolver();

        // Current file: /project/src/service/handler.py
        // Current package: src.service
        // Import: from ... import config
        // Expected: config

        Optional<String> resolved = resolver.resolve(
            "...config",
            "src.service",
            "/project/src"
        );

        assertEquals(Optional.of("config"), resolved,
            "Should resolve to config (root level)");
    }

    @Test
    @DisplayName("resolve returns empty when relative import goes above root")
    void testResolveAboveRoot() {
        PythonModuleResolver resolver = new PythonModuleResolver();

        // Current file: /project/service.py (root level, no package)
        // Current package: (empty)
        // Import: from .. import foo
        // Expected: empty (can't go above root)

        Optional<String> resolved = resolver.resolve(
            "..foo",
            "",
            "/project"
        );

        assertEquals(Optional.empty(), resolved,
            "Should return empty when going above root");
    }

    @Test
    @DisplayName("resolve handles subpackage relative import")
    void testResolveSubpackageRelative() {
        PythonModuleResolver resolver = new PythonModuleResolver();

        // Current file: /project/src/service/handler.py
        // Current package: src.service
        // Import: from .sub import func
        // Expected: src.service.sub

        Optional<String> resolved = resolver.resolve(
            ".sub",
            "src.service",
            "/project/src"
        );

        assertEquals(Optional.of("src.service.sub"), resolved,
            "Should resolve to src.service.sub");
    }

    @Test
    @DisplayName("resolve performs filesystem check for sibling module")
    void testFilesystemCheckForSiblingModule(@TempDir Path tempDir) throws IOException {
        PythonModuleResolver resolver = new PythonModuleResolver();

        // Create test directory structure
        Path serviceDir = tempDir.resolve("project/src/service");
        Files.createDirectories(serviceDir);

        Path siblingFile = serviceDir.resolve("sibling.py");
        Files.writeString(siblingFile, "# sibling module");

        // Current file: service/handler.py
        // Package: service
        // Import: from . import sibling
        // Source root: /project/src

        Optional<String> resolved = resolver.resolve(
            ".sibling",
            "service",
            tempDir.resolve("project/src").toString()
        );

        // Filesystem check should succeed
        assertEquals(Optional.of("service.sibling"), resolved,
            "Should resolve to service.sibling when file exists");

        // Clean up
        Files.deleteIfExists(siblingFile);
    }

    @Test
    @DisplayName("resolve returns empty when sibling file not found")
    void testFilesystemCheckReturnsEmptyWhenNotFound(@TempDir Path tempDir) throws IOException {
        PythonModuleResolver resolver = new PythonModuleResolver();

        // Create test directory structure WITHOUT sibling.py
        Path serviceDir = tempDir.resolve("project/src/service");
        Files.createDirectories(serviceDir);

        // Current file: service/handler.py
        // Package: service
        // Import: from . import sibling
        // But sibling.py doesn't exist

        Optional<String> resolved = resolver.resolve(
            ".sibling",
            "service",
            tempDir.resolve("project/src").toString()
        );

        // Filesystem check should fail
        assertEquals(Optional.empty(), resolved,
            "Should return empty when sibling file doesn't exist");
    }

    @Test
    @DisplayName("resolve performs ancestral search for tests directory")
    void testAncestralSearchForTestsDirectory(@TempDir Path tempDir) throws IOException {
        PythonModuleResolver resolver = new PythonModuleResolver();

        // Create directory structure: /project/src/tests/
        // NOT /project/src/service/tests/
        Path serviceDir = tempDir.resolve("project/src/service");
        Files.createDirectories(serviceDir);

        Path testsDir = tempDir.resolve("project/src/tests");
        Files.createDirectories(testsDir);
        Files.writeString(testsDir.resolve("test_utils.py"), "# test utils");

        // Current file: /project/src/service/handler.py
        // Package: service
        // Import: from ...tests import test_utils
        // Ancestral search: service/ (not found) → ../tests (found!)
        // Source root: /project/src

        Optional<String> resolved = resolver.resolve(
            "...tests.test_utils",
            "service",
            tempDir.resolve("project/src").toString()
        );

        // Should find tests/ at parent level
        assertEquals(Optional.of("tests.test_utils"), resolved,
            "Should find tests/ directory via ancestral search");

        // Clean up
        Files.deleteIfExists(testsDir.resolve("test_utils.py"));
        Files.deleteIfExists(testsDir);
    }

    @Test
    @DisplayName("resolve validates module with __init__.py")
    void testFilesystemCheckForModuleInit(@TempDir Path tempDir) throws IOException {
        PythonModuleResolver resolver = new PythonModuleResolver();

        // Create test directory structure with __init__.py
        Path serviceDir = tempDir.resolve("project/src/service");
        Files.createDirectories(serviceDir);

        Path utilsDir = tempDir.resolve("project/src/utils");
        Files.createDirectories(utilsDir);
        Files.writeString(utilsDir.resolve("__init__.py"), "# utils package");

        // Current file: service/handler.py
        // Package: service
        // Import: from .. import utils
        // Source root: /project/src

        Optional<String> resolved = resolver.resolve(
            "..utils",
            "service",
            tempDir.resolve("project/src").toString()
        );

        // Filesystem check should succeed with __init__.py
        assertEquals(Optional.of("utils"), resolved,
            "Should resolve to utils when __init__.py exists");

        // Clean up
        Files.deleteIfExists(utilsDir.resolve("__init__.py"));
        Files.deleteIfExists(utilsDir);
    }
}
