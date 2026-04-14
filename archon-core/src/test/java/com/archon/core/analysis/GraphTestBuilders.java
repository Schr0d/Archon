package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.Node;

public class GraphTestBuilders {
    public static DependencyGraph buildGraph(Node[] nodes, Edge[] edges) {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        for (Node node : nodes) builder.addNode(node);
        for (Edge edge : edges) builder.addEdge(edge);
        return builder.build();
    }
}
