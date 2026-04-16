package com.archon.core.output;

import com.archon.core.analysis.CentralityService;
import com.archon.core.analysis.FullAnalysisData;
import com.archon.core.analysis.ImpactResult;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.Node;
import com.archon.core.graph.RiskLevel;
import com.archon.core.plugin.BlindSpot;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Produces compressed JSON output for AI agents with tiered auto-scaling.
 *
 * Tier 1 (<200 nodes): Full graph with indexed edges (~9KB for 98 nodes).
 * Node format: [id, domainIdx, pageRank*10000, risk, bridge, hotspot]
 * Edge format: [srcIdx, tgtIdx]
 *
 * Tier 2 (200-500 nodes): Summary + top hotspots with PageRank (~5KB).
 * Tier 3 (500+ nodes): Summary with capped lists (~5KB).
 *
 * Target mode (--target --format agent): Tier 1 scoped to impact subgraph.
 */
public class AgentOutputFormatter {

    private static final String VERSION = "1.0.0";
    private static final int TIER1_MAX = 200;
    private static final int TIER2_MAX = 500;
    private static final int TIER3_MAX_HOTSPOTS = 20;
    private static final int TIER3_MAX_BRIDGES = 30;

    /**
     * Format analysis results as compressed JSON for AI agents.
     * Auto-selects tier based on graph size.
     */
    public String format(DependencyGraph graph,
                         Map<String, String> domainMap,
                         List<List<String>> cycles,
                         List<Node> hotspots,
                         List<BlindSpot> blindSpots,
                         String projectPath) {
        CentralityService centralityService = new CentralityService(graph);
        FullAnalysisData analysis = centralityService.computeFullAnalysis();

        int nodeCount = graph.nodeCount();
        if (nodeCount < TIER1_MAX) {
            return formatTier1(graph, domainMap, cycles, hotspots, blindSpots, analysis);
        } else if (nodeCount < TIER2_MAX) {
            return formatTier2(graph, domainMap, cycles, hotspots, blindSpots, analysis, false);
        } else {
            return formatTier2(graph, domainMap, cycles, hotspots, blindSpots, analysis, true);
        }
    }

    /**
     * Format focused impact analysis as Tier 1 JSON scoped to the impact subgraph.
     * Includes target + downstream dependents (from impact) + upstream ancestors to depth 3.
     */
    public String formatTarget(DependencyGraph graph,
                               Map<String, String> domainMap,
                               ImpactResult impact,
                               String targetId,
                               FullAnalysisData analysis) {
        // Collect relevant node IDs: target + all impacted nodes (downstream)
        Set<String> relevantIds = new LinkedHashSet<>();
        relevantIds.add(targetId);
        for (ImpactResult.ImpactNode node : impact.getImpactedNodes()) {
            relevantIds.add(node.getNodeId());
        }

        // Also include ancestors (what target depends on) up to depth 3
        Set<String> ancestors = new LinkedHashSet<>();
        Set<String> frontier = Set.of(targetId);
        for (int d = 0; d < 3; d++) {
            Set<String> nextFrontier = new LinkedHashSet<>();
            for (String nodeId : frontier) {
                for (String dep : graph.getDependencies(nodeId)) {
                    if (!relevantIds.contains(dep) && !ancestors.contains(dep)) {
                        ancestors.add(dep);
                        nextFrontier.add(dep);
                    }
                }
            }
            frontier = nextFrontier;
        }
        relevantIds.addAll(ancestors);

        // Filter to nodes that exist in the graph
        List<String> nodeIds = relevantIds.stream()
            .filter(graph::containsNode)
            .collect(Collectors.toList());

        // Build domain list
        List<String> domains = new ArrayList<>(new LinkedHashSet<>(
            nodeIds.stream()
                .map(id -> domainMap != null ? domainMap.getOrDefault(id, "unknown") : "unknown")
                .collect(Collectors.toList())));
        Map<String, Integer> domainIndex = new HashMap<>();
        for (int i = 0; i < domains.size(); i++) {
            domainIndex.put(domains.get(i), i);
        }

        // Build hotspot set from impact risk levels
        Set<String> hotspotIds = new HashSet<>();
        for (ImpactResult.ImpactNode impNode : impact.getImpactedNodes()) {
            if (impNode.getRisk().ordinal() >= RiskLevel.HIGH.ordinal()) {
                hotspotIds.add(impNode.getNodeId());
            }
        }

        // Build node index
        Map<String, Integer> nodeIndex = new HashMap<>();
        for (int i = 0; i < nodeIds.size(); i++) {
            nodeIndex.put(nodeIds.get(i), i);
        }

        Set<String> bridgeEdges = analysis.getBridges();

        // Collect edges between relevant nodes
        List<int[]> edges = new ArrayList<>();
        for (String src : nodeIds) {
            for (String tgt : graph.getDependencies(src)) {
                if (nodeIndex.containsKey(tgt)) {
                    edges.add(new int[]{nodeIndex.get(src), nodeIndex.get(tgt)});
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"v\":\"").append(VERSION).append("\"");
        sb.append(",\"target\":\"").append(escapeJson(targetId)).append("\"");
        sb.append(",\"n\":").append(nodeIds.size());
        sb.append(",\"depth\":").append(impact.getMaxDepthReached());
        sb.append(",\"e\":").append(edges.size());
        sb.append(",\"affected\":").append(impact.getTotalAffected());

        // Domains
        sb.append(",\"domains\":[");
        for (int i = 0; i < domains.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(domains.get(i))).append("\"");
        }
        sb.append("]");

        // Nodes: [id, domainIdx, pageRank*10000, risk, bridge, hotspot]
        sb.append(",\"nodes\":[");
        for (int i = 0; i < nodeIds.size(); i++) {
            if (i > 0) sb.append(",");
            String nodeId = nodeIds.get(i);
            int domainIdx = domainIndex.getOrDefault(
                domainMap != null ? domainMap.getOrDefault(nodeId, "unknown") : "unknown", 0);
            double pr = analysis.getPageRank().getOrDefault(nodeId, 0.0);
            int prScaled = (int) Math.round(pr * 10000);

            int risk = computeRiskLevel(graph, nodeId, hotspotIds);
            int bridge = isBridgeNode(bridgeEdges, nodeId) ? 1 : 0;
            int hotspot = hotspotIds.contains(nodeId) ? 1 : 0;

            sb.append("[\"").append(escapeJson(nodeId)).append("\",")
              .append(domainIdx).append(",")
              .append(prScaled).append(",")
              .append(risk).append(",")
              .append(bridge).append(",")
              .append(hotspot).append("]");
        }
        sb.append("]");

        // Edges: [srcIdx, tgtIdx]
        sb.append(",\"edges\":[");
        for (int i = 0; i < edges.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("[").append(edges.get(i)[0]).append(",").append(edges.get(i)[1]).append("]");
        }
        sb.append("]");

        // Bridges in subgraph
        sb.append(",\"bridges\":[");
        int bc = 0;
        for (String be : bridgeEdges) {
            String[] parts = be.split("->");
            if (parts.length == 2 && nodeIndex.containsKey(parts[0]) && nodeIndex.containsKey(parts[1])) {
                if (bc > 0) sb.append(",");
                sb.append("[").append(nodeIndex.get(parts[0])).append(",").append(nodeIndex.get(parts[1])).append("]");
                bc++;
            }
        }
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    // --- Tier 1: full graph with indexed edges ---

    private String formatTier1(DependencyGraph graph,
                               Map<String, String> domainMap,
                               List<List<String>> cycles,
                               List<Node> hotspots,
                               List<BlindSpot> blindSpots,
                               FullAnalysisData analysis) {
        // Build sorted domain list
        List<String> domains;
        if (domainMap != null && !domainMap.isEmpty()) {
            domains = domainMap.values().stream()
                .distinct().sorted().collect(Collectors.toList());
        } else {
            domains = Collections.singletonList("unknown");
        }
        Map<String, Integer> domainIndex = new HashMap<>();
        for (int i = 0; i < domains.size(); i++) {
            domainIndex.put(domains.get(i), i);
        }

        // Build hotspot set
        Set<String> hotspotIds = hotspots.stream()
            .map(Node::getId).collect(Collectors.toSet());

        // Build bridge node set
        Set<String> bridgeNodes = extractBridgeNodes(analysis.getBridges());

        // Build node index (sorted for deterministic output)
        List<String> nodeIds = graph.getNodeIds().stream().sorted().collect(Collectors.toList());
        Map<String, Integer> nodeIndex = new HashMap<>();
        for (int i = 0; i < nodeIds.size(); i++) {
            nodeIndex.put(nodeIds.get(i), i);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"v\":\"").append(VERSION).append("\"");
        sb.append(",\"n\":").append(graph.nodeCount());
        sb.append(",\"e\":").append(graph.edgeCount());
        sb.append(",\"cc\":").append(analysis.getConnectedComponents());

        // Domains
        sb.append(",\"domains\":[");
        for (int i = 0; i < domains.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(domains.get(i))).append("\"");
        }
        sb.append("]");

        // Nodes
        sb.append(",\"nodes\":[");
        for (int i = 0; i < nodeIds.size(); i++) {
            if (i > 0) sb.append(",");
            String nodeId = nodeIds.get(i);
            appendNodeArray(sb, graph, nodeId, domainMap, domainIndex,
                           hotspotIds, bridgeNodes, analysis.getPageRank());
        }
        sb.append("]");

        // Edges
        sb.append(",\"edges\":[");
        List<Edge> allEdges = new ArrayList<>(graph.getAllEdges());
        for (int i = 0; i < allEdges.size(); i++) {
            if (i > 0) sb.append(",");
            Edge edge = allEdges.get(i);
            Integer srcIdx = nodeIndex.get(edge.getSource());
            Integer tgtIdx = nodeIndex.get(edge.getTarget());
            if (srcIdx != null && tgtIdx != null) {
                sb.append("[").append(srcIdx).append(",").append(tgtIdx).append("]");
            } else {
                sb.append("[-1,-1]");
            }
        }
        sb.append("]");

        // Bridges
        sb.append(",\"bridges\":[");
        appendBridgeIndices(sb, analysis.getBridges(), nodeIndex);
        sb.append("]");

        // Blind spots
        sb.append(",\"bs\":{");
        appendBlindSpotCounts(sb, blindSpots);
        sb.append("}");

        // Cycles
        sb.append(",\"cycles\":[");
        appendCycleArrays(sb, cycles);
        sb.append("]");

        sb.append("}");
        return sb.toString();
    }

    // --- Tier 2/3: summary + hotspots ---

    private String formatTier2(DependencyGraph graph,
                               Map<String, String> domainMap,
                               List<List<String>> cycles,
                               List<Node> hotspots,
                               List<BlindSpot> blindSpots,
                               FullAnalysisData analysis,
                               boolean cap) {
        int maxHotspots = cap ? TIER3_MAX_HOTSPOTS : Integer.MAX_VALUE;
        int maxBridges = cap ? TIER3_MAX_BRIDGES : Integer.MAX_VALUE;

        StringBuilder sb = new StringBuilder();
        sb.append("{\"v\":\"").append(VERSION).append("\"");
        sb.append(",\"n\":").append(graph.nodeCount());
        sb.append(",\"e\":").append(graph.edgeCount());
        sb.append(",\"cc\":").append(analysis.getConnectedComponents());

        // Domains with counts
        sb.append(",\"domains\":{");
        if (domainMap != null && !domainMap.isEmpty()) {
            Map<String, Long> domainCounts = domainMap.values().stream()
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()));
            int dc = 0;
            for (Map.Entry<String, Long> entry : domainCounts.entrySet()) {
                if (dc > 0) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey())).append("\":").append(entry.getValue());
                dc++;
            }
        }
        sb.append("}");

        // Hotspots with PageRank
        sb.append(",\"hotspots\":[");
        List<Node> topHotspots = hotspots.stream().limit(maxHotspots).collect(Collectors.toList());
        for (int i = 0; i < topHotspots.size(); i++) {
            if (i > 0) sb.append(",");
            Node node = topHotspots.get(i);
            double pr = analysis.getPageRank().getOrDefault(node.getId(), 0.0);
            int dependents = graph.getDependents(node.getId()).size();
            sb.append("{\"id\":\"").append(escapeJson(node.getId())).append("\"");
            sb.append(",\"pr\":").append(pr);
            sb.append(",\"dependents\":").append(dependents);
            sb.append("}");
        }
        sb.append("]");

        // Bridges as string pairs (capped)
        sb.append(",\"bridges\":[");
        int bc = 0;
        for (String be : analysis.getBridges()) {
            if (bc >= maxBridges) break;
            if (bc > 0) sb.append(",");
            sb.append("\"").append(escapeJson(be)).append("\"");
            bc++;
        }
        sb.append("]");

        // Blind spots
        sb.append(",\"bs\":{");
        appendBlindSpotCounts(sb, blindSpots);
        sb.append("}");

        // Cycles (capped at 10)
        sb.append(",\"cycles\":[");
        int cycleCap = 10;
        for (int i = 0; i < Math.min(cycles.size(), cycleCap); i++) {
            if (i > 0) sb.append(",");
            List<String> cycle = cycles.get(i);
            String arrowCycle = cycle.stream()
                .map(this::shortName)
                .collect(Collectors.joining("->"));
            sb.append("[\"").append(escapeJson(arrowCycle)).append("\",").append(cycle.size()).append("]");
        }
        sb.append("]");

        if (cap) {
            sb.append(",\"hint\":\"Use --target <class> --format agent for focused impact analysis\"");
        }

        sb.append("}");
        return sb.toString();
    }

    // --- Helpers ---

    private void appendNodeArray(StringBuilder sb, DependencyGraph graph,
                                  String nodeId, Map<String, String> domainMap,
                                  Map<String, Integer> domainIndex,
                                  Set<String> hotspotIds, Set<String> bridgeNodes,
                                  Map<String, Double> pageRank) {
        int domainIdx = domainIndex.getOrDefault(
            domainMap != null ? domainMap.getOrDefault(nodeId, "unknown") : "unknown", 0);
        double pr = pageRank.getOrDefault(nodeId, 0.0);
        int prScaled = (int) Math.round(pr * 10000);

        Node n = graph.getNode(nodeId).orElse(null);
        int risk;
        if (hotspotIds.contains(nodeId)) {
            risk = 2;
        } else if (n != null && n.getInDegree() > 2) {
            risk = 1;
        } else {
            risk = 0;
        }

        int bridge = bridgeNodes.contains(nodeId) ? 1 : 0;
        int hotspot = hotspotIds.contains(nodeId) ? 1 : 0;

        sb.append("[\"").append(escapeJson(nodeId)).append("\",")
          .append(domainIdx).append(",")
          .append(prScaled).append(",")
          .append(risk).append(",")
          .append(bridge).append(",")
          .append(hotspot).append("]");
    }

    private void appendBridgeIndices(StringBuilder sb, Set<String> bridges,
                                      Map<String, Integer> nodeIndex) {
        int bc = 0;
        for (String be : bridges) {
            String[] parts = be.split("->");
            if (parts.length == 2) {
                Integer srcIdx = nodeIndex.get(parts[0]);
                Integer tgtIdx = nodeIndex.get(parts[1]);
                if (srcIdx != null && tgtIdx != null) {
                    if (bc > 0) sb.append(",");
                    sb.append("[").append(srcIdx).append(",").append(tgtIdx).append("]");
                    bc++;
                }
            }
        }
    }

    private void appendBlindSpotCounts(StringBuilder sb, List<BlindSpot> blindSpots) {
        if (blindSpots == null || blindSpots.isEmpty()) return;
        Map<String, Long> bsCounts = blindSpots.stream()
            .collect(Collectors.groupingBy(BlindSpot::getType, Collectors.counting()));
        int bsc = 0;
        for (Map.Entry<String, Long> entry : bsCounts.entrySet()) {
            if (bsc > 0) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\":").append(entry.getValue());
            bsc++;
        }
    }

    private void appendCycleArrays(StringBuilder sb, List<List<String>> cycles) {
        for (int i = 0; i < cycles.size(); i++) {
            if (i > 0) sb.append(",");
            List<String> cycle = cycles.get(i);
            sb.append("[");
            for (int j = 0; j < cycle.size(); j++) {
                if (j > 0) sb.append(",");
                sb.append("\"").append(escapeJson(cycle.get(j))).append("\"");
            }
            sb.append("]");
        }
    }

    private int computeRiskLevel(DependencyGraph graph, String nodeId, Set<String> hotspotIds) {
        if (hotspotIds.contains(nodeId)) return 2;
        Node n = graph.getNode(nodeId).orElse(null);
        if (n != null && n.getInDegree() > 2) return 1;
        return 0;
    }

    private boolean isBridgeNode(Set<String> bridgeEdges, String nodeId) {
        for (String be : bridgeEdges) {
            String[] parts = be.split("->");
            if (parts.length == 2 && (parts[0].equals(nodeId) || parts[1].equals(nodeId))) {
                return true;
            }
        }
        return false;
    }

    private Set<String> extractBridgeNodes(Set<String> bridgeEdges) {
        Set<String> nodes = new HashSet<>();
        for (String be : bridgeEdges) {
            String[] parts = be.split("->");
            if (parts.length == 2) {
                nodes.add(parts[0]);
                nodes.add(parts[1]);
            }
        }
        return nodes;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String shortName(String fqcn) {
        int lastSlash = fqcn.lastIndexOf('/');
        if (lastSlash >= 0) {
            return fqcn.substring(lastSlash + 1);
        }
        int lastDot = fqcn.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fqcn.length() - 1) {
            String candidate = fqcn.substring(lastDot + 1);
            if (!candidate.isEmpty() && Character.isUpperCase(candidate.charAt(0))) {
                return candidate;
            }
        }
        return fqcn;
    }
}
