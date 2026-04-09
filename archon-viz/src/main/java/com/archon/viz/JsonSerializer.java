package com.archon.viz;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;
import com.archon.core.plugin.BlindSpot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serializes analysis results to JSON format for web viewer and CLI output.
 */
public class JsonSerializer {
    private static final String SCHEMA_VERSION = "archon-metadata-v1";
    private static final String OUTPUT_VERSION = "1.0.0";
    private static final int RISK_HIGH_FAN_THRESHOLD = 20;
    private static final int RISK_MEDIUM_FAN_THRESHOLD = 10;
    private static final double RISK_HIGH_CENTRALITY_THRESHOLD = 0.7;
    private static final double RISK_MEDIUM_CENTRALITY_THRESHOLD = 0.3;
    private final ObjectMapper mapper;

    public JsonSerializer() {
        this.mapper = new ObjectMapper();
    }

    /**
     * Convert analysis results to JSON string.
     *
     * @param graph Dependency graph
     * @param domains Domain mapping
     * @param cycles Detected cycles
     * @param hotspots Hotspot nodes
     * @param blindSpots Blind spot reports
     * @return JSON string
     */
    public String toJson(
        DependencyGraph graph,
        Map<String, String> domains,
        List<List<String>> cycles,
        List<Node> hotspots,
        List<BlindSpot> blindSpots
    ) {
        return toJson(graph, domains, cycles, hotspots, blindSpots, false);
    }

    /**
     * Convert analysis results to JSON string with optional metadata.
     *
     * @param graph Dependency graph
     * @param domains Domain mapping
     * @param cycles Detected cycles
     * @param hotspots Hotspot nodes
     * @param blindSpots Blind spot reports
     * @param withMetadata Include metadata field for AI integration (opt-in for backward compatibility)
     * @return JSON string
     */
    public String toJson(
        DependencyGraph graph,
        Map<String, String> domains,
        List<List<String>> cycles,
        List<Node> hotspots,
        List<BlindSpot> blindSpots,
        boolean withMetadata
    ) {
        ObjectNode root = mapper.createObjectNode();

        // Add schema versioning for AI integration
        root.put("$schema", SCHEMA_VERSION);
        root.put("version", OUTPUT_VERSION);

        // Serialize nodes
        ArrayNode nodesArray = root.putArray("nodes");
        for (String nodeId : graph.getNodeIds()) {
            ObjectNode nodeObj = nodesArray.addObject();
            nodeObj.put("id", nodeId);
            nodeObj.put("domain", domains.getOrDefault(nodeId, "ungrouped"));
            nodeObj.put("inDegree", graph.getDependents(nodeId).size());
            nodeObj.put("outDegree", graph.getDependencies(nodeId).size());

            // Add metadata field only if withMetadata is true (opt-in for AI integration)
            if (withMetadata) {
                ObjectNode metadata = nodeObj.putObject("metadata");

                // Metrics
                ObjectNode metrics = metadata.putObject("metrics");
                metrics.put("fanIn", graph.getDependents(nodeId).size());
                metrics.put("fanOut", graph.getDependencies(nodeId).size());
                // impactScore and riskLevel use simple heuristics for Tier 1
                metrics.put("impactScore", calculateImpactScore(nodeId, graph));
                metrics.put("riskLevel", calculateRiskLevel(nodeId, graph));

                // Issues
                ObjectNode issues = metadata.putObject("issues");
                issues.put("hotspot", isHotspot(nodeId, hotspots));
                issues.put("cycle", isInCycle(nodeId, cycles));
                issues.put("blindSpots", getBlindSpotsForNode(nodeId, blindSpots));
            }
        }

        // Serialize edges
        serializeEdges(root, graph);

        // Serialize cycles
        serializeCycles(root, cycles);

        // Serialize hotspots
        serializeHotspots(root, hotspots);

        // Serialize blind spots
        serializeBlindSpots(root, blindSpots);

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    /**
     * Full analysis data for Tier 2 output.
     */
    public static class FullAnalysisData {
        private final Map<String, Double> pageRank;
        private final Map<String, Double> betweenness;
        private final Map<String, Double> closeness;
        private final int connectedComponents;
        private final Set<String> bridges;

        public FullAnalysisData(
                Map<String, Double> pageRank,
                Map<String, Double> betweenness,
                Map<String, Double> closeness,
                int connectedComponents,
                Set<String> bridges) {
            this.pageRank = pageRank;
            this.betweenness = betweenness;
            this.closeness = closeness;
            this.connectedComponents = connectedComponents;
            this.bridges = bridges;
        }

        public Map<String, Double> getPageRank() { return pageRank; }
        public Map<String, Double> getBetweenness() { return betweenness; }
        public Map<String, Double> getCloseness() { return closeness; }
        public int getConnectedComponents() { return connectedComponents; }
        public Set<String> getBridges() { return bridges; }
    }

    /**
     * Convert analysis results to JSON string with optional full analysis.
     *
     * @param graph Dependency graph
     * @param domains Domain mapping
     * @param cycles Detected cycles
     * @param hotspots Hotspot nodes
     * @param blindSpots Blind spot reports
     * @param withMetadata Include metadata field for AI integration (opt-in for backward compatibility)
     * @param fullAnalysis Full analysis data (PageRank, betweenness, etc.) or null for Tier 1 only
     * @return JSON string
     */
    public String toJson(
        DependencyGraph graph,
        Map<String, String> domains,
        List<List<String>> cycles,
        List<Node> hotspots,
        List<BlindSpot> blindSpots,
        boolean withMetadata,
        FullAnalysisData fullAnalysis
    ) {
        ObjectNode root = mapper.createObjectNode();

        // Add schema versioning for AI integration
        root.put("$schema", SCHEMA_VERSION);
        root.put("version", OUTPUT_VERSION);

        // Validate centrality maps contain all nodes
        if (fullAnalysis != null) {
            validateFullAnalysisData(graph, fullAnalysis);
        }

        // Serialize nodes
        ArrayNode nodesArray = root.putArray("nodes");
        for (String nodeId : graph.getNodeIds()) {
            ObjectNode nodeObj = nodesArray.addObject();
            nodeObj.put("id", nodeId);
            nodeObj.put("domain", domains.getOrDefault(nodeId, "ungrouped"));
            nodeObj.put("inDegree", graph.getDependents(nodeId).size());
            nodeObj.put("outDegree", graph.getDependencies(nodeId).size());

            // Add metadata field only if withMetadata is true (opt-in for AI integration)
            if (withMetadata) {
                ObjectNode metadata = nodeObj.putObject("metadata");

                // Metrics
                ObjectNode metrics = metadata.putObject("metrics");
                metrics.put("fanIn", graph.getDependents(nodeId).size());
                metrics.put("fanOut", graph.getDependencies(nodeId).size());

                if (fullAnalysis != null) {
                    // Tier 2: Use computed centrality metrics
                    metrics.put("pageRank", fullAnalysis.getPageRank().getOrDefault(nodeId, 0.0));
                    metrics.put("betweenness", fullAnalysis.getBetweenness().getOrDefault(nodeId, 0.0));
                    metrics.put("closeness", fullAnalysis.getCloseness().getOrDefault(nodeId, 0.0));
                    // impactScore and riskLevel for Tier 2: use PageRank as improved impact metric
                    metrics.put("impactScore", fullAnalysis.getPageRank().getOrDefault(nodeId, 0.0));
                    metrics.put("riskLevel", calculateRiskLevelFromCentrality(nodeId, fullAnalysis));
                } else {
                    // Tier 1: Simple heuristics
                    metrics.put("impactScore", calculateImpactScore(nodeId, graph));
                    metrics.put("riskLevel", calculateRiskLevel(nodeId, graph));
                }

                // Issues
                ObjectNode issues = metadata.putObject("issues");
                issues.put("hotspot", isHotspot(nodeId, hotspots));
                issues.put("cycle", isInCycle(nodeId, cycles));
                issues.put("blindSpots", getBlindSpotsForNode(nodeId, blindSpots));
                issues.put("bridge", fullAnalysis != null && fullAnalysis.getBridges().contains(nodeId));
            }
        }

        // Serialize edges
        serializeEdges(root, graph);

        // Serialize cycles
        serializeCycles(root, cycles);

        // Serialize hotspots
        serializeHotspots(root, hotspots);

        // Serialize blind spots
        serializeBlindSpots(root, blindSpots);

        // Serialize full analysis metadata if available
        if (fullAnalysis != null) {
            serializeFullAnalysis(root, fullAnalysis);
        }

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }

    /**
     * Get the underlying ObjectMapper for custom serialization.
     */
    public ObjectMapper getMapper() {
        return mapper;
    }

    // Helper methods for metadata calculation

    private double calculateImpactScore(String nodeId, DependencyGraph graph) {
        // Simple heuristic for Tier 1: fanIn * fanOut normalized
        int fanIn = graph.getDependents(nodeId).size();
        int fanOut = graph.getDependencies(nodeId).size();
        int total = fanIn + fanOut;
        if (total == 0) return 0.0;
        // Normalize to 0-1 range using logarithmic scale
        return Math.min(1.0, Math.log(total + 1) / Math.log(100));
    }

    private String calculateRiskLevel(String nodeId, DependencyGraph graph) {
        int fanIn = graph.getDependents(nodeId).size();
        int fanOut = graph.getDependencies(nodeId).size();

        // Simple heuristic thresholds for Tier 1
        if (fanIn > RISK_HIGH_FAN_THRESHOLD || fanOut > RISK_HIGH_FAN_THRESHOLD) return "high";
        if (fanIn > RISK_MEDIUM_FAN_THRESHOLD || fanOut > RISK_MEDIUM_FAN_THRESHOLD) return "medium";
        return "low";
    }

    private boolean isHotspot(String nodeId, List<Node> hotspots) {
        return hotspots.stream().anyMatch(n -> n.getId().equals(nodeId));
    }

    private boolean isInCycle(String nodeId, List<List<String>> cycles) {
        return cycles.stream().anyMatch(cycle -> cycle.contains(nodeId));
    }

    private ArrayNode getBlindSpotsForNode(String nodeId, List<BlindSpot> blindSpots) {
        ArrayNode nodeBlindSpots = mapper.createArrayNode();
        for (BlindSpot bs : blindSpots) {
            // Match blind spots to nodes by location/pattern
            if (bs.getLocation() != null && bs.getLocation().contains(nodeId)) {
                nodeBlindSpots.add(bs.getType());
            }
        }
        return nodeBlindSpots;
    }

    // Helper methods for DRY serialization

    private void serializeEdges(ObjectNode root, DependencyGraph graph) {
        ArrayNode edgesArray = root.putArray("edges");
        for (String source : graph.getNodeIds()) {
            for (String target : graph.getDependencies(source)) {
                ObjectNode edgeObj = edgesArray.addObject();
                edgeObj.put("source", source);
                edgeObj.put("target", target);
            }
        }
    }

    private void serializeCycles(ObjectNode root, List<List<String>> cycles) {
        ArrayNode cyclesArray = root.putArray("cycles");
        for (List<String> cycle : cycles) {
            ArrayNode cycleArray = cyclesArray.addArray();
            for (String nodeId : cycle) {
                cycleArray.add(nodeId);
            }
        }
    }

    private void serializeHotspots(ObjectNode root, List<Node> hotspots) {
        ArrayNode hotspotsArray = root.putArray("hotspots");
        for (Node hotspot : hotspots) {
            ObjectNode hotspotObj = hotspotsArray.addObject();
            hotspotObj.put("id", hotspot.getId());
            hotspotObj.put("inDegree", hotspot.getInDegree());
            hotspotObj.put("outDegree", hotspot.getOutDegree());
        }
    }

    private void serializeBlindSpots(ObjectNode root, List<BlindSpot> blindSpots) {
        ArrayNode blindSpotsArray = root.putArray("blindSpots");
        for (BlindSpot blindSpot : blindSpots) {
            ObjectNode bsObj = blindSpotsArray.addObject();
            bsObj.put("type", blindSpot.getType());
            bsObj.put("location", blindSpot.getLocation());
            bsObj.put("description", blindSpot.getDescription());
        }
    }

    private void serializeFullAnalysis(ObjectNode root, FullAnalysisData fullAnalysis) {
        ObjectNode analysis = root.putObject("fullAnalysis");
        analysis.put("connectedComponents", fullAnalysis.getConnectedComponents());

        ArrayNode bridgesArray = analysis.putArray("bridges");
        for (String bridge : fullAnalysis.getBridges()) {
            bridgesArray.add(bridge);
        }
    }

    private String calculateRiskLevelFromCentrality(String nodeId, FullAnalysisData fullAnalysis) {
        double pageRank = fullAnalysis.getPageRank().getOrDefault(nodeId, 0.0);
        double betweenness = fullAnalysis.getBetweenness().getOrDefault(nodeId, 0.0);
        double closeness = fullAnalysis.getCloseness().getOrDefault(nodeId, 0.0);

        // Combine centrality metrics for better risk assessment
        double combined = (pageRank * 0.5) + (betweenness * 0.3) + (closeness * 0.2);

        // Clamp to [0, 1] to handle edge cases in dense graphs
        combined = Math.max(0.0, Math.min(1.0, combined));

        // Tier 2 thresholds based on centrality
        if (combined > RISK_HIGH_CENTRALITY_THRESHOLD) return "high";
        if (combined > RISK_MEDIUM_CENTRALITY_THRESHOLD) return "medium";
        return "low";
    }

    /**
     * Validates that centrality maps contain all nodes in the graph.
     * Throws IllegalStateException if any nodes are missing.
     */
    private void validateFullAnalysisData(DependencyGraph graph, FullAnalysisData fullAnalysis) {
        // Defensive null check: ensures validation safety even if caller logic changes
        if (fullAnalysis == null) {
            return;
        }

        Set<String> graphNodes = new HashSet<>(graph.getNodeIds());

        // Check PageRank contains all nodes
        Set<String> missingPageRank = new HashSet<>(graphNodes);
        missingPageRank.removeAll(fullAnalysis.getPageRank().keySet());
        if (!missingPageRank.isEmpty()) {
            throw new IllegalStateException(
                "PageRank missing " + missingPageRank.size() + " nodes: " +
                missingPageRank.stream().limit(5).collect(java.util.stream.Collectors.joining(", ")) +
                (missingPageRank.size() > 5 ? "..." : "")
            );
        }

        // Check betweenness contains all nodes
        Set<String> missingBetweenness = new HashSet<>(graphNodes);
        missingBetweenness.removeAll(fullAnalysis.getBetweenness().keySet());
        if (!missingBetweenness.isEmpty()) {
            throw new IllegalStateException(
                "Betweenness missing " + missingBetweenness.size() + " nodes: " +
                missingBetweenness.stream().limit(5).collect(java.util.stream.Collectors.joining(", ")) +
                (missingBetweenness.size() > 5 ? "..." : "")
            );
        }

        // Check closeness contains all nodes
        Set<String> missingCloseness = new HashSet<>(graphNodes);
        missingCloseness.removeAll(fullAnalysis.getCloseness().keySet());
        if (!missingCloseness.isEmpty()) {
            throw new IllegalStateException(
                "Closeness missing " + missingCloseness.size() + " nodes: " +
                missingCloseness.stream().limit(5).collect(java.util.stream.Collectors.joining(", ")) +
                (missingCloseness.size() > 5 ? "..." : "")
            );
        }
    }
}
