package com.archon.core.analysis;

import com.archon.core.config.ArchonConfig;
import com.archon.core.coordination.ParseOrchestrator;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;
import com.archon.core.plugin.BlindSpot;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.archon.core.plugin.PluginDiscoverer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared analysis pipeline used by both AnalyzeCommand and ViewCommand.
 * Encapsulates plugin discovery, source collection, parsing, and computation.
 */
public class AnalysisPipeline {

    private static final Logger LOGGER = Logger.getLogger(AnalysisPipeline.class.getName());
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
        "node_modules",
        ".git",
        "target",
        "build",
        "dist",
        ".gradle"
    );

    /**
     * Run complete analysis pipeline on a project root.
     *
     * @param root project root path
     * @param config archon configuration (uses defaults if null)
     * @return AnalysisResult with all computed data
     */
    public static AnalysisResult run(Path root, ArchonConfig config) {
        if (config == null) {
            config = ArchonConfig.loadOrDefault(root.resolve(".archon.yml"));
        }

        // Discover plugins
        PluginDiscoverer discoverer = new PluginDiscoverer();
        List<LanguagePlugin> plugins = discoverer.discoverWithConflictCheck();

        if (plugins.isEmpty()) {
            throw new IllegalStateException("No language plugins found. Ensure plugin JARs are on the classpath.");
        }

        // Collect all file extensions from plugins
        Set<String> extensions = plugins.stream()
            .flatMap(p -> p.fileExtensions().stream())
            .collect(Collectors.toSet());

        // Collect all source files
        List<Path> sourceFiles = collectSourceFiles(root, extensions);

        if (sourceFiles.isEmpty()) {
            return new AnalysisResult(
                new DependencyGraph.MutableBuilder().build(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                ThresholdCalculator.calculate(0, 0)
            );
        }

        // Reset any plugin state before parsing
        plugins.forEach(LanguagePlugin::reset);

        // Parse
        ParseOrchestrator orchestrator = new ParseOrchestrator(plugins);
        ParseContext context = new ParseContext(root, extensions);
        ParseResult result = orchestrator.parse(sourceFiles, context);

        DependencyGraph graph = result.getGraph();

        // Domain detection
        DomainDetector domainDetector = new DomainDetector();
        DomainResult domainResult = domainDetector.assignDomains(graph, config.getDomains());
        Map<String, String> domainMap = domainResult.getDomains();

        // Cycle detection
        CycleDetector cycleDetector = new CycleDetector();
        List<List<String>> cycles = cycleDetector.detectCycles(graph);

        // Coupling hotspots
        CouplingAnalyzer couplingAnalyzer = new CouplingAnalyzer();
        long distinctDomains = domainMap.values().stream().distinct().count();
        Thresholds thresholds = ThresholdCalculator.calculate(graph.nodeCount(), (int) distinctDomains);
        List<Node> hotspots = couplingAnalyzer.findHotspots(graph, thresholds.getCouplingThreshold());

        // Blind spots
        List<BlindSpot> blindSpots = result.getBlindSpots();

        return new AnalysisResult(
            graph,
            domainMap,
            cycles,
            hotspots,
            blindSpots,
            thresholds
        );
    }

    /**
     * Run complete analysis pipeline on a project root with default config.
     *
     * @param root project root path
     * @return AnalysisResult with all computed data
     */
    public static AnalysisResult run(Path root) {
        return run(root, null);
    }

    /**
     * Collect all source files from the project root matching the given extensions.
     * Uses ModuleDetector for Java projects and walks the tree for other files.
     */
    private static List<Path> collectSourceFiles(Path root, Set<String> extensions) {
        List<Path> sourceFiles = new ArrayList<>();

        // Use ModuleDetector to find Java source roots (only if "java" in extensions)
        if (extensions.contains("java")) {
            try {
                // Try to use ModuleDetector from archon-java module
                Class<?> moduleDetectorClass = Class.forName("com.archon.java.ModuleDetector");
                Object moduleDetector = moduleDetectorClass.getDeclaredConstructor().newInstance();
                java.lang.reflect.Method detectMethod = moduleDetectorClass.getMethod("detectModules", Path.class);
                Object sourceRoots = detectMethod.invoke(moduleDetector, root);

                if (sourceRoots instanceof List<?> javaSourceRoots && !javaSourceRoots.isEmpty()) {
                    Class<?> sourceRootClass = Class.forName("com.archon.java.ModuleDetector$SourceRoot");
                    java.lang.reflect.Method getPathMethod = sourceRootClass.getMethod("getPath");

                    for (Object sourceRoot : javaSourceRoots) {
                        Path sourceRootPath = (Path) getPathMethod.invoke(sourceRoot);
                        collectFilesFromDirectory(sourceRootPath, Set.of("java"), sourceFiles);
                    }
                }
            } catch (ReflectiveOperationException e) {
                // Fall back to tree walk if ModuleDetector is not available (archon-core doesn't depend on archon-java)
                walkProjectForFiles(root, extensions, sourceFiles);
            }
        }

        // For non-Java files, walk the entire project tree
        Set<String> nonJavaExtensions = extensions.stream()
            .filter(ext -> !ext.equals("java"))
            .collect(Collectors.toSet());

        if (!nonJavaExtensions.isEmpty()) {
            walkProjectForFiles(root, nonJavaExtensions, sourceFiles);
        }

        return sourceFiles;
    }

    private static void collectFilesFromDirectory(Path dir, Set<String> extensions, List<Path> sourceFiles) {
        if (!java.nio.file.Files.isDirectory(dir)) {
            return;
        }

        try {
            java.nio.file.Files.walkFileTree(dir, new java.nio.file.SimpleFileVisitor<Path>() {
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
        } catch (java.io.IOException e) {
            LOGGER.log(Level.WARNING, "Cannot read directory: " + dir + ", skipping", e);
        }
    }

    private static void walkProjectForFiles(Path root, Set<String> extensions, List<Path> sourceFiles) {
        try {
            java.nio.file.Files.walkFileTree(root, new java.nio.file.SimpleFileVisitor<Path>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (EXCLUDED_DIRECTORIES.contains(dirName)) {
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
        } catch (java.io.IOException e) {
            LOGGER.log(Level.WARNING, "Cannot walk project tree: " + root + ", skipping", e);
        }
    }
}
