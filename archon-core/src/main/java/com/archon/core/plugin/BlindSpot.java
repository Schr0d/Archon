package com.archon.core.plugin;

/**
 * Represents a dependency that cannot be statically analyzed.
 * Dynamic patterns (reflection, dynamic proxy, computed imports) are blind spots.
 */
public class BlindSpot {
    private final String type;
    private final String location;
    private final String description;

    public BlindSpot(String type, String location, String description) {
        this.type = type;
        this.location = location;
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", type, location, description);
    }
}
