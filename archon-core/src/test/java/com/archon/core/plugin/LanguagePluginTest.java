package com.archon.core.plugin;

import com.archon.core.coordination.PostProcessResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import java.nio.file.Path;

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

    @Test
    void defaultPostProcessReturnsEmpty() {
        LanguagePlugin plugin = new LanguagePlugin() {
            @Override public Set<String> fileExtensions() { return Set.of("java"); }
            @Override public ParseResult parseFromContent(String filePath, String content, ParseContext context) {
                return new ParseResult(Set.of(), java.util.List.of(), java.util.List.of());
            }
        };

        var result = plugin.postProcess(List.of(), new ParseContext(Path.of("/src"), Set.of("java")));
        assertTrue(result.declarations().isEmpty());
        assertTrue(result.blindSpots().isEmpty());
    }
}
