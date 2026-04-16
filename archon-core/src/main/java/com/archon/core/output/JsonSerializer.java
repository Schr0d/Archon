package com.archon.core.output;

import com.archon.core.analysis.FullAnalysisData;
import com.archon.core.graph.*;
import com.archon.core.plugin.BlindSpot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Serializes analysis results to machine-readable JSON.
 */
public class JsonSerializer {
    private final ObjectMapper mapper = new ObjectMapper();

    public String toJson(DependencyGraph graph) {
        try {
            ObjectNode root = mapper.createObjectNode();

            // Nodes
            ArrayNode nodesArray = root.putArray("nodes");
            for (String nodeId : graph.getNodeIds()) {
                graph.getNode(nodeId).ifPresent(node -> {
                    ObjectNode nodeObj = mapper.createObjectNode();
                    nodeObj.put("id", node.getId());
                    nodeObj.put("type", node.getType().name());
                    node.getDomain().ifPresent(d -> nodeObj.put("domain", d));
                    node.getSourcePath().ifPresent(p -> nodeObj.put("sourcePath", p));
                    nodeObj.put("confidence", node.getConfidence().name());
                    nodesArray.add(nodeObj);
                });
            }

            // Edges
            ArrayNode edgesArray = root.putArray("edges");
            for (Edge edge : graph.getAllEdges()) {
                ObjectNode edgeObj = mapper.createObjectNode();
                edgeObj.put("source", edge.getSource());
                edgeObj.put("target", edge.getTarget());
                edgeObj.put("type", edge.getType().name());
                edgeObj.put("confidence", edge.getConfidence().name());
                edgeObj.put("dynamic", edge.isDynamic());
                if (edge.getEvidence() != null) {
                    edgeObj.put("evidence", edge.getEvidence());
                }
                edgesArray.add(edgeObj);
            }

            // Stats
            ObjectNode statsObj = root.putObject("stats");
            statsObj.put("nodeCount", graph.nodeCount());
            statsObj.put("edgeCount", graph.edgeCount());

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize graph to JSON", e);
        }
    }

    public String toJson(DependencyGraph graph, Map<String, String> domains,
                         List<List<String>> cycles, List<Node> hotspots,
                         List<BlindSpot> blindSpots) {
        return toJson(graph, domains, cycles, hotspots, blindSpots, List.of());
    }

    public String toJson(DependencyGraph graph, Map<String, String> domains,
                         List<List<String>> cycles, List<Node> hotspots,
                         List<BlindSpot> blindSpots, List<String> errors) {
        Objects.requireNonNull(errors, "errors cannot be null");
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("graph", mapper.readTree(toJson(graph)));

            // Domains
            ObjectNode domainsObj = root.putObject("domains");
            for (Map.Entry<String, Set<String>> entry : groupByDomain(domains).entrySet()) {
                domainsObj.put(entry.getKey(), mapper.valueToTree(entry.getValue()));
            }

            // Cycles
            ArrayNode cyclesArray = root.putArray("cycles");
            for (List<String> cycle : cycles) {
                cyclesArray.add(mapper.valueToTree(cycle));
            }

            // Hotspots
            ArrayNode hotspotsArray = root.putArray("hotspots");
            for (Node hotspot : hotspots) {
                hotspotsArray.add(hotspot.getId());
            }

            // Blind spots
            ArrayNode blindSpotsArray = root.putArray("blindSpots");
            for (BlindSpot spot : blindSpots) {
                ObjectNode spotObj = mapper.createObjectNode();
                spotObj.put("type", spot.getType());
                spotObj.put("location", spot.getLocation());
                spotObj.put("description", spot.getDescription());
                blindSpotsArray.add(spotObj);
            }

            // Errors (NEW)
            ArrayNode errorsArray = root.putArray("errors");
            for (String error : errors) {
                errorsArray.add(error);
            }

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize analysis result to JSON", e);
        }
    }

    /**
     * Full serialization with optional metadata and full analysis data.
     */
    public String toJson(DependencyGraph graph, Map<String, String> domains,
                         List<List<String>> cycles, List<Node> hotspots,
                         List<BlindSpot> blindSpots, boolean withMetadata,
                         FullAnalysisData fullAnalysis) {
        try {
            // Build base JSON from the 6-arg version (with empty errors)
            ObjectNode root = (ObjectNode) mapper.readTree(
                toJson(graph, domains, cycles, hotspots, blindSpots, List.of()));

            if (withMetadata) {
                ObjectNode meta = root.putObject("metadata");
                meta.put("version", System.getProperty("archon.version", "unknown"));
                meta.put("timestamp", java.time.Instant.now().toString());
            }

            if (fullAnalysis != null) {
                ObjectNode analysis = root.putObject("fullAnalysis");

                ObjectNode pageRankObj = analysis.putObject("pageRank");
                for (Map.Entry<String, Double> entry : fullAnalysis.getPageRank().entrySet()) {
                    pageRankObj.put(entry.getKey(), entry.getValue());
                }

                ObjectNode betweennessObj = analysis.putObject("betweenness");
                for (Map.Entry<String, Double> entry : fullAnalysis.getBetweenness().entrySet()) {
                    betweennessObj.put(entry.getKey(), entry.getValue());
                }

                ObjectNode closenessObj = analysis.putObject("closeness");
                for (Map.Entry<String, Double> entry : fullAnalysis.getCloseness().entrySet()) {
                    closenessObj.put(entry.getKey(), entry.getValue());
                }

                analysis.put("connectedComponents", fullAnalysis.getConnectedComponents());

                ArrayNode bridgesArray = analysis.putArray("bridges");
                for (String bridge : fullAnalysis.getBridges()) {
                    bridgesArray.add(bridge);
                }
            }

            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize analysis result to JSON", e);
        }
    }

    private Map<String, Set<String>> groupByDomain(Map<String, String> domainMap) {
        // Group node IDs by domain name
        Map<String, Set<String>> grouped = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : domainMap.entrySet()) {
            grouped.computeIfAbsent(entry.getValue(), k -> new java.util.LinkedHashSet<>()).add(entry.getKey());
        }
        return grouped;
    }
}
