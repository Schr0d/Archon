package com.archon.cli;

import com.archon.core.analysis.CentralityService;
import com.archon.core.analysis.CouplingAnalyzer;
import com.archon.core.analysis.CycleDetector;
import com.archon.core.analysis.DomainDetector;
import com.archon.core.analysis.DomainResult;
import com.archon.core.analysis.FullAnalysisData;
import com.archon.core.analysis.ThresholdCalculator;
import com.archon.core.analysis.Thresholds;
import com.archon.core.config.ArchonConfig;
import com.archon.core.coordination.ParseOrchestrator;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;
import com.archon.core.plugin.BlindSpot;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.archon.core.plugin.PluginDiscoverer;
import com.archon.core.viz.DotExporter;
import com.archon.core.util.ModuleDetector;
import com.archon.viz.JsonSerializer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
    name = "analyze",
    description = "Full structural analysis of a Java project",
    mixinStandardHelpOptions = true
)
public class AnalyzeCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Path to the project root")
    String projectPath;

    @Option(names = "--json", description = "Output machine-readable JSON")
    boolean json;

    @Option(names = "--dot", description = "Export Graphviz DOT to file")
    String dotFile;

    @Option(names = "--mermaid", description = "Export Mermaid flowchart to file")
    String mermaidFile;

    @Option(names = "--verbose", description = "Show detailed parsing logs")
    boolean verbose;

    @Option(names = "--quiet", description = "Suppress progress messages (errors still shown)")
    boolean quiet;

    @Option(names = "--with-metadata", description = "Include metadata field in JSON output for AI integration")
    boolean withMetadata;

    @Option(names = "--with-full-analysis", description = "Include full analysis (centrality metrics, bridges, components) in JSON output")
    boolean withFullAnalysis;

    @Override
    public Integer call() {
        Path root = Path.of(projectPath);
        if (!root.toFile().exists()) {
            System.err.println("Error: path does not exist: " + projectPath);
            return 1;
        }

        ArchonConfig config = ArchonConfig.loadOrDefault(root.resolve(".archon.yml"));

        // Step 1: Discover plugins and collect source files
        PluginDiscoverer discoverer = new PluginDiscoverer();
        List<LanguagePlugin> plugins = discoverer.discoverWithConflictCheck();

        if (plugins.isEmpty()) {
            System.err.println("Error: No language plugins found. Please ensure plugin JARs are on the classpath.");
            return 1;
        }

        // Collect all file extensions from plugins
        Set<String> extensions = plugins.stream()
            .flatMap(p -> p.fileExtensions().stream())
            .collect(Collectors.toSet());

        // Collect all source files using ModuleDetector
        List<Path> sourceFiles = collectSourceFilesStatic(root, extensions);

        if (sourceFiles.isEmpty()) {
            System.out.println("No source files found. Check project path.");
            return 0;
        }

        // Reset any plugin state before parsing
        // Reset any plugin state before parsing
        plugins.forEach(LanguagePlugin::reset);

        // Step 2: Parse with orchestrator
        System.out.println("Parsing " + root + " (" + sourceFiles.size() + " files) ...");
        ParseOrchestrator orchestrator = new ParseOrchestrator(plugins);
        ParseContext context = new ParseContext(root, extensions);
        ParseResult result = orchestrator.parse(sourceFiles, context);

        if (!result.getParseErrors().isEmpty()) {
            System.err.println(result.getParseErrors().size() + " file(s) failed to parse:");
            for (String err : result.getParseErrors()) {
                System.err.println("  " + err);
            }
        }

        DependencyGraph graph = result.getGraph();
        System.out.println("Parsed " + graph.nodeCount() + " classes, " + graph.edgeCount() + " dependencies");

        // Step 2: Domain detection
        DomainDetector domainDetector = new DomainDetector();
        DomainResult domainResult = domainDetector.assignDomains(graph, config.getDomains());
        Map<String, String> domainMap = domainResult.getDomains();

        long distinctDomains = domainMap.values().stream().distinct().count();
        Thresholds thresholds = ThresholdCalculator.calculate(graph.nodeCount(), (int) distinctDomains);
        if (distinctDomains > 0) {
            System.out.println("Domains detected: " + distinctDomains + " (" + domainMap.size() + " classes mapped)");
            if (verbose) {
                domainMap.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue())
                    .forEach(e -> System.out.println("  " + e.getValue() + " <- " + e.getKey()));
            }
        }

        // Step 3: Cycle detection
        CycleDetector cycleDetector = new CycleDetector();
        List<List<String>> cycles = cycleDetector.detectCycles(graph);
        if (cycles.isEmpty()) {
            System.out.println("\nCycles: none");
        } else {
            System.out.println("\n\u001B[31mCycles detected: " + cycles.size() + "\u001B[0m");
            for (List<String> cycle : cycles) {
                System.out.println("  " + String.join(" -> ", cycle) + " -> " + cycle.get(0));
            }
        }

        // Step 4: Coupling hotspots
        CouplingAnalyzer couplingAnalyzer = new CouplingAnalyzer();
        List<Node> hotspots = couplingAnalyzer.findHotspots(graph, thresholds.getCouplingThreshold());
        if (hotspots.isEmpty()) {
            System.out.println("\nCoupling hotspots: none (in-degree <= " + thresholds.getCouplingThreshold() + ")");
        } else {
            System.out.println("\n\u001B[33mCoupling hotspots (in-degree > " + thresholds.getCouplingThreshold() + "):\u001B[0m");
            int displayCap = thresholds.getHotspotDisplayCap();
            List<Node> displayed = hotspots.stream().limit(displayCap).collect(Collectors.toList());
            for (Node node : displayed) {
                System.out.println("  " + node.getId() + " (in-degree: " + node.getInDegree() + ")");
            }
            long remaining = hotspots.size() - displayed.size();
            if (remaining > 0) {
                System.out.println("  ... and " + remaining + " more above threshold " + thresholds.getCouplingThreshold());
            }
        }

        // Step 5: Blind spots
        List<BlindSpot> blindSpots = result.getBlindSpots();
        if (blindSpots.isEmpty()) {
            System.out.println("\nBlind spots: none");
        } else {
            System.out.println("\n\u001B[36mBlind spots (" + blindSpots.size() + " occurrences):\u001B[0m");
            for (BlindSpot spot : blindSpots) {
                System.out.println("  [" + spot.getType() + "] " + spot.getLocation()
                    + " \u2014 " + spot.getDescription());
            }
        }

        // Step 6: DOT export
        if (dotFile != null) {
            DotExporter exporter = new DotExporter();
            String dot = exporter.exportWithSubgraphs(graph, domainMap);
            try {
                java.nio.file.Files.writeString(Path.of(dotFile), dot);
            } catch (Exception e) {
                System.err.println("Failed to write DOT file: " + e.getMessage());
            }
            System.out.println("\nDOT exported to: " + dotFile);
        }

        // Step 7: Mermaid export
        if (mermaidFile != null) {
            String mermaid = exportMermaid(graph, domainMap, cycles);
            try {
                java.nio.file.Files.writeString(Path.of(mermaidFile), mermaid);
            } catch (Exception e) {
                System.err.println("Failed to write Mermaid file: " + e.getMessage());
            }
            System.out.println("\nMermaid exported to: " + mermaidFile);
        }

        // JSON output format (must come before summary to avoid mixing output)
        if (json) {
            JsonSerializer serializer = new JsonSerializer();
            String jsonOutput;

            // Check if full analysis is requested
            FullAnalysisData fullAnalysis = null;
            if (withFullAnalysis) {
                CentralityService centralityService = new CentralityService(graph);
                fullAnalysis = centralityService.computeFullAnalysis();
            }

            jsonOutput = serializer.toJson(
                graph,
                domainMap,
                cycles,
                hotspots,
                blindSpots,
                withMetadata,
                fullAnalysis
            );

            System.out.println(jsonOutput);
            return 0;
        }

        // Summary
        System.out.println("\n--- Summary ---");
        System.out.println("Nodes:       " + graph.nodeCount());
        System.out.println("Edges:       " + graph.edgeCount());
        System.out.println("Domains:     " + distinctDomains);
        System.out.println("Cycles:      " + cycles.size());
        System.out.println("Hotspots:    " + hotspots.size() + " (threshold: " + thresholds.getCouplingThreshold() + ")");
        System.out.println("Blind spots: " + blindSpots.size() + " occurrences");
        System.out.println("Errors:      " + result.getParseErrors().size());

        return (!cycles.isEmpty()) ? 1 : 0;
    }

    /**
     * Export dependency graph to Mermaid flowchart format.
     * Uses domain-based subgraph grouping for better visualization.
     */
    private String exportMermaid(DependencyGraph graph, Map<String, String> domainMap, List<List<String>> cycles) {
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart TD\n");

        // Group nodes by domain for subgraph organization
        java.util.Map<String, java.util.List<String>> domains = new java.util.LinkedHashMap<>();
        for (String nodeId : graph.getNodeIds()) {
            String domain = domainMap.getOrDefault(nodeId, "ungrouped");
            domains.computeIfAbsent(domain, k -> new java.util.ArrayList<>()).add(nodeId);
        }

        // Define nodes with domain grouping
        for (java.util.Map.Entry<String, java.util.List<String>> entry : domains.entrySet()) {
            String domain = entry.getKey();
            sb.append("  subgraph ").append(mermaidSanitize(domain)).append("[\"").append(domain).append("\"]\n");
            for (String nodeId : entry.getValue()) {
                String label = getNodeLabel(nodeId);
                sb.append("    ").append(mermaidSanitize(nodeId)).append("[\"").append(label).append("\"]\n");
            }
            sb.append("  end\n");
        }

        // Define edges
        for (String source : graph.getNodeIds()) {
            for (String target : graph.getDependencies(source)) {
                // Skip self-edges
                if (!source.equals(target)) {
                    sb.append("  ").append(mermaidSanitize(source)).append(" --> ")
                        .append(mermaidSanitize(target)).append("\n");
                }
            }
        }

        // Annotate cycles if any
        if (!cycles.isEmpty()) {
            sb.append("\n  %% Cycles detected:\n");
            for (List<String> cycle : cycles) {
                sb.append("  %% Cycle: ").append(String.join(" -> ", cycle)).append(" -> ").append(cycle.get(0)).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Sanitize node IDs for Mermaid format.
     * Mermaid IDs cannot contain certain characters like dots or colons.
     */
    private String mermaidSanitize(String id) {
        // Replace dots and colons with underscores, and remove other problematic characters
        return id.replaceAll("[.:]", "_").replaceAll("[^a-zA-Z0-9_]", "");
    }

    /**
     * Extract a short label from the node ID.
     * For fully-qualified class names, uses just the class name.
     */
    private String getNodeLabel(String nodeId) {
        int lastDot = nodeId.lastIndexOf('.');
        if (lastDot > 0 && Character.isUpperCase(nodeId.charAt(lastDot + 1))) {
            return nodeId.substring(lastDot + 1);
        }
        int lastSlash = nodeId.lastIndexOf('/');
        if (lastSlash > 0) {
            return nodeId.substring(lastSlash + 1);
        }
        return nodeId;
    }

    /**
     * Collect all source files from the project root matching the given extensions.
     * Uses ModuleDetector for Java projects and walks the tree for other files.
     * Static version for reuse by other commands.
     */
    public static List<Path> collectSourceFilesStatic(Path root, Set<String> extensions) {
        List<Path> sourceFiles = new ArrayList<>();

        // Use ModuleDetector to find Java source roots
        ModuleDetector moduleDetector = new ModuleDetector();
        List<ModuleDetector.SourceRoot> javaSourceRoots = moduleDetector.detectModules(root);

        if (!javaSourceRoots.isEmpty()) {
            // Collect Java files from detected source roots
            for (ModuleDetector.SourceRoot sourceRoot : javaSourceRoots) {
                collectFilesFromDirectoryStatic(sourceRoot.getPath(), Set.of("java"), sourceFiles);
            }
        }

        // For non-Java files, walk the entire project tree
        Set<String> nonJavaExtensions = extensions.stream()
            .filter(ext -> !ext.equals("java"))
            .collect(Collectors.toSet());

        if (!nonJavaExtensions.isEmpty()) {
            walkProjectForFilesStatic(root, nonJavaExtensions, sourceFiles);
        }

        return sourceFiles;
    }

    private static void collectFilesFromDirectoryStatic(Path dir, Set<String> extensions, List<Path> sourceFiles) {
        if (!Files.isDirectory(dir)) {
            return;
        }

        try {
            Files.walkFileTree(dir, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        String ext = fileName.substring(dotIndex + 1);
                        if (extensions.contains(ext)) {
                            sourceFiles.add(file);
                        }
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("Warning: Failed to walk directory " + dir + ": " + e.getMessage());
        }
    }

    private static void walkProjectForFilesStatic(Path root, Set<String> extensions, List<Path> sourceFiles) {
        try {
            Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                    // Skip common non-source directories
                    String dirName = dir.getFileName().toString();
                    if (dirName.equals("node_modules") || dirName.equals(".git") ||
                        dirName.equals("target") || dirName.equals("build") ||
                        dirName.equals("dist") || dirName.equals(".gradle")) {
                        return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        String ext = fileName.substring(dotIndex + 1);
                        if (extensions.contains(ext)) {
                            sourceFiles.add(file);
                        }
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("Warning: Failed to walk project tree: " + e.getMessage());
        }
    }
}
