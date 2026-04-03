package com.archon.core.plugin;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.analysis.DomainStrategy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.List;

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
    void testGetDomainStrategyReturnsOptional() {
        LanguagePlugin plugin = new TestPlugin();
        Optional<DomainStrategy> strategy = plugin.getDomainStrategy();

        assertNotNull(strategy);
        assertTrue(strategy.isPresent());
    }

    @Test
    void testParseFromContentReturnsParseResult() {
        LanguagePlugin plugin = new TestPlugin();
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        ParseContext context = new ParseContext(
            java.nio.file.Path.of("/test/src"),
            Set.of("java")
        );

        ParseResult result = plugin.parseFromContent(
            "/test/src/Foo.java",
            "public class Foo {}",
            context,
            builder
        );

        assertNotNull(result);
        assertNotNull(result.getGraph());
    }

    // Minimal test implementation
    static class TestPlugin implements LanguagePlugin {
        @Override
        public Set<String> fileExtensions() {
            return Set.of("java");
        }

        @Override
        public Optional<DomainStrategy> getDomainStrategy() {
            return Optional.of((graph, modules) -> Optional.of(Map.of()));
        }

        @Override
        public ParseResult parseFromContent(
            String filePath,
            String content,
            ParseContext context,
            DependencyGraph.MutableBuilder builder
        ) {
            return new ParseResult(builder.build(), Set.of(), List.of());
        }
    }
}
