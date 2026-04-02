package com.archon.core.analysis;

import com.archon.core.graph.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GraphDiffer.
 */
class GraphDifferTest {

    private final GraphDiffer differ = new GraphDiffer();

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
                .type(EdgeType.valueOf(e[2]))
                .confidence(Confidence.HIGH)
                .build());
        }
        return builder.build();
    }

    @Test
    void diff_identicalGraphs_emptyDiff() {
        DependencyGraph base = buildGraph("A", "B", "C");
        DependencyGraph head = buildGraph("A", "B", "C");

        GraphDiff diff = differ.diff(base, head);

        assertTrue(diff.isEmpty());
        assertTrue(diff.getAddedNodes().isEmpty());
        assertTrue(diff.getRemovedNodes().isEmpty());
        assertTrue(diff.getAddedEdges().isEmpty());
        assertTrue(diff.getRemovedEdges().isEmpty());
        assertTrue(diff.getNewCycles().isEmpty());
        assertTrue(diff.getFixedCycles().isEmpty());
    }

    @Test
    void diff_addedNode() {
        DependencyGraph base = buildGraph("A", "B");
        DependencyGraph head = buildGraph("A", "B", "C");

        GraphDiff diff = differ.diff(base, head);

        assertFalse(diff.isEmpty());
        assertEquals(Set.of("C"), diff.getAddedNodes());
        assertTrue(diff.getRemovedNodes().isEmpty());
        assertTrue(diff.getAddedEdges().isEmpty());
        assertTrue(diff.getRemovedEdges().isEmpty());
        assertTrue(diff.getNewCycles().isEmpty());
        assertTrue(diff.getFixedCycles().isEmpty());
    }

    @Test
    void diff_removedNode() {
        DependencyGraph base = buildGraph("A", "B", "C");
        DependencyGraph head = buildGraph("A", "B");

        GraphDiff diff = differ.diff(base, head);

        assertFalse(diff.isEmpty());
        assertTrue(diff.getAddedNodes().isEmpty());
        assertEquals(Set.of("C"), diff.getRemovedNodes());
        assertTrue(diff.getAddedEdges().isEmpty());
        assertTrue(diff.getRemovedEdges().isEmpty());
        assertTrue(diff.getNewCycles().isEmpty());
        assertTrue(diff.getFixedCycles().isEmpty());
    }

    @Test
    void diff_addedEdge() {
        DependencyGraph base = buildGraphWithEdges(new String[][]{
            {"A", "B", "IMPORTS"}
        }, "A", "B", "C");

        DependencyGraph head = buildGraphWithEdges(new String[][]{
            {"A", "B", "IMPORTS"},
            {"B", "C", "IMPORTS"}
        }, "A", "B", "C");

        GraphDiff diff = differ.diff(base, head);

        assertFalse(diff.isEmpty());
        assertTrue(diff.getAddedNodes().isEmpty());
        assertTrue(diff.getRemovedNodes().isEmpty());
        assertEquals(1, diff.getAddedEdges().size());
        Edge addedEdge = diff.getAddedEdges().iterator().next();
        assertEquals("B", addedEdge.getSource());
        assertEquals("C", addedEdge.getTarget());
        assertEquals(EdgeType.IMPORTS, addedEdge.getType());
        assertTrue(diff.getRemovedEdges().isEmpty());
        assertTrue(diff.getNewCycles().isEmpty());
        assertTrue(diff.getFixedCycles().isEmpty());
    }

    @Test
    void diff_removedEdge() {
        DependencyGraph base = buildGraphWithEdges(new String[][]{
            {"A", "B", "IMPORTS"},
            {"B", "C", "IMPORTS"}
        }, "A", "B", "C");

        DependencyGraph head = buildGraphWithEdges(new String[][]{
            {"A", "B", "IMPORTS"}
        }, "A", "B", "C");

        GraphDiff diff = differ.diff(base, head);

        assertFalse(diff.isEmpty());
        assertTrue(diff.getAddedNodes().isEmpty());
        assertTrue(diff.getRemovedNodes().isEmpty());
        assertTrue(diff.getAddedEdges().isEmpty());
        assertEquals(1, diff.getRemovedEdges().size());
        Edge removedEdge = diff.getRemovedEdges().iterator().next();
        assertEquals("B", removedEdge.getSource());
        assertEquals("C", removedEdge.getTarget());
        assertEquals(EdgeType.IMPORTS, removedEdge.getType());
        assertTrue(diff.getNewCycles().isEmpty());
        assertTrue(diff.getFixedCycles().isEmpty());
    }

    @Test
    void diff_edgeTypeChange() {
        // Edge.equals() only compares source+target, so we must detect type changes
        DependencyGraph base = buildGraphWithEdges(new String[][]{
            {"A", "B", "EXTENDS"}
        }, "A", "B");

        DependencyGraph head = buildGraphWithEdges(new String[][]{
            {"A", "B", "IMPLEMENTS"}
        }, "A", "B");

        GraphDiff diff = differ.diff(base, head);

        assertFalse(diff.isEmpty());
        assertTrue(diff.getAddedNodes().isEmpty());
        assertTrue(diff.getRemovedNodes().isEmpty());

        // Should see as removed + added because type changed
        assertEquals(1, diff.getRemovedEdges().size());
        assertEquals(1, diff.getAddedEdges().size());

        Edge removedEdge = diff.getRemovedEdges().iterator().next();
        assertEquals("A", removedEdge.getSource());
        assertEquals("B", removedEdge.getTarget());
        assertEquals(EdgeType.EXTENDS, removedEdge.getType());

        Edge addedEdge = diff.getAddedEdges().iterator().next();
        assertEquals("A", addedEdge.getSource());
        assertEquals("B", addedEdge.getTarget());
        assertEquals(EdgeType.IMPLEMENTS, addedEdge.getType());
    }

    @Test
    void diff_newCycle() {
        // Base: A -> B, no cycle
        DependencyGraph base = buildGraphWithEdges(new String[][]{
            {"A", "B", "IMPORTS"}
        }, "A", "B");

        // Head: A -> B -> A, forms cycle
        DependencyGraph head = buildGraphWithEdges(new String[][]{
            {"A", "B", "IMPORTS"},
            {"B", "A", "IMPORTS"}
        }, "A", "B");

        GraphDiff diff = differ.diff(base, head);

        assertFalse(diff.isEmpty());
        assertEquals(1, diff.getNewCycles().size());
        assertTrue(diff.getFixedCycles().isEmpty());

        // The cycle should be normalized to [A, B]
        List<String> newCycle = diff.getNewCycles().get(0);
        assertEquals(2, newCycle.size());
        // Should be normalized starting from lexicographically smallest
        assertEquals("A", newCycle.get(0));
        assertEquals("B", newCycle.get(1));
    }

    @Test
    void diff_fixedCycle() {
        // Base: A -> B -> A, has cycle
        DependencyGraph base = buildGraphWithEdges(new String[][]{
            {"A", "B", "IMPORTS"},
            {"B", "A", "IMPORTS"}
        }, "A", "B");

        // Head: A -> B only, cycle removed
        DependencyGraph head = buildGraphWithEdges(new String[][]{
            {"A", "B", "IMPORTS"}
        }, "A", "B");

        GraphDiff diff = differ.diff(base, head);

        assertFalse(diff.isEmpty());
        assertTrue(diff.getNewCycles().isEmpty());
        assertEquals(1, diff.getFixedCycles().size());

        // The fixed cycle should be normalized to [A, B]
        List<String> fixedCycle = diff.getFixedCycles().get(0);
        assertEquals(2, fixedCycle.size());
        assertEquals("A", fixedCycle.get(0));
        assertEquals("B", fixedCycle.get(1));
    }

    @Test
    void normalizeCycle_rotatesToMinElement() {
        // [C, A, B] should rotate to [A, B, C] since A is smallest
        List<String> input = List.of("C", "A", "B");
        List<String> normalized = GraphDiffer.normalizeCycle(input);

        assertEquals(3, normalized.size());
        assertEquals("A", normalized.get(0));
        assertEquals("B", normalized.get(1));
        assertEquals("C", normalized.get(2));
    }

    @Test
    void normalizeCycle_alreadyNormalized() {
        // [A, B, C] is already normalized
        List<String> input = List.of("A", "B", "C");
        List<String> normalized = GraphDiffer.normalizeCycle(input);

        assertEquals(input, normalized);
    }

    @Test
    void normalizeCycle_singleElement() {
        List<String> input = List.of("A");
        List<String> normalized = GraphDiffer.normalizeCycle(input);

        assertEquals(input, normalized);
    }

    @Test
    void normalizeCycle_twoElements() {
        // [B, A] should rotate to [A, B]
        List<String> input = List.of("B", "A");
        List<String> normalized = GraphDiffer.normalizeCycle(input);

        assertEquals(2, normalized.size());
        assertEquals("A", normalized.get(0));
        assertEquals("B", normalized.get(1));
    }

    @Test
    void normalizeCycle_duplicateMinElements() {
        // When there are duplicate minimum elements, first occurrence wins
        // [A, B, A, C] - A is min, first at index 0, so should stay as-is
        List<String> input = List.of("A", "B", "A", "C");
        List<String> normalized = GraphDiffer.normalizeCycle(input);

        // Should rotate to first A (index 0)
        assertEquals(4, normalized.size());
        assertEquals("A", normalized.get(0));
        assertEquals("B", normalized.get(1));
        assertEquals("A", normalized.get(2));
        assertEquals("C", normalized.get(3));
    }

    @Test
    void diff_complexCycle() {
        // Three-node cycle: A -> B -> C -> A
        DependencyGraph graph = buildGraphWithEdges(new String[][]{
            {"A", "B", "IMPORTS"},
            {"B", "C", "IMPORTS"},
            {"C", "A", "IMPORTS"}
        }, "A", "B", "C");

        CycleDetector detector = new CycleDetector();
        List<List<String>> cycles = detector.detectCycles(graph);

        assertEquals(1, cycles.size());
        List<String> cycle = cycles.get(0);
        assertEquals(3, cycle.size());

        // All three nodes should be in the cycle
        Set<String> cycleSet = Set.copyOf(cycle);
        assertEquals(Set.of("A", "B", "C"), cycleSet);
    }
}
