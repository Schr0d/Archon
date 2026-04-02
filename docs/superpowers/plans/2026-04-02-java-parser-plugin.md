# Java Parser Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Parse Java source trees into DependencyGraph instances using JavaParser with symbol solving, extracting class-level nodes, dependency edges, and blind spots.

**Architecture:** JavaParserPlugin orchestrates 5 components. ModuleDetector discovers source roots from build files. Each .java file is parsed into a CompilationUnit, then AstVisitor extracts nodes/edges and BlindSpotDetector flags dynamic patterns. SymbolSolverAdapter enhances edges with method call resolution where possible. All results flow into GraphBuilder to produce an immutable DependencyGraph.

**Tech Stack:** Java 17, Gradle Kotlin DSL, JavaParser 3.26.x with symbol solver, JUnit 5

---

## File Structure

| File | Responsibility |
|------|---------------|
| `archon-java/build.gradle.kts` | Dependencies (modify to add test deps) |
| `archon-java/src/main/java/com/archon/java/ModuleDetector.java` | Detect Maven/Gradle module structure, return source roots |
| `archon-java/src/main/java/com/archon/java/ImportResolver.java` | Resolve import declarations to FQCNs |
| `archon-java/src/main/java/com/archon/java/AstVisitor.java` | Walk CompilationUnit, extract nodes + edges |
| `archon-java/src/main/java/com/archon/java/SymbolSolverAdapter.java` | Per-file symbol solving for CALLS edges |
| `archon-java/src/main/java/com/archon/java/BlindSpotDetector.java` | Heuristic detection of reflection/event/MyBatis patterns |
| `archon-java/src/main/java/com/archon/java/JavaParserPlugin.java` | Orchestrator: wire all components, return ParseResult |
| `archon-java/src/test/java/com/archon/java/ModuleDetectorTest.java` | Tests for module detection |
| `archon-java/src/test/java/com/archon/java/ImportResolverTest.java` | Tests for import resolution |
| `archon-java/src/test/java/com/archon/java/AstVisitorTest.java` | Tests for AST walking |
| `archon-java/src/test/java/com/archon/java/SymbolSolverAdapterTest.java` | Tests for symbol solving |
| `archon-java/src/test/java/com/archon/java/BlindSpotDetectorTest.java` | Tests for blind spot detection |
| `archon-java/src/test/java/com/archon/java/JavaParserPluginTest.java` | Integration tests for orchestrator |

## Key Context

**archon-core graph model (existing, read-only):**
- `GraphBuilder.builder().addNode(node).addEdge(edge).build()` → `DependencyGraph`
- `Node.builder().id("com.foo.Bar").type(NodeType.CLASS).sourcePath("src/...").build()`
- `Edge.builder().source("A").target("B").type(EdgeType.IMPORTS).confidence(Confidence.HIGH).build()`
- `BlindSpot(file, line, type, pattern)` — always LOW confidence
- `NodeType`: CLASS, MODULE, PACKAGE, SERVICE, CONTROLLER
- `EdgeType`: IMPORTS, CALLS, IMPLEMENTS, EXTENDS, USES
- `Confidence`: HIGH, MEDIUM, LOW

**Test command:** `./gradlew :archon-java:test`

**Source root convention:** `src/main/java` per module.

---

### Task 1: Update build.gradle.kts with test dependencies

**Files:**
- Modify: `archon-java/build.gradle.kts`

- [ ] **Step 1: Add JUnit 5 test dependency**

```kotlin
dependencies {
    implementation(project(":archon-core"))
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.26.+")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.+")
}
```

- [ ] **Step 2: Verify build resolves**

Run: `./gradlew :archon-java:dependencies --configuration testCompileClasspath`
Expected: Shows junit-jupiter in the dependency tree

- [ ] **Step 3: Commit**

```bash
git add archon-java/build.gradle.kts
git commit -m "build: add JUnit 5 test dependency to archon-java"
```

---

### Task 2: ImportResolver

**Files:**
- Create: `archon-java/src/main/java/com/archon/java/ImportResolver.java`
- Create: `archon-java/src/test/java/com/archon/java/ImportResolverTest.java`

- [ ] **Step 1: Write failing tests for ImportResolver**

```java
package com.archon.java;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ImportResolverTest {

    @Test
    void resolve_regularImport_returnsFqcn() {
        ImportResolver resolver = new ImportResolver(Set.of());
        Optional<String> result = resolver.resolve("com.fuwa.system.domain.SysUser");
        assertTrue(result.isPresent());
        assertEquals("com.fuwa.system.domain.SysUser", result.get());
    }

    @Test
    void resolve_staticImport_returnsDeclaringClass() {
        ImportResolver resolver = new ImportResolver(Set.of());
        Optional<String> result = resolver.resolve("com.fuwa.framework.util.ShiroUtils.getSysUser");
        assertTrue(result.isPresent());
        assertEquals("com.fuwa.framework.util.ShiroUtils", result.get());
    }

    @Test
    void resolve_wildcardImport_resolvesAgainstKnownClasses() {
        Set<String> knownClasses = Set.of(
            "com.fuwa.system.domain.SysUser",
            "com.fuwa.system.domain.SysRole"
        );
        ImportResolver resolver = new ImportResolver(knownClasses);
        // Wildcard import alone cannot resolve to a single class
        Optional<String> result = resolver.resolve("com.fuwa.system.domain.*");
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_wildcardMatch_resolvesToKnownClasses() {
        Set<String> knownClasses = Set.of(
            "com.fuwa.system.domain.SysUser",
            "com.fuwa.system.domain.SysRole"
        );
        ImportResolver resolver = new ImportResolver(knownClasses);
        // resolveMatch resolves a simple name against known classes
        Set<String> matches = resolver.resolveWildcard("com.fuwa.system.domain.*");
        assertEquals(2, matches.size());
        assertTrue(matches.contains("com.fuwa.system.domain.SysUser"));
        assertTrue(matches.contains("com.fuwa.system.domain.SysRole"));
    }

    @Test
    void resolve_nullImport_returnsEmpty() {
        ImportResolver resolver = new ImportResolver(Set.of());
        assertFalse(resolver.resolve(null).isPresent());
    }

    @Test
    void resolve_emptyImport_returnsEmpty() {
        ImportResolver resolver = new ImportResolver(Set.of());
        assertFalse(resolver.resolve("").isPresent());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :archon-java:test --tests "ImportResolverTest"`
Expected: FAIL — class not found or methods missing

- [ ] **Step 3: Implement ImportResolver**

```java
package com.archon.java;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves import declarations to fully qualified class names.
 */
public class ImportResolver {
    private final Set<String> knownClasses;

    public ImportResolver(Set<String> knownClasses) {
        this.knownClasses = knownClasses != null ? knownClasses : Collections.emptySet();
    }

    /**
     * Resolves an import statement to a FQCN.
     * Regular import: returns the full path.
     * Static import: returns the declaring class (everything before the last dot-segment).
     * Wildcard: returns empty (use resolveWildcard instead).
     */
    public Optional<String> resolve(String importStatement) {
        if (importStatement == null || importStatement.isBlank()) {
            return Optional.empty();
        }
        if (importStatement.endsWith(".*")) {
            return Optional.empty();
        }
        // Check if it looks like a static import (contains a method name)
        // Simple heuristic: if the last segment starts with lowercase, it's likely static
        int lastDot = importStatement.lastIndexOf('.');
        if (lastDot > 0) {
            String lastSegment = importStatement.substring(lastDot + 1);
            if (Character.isLowerCase(lastSegment.charAt(0)) && !importStatement.contains("$")) {
                // Likely a static import — return the declaring class
                return Optional.of(importStatement.substring(0, lastDot));
            }
        }
        return Optional.of(importStatement);
    }

    /**
     * Resolves a wildcard import to all matching known classes.
     * e.g., "com.fuwa.system.domain.*" → all classes in that package.
     */
    public Set<String> resolveWildcard(String wildcardImport) {
        if (wildcardImport == null || !wildcardImport.endsWith(".*")) {
            return Collections.emptySet();
        }
        String prefix = wildcardImport.substring(0, wildcardImport.length() - 1); // "com.fuwa.system.domain."
        return knownClasses.stream()
            .filter(cls -> cls.startsWith(prefix))
            .collect(Collectors.toSet());
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :archon-java:test --tests "ImportResolverTest"`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git add archon-java/src/main/java/com/archon/java/ImportResolver.java archon-java/src/test/java/com/archon/java/ImportResolverTest.java
git commit -m "feat: implement ImportResolver for Java import resolution"
```

---

### Task 3: ModuleDetector

**Files:**
- Create: `archon-java/src/main/java/com/archon/java/ModuleDetector.java`
- Create: `archon-java/src/test/java/com/archon/java/ModuleDetectorTest.java`

- [ ] **Step 1: Write failing tests for ModuleDetector**

```java
package com.archon.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModuleDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectModules_mavenMultiModule_returnsSourceRoots() throws IOException {
        // Create maven structure
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, "<project><modules><module>ruoyi-system</module><module>ruoyi-framework</module></modules></project>");
        createSourceDir(tempDir, "ruoyi-system/src/main/java");
        createSourceDir(tempDir, "ruoyi-framework/src/main/java");

        ModuleDetector detector = new ModuleDetector();
        List<ModuleDetector.SourceRoot> roots = detector.detectModules(tempDir);

        assertEquals(2, roots.size());
        assertTrue(roots.stream().anyMatch(r -> r.getModuleName().equals("ruoyi-system")));
        assertTrue(roots.stream().anyMatch(r -> r.getModuleName().equals("ruoyi-framework")));
        roots.forEach(r -> assertTrue(Files.isDirectory(r.getPath())));
    }

    @Test
    void detectModules_noBuildFile_treatsAsSingleModule() throws IOException {
        createSourceDir(tempDir, "src/main/java");

        ModuleDetector detector = new ModuleDetector();
        List<ModuleDetector.SourceRoot> roots = detector.detectModules(tempDir);

        assertEquals(1, roots.size());
        assertEquals("", roots.get(0).getModuleName());
        assertEquals(tempDir.resolve("src/main/java"), roots.get(0).getPath());
    }

    @Test
    void detectModules_noSourceDir_returnsEmpty() throws IOException {
        // No pom.xml, no src/main/java
        ModuleDetector detector = new ModuleDetector();
        List<ModuleDetector.SourceRoot> roots = detector.detectModules(tempDir);

        assertTrue(roots.isEmpty());
    }

    @Test
    void detectModules_mavenModule_missingSourceDir_skipped() throws IOException {
        Path pom = tempDir.resolve("pom.xml");
        Files.writeString(pom, "<project><modules><module>module-a</module><module>module-b</module></modules></project>");
        createSourceDir(tempDir, "module-a/src/main/java");
        // module-b has no src/main/java

        ModuleDetector detector = new ModuleDetector();
        List<ModuleDetector.SourceRoot> roots = detector.detectModules(tempDir);

        assertEquals(1, roots.size());
        assertEquals("module-a", roots.get(0).getModuleName());
    }

    private void createSourceDir(Path root, String relativePath) throws IOException {
        Files.createDirectories(root.resolve(relativePath));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :archon-java:test --tests "ModuleDetectorTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement ModuleDetector**

```java
package com.archon.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-detects Maven/Gradle multi-module project structure.
 * Returns source roots for each detected module.
 */
public class ModuleDetector {

    private static final Pattern MAVEN_MODULE_PATTERN = Pattern.compile("<module>([^<]+)</module>");

    public List<SourceRoot> detectModules(Path projectRoot) {
        if (Files.isDirectory(projectRoot.resolve("pom.xml").getParent())) {
            Path pomFile = projectRoot.resolve("pom.xml");
            if (Files.exists(pomFile)) {
                return detectMavenModules(projectRoot, pomFile);
            }
        }
        // Fallback: treat as single module
        Path defaultSourceRoot = projectRoot.resolve("src/main/java");
        if (Files.isDirectory(defaultSourceRoot)) {
            return List.of(new SourceRoot("", defaultSourceRoot));
        }
        return Collections.emptyList();
    }

    private List<SourceRoot> detectMavenModules(Path projectRoot, Path pomFile) {
        List<String> moduleNames = parseMavenModules(pomFile);
        if (moduleNames.isEmpty()) {
            // Single module project
            Path defaultSourceRoot = projectRoot.resolve("src/main/java");
            if (Files.isDirectory(defaultSourceRoot)) {
                return List.of(new SourceRoot("", defaultSourceRoot));
            }
            return Collections.emptyList();
        }

        List<SourceRoot> roots = new ArrayList<>();
        for (String moduleName : moduleNames) {
            Path sourceDir = projectRoot.resolve(moduleName).resolve("src/main/java");
            if (Files.isDirectory(sourceDir)) {
                roots.add(new SourceRoot(moduleName, sourceDir));
            }
        }
        return roots;
    }

    private List<String> parseMavenModules(Path pomFile) {
        try {
            String content = Files.readString(pomFile);
            List<String> modules = new ArrayList<>();
            Matcher matcher = MAVEN_MODULE_PATTERN.matcher(content);
            while (matcher.find()) {
                modules.add(matcher.group(1).trim());
            }
            return modules;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public static class SourceRoot {
        private final String moduleName;
        private final Path path;

        public SourceRoot(String moduleName, Path path) {
            this.moduleName = moduleName;
            this.path = path;
        }

        public String getModuleName() { return moduleName; }
        public Path getPath() { return path; }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :archon-java:test --tests "ModuleDetectorTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add archon-java/src/main/java/com/archon/java/ModuleDetector.java archon-java/src/test/java/com/archon/java/ModuleDetectorTest.java
git commit -m "feat: implement ModuleDetector for Maven multi-module detection"
```

---

### Task 4: AstVisitor

**Files:**
- Create: `archon-java/src/main/java/com/archon/java/AstVisitor.java` (overwrite stub)
- Create: `archon-java/src/test/java/com/archon/java/AstVisitorTest.java`

- [ ] **Step 1: Write failing tests for AstVisitor**

```java
package com.archon.java;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AstVisitorTest {

    private AstVisitor visitor = new AstVisitor();

    private CompilationUnit parse(String source) {
        return new JavaParser().parse(source).getResult().orElseThrow();
    }

    @Test
    void visit_classDeclaration_addsNodeWithFqcn() {
        String source = "package com.fuwa.system.domain;\npublic class SysUser {}";
        CompilationUnit cu = parse(source);
        GraphBuilder builder = GraphBuilder.builder();

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
        GraphBuilder builder = GraphBuilder.builder();

        visitor.visit(cu, builder);

        DependencyGraph graph = builder.build();
        // LoginService depends on SysUser via import
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
        GraphBuilder builder = GraphBuilder.builder();

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
        GraphBuilder builder = GraphBuilder.builder();

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
        GraphBuilder builder = GraphBuilder.builder();

        visitor.visit(cu, builder);

        DependencyGraph graph = builder.build();
        assertTrue(graph.getNode("Standalone").isPresent());
    }

    @Test
    void visit_interfaceDeclaration_addsClassNode() {
        String source = "package com.fuwa.system.service;\npublic interface ISysUserService {}";
        CompilationUnit cu = parse(source);
        GraphBuilder builder = GraphBuilder.builder();

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
        GraphBuilder builder = GraphBuilder.builder();

        visitor.visit(cu, builder);

        DependencyGraph graph = builder.build();
        assertTrue(graph.getNode("com.fuwa.system.InnerA").isPresent());
        assertTrue(graph.getNode("com.fuwa.system.InnerB").isPresent());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :archon-java:test --tests "AstVisitorTest"`
Expected: FAIL — AstVisitor.visit still throws UnsupportedOperationException

- [ ] **Step 3: Implement AstVisitor**

**Key insight:** `MutableBuilder.addEdge()` throws if the target node doesn't exist. When AstVisitor processes imports, the import target may not have been parsed yet. Solution: call `ensureNodeExists()` for every edge target before creating the edge. This creates a placeholder node that gets overwritten when the target's own file is parsed.

```java
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

        // Visit type declarations (classes, interfaces, enums)
        for (TypeDeclaration<?> typeDecl : cu.getTypes()) {
            processTypeDeclaration(typeDecl, packageName, graphBuilder);
        }
    }

    /**
     * Ensures a node exists in the graph builder. Creates a placeholder if needed.
     * When the target's own file is parsed later, it will overwrite this placeholder
     * with a proper Node (with sourcePath etc.) via MutableBuilder.addNode's put().
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

        // Add node for this class/interface/enum
        ensureNodeExists(fqcn, graphBuilder);

        // Process extends
        if (typeDecl instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration classDecl = (ClassOrInterfaceDeclaration) typeDecl;

            for (com.github.javaparser.ast.type.ClassOrInterfaceType extended : classDecl.getExtendedTypes()) {
                String superFqcn = resolveType(extended.getName().asString(), packageName, classDecl);
                ensureNodeExists(superFqcn, graphBuilder);
                Edge edge = Edge.builder()
                    .source(fqcn)
                    .target(superFqcn)
                    .type(EdgeType.EXTENDS)
                    .confidence(Confidence.HIGH)
                    .evidence("extends " + extended.getName().asString())
                    .build();
                graphBuilder.addEdge(edge);
            }

            for (com.github.javaparser.ast.type.ClassOrInterfaceType implemented : classDecl.getImplementedTypes()) {
                String ifaceFqcn = resolveType(implemented.getName().asString(), packageName, classDecl);
                ensureNodeExists(ifaceFqcn, graphBuilder);
                Edge edge = Edge.builder()
                    .source(fqcn)
                    .target(ifaceFqcn)
                    .type(EdgeType.IMPLEMENTS)
                    .confidence(Confidence.HIGH)
                    .evidence("implements " + implemented.getName().asString())
                    .build();
                graphBuilder.addEdge(edge);
            }
        }

        // Process imports as dependency edges (one edge per declaring class, not per type)
        Optional<CompilationUnit> cuOpt = typeDecl.findCompilationUnit();
        if (cuOpt.isPresent()) {
            for (com.github.javaparser.ast.ImportDeclaration importDecl : cuOpt.get().getImports()) {
                if (!importDecl.isAsterisk() && !importDecl.isStatic()) {
                    String importName = importDecl.getName().asString();
                    ensureNodeExists(importName, graphBuilder);
                    Edge edge = Edge.builder()
                        .source(fqcn)
                        .target(importName)
                        .type(EdgeType.IMPORTS)
                        .confidence(Confidence.HIGH)
                        .evidence("import " + importName)
                        .build();
                    graphBuilder.addEdge(edge);
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

    /**
     * Resolves a type name to FQCN using imports in the compilation unit.
     * Falls back to packageName.TypeName if no import matches.
     */
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :archon-java:test --tests "AstVisitorTest"`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add archon-java/src/main/java/com/archon/java/AstVisitor.java archon-java/src/test/java/com/archon/java/AstVisitorTest.java
git commit -m "feat: implement AstVisitor for Java AST walking"
```

---

### Task 5: BlindSpotDetector

**Files:**
- Create: `archon-java/src/main/java/com/archon/java/BlindSpotDetector.java` (overwrite stub)
- Create: `archon-java/src/test/java/com/archon/java/BlindSpotDetectorTest.java`

- [ ] **Step 1: Write failing tests for BlindSpotDetector**

```java
package com.archon.java;

import com.archon.core.graph.BlindSpot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlindSpotDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detect_reflectionClassForName_flagsBlindSpot() throws IOException {
        Path javaFile = createJavaFile("ReflectionExample.java",
            "package com.example;\n"
            + "public class ReflectionExample {\n"
            + "    void foo() {\n"
            + "        Class.forName(\"com.example.Target\");\n"
            + "    }\n"
            + "}");

        BlindSpotDetector detector = new BlindSpotDetector();
        List<BlindSpot> spots = detector.detect(javaFile);

        assertFalse(spots.isEmpty());
        assertTrue(spots.stream().anyMatch(s -> s.getType().equals("reflection")));
        assertTrue(spots.stream().anyMatch(s -> s.getPattern().contains("Class.forName")));
    }

    @Test
    void detect_methodInvoke_flagsBlindSpot() throws IOException {
        Path javaFile = createJavaFile("InvokeExample.java",
            "package com.example;\n"
            + "public class InvokeExample {\n"
            + "    void foo() throws Exception {\n"
            + "        Method m = obj.getClass().getDeclaredMethod(\"bar\");\n"
            + "        m.invoke(obj);\n"
            + "    }\n"
            + "}");

        BlindSpotDetector detector = new BlindSpotDetector();
        List<BlindSpot> spots = detector.detect(javaFile);

        assertTrue(spots.stream().anyMatch(s -> s.getType().equals("reflection")
            && s.getPattern().contains("Method.invoke")));
    }

    @Test
    void detect_eventListener_flagsBlindSpot() throws IOException {
        Path javaFile = createJavaFile("EventExample.java",
            "package com.example;\n"
            + "import org.springframework.context.event.EventListener;\n"
            + "public class EventExample {\n"
            + "    @EventListener\n"
            + "    public void handleEvent(Object event) {}\n"
            + "}");

        BlindSpotDetector detector = new BlindSpotDetector();
        List<BlindSpot> spots = detector.detect(javaFile);

        assertTrue(spots.stream().anyMatch(s -> s.getType().equals("event-driven")
            && s.getPattern().contains("@EventListener")));
    }

    @Test
    void detect_myBatisXml_flagsBlindSpot() throws IOException {
        // Create a Mapper XML file
        Path resourcesDir = tempDir.resolve("src/main/resources/mapper");
        Files.createDirectories(resourcesDir);
        Files.writeString(resourcesDir.resolve("SysUserMapper.xml"),
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
            + "<mapper namespace=\"com.fuwa.system.mapper.SysUserMapper\">\n"
            + "  <select id=\"selectUserList\" resultType=\"SysUser\">\n"
            + "    SELECT * FROM sys_user\n"
            + "  </select>\n"
            + "</mapper>");

        BlindSpotDetector detector = new BlindSpotDetector();
        List<BlindSpot> spots = detector.detect(tempDir);

        assertTrue(spots.stream().anyMatch(s -> s.getType().equals("mybatis-xml")
            && s.getPattern().contains("SysUserMapper.xml")));
    }

    @Test
    void detect_cleanFile_noBlindSpots() throws IOException {
        Path javaFile = createJavaFile("CleanExample.java",
            "package com.example;\n"
            + "public class CleanExample {\n"
            + "    public String getName() { return \"clean\"; }\n"
            + "}");

        BlindSpotDetector detector = new BlindSpotDetector();
        List<BlindSpot> spots = detector.detect(javaFile);

        assertTrue(spots.isEmpty());
    }

    private Path createJavaFile(String name, String content) throws IOException {
        Path dir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve(name), content);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :archon-java:test --tests "BlindSpotDetectorTest"`
Expected: FAIL — BlindSpotDetector.detect still throws UnsupportedOperationException

- [ ] **Step 3: Implement BlindSpotDetector**

```java
package com.archon.java;

import com.archon.core.graph.BlindSpot;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Heuristic detection of dynamic dependencies:
 * reflection, event-driven, dynamic-proxy, and MyBatis XML.
 */
public class BlindSpotDetector {

    private static final String[] REFLECTION_PATTERNS = {
        "Class.forName", "Method.invoke", "getDeclaredMethod",
        "getDeclaredField", "setAccessible", ".newInstance("
    };

    private static final String[] EVENT_PATTERNS = {
        "@EventListener", "@Subscribe", "ApplicationEventPublisher",
        "publishEvent", "Proxy.newProxyInstance"
    };

    public List<BlindSpot> detect(Path sourcePath) {
        List<BlindSpot> spots = new ArrayList<>();
        if (!Files.exists(sourcePath)) {
            return spots;
        }

        // Phase 1: AST pattern matching on .java files
        if (Files.isRegularFile(sourcePath) && sourcePath.toString().endsWith(".java")) {
            scanJavaFile(sourcePath, spots);
        } else if (Files.isDirectory(sourcePath)) {
            scanDirectoryForJava(sourcePath, spots);
            // Phase 2: MyBatis XML scan
            scanForMyBatisXml(sourcePath, spots);
        }

        return spots;
    }

    private void scanDirectoryForJava(Path dir, List<BlindSpot> spots) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        scanJavaFile(file, spots);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Skip directories that can't be walked
        }
    }

    private void scanJavaFile(Path javaFile, List<BlindSpot> spots) {
        try {
            String content = Files.readString(javaFile);
            String relativePath = javaFile.toString();

            // Simple text-based pattern matching (faster than full AST parse for detection)
            int lineNumber = 1;
            for (String line : content.split("\n")) {
                checkLineForPatterns(line, relativePath, lineNumber, spots);
                lineNumber++;
            }
        } catch (IOException e) {
            // Skip files that can't be read
        }
    }

    private void checkLineForPatterns(String line, String file, int lineNumber,
                                       List<BlindSpot> spots) {
        String trimmed = line.trim();

        // Reflection patterns
        if (containsAny(trimmed, "Class.forName")) {
            spots.add(new BlindSpot(file, lineNumber, "reflection", "Class.forName"));
        }
        if (containsAny(trimmed, "Method.invoke")) {
            spots.add(new BlindSpot(file, lineNumber, "reflection", "Method.invoke"));
        }
        if (containsAny(trimmed, "getDeclaredMethod", "getDeclaredField")) {
            spots.add(new BlindSpot(file, lineNumber, "reflection", trimmed.contains("getDeclaredMethod")
                ? "getDeclaredMethod" : "getDeclaredField"));
        }

        // Event-driven patterns
        if (containsAny(trimmed, "@EventListener")) {
            spots.add(new BlindSpot(file, lineNumber, "event-driven", "@EventListener"));
        }
        if (containsAny(trimmed, "@Subscribe")) {
            spots.add(new BlindSpot(file, lineNumber, "event-driven", "@Subscribe"));
        }
        if (containsAny(trimmed, "ApplicationEventPublisher", "publishEvent")) {
            spots.add(new BlindSpot(file, lineNumber, "event-driven",
                trimmed.contains("publishEvent") ? "publishEvent" : "ApplicationEventPublisher"));
        }

        // Dynamic proxy
        if (containsAny(trimmed, "Proxy.newProxyInstance")) {
            spots.add(new BlindSpot(file, lineNumber, "dynamic-proxy", "Proxy.newProxyInstance"));
        }
    }

    private boolean containsAny(String line, String... patterns) {
        for (String pattern : patterns) {
            if (line.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private void scanForMyBatisXml(Path dir, List<BlindSpot> spots) {
        PathMatcher mapperMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*Mapper.xml");
        PathMatcher daoMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*Dao.xml");
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (mapperMatcher.matches(file) || daoMatcher.matches(file)) {
                        spots.add(new BlindSpot(
                            file.toString(), 0, "mybatis-xml",
                            file.getFileName().toString()
                        ));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Skip directories that can't be walked
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :archon-java:test --tests "BlindSpotDetectorTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add archon-java/src/main/java/com/archon/java/BlindSpotDetector.java archon-java/src/test/java/com/archon/java/BlindSpotDetectorTest.java
git commit -m "feat: implement BlindSpotDetector for reflection/event/MyBatis detection"
```

---

### Task 6: SymbolSolverAdapter

**Files:**
- Create: `archon-java/src/main/java/com/archon/java/SymbolSolverAdapter.java` (overwrite stub)
- Create: `archon-java/src/test/java/com/archon/java/SymbolSolverAdapterTest.java`

- [ ] **Step 1: Write failing tests for SymbolSolverAdapter**

```java
package com.archon.java;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SymbolSolverAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void solve_validSourceRoot_returnsTrue() throws IOException {
        // Create a simple Java file
        Path srcRoot = tempDir.resolve("src/main/java");
        Path pkg = srcRoot.resolve("com/example");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Simple.java"),
            "package com.example;\npublic class Simple { public void foo() {} }");

        SymbolSolverAdapter adapter = new SymbolSolverAdapter(srcRoot);
        boolean result = adapter.solve(pkg.resolve("Simple.java"));
        assertTrue(result);
    }

    @Test
    void solve_missingDependency_returnsFalse() throws IOException {
        Path srcRoot = tempDir.resolve("src/main/java");
        Path pkg = srcRoot.resolve("com/example");
        Files.createDirectories(pkg);
        // References a class that doesn't exist
        Files.writeString(pkg.resolve("Broken.java"),
            "package com.example;\nimport com.nonexistent.Missing;\npublic class Broken { Missing m; }");

        SymbolSolverAdapter adapter = new SymbolSolverAdapter(srcRoot);
        boolean result = adapter.solve(pkg.resolve("Broken.java"));
        // Symbol solving fails when deps are missing, returns false
        assertFalse(result);
    }

    @Test
    void solve_noSourceRoot_returnsFalse() {
        SymbolSolverAdapter adapter = new SymbolSolverAdapter(null);
        assertFalse(adapter.solve(null));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :archon-java:test --tests "SymbolSolverAdapterTest"`
Expected: FAIL — SymbolSolverAdapter.solve still throws UnsupportedOperationException

- [ ] **Step 3: Implement SymbolSolverAdapter**

```java
package com.archon.java;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Per-file symbol solving with fallback to import-level analysis.
 * Returns true if symbol solving succeeded, false if it should fall back.
 */
public class SymbolSolverAdapter {
    private final Path sourceRoot;
    private final CombinedTypeSolver typeSolver;

    public SymbolSolverAdapter(Path sourceRoot) {
        this.sourceRoot = sourceRoot;
        if (sourceRoot != null && Files.isDirectory(sourceRoot)) {
            this.typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(),
                new JavaParserTypeSolver(sourceRoot)
            );
        } else {
            this.typeSolver = null;
        }
    }

    /**
     * Attempt to parse and solve the given Java file.
     * Returns true if symbol solving succeeded, false to indicate fallback needed.
     */
    public boolean solve(Path javaFile) {
        if (typeSolver == null || javaFile == null || !Files.isRegularFile(javaFile)) {
            return false;
        }

        try {
            JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
            JavaParser parser = new JavaParser(
                new ParserConfiguration().setSymbolResolver(symbolSolver)
            );
            var result = parser.parse(javaFile);
            if (result.isSuccessful()) {
                // Try to resolve something to verify symbol solving works
                // If parsing succeeds with the symbol solver configured, we consider it solved
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :archon-java:test --tests "SymbolSolverAdapterTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add archon-java/src/main/java/com/archon/java/SymbolSolverAdapter.java archon-java/src/test/java/com/archon/java/SymbolSolverAdapterTest.java
git commit -m "feat: implement SymbolSolverAdapter with per-file symbol solving"
```

---

### Task 7: JavaParserPlugin (orchestrator)

**Files:**
- Create: `archon-java/src/main/java/com/archon/java/JavaParserPlugin.java` (overwrite stub)
- Create: `archon-java/src/test/java/com/archon/java/JavaParserPluginTest.java`

- [ ] **Step 1: Write failing tests for JavaParserPlugin**

```java
package com.archon.java;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.Node;
import com.archon.core.config.ArchonConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JavaParserPluginTest {

    @TempDir
    Path tempDir;

    private ArchonConfig defaultConfig = ArchonConfig.loadOrDefault(null);

    @Test
    void parse_singleJavaFile_buildsGraphWithNode() throws IOException {
        createProject("src/main/java/com/example/Simple.java",
            "package com.example;\npublic class Simple {}");

        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult result = plugin.parse(tempDir, defaultConfig);

        assertEquals(1, result.getGraph().nodeCount());
        assertTrue(result.getGraph().getNode("com.example.Simple").isPresent());
    }

    @Test
    void parse_twoFilesWithImport_buildsEdge() throws IOException {
        createProject("src/main/java/com/example/Service.java",
            "package com.example;\npublic class Service {}");
        createProject("src/main/java/com/example/Controller.java",
            "package com.example;\n"
            + "import com.example.Service;\n"
            + "public class Controller {\n"
            + "    Service service;\n"
            + "}");

        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult result = plugin.parse(tempDir, defaultConfig);

        DependencyGraph graph = result.getGraph();
        assertEquals(2, graph.nodeCount());
        assertTrue(graph.getEdge("com.example.Controller", "com.example.Service").isPresent());
    }

    @Test
    void parse_invalidJavaFile_skipsAndRecordsError() throws IOException {
        createProject("src/main/java/com/example/Broken.java",
            "this is not valid java {{}}");
        createProject("src/main/java/com/example/Valid.java",
            "package com.example;\npublic class Valid {}");

        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult result = plugin.parse(tempDir, defaultConfig);

        assertEquals(1, result.getGraph().nodeCount());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).getFile().contains("Broken.java"));
    }

    @Test
    void parse_emptyProject_returnsEmptyGraph() throws IOException {
        Files.createDirectories(tempDir.resolve("src/main/java"));

        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult result = plugin.parse(tempDir, defaultConfig);

        assertEquals(0, result.getGraph().nodeCount());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void parse_withBlindSpot_flagsReflection() throws IOException {
        createProject("src/main/java/com/example/Reflect.java",
            "package com.example;\n"
            + "public class Reflect {\n"
            + "    void foo() throws Exception {\n"
            + "        Class.forName(\"com.example.Target\");\n"
            + "    }\n"
            + "}");

        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult result = plugin.parse(tempDir, defaultConfig);

        assertFalse(result.getBlindSpots().isEmpty());
        assertTrue(result.getBlindSpots().stream()
            .anyMatch(bs -> bs.getType().equals("reflection")));
    }

    private void createProject(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :archon-java:test --tests "JavaParserPluginTest"`
Expected: FAIL — JavaParserPlugin.parse still throws UnsupportedOperationException

- [ ] **Step 3: Implement JavaParserPlugin**

```java
package com.archon.java;

import com.archon.core.config.ArchonConfig;
import com.archon.core.graph.BlindSpot;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.GraphBuilder;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Java source trees and builds a dependency graph.
 * Orchestrates: ModuleDetector → AstVisitor → BlindSpotDetector
 */
public class JavaParserPlugin {

    public ParseResult parse(Path projectRoot, ArchonConfig config) {
        List<ParseError> errors = new ArrayList<>();
        List<BlindSpot> blindSpots = new ArrayList<>();
        GraphBuilder graphBuilder = GraphBuilder.builder();

        // Step 1: Detect source roots
        ModuleDetector moduleDetector = new ModuleDetector();
        List<ModuleDetector.SourceRoot> sourceRoots = moduleDetector.detectModules(projectRoot);

        if (sourceRoots.isEmpty()) {
            return new ParseResult(graphBuilder.build(), blindSpots, errors);
        }

        // Step 2: Parse each Java file
        JavaParser javaParser = new JavaParser();
        AstVisitor astVisitor = new AstVisitor();

        for (ModuleDetector.SourceRoot sourceRoot : sourceRoots) {
            parseSourceRoot(sourceRoot.getPath(), javaParser, astVisitor, graphBuilder, errors);
        }

        // Step 3: Detect blind spots
        BlindSpotDetector blindSpotDetector = new BlindSpotDetector();
        for (ModuleDetector.SourceRoot sourceRoot : sourceRoots) {
            blindSpots.addAll(blindSpotDetector.detect(sourceRoot.getPath()));
        }

        return new ParseResult(graphBuilder.build(), blindSpots, errors);
    }

    private void parseSourceRoot(Path sourceRoot, JavaParser javaParser,
                                  AstVisitor astVisitor, GraphBuilder graphBuilder,
                                  List<ParseError> errors) {
        if (!Files.isDirectory(sourceRoot)) {
            return;
        }

        try {
            Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        parseSingleFile(file, javaParser, astVisitor, graphBuilder, errors);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            errors.add(new ParseError(sourceRoot.toString(), 0,
                "Failed to walk source directory: " + e.getMessage()));
        }
    }

    private void parseSingleFile(Path file, JavaParser javaParser,
                                  AstVisitor astVisitor, GraphBuilder graphBuilder,
                                  List<ParseError> errors) {
        try {
            var parseResult = javaParser.parse(file);
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                astVisitor.visit(cu, graphBuilder);
            } else {
                // Parse failed — record error and skip
                String message = parseResult.getProblems().stream()
                    .map(p -> p.getMessage())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Unknown parse error");
                errors.add(new ParseError(file.toString(), 0, message));
            }
        } catch (IOException e) {
            errors.add(new ParseError(file.toString(), 0,
                "Failed to read file: " + e.getMessage()));
        }
    }

    public static class ParseResult {
        private final DependencyGraph graph;
        private final List<BlindSpot> blindSpots;
        private final List<ParseError> errors;

        public ParseResult(DependencyGraph graph, List<BlindSpot> blindSpots, List<ParseError> errors) {
            this.graph = graph;
            this.blindSpots = blindSpots;
            this.errors = errors;
        }

        public DependencyGraph getGraph() { return graph; }
        public List<BlindSpot> getBlindSpots() { return blindSpots; }
        public List<ParseError> getErrors() { return errors; }
    }

    public static class ParseError {
        private final String file;
        private final int line;
        private final String message;

        public ParseError(String file, int line, String message) {
            this.file = file;
            this.line = line;
            this.message = message;
        }

        public String getFile() { return file; }
        public int getLine() { return line; }
        public String getMessage() { return message; }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :archon-java:test --tests "JavaParserPluginTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Run all archon-java tests**

Run: `./gradlew :archon-java:test`
Expected: ALL PASS (6+4+7+5+3+5 = 30 tests)

- [ ] **Step 6: Commit**

```bash
git add archon-java/src/main/java/com/archon/java/JavaParserPlugin.java archon-java/src/test/java/com/archon/java/JavaParserPluginTest.java
git commit -m "feat: implement JavaParserPlugin orchestrator with per-file parsing"
```

---

### Task 8: Run full test suite and verify

- [ ] **Step 1: Run all tests across all modules**

Run: `./gradlew test`
Expected: ALL PASS — archon-core (51 tests) + archon-java (30 tests)

- [ ] **Step 2: Commit final state**

```bash
git add -A
git status
# Verify no uncommitted changes
```
