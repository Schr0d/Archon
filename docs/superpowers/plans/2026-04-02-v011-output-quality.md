# v0.1.1 Output Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Archon's output actionable on real projects by filtering external classes, auto-scaling thresholds, improving domain detection, and polishing output.

**Architecture:** Four surgical fixes applied to existing modules. External filtering happens during parsing (AstVisitor receives a source-class set). Adaptive thresholds are a new `Thresholds` value object computed from graph stats. Domain detection gains a pivot-finding heuristic. Output formatting is improved in CLI commands.

**Tech Stack:** Java 17, Gradle Kotlin DSL, JavaParser 3.26.x, JUnit 5, picocli 4.7.x

**Design doc:** `~/.gstack/projects/Archon/ThinkPad-master-design-20260402-133035.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `archon-core/.../analysis/Thresholds.java` | **Create** | Value object holding adaptive threshold values |
| `archon-core/.../analysis/ThresholdCalculator.java` | **Create** | Computes thresholds from graph statistics |
| `archon-core/.../graph/DependencyGraph.java` | Modify | `addEdge()` skips when target node missing |
| `archon-core/.../analysis/DomainDetector.java` | Modify | Pivot detection heuristic replaces fixed 3rd-segment |
| `archon-core/.../analysis/CouplingAnalyzer.java` | Modify | Accept `Thresholds` instead of raw int |
| `archon-core/.../config/RuleValidator.java` | Modify | Use adaptive crossDomainMax from Thresholds |
| `archon-core/.../config/ArchonConfig.java` | Modify | `getMaxCrossDomain()` returns -1 sentinel for "auto" |
| `archon-java/.../AstVisitor.java` | Modify | Accept `Set<String> sourceClasses`, skip non-source nodes |
| `archon-java/.../JavaParserPlugin.java` | Modify | Collect source FQCNs in first pass, pass to AstVisitor |
| `archon-cli/.../AnalyzeCommand.java` | Modify | Use ThresholdCalculator, group blind spots, cap hotspots |
| `archon-cli/.../CheckCommand.java` | Modify | Use adaptive thresholds from ThresholdCalculator |
| `archon-core/.../test/ThresholdCalculatorTest.java` | **Create** | Tests for adaptive threshold formulas |
| `archon-core/.../test/DomainDetectorTest.java` | Modify | Add pivot detection tests |
| `archon-java/.../test/AstVisitorTest.java` | Modify | Add external class filtering tests |
| `archon-java/.../test/JavaParserPluginTest.java` | Modify | Update for new two-pass API |

---

### Task 1: Make DependencyGraph.addEdge() skip missing targets

**Files:**
- Modify: `archon-core/src/main/java/com/archon/core/graph/DependencyGraph.java:116-117`

This unblocks external class filtering — when AstVisitor skips non-source nodes, edges targeting them will hit "target not found" and need to be silently skipped.

- [ ] **Step 1: Write the failing test**

Add to `archon-core/src/test/java/com/archon/core/graph/DependencyGraphTest.java`:

```java
package com.archon.core.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DependencyGraphTest {

    @Test
    void addEdge_targetNodeMissing_edgeSkipped() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(Node.builder().id("A").type(NodeType.CLASS).build())
            .addEdge(Edge.builder().source("A").target("Missing").type(EdgeType.IMPORTS).build())
            .build();

        assertEquals(1, graph.nodeCount());
        assertEquals(0, graph.edgeCount());
    }

    @Test
    void addEdge_sourceNodeMissing_throws() {
        GraphBuilder builder = GraphBuilder.builder();

        assertThrows(IllegalArgumentException.class, () ->
            builder.addEdge(Edge.builder().source("Missing").target("A").type(EdgeType.IMPORTS).build())
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-core:test --tests DependencyGraphTest -q 2>&1 | tail -20`
Expected: FAIL — `addEdge` currently throws `IllegalArgumentException: Edge target node not found: Missing`

- [ ] **Step 3: Change addEdge to skip missing targets**

In `archon-core/src/main/java/com/archon/core/graph/DependencyGraph.java`, change lines 116-117 from:

```java
            if (!nodes.containsKey(tgt)) {
                throw new IllegalArgumentException("Edge target node not found: " + tgt);
            }
```

to:

```java
            if (!nodes.containsKey(tgt)) {
                return this; // skip edges to missing targets (external classes)
            }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-core:test --tests DependencyGraphTest -q 2>&1 | tail -5`
Expected: PASS

- [ ] **Step 5: Run all tests to check for regressions**

Run: `./gradlew test -q 2>&1 | tail -20`
Expected: All tests pass (existing tests that relied on target-missing throws will need updating — check output)

- [ ] **Step 6: Commit**

```bash
git add archon-core/src/main/java/com/archon/core/graph/DependencyGraph.java archon-core/src/test/java/com/archon/core/graph/DependencyGraphTest.java
git commit -m "feat: skip edges to missing target nodes instead of throwing

Enables external class filtering — edges to classes not in the
source tree are silently skipped during graph construction."
```

---

### Task 2: Create Thresholds value object and ThresholdCalculator

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/analysis/Thresholds.java`
- Create: `archon-core/src/main/java/com/archon/core/analysis/ThresholdCalculator.java`
- Create: `archon-core/src/test/java/com/archon/core/analysis/ThresholdCalculatorTest.java`

- [ ] **Step 1: Write the failing test**

Create `archon-core/src/test/java/com/archon/core/analysis/ThresholdCalculatorTest.java`:

```java
package com.archon.core.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThresholdCalculatorTest {

    @Test
    void calculate_smallProject_strictThresholds() {
        Thresholds t = ThresholdCalculator.calculate(50, 3);

        assertEquals(3, t.getCouplingThreshold());   // max(3, 50/100) = max(3,0) = 3
        assertEquals(2, t.getCrossDomainMax());       // max(2, ceil(3*0.3)) = max(2,1) = 2
        assertEquals(4, t.getMaxCallDepth());
        assertEquals(5, t.getHotspotDisplayCap());     // min(20, 50/10) = min(20,5) = 5
    }

    @Test
    void calculate_mediumProject_moderateThresholds() {
        Thresholds t = ThresholdCalculator.calculate(200, 5);

        assertEquals(3, t.getCouplingThreshold());   // max(3, 200/100) = max(3,2) = 3
        assertEquals(2, t.getCrossDomainMax());       // max(2, ceil(5*0.3)) = max(2,2) = 2
        assertEquals(20, t.getHotspotDisplayCap());   // min(20, 200/10) = min(20,20) = 20
    }

    @Test
    void calculate_largeProject_relaxedThresholds() {
        Thresholds t = ThresholdCalculator.calculate(1000, 71);

        assertEquals(10, t.getCouplingThreshold());  // max(3, 1000/100) = max(3,10) = 10
        assertEquals(22, t.getCrossDomainMax());      // max(2, ceil(71*0.3)) = max(2,22) = 22
        assertEquals(20, t.getHotspotDisplayCap());   // min(20, 1000/10) = min(20,100) = 20
    }

    @Test
    void calculate_singleDomain_crossDomainMaxFloor() {
        Thresholds t = ThresholdCalculator.calculate(10, 1);

        assertEquals(2, t.getCrossDomainMax());       // floor is 2
    }

    @Test
    void thresholds_hasGetterForAllFields() {
        Thresholds t = new Thresholds(5, 3, 4, 10);

        assertEquals(5, t.getCouplingThreshold());
        assertEquals(3, t.getCrossDomainMax());
        assertEquals(4, t.getMaxCallDepth());
        assertEquals(10, t.getHotspotDisplayCap());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-core:test --tests ThresholdCalculatorTest -q 2>&1 | tail -10`
Expected: FAIL — classes don't exist

- [ ] **Step 3: Create Thresholds value object**

Create `archon-core/src/main/java/com/archon/core/analysis/Thresholds.java`:

```java
package com.archon.core.analysis;

/**
 * Immutable threshold values for analysis engines.
 * Computed by ThresholdCalculator based on project size, or overridden via config.
 */
public class Thresholds {
    private final int couplingThreshold;
    private final int crossDomainMax;
    private final int maxCallDepth;
    private final int hotspotDisplayCap;

    public Thresholds(int couplingThreshold, int crossDomainMax,
                      int maxCallDepth, int hotspotDisplayCap) {
        this.couplingThreshold = couplingThreshold;
        this.crossDomainMax = crossDomainMax;
        this.maxCallDepth = maxCallDepth;
        this.hotspotDisplayCap = hotspotDisplayCap;
    }

    public int getCouplingThreshold() { return couplingThreshold; }
    public int getCrossDomainMax() { return crossDomainMax; }
    public int getMaxCallDepth() { return maxCallDepth; }
    public int getHotspotDisplayCap() { return hotspotDisplayCap; }
}
```

- [ ] **Step 4: Create ThresholdCalculator**

Create `archon-core/src/main/java/com/archon/core/analysis/ThresholdCalculator.java`:

```java
package com.archon.core.analysis;

/**
 * Computes adaptive thresholds based on project size.
 * Larger projects get more relaxed thresholds to avoid flooding the user with warnings.
 */
public class ThresholdCalculator {

    public static Thresholds calculate(int nodeCount, int domainCount) {
        int couplingThreshold = Math.max(3, nodeCount / 100);
        int crossDomainMax = Math.max(2, (int) Math.ceil(domainCount * 0.3));
        int maxCallDepth = 4;
        int hotspotDisplayCap = Math.min(20, Math.max(5, nodeCount / 10));

        return new Thresholds(couplingThreshold, crossDomainMax, maxCallDepth, hotspotDisplayCap);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :archon-core:test --tests ThresholdCalculatorTest -q 2>&1 | tail -10`
Expected: PASS (all 5 tests)

- [ ] **Step 6: Commit**

```bash
git add archon-core/src/main/java/com/archon/core/analysis/Thresholds.java archon-core/src/main/java/com/archon/core/analysis/ThresholdCalculator.java archon-core/src/test/java/com/archon/core/analysis/ThresholdCalculatorTest.java
git commit -m "feat: add adaptive ThresholdCalculator for project-size scaling"
```

---

### Task 3: External class filtering in AstVisitor

**Files:**
- Modify: `archon-java/src/main/java/com/archon/java/AstVisitor.java`
- Modify: `archon-java/src/main/java/com/archon/java/JavaParserPlugin.java`
- Modify: `archon-java/src/test/java/com/archon/java/AstVisitorTest.java`
- Modify: `archon-java/src/test/java/com/archon/java/JavaParserPluginTest.java`

This is the biggest impact change. AstVisitor will receive a `Set<String> sourceClasses` containing only FQCNs that exist in the source tree. It will only create nodes and edges for classes in that set.

- [ ] **Step 1: Write the failing tests in AstVisitorTest**

Add these tests to `archon-java/src/test/java/com/archon/java/AstVisitorTest.java`:

```java
@Test
void visit_externalImport_notAddedToGraph() {
    // Setup: a class that imports java.util.List
    String source = """
        package com.example;
        import java.util.List;
        public class Foo { }
        """;

    Set<String> sourceClasses = Set.of("com.example.Foo");
    GraphBuilder builder = GraphBuilder.builder();
    AstVisitor visitor = new AstVisitor(sourceClasses);
    CompilationUnit cu = new JavaParser().parse(source).getResult().get();
    visitor.visit(cu, builder);

    DependencyGraph graph = builder.build();
    assertTrue(graph.containsNode("com.example.Foo"));
    assertFalse(graph.containsNode("java.util.List"));
    assertEquals(0, graph.edgeCount()); // edge to List skipped
}

@Test
void visit_sourceClassImport_addedToGraph() {
    // Setup: a class that imports another source class
    String source = """
        package com.example;
        import com.example.Bar;
        public class Foo { }
        """;

    Set<String> sourceClasses = Set.of("com.example.Foo", "com.example.Bar");
    GraphBuilder builder = GraphBuilder.builder();
    AstVisitor visitor = new AstVisitor(sourceClasses);
    CompilationUnit cu = new JavaParser().parse(source).getResult().get();
    visitor.visit(cu, builder);

    DependencyGraph graph = builder.build();
    assertTrue(graph.containsNode("com.example.Foo"));
    assertTrue(graph.containsNode("com.example.Bar"));
    assertEquals(1, graph.edgeCount());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-java:test --tests AstVisitorTest -q 2>&1 | tail -10`
Expected: FAIL — `AstVisitor()` no-arg constructor doesn't exist, `Set<String>` constructor doesn't exist

- [ ] **Step 3: Modify AstVisitor to accept sourceClasses**

In `archon-java/src/main/java/com/archon/java/AstVisitor.java`, replace the entire class with:

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
 * Only creates nodes for classes in the sourceClasses set (external classes are filtered out).
 */
public class AstVisitor {

    private final Set<String> addedNodes = new HashSet<>();
    private final Set<String> sourceClasses;

    /**
     * Create visitor that only graphs classes in sourceClasses.
     */
    public AstVisitor(Set<String> sourceClasses) {
        this.sourceClasses = sourceClasses;
    }

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
     * Ensures a node exists in the graph builder ONLY if it's in sourceClasses.
     * Returns true if the node was added (or already existed).
     */
    private boolean ensureNodeExists(String fqcn, GraphBuilder graphBuilder) {
        if (!sourceClasses.contains(fqcn)) {
            return false; // external class — skip
        }
        if (!addedNodes.contains(fqcn)) {
            graphBuilder.addNode(Node.builder().id(fqcn).type(NodeType.CLASS).build());
            addedNodes.add(fqcn);
        }
        return true;
    }

    private void processTypeDeclaration(TypeDeclaration<?> typeDecl, String packageName,
                                         GraphBuilder graphBuilder) {
        String fqcn = packageName.isEmpty() ? typeDecl.getName().asString()
            : packageName + "." + typeDecl.getName().asString();

        // Source class always gets a node (we're parsing its file)
        ensureNodeExists(fqcn, graphBuilder);

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
```

Key changes from original:
1. Constructor takes `Set<String> sourceClasses`
2. `ensureNodeExists()` returns `boolean` — `false` if external class
3. `ensureNodeExists()` checks `sourceClasses.contains(fqcn)` before creating node
4. `addEdge()` is only called when `ensureNodeExists()` returns `true`

- [ ] **Step 4: Update existing AstVisitorTest for new constructor**

All existing tests in `archon-java/src/test/java/com/archon/java/AstVisitorTest.java` need updating. The old `new AstVisitor()` calls must become `new AstVisitor(Set.of(...))`. For each existing test, pass a set containing all expected class names. Read the existing test file and update every `new AstVisitor()` to `new AstVisitor(sourceClasses)` where `sourceClasses` includes all FQCNs used in that test.

Example: for a test that parses `com.example.Foo` which imports `com.example.Bar`, pass `Set.of("com.example.Foo", "com.example.Bar")`.

- [ ] **Step 5: Update JavaParserPlugin to collect source FQCNs**

In `archon-java/src/main/java/com/archon/java/JavaParserPlugin.java`, change the `parse` method to do a two-pass approach:

1. First pass: walk all `.java` files, collect FQCNs into a `Set<String>`
2. Second pass: walk again with AstVisitor that has the sourceClasses set

Replace the `parse` method body with:

```java
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

    // Step 2a: First pass — collect all source FQCNs
    Set<String> sourceClasses = new java.util.HashSet<>();
    JavaParser javaParser = new JavaParser();
    for (ModuleDetector.SourceRoot sourceRoot : sourceRoots) {
        collectSourceFqcns(sourceRoot.getPath(), javaParser, sourceClasses, errors);
    }

    // Step 2b: Second pass — build graph with filtering
    AstVisitor astVisitor = new AstVisitor(sourceClasses);
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
```

Add the new `collectSourceFqcns` method to `JavaParserPlugin`:

```java
private void collectSourceFqcns(Path sourceRoot, JavaParser javaParser,
                                 Set<String> sourceClasses, List<ParseError> errors) {
    if (!Files.isDirectory(sourceRoot)) {
        return;
    }
    try {
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    try {
                        var parseResult = javaParser.parse(file);
                        if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                            CompilationUnit cu = parseResult.getResult().get();
                            String pkg = cu.getPackageDeclaration()
                                .map(pd -> pd.getName().asString())
                                .orElse("");
                            for (com.github.javaparser.ast.body.TypeDeclaration<?> type : cu.getTypes()) {
                                String fqcn = pkg.isEmpty() ? type.getName().asString()
                                    : pkg + "." + type.getName().asString();
                                sourceClasses.add(fqcn);
                            }
                        }
                    } catch (IOException e) {
                        errors.add(new ParseError(file.toString(), 0,
                            "Failed to read file: " + e.getMessage()));
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    } catch (IOException e) {
        errors.add(new ParseError(sourceRoot.toString(), 0,
            "Failed to walk source directory: " + e.getMessage()));
    }
}
```

Add the import `import java.util.Set;` to JavaParserPlugin.java.

- [ ] **Step 6: Run all archon-java tests**

Run: `./gradlew :archon-java:test -q 2>&1 | tail -20`
Expected: All tests pass. If any test fails due to the constructor change, fix it.

- [ ] **Step 7: Commit**

```bash
git add archon-java/src/main/java/com/archon/java/AstVisitor.java archon-java/src/main/java/com/archon/java/JavaParserPlugin.java archon-java/src/test/java/com/archon/java/AstVisitorTest.java archon-java/src/test/java/com/archon/java/JavaParserPluginTest.java
git commit -m "feat: filter external classes — only graph source-tree classes

Two-pass parsing: first collect all FQCNs in source tree, then
build graph with AstVisitor that only creates nodes for source classes.
Edges to external classes (JDK, Spring, etc.) are silently skipped."
```

---

### Task 4: Smarter domain detection with pivot heuristic

**Files:**
- Modify: `archon-core/src/main/java/com/archon/core/analysis/DomainDetector.java`
- Modify: `archon-core/src/test/java/com/archon/core/analysis/DomainDetectorTest.java`

- [ ] **Step 1: Write the failing tests**

Add these tests to `archon-core/src/test/java/com/archon/core/analysis/DomainDetectorTest.java`:

```java
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

    // Should detect pivot at depth 3: common, framework, project, domain, api
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-core:test --tests DomainDetectorTest -q 2>&1 | tail -10`
Expected: FAIL — pivot detection doesn't exist yet, old code uses fixed `parts[2]`

- [ ] **Step 3: Implement pivot detection in DomainDetector**

Replace `archon-core/src/main/java/com/archon/core/analysis/DomainDetector.java` with:

```java
package com.archon.core.analysis;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assigns domain labels to nodes based on package conventions and config overrides.
 * Three-tier resolution: config match (HIGH) -> pivot detection (MEDIUM) -> fallback (LOW).
 */
public class DomainDetector {

    public DomainResult assignDomains(DependencyGraph graph, Map<String, List<String>> domainMappings) {
        Map<String, String> domains = new LinkedHashMap<>();
        Map<String, Confidence> confidence = new LinkedHashMap<>();

        // Find pivot depth from the full node set
        int pivotDepth = findPivotDepth(graph.getNodeIds());

        for (String nodeId : graph.getNodeIds()) {
            Resolution res = resolveDomain(nodeId, domainMappings, pivotDepth);
            domains.put(nodeId, res.domain);
            confidence.put(nodeId, res.confidence);
        }

        return new DomainResult(domains, confidence);
    }

    /**
     * Find the package depth where there are 3-10 distinct segments.
     * This identifies the "pivot" — the level representing logical modules.
     * Returns -1 if no pivot found.
     */
    int findPivotDepth(Set<String> nodeIds) {
        int maxDepth = nodeIds.stream()
            .mapToInt(id -> id.split("\\.").length)
            .max().orElse(0);

        for (int depth = 2; depth < maxDepth; depth++) {
            Set<String> segments = new LinkedHashSet<>();
            for (String nodeId : nodeIds) {
                String[] parts = nodeId.split("\\.");
                if (parts.length > depth) {
                    segments.add(parts[depth]);
                }
            }
            if (segments.size() >= 3 && segments.size() <= 10) {
                return depth;
            }
        }
        return -1;
    }

    private Resolution resolveDomain(String nodeId, Map<String, List<String>> domainMappings,
                                      int pivotDepth) {
        // Tier 1: config override - exact package prefix match
        for (Map.Entry<String, List<String>> entry : domainMappings.entrySet()) {
            for (String prefix : entry.getValue()) {
                if (nodeId.startsWith(prefix)) {
                    return new Resolution(entry.getKey(), Confidence.HIGH);
                }
            }
        }

        String[] parts = nodeId.split("\\.");

        // Tier 2: pivot detection — use auto-detected depth
        if (pivotDepth >= 0 && parts.length > pivotDepth) {
            return new Resolution(parts[pivotDepth], Confidence.MEDIUM);
        }

        // Tier 2 fallback: old behavior (3rd segment for 4+ deep packages)
        if (parts.length >= 4) {
            return new Resolution(parts[2], Confidence.MEDIUM);
        }

        // Tier 3: top-level fallback
        if (parts.length >= 2) {
            return new Resolution(parts[0], Confidence.LOW);
        }

        return new Resolution("unknown", Confidence.LOW);
    }

    private record Resolution(String domain, Confidence confidence) {}
}
```

Key change: `findPivotDepth()` scans all package depths looking for one with 3-10 distinct segments. `resolveDomain()` uses this pivot depth instead of hardcoded `parts[2]`. Config overrides still take priority (Tier 1).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-core:test --tests DomainDetectorTest -q 2>&1 | tail -10`
Expected: PASS (all 7 tests, including 2 new ones)

- [ ] **Step 5: Commit**

```bash
git add archon-core/src/main/java/com/archon/core/analysis/DomainDetector.java archon-core/src/test/java/com/archon/core/analysis/DomainDetectorTest.java
git commit -m "feat: pivot-based domain detection replaces fixed 3rd-segment heuristic

Scans package depths to find the level with 3-10 distinct segments,
identifying logical module boundaries. Falls back to old behavior if
no pivot found."
```

---

### Task 5: Wire ThresholdCalculator into CLI commands

**Files:**
- Modify: `archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java`
- Modify: `archon-cli/src/main/java/com/archon/cli/CheckCommand.java`

- [ ] **Step 1: Update AnalyzeCommand to use adaptive thresholds and polished output**

In `archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java`:

1. Add import for `ThresholdCalculator` and `Thresholds`
2. After `DependencyGraph graph = result.getGraph();` and domain detection, add:
   ```java
   Thresholds thresholds = ThresholdCalculator.calculate(
       graph.nodeCount(), (int) domainMap.values().stream().distinct().count());
   ```
3. Replace `couplingAnalyzer.findHotspots(graph, 5)` with `couplingAnalyzer.findHotspots(graph, thresholds.getCouplingThreshold())`
4. In hotspot display, cap at `thresholds.getHotspotDisplayCap()`:
   ```java
   List<Node> displayed = hotspots.stream()
       .limit(thresholds.getHotspotDisplayCap())
       .collect(Collectors.toList());
   long remaining = hotspots.size() - displayed.size();
   // ... print displayed, then if remaining > 0:
   // System.out.println("  ... and " + remaining + " more above threshold " + thresholds.getCouplingThreshold());
   ```
5. Group blind spots by file:
   ```java
   Map<String, List<BlindSpot>> byFile = blindSpots.stream()
       .collect(Collectors.groupingBy(BlindSpot::getFile, LinkedHashMap::new, Collectors.toList()));
   System.out.println("\n\u001B[36mBlind spots (" + byFile.size() + " files, "
       + blindSpots.size() + " occurrences):\u001B[0m");
   for (var entry : byFile.entrySet()) {
       List<BlindSpot> spots = entry.getValue();
       String file = spots.get(0).getFile();
       String fileName = file.substring(file.lastIndexOf(java.io.File.separatorChar) + 1);
       String patterns = spots.stream().map(BlindSpot::getPattern).collect(Collectors.joining(", "));
       System.out.println("  [" + spots.get(0).getType() + "] " + fileName + " — "
           + spots.size() + " occurrence" + (spots.size() > 1 ? "s" : "")
           + " (" + patterns + ")");
   }
   ```
6. Update summary line to show internal edge count and threshold info

- [ ] **Step 2: Update CheckCommand to use adaptive thresholds**

In `archon-cli/src/main/java/com/archon/cli/CheckCommand.java`:

1. Add import for `ThresholdCalculator` and `Thresholds`
2. After domain detection, compute thresholds:
   ```java
   Thresholds thresholds = ThresholdCalculator.calculate(
       graph.nodeCount(), (int) domainMap.values().stream().distinct().count());
   ```
3. Replace `config.getMaxCrossDomain()` usage in the validator call. The simplest approach: pass thresholds to RuleValidator. Add an overloaded `validate` method or pass thresholds as a parameter.

For `RuleValidator`, add a method that accepts `Thresholds`:
```java
public List<RuleViolation> validate(DependencyGraph graph, ArchonConfig config,
                                     Map<String, String> domainMap,
                                     List<List<String>> cycles, Thresholds thresholds) {
```
And use `thresholds.getCrossDomainMax()` in place of `config.getMaxCrossDomain()` for the cross-domain check.

- [ ] **Step 3: Run all tests**

Run: `./gradlew test -q 2>&1 | tail -20`
Expected: All tests pass

- [ ] **Step 4: Build and test against geditor-api**

Run: `./gradlew shadowJar --quiet && java -jar archon-cli/build/libs/archon-0.1.0.jar analyze "/c/Users/T480/Documents/codebase/geditor-api/"`
Expected: Nodes ~1043, edges ~2000-2500, domains ~5, hotspots ~10-15, blind spots grouped by file

- [ ] **Step 5: Commit**

```bash
git add archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java archon-cli/src/main/java/com/archon/cli/CheckCommand.java archon-core/src/main/java/com/archon/core/config/RuleValidator.java
git commit -m "feat: wire adaptive thresholds into CLI, polish output display

- CouplingAnalyzer uses auto-scaled threshold
- Hotspot display capped at top N
- Blind spots grouped by file
- Cross-domain max scaled by domain count"
```

---

### Task 6: Validation and version bump

**Files:**
- Modify: `archon-cli/build.gradle.kts` (version bump)

- [ ] **Step 1: Run full test suite**

Run: `./gradlew test -q 2>&1 | tail -20`
Expected: All tests pass

- [ ] **Step 2: Test on archon itself (small project)**

Run: `java -jar archon-cli/build/libs/archon-0.1.0.jar analyze .`
Expected: Small project gets strict thresholds. No external class noise. Domains detected correctly at proper depth.

- [ ] **Step 3: Test on geditor-api (large project)**

Run: `java -jar archon-cli/build/libs/archon-0.1.0.jar analyze "/c/Users/T480/Documents/codebase/geditor-api/"`
Expected:
- Nodes: ~1043
- Edges: ~2000-2500 (external edges removed)
- Domains: ~5 (common, framework, project, domain, api)
- Hotspots: ~10-15 (top 20 displayed)
- Warnings from `check`: ~10-20

- [ ] **Step 4: Bump version to 0.1.1**

In `archon-cli/build.gradle.kts`, change `archiveVersion.set("0.1.0")` to `archiveVersion.set("0.1.1")`

- [ ] **Step 5: Rebuild and final smoke test**

Run: `./gradlew shadowJar --quiet && java -jar archon-cli/build/libs/archon-0.1.1.jar analyze .`

- [ ] **Step 6: Commit**

```bash
git add archon-cli/build.gradle.kts
git commit -m "chore: bump version to 0.1.1"
```
