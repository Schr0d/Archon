package com.archon.python;

import com.archon.python.PythonImportExtractor.ImportInfo;
import com.archon.python.PythonImportExtractor.ImportType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

@DisplayName("PythonImportExtractor Tests")
class PythonImportExtractorTest {

    @Test
    @DisplayName("extractImports detects simple import")
    void testSimpleImport() {
        String code = "import os";
        List<ImportInfo> imports = PythonImportExtractor.extractImports(code);

        assertEquals(1, imports.size(), "Should extract one import");
        assertEquals("os", imports.get(0).moduleName(), "Module name should be 'os'");
        assertEquals(ImportType.ABSOLUTE, imports.get(0).type(), "Should be absolute import");
        assertFalse(imports.get(0).isRelative(), "Should not be relative");
    }

    @Test
    @DisplayName("extractImports detects import with alias")
    void testImportWithAlias() {
        String code = "import sys as system";
        List<ImportInfo> imports = PythonImportExtractor.extractImports(code);

        assertEquals(1, imports.size(), "Should extract one import");
        assertEquals("sys", imports.get(0).moduleName(), "Module name should be 'sys' (alias ignored)");
    }

    @Test
    @DisplayName("extractImports detects multiple imports")
    void testMultipleImports() {
        String code = "import os, sys, json";
        List<ImportInfo> imports = PythonImportExtractor.extractImports(code);

        assertEquals(3, imports.size(), "Should extract three imports");
        assertEquals("os", imports.get(0).moduleName());
        assertEquals("sys", imports.get(1).moduleName());
        assertEquals("json", imports.get(2).moduleName());
    }

    @Test
    @DisplayName("extractImports detects from import")
    void testFromImport() {
        String code = "from pathlib import Path";
        List<ImportInfo> imports = PythonImportExtractor.extractImports(code);

        assertEquals(1, imports.size(), "Should extract one import");
        assertEquals("pathlib", imports.get(0).moduleName(), "Module should be 'pathlib'");
    }

    @Test
    @DisplayName("extractImports detects from import with multiple items")
    void testFromImportMultiple() {
        String code = "from collections import deque, defaultdict, Counter";
        List<ImportInfo> imports = PythonImportExtractor.extractImports(code);

        assertEquals(3, imports.size(), "Should extract three imports from one module");
        assertEquals("collections", imports.get(0).moduleName());
    }

    @Test
    @DisplayName("extractImports handles empty file")
    void testEmptyFile() {
        String code = "";
        List<ImportInfo> imports = PythonImportExtractor.extractImports(code);

        assertTrue(imports.isEmpty(), "Empty file should have no imports");
    }

    @Test
    @DisplayName("extractImports handles comment-only file")
    void testCommentOnlyFile() {
        String code = "# This is a comment\n# Another comment";
        List<ImportInfo> imports = PythonImportExtractor.extractImports(code);

        assertTrue(imports.isEmpty(), "Comment-only file should have no imports");
    }

    @Test
    @DisplayName("extractImports handles import with leading whitespace")
    void testImportWithLeadingWhitespace() {
        String code = "    import os";
        List<ImportInfo> imports = PythonImportExtractor.extractImports(code);

        assertEquals(1, imports.size(), "Should extract one import");
        assertEquals("os", imports.get(0).moduleName(), "Module name should be 'os'");
    }

    // Relative import tests will be added in Task 4 after PythonModuleResolver exists
}
