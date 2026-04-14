package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImpactPropagatorTest {

    private Node node(String fqn) {
        return Node.builder().id(fqn).type(NodeType.CLASS).build();
    }

    private Edge edge(String from, String to) {
        return Edge.builder().source(from).target(to).type(EdgeType.IMPORTS).build();
    }

    @Test
    void propagate_singleHop_findsDirectDependents() {
        DependencyGraph graph = GraphTestBuilders.buildGraph(
            new Node[]{node("A"), node("B")},
            new Edge[]{edge("B", "A")}
        );

        ImpactResult result = new ImpactPropagator().propagate(
            graph, "A", 3, Map.of("A", "domain1", "B", "domain1"));

        assertEquals(1, result.getTotalAffected());
        assertEquals("B", result.getImpactedNodes().get(0).getNodeId());
        assertEquals(1, result.getImpactedNodes().get(0).getDepth());
    }

    @Test
    void propagate_threeHops_respectsDepthLimit() {
        DependencyGraph graph = GraphTestBuilders.buildGraph(
            new Node[]{node("A"), node("B"), node("C"), node("D")},
            new Edge[]{edge("B", "A"), edge("C", "B"), edge("D", "C")}
        );

        ImpactResult result = new ImpactPropagator().propagate(
            graph, "A", 2, Map.of("A", "d1", "B", "d1", "C", "d1", "D", "d1"));

        assertEquals(2, result.getTotalAffected());
        assertEquals(2, result.getMaxDepthReached());
    }

    @Test
    void propagate_diamondDependency_countsNodesOnce() {
        DependencyGraph graph = GraphTestBuilders.buildGraph(
            new Node[]{node("A"), node("B"), node("C"), node("D")},
            new Edge[]{edge("B", "A"), edge("C", "A"), edge("D", "B"), edge("D", "C")}
        );

        ImpactResult result = new ImpactPropagator().propagate(
            graph, "A", 3, Map.of("A", "d1", "B", "d1", "C", "d1", "D", "d1"));

        assertEquals(3, result.getTotalAffected());
    }

    @Test
    void propagate_isolatedNode_returnsEmpty() {
        DependencyGraph graph = GraphTestBuilders.buildGraph(
            new Node[]{node("A")},
            new Edge[]{}
        );

        ImpactResult result = new ImpactPropagator().propagate(
            graph, "A", 3, Map.of("A", "d1"));

        assertEquals(0, result.getTotalAffected());
    }

    @Test
    void propagate_targetNotFound_throwsException() {
        DependencyGraph graph = GraphTestBuilders.buildGraph(
            new Node[]{node("A")},
            new Edge[]{}
        );

        assertThrows(IllegalArgumentException.class, () ->
            new ImpactPropagator().propagate(graph, "NonExistent", 3, Map.of()));
    }

    @Test
    void propagate_crossDomainEdges_counted() {
        DependencyGraph graph = GraphTestBuilders.buildGraph(
            new Node[]{node("A"), node("B")},
            new Edge[]{edge("B", "A")}
        );

        ImpactResult result = new ImpactPropagator().propagate(
            graph, "A", 3, Map.of("A", "domain2", "B", "domain1"));

        assertEquals(1, result.getCrossDomainEdges());
    }

    @Test
    void propagate_sameDomainEdges_noCrossDomainCount() {
        DependencyGraph graph = GraphTestBuilders.buildGraph(
            new Node[]{node("A"), node("B")},
            new Edge[]{edge("B", "A")}
        );

        ImpactResult result = new ImpactPropagator().propagate(
            graph, "A", 3, Map.of("A", "domain1", "B", "domain1"));

        assertEquals(0, result.getCrossDomainEdges());
    }

    @Test
    void propagate_withLayerClassifier_populatesLayerField() {
        // Build a small graph
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        builder.addNode(Node.builder().id("com.example.web.UserController").type(NodeType.CLASS).build());
        builder.addNode(Node.builder().id("com.example.service.UserService").type(NodeType.CLASS).build());
        builder.addNode(Node.builder().id("com.example.entity.User").type(NodeType.CLASS).build());
        builder.addEdge(Edge.builder().source("com.example.web.UserController").target("com.example.service.UserService").type(EdgeType.IMPORTS).build());
        builder.addEdge(Edge.builder().source("com.example.service.UserService").target("com.example.entity.User").type(EdgeType.IMPORTS).build());
        DependencyGraph graph = builder.build();

        LayerClassifier classifier = new LayerClassifier();
        ImpactPropagator propagator = new ImpactPropagator(classifier);
        Map<String, String> domainMap = Map.of();

        ImpactResult result = propagator.propagate(graph, "com.example.entity.User", 3, domainMap);

        // UserService is a downstream dependent of User
        assertFalse(result.getImpactedNodes().isEmpty());
        ImpactResult.ImpactNode serviceNode = result.getImpactedNodes().stream()
            .filter(n -> n.getNodeId().equals("com.example.service.UserService"))
            .findFirst().orElse(null);
        assertNotNull(serviceNode);
        assertEquals(ArchLayer.SERVICE, serviceNode.getLayer());
    }
}
