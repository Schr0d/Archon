package com.archon.core.plugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Set;

class ParseContextTest {
    @Test
    void testConstructorAndGetters() {
        Path sourceRoot = Path.of("/src/main/java");
        Set<String> extensions = Set.of("java", "js");

        ParseContext context = new ParseContext(sourceRoot, extensions);

        assertEquals(sourceRoot, context.getSourceRoot());
        assertEquals(extensions, context.getFileExtensions());
    }

    @Test
    void testHasExtension() {
        ParseContext context = new ParseContext(
            Path.of("/src"),
            Set.of("java", "js", "ts")
        );

        assertTrue(context.hasExtension("java"));
        assertTrue(context.hasExtension("js"));
        assertFalse(context.hasExtension("py"));
    }
}
