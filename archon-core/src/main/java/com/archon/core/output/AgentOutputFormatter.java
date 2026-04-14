package com.archon.core.output;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.Node;
import com.archon.core.plugin.BlindSpot;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Produces compressed, structured text output for AI agents.
 * Target: <=200 lines for a 500-node project.
 * No ANSI color codes -- agents parse plain text.
 */
public class AgentOutputFormatter {

    private static final int MAX_HOTSPOTS = 5;
    private static final int MAX_CYCLES = 10;
    private static final int MAX_DOMAIN_PAIRS = 5;
    private static final int MAX_DEPENDENTS_SHOWN = 5;

    /**
     * Format analysis results as compressed, structured text for AI agents.
     *
     * @param graph       the dependency graph
     * @param domainMap   node ID to domain name mapping
     * @param cycles      detected cycles
     * @param hotspots    hotspot nodes (sorted by in-degree desc)
     * @param blindSpots  detected blind spots
     * @param projectPath the project root path
     * @return formatted string output
     */
    public String format(DependencyGraph graph,
                         Map<String, String> domainMap,
                         List<List<String>> cycles,
                         List<Node> hotspots,
                         List<BlindSpot> blindSpots,
                         String projectPath) {
        StringBuilder sb = new StringBuilder();

        // Header
        appendHeader(sb, graph, domainMap, projectPath);

        // Hotspots
        appendHotspots(sb, graph, hotspots, domainMap);

        // Cycles
        appendCycles(sb, cycles);

        // Domain coupling
        appendDomainCoupling(sb, graph, domainMap);

        // Blind spots
        appendBlindSpots(sb, blindSpots);

        // Footer
        sb.append("\nRun `archon diff` before committing to see blast radius.\n");

        return sb.toString();
    }

    private void appendHeader(StringBuilder sb, DependencyGraph graph,
                               Map<String, String> domainMap, String projectPath) {
        String displayPath = projectPath;
        if (displayPath == null || displayPath.equals(".")) {
            displayPath = "./";
        }

        // Count nodes by language/type from sourcePath extension
        Map<String, Integer> langCounts = countByLanguage(graph);

        sb.append("Archon Analysis: ").append(displayPath).append("\n");

        // Language line: "Languages: Vue (187), TypeScript (203), JavaScript (99)"
        if (!langCounts.isEmpty()) {
            String langLine = langCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
            sb.append("Languages: ").append(langLine).append("\n");
        }

        long distinctDomains = domainMap != null
            ? domainMap.values().stream().distinct().count()
            : 0;

        double edgeNodeRatio = graph.nodeCount() > 0
            ? (double) graph.edgeCount() / graph.nodeCount()
            : 0;

        sb.append("Graph: ")
          .append(graph.nodeCount()).append(" nodes, ")
          .append(graph.edgeCount()).append(" edges (")
          .append(String.format("%.2f", edgeNodeRatio)).append(" e/n)");

        if (distinctDomains > 0) {
            sb.append(" | ").append(distinctDomains).append(" domains");
        }
        sb.append("\n");
    }

    private void appendHotspots(StringBuilder sb, DependencyGraph graph,
                                List<Node> hotspots, Map<String, String> domainMap) {
        sb.append("\nHOTSPOTS");
        if (hotspots.isEmpty()) {
            sb.append(": none\n");
            return;
        }
        sb.append(" (top ").append(Math.min(MAX_HOTSPOTS, hotspots.size()))
          .append(" by in-degree):\n");

        int count = 0;
        for (Node node : hotspots) {
            if (count >= MAX_HOTSPOTS) break;
            count++;

            String nodeId = node.getId();
            String shortName = shortName(nodeId);
            int inDegree = node.getInDegree();

            // Get dependents (nodes that depend on this node)
            Set<String> dependents = graph.getDependents(nodeId);

            // Count distinct domains among dependents
            long dependentDomains = dependents.stream()
                .filter(dep -> domainMap != null && domainMap.containsKey(dep))
                .map(dep -> domainMap.get(dep))
                .distinct()
                .count();

            sb.append(count).append(". ").append(shortName);

            if (dependentDomains > 1) {
                sb.append(" — cross-domain hub (in: ").append(inDegree)
                  .append(", out: ").append(node.getOutDegree()).append(")");
            } else if (dependentDomains == 1 && domainMap != null) {
                // Count how many domains this node's dependents span
                long depDomainCount = domainMap.containsKey(nodeId)
                    ? dependents.stream()
                        .filter(domainMap::containsKey)
                        .map(domainMap::get)
                        .filter(d -> !d.equals(domainMap.get(nodeId)))
                        .distinct()
                        .count()
                    : 0;

                if (depDomainCount > 0) {
                    sb.append(" — depended on by ").append(inDegree)
                      .append(" nodes across ").append(depDomainCount + 1).append(" domains");
                } else {
                    sb.append(" — depended on by ").append(inDegree).append(" nodes");
                }
            } else {
                sb.append(" — depended on by ").append(inDegree).append(" nodes");
            }
            sb.append("\n");

            // Show top dependents
            if (!dependents.isEmpty()) {
                List<String> depNames = dependents.stream()
                    .limit(MAX_DEPENDENTS_SHOWN)
                    .map(this::shortName)
                    .collect(Collectors.toList());
                sb.append("   <- ").append(String.join(", ", depNames));
                if (dependents.size() > MAX_DEPENDENTS_SHOWN) {
                    sb.append(", +").append(dependents.size() - MAX_DEPENDENTS_SHOWN).append(" more");
                }
                sb.append("\n");
            }
        }
    }

    private void appendCycles(StringBuilder sb, List<List<String>> cycles) {
        sb.append("\nCYCLES");
        if (cycles.isEmpty()) {
            sb.append(": none detected\n");
            return;
        }
        sb.append(" (").append(cycles.size()).append(" detected):\n");

        int count = 0;
        for (List<String> cycle : cycles) {
            if (count >= MAX_CYCLES) break;
            count++;

            String arrowCycle = cycle.stream()
                .map(this::shortName)
                .collect(Collectors.joining(" -> "));
            sb.append("- ").append(arrowCycle).append(" -> ")
              .append(shortName(cycle.get(0)))
              .append(" (").append(cycle.size()).append(" nodes)\n");
        }
        if (cycles.size() > MAX_CYCLES) {
            sb.append("  ... and ").append(cycles.size() - MAX_CYCLES).append(" more cycles\n");
        }
    }

    private void appendDomainCoupling(StringBuilder sb, DependencyGraph graph,
                                       Map<String, String> domainMap) {
        if (domainMap == null || domainMap.isEmpty()) {
            return;
        }

        // Count edges between each domain pair
        Map<String, Integer> pairCounts = new LinkedHashMap<>();
        for (Edge edge : graph.getAllEdges()) {
            String srcDomain = domainMap.getOrDefault(edge.getSource(), "unknown");
            String tgtDomain = domainMap.getOrDefault(edge.getTarget(), "unknown");
            if (!srcDomain.equals(tgtDomain)) {
                // Normalize pair key so A-B and B-A are counted together
                String pairKey = srcDomain.compareTo(tgtDomain) < 0
                    ? srcDomain + " <-> " + tgtDomain
                    : tgtDomain + " <-> " + srcDomain;
                pairCounts.merge(pairKey, 1, Integer::sum);
            }
        }

        if (pairCounts.isEmpty()) {
            return;
        }

        sb.append("\nDOMAIN COUPLING:\n");

        pairCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(MAX_DOMAIN_PAIRS)
            .forEach(e -> sb.append("- ").append(e.getKey())
                .append(": ").append(e.getValue()).append(" edges\n"));

        if (pairCounts.size() > MAX_DOMAIN_PAIRS) {
            sb.append("  ... and ").append(pairCounts.size() - MAX_DOMAIN_PAIRS)
              .append(" more pairs\n");
        }
    }

    private void appendBlindSpots(StringBuilder sb, List<BlindSpot> blindSpots) {
        sb.append("\nBLIND SPOTS");
        if (blindSpots == null || blindSpots.isEmpty()) {
            sb.append(": none\n");
            return;
        }

        // Group by type, count occurrences
        Map<String, List<BlindSpot>> byType = blindSpots.stream()
            .collect(Collectors.groupingBy(BlindSpot::getType, LinkedHashMap::new, Collectors.toList()));

        sb.append(":\n");
        for (Map.Entry<String, List<BlindSpot>> entry : byType.entrySet()) {
            String type = entry.getKey();
            List<BlindSpot> spots = entry.getValue();
            // Use the first spot's description as representative
            String desc = spots.get(0).getDescription();
            sb.append("- ").append(spots.size()).append(" ").append(type);
            if (desc != null && !desc.isEmpty()) {
                sb.append(": ").append(desc);
            }
            sb.append("\n");
        }
    }

    /**
     * Count nodes by language inferred from source path extension or node ID pattern.
     */
    private Map<String, Integer> countByLanguage(DependencyGraph graph) {
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (String nodeId : graph.getNodeIds()) {
            String lang = inferLanguage(nodeId, graph);
            counts.merge(lang, 1, Integer::sum);
        }

        return counts;
    }

    private String inferLanguage(String nodeId, DependencyGraph graph) {
        // Try sourcePath first
        Optional<Node> nodeOpt = graph.getNode(nodeId);
        if (nodeOpt.isPresent()) {
            String sourcePath = nodeOpt.get().getSourcePath().orElse(null);
            if (sourcePath != null && !sourcePath.isEmpty()) {
                return extensionToLanguage(sourcePath);
            }
        }
        // Fall back to node ID heuristics
        return extensionToLanguage(nodeId);
    }

    private String extensionToLanguage(String path) {
        if (path.endsWith(".vue")) return "Vue";
        if (path.endsWith(".tsx")) return "TypeScript";
        if (path.endsWith(".ts")) return "TypeScript";
        if (path.endsWith(".jsx")) return "JavaScript";
        if (path.endsWith(".js")) return "JavaScript";
        if (path.endsWith(".py")) return "Python";
        if (path.endsWith(".java")) return "Java";
        // FQCN heuristic: dots with uppercase segments
        if (path.contains(".") && path.chars().filter(c -> c == '.').count() >= 2) {
            return "Java";
        }
        return "Unknown";
    }

    private String shortName(String fqcn) {
        // For Vue/JS paths: take last segment
        int lastSlash = fqcn.lastIndexOf('/');
        if (lastSlash >= 0) {
            return fqcn.substring(lastSlash + 1);
        }
        // For Java FQCN: take class name
        int lastDot = fqcn.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fqcn.length() - 1) {
            String candidate = fqcn.substring(lastDot + 1);
            // Only use short form if it looks like a class name (starts with uppercase)
            if (!candidate.isEmpty() && Character.isUpperCase(candidate.charAt(0))) {
                return candidate;
            }
        }
        return fqcn;
    }
}
