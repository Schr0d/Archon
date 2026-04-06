package com.archon.viz;

import com.archon.core.graph.DependencyGraph;
import java.util.*;
import java.util.stream.Collectors;

public class PerspectiveBuilder {
    private static final String UNGROUPED_DOMAIN = "ungrouped";
    private static final String TYPE_CLASS = "CLASS";
    private static final String TYPE_MODULE = "MODULE";
    private static final String TYPE_PACKAGE = "PACKAGE";
    private static final String CONFIDENCE_HIGH = "HIGH";

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
        // Check if focusId is a domain
        if (domains.containsValue(focusId)) {
            List<NodeView> nodes = getNodesForDomain(focusId);
            List<EdgeView> edges = buildFocusEdges(nodes, focusId);
            List<String> drillable = getDrillableNodesForFocus(nodes);
            NodeGroup group = new NodeGroup(focusId, nodes);
            return new PerspectiveView(focusId, depth, List.of(group), edges, drillable);
        }

        // Check if focusId is a node in the graph
        if (!graph.getNodeIds().contains(focusId)) {
            // Non-existent node, return empty view
            return new PerspectiveView(focusId, depth, List.of(), List.of(), List.of());
        }

        // Focus is a specific node
        List<NodeView> nodes = getNodesForNode(focusId);
        List<EdgeView> edges = buildFocusEdges(nodes, focusId);
        List<String> drillable = getDrillableNodesForFocus(nodes);
        String groupLabel = domains.getOrDefault(focusId, UNGROUPED_DOMAIN);
        NodeGroup group = new NodeGroup(groupLabel, nodes);
        return new PerspectiveView(focusId, depth, List.of(group), edges, drillable);
    }

    private List<NodeGroup> groupByDomain() {
        Map<String, List<String>> domainMap = new HashMap<>();
        for (String nodeId : graph.getNodeIds()) {
            String domain = domains.getOrDefault(nodeId, UNGROUPED_DOMAIN);
            domainMap.computeIfAbsent(domain, k -> new ArrayList<>()).add(nodeId);
        }
        List<NodeGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : domainMap.entrySet()) {
            List<NodeView> nodeViews = new ArrayList<>();
            for (String nodeId : entry.getValue()) {
                nodeViews.add(createNodeView(nodeId, entry.getKey()));
            }
            groups.add(new NodeGroup(entry.getKey(), nodeViews));
        }
        return groups;
    }

    private String determineNodeType(String nodeId) {
        if (nodeId.contains(".")) return TYPE_CLASS;
        if (nodeId.endsWith(".js") || nodeId.endsWith(".ts")) return TYPE_MODULE;
        return TYPE_PACKAGE;
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
                    edgeSet.add(new EdgeView(sourceDomain, targetDomain, CONFIDENCE_HIGH, 1));
                }
            }
        }
        return new ArrayList<>(edgeSet);
    }

    private List<String> getDrillableNodes() {
        return new ArrayList<>(new HashSet<>(domains.values()));
    }

    private List<NodeView> getNodesForDomain(String domain) {
        List<NodeView> nodes = new ArrayList<>();
        for (Map.Entry<String, String> entry : domains.entrySet()) {
            if (entry.getValue().equals(domain)) {
                nodes.add(createNodeView(entry.getKey(), domain));
            }
        }
        return nodes;
    }

    private List<NodeView> getNodesForNode(String nodeId) {
        List<NodeView> nodes = new ArrayList<>();
        nodes.add(createNodeView(nodeId, domains.getOrDefault(nodeId, UNGROUPED_DOMAIN)));

        for (String dep : graph.getDependencies(nodeId)) {
            nodes.add(createNodeView(dep, domains.getOrDefault(dep, UNGROUPED_DOMAIN)));
        }
        for (String other : graph.getNodeIds()) {
            if (graph.getDependencies(other).contains(nodeId)) {
                nodes.add(createNodeView(other, domains.getOrDefault(other, UNGROUPED_DOMAIN)));
            }
        }
        return nodes;
    }

    private List<EdgeView> buildFocusEdges(List<NodeView> nodes, String focusId) {
        Set<String> nodeSet = nodes.stream().map(NodeView::id).collect(Collectors.toSet());
        List<EdgeView> edges = new ArrayList<>();
        for (NodeView node : nodes) {
            for (String dep : graph.getDependencies(node.id())) {
                if (nodeSet.contains(dep)) {
                    edges.add(new EdgeView(node.id(), dep, CONFIDENCE_HIGH, 1));
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

    private NodeView createNodeView(String nodeId, String domain) {
        return new NodeView(nodeId, determineNodeType(nodeId), domain,
            graph.getDependents(nodeId).size(), graph.getDependencies(nodeId).size());
    }
}