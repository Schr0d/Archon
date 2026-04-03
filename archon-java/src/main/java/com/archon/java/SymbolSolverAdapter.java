package com.archon.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Per-file symbol solving with fallback to import-level analysis.
 * Returns true if symbol solving succeeded, false if it should fall back.
 */
public class SymbolSolverAdapter {
    private final CombinedTypeSolver typeSolver;

    public SymbolSolverAdapter(Path sourceRoot) {
        if (sourceRoot != null && Files.isDirectory(sourceRoot)) {
            this.typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(),
                new JavaParserTypeSolver(sourceRoot)
            );
        } else {
            this.typeSolver = null;
        }
    }

    /**
     * Attempt to parse and solve the given Java file.
     * Returns true if symbol solving succeeded, false to indicate fallback needed.
     */
    public boolean solve(Path javaFile) {
        if (typeSolver == null || javaFile == null || !Files.isRegularFile(javaFile)) {
            return false;
        }

        try {
            JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
            ParserConfiguration parserConfig = new ParserConfiguration()
                .setSymbolResolver(symbolSolver);
            JavaParser parser = new JavaParser(parserConfig);
            var result = parser.parse(javaFile);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                return false;
            }

            // Try to resolve at least one type to verify symbol solving works
            CompilationUnit cu = result.getResult().get();
            try {
                // Attempt to resolve field types — this catches unresolved imports
                for (FieldDeclaration field : cu.findAll(FieldDeclaration.class)) {
                    if (field.getElementType().isClassOrInterfaceType()) {
                        ClassOrInterfaceType cit = field.getElementType().asClassOrInterfaceType();
                        cit.resolve();
                    }
                }
            } catch (Exception resolveEx) {
                // Symbol solving failed — return false for fallback
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
