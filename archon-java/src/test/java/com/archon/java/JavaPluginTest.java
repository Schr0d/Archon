package com.archon.java;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.NodeType;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JavaPlugin SPI implementation.
 */
class JavaPluginTest {

    @Test
    void testFileExtensions() {
        JavaPlugin plugin = new JavaPlugin();
        Set<String> extensions = plugin.fileExtensions();

        assertTrue(extensions.contains("java"));
        assertEquals(1, extensions.size());
    }

    @Test
    void testParseSimpleClass() {
        JavaPlugin plugin = new JavaPlugin();
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        String javaCode = """
            package com.example;

            public class Foo {
                public void bar() { }
            }
            """;

        ParseContext context = new ParseContext(Path.of("/tmp"), Set.of("java"));
        ParseResult result = plugin.parseFromContent(
            "Foo.java",
            javaCode,
            context,
            builder
        );

        // Check that no errors were reported
        assertFalse(result.hasErrors(), "Should have no parse errors");
        assertTrue(result.getParseErrors().isEmpty());

        // Check that source modules were extracted
        assertFalse(result.getSourceModules().isEmpty());
        assertTrue(result.getSourceModules().contains("java:com.example.Foo"));
    }

    @Test
    void testParseClassWithImport() {
        JavaPlugin plugin = new JavaPlugin();
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        String javaCode = """
            package com.example;

            import java.util.List;

            public class Foo {
                private List<String> items;
            }
            """;

        ParseContext context = new ParseContext(Path.of("/tmp"), Set.of("java"));
        ParseResult result = plugin.parseFromContent(
            "Foo.java",
            javaCode,
            context,
            builder
        );

        // Check that no errors were reported
        assertFalse(result.hasErrors());

        // Check source module
        assertTrue(result.getSourceModules().contains("java:com.example.Foo"));

        // Build and check the graph has the node with namespace prefix
        DependencyGraph graph = builder.build();
        assertTrue(graph.containsNode("java:com.example.Foo"));
    }

    @Test
    void testParseClassWithExtends() {
        JavaPlugin plugin = new JavaPlugin();
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        String parentCode = """
            package com.example;

            public class Parent {
                public void parentMethod() { }
            }
            """;

        String childCode = """
            package com.example;

            public class Child extends Parent {
                public void childMethod() { }
            }
            """;

        ParseContext context = new ParseContext(Path.of("/tmp"), Set.of("java"));

        // Parse parent first
        plugin.parseFromContent("Parent.java", parentCode, context, builder);
        // Parse child
        ParseResult result = plugin.parseFromContent("Child.java", childCode, context, builder);

        assertFalse(result.hasErrors());

        // Build and check nodes are prefixed
        DependencyGraph graph = builder.build();
        assertTrue(graph.containsNode("java:com.example.Parent"));
        assertTrue(graph.containsNode("java:com.example.Child"));

        // Check edge exists with prefixed IDs
        assertTrue(graph.getEdge("java:com.example.Child", "java:com.example.Parent").isPresent());
    }

    @Test
    void testParseWithSyntaxError() {
        JavaPlugin plugin = new JavaPlugin();
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        String invalidCode = """
            package com.example;

            public class Broken {
                // Missing closing brace
            """;

        ParseContext context = new ParseContext(Path.of("/tmp"), Set.of("java"));
        ParseResult result = plugin.parseFromContent(
            "Broken.java",
            invalidCode,
            context,
            builder
        );

        // Should have errors
        assertTrue(result.hasErrors() || result.getParseErrors().size() > 0);
    }

    @Test
    void testNamespacePrefixing() {
        JavaPlugin plugin = new JavaPlugin();
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        String javaCode = """
            package test;

            public class TestClass {
                public void method() { }
            }
            """;

        ParseContext context = new ParseContext(Path.of("/tmp"), Set.of("java"));
        plugin.parseFromContent("TestClass.java", javaCode, context, builder);

        DependencyGraph graph = builder.build();

        // Verify all node IDs have "java:" prefix
        for (String nodeId : graph.getNodeIds()) {
            assertTrue(nodeId.startsWith("java:"),
                "Node ID should be prefixed with 'java:': " + nodeId);
        }

        // Verify the unprefixed version doesn't exist
        assertFalse(graph.containsNode("test.TestClass"));
        assertTrue(graph.containsNode("java:test.TestClass"));
    }
}
