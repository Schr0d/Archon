package com.archon.cli;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImpactCommandTest {

    private ImpactCommand command;

    @BeforeEach
    void setUp() {
        command = new ImpactCommand();
    }

    private DependencyGraph buildGraph(Node... nodes) {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        for (Node node : nodes) {
            builder.addNode(node);
        }
        return builder.build();
    }

    @Nested
    @DisplayName("resolveTarget")
    class ResolveTarget {

        @Test
        @DisplayName("exact match returns target")
        void exactMatch_returnsTarget() {
            DependencyGraph graph = buildGraph(
                Node.builder().id("com.example.Service").type(NodeType.CLASS).build()
            );

            String result = command.resolveTarget(graph, "com.example.Service");

            assertEquals("com.example.Service", result);
        }

        @Test
        @DisplayName("Java suffix match returns node ID")
        void javaSuffixMatch_returnsNodeId() {
            DependencyGraph graph = buildGraph(
                Node.builder().id("com.example.RouterStore").type(NodeType.CLASS).build()
            );

            String result = command.resolveTarget(graph, "RouterStore");

            assertEquals("com.example.RouterStore", result);
        }

        @Test
        @DisplayName("path suffix match returns node ID")
        void pathSuffixMatch_returnsNodeId() {
            DependencyGraph graph = buildGraph(
                Node.builder().id("stores/routerStore").type(NodeType.MODULE).build()
            );

            String result = command.resolveTarget(graph, "routerStore");

            assertEquals("stores/routerStore", result);
        }

        @Test
        @DisplayName("namespace prefix js: strips and matches")
        void namespacePrefixJs_stripsAndMatches() {
            DependencyGraph graph = buildGraph(
                Node.builder().id("js:stores/routerStore").type(NodeType.MODULE).build()
            );

            String result = command.resolveTarget(graph, "routerStore");

            assertEquals("js:stores/routerStore", result);
        }

        @Test
        @DisplayName("namespace prefix py: strips and matches")
        void namespacePrefixPy_stripsAndMatches() {
            DependencyGraph graph = buildGraph(
                Node.builder().id("py:src/utils/helpers").type(NodeType.MODULE).build()
            );

            String result = command.resolveTarget(graph, "helpers");

            assertEquals("py:src/utils/helpers", result);
        }

        @Test
        @DisplayName("namespace prefix java: strips and matches")
        void namespacePrefixJava_stripsAndMatches() {
            DependencyGraph graph = buildGraph(
                Node.builder().id("java:com.example.Service").type(NodeType.CLASS).build()
            );

            String result = command.resolveTarget(graph, "Service");

            assertEquals("java:com.example.Service", result);
        }

        @Test
        @DisplayName("exact match with prefix returns target")
        void exactMatchWithPrefix_returnsTarget() {
            DependencyGraph graph = buildGraph(
                Node.builder().id("js:stores/routerStore").type(NodeType.MODULE).build()
            );

            String result = command.resolveTarget(graph, "js:stores/routerStore");

            assertEquals("js:stores/routerStore", result);
        }

        @Test
        @DisplayName("no match returns null")
        void noMatch_returnsNull() {
            DependencyGraph graph = buildGraph(
                Node.builder().id("com.example.Other").type(NodeType.CLASS).build()
            );

            String result = command.resolveTarget(graph, "NonExistent");

            assertNull(result);
        }

        @Test
        @DisplayName("multiple matches returns first")
        void multipleMatches_returnsFirst() {
            DependencyGraph graph = buildGraph(
                Node.builder().id("com.foo.Service").type(NodeType.CLASS).build(),
                Node.builder().id("com.bar.Service").type(NodeType.CLASS).build()
            );

            String result = command.resolveTarget(graph, "Service");

            // Returns first match (iteration order)
            assertNotNull(result);
            assertTrue(result.endsWith(".Service"));
        }

        @Test
        @DisplayName("deeply nested path matches")
        void deeplyNestedPath_matches() {
            DependencyGraph graph = buildGraph(
                Node.builder().id("js:components/features/auth/stores/userStore").type(NodeType.MODULE).build()
            );

            String result = command.resolveTarget(graph, "userStore");

            assertEquals("js:components/features/auth/stores/userStore", result);
        }
    }

    @Nested
    @DisplayName("stripNamespacePrefix")
    class StripNamespacePrefix {

        @Test
        @DisplayName("js: prefix strips correctly")
        void jsPrefix_stripsPrefix() {
            assertEquals("stores/routerStore", command.stripNamespacePrefix("js:stores/routerStore"));
        }

        @Test
        @DisplayName("py: prefix strips correctly")
        void pyPrefix_stripsPrefix() {
            assertEquals("src/utils/helpers", command.stripNamespacePrefix("py:src/utils/helpers"));
        }

        @Test
        @DisplayName("java: prefix strips correctly")
        void javaPrefix_stripsPrefix() {
            assertEquals("com.example.Service", command.stripNamespacePrefix("java:com.example.Service"));
        }

        @Test
        @DisplayName("no prefix returns unchanged")
        void noPrefix_returnsUnchanged() {
            assertEquals("com.example.Service", command.stripNamespacePrefix("com.example.Service"));
        }

        @Test
        @DisplayName("empty string returns empty")
        void emptyString_returnsEmpty() {
            assertEquals("", command.stripNamespacePrefix(""));
        }

        @Test
        @DisplayName("colon at start returns unchanged (malformed)")
        void colonAtStart_returnsUnchanged() {
            // Leading colon is malformed - no namespace prefix
            assertEquals(":test", command.stripNamespacePrefix(":test"));
        }
    }
}
