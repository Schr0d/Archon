package com.archon.java;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.archon.core.plugin.ModuleDeclaration;
import com.archon.core.plugin.DependencyDeclaration;
import com.archon.core.plugin.Confidence;
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

    private static final String NAMESPACE = "java";
    private static final Set<String> EXTENSIONS = Set.of("java");

    private final JavaParser javaParser;
    private final Set<String> allSourceFqcns = ConcurrentHashMap.newKeySet();

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
        List<ModuleDeclaration> moduleDeclarations = new ArrayList<>();
        List<DependencyDeclaration> declarations = new ArrayList<>();

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
                    new DependencyGraph.MutableBuilder().build(),
                    sourceModules,
                    blindSpots,
                    parseErrors,
                    moduleDeclarations,
                    declarations
                );
            }

            CompilationUnit cu = parseResult.getResult().get();

            // Extract package and collect FQCNs
            String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getName().asString())
                .orElse("");

            Set<String> fileFqcns = new HashSet<>();
            for (com.github.javaparser.ast.body.TypeDeclaration<?> typeDecl : cu.getTypes()) {
                String fqcn = packageName.isEmpty()
                    ? typeDecl.getName().asString()
                    : packageName + "." + typeDecl.getName().asString();
                fileFqcns.add(fqcn);
                allSourceFqcns.add(fqcn); // Add to global set for edge resolution
            }

            // Create AstVisitor with all known source classes
            AstVisitor astVisitor = new AstVisitor(allSourceFqcns);

            // Create a local builder to capture nodes/edges from this file
            DependencyGraph.MutableBuilder localBuilder = new DependencyGraph.MutableBuilder();
            astVisitor.visit(cu, localBuilder);

            // Build the local graph to extract nodes and edges
            DependencyGraph localGraph = localBuilder.build();

            // Add nodes with namespace prefix, collect module declarations
            Set<String> addedPrefixedIds = new HashSet<>();
            for (String fqcn : fileFqcns) {
                String prefixedId = NAMESPACE + ":" + fqcn;
                sourceModules.add(prefixedId);
                localGraph.getNode(fqcn).ifPresent(node -> {
                    addedPrefixedIds.add(prefixedId);
                    // Collect module declaration
                    moduleDeclarations.add(new ModuleDeclaration(
                        prefixedId,
                        com.archon.core.plugin.NodeType.CLASS,
                        filePath,
                        com.archon.core.plugin.Confidence.HIGH
                    ));
                });
            }

            // Add edges with namespace prefix, collect dependency declarations
            for (Edge edge : localGraph.getAllEdges()) {
                String prefixedSource = NAMESPACE + ":" + edge.getSource();
                String prefixedTarget = NAMESPACE + ":" + edge.getTarget();
                // Only add edges where source node exists in the builder
                if (addedPrefixedIds.contains(prefixedSource)) {
                    // Collect dependency declaration
                    declarations.add(new DependencyDeclaration(
                        prefixedSource,
                        prefixedTarget,
                        com.archon.core.plugin.EdgeType.valueOf(edge.getType().name()),
                        com.archon.core.plugin.Confidence.valueOf(edge.getConfidence().name()),
                        edge.getEvidence(),
                        edge.isDynamic()
                    ));
                }
            }

            // Return with both graph and declarations populated
            return new ParseResult(
                localGraph,
                sourceModules,
                blindSpots,
                parseErrors,
                moduleDeclarations,
                declarations
            );

        } catch (Exception e) {
            parseErrors.add(filePath + ":0 - Failed to parse: " + e.getMessage());
        }

        return new ParseResult(
            new DependencyGraph.MutableBuilder().build(),
            sourceModules,
            blindSpots,
            parseErrors,
            moduleDeclarations,
            declarations
        );
    }
}
