package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;

/**
 * Service for computing full centrality analysis on dependency graphs.
 * Extracts centrality computation logic from view commands into a reusable service.
 */
public class CentralityService {
    private final DependencyGraph graph;

    /**
     * Creates a new centrality service for the given graph.
     *
     * @param graph the dependency graph to analyze
     */
    public CentralityService(DependencyGraph graph) {
        this.graph = graph;
    }

    /**
     * Compute full centrality analysis for AI integration.
     *
     * @return FullAnalysisData with PageRank, betweenness, closeness, components, bridges
     */
    public FullAnalysisData computeFullAnalysis() {
        CentralityCalculator calculator = new CentralityCalculator(graph);
        return new FullAnalysisData(
            calculator.computePageRank(),
            calculator.computeBetweenness(),
            calculator.computeCloseness(),
            calculator.computeConnectedComponents(),
            calculator.findBridges()
        );
    }
}
