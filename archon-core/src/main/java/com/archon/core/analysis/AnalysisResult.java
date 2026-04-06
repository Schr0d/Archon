package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;
import com.archon.core.plugin.BlindSpot;

import java.util.List;
import java.util.Map;

/**
 * Complete analysis result from parsing and computation.
 * Returned by AnalysisPipeline.run() for reuse across commands.
 */
public record AnalysisResult(
    DependencyGraph graph,
    Map<String, String> domains,
    List<List<String>> cycles,
    List<Node> hotspots,
    List<BlindSpot> blindSpots,
    Thresholds thresholds
) {}
