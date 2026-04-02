package com.archon.cli;

import com.archon.core.analysis.CouplingAnalyzer;
import com.archon.core.analysis.CycleDetector;
import com.archon.core.analysis.DomainDetector;
import com.archon.core.analysis.DomainResult;
import com.archon.core.config.ArchonConfig;
import com.archon.core.graph.BlindSpot;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;
import com.archon.java.JavaParserPlugin;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
    name = "analyze",
    description = "Full structural analysis of a Java project",
    mixinStandardHelpOptions = true
)
public class AnalyzeCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Path to the project root")
    private String projectPath;

    @Option(names = "--json", description = "Output machine-readable JSON")
    private boolean json;

    @Option(names = "--dot", description = "Export Graphviz DOT to file")
    private String dotFile;

    @Option(names = "--verbose", description = "Show detailed parsing logs")
    private boolean verbose;

    @Override
    public Integer call() {
        Path root = Path.of(projectPath);
        if (!root.toFile().exists()) {
            System.err.println("Error: path does not exist: " + projectPath);
            return 1;
        }

        ArchonConfig config = ArchonConfig.loadOrDefault(root.resolve(".archon.yml"));

        // Step 1: Parse
        System.out.println("Parsing " + root + " ...");
        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult result = plugin.parse(root, config);

        if (!result.getErrors().isEmpty()) {
            System.err.println(result.getErrors().size() + " file(s) failed to parse:");
            for (JavaParserPlugin.ParseError err : result.getErrors()) {
                System.err.println("  " + err.getFile() + ": " + err.getMessage());
            }
        }

        DependencyGraph graph = result.getGraph();
        System.out.println("Parsed " + graph.nodeCount() + " classes, " + graph.edgeCount() + " dependencies");

        // Step 2: Domain detection
        DomainDetector domainDetector = new DomainDetector();
        DomainResult domainResult = domainDetector.assignDomains(graph, config.getDomains());
        Map<String, String> domainMap = domainResult.getDomains();

        long distinctDomains = domainMap.values().stream().distinct().count();
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
        List<Node> hotspots = couplingAnalyzer.findHotspots(graph, 5);
        if (hotspots.isEmpty()) {
            System.out.println("\nCoupling hotspots: none (all in-degree <= 5)");
        } else {
            System.out.println("\n\u001B[33mCoupling hotspots (in-degree > 5):\u001B[0m");
            for (Node node : hotspots) {
                System.out.println("  " + node.getId() + " (in-degree: " + node.getInDegree() + ")");
            }
        }

        // Step 5: Blind spots
        List<BlindSpot> blindSpots = result.getBlindSpots();
        if (blindSpots.isEmpty()) {
            System.out.println("\nBlind spots: none");
        } else {
            System.out.println("\n\u001B[36mBlind spots (" + blindSpots.size() + "):\u001B[0m");
            for (BlindSpot bs : blindSpots) {
                System.out.println("  [" + bs.getType() + "] " + bs.getFile() + ":" + bs.getLine()
                    + " " + bs.getPattern());
            }
        }

        // Step 6: DOT export
        if (dotFile != null) {
            exportDot(graph, domainMap, dotFile);
            System.out.println("\nDOT exported to: " + dotFile);
        }

        // Summary
        System.out.println("\n--- Summary ---");
        System.out.println("Nodes:       " + graph.nodeCount());
        System.out.println("Edges:       " + graph.edgeCount());
        System.out.println("Cycles:      " + cycles.size());
        System.out.println("Hotspots:    " + hotspots.size());
        System.out.println("Blind spots: " + blindSpots.size());
        System.out.println("Errors:      " + result.getErrors().size());

        return (!cycles.isEmpty()) ? 1 : 0;
    }

    private void exportDot(DependencyGraph graph, Map<String, String> domainMap, String file) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph dependencies {\n");
        sb.append("  rankdir=LR;\n");
        for (String nodeId : graph.getNodeIds()) {
            String domain = domainMap.getOrDefault(nodeId, "");
            String label = nodeId.substring(nodeId.lastIndexOf('.') + 1);
            String color = domain.isEmpty() ? "white" : hashColor(domain);
            sb.append("  \"").append(nodeId).append("\" [label=\"").append(label)
                .append("\" style=filled fillcolor=\"").append(color).append("\"];\n");
        }
        for (String src : graph.getNodeIds()) {
            for (String tgt : graph.getDependencies(src)) {
                sb.append("  \"").append(src).append("\" -> \"").append(tgt).append("\";\n");
            }
        }
        sb.append("}\n");
        try {
            java.nio.file.Files.writeString(Path.of(file), sb.toString());
        } catch (Exception e) {
            System.err.println("Failed to write DOT file: " + e.getMessage());
        }
    }

    private String hashColor(String domain) {
        int hash = domain.hashCode();
        int r = Math.abs(hash % 128) + 100;
        int g = Math.abs((hash >> 8) % 128) + 100;
        int b = Math.abs((hash >> 16) % 128) + 100;
        return "#" + Integer.toHexString(r) + Integer.toHexString(g) + Integer.toHexString(b);
    }
}
