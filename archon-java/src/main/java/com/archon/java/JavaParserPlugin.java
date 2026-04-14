package com.archon.java;

import com.archon.core.config.ArchonConfig;
import com.archon.core.coordination.DeclarationGraphBuilder;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.plugin.BlindSpot;
import com.archon.core.plugin.DependencyDeclaration;
import com.archon.core.plugin.ModuleDeclaration;
import com.archon.core.util.ModuleDetector;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
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
import java.util.Map;
import java.util.Set;

/**
 * Parses Java source trees and builds a dependency graph.
 * Orchestrates: ModuleDetector -> AstVisitor -> BlindSpotDetector
 *
 * <p>This is a standalone utility (NOT implementing LanguagePlugin).
 * It has its own ParseResult and ParseError inner classes for its
 * file-system-based parsing mode.
 */
public class JavaParserPlugin {

    /**
     * Creates a JavaParser configured for the current runtime Java version.
     * Detects the runtime version and sets the language level accordingly.
     */
    private static JavaParser createConfiguredParser() {
        ParserConfiguration parserConfig = new ParserConfiguration();

        // Detect runtime Java version
        String javaVersion = System.getProperty("java.specification.version");
        if (javaVersion != null) {
            // Map Java version to JavaParser language level
            if (javaVersion.startsWith("17")) {
                parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
            } else if (javaVersion.startsWith("16")) {
                parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_16);
            } else if (javaVersion.startsWith("15")) {
                parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_15);
            } else if (javaVersion.startsWith("14")) {
                parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_14);
            } else if (javaVersion.startsWith("13")) {
                parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_13);
            } else if (javaVersion.startsWith("11")) {
                parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
            } else if (javaVersion.startsWith("1.8")) {
                parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8);
            } else {
                // Default to Java 8 for unknown versions (conservative)
                parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8);
            }
        }

        return new JavaParser(parserConfig);
    }

    public ParseResult parse(Path projectRoot, ArchonConfig config) {
        List<ParseError> errors = new ArrayList<>();
        List<BlindSpot> blindSpots = new ArrayList<>();

        // Step 1: Detect source roots
        ModuleDetector moduleDetector = new ModuleDetector();
        List<ModuleDetector.SourceRoot> sourceRoots = moduleDetector.detectModules(projectRoot);

        if (sourceRoots.isEmpty()) {
            return new ParseResult(new DependencyGraph.MutableBuilder().build(), blindSpots, errors);
        }

        // Step 2: First pass — collect all source FQCNs
        JavaParser javaParser = createConfiguredParser();
        Set<String> sourceClasses = new HashSet<>();
        for (ModuleDetector.SourceRoot sourceRoot : sourceRoots) {
            collectSourceFqcns(sourceRoot.getPath(), javaParser, sourceClasses, errors);
        }

        // Step 3: Second pass — build graph with filtering (only source-tree classes)
        AstVisitor astVisitor = new AstVisitor(sourceClasses);

        for (ModuleDetector.SourceRoot sourceRoot : sourceRoots) {
            parseSourceRoot(sourceRoot.getPath(), javaParser, astVisitor, errors);
        }

        // Step 4: Build graph from declarations collected by AstVisitor
        DependencyGraph graph = DeclarationGraphBuilder.build(
            astVisitor.getModuleDeclarations(), astVisitor.getDependencyDeclarations()).graph();

        // Step 5: Detect blind spots
        BlindSpotDetector blindSpotDetector = new BlindSpotDetector();
        for (ModuleDetector.SourceRoot sourceRoot : sourceRoots) {
            blindSpots.addAll(blindSpotDetector.detect(sourceRoot.getPath()));
        }

        return new ParseResult(graph, blindSpots, errors);
    }

    /**
     * Parse Java source from in-memory content map.
     * Used by diff analysis to parse base versions of changed files from git show.
     *
     * @param fileContents     map of relative file path -> file content string
     * @param knownSourceClasses  complete set of source FQCNs (from head graph + base FQCNs)
     * @return ParseResult with the dependency graph
     */
    public ParseResult parseFromContent(Map<Path, String> fileContents, Set<String> knownSourceClasses) {
        List<ParseError> errors = new ArrayList<>();

        if (fileContents.isEmpty()) {
            return new ParseResult(new DependencyGraph.MutableBuilder().build(), List.of(), errors);
        }

        // Collect FQCNs from the provided content
        Set<String> sourceClasses = new HashSet<>(knownSourceClasses);
        JavaParser javaParser = createConfiguredParser();
        for (Map.Entry<Path, String> entry : fileContents.entrySet()) {
            collectFqcnsFromContent(entry.getKey().toString(), entry.getValue(), javaParser, sourceClasses, errors);
        }

        // Build graph with AstVisitor using the combined source class set
        AstVisitor astVisitor = new AstVisitor(sourceClasses);
        for (Map.Entry<Path, String> entry : fileContents.entrySet()) {
            parseContentFile(entry.getKey().toString(), entry.getValue(), javaParser, astVisitor, errors);
        }

        // Build graph from declarations
        DependencyGraph graph = DeclarationGraphBuilder.build(
            astVisitor.getModuleDeclarations(), astVisitor.getDependencyDeclarations()).graph();

        // No blind spot detection for content-based parsing (no filesystem to scan)
        return new ParseResult(graph, List.of(), errors);
    }

    private void parseSourceRoot(Path sourceRoot, JavaParser javaParser,
                                  AstVisitor astVisitor, List<ParseError> errors) {
        if (!Files.isDirectory(sourceRoot)) {
            return;
        }

        try {
            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        parseSingleFile(file, javaParser, astVisitor, errors);
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
                                  AstVisitor astVisitor, List<ParseError> errors) {
        try {
            var parseResult = javaParser.parse(file);
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                astVisitor.visit(cu, file.toString());
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

    private void collectFqcnsFromContent(String fileName, String content, JavaParser javaParser,
                                          Set<String> sourceClasses, List<ParseError> errors) {
        try {
            var parseResult = javaParser.parse(content);
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
        } catch (Exception e) {
            errors.add(new ParseError(fileName, 0,
                "Failed to parse content for FQCN collection: " + e.getMessage()));
        }
    }

    private void parseContentFile(String fileName, String content, JavaParser javaParser,
                                   AstVisitor astVisitor, List<ParseError> errors) {
        try {
            var parseResult = javaParser.parse(content);
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                astVisitor.visit(cu, fileName);
            } else {
                String message = parseResult.getProblems().stream()
                    .map(p -> p.getMessage())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Unknown parse error");
                errors.add(new ParseError(fileName, 0, message));
            }
        } catch (Exception e) {
            errors.add(new ParseError(fileName, 0,
                "Failed to parse content: " + e.getMessage()));
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
