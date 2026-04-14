package com.archon.core.plugin;

import java.util.Objects;

/**
 * Represents a dependency that cannot be statically analyzed.
 * Dynamic patterns (reflection, dynamic proxy, computed imports) are blind spots.
 */
public class BlindSpot {
    private final String type;
    private final String location;
    private final String description;

    public BlindSpot(String type, String location, String description) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.location = Objects.requireNonNull(location, "location must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlindSpot blindSpot = (BlindSpot) o;
        return type.equals(blindSpot.type)
            && location.equals(blindSpot.location)
            && description.equals(blindSpot.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, location, description);
    }
}
