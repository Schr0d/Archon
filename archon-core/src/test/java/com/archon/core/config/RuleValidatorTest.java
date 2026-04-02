package com.archon.core.config;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.GraphBuilder;
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

    @Test
    void validate_noViolations_returnsEmptyList() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("com.a.Service")).addNode(node("com.b.Client"))
            .addEdge(edge("com.b.Client", "com.a.Service"))
            .build();

        ArchonConfig config = ArchonConfig.defaults();
        Map<String, String> domainMap = Map.of("com.a.Service", "domainA", "com.b.Client", "domainB");
        List<List<String>> cycles = List.of();

        List<RuleViolation> violations = new RuleValidator().validate(graph, config, domainMap, cycles);

        assertTrue(violations.isEmpty());
    }

    @Test
    void validate_cycleDetected_reportsViolation() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B"))
            .addEdge(edge("A", "B")).addEdge(edge("B", "A"))
            .build();

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
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("com.a.Core"))
            .addNode(node("com.b.One")).addNode(node("com.c.Two")).addNode(node("com.d.Three"))
            .addEdge(edge("com.b.One", "com.a.Core"))
            .addEdge(edge("com.c.Two", "com.a.Core"))
            .addEdge(edge("com.d.Three", "com.a.Core"))
            .build();

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
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B"))
            .addNode(node("C")).addNode(node("D"))
            .addEdge(edge("B", "A")).addEdge(edge("C", "B"))
            .addEdge(edge("D", "C"))
            .build();

        ArchonConfig config = ArchonConfig.defaults(); // maxCallDepth = 3

        List<RuleViolation> violations = new RuleValidator().validate(
            graph, config, Map.of("A", "d1", "B", "d1", "C", "d1", "D", "d1"), List.of());

        assertTrue(violations.stream().anyMatch(v -> v.getRule().equals("max_call_depth")));
    }

    @Test
    void validate_forbidCoreEntityLeakage_reportsViolation() throws Exception {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("com.sys.SysUser")).addNode(node("com.auth.Handler"))
            .addEdge(edge("com.auth.Handler", "com.sys.SysUser"))
            .build();

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
        DependencyGraph graph = GraphBuilder.builder().build();
        ArchonConfig config = ArchonConfig.defaults();

        List<RuleViolation> violations = new RuleValidator().validate(
            graph, config, Map.of(), List.of());

        assertTrue(violations.isEmpty());
    }
}
