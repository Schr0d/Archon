package com.archon.core.plugin;

import java.util.Objects;

/**
 * A module or class discovered by a language plugin.
 *
 * <p>Plugins return these instead of adding nodes directly to the graph.
 * The ParseOrchestrator converts declarations into graph nodes.
 *
 * @param id          namespace-prefixed identifier (e.g. "java:com.example.Foo")
 * @param type        node category — CLASS for Java, MODULE for JS/Python
 * @param sourcePath  relative path from project root (nullable)
 * @param confidence  HIGH for parsed, MEDIUM/LOW for heuristic
 */
public record ModuleDeclaration(
    String id,
    NodeType type,
    String sourcePath,
    Confidence confidence
) {
    public ModuleDeclaration {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (id.isBlank()) throw new IllegalArgumentException("id must not be blank");
    }
}
