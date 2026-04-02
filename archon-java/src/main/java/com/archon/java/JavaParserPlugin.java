package com.archon.java;

import com.archon.core.config.ArchonConfig;
import com.archon.core.graph.BlindSpot;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.GraphBuilder;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses Java source trees and builds a dependency graph.
 * Orchestrates: ModuleDetector -> AstVisitor -> BlindSpotDetector
 */
public class JavaParserPlugin {

    public ParseResult parse(Path projectRoot, ArchonConfig config) {
        List<ParseError> errors = new ArrayList<>();
        List<BlindSpot> blindSpots = new ArrayList<>();
        GraphBuilder graphBuilder = GraphBuilder.builder();

        // Step 1: Detect source roots
        ModuleDetector moduleDetector = new ModuleDetector();
        List<ModuleDetector.SourceRoot> sourceRoots = moduleDetector.detectModules(projectRoot);

        if (sourceRoots.isEmpty()) {
            return new ParseResult(graphBuilder.build(), blindSpots, errors);
        }

        // Step 2: First pass — collect all source FQCNs
        JavaParser javaParser = new JavaParser();
        Set<String> sourceClasses = new HashSet<>();
        for (ModuleDetector.SourceRoot sourceRoot : sourceRoots) {
            collectSourceFqcns(sourceRoot.getPath(), javaParser, sourceClasses, errors);
        }

        // Step 3: Second pass — build graph with filtering (only source-tree classes)
        AstVisitor astVisitor = new AstVisitor(sourceClasses);

        for (ModuleDetector.SourceRoot sourceRoot : sourceRoots) {
            parseSourceRoot(sourceRoot.getPath(), javaParser, astVisitor, graphBuilder, errors);
        }

        // Step 4: Detect blind spots
        BlindSpotDetector blindSpotDetector = new BlindSpotDetector();
        for (ModuleDetector.SourceRoot sourceRoot : sourceRoots) {
            blindSpots.addAll(blindSpotDetector.detect(sourceRoot.getPath()));
        }

        return new ParseResult(graphBuilder.build(), blindSpots, errors);
    }

    private void parseSourceRoot(Path sourceRoot, JavaParser javaParser,
                                  AstVisitor astVisitor, GraphBuilder graphBuilder,
                                  List<ParseError> errors) {
        if (!Files.isDirectory(sourceRoot)) {
            return;
        }

        try {
            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        parseSingleFile(file, javaParser, astVisitor, graphBuilder, errors);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            errors.add(new ParseError(sourceRoot.toString(), 0,
                "Failed to walk source directory: " + e.getMessage()));
        }
    }

    /**
     * First pass: walk all Java files and collect their FQCNs into a set.
     * This set is later passed to AstVisitor so it only creates graph nodes
     * for classes that actually exist in the source tree.
     */
    private void collectSourceFqcns(Path sourceRoot, JavaParser javaParser,
                                     Set<String> sourceClasses, List<ParseError> errors) {
        if (!Files.isDirectory(sourceRoot)) {
            return;
        }

        try {
            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        collectFqcnsFromFile(file, javaParser, sourceClasses, errors);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            errors.add(new ParseError(sourceRoot.toString(), 0,
                "Failed to walk source directory for FQCN collection: " + e.getMessage()));
        }
    }

    private void collectFqcnsFromFile(Path file, JavaParser javaParser,
                                       Set<String> sourceClasses, List<ParseError> errors) {
        try {
            var parseResult = javaParser.parse(file);
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getName().asString())
                    .orElse("");
                for (com.github.javaparser.ast.body.TypeDeclaration<?> typeDecl : cu.getTypes()) {
                    String fqcn = packageName.isEmpty()
                        ? typeDecl.getName().asString()
                        : packageName + "." + typeDecl.getName().asString();
                    sourceClasses.add(fqcn);
                }
            }
            // Silently skip files that fail to parse in the collection pass;
            // they'll be reported as errors during the main parsing pass.
        } catch (IOException e) {
            errors.add(new ParseError(file.toString(), 0,
                "Failed to read file for FQCN collection: " + e.getMessage()));
        }
    }

    private void parseSingleFile(Path file, JavaParser javaParser,
                                  AstVisitor astVisitor, GraphBuilder graphBuilder,
                                  List<ParseError> errors) {
        try {
            var parseResult = javaParser.parse(file);
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                astVisitor.visit(cu, graphBuilder);
            } else {
                String message = parseResult.getProblems().stream()
                    .map(p -> p.getMessage())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Unknown parse error");
                errors.add(new ParseError(file.toString(), 0, message));
            }
        } catch (IOException e) {
            errors.add(new ParseError(file.toString(), 0,
                "Failed to read file: " + e.getMessage()));
        }
    }

    public static class ParseResult {
        private final DependencyGraph graph;
        private final List<BlindSpot> blindSpots;
        private final List<ParseError> errors;

        public ParseResult(DependencyGraph graph, List<BlindSpot> blindSpots, List<ParseError> errors) {
            this.graph = graph;
            this.blindSpots = blindSpots;
            this.errors = errors;
        }

        public DependencyGraph getGraph() { return graph; }
        public List<BlindSpot> getBlindSpots() { return blindSpots; }
        public List<ParseError> getErrors() { return errors; }
    }

    public static class ParseError {
        private final String file;
        private final int line;
        private final String message;

        public ParseError(String file, int line, String message) {
            this.file = file;
            this.line = line;
            this.message = message;
        }

        public String getFile() { return file; }
        public int getLine() { return line; }
        public String getMessage() { return message; }
    }
}
