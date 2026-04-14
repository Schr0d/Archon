package com.archon.java;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import com.archon.core.plugin.DependencyDeclaration;
import com.archon.core.plugin.ModuleDeclaration;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AstVisitorTest {

    private CompilationUnit parse(String source) {
        return new JavaParser().parse(source).getResult().orElseThrow();
    }

    /**
     * Build a DependencyGraph from AstVisitor declarations, same pattern as JavaParserPlugin.
     */
    private DependencyGraph buildGraph(AstVisitor visitor) {
        List<ModuleDeclaration> modDecls = visitor.getModuleDeclarations();
        List<DependencyDeclaration> depDecls = visitor.getDependencyDeclarations();

        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        Set<String> seenIds = new HashSet<>();
        for (ModuleDeclaration decl : modDecls) {
            if (seenIds.add(decl.id())) {
                builder.addNode(Node.builder()
                    .id(decl.id())
                    .type(NodeType.valueOf(decl.type().name()))
                    .sourcePath(decl.sourcePath())
                    .confidence(Confidence.valueOf(decl.confidence().name()))
                    .build());
            }
        }
        Set<String> knownIds = new HashSet<>(builder.knownNodeIds());
        for (DependencyDeclaration decl : depDecls) {
            if (!knownIds.contains(decl.sourceId()) || !knownIds.contains(decl.targetId())) {
                continue;
            }
            builder.addEdge(Edge.builder()
                .source(decl.sourceId())
                .target(decl.targetId())
                .type(EdgeType.valueOf(decl.edgeType().name()))
                .confidence(Confidence.valueOf(decl.confidence().name()))
                .evidence(decl.evidence())
                .dynamic(decl.dynamic())
                .build());
        }
        return DependencyGraph.stripNamespacePrefixesAndBuild(builder);
    }

    @Test
    void visit_classDeclaration_addsNodeWithFqcn() {
        String source = "package com.fuwa.system.domain;\npublic class SysUser {}";
        CompilationUnit cu = parse(source);

        Set<String> sourceClasses = Set.of("com.fuwa.system.domain.SysUser");
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, "SysUser.java");

        DependencyGraph graph = buildGraph(visitor);
        Optional<Node> node = graph.getNode("com.fuwa.system.domain.SysUser");
        assertTrue(node.isPresent());
        assertEquals(NodeType.CLASS, node.get().getType());
    }

    @Test
    void visit_importStatement_addsImportEdge() {
        String source = "package com.fuwa.framework.security;\n"
            + "import com.fuwa.system.domain.SysUser;\n"
            + "public class LoginService {}";
        CompilationUnit cu = parse(source);

        Set<String> sourceClasses = Set.of(
            "com.fuwa.framework.security.LoginService",
            "com.fuwa.system.domain.SysUser"
        );
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, "LoginService.java");

        DependencyGraph graph = buildGraph(visitor);
        assertTrue(graph.getDependencies("com.fuwa.framework.security.LoginService")
            .contains("com.fuwa.system.domain.SysUser"));
        Edge edge = graph.getEdge("com.fuwa.framework.security.LoginService",
            "com.fuwa.system.domain.SysUser").orElseThrow();
        assertEquals(EdgeType.IMPORTS, edge.getType());
        assertEquals(Confidence.HIGH, edge.getConfidence());
    }

    @Test
    void visit_extendsClause_addsExtendsEdge() {
        String source = "package com.fuwa.system.service;\n"
            + "import com.fuwa.system.domain.SysUser;\n"
            + "public class SysUserServiceImpl extends SysUser {}";
        CompilationUnit cu = parse(source);

        Set<String> sourceClasses = Set.of(
            "com.fuwa.system.service.SysUserServiceImpl",
            "com.fuwa.system.domain.SysUser"
        );
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, "SysUserServiceImpl.java");

        DependencyGraph graph = buildGraph(visitor);
        Edge edge = graph.getEdge("com.fuwa.system.service.SysUserServiceImpl",
            "com.fuwa.system.domain.SysUser").orElseThrow();
        assertEquals(EdgeType.EXTENDS, edge.getType());
    }

    @Test
    void visit_implementsClause_addsImplementsEdge() {
        String source = "package com.fuwa.system.service;\n"
            + "import com.fuwa.system.service.ISysUserService;\n"
            + "public class SysUserServiceImpl implements ISysUserService {}";
        CompilationUnit cu = parse(source);

        Set<String> sourceClasses = Set.of(
            "com.fuwa.system.service.SysUserServiceImpl",
            "com.fuwa.system.service.ISysUserService"
        );
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, "SysUserServiceImpl.java");

        DependencyGraph graph = buildGraph(visitor);
        Edge edge = graph.getEdge("com.fuwa.system.service.SysUserServiceImpl",
            "com.fuwa.system.service.ISysUserService").orElseThrow();
        assertEquals(EdgeType.IMPLEMENTS, edge.getType());
    }

    @Test
    void visit_noPackage_defaultPackage() {
        String source = "public class Standalone {}";
        CompilationUnit cu = parse(source);

        Set<String> sourceClasses = Set.of("Standalone");
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, "Standalone.java");

        DependencyGraph graph = buildGraph(visitor);
        assertTrue(graph.getNode("Standalone").isPresent());
    }

    @Test
    void visit_interfaceDeclaration_addsInterfaceNode() {
        String source = "package com.fuwa.system.service;\npublic interface ISysUserService {}";
        CompilationUnit cu = parse(source);

        Set<String> sourceClasses = Set.of("com.fuwa.system.service.ISysUserService");
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, "ISysUserService.java");

        DependencyGraph graph = buildGraph(visitor);
        assertTrue(graph.getNode("com.fuwa.system.service.ISysUserService").isPresent());
        assertEquals(NodeType.INTERFACE, graph.getNode("com.fuwa.system.service.ISysUserService").get().getType());
    }

    @Test
    void visit_multipleClassesInFile_addsAllNodes() {
        String source = "package com.fuwa.system;\n"
            + "class InnerA {}\n"
            + "class InnerB {}";
        CompilationUnit cu = parse(source);

        Set<String> sourceClasses = Set.of("com.fuwa.system.InnerA", "com.fuwa.system.InnerB");
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, "InnerClasses.java");

        DependencyGraph graph = buildGraph(visitor);
        assertTrue(graph.getNode("com.fuwa.system.InnerA").isPresent());
        assertTrue(graph.getNode("com.fuwa.system.InnerB").isPresent());
    }

    @Test
    void visit_externalImport_notAddedToGraph() {
        String source = """
            package com.example;
            import java.util.List;
            public class Foo { }
            """;
        Set<String> sourceClasses = Set.of("com.example.Foo");
        AstVisitor visitor = new AstVisitor(sourceClasses);
        CompilationUnit cu = parse(source);
        visitor.visit(cu, "Foo.java");
        DependencyGraph graph = buildGraph(visitor);
        assertTrue(graph.containsNode("com.example.Foo"));
        assertFalse(graph.containsNode("java.util.List"));
        assertEquals(0, graph.edgeCount());
    }

    @Test
    void visit_sourceClassImport_addedToGraph() {
        String source = """
            package com.example;
            import com.example.Bar;
            public class Foo { }
            """;
        Set<String> sourceClasses = Set.of("com.example.Foo", "com.example.Bar");
        AstVisitor visitor = new AstVisitor(sourceClasses);
        CompilationUnit cu = parse(source);
        visitor.visit(cu, "Foo.java");
        DependencyGraph graph = buildGraph(visitor);
        assertTrue(graph.containsNode("com.example.Foo"));
        assertTrue(graph.containsNode("com.example.Bar"));
        assertEquals(1, graph.edgeCount());
    }
}
