package com.archon.viz;

import com.archon.core.analysis.*;
import com.archon.core.plugin.BlindSpot;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

public class TerminalRenderer {
    private final AnalysisResult result;
    private final PrintWriter writer;

    public TerminalRenderer(AnalysisResult result, PrintWriter writer) {
        this.result = result;
        this.writer = writer;
    }

    public void render() {
        renderSummary();
        renderDomains();
        renderHotspots();
        renderBlindSpots();
    }

    private void renderSummary() {
        writer.println("archon: analyzed " + result.graph().getNodeIds().size() +
            " nodes, " + countEdges() + " edges, " + result.cycles().size() + " cycles");
    }

    private void renderDomains() {
        Map<String, List<String>> domainMap = groupByDomain();
        for (Map.Entry<String, List<String>> entry : domainMap.entrySet()) {
            writer.println("├── domain: " + entry.getKey() + " (" + entry.getValue().size() + " nodes)");
            for (String nodeId : entry.getValue()) {
                writer.println("│   ├── " + shortId(nodeId));
                Set<String> deps = result.graph().getDependencies(nodeId);
                if (!deps.isEmpty()) {
                    writer.println("│   │   └── → " + deps.stream()
                        .map(this::shortId)
                        .collect(Collectors.joining(", ")));
                }
            }
        }
    }

    private void renderHotspots() {
        if (result.hotspots().isEmpty()) return;
        writer.println(result.hotspots().size() + " hotspots: " +
            result.hotspots().stream()
                .map(node -> shortId(node.getId()))
                .collect(Collectors.joining(", ")));
    }

    private void renderBlindSpots() {
        if (result.blindSpots().isEmpty()) return;
        writer.println(result.blindSpots().size() + " blind spots: " +
            result.blindSpots().stream()
                .map(BlindSpot::getDescription)
                .collect(Collectors.joining(", ")));
    }

    private String shortId(String nodeId) {
        return nodeId.substring(nodeId.lastIndexOf(':') + 1);
    }

    private Map<String, List<String>> groupByDomain() {
        Map<String, List<String>> map = new HashMap<>();
        for (String nodeId : result.graph().getNodeIds()) {
            String domain = result.domains().getOrDefault(nodeId, "general");
            map.computeIfAbsent(domain, k -> new ArrayList<>()).add(nodeId);
        }
        return map;
    }

    private long countEdges() {
        long count = 0;
        for (String nodeId : result.graph().getNodeIds()) {
            count += result.graph().getDependencies(nodeId).size();
        }
        return count;
    }
}
