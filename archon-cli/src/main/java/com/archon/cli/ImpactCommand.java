package com.archon.cli;

import com.archon.core.analysis.DomainDetector;
import com.archon.core.analysis.DomainResult;
import com.archon.core.analysis.ImpactPropagator;
import com.archon.core.analysis.ImpactResult;
import com.archon.core.config.ArchonConfig;
import com.archon.core.graph.DependencyGraph;
import com.archon.java.JavaParserPlugin;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
    name = "impact",
    description = "Impact analysis for a specific target class",
    mixinStandardHelpOptions = true
)
public class ImpactCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Target class (FQCN or short name)")
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

        // Parse
        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult result = plugin.parse(root, config);
        DependencyGraph graph = result.getGraph();

        // Resolve short name to FQCN
        String fqcn = resolveTarget(graph, target);
        if (fqcn == null) {
            System.err.println("Error: class not found: " + target);
            System.err.println("Available classes:");
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
            String riskTag = colorRisk(node.getRisk());
            System.out.println(indent + "L" + node.getDepth() + " " + node.getNodeId()
                + domainTag + " " + riskTag);
        }

        return 0;
    }

    private String resolveTarget(DependencyGraph graph, String target) {
        if (graph.containsNode(target)) {
            return target;
        }
        // Try suffix match (short class name)
        for (String nodeId : graph.getNodeIds()) {
            if (nodeId.endsWith("." + target)) {
                return nodeId;
            }
        }
        return null;
    }

    private String colorRisk(com.archon.core.graph.RiskLevel risk) {
        return switch (risk) {
            case LOW -> "\u001B[32m" + risk + "\u001B[0m";
            case MEDIUM -> "\u001B[33m" + risk + "\u001B[0m";
            case HIGH, VERY_HIGH, BLOCKED -> "\u001B[31m" + risk + "\u001B[0m";
        };
    }
}
