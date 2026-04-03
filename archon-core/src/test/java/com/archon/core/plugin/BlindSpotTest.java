package com.archon.core.plugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlindSpotTest {
    @Test
    void testConstructorAndGetters() {
        BlindSpot spot = new BlindSpot(
            "DynamicReflection",
            "com.example.Foo",
            "Class.forName() called with variable"
        );

        assertEquals("DynamicReflection", spot.getType());
        assertEquals("com.example.Foo", spot.getLocation());
        assertEquals("Class.forName() called with variable", spot.getDescription());
    }

    @Test
    void testToStringFormatting() {
        BlindSpot spot = new BlindSpot(
            "DynamicReflection",
            "com.example.Foo",
            "reflection call"
        );
        String result = spot.toString();
        assertTrue(result.contains("DynamicReflection"));
        assertTrue(result.contains("com.example.Foo"));
    }
}
