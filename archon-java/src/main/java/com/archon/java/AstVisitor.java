package com.archon.java;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Walks JavaParser AST and extracts nodes + edges into a GraphBuilder.
 * Tracks added nodes to ensure edge targets exist before creating edges.
 */
public class AstVisitor {

    private final Set<String> addedNodes = new HashSet<>();

    /**
     * Visit a CompilationUnit and extract class declarations, imports, and type hierarchies.
     */
    public void visit(CompilationUnit cu, GraphBuilder graphBuilder) {
        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getName().asString())
            .orElse("");

        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            processTypeDeclaration(typeDecl, packageName, graphBuilder);
        }
    }

    /**
     * Ensures a node exists in the graph builder. Creates a placeholder if needed.
     * When the target's own file is parsed later, it will overwrite this placeholder.
     */
    private void ensureNodeExists(String fqcn, GraphBuilder graphBuilder) {
        if (!addedNodes.contains(fqcn)) {
            graphBuilder.addNode(Node.builder().id(fqcn).type(NodeType.CLASS).build());
            addedNodes.add(fqcn);
        }
    }

    private void processTypeDeclaration(TypeDeclaration<?> typeDecl, String packageName,
                                         GraphBuilder graphBuilder) {
        String fqcn = packageName.isEmpty() ? typeDecl.getName().asString()
            : packageName + "." + typeDecl.getName().asString();

        ensureNodeExists(fqcn, graphBuilder);

        // Process imports FIRST (as IMPORTS edges), then extends/implements will
        // overwrite with more specific edge types for the same source→target pair.
        Optional<CompilationUnit> cuOpt = typeDecl.findCompilationUnit();
        if (cuOpt.isPresent()) {
            for (com.github.javaparser.ast.ImportDeclaration importDecl : cuOpt.get().getImports()) {
                if (!importDecl.isAsterisk() && !importDecl.isStatic()) {
                    String importName = importDecl.getName().asString();
                    ensureNodeExists(importName, graphBuilder);
                    graphBuilder.addEdge(Edge.builder()
                        .source(fqcn)
                        .target(importName)
                        .type(EdgeType.IMPORTS)
                        .confidence(Confidence.HIGH)
                        .evidence("import " + importName)
                        .build());
                }
            }
        }

        // Process extends/implements — these overwrite IMPORTS edges for same pair
        if (typeDecl instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) typeDecl;

            for (com.github.javaparser.ast.type.ClassOrInterfaceType extended : classDecl.getExtendedTypes()) {
                String superFqcn = resolveType(extended.getName().asString(), packageName, classDecl);
                ensureNodeExists(superFqcn, graphBuilder);
                graphBuilder.addEdge(Edge.builder()
                    .source(fqcn)
                    .target(superFqcn)
                    .type(EdgeType.EXTENDS)
                    .confidence(Confidence.HIGH)
                    .evidence("extends " + extended.getName().asString())
                    .build());
            }

            for (com.github.javaparser.ast.type.ClassOrInterfaceType implemented : classDecl.getImplementedTypes()) {
                String ifaceFqcn = resolveType(implemented.getName().asString(), packageName, classDecl);
                ensureNodeExists(ifaceFqcn, graphBuilder);
                graphBuilder.addEdge(Edge.builder()
                    .source(fqcn)
                    .target(ifaceFqcn)
                    .type(EdgeType.IMPLEMENTS)
                    .confidence(Confidence.HIGH)
                    .evidence("implements " + implemented.getName().asString())
                    .build());
            }
        }

        // Process inner classes
        if (typeDecl instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) typeDecl;
            for (ClassOrInterfaceDeclaration inner : classDecl.getMembers()
                    .stream()
                    .filter(bd -> bd instanceof ClassOrInterfaceDeclaration)
                    .map(bd -> (ClassOrInterfaceDeclaration) bd)
                    .toList()) {
                processTypeDeclaration(inner, packageName, graphBuilder);
            }
            for (EnumDeclaration inner : classDecl.getMembers()
                    .stream()
                    .filter(bd -> bd instanceof EnumDeclaration)
                    .map(bd -> (EnumDeclaration) bd)
                    .toList()) {
                processTypeDeclaration(inner, packageName, graphBuilder);
            }
        }
    }

    private String resolveType(String typeName, String packageName,
                               TypeDeclaration<?> typeDecl) {
        Optional<CompilationUnit> cuOpt = typeDecl.findCompilationUnit();
        if (cuOpt.isPresent()) {
            for (com.github.javaparser.ast.ImportDeclaration importDecl : cuOpt.get().getImports()) {
                String importName = importDecl.getName().asString();
                if (importName.endsWith("." + typeName)) {
                    return importName;
                }
            }
        }
        return packageName.isEmpty() ? typeName : packageName + "." + typeName;
    }
}
