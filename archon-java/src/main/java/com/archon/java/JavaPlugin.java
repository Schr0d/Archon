package com.archon.java;

import com.archon.core.analysis.DomainStrategy;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final Set<String> allSourceFqcns = new HashSet<>();

    public JavaPlugin() {
        this.javaParser = new JavaParser();
    }

    /**
     * Reset the internal FQCN cache. Should be called before parsing a new project.
     */
    public void reset() {
        allSourceFqcns.clear();
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    @Override
    public Optional<DomainStrategy> getDomainStrategy() {
        return Optional.of(new JavaDomainStrategy());
    }

    @Override
    public ParseResult parseFromContent(
        String filePath,
        String content,
        ParseContext context,
        DependencyGraph.MutableBuilder builder
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
                    GraphBuilder.builder().build(),
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

            // Create a temporary builder to capture nodes/edges from this file
            GraphBuilder tempBuilder = GraphBuilder.builder();
            astVisitor.visit(cu, tempBuilder);

            // Build the temp graph to extract nodes and edges
            DependencyGraph tempGraph = tempBuilder.build();

            // Add nodes with namespace prefix to the shared builder
            for (String fqcn : fileFqcns) {
                String prefixedId = NAMESPACE + ":" + fqcn;
                sourceModules.add(prefixedId);
                tempGraph.getNode(fqcn).ifPresent(node -> {
                    builder.addNode(com.archon.core.graph.Node.builder()
                        .id(prefixedId)
                        .type(node.getType())
                        .build());
                });
            }

            // Add edges with namespace prefix to the shared builder
            for (com.archon.core.graph.Edge edge : tempGraph.getAllEdges()) {
                String prefixedSource = NAMESPACE + ":" + edge.getSource();
                String prefixedTarget = NAMESPACE + ":" + edge.getTarget();
                builder.addEdge(com.archon.core.graph.Edge.builder()
                    .source(prefixedSource)
                    .target(prefixedTarget)
                    .type(edge.getType())
                    .confidence(edge.getConfidence())
                    .evidence(edge.getEvidence())
                    .build());
            }

        } catch (Exception e) {
            parseErrors.add(filePath + ":0 - Failed to parse: " + e.getMessage());
        }

        return new ParseResult(
            GraphBuilder.builder().build(),
            sourceModules,
            blindSpots,
            parseErrors
        );
    }
}
