package com.archon.java;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
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
 * Walks JavaParser AST and extracts nodes + edges into a DependencyGraph.MutableBuilder.
 * Only creates graph nodes for classes that exist in the user's source tree,
 * silently skipping external dependencies (JDK, libraries, etc.).
 */
public class AstVisitor {

    private final Set<String> sourceClasses;
    private final Set<String> addedNodes = new HashSet<>();

    /**
     * Creates an AstVisitor that only creates nodes for classes in the given set.
     * @param sourceClasses fully-qualified class names that exist in the source tree
     */
    public AstVisitor(Set<String> sourceClasses) {
        this.sourceClasses = sourceClasses;
    }

    /**
     * Visit a CompilationUnit and extract class declarations, imports, and type hierarchies.
     */
    public void visit(CompilationUnit cu, DependencyGraph.MutableBuilder graphBuilder) {
        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getName().asString())
            .orElse("");

        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            processTypeDeclaration(typeDecl, packageName, graphBuilder);
        }
    }

    /**
     * Ensures a node exists in the graph builder, but only for source-tree classes.
     * External classes (not in sourceClasses) are silently skipped.
     * @return true if the node is a source class (exists or just added), false if external
     */
    private boolean ensureNodeExists(String fqcn, DependencyGraph.MutableBuilder graphBuilder) {
        if (!sourceClasses.contains(fqcn)) {
            return false; // external class — skip node and edge
        }
        if (!addedNodes.contains(fqcn)) {
            graphBuilder.addNode(Node.builder().id(fqcn).type(NodeType.CLASS).build());
            addedNodes.add(fqcn);
        }
        return true;
    }

    private void processTypeDeclaration(TypeDeclaration<?> typeDecl, String packageName,
                                         DependencyGraph.MutableBuilder graphBuilder) {
        String fqcn = packageName.isEmpty() ? typeDecl.getName().asString()
            : packageName + "." + typeDecl.getName().asString();

        // The class being parsed is always a source class — always add its node
        if (!addedNodes.contains(fqcn)) {
            com.archon.core.graph.NodeType nodeType;
            if (typeDecl instanceof EnumDeclaration) {
                nodeType = com.archon.core.graph.NodeType.ENUM;
            } else if (typeDecl instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) typeDecl;
                nodeType = cid.isInterface() ? com.archon.core.graph.NodeType.INTERFACE : com.archon.core.graph.NodeType.CLASS;
            } else {
                nodeType = com.archon.core.graph.NodeType.CLASS;
            }
            graphBuilder.addNode(Node.builder().id(fqcn).type(nodeType).build());
            addedNodes.add(fqcn);
        }

        // Process imports FIRST (as IMPORTS edges), then extends/implements will
        // overwrite with more specific edge types for the same source→target pair.
        Optional<CompilationUnit> cuOpt = typeDecl.findCompilationUnit();
        if (cuOpt.isPresent()) {
            for (com.github.javaparser.ast.ImportDeclaration importDecl : cuOpt.get().getImports()) {
                if (!importDecl.isAsterisk() && !importDecl.isStatic()) {
                    String importName = importDecl.getName().asString();
                    if (ensureNodeExists(importName, graphBuilder)) {
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
        }

        // Process extends/implements — these overwrite IMPORTS edges for same pair
        if (typeDecl instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) typeDecl;

            for (com.github.javaparser.ast.type.ClassOrInterfaceType extended : classDecl.getExtendedTypes()) {
                String superFqcn = resolveType(extended.getName().asString(), packageName, classDecl);
                if (ensureNodeExists(superFqcn, graphBuilder)) {
                    graphBuilder.addEdge(Edge.builder()
                        .source(fqcn)
                        .target(superFqcn)
                        .type(EdgeType.EXTENDS)
                        .confidence(Confidence.HIGH)
                        .evidence("extends " + extended.getName().asString())
                        .build());
                }
            }

            for (com.github.javaparser.ast.type.ClassOrInterfaceType implemented : classDecl.getImplementedTypes()) {
                String ifaceFqcn = resolveType(implemented.getName().asString(), packageName, classDecl);
                if (ensureNodeExists(ifaceFqcn, graphBuilder)) {
                    graphBuilder.addEdge(Edge.builder()
                        .source(fqcn)
                        .target(ifaceFqcn)
                        .type(EdgeType.IMPLEMENTS)
                        .confidence(Confidence.HIGH)
                        .evidence("implements " + implemented.getName().asString())
                        .build());
                }
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
