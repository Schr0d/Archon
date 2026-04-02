package com.archon.core.analysis;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DomainDetectorTest {

    private DependencyGraph buildGraph(String... fqns) {
        GraphBuilder builder = GraphBuilder.builder();
        for (String fqn : fqns) {
            builder.addNode(Node.builder().id(fqn).type(NodeType.CLASS).build());
        }
        return builder.build();
    }

    @Test
    void assignDomains_configOverride_assignsCorrectDomain() {
        DependencyGraph graph = buildGraph(
            "com.fuwa.framework.security.LoginService",
            "com.fuwa.system.domain.SysUser"
        );
        Map<String, List<String>> mappings = Map.of(
            "auth", List.of("com.fuwa.framework.security"),
            "system", List.of("com.fuwa.system")
        );

        DomainResult result = new DomainDetector().assignDomains(graph, mappings);

        assertEquals("auth", result.getDomain("com.fuwa.framework.security.LoginService").orElse(null));
        assertEquals("system", result.getDomain("com.fuwa.system.domain.SysUser").orElse(null));
    }

    @Test
    void assignDomains_configOverride_hasHighConfidence() {
        DependencyGraph graph = buildGraph("com.fuwa.framework.security.LoginService");
        Map<String, List<String>> mappings = Map.of("auth", List.of("com.fuwa.framework.security"));

        DomainResult result = new DomainDetector().assignDomains(graph, mappings);

        assertEquals(Confidence.HIGH, result.getConfidence("com.fuwa.framework.security.LoginService"));
    }

    @Test
    void assignDomains_packageConvention_assignsFromPackageSegment() {
        DependencyGraph graph = buildGraph("com.fuwa.system.domain.SysUser");

        DomainResult result = new DomainDetector().assignDomains(graph, Map.of());

        assertTrue(result.getDomain("com.fuwa.system.domain.SysUser").isPresent());
    }

    @Test
    void assignDomains_noMatch_lowConfidence() {
        DependencyGraph graph = buildGraph("x.Service");

        DomainResult result = new DomainDetector().assignDomains(graph, Map.of());

        assertEquals(Confidence.LOW, result.getConfidence("x.Service"));
    }

    @Test
    void assignDomains_emptyGraph_returnsEmptyResult() {
        DependencyGraph graph = GraphBuilder.builder().build();

        DomainResult result = new DomainDetector().assignDomains(graph, Map.of());

        assertEquals(0, result.size());
    }

    @Test
    void assignDomains_configTakesPriorityOverConvention() {
        DependencyGraph graph = buildGraph("com.fuwa.security.AuthHandler");
        Map<String, List<String>> mappings = Map.of("auth", List.of("com.fuwa.security"));

        DomainResult result = new DomainDetector().assignDomains(graph, mappings);

        assertEquals("auth", result.getDomain("com.fuwa.security.AuthHandler").orElse(null));
        assertEquals(Confidence.HIGH, result.getConfidence("com.fuwa.security.AuthHandler"));
    }
}