package com.archon.core.plugin;

import java.util.Objects;

/**
 * A single dependency relationship discovered by a language plugin.
 *
 * <p>Plugins return these instead of building graph edges directly.
 * The ParseOrchestrator converts declarations into graph nodes and edges.
 *
 * @param sourceId   namespace-prefixed source (e.g. "java:com.example.Foo")
 * @param targetId   namespace-prefixed target (e.g. "java:com.example.Bar")
 * @param edgeType   kind of dependency relationship
 * @param confidence how certain the parser is about this dependency
 * @param evidence   raw source text that produced this declaration (nullable)
 * @param dynamic    true for reflection / computed-import patterns
 */
public record DependencyDeclaration(
    String sourceId,
    String targetId,
    EdgeType edgeType,
    Confidence confidence,
    String evidence,
    boolean dynamic
) {
    public DependencyDeclaration {
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(edgeType, "edgeType must not be null");
        if (sourceId.isBlank()) throw new IllegalArgumentException("sourceId must not be blank");
        if (targetId.isBlank()) throw new IllegalArgumentException("targetId must not be blank");
    }
}
