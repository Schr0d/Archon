package com.archon.core.plugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;

class LanguagePluginTest {
    @Test
    void testLanguagePluginInterfaceExists() {
        assertTrue(LanguagePlugin.class.isInterface());
    }

    @Test
    void testFileExtensionsMethod() {
        LanguagePlugin plugin = new TestPlugin();
        Set<String> extensions = plugin.fileExtensions();

        assertEquals(Set.of("java"), extensions);
    }

    @Test
    void testParseFromContentReturnsParseResult() {
        LanguagePlugin plugin = new TestPlugin();
        ParseContext context = new ParseContext(
            java.nio.file.Path.of("/test/src"),
            Set.of("java")
        );

        ParseResult result = plugin.parseFromContent(
            "/test/src/Foo.java",
            "public class Foo {}",
            context
        );

        assertNotNull(result);
        assertNotNull(result.getSourceModules());
    }

    // Minimal test implementation
    static class TestPlugin implements LanguagePlugin {
        @Override
        public Set<String> fileExtensions() {
            return Set.of("java");
        }

        @Override
        public ParseResult parseFromContent(
            String filePath,
            String content,
            ParseContext context
        ) {
            return new ParseResult(Set.of(), java.util.List.of(), java.util.List.of());
        }
    }
}
