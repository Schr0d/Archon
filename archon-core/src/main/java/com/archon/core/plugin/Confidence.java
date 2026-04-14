package com.archon.core.plugin;

/**
 * Confidence level for a declaration produced by a language plugin.
 *
 * <p>Re-exported in the plugin package so that language plugins
 * do not need to depend on {@code com.archon.core.graph.Confidence}.
 */
public enum Confidence {
    HIGH,
    MEDIUM,
    LOW
}
