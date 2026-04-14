package com.archon.java;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AstVisitorTest {

    private CompilationUnit parse(String source) {
        return new JavaParser().parse(source).getResult().orElseThrow();
    }

    @Test
    void visit_classDeclaration_addsNodeWithFqcn() {
        String source = "package com.fuwa.system.domain;\npublic class SysUser {}";
        CompilationUnit cu = parse(source);
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        Set<String> sourceClasses = Set.of("com.fuwa.system.domain.SysUser");
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, builder);

        DependencyGraph graph = builder.build();
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
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        Set<String> sourceClasses = Set.of(
            "com.fuwa.framework.security.LoginService",
            "com.fuwa.system.domain.SysUser"
        );
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, builder);

        DependencyGraph graph = builder.build();
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
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        Set<String> sourceClasses = Set.of(
            "com.fuwa.system.service.SysUserServiceImpl",
            "com.fuwa.system.domain.SysUser"
        );
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, builder);

        DependencyGraph graph = builder.build();
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
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        Set<String> sourceClasses = Set.of(
            "com.fuwa.system.service.SysUserServiceImpl",
            "com.fuwa.system.service.ISysUserService"
        );
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, builder);

        DependencyGraph graph = builder.build();
        Edge edge = graph.getEdge("com.fuwa.system.service.SysUserServiceImpl",
            "com.fuwa.system.service.ISysUserService").orElseThrow();
        assertEquals(EdgeType.IMPLEMENTS, edge.getType());
    }

    @Test
    void visit_noPackage_defaultPackage() {
        String source = "public class Standalone {}";
        CompilationUnit cu = parse(source);
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        Set<String> sourceClasses = Set.of("Standalone");
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, builder);

        DependencyGraph graph = builder.build();
        assertTrue(graph.getNode("Standalone").isPresent());
    }

    @Test
    void visit_interfaceDeclaration_addsClassNode() {
        String source = "package com.fuwa.system.service;\npublic interface ISysUserService {}";
        CompilationUnit cu = parse(source);
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        Set<String> sourceClasses = Set.of("com.fuwa.system.service.ISysUserService");
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, builder);

        DependencyGraph graph = builder.build();
        assertTrue(graph.getNode("com.fuwa.system.service.ISysUserService").isPresent());
        assertEquals(NodeType.CLASS, graph.getNode("com.fuwa.system.service.ISysUserService").get().getType());
    }

    @Test
    void visit_multipleClassesInFile_addsAllNodes() {
        String source = "package com.fuwa.system;\n"
            + "class InnerA {}\n"
            + "class InnerB {}";
        CompilationUnit cu = parse(source);
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();

        Set<String> sourceClasses = Set.of("com.fuwa.system.InnerA", "com.fuwa.system.InnerB");
        AstVisitor visitor = new AstVisitor(sourceClasses);
        visitor.visit(cu, builder);

        DependencyGraph graph = builder.build();
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
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        AstVisitor visitor = new AstVisitor(sourceClasses);
        CompilationUnit cu = parse(source);
        visitor.visit(cu, builder);
        DependencyGraph graph = builder.build();
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
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        AstVisitor visitor = new AstVisitor(sourceClasses);
        CompilationUnit cu = parse(source);
        visitor.visit(cu, builder);
        DependencyGraph graph = builder.build();
        assertTrue(graph.containsNode("com.example.Foo"));
        assertTrue(graph.containsNode("com.example.Bar"));
        assertEquals(1, graph.edgeCount());
    }
}
