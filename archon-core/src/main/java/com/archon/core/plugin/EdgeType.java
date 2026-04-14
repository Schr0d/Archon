package com.archon.core.plugin;

/**
 * Kind of dependency relationship between two modules.
 *
 * <p>Re-exported in the plugin package so that language plugins
 * do not need to depend on {@code com.archon.core.graph.EdgeType}.
 */
public enum EdgeType {
    IMPORTS,
    CALLS,
    IMPLEMENTS,
    EXTENDS,
    USES
}
