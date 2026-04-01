package com.archon.core.graph;

import java.util.Objects;

/**
 * Represents a directed edge in the dependency graph.
 * Immutable after construction.
 */
public class Edge {
    private final String source;
    private final String target;
    private final EdgeType type;
    private final Confidence confidence;
    private final boolean dynamic;
    private final String evidence;

    private Edge(Builder builder) {
        this.source = Objects.requireNonNull(builder.source, "edge source must not be null");
        this.target = Objects.requireNonNull(builder.target, "edge target must not be null");
        this.type = Objects.requireNonNull(builder.type, "edge type must not be null");
        this.confidence = builder.confidence != null ? builder.confidence : Confidence.HIGH;
        this.dynamic = builder.dynamic;
        this.evidence = builder.evidence;
    }

    public String getSource() { return source; }
    public String getTarget() { return target; }
    public EdgeType getType() { return type; }
    public Confidence getConfidence() { return confidence; }
    public boolean isDynamic() { return dynamic; }
    public String getEvidence() { return evidence; }

    /**
     * Returns a key for indexing this edge: source -> target.
     */
    public String key() { return source + "->" + target; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Edge)) return false;
        Edge edge = (Edge) o;
        return source.equals(edge.source) && target.equals(edge.target);
    }

    @Override
    public int hashCode() { return Objects.hash(source, target); }

    @Override
    public String toString() {
        return "Edge{" + source + " -" + type + "-> " + target +
               ", confidence=" + confidence + (dynamic ? ", DYNAMIC" : "") + "}";
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String source;
        private String target;
        private EdgeType type;
        private Confidence confidence;
        private boolean dynamic;
        private String evidence;

        public Builder source(String source) { this.source = source; return this; }
        public Builder target(String target) { this.target = target; return this; }
        public Builder type(EdgeType type) { this.type = type; return this; }
        public Builder confidence(Confidence confidence) { this.confidence = confidence; return this; }
        public Builder dynamic(boolean dynamic) { this.dynamic = dynamic; return this; }
        public Builder evidence(String evidence) { this.evidence = evidence; return this; }
        public Edge build() { return new Edge(this); }
    }
}
