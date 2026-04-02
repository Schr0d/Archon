package com.archon.core.analysis;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import com.archon.core.graph.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RiskSynthesizer.
 */
class RiskSynthesizerTest {

    private final RiskSynthesizer synthesizer = new RiskSynthesizer();

    private DependencyGraph buildGraph(String... nodeIds) {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        for (String id : nodeIds) {
            builder.addNode(Node.builder().id(id).type(NodeType.CLASS).build());
        }
        return builder.build();
    }

    private DependencyGraph buildGraphWithEdges(String[][] edges, String... nodeIds) {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        for (String id : nodeIds) {
            builder.addNode(Node.builder().id(id).type(NodeType.CLASS).build());
        }
        for (String[] e : edges) {
            builder.addEdge(Edge.builder()
                .source(e[0])
                .target(e[1])
                .type(EdgeType.IMPORTS)
                .confidence(Confidence.HIGH)
                .build());
        }
        return builder.build();
    }

    private GraphDiff createDiff(List<List<String>> newCycles) {
        return new GraphDiff(
            Set.of(),  // addedNodes
            Set.of(),  // removedNodes
            Set.of(),  // addedEdges
            Set.of(),  // removedEdges
            newCycles, // newCycles
            List.of()  // fixedCycles
        );
    }

    private GraphDiff createDiffWithEdges(Set<Edge> addedEdges, Set<Edge> removedEdges) {
        return new GraphDiff(
            Set.of(),
            Set.of(),
            addedEdges,
            removedEdges,
            List.of(),
            List.of()
        );
    }

    @Test
    void synthesize_noChanges_returnsLowRisk() {
        DependencyGraph graph = buildGraph("A", "B", "C");
        Map<String, String> domains = Map.of();
        Set<String> changedClasses = Set.of();
        GraphDiff diff = createDiff(List.of());
        List<String> criticalPaths = List.of();

        RiskSummary summary = synthesizer.synthesize(graph, domains, changedClasses, diff, criticalPaths);

        assertEquals(RiskLevel.LOW, summary.getOverallRisk());
        assertEquals(0, summary.getNewCycleCount());
        assertEquals(0, summary.getCrossDomainEdgeChanges());
        assertEquals(0, summary.getCriticalPathHits());
        assertTrue(summary.getPerClassRisk().isEmpty());
    }

    @Test
    void synthesize_highCouplingChangedClass_returnsHighRisk() {
        // Build graph with 11 dependents on A (exceeds threshold of 10 for HIGH)
        DependencyGraph graph = buildGraphWithEdges(
            new String[][] {
                {"B", "A"}, {"C", "A"}, {"D", "A"}, {"E", "A"}, {"F", "A"},
                {"G", "A"}, {"H", "A"}, {"I", "A"}, {"J", "A"}, {"K", "A"}, {"L", "A"}
            },
            "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L"
        );
        Map<String, String> domains = Map.of("A", "domain1");
        Set<String> changedClasses = Set.of("A");
        GraphDiff diff = createDiff(List.of());
        List<String> criticalPaths = List.of();

        RiskSummary summary = synthesizer.synthesize(graph, domains, changedClasses, diff, criticalPaths);

        assertEquals(RiskLevel.HIGH, summary.getOverallRisk());
        assertEquals(1, summary.getPerClassRisk().size());
        assertEquals(RiskLevel.HIGH, summary.getPerClassRisk().get("A"));
    }

    @Test
    void synthesize_newCycle_returnsVeryHighOverallRisk() {
        DependencyGraph graph = buildGraph("A", "B", "C");
        Map<String, String> domains = Map.of();
        Set<String> changedClasses = Set.of("A");
        // Create a cycle A -> B -> C -> A
        GraphDiff diff = createDiff(List.of(List.of("A", "B", "C", "A")));
        List<String> criticalPaths = List.of();

        RiskSummary summary = synthesizer.synthesize(graph, domains, changedClasses, diff, criticalPaths);

        assertEquals(RiskLevel.VERY_HIGH, summary.getOverallRisk());
        assertEquals(1, summary.getNewCycleCount());
        // Per-class risk should reflect being in a cycle
        assertEquals(RiskLevel.VERY_HIGH, summary.getPerClassRisk().get("A"));
    }

    @Test
    void synthesize_criticalPathClass_returnsHighRisk() {
        DependencyGraph graph = buildGraph("com.example.AuthService", "A", "B");
        Map<String, String> domains = Map.of();
        Set<String> changedClasses = Set.of("com.example.AuthService");
        GraphDiff diff = createDiff(List.of());
        List<String> criticalPaths = List.of("auth");

        RiskSummary summary = synthesizer.synthesize(graph, domains, changedClasses, diff, criticalPaths);

        assertEquals(RiskLevel.HIGH, summary.getOverallRisk());
        assertEquals(1, summary.getPerClassRisk().size());
        assertEquals(RiskLevel.HIGH, summary.getPerClassRisk().get("com.example.AuthService"));
        assertEquals(1, summary.getCriticalPathHits());
    }

    @Test
    void synthesize_crossDomainEdgeChanges_countsCorrectly() {
        DependencyGraph graph = buildGraph("A", "B", "C");
        Map<String, String> domains = Map.of(
            "A", "domain1",
            "B", "domain2",
            "C", "domain1"
        );

        // Add edge A->B (cross-domain) and remove edge C->A (same domain)
        Edge addedEdge = Edge.builder()
            .source("A")
            .target("B")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build();

        Edge removedEdge = Edge.builder()
            .source("C")
            .target("A")
            .type(EdgeType.IMPORTS)
            .confidence(Confidence.HIGH)
            .build();

        GraphDiff diff = createDiffWithEdges(Set.of(addedEdge), Set.of(removedEdge));
        Set<String> changedClasses = Set.of("A");
        List<String> criticalPaths = List.of();

        RiskSummary summary = synthesizer.synthesize(graph, domains, changedClasses, diff, criticalPaths);

        // Only A->B is cross-domain (domain1 -> domain2)
        assertEquals(1, summary.getCrossDomainEdgeChanges());
    }

    @Test
    void synthesize_multipleChangedClasses_returnsMaxRisk() {
        DependencyGraph graph = buildGraphWithEdges(
            new String[][] {
                {"B", "LowRiskClass"},
                {"C", "LowRiskClass"},
                {"D", "LowRiskClass"},
                {"E", "LowRiskClass"},
                {"F", "LowRiskClass"},
                {"G", "LowRiskClass"},
                {"H", "HighRiskClass"},
                {"I", "HighRiskClass"},
                {"J", "HighRiskClass"},
                {"K", "HighRiskClass"},
                {"L", "HighRiskClass"},
                {"M", "HighRiskClass"},
                {"N", "HighRiskClass"},
                {"O", "HighRiskClass"},
                {"P", "HighRiskClass"},
                {"Q", "HighRiskClass"},
                {"R", "HighRiskClass"}
            },
            "LowRiskClass", "HighRiskClass", "B", "C", "D", "E", "F", "G",
            "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R"
        );
        Map<String, String> domains = Map.of();
        Set<String> changedClasses = Set.of("LowRiskClass", "HighRiskClass");
        GraphDiff diff = createDiff(List.of());
        List<String> criticalPaths = List.of();

        RiskSummary summary = synthesizer.synthesize(graph, domains, changedClasses, diff, criticalPaths);

        // LowRiskClass has 6 dependents -> MEDIUM
        // HighRiskClass has 11 dependents -> HIGH
        // Overall should be max = HIGH
        assertEquals(RiskLevel.HIGH, summary.getOverallRisk());
        assertEquals(RiskLevel.MEDIUM, summary.getPerClassRisk().get("LowRiskClass"));
        assertEquals(RiskLevel.HIGH, summary.getPerClassRisk().get("HighRiskClass"));
    }

    @Test
    void synthesize_changedClassNotInGraph_skipsGracefully() {
        DependencyGraph graph = buildGraph("A", "B");
        Map<String, String> domains = Map.of();
        Set<String> changedClasses = Set.of("NonExistentClass");
        GraphDiff diff = createDiff(List.of());
        List<String> criticalPaths = List.of();

        RiskSummary summary = synthesizer.synthesize(graph, domains, changedClasses, diff, criticalPaths);

        assertEquals(RiskLevel.LOW, summary.getOverallRisk());
        assertTrue(summary.getPerClassRisk().isEmpty());
    }

    @Test
    void synthesize_crossDomainDependencyInChangedClass_countsCorrectly() {
        // Build graph where A depends on classes from other domains
        DependencyGraph graph = buildGraphWithEdges(
            new String[][] {
                {"A", "B1"},
                {"A", "B2"},
                {"A", "B3"},
                {"A", "A1"}  // same domain dependency
            },
            "A", "B1", "B2", "B3", "A1"
        );
        Map<String, String> domains = Map.of(
            "A", "domainA",
            "B1", "domainB",
            "B2", "domainB",
            "B3", "domainB",
            "A1", "domainA"
        );
        Set<String> changedClasses = Set.of("A");
        GraphDiff diff = createDiff(List.of());
        List<String> criticalPaths = List.of();

        RiskSummary summary = synthesizer.synthesize(graph, domains, changedClasses, diff, criticalPaths);

        // A has 3 cross-domain dependencies (B1, B2, B3) -> triggers HIGH risk
        assertEquals(RiskLevel.HIGH, summary.getOverallRisk());
        assertEquals(RiskLevel.HIGH, summary.getPerClassRisk().get("A"));
    }

    @Test
    void synthesize_multipleNewCycles_countsAllCycles() {
        DependencyGraph graph = buildGraph("A", "B", "C", "D");
        Map<String, String> domains = Map.of();
        Set<String> changedClasses = Set.of("A");
        GraphDiff diff = createDiff(List.of(
            List.of("A", "B", "A"),
            List.of("C", "D", "C")
        ));
        List<String> criticalPaths = List.of();

        RiskSummary summary = synthesizer.synthesize(graph, domains, changedClasses, diff, criticalPaths);

        assertEquals(RiskLevel.VERY_HIGH, summary.getOverallRisk());
        assertEquals(2, summary.getNewCycleCount());
    }

    @Test
    void synthesize_caseInsensitiveCriticalPathMatching() {
        DependencyGraph graph = buildGraph("com.example.PAYMENTService");
        Map<String, String> domains = Map.of();
        Set<String> changedClasses = Set.of("com.example.PAYMENTService");
        GraphDiff diff = createDiff(List.of());
        List<String> criticalPaths = List.of("payment");  // lowercase

        RiskSummary summary = synthesizer.synthesize(graph, domains, changedClasses, diff, criticalPaths);

        assertEquals(RiskLevel.HIGH, summary.getOverallRisk());
        assertEquals(1, summary.getCriticalPathHits());
    }
}
