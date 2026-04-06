package com.archon.viz;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;
import com.archon.core.plugin.BlindSpot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

/**
 * Serializes analysis results to JSON format for web viewer and CLI output.
 */
public class JsonSerializer {
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
        ObjectNode root = mapper.createObjectNode();

        // Serialize nodes
        ArrayNode nodesArray = root.putArray("nodes");
        for (String nodeId : graph.getNodeIds()) {
            ObjectNode nodeObj = nodesArray.addObject();
            nodeObj.put("id", nodeId);
            nodeObj.put("domain", domains.getOrDefault(nodeId, "ungrouped"));
            nodeObj.put("inDegree", graph.getDependents(nodeId).size());
            nodeObj.put("outDegree", graph.getDependencies(nodeId).size());
        }

        // Serialize edges
        ArrayNode edgesArray = root.putArray("edges");
        for (String source : graph.getNodeIds()) {
            for (String target : graph.getDependencies(source)) {
                ObjectNode edgeObj = edgesArray.addObject();
                edgeObj.put("source", source);
                edgeObj.put("target", target);
            }
        }

        // Serialize cycles
        ArrayNode cyclesArray = root.putArray("cycles");
        for (List<String> cycle : cycles) {
            ArrayNode cycleArray = cyclesArray.addArray();
            for (String nodeId : cycle) {
                cycleArray.add(nodeId);
            }
        }

        // Serialize hotspots
        ArrayNode hotspotsArray = root.putArray("hotspots");
        for (Node hotspot : hotspots) {
            ObjectNode hotspotObj = hotspotsArray.addObject();
            hotspotObj.put("id", hotspot.getId());
            hotspotObj.put("inDegree", hotspot.getInDegree());
            hotspotObj.put("outDegree", hotspot.getOutDegree());
        }

        // Serialize blind spots
        ArrayNode blindSpotsArray = root.putArray("blindSpots");
        for (BlindSpot blindSpot : blindSpots) {
            ObjectNode bsObj = blindSpotsArray.addObject();
            bsObj.put("type", blindSpot.getType());
            bsObj.put("location", blindSpot.getLocation());
            bsObj.put("description", blindSpot.getDescription());
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
}
