package com.archon.core.graph;

/**
 * Fluent builder for constructing immutable DependencyGraph instances.
 * Usage: GraphBuilder.builder().addNode(...).addEdge(...).build()
 */
public class GraphBuilder {
    private final DependencyGraph.MutableBuilder delegate = new DependencyGraph.MutableBuilder();

    public static GraphBuilder builder() {
        return new GraphBuilder();
    }

    public GraphBuilder addNode(Node node) {
        delegate.addNode(node);
        return this;
    }

    public GraphBuilder addEdge(Edge edge) {
        delegate.addEdge(edge);
        return this;
    }

    /**
     * Builds and freezes the dependency graph.
     * After this call, the graph is immutable.
     */
    public DependencyGraph build() {
        return delegate.build();
    }
}
