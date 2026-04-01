package com.archon.core.graph;

/**
 * Confidence level for graph nodes, edges, and blind spots.
 * HIGH = symbol-solved or direct evidence
 * MEDIUM = import-level inference
 * LOW = heuristic or dynamic pattern detection
 */
public enum Confidence {
    HIGH,
    MEDIUM,
    LOW
}
