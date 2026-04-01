package com.archon.core.graph;

import java.util.Objects;

/**
 * Represents a dependency that static analysis cannot fully resolve.
 * Always has LOW confidence.
 */
public class BlindSpot {
    private final String file;
    private final int line;
    private final String type;       // "reflection", "event-driven", "dynamic-proxy", "mybatis-xml"
    private final String pattern;    // "Class.forName", "@EventListener", "SysUserMapper.xml"
    private final Confidence confidence;

    public BlindSpot(String file, int line, String type, String pattern) {
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.line = line;
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
        this.confidence = Confidence.LOW; // blind spots are always LOW confidence
    }

    public String getFile() { return file; }
    public int getLine() { return line; }
    public String getType() { return type; }
    public String getPattern() { return pattern; }
    public Confidence getConfidence() { return confidence; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlindSpot)) return false;
        BlindSpot that = (BlindSpot) o;
        return line == that.line && file.equals(that.file) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, line, type);
    }

    @Override
    public String toString() {
        return "BlindSpot{" + file + ":" + line + " — " + pattern + " (" + type + ")}";
    }
}
