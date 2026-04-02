package com.archon.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SymbolSolverAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void solve_validSourceRoot_returnsTrue() throws IOException {
        Path srcRoot = tempDir.resolve("src/main/java");
        Path pkg = srcRoot.resolve("com/example");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Simple.java"),
            "package com.example;\npublic class Simple { public void foo() {} }");

        SymbolSolverAdapter adapter = new SymbolSolverAdapter(srcRoot);
        boolean result = adapter.solve(pkg.resolve("Simple.java"));
        assertTrue(result);
    }

    @Test
    void solve_missingDependency_returnsFalse() throws IOException {
        Path srcRoot = tempDir.resolve("src/main/java");
        Path pkg = srcRoot.resolve("com/example");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Broken.java"),
            "package com.example;\nimport com.nonexistent.Missing;\npublic class Broken { Missing m; }");

        SymbolSolverAdapter adapter = new SymbolSolverAdapter(srcRoot);
        boolean result = adapter.solve(pkg.resolve("Broken.java"));
        assertFalse(result);
    }

    @Test
    void solve_noSourceRoot_returnsFalse() {
        SymbolSolverAdapter adapter = new SymbolSolverAdapter(null);
        assertFalse(adapter.solve(null));
    }
}
