package com.archon.core.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a node in the dependency graph (typically a Java class).
 * Immutable after construction via GraphBuilder.
 */
public class Node {
    private final String id;              // FQCN: "com.fuwa.system.domain.SysUser"
    private final NodeType type;
    private final String domain;          // "system", "auth", "shared"
    private final String sourcePath;      // relative path from project root
    private final List<String> tags;
    private final Confidence confidence;
    private int inDegree;                 // computed during analysis
    private int outDegree;                // computed during analysis

    private Node(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "node id must not be null");
        this.type = Objects.requireNonNull(builder.type, "node type must not be null");
        this.domain = builder.domain;
        this.sourcePath = builder.sourcePath;
        this.tags = Collections.unmodifiableList(new ArrayList<>(builder.tags));
        this.confidence = builder.confidence != null ? builder.confidence : Confidence.HIGH;
        this.inDegree = 0;
        this.outDegree = 0;
    }

    public String getId() { return id; }
    public NodeType getType() { return type; }
    public Optional<String> getDomain() { return Optional.ofNullable(domain); }
    public Optional<String> getSourcePath() { return Optional.ofNullable(sourcePath); }
    public List<String> getTags() { return tags; }
    public Confidence getConfidence() { return confidence; }
    public int getInDegree() { return inDegree; }
    public int getOutDegree() { return outDegree; }

    void setInDegree(int degree) { this.inDegree = degree; }
    void setOutDegree(int degree) { this.outDegree = degree; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        return id.equals(((Node) o).id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "Node{id=" + id + ", type=" + type + ", domain=" + domain + "}";
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private NodeType type;
        private String domain;
        private String sourcePath;
        private List<String> tags = new ArrayList<>();
        private Confidence confidence;

        public Builder id(String id) { this.id = id; return this; }
        public Builder type(NodeType type) { this.type = type; return this; }
        public Builder domain(String domain) { this.domain = domain; return this; }
        public Builder sourcePath(String sourcePath) { this.sourcePath = sourcePath; return this; }
        public Builder tags(List<String> tags) { this.tags = tags; return this; }
        public Builder addTag(String tag) { this.tags.add(tag); return this; }
        public Builder confidence(Confidence confidence) { this.confidence = confidence; return this; }
        public Node build() { return new Node(this); }
    }
}
