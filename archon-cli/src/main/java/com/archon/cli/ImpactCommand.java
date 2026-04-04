package com.archon.cli;

import com.archon.core.analysis.ArchLayer;
import com.archon.core.analysis.DomainDetector;
import com.archon.core.analysis.DomainResult;
import com.archon.core.analysis.ImpactPropagator;
import com.archon.core.analysis.ImpactResult;
import com.archon.core.config.ArchonConfig;
import com.archon.core.coordination.ParseOrchestrator;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.archon.core.plugin.PluginDiscoverer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
    name = "impact",
    description = "Impact analysis for a specific target module",
    mixinStandardHelpOptions = true
)
public class ImpactCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Target module (FQCN, path, or short name)")
    private String target;

    @Parameters(index = "1", description = "Path to the project root")
    private String projectPath;

    @Option(names = "--depth", defaultValue = "3", description = "Max propagation depth (default: 3)")
    private int maxDepth;

    @Override
    public Integer call() {
        Path root = Path.of(projectPath);
        if (!root.toFile().exists()) {
            System.err.println("Error: path does not exist: " + projectPath);
            return 1;
        }

        ArchonConfig config = ArchonConfig.loadOrDefault(root.resolve(".archon.yml"));

        // Discover plugins
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

        // Collect source files
        List<Path> sourceFiles = AnalyzeCommand.collectSourceFilesStatic(root, extensions);

        if (sourceFiles.isEmpty()) {
            System.out.println("No source files found. Check project path.");
            return 0;
        }

        // Reset any plugin state before parsing
        plugins.forEach(LanguagePlugin::reset);

        // Parse with orchestrator
        ParseOrchestrator orchestrator = new ParseOrchestrator(plugins);
        ParseContext context = new ParseContext(root, extensions);
        ParseResult result = orchestrator.parse(sourceFiles, context);
        DependencyGraph graph = result.getGraph();

        // Resolve short name to FQCN
        String fqcn = resolveTarget(graph, target);
        if (fqcn == null) {
            System.err.println("Error: module not found: " + target);
            System.err.println("Available modules:");
            graph.getNodeIds().stream().sorted().forEach(id -> System.err.println("  " + id));
            return 1;
        }

        // Domain detection
        DomainDetector domainDetector = new DomainDetector();
        DomainResult domainResult = domainDetector.assignDomains(graph, config.getDomains());
        Map<String, String> domainMap = domainResult.getDomains();

        // Propagate impact
        ImpactPropagator propagator = new ImpactPropagator();
        ImpactResult impact;
        try {
            impact = propagator.propagate(graph, fqcn, maxDepth, domainMap);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }

        // Display
        System.out.println("Impact analysis for: " + fqcn);
        System.out.println("Max depth: " + maxDepth);
        System.out.println();

        if (impact.getImpactedNodes().isEmpty()) {
            System.out.println("No downstream dependents found.");
            return 0;
        }

        System.out.println("Affected nodes: " + impact.getTotalAffected());
        System.out.println("Max depth reached: " + impact.getMaxDepthReached());
        System.out.println("Cross-domain edges: " + impact.getCrossDomainEdges());
        System.out.println();

        System.out.println("Impact tree:");
        for (ImpactResult.ImpactNode node : impact.getImpactedNodes()) {
            String indent = "  ".repeat(node.getDepth());
            String domain = node.getDomain().orElse("");
            String domainTag = domain.isEmpty() ? "" : " [" + domain + "]";
            String layerTag = " [" + node.getLayer() + "]";
            String riskTag = colorRisk(node.getRisk());
            System.out.println(indent + "L" + node.getDepth() + " " + node.getNodeId()
                + domainTag + layerTag + " " + riskTag);
        }

        // Layer breakdown
        Map<ArchLayer, Long> layerCounts = impact.getImpactedNodes().stream()
            .collect(Collectors.groupingBy(
                ImpactResult.ImpactNode::getLayer, Collectors.counting()));
        if (!layerCounts.isEmpty()) {
            System.out.println();
            System.out.println("Layer breakdown:");
            for (Map.Entry<ArchLayer, Long> entry : layerCounts.entrySet()) {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue());
            }
        }

        return 0;
    }

    // Package-private for testing
    String resolveTarget(DependencyGraph graph, String target) {
        // 1. Exact match (handles full node IDs with namespace prefix)
        if (graph.containsNode(target)) {
            return target;
        }

        // 2. Try matching with common separators
        for (String nodeId : graph.getNodeIds()) {
            // Strip namespace prefix for matching (js:stores/foo -> stores/foo)
            String unprefixed = stripNamespacePrefix(nodeId);

            // Java-style: com.example.RouterStore matches "RouterStore"
            if (unprefixed.endsWith("." + target)) {
                return nodeId;
            }
            // Path-style: stores/routerStore matches "routerStore"
            if (unprefixed.endsWith("/" + target)) {
                return nodeId;
            }
        }
        return null;
    }

    // Package-private for testing
    String stripNamespacePrefix(String nodeId) {
        int colonIndex = nodeId.indexOf(':');
        if (colonIndex > 0) {
            return nodeId.substring(colonIndex + 1);
        }
        return nodeId;
    }

    private String colorRisk(com.archon.core.graph.RiskLevel risk) {
        return switch (risk) {
            case LOW -> "\u001B[32m" + risk + "\u001B[0m";
            case MEDIUM -> "\u001B[33m" + risk + "\u001B[0m";
            case HIGH, VERY_HIGH, BLOCKED -> "\u001B[31m" + risk + "\u001B[0m";
        };
    }
}
