package com.archon.python;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

@DisplayName("PythonImportExtractor Tests")
class PythonImportExtractorTest {

    @Test
    @DisplayName("extractImports detects simple import")
    void testSimpleImport() {
        String code = "import os";
        Set<String> imports = PythonImportExtractor.extractImports(code);

        assertEquals(1, imports.size(), "Should extract one import");
        assertTrue(imports.contains("os"), "Module name should be 'os'");
    }

    @Test
    @DisplayName("extractImports detects import with alias")
    void testImportWithAlias() {
        String code = "import sys as system";
        Set<String> imports = PythonImportExtractor.extractImports(code);

        assertEquals(1, imports.size(), "Should extract one import");
        assertTrue(imports.contains("sys"), "Module name should be 'sys' (alias ignored)");
    }

    @Test
    @DisplayName("extractImports detects multiple imports")
    void testMultipleImports() {
        String code = "import os, sys, json";
        Set<String> imports = PythonImportExtractor.extractImports(code);

        assertEquals(3, imports.size(), "Should extract three imports");
        assertTrue(imports.contains("os"));
        assertTrue(imports.contains("sys"));
        assertTrue(imports.contains("json"));
    }

    @Test
    @DisplayName("extractImports detects from import")
    void testFromImport() {
        String code = "from pathlib import Path";
        Set<String> imports = PythonImportExtractor.extractImports(code);

        assertEquals(1, imports.size(), "Should extract one import");
        assertTrue(imports.contains("pathlib"), "Module should be 'pathlib'");
    }

    @Test
    @DisplayName("extractImports detects from import with multiple items")
    void testFromImportMultiple() {
        String code = "from collections import deque, defaultdict, Counter";
        Set<String> imports = PythonImportExtractor.extractImports(code);

        assertEquals(1, imports.size(), "Should extract one module (collections) with multiple items");
        assertTrue(imports.contains("collections"));
    }

    @Test
    @DisplayName("extractImports handles empty file")
    void testEmptyFile() {
        String code = "";
        Set<String> imports = PythonImportExtractor.extractImports(code);

        assertTrue(imports.isEmpty(), "Empty file should have no imports");
    }

    @Test
    @DisplayName("extractImports handles comment-only file")
    void testCommentOnlyFile() {
        String code = "# This is a comment\n# Another comment";
        Set<String> imports = PythonImportExtractor.extractImports(code);

        assertTrue(imports.isEmpty(), "Comment-only file should have no imports");
    }

    @Test
    @DisplayName("extractImports handles import with leading whitespace")
    void testImportWithLeadingWhitespace() {
        String code = "    import os";
        Set<String> imports = PythonImportExtractor.extractImports(code);

        assertEquals(1, imports.size(), "Should extract one import");
        assertTrue(imports.contains("os"), "Module name should be 'os'");
    }

    // Relative import tests will be added in Task 4 after PythonModuleResolver exists
}
