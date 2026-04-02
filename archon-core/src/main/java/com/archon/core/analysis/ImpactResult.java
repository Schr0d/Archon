package com.archon.core.analysis;

import com.archon.core.graph.RiskLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Result of impact propagation from a target node.
 */
public class ImpactResult {

    public static class ImpactNode {
        private final String nodeId;
        private final String domain;
        private final int depth;
        private final RiskLevel risk;

        public ImpactNode(String nodeId, String domain, int depth, RiskLevel risk) {
            this.nodeId = nodeId;
            this.domain = domain;
            this.depth = depth;
            this.risk = risk;
        }

        public String getNodeId() { return nodeId; }
        public Optional<String> getDomain() { return Optional.ofNullable(domain); }
        public int getDepth() { return depth; }
        public RiskLevel getRisk() { return risk; }
    }

    private final String target;
    private final List<ImpactNode> impactedNodes;
    private final int maxDepthReached;
    private final int crossDomainEdges;

    public ImpactResult(String target, List<ImpactNode> impactedNodes,
                        int maxDepthReached, int crossDomainEdges) {
        this.target = target;
        this.impactedNodes = Collections.unmodifiableList(new ArrayList<>(impactedNodes));
        this.maxDepthReached = maxDepthReached;
        this.crossDomainEdges = crossDomainEdges;
    }

    public String getTarget() { return target; }
    public List<ImpactNode> getImpactedNodes() { return impactedNodes; }
    public int getMaxDepthReached() { return maxDepthReached; }
    public int getCrossDomainEdges() { return crossDomainEdges; }
    public int getTotalAffected() { return impactedNodes.size(); }
}
