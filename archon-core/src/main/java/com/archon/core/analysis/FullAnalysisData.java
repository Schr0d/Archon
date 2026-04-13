package com.archon.core.analysis;

import java.util.Map;
import java.util.Set;

/**
 * Full analysis data for Tier 2 output.
 * Contains centrality metrics computed by {@link CentralityCalculator}.
 */
public class FullAnalysisData {
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
