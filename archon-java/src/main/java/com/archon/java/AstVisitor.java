package com.archon.java;

import com.archon.core.plugin.Confidence;
import com.archon.core.plugin.DependencyDeclaration;
import com.archon.core.plugin.EdgeType;
import com.archon.core.plugin.ModuleDeclaration;
import com.archon.core.plugin.NodeType;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Walks JavaParser AST and extracts declarations into ModuleDeclaration and
 * DependencyDeclaration lists. Only creates declarations for classes that exist
 * in the user's source tree, silently skipping external dependencies.
 *
 * <p>All IDs carry the "java:" namespace prefix.
 */
public class AstVisitor {

    private static final String NAMESPACE = "java";

    private final Set<String> sourceClasses;
    private final Set<String> addedNodeIds = new HashSet<>();
    private final List<ModuleDeclaration> moduleDeclarations = new ArrayList<>();
    private final List<DependencyDeclaration> dependencyDeclarations = new ArrayList<>();

    /**
     * Creates an AstVisitor that only creates declarations for classes in the given set.
     * @param sourceClasses fully-qualified class names that exist in the source tree
     */
    public AstVisitor(Set<String> sourceClasses) {
        this.sourceClasses = sourceClasses;
    }

    /**
     * Visit a CompilationUnit and extract class declarations, imports, and type hierarchies.
     *
     * @param cu        the compilation unit
     * @param filePath  source file path (stored in ModuleDeclaration.sourcePath)
     */
    public void visit(CompilationUnit cu, String filePath) {
        String packageName = cu.getPackageDeclaration()
            .map(pd -> pd.getName().asString())
            .orElse("");

        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            processTypeDeclaration(typeDecl, packageName, filePath, cu);
        }
    }

    public List<ModuleDeclaration> getModuleDeclarations() {
        return moduleDeclarations;
    }

    public List<DependencyDeclaration> getDependencyDeclarations() {
        return dependencyDeclarations;
    }

    /**
     * Ensures a module declaration exists, but only for source-tree classes.
     * External classes (not in sourceClasses) are silently skipped.
     * @return true if the node is a source class, false if external
     */
    private boolean ensureNodeExists(String fqcn, String filePath) {
        if (!sourceClasses.contains(fqcn)) {
            return false;
        }
        String prefixedId = NAMESPACE + ":" + fqcn;
        if (!addedNodeIds.contains(prefixedId)) {
            moduleDeclarations.add(new ModuleDeclaration(
                prefixedId,
                NodeType.CLASS,
                filePath,
                Confidence.HIGH
            ));
            addedNodeIds.add(prefixedId);
        }
        return true;
    }

    private void processTypeDeclaration(TypeDeclaration<?> typeDecl, String packageName,
                                         String filePath, CompilationUnit cu) {
        String fqcn = packageName.isEmpty() ? typeDecl.getName().asString()
            : packageName + "." + typeDecl.getName().asString();

        // The class being parsed is always a source class — always add its declaration
        String prefixedId = NAMESPACE + ":" + fqcn;
        if (!addedNodeIds.contains(prefixedId)) {
            NodeType nodeType;
            if (typeDecl instanceof EnumDeclaration) {
                nodeType = NodeType.ENUM;
            } else if (typeDecl instanceof ClassOrInterfaceDeclaration) {
                ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) typeDecl;
                nodeType = cid.isInterface() ? NodeType.INTERFACE : NodeType.CLASS;
            } else {
                nodeType = NodeType.CLASS;
            }
            moduleDeclarations.add(new ModuleDeclaration(
                prefixedId,
                nodeType,
                filePath,
                Confidence.HIGH
            ));
            addedNodeIds.add(prefixedId);
        }

        // Process imports FIRST (as IMPORTS edges), then extends/implements will
        // overwrite with more specific edge types for the same source->target pair.
        if (cu != null) {
            for (com.github.javaparser.ast.ImportDeclaration importDecl : cu.getImports()) {
                if (!importDecl.isAsterisk() && !importDecl.isStatic()) {
                    String importName = importDecl.getName().asString();
                    if (ensureNodeExists(importName, filePath)) {
                        dependencyDeclarations.add(new DependencyDeclaration(
                            prefixedId,
                            NAMESPACE + ":" + importName,
                            EdgeType.IMPORTS,
                            Confidence.HIGH,
                            "import " + importName,
                            false
                        ));
                    }
                }
            }
        }

        // Process extends/implements — these overwrite IMPORTS edges for same pair
        if (typeDecl instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) typeDecl;

            for (com.github.javaparser.ast.type.ClassOrInterfaceType extended : classDecl.getExtendedTypes()) {
                String superFqcn = resolveType(extended.getName().asString(), packageName, classDecl);
                if (ensureNodeExists(superFqcn, filePath)) {
                    dependencyDeclarations.add(new DependencyDeclaration(
                        prefixedId,
                        NAMESPACE + ":" + superFqcn,
                        EdgeType.EXTENDS,
                        Confidence.HIGH,
                        "extends " + extended.getName().asString(),
                        false
                    ));
                }
            }

            for (com.github.javaparser.ast.type.ClassOrInterfaceType implemented : classDecl.getImplementedTypes()) {
                String ifaceFqcn = resolveType(implemented.getName().asString(), packageName, classDecl);
                if (ensureNodeExists(ifaceFqcn, filePath)) {
                    dependencyDeclarations.add(new DependencyDeclaration(
                        prefixedId,
                        NAMESPACE + ":" + ifaceFqcn,
                        EdgeType.IMPLEMENTS,
                        Confidence.HIGH,
                        "implements " + implemented.getName().asString(),
                        false
                    ));
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
                processTypeDeclaration(inner, packageName, filePath, cu);
            }
            for (EnumDeclaration inner : classDecl.getMembers()
                    .stream()
                    .filter(bd -> bd instanceof EnumDeclaration)
                    .map(bd -> (EnumDeclaration) bd)
                    .toList()) {
                processTypeDeclaration(inner, packageName, filePath, cu);
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
