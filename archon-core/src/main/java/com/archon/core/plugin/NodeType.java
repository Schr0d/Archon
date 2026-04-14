package com.archon.core.plugin;

/**
 * Category of a module or class discovered by a language plugin.
 *
 * <p>Re-exported in the plugin package so that language plugins
 * do not need to depend on {@code com.archon.core.graph.NodeType}.
 */
public enum NodeType {
    CLASS,
    MODULE,
    PACKAGE,
    SERVICE,
    CONTROLLER
}
