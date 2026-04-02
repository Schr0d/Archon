package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects strongly connected components (cycles) using Tarjan's algorithm.
 * Returns SCCs with size > 1 (single-node SCCs without self-loops are not cycles).
 * O(V+E) time complexity.
 */
public class CycleDetector {

    public List<List<String>> detectCycles(DependencyGraph graph) {
        List<List<String>> cycles = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowlink = new HashMap<>();
        Set<String> onStack = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        int[] counter = {0};

        for (String nodeId : graph.getNodeIds()) {
            if (!index.containsKey(nodeId)) {
                tarjan(nodeId, graph, index, lowlink, onStack, stack, counter, cycles);
            }
        }

        return cycles;
    }

    private void tarjan(String v, DependencyGraph graph,
                        Map<String, Integer> index, Map<String, Integer> lowlink,
                        Set<String> onStack, Deque<String> stack,
                        int[] counter, List<List<String>> cycles) {
        index.put(v, counter[0]);
        lowlink.put(v, counter[0]);
        counter[0]++;
        stack.push(v);
        onStack.add(v);

        for (String w : graph.getDependencies(v)) {
            if (!index.containsKey(w)) {
                tarjan(w, graph, index, lowlink, onStack, stack, counter, cycles);
                lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
            } else if (onStack.contains(w)) {
                lowlink.put(v, Math.min(lowlink.get(v), index.get(w)));
            }
        }

        if (lowlink.get(v).equals(index.get(v))) {
            List<String> scc = new ArrayList<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (!w.equals(v));

            if (scc.size() > 1) {
                cycles.add(scc);
            }
        }
    }
}
