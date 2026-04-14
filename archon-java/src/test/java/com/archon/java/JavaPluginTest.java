package com.archon.java;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.plugin.DependencyDeclaration;
import com.archon.core.plugin.ModuleDeclaration;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JavaPlugin SPI implementation.
 */
class JavaPluginTest {

    private JavaPlugin plugin;
    private ParseContext context;

    @BeforeEach
    void setUp() {
        plugin = new JavaPlugin();
        plugin.reset();
        context = new ParseContext(Path.of("/tmp"), Set.of("java"));
    }

    @Test
    void testFileExtensions() {
        Set<String> extensions = plugin.fileExtensions();

        assertTrue(extensions.contains("java"));
        assertEquals(1, extensions.size());
    }

    @Test
    void testParseSimpleClass() {
        String javaCode = """
            package com.example;

            public class Foo {
                public void bar() { }
            }
            """;

        ParseResult result = plugin.parseFromContent(
            "Foo.java",
            javaCode,
            context
        );

        // Check that no errors were reported
        assertFalse(result.hasErrors(), "Should have no parse errors");
        assertTrue(result.getParseErrors().isEmpty());

        // Check that source modules were extracted
        assertFalse(result.getSourceModules().isEmpty());
        assertTrue(result.getSourceModules().contains("java:com.example.Foo"));

        // Check module declarations
        assertFalse(result.getModuleDeclarations().isEmpty());
        assertTrue(result.getModuleDeclarations().stream()
            .anyMatch(md -> md.id().equals("java:com.example.Foo")));
    }

    @Test
    void testParseClassWithImport() {
        String javaCode = """
            package com.example;

            import java.util.List;

            public class Foo {
                private List<String> items;
            }
            """;

        ParseResult result = plugin.parseFromContent(
            "Foo.java",
            javaCode,
            context
        );

        // Check that no errors were reported
        assertFalse(result.hasErrors());

        // Check source module
        assertTrue(result.getSourceModules().contains("java:com.example.Foo"));

        // Check the graph has the node with namespace prefix
        DependencyGraph graph = result.getGraph();
        assertTrue(graph.containsNode("com.example.Foo"));
    }

    @Test
    void testParseClassWithExtends() {
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

        // Parse parent first
        ParseResult parentResult = plugin.parseFromContent("Parent.java", parentCode, context);
        // Parse child
        ParseResult childResult = plugin.parseFromContent("Child.java", childCode, context);

        assertFalse(childResult.hasErrors());

        // Check nodes are in child result graph
        DependencyGraph graph = childResult.getGraph();
        assertTrue(graph.containsNode("com.example.Parent"));
        assertTrue(graph.containsNode("com.example.Child"));

        // Check edge exists
        assertTrue(graph.getEdge("com.example.Child", "com.example.Parent").isPresent());

        // Check dependency declarations
        assertFalse(childResult.getDeclarations().isEmpty());
        assertTrue(childResult.getDeclarations().stream()
            .anyMatch(dd -> dd.sourceId().equals("java:com.example.Child")
                && dd.targetId().equals("java:com.example.Parent")));
    }

    @Test
    void testParseWithSyntaxError() {
        String invalidCode = """
            package com.example;

            public class Broken {
                // Missing closing brace
            """;

        ParseResult result = plugin.parseFromContent(
            "Broken.java",
            invalidCode,
            context
        );

        // Should have errors
        assertTrue(result.hasErrors() || result.getParseErrors().size() > 0);
    }

    @Test
    void testNamespacePrefixing() {
        String javaCode = """
            package test;

            public class TestClass {
                public void method() { }
            }
            """;

        ParseResult result = plugin.parseFromContent("TestClass.java", javaCode, context);

        // Verify all node IDs in source modules have "java:" prefix
        for (String moduleId : result.getSourceModules()) {
            assertTrue(moduleId.startsWith("java:"),
                "Module ID should be prefixed with 'java:': " + moduleId);
        }

        // Verify the module declaration is correct
        assertTrue(result.getSourceModules().contains("java:test.TestClass"));

        // Verify module declarations have namespace prefix
        assertTrue(result.getModuleDeclarations().stream()
            .anyMatch(md -> md.id().equals("java:test.TestClass")));
    }

    @Test
    void testModuleDeclarationsReturned() {
        String javaCode = """
            package com.example;

            public class MyClass {
                public void method() { }
            }
            """;

        ParseResult result = plugin.parseFromContent("MyClass.java", javaCode, context);

        // Should have module declarations
        assertEquals(1, result.getModuleDeclarations().size());
        ModuleDeclaration md = result.getModuleDeclarations().get(0);
        assertEquals("java:com.example.MyClass", md.id());
        assertEquals(com.archon.core.plugin.NodeType.CLASS, md.type());
        assertEquals("MyClass.java", md.sourcePath());
        assertEquals(com.archon.core.plugin.Confidence.HIGH, md.confidence());
    }

    @Test
    void testDependencyDeclarationsReturned() {
        String fooCode = """
            package com.example;
            import com.example.Bar;
            public class Foo { }
            """;
        String barCode = """
            package com.example;
            public class Bar { }
            """;

        // Parse Bar first so it's in the source set
        plugin.parseFromContent("Bar.java", barCode, context);
        ParseResult result = plugin.parseFromContent("Foo.java", fooCode, context);

        // Should have dependency declarations
        assertFalse(result.getDeclarations().isEmpty());
        DependencyDeclaration dd = result.getDeclarations().stream()
            .filter(d -> d.targetId().equals("java:com.example.Bar"))
            .findFirst()
            .orElseThrow();
        assertEquals("java:com.example.Foo", dd.sourceId());
        assertEquals(com.archon.core.plugin.EdgeType.IMPORTS, dd.edgeType());
        assertEquals(com.archon.core.plugin.Confidence.HIGH, dd.confidence());
        assertFalse(dd.dynamic());
    }
}
