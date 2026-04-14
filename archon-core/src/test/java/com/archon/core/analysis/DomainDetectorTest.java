package com.archon.core.analysis;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DomainDetectorTest {

    private DependencyGraph buildGraph(String... fqns) {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
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

        assertEquals("system", result.getDomain("com.fuwa.system.domain.SysUser").orElse(null));
        assertEquals(Confidence.MEDIUM, result.getConfidence("com.fuwa.system.domain.SysUser"));
    }

    @Test
    void assignDomains_noMatch_lowConfidence() {
        DependencyGraph graph = buildGraph("x.Service");

        DomainResult result = new DomainDetector().assignDomains(graph, Map.of());

        assertEquals("x", result.getDomain("x.Service").orElse(null));
        assertEquals(Confidence.LOW, result.getConfidence("x.Service"));
    }

    @Test
    void assignDomains_emptyGraph_returnsEmptyResult() {
        DependencyGraph graph = new DependencyGraph.MutableBuilder().build();

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

    @Test
    void assignDomains_pivotDetection_findsCorrectDepth() {
        // geditor-api-like structure: 5 logical modules at depth 3
        DependencyGraph graph = buildGraph(
            "com.fuwa.common.utils.StringUtils",
            "com.fuwa.framework.web.controller.BaseController",
            "com.fuwa.project.docmd.controller.DocController",
            "com.fuwa.domain.system.domain.SysUser",
            "com.fuwa.api.controller.ApiController"
        );

        DomainResult result = new DomainDetector().assignDomains(graph, Map.of());

        assertEquals("common", result.getDomain("com.fuwa.common.utils.StringUtils").orElse(null));
        assertEquals("framework", result.getDomain("com.fuwa.framework.web.controller.BaseController").orElse(null));
        assertEquals("project", result.getDomain("com.fuwa.project.docmd.controller.DocController").orElse(null));
        assertEquals("domain", result.getDomain("com.fuwa.domain.system.domain.SysUser").orElse(null));
        assertEquals("api", result.getDomain("com.fuwa.api.controller.ApiController").orElse(null));
    }

    @Test
    void assignDomains_pivotDetection_deepProject() {
        // Project where pivot is at depth 4, not 3
        DependencyGraph graph = buildGraph(
            "com.company.modules.auth.service.AuthService",
            "com.company.modules.user.service.UserService",
            "com.company.modules.order.service.OrderService",
            "com.company.modules.payment.service.PaymentService",
            "com.company.modules.inventory.service.InventoryService"
        );

        DomainResult result = new DomainDetector().assignDomains(graph, Map.of());

        // depth 3 = "modules" (1 segment) — too few
        // depth 4 = auth, user, order, payment, inventory (5 segments) — pivot!
        assertEquals("auth", result.getDomain("com.company.modules.auth.service.AuthService").orElse(null));
        assertEquals("user", result.getDomain("com.company.modules.user.service.UserService").orElse(null));
        assertEquals("order", result.getDomain("com.company.modules.order.service.OrderService").orElse(null));
    }

    @Test
    void assignDomains_noPivot_fallsBackToThirdSegment() {
        // Only 2 distinct segments at every depth — no pivot found
        DependencyGraph graph = buildGraph(
            "com.myapp.service.Foo",
            "com.myapp.service.Bar"
        );

        DomainResult result = new DomainDetector().assignDomains(graph, Map.of());

        // No pivot (depth 2 = "myapp", 1 segment; depth 3 = "service", 1 segment)
        // Falls back to old behavior: 3rd segment (index 2) for 4+ deep packages
        assertEquals("service", result.getDomain("com.myapp.service.Foo").orElse(null));
    }
}
