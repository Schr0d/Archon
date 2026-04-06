package com.archon.viz;

import com.archon.core.graph.DependencyGraph;
import java.util.*;
import java.util.stream.Collectors;

public class PerspectiveBuilder {
    private final DependencyGraph graph;
    private final Map<String, String> domains;

    public PerspectiveBuilder(DependencyGraph graph, Map<String, String> domains) {
        this.graph = graph;
        this.domains = domains != null ? domains : Map.of();
    }

    // Level 1: Domain-level view (groups by domain)
    public PerspectiveView buildPerspective() {
        List<NodeGroup> groups = groupByDomain();
        List<EdgeView> edges = buildDomainLevelEdges(groups);
        List<String> drillable = getDrillableNodes();
        return new PerspectiveView(null, 1, groups, edges, drillable);
    }

    // Level 2: Focus on specific domain or node (shows children)
    public PerspectiveView buildFocusPerspective(String focusId, int depth) {
        List<NodeView> nodes = getNodesInFocus(focusId, depth);
        List<EdgeView> edges = buildFocusEdges(nodes, focusId);
        List<String> drillable = getDrillableNodesForFocus(nodes);
        String groupLabel = domains.getOrDefault(focusId, "ungrouped");
        NodeGroup group = new NodeGroup(groupLabel, nodes);
        return new PerspectiveView(focusId, depth, List.of(group), edges, drillable);
    }

    private List<NodeGroup> groupByDomain() {
        Map<String, List<String>> domainMap = new HashMap<>();
        for (String nodeId : graph.getNodeIds()) {
            String domain = domains.getOrDefault(nodeId, "ungrouped");
            domainMap.computeIfAbsent(domain, k -> new ArrayList<>()).add(nodeId);
        }
        List<NodeGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : domainMap.entrySet()) {
            List<NodeView> nodeViews = new ArrayList<>();
            for (String nodeId : entry.getValue()) {
                nodeViews.add(new NodeView(nodeId, determineNodeType(nodeId),
                    entry.getKey(), graph.getDependents(nodeId).size(),
                    graph.getDependencies(nodeId).size()));
            }
            groups.add(new NodeGroup(entry.getKey(), nodeViews));
        }
        return groups;
    }

    private String determineNodeType(String nodeId) {
        if (nodeId.contains(".")) return "CLASS";
        if (nodeId.endsWith(".js") || nodeId.endsWith(".ts")) return "MODULE";
        return "PACKAGE";
    }

    private List<EdgeView> buildDomainLevelEdges(List<NodeGroup> groups) {
        Set<EdgeView> edgeSet = new HashSet<>();
        Map<String, String> nodeToDomain = new HashMap<>();
        for (NodeGroup group : groups) {
            for (NodeView node : group.nodes()) {
                nodeToDomain.put(node.id(), group.label());
            }
        }
        for (String source : graph.getNodeIds()) {
            for (String target : graph.getDependencies(source)) {
                String sourceDomain = nodeToDomain.get(source);
                String targetDomain = nodeToDomain.get(target);
                if (sourceDomain != null && targetDomain != null && !sourceDomain.equals(targetDomain)) {
                    edgeSet.add(new EdgeView(sourceDomain, targetDomain, "HIGH", 1));
                }
            }
        }
        return new ArrayList<>(edgeSet);
    }

    private List<String> getDrillableNodes() {
        List<String> drillable = new ArrayList<>();
        for (String domain : new HashSet<>(domains.values())) {
            drillable.add(domain);
        }
        return drillable;
    }

    private List<NodeView> getNodesInFocus(String focusId, int depth) {
        List<String> nodeIds;
        if (domains.containsValue(focusId)) {
            // Focus is a domain - return all nodes in that domain
            nodeIds = new ArrayList<>();
            for (Map.Entry<String, String> entry : domains.entrySet()) {
                if (entry.getValue().equals(focusId)) {
                    nodeIds.add(entry.getKey());
                }
            }
        } else {
            // Focus is a specific node
            nodeIds = new ArrayList<>();
            nodeIds.add(focusId);
            // Add direct dependencies
            for (String dep : graph.getDependencies(focusId)) {
                nodeIds.add(dep);
            }
            // Add direct dependents
            for (String nodeId : graph.getNodeIds()) {
                if (graph.getDependencies(nodeId).contains(focusId)) {
                    nodeIds.add(nodeId);
                }
            }
        }
        List<NodeView> nodes = new ArrayList<>();
        for (String nodeId : nodeIds) {
            nodes.add(new NodeView(nodeId, determineNodeType(nodeId),
                domains.getOrDefault(nodeId, "ungrouped"),
                graph.getDependents(nodeId).size(), graph.getDependencies(nodeId).size()));
        }
        return nodes;
    }

    private List<EdgeView> buildFocusEdges(List<NodeView> nodes, String focusId) {
        Set<String> nodeSet = nodes.stream().map(NodeView::id).collect(Collectors.toSet());
        List<EdgeView> edges = new ArrayList<>();
        for (NodeView node : nodes) {
            for (String dep : graph.getDependencies(node.id())) {
                if (nodeSet.contains(dep)) {
                    edges.add(new EdgeView(node.id(), dep, "HIGH", 1));
                }
            }
        }
        return edges;
    }

    private List<String> getDrillableNodesForFocus(List<NodeView> nodes) {
        return nodes.stream()
            .filter(n -> graph.getDependencies(n.id()).size() > 0 || graph.getDependents(n.id()).size() > 0)
            .map(NodeView::id)
            .collect(Collectors.toList());
    }
}