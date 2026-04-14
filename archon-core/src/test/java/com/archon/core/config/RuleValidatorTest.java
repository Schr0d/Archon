package com.archon.core.config;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuleValidatorTest {

    private Node node(String fqn) {
        return Node.builder().id(fqn).type(NodeType.CLASS).build();
    }

    private Edge edge(String from, String to) {
        return Edge.builder().source(from).target(to).type(EdgeType.IMPORTS).build();
    }

    private DependencyGraph buildGraph(Node[] nodes, Edge[] edges) {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        for (Node n : nodes) {
            builder.addNode(n);
        }
        for (Edge e : edges) {
            builder.addEdge(e);
        }
        return builder.build();
    }

    @Test
    void validate_noViolations_returnsEmptyList() {
        DependencyGraph graph = buildGraph(
            new Node[]{node("com.a.Service"), node("com.b.Client")},
            new Edge[]{edge("com.b.Client", "com.a.Service")}
        );

        ArchonConfig config = ArchonConfig.defaults();
        Map<String, String> domainMap = Map.of("com.a.Service", "domainA", "com.b.Client", "domainB");
        List<List<String>> cycles = List.of();

        List<RuleViolation> violations = new RuleValidator().validate(graph, config, domainMap, cycles);

        assertTrue(violations.isEmpty());
    }

    @Test
    void validate_cycleDetected_reportsViolation() {
        DependencyGraph graph = buildGraph(
            new Node[]{node("A"), node("B")},
            new Edge[]{edge("A", "B"), edge("B", "A")}
        );

        ArchonConfig config = ArchonConfig.defaults();
        List<List<String>> cycles = List.of(List.of("A", "B"));

        List<RuleViolation> violations = new RuleValidator().validate(
            graph, config, Map.of("A", "d1", "B", "d1"), cycles);

        assertTrue(violations.stream().anyMatch(v -> v.getRule().equals("no_cycle")));
        assertEquals("ERROR", violations.stream()
            .filter(v -> v.getRule().equals("no_cycle"))
            .findFirst().get().getSeverity());
    }

    @Test
    void validate_maxCrossDomainExceeded_reportsViolation() {
        DependencyGraph graph = buildGraph(
            new Node[]{node("com.a.Core"), node("com.b.One"), node("com.c.Two"), node("com.d.Three")},
            new Edge[]{edge("com.b.One", "com.a.Core"), edge("com.c.Two", "com.a.Core"), edge("com.d.Three", "com.a.Core")}
        );

        ArchonConfig config = ArchonConfig.defaults(); // maxCrossDomain = 2
        Map<String, String> domainMap = Map.of(
            "com.a.Core", "core",
            "com.b.One", "domain1",
            "com.c.Two", "domain2",
            "com.d.Three", "domain3"
        );

        List<RuleViolation> violations = new RuleValidator().validate(
            graph, config, domainMap, List.of());

        assertTrue(violations.stream().anyMatch(v -> v.getRule().equals("max_cross_domain")));
    }

    @Test
    void validate_maxCallDepthExceeded_reportsViolation() {
        // Chain: D -> C -> B -> A (depth 3 from A's dependents perspective)
        DependencyGraph graph = buildGraph(
            new Node[]{node("A"), node("B"), node("C"), node("D")},
            new Edge[]{edge("B", "A"), edge("C", "B"), edge("D", "C")}
        );

        ArchonConfig config = ArchonConfig.defaults(); // maxCallDepth = 3

        List<RuleViolation> violations = new RuleValidator().validate(
            graph, config, Map.of("A", "d1", "B", "d1", "C", "d1", "D", "d1"), List.of());

        assertTrue(violations.stream().anyMatch(v -> v.getRule().equals("max_call_depth")));
    }

    @Test
    void validate_forbidCoreEntityLeakage_reportsViolation() throws Exception {
        DependencyGraph graph = buildGraph(
            new Node[]{node("com.sys.SysUser"), node("com.auth.Handler")},
            new Edge[]{edge("com.auth.Handler", "com.sys.SysUser")}
        );

        Path yaml = Files.createTempFile("archon-test", ".yml");
        Files.writeString(yaml, """
            version: 1
            rules:
              forbid_core_entity_leakage:
                - com.sys.SysUser
            """);
        ArchonConfig config = ArchonConfig.loadOrDefault(yaml);
        Files.deleteIfExists(yaml);

        Map<String, String> domainMap = Map.of(
            "com.sys.SysUser", "system",
            "com.auth.Handler", "auth"
        );

        List<RuleViolation> violations = new RuleValidator().validate(
            graph, config, domainMap, List.of());

        assertTrue(violations.stream().anyMatch(v -> v.getRule().equals("forbid_core_entity_leakage")));
        assertEquals("ERROR", violations.stream()
            .filter(v -> v.getRule().equals("forbid_core_entity_leakage"))
            .findFirst().get().getSeverity());
    }

    @Test
    void validate_emptyGraph_noViolations() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();
        ArchonConfig config = ArchonConfig.defaults();

        List<RuleViolation> violations = new RuleValidator().validate(
            graph, config, Map.of(), List.of());

        assertTrue(violations.isEmpty());
    }
}
