package com.archon.core.graph;

/**
 * Edge type representing the kind of dependency between two nodes.
 */
public enum EdgeType {
    IMPORTS,
    CALLS,
    IMPLEMENTS,
    EXTENDS,
    USES,
    SPRING_DI
}
