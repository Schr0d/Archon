package com.archon.core.plugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

class EnumSyncTest {

    @Test
    void nodeTypesAreIdentical() {
        Set<String> graphNames = Arrays.stream(com.archon.core.graph.NodeType.values())
            .map(Enum::name).collect(Collectors.toSet());
        Set<String> pluginNames = Arrays.stream(NodeType.values())
            .map(Enum::name).collect(Collectors.toSet());
        assertEquals(graphNames, pluginNames,
            "plugin.NodeType must have identical values to graph.NodeType");
    }

    @Test
    void edgeTypesAreIdentical() {
        Set<String> graphNames = Arrays.stream(com.archon.core.graph.EdgeType.values())
            .map(Enum::name).collect(Collectors.toSet());
        Set<String> pluginNames = Arrays.stream(EdgeType.values())
            .map(Enum::name).collect(Collectors.toSet());
        assertEquals(graphNames, pluginNames,
            "plugin.EdgeType must have identical values to graph.EdgeType");
    }

    @Test
    void confidenceLevelsAreIdentical() {
        Set<String> graphNames = Arrays.stream(com.archon.core.graph.Confidence.values())
            .map(Enum::name).collect(Collectors.toSet());
        Set<String> pluginNames = Arrays.stream(Confidence.values())
            .map(Enum::name).collect(Collectors.toSet());
        assertEquals(graphNames, pluginNames,
            "plugin.Confidence must have identical values to graph.Confidence");
    }

    @Test
    void nodeTypeRoundTrip() {
        for (NodeType pluginType : NodeType.values()) {
            com.archon.core.graph.NodeType graphType =
                com.archon.core.graph.NodeType.valueOf(pluginType.name());
            assertEquals(pluginType.name(), graphType.name());
        }
    }

    @Test
    void edgeTypeRoundTrip() {
        for (EdgeType pluginType : EdgeType.values()) {
            com.archon.core.graph.EdgeType graphType =
                com.archon.core.graph.EdgeType.valueOf(pluginType.name());
            assertEquals(pluginType.name(), graphType.name());
        }
    }

    @Test
    void confidenceRoundTrip() {
        for (Confidence pluginConf : Confidence.values()) {
            com.archon.core.graph.Confidence graphConf =
                com.archon.core.graph.Confidence.valueOf(pluginConf.name());
            assertEquals(pluginConf.name(), graphConf.name());
        }
    }
}
