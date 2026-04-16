package com.archon.java;

import com.archon.core.coordination.PostProcessResult;
import com.archon.core.plugin.BlindSpot;
import com.archon.core.plugin.DependencyDeclaration;
import com.archon.core.plugin.EdgeType;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ModuleDeclaration;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LanguagePlugin implementation for Java.
 *
 * <p>Delegates to JavaParserPlugin for parsing, adding namespace prefixing
 * for multi-language support. All node IDs are prefixed with "java:".
 *
 * <p>Implements the LanguagePlugin SPI for ServiceLoader discovery.
 *
 * <p>Maintains an internal set of source FQCNs across parseFromContent calls
 * to enable proper edge resolution between classes parsed in different calls.
 */
public class JavaPlugin implements LanguagePlugin {

    private static final Set<String> EXTENSIONS = Set.of("java");

    private final JavaParser javaParser;
    private final Set<String> allSourceFqcns = ConcurrentHashMap.newKeySet();
    private final SpringDIPostProcessor springDIPostProcessor = new SpringDIPostProcessor();

    public JavaPlugin() {
        this.javaParser = createConfiguredParser();
    }

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

    /**
     * Reset the internal FQCN cache. Should be called before parsing a new project.
     */
    @Override
    public void reset() {
        allSourceFqcns.clear();
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    @Override
    public ParseResult parseFromContent(
        String filePath,
        String content,
        ParseContext context
    ) {
        List<String> parseErrors = new ArrayList<>();
        List<com.archon.core.plugin.BlindSpot> blindSpots = new ArrayList<>();
        Set<String> sourceModules = new HashSet<>();

        try {
            // First pass: collect FQCN from this file
            var parseResult = javaParser.parse(content);
            if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
                String message = parseResult.getProblems().stream()
                    .map(p -> p.getMessage())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Unknown parse error");
                parseErrors.add(filePath + ":0 - " + message);
                return new ParseResult(
                    sourceModules,
                    blindSpots,
                    parseErrors
                );
            }

            CompilationUnit cu = parseResult.getResult().get();

            // Extract package and collect FQCNs
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getName().asString())
                .orElse("");

            for (com.github.javaparser.ast.body.TypeDeclaration<?> typeDecl : cu.getTypes()) {
                String fqcn = packageName.isEmpty()
                    ? typeDecl.getName().asString()
                    : packageName + "." + typeDecl.getName().asString();
                allSourceFqcns.add(fqcn);
            }

            // Collect all source modules for this file
            for (com.github.javaparser.ast.body.TypeDeclaration<?> typeDecl : cu.getTypes()) {
                String fqcn = packageName.isEmpty()
                    ? typeDecl.getName().asString()
                    : packageName + "." + typeDecl.getName().asString();
                sourceModules.add("java:" + fqcn);
            }

            // Create AstVisitor with all known source classes — it collects declarations directly
            AstVisitor astVisitor = new AstVisitor(allSourceFqcns);
            astVisitor.visit(cu, filePath);

            return new ParseResult(
                sourceModules,
                blindSpots,
                parseErrors,
                astVisitor.getModuleDeclarations(),
                astVisitor.getDependencyDeclarations()
            );

        } catch (Exception e) {
            parseErrors.add(filePath + ":0 - Failed to parse: " + e.getMessage());
        }

        return new ParseResult(
            sourceModules,
            blindSpots,
            parseErrors
        );
    }

    @Override
    public PostProcessResult postProcess(
        List<ModuleDeclaration> allModules,
        ParseContext context
    ) {
        // Only run if there are Java modules
        boolean hasJavaModules = allModules.stream()
            .anyMatch(m -> m.id().startsWith("java:"));
        if (!hasJavaModules) {
            return PostProcessResult.empty();
        }

        // Find compiled class directories
        List<Path> classDirs = ClassDirectoryFinder.findClassDirectories(
            context.getSourceRoot()
        );
        if (classDirs.isEmpty()) {
            return PostProcessResult.empty();
        }

        // Run Spring DI scanning for each class directory
        List<DependencyDeclaration> allDecls = new ArrayList<>();
        List<BlindSpot> allBlindSpots = new ArrayList<>();
        for (Path classDir : classDirs) {
            PostProcessResult result = springDIPostProcessor.process(allModules, classDir);
            allDecls.addAll(result.declarations());
            allBlindSpots.addAll(result.blindSpots());
        }

        return new PostProcessResult(allDecls, allBlindSpots);
    }
}
