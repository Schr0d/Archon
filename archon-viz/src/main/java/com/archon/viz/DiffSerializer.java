package com.archon.viz;

import com.archon.core.analysis.ChangeImpactReport;
import com.archon.core.analysis.GraphDiff;
import com.archon.core.analysis.ImpactResult;
import com.archon.core.analysis.RiskSummary;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.output.JsonSerializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serializes a ChangeImpactReport with diff annotations overlayed on the base graph.
 * <p>
 * This class extends the base JsonSerializer output by adding:
 * <ul>
 *   <li>addedNodes - Set of node IDs that were added</li>
 *   <li>removedNodes - Set of node IDs that were removed</li>
 *   <li>addedEdges - Set of edges that were added</li>
 *   <li>removedEdges - Set of edges that were removed</li>
 *   <li>newCycles - List of cycles that were introduced</li>
 *   <li>fixedCycles - List of cycles that were resolved</li>
 *   <li>impactedNodes - List of nodes affected by the change</li>
 *   <li>riskSummary - Risk assessment summary</li>
 * </ul>
 */
public class DiffSerializer {
    private static final ObjectMapper mapper = createObjectMapper();
    private final ChangeImpactReport impactReport;
    private final DependencyGraph headGraph;
    private final Map<String, String> domains;

    public DiffSerializer(ChangeImpactReport impactReport, DependencyGraph headGraph, Map<String, String> domains) {
        this.impactReport = impactReport;
        this.headGraph = headGraph;
        this.domains = domains != null ? domains : Map.of();
    }

    /**
     * Serializes the ChangeImpactReport to JSON with diff annotations.
     *
     * @return JSON string representation of the diff report
     * @throws IOException if serialization fails
     */
    public String toJson() throws IOException {
        // Start with base graph serialization
        JsonSerializer jsonSerializer = new JsonSerializer();
        String baseJson = jsonSerializer.toJson(headGraph, domains,
            impactReport.getGraphDiff().getNewCycles(),
            List.of(), // hotspots - not available in ChangeImpactReport
            List.of()  // blindSpots - not available in ChangeImpactReport
        );

        ObjectNode root = (ObjectNode) mapper.readTree(baseJson);

        // Add diff annotations
        addDiffAnnotations(root);

        // Add risk summary
        addRiskSummary(root);

        // Add impacted nodes
        addImpactedNodes(root);

        // Add cycle information
        addCycleInformation(root);

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private void addDiffAnnotations(ObjectNode root) {
        GraphDiff diff = impactReport.getGraphDiff();

        // Mark added nodes
        ArrayNode addedNodes = root.putArray("addedNodes");
        for (String nodeId : diff.getAddedNodes()) {
            addedNodes.add(nodeId);
        }

        // Mark removed nodes
        ArrayNode removedNodes = root.putArray("removedNodes");
        for (String nodeId : diff.getRemovedNodes()) {
            removedNodes.add(nodeId);
        }

        // Mark added edges
        ArrayNode addedEdges = root.putArray("addedEdges");
        for (Edge edge : diff.getAddedEdges()) {
            addedEdges.add(serializeEdge(edge));
        }

        // Mark removed edges
        ArrayNode removedEdges = root.putArray("removedEdges");
        for (Edge edge : diff.getRemovedEdges()) {
            removedEdges.add(serializeEdge(edge));
        }
    }

    private void addRiskSummary(ObjectNode root) {
        RiskSummary riskSummary = impactReport.getRiskSummary();
        ObjectNode riskObj = root.putObject("riskSummary");

        riskObj.put("overallRisk", riskSummary.getOverallRisk().name());
        riskObj.put("newCycleCount", riskSummary.getNewCycleCount());
        riskObj.put("crossDomainEdgeChanges", riskSummary.getCrossDomainEdgeChanges());
        riskObj.put("criticalPathHits", riskSummary.getCriticalPathHits());
        riskObj.put("perClassRiskCount", riskSummary.getPerClassRisk().size());
    }

    private void addImpactedNodes(ObjectNode root) {
        ArrayNode impactedNodesArray = root.putArray("impactedNodes");
        for (ImpactResult.ImpactNode impactNode : impactReport.getImpactedNodes()) {
            ObjectNode impactObj = mapper.createObjectNode();
            impactObj.put("nodeId", impactNode.getNodeId());
            impactNode.getDomain().ifPresent(domain -> impactObj.put("domain", domain));
            impactObj.put("depth", impactNode.getDepth());
            impactObj.put("risk", impactNode.getRisk().name());
            impactObj.put("layer", impactNode.getLayer().name());
            impactedNodesArray.add(impactObj);
        }
    }

    private void addCycleInformation(ObjectNode root) {
        GraphDiff diff = impactReport.getGraphDiff();

        // Add new cycles
        ArrayNode newCyclesArray = root.putArray("newCycles");
        for (java.util.List<String> cycle : diff.getNewCycles()) {
            newCyclesArray.add(mapper.valueToTree(cycle));
        }

        // Add fixed cycles
        ArrayNode fixedCyclesArray = root.putArray("fixedCycles");
        for (java.util.List<String> cycle : diff.getFixedCycles()) {
            fixedCyclesArray.add(mapper.valueToTree(cycle));
        }
    }

    private JsonNode serializeEdge(Edge edge) {
        ObjectNode edgeObj = mapper.createObjectNode();
        edgeObj.put("source", edge.getSource());
        edgeObj.put("target", edge.getTarget());
        edgeObj.put("type", edge.getType().name());
        edgeObj.put("confidence", edge.getConfidence().name());
        edgeObj.put("dynamic", edge.isDynamic());
        if (edge.getEvidence() != null) {
            edgeObj.put("evidence", edge.getEvidence());
        }
        return edgeObj;
    }

    /**
     * Creates an ObjectMapper for JSON serialization.
     * Reuses the configuration from JsonSerializer.
     */
    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper();
    }
}
