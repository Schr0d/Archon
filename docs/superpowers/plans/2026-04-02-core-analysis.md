# Core Analysis Logic — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement all core analysis engines in `archon-core` (configuration, cycle detection, coupling, domain detection, risk scoring, impact propagation, rule validation) with full unit test coverage.

**Architecture:** Seven independent engines that operate on the immutable `DependencyGraph`. `DomainDetector` runs first (provides domain info to others). `CycleDetector` and `CouplingAnalyzer` are independent of each other. `RiskScorer` is a pure function. `ImpactPropagator` performs BFS traversal. `RuleValidator` consumes results from all other engines. All engines follow TDD — tests written first, then implementation.

**Tech Stack:** Java 17, JUnit 5, Jackson YAML, Gradle Kotlin DSL

---

## File Structure

### New Files

| File | Responsibility |
|------|---------------|
| `archon-core/src/main/java/com/archon/core/analysis/DomainResult.java` | Domain assignment result: node→domain map + node→confidence map |
| `archon-core/src/main/java/com/archon/core/analysis/ImpactResult.java` | Impact propagation result: reached nodes, depth, cross-domain count |
| `archon-core/src/main/java/com/archon/core/config/RuleViolation.java` | Rule violation record: rule name, severity, details |
| `archon-core/src/test/java/com/archon/core/config/ArchonConfigTest.java` | Tests for YAML parsing and defaults |
| `archon-core/src/test/java/com/archon/core/analysis/DomainDetectorTest.java` | Tests for domain detection (3 tiers) |
| `archon-core/src/test/java/com/archon/core/analysis/CycleDetectorTest.java` | Tests for Tarjan's SCC |
| `archon-core/src/test/java/com/archon/core/analysis/CouplingAnalyzerTest.java` | Tests for hotspot identification |
| `archon-core/src/test/java/com/archon/core/analysis/RiskScorerTest.java` | Tests for threshold-based scoring |
| `archon-core/src/test/java/com/archon/core/analysis/ImpactPropagatorTest.java` | Tests for BFS propagation |
| `archon-core/src/test/java/com/archon/core/config/RuleValidatorTest.java` | Tests for rule enforcement |

### Modified Files

| File | Change |
|------|--------|
| `archon-core/src/main/java/com/archon/core/config/ArchonConfig.java` | Add `load(Path)` and `loadOrDefault(Path)` static methods |
| `archon-core/src/main/java/com/archon/core/analysis/DomainDetector.java` | Change `void` return to `DomainResult` |
| `archon-core/src/main/java/com/archon/core/analysis/ImpactPropagator.java` | Change return type to `ImpactResult`, add domain map parameter |
| `archon-core/src/main/java/com/archon/core/config/RuleValidator.java` | Change return type to `List<RuleViolation>`, add analysis context params |

---

## Task 1: ArchonConfig YAML Parsing

**Files:**
- Modify: `archon-core/src/main/java/com/archon/core/config/ArchonConfig.java`
- Create: `archon-core/src/test/java/com/archon/core/config/ArchonConfigTest.java`

- [ ] **Step 1: Write the test file**

```java
// archon-core/src/test/java/com/archon/core/config/ArchonConfigTest.java
package com.archon.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ArchonConfigTest {

    @Test
    void defaults_returnsDefaultValues() {
        ArchonConfig config = ArchonConfig.defaults();

        assertTrue(config.isNoCycle());
        assertEquals(2, config.getMaxCrossDomain());
        assertEquals(3, config.getMaxCallDepth());
        assertTrue(config.getForbidCoreEntityLeakage().isEmpty());
        assertTrue(config.getDomains().isEmpty());
        assertTrue(config.getCriticalPaths().isEmpty());
        assertTrue(config.getIgnore().isEmpty());
    }

    @Test
    void loadOrDefault_missingFile_returnsDefaults(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("nonexistent.yml");
        ArchonConfig config = ArchonConfig.loadOrDefault(missing);

        assertTrue(config.isNoCycle());
        assertEquals(2, config.getMaxCrossDomain());
    }

    @Test
    void loadOrDefault_validYaml_mergesWithDefaults(@TempDir Path tempDir) throws Exception {
        String yaml = """
            version: 1
            rules:
              no_cycle: false
              max_cross_domain: 5
            domains:
              auth: ["com.example.auth"]
            critical_paths:
              - auth
            """;
        Path file = tempDir.resolve(".archon.yml");
        Files.writeString(file, yaml);

        ArchonConfig config = ArchonConfig.loadOrDefault(file);

        assertFalse(config.isNoCycle());
        assertEquals(5, config.getMaxCrossDomain());
        assertEquals(3, config.getMaxCallDepth()); // default preserved
        assertEquals(1, config.getDomains().size());
        assertTrue(config.getDomains().containsKey("auth"));
        assertEquals(1, config.getCriticalPaths().size());
        assertEquals("auth", config.getCriticalPaths().get(0));
    }

    @Test
    void load_invalidYaml_throwsException(@TempDir Path tempDir) throws Exception {
        String yaml = "rules: [invalid\n  broken: {";
        Path file = tempDir.resolve(".archon.yml");
        Files.writeString(file, yaml);

        assertThrows(RuntimeException.class, () -> ArchonConfig.load(file));
    }

    @Test
    void loadOrDefault_validYaml_parsesIgnorePatterns(@TempDir Path tempDir) throws Exception {
        String yaml = """
            version: 1
            ignore:
              - "**/generated/**"
              - "**/*.generated.java"
            """;
        Path file = tempDir.resolve(".archon.yml");
        Files.writeString(file, yaml);

        ArchonConfig config = ArchonConfig.loadOrDefault(file);

        assertEquals(2, config.getIgnore().size());
        assertEquals("**/generated/**", config.getIgnore().get(0));
    }

    @Test
    void loadOrDefault_validYaml_parsesForbidCoreEntityLeakage(@TempDir Path tempDir) throws Exception {
        String yaml = """
            version: 1
            rules:
              forbid_core_entity_leakage:
                - com.example.core.EntityA
                - com.example.core.EntityB
            """;
        Path file = tempDir.resolve(".archon.yml");
        Files.writeString(file, yaml);

        ArchonConfig config = ArchonConfig.loadOrDefault(file);

        assertEquals(2, config.getForbidCoreEntityLeakage().size());
        assertEquals("com.example.core.EntityA", config.getForbidCoreEntityLeakage().get(0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.config.ArchonConfigTest"`
Expected: FAIL — `loadOrDefault` and `load` methods do not exist yet

- [ ] **Step 3: Implement ArchonConfig YAML parsing**

Replace the entire `ArchonConfig.java` with:

```java
// archon-core/src/main/java/com/archon/core/config/ArchonConfig.java
package com.archon.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses .archon.yml and merges with built-in defaults.
 */
public class ArchonConfig {
    private boolean noCycle = true;
    private int maxCrossDomain = 2;
    private int maxCallDepth = 3;
    private List<String> forbidCoreEntityLeakage = List.of();
    private Map<String, List<String>> domains = Map.of();
    private List<String> criticalPaths = List.of();
    private List<String> ignore = List.of();

    public static ArchonConfig defaults() {
        return new ArchonConfig();
    }

    /**
     * Load config from file. Throws on missing or invalid file.
     */
    public static ArchonConfig load(Path configPath) {
        try {
            String yaml = Files.readString(configPath);
            return parseYaml(yaml);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config file: " + configPath, e);
        }
    }

    /**
     * Load config from file, returning defaults if file is missing.
     * Throws on invalid YAML.
     */
    public static ArchonConfig loadOrDefault(Path configPath) {
        if (!Files.exists(configPath)) {
            return defaults();
        }
        return load(configPath);
    }

    @SuppressWarnings("unchecked")
    private static ArchonConfig parseYaml(String yaml) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Map<String, Object> root;
        try {
            root = mapper.readValue(yaml, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Invalid YAML in config: " + e.getMessage(), e);
        }

        ArchonConfig config = new ArchonConfig();

        Map<String, Object> rules = (Map<String, Object>) root.get("rules");
        if (rules != null) {
            if (rules.containsKey("no_cycle")) {
                config.noCycle = (Boolean) rules.get("no_cycle");
            }
            if (rules.containsKey("max_cross_domain")) {
                config.maxCrossDomain = ((Number) rules.get("max_cross_domain")).intValue();
            }
            if (rules.containsKey("max_call_depth")) {
                config.maxCallDepth = ((Number) rules.get("max_call_depth")).intValue();
            }
            if (rules.containsKey("forbid_core_entity_leakage")) {
                config.forbidCoreEntityLeakage = new ArrayList<>((List<String>) rules.get("forbid_core_entity_leakage"));
            }
        }

        Map<String, List<String>> domains = (Map<String, List<String>>) root.get("domains");
        if (domains != null) {
            config.domains = new LinkedHashMap<>(domains);
        }

        List<String> criticalPaths = (List<String>) root.get("critical_paths");
        if (criticalPaths != null) {
            config.criticalPaths = new ArrayList<>(criticalPaths);
        }

        List<String> ignore = (List<String>) root.get("ignore");
        if (ignore != null) {
            config.ignore = new ArrayList<>(ignore);
        }

        return config;
    }

    public boolean isNoCycle() { return noCycle; }
    public int getMaxCrossDomain() { return maxCrossDomain; }
    public int getMaxCallDepth() { return maxCallDepth; }
    public List<String> getForbidCoreEntityLeakage() { return forbidCoreEntityLeakage; }
    public Map<String, List<String>> getDomains() { return domains; }
    public List<String> getCriticalPaths() { return criticalPaths; }
    public List<String> getIgnore() { return ignore; }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.config.ArchonConfigTest"`
Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```
git add archon-core/src/main/java/com/archon/core/config/ArchonConfig.java archon-core/src/test/java/com/archon/core/config/ArchonConfigTest.java
git commit -m "feat: add YAML parsing to ArchonConfig with load/loadOrDefault"
```

---

## Task 2: DomainDetector

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/analysis/DomainResult.java`
- Modify: `archon-core/src/main/java/com/archon/core/analysis/DomainDetector.java`
- Create: `archon-core/src/test/java/com/archon/core/analysis/DomainDetectorTest.java`

- [ ] **Step 1: Write the DomainResult data class**

```java
// archon-core/src/main/java/com/archon/core/analysis/DomainResult.java
package com.archon.core.analysis;

import com.archon.core.graph.Confidence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Result of domain detection: maps each node to its detected domain and confidence.
 */
public class DomainResult {
    private final Map<String, String> domains;
    private final Map<String, Confidence> confidence;

    public DomainResult(Map<String, String> domains, Map<String, Confidence> confidence) {
        this.domains = Collections.unmodifiableMap(new LinkedHashMap<>(domains));
        this.confidence = Collections.unmodifiableMap(new LinkedHashMap<>(confidence));
    }

    public Map<String, String> getDomains() {
        return domains;
    }

    public Optional<String> getDomain(String nodeId) {
        return Optional.ofNullable(domains.get(nodeId));
    }

    public Confidence getConfidence(String nodeId) {
        return confidence.getOrDefault(nodeId, Confidence.LOW);
    }

    public int size() {
        return domains.size();
    }
}
```

- [ ] **Step 2: Write the test file**

```java
// archon-core/src/test/java/com/archon/core/analysis/DomainDetectorTest.java
package com.archon.core.analysis;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DomainDetectorTest {

    private DependencyGraph buildGraph(String... fqns) {
        GraphBuilder builder = GraphBuilder.builder();
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
        // No config mapping — falls back to package convention
        DependencyGraph graph = buildGraph("com.fuwa.system.domain.SysUser");

        DomainResult result = new DomainDetector().assignDomains(graph, Map.of());

        // Tier 2: extracts meaningful segment from package (3rd segment after com.fuwa)
        assertTrue(result.getDomain("com.fuwa.system.domain.SysUser").isPresent());
    }

    @Test
    void assignDomains_noMatch_lowConfidence() {
        // Node with no config match and short package
        DependencyGraph graph = buildGraph("x.Service");

        DomainResult result = new DomainDetector().assignDomains(graph, Map.of());

        assertEquals(Confidence.LOW, result.getConfidence("x.Service"));
    }

    @Test
    void assignDomains_emptyGraph_returnsEmptyResult() {
        GraphBuilder builder = GraphBuilder.builder();
        // Add one node and remove it — we just need an empty graph
        // Actually, build with no nodes
        DependencyGraph graph = builder.build();

        DomainResult result = new DomainDetector().assignDomains(graph, Map.of());

        assertEquals(0, result.size());
    }

    @Test
    void assignDomains_configTakesPriorityOverConvention() {
        // Config says auth, convention would say security
        DependencyGraph graph = buildGraph("com.fuwa.security.AuthHandler");
        Map<String, List<String>> mappings = Map.of("auth", List.of("com.fuwa.security"));

        DomainResult result = new DomainDetector().assignDomains(graph, mappings);

        assertEquals("auth", result.getDomain("com.fuwa.security.AuthHandler").orElse(null));
        assertEquals(Confidence.HIGH, result.getConfidence("com.fuwa.security.AuthHandler"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.analysis.DomainDetectorTest"`
Expected: FAIL — `assignDomains` throws UnsupportedOperationException

- [ ] **Step 4: Implement DomainDetector**

Replace the entire `DomainDetector.java` with:

```java
// archon-core/src/main/java/com/archon/core/analysis/DomainDetector.java
package com.archon.core.analysis;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assigns domain labels to nodes based on package conventions and config overrides.
 * Three-tier resolution: config match (HIGH) → package convention (MEDIUM) → fallback (LOW).
 */
public class DomainDetector {

    /**
     * Assigns domains to all nodes in the graph.
     *
     * @param graph          the dependency graph
     * @param domainMappings config domain mappings (domain name → list of package prefixes)
     * @return domain result with node→domain and node→confidence mappings
     */
    public DomainResult assignDomains(DependencyGraph graph, Map<String, List<String>> domainMappings) {
        Map<String, String> domains = new LinkedHashMap<>();
        Map<String, Confidence> confidence = new LinkedHashMap<>();

        for (String nodeId : graph.getNodeIds()) {
            Resolution res = resolveDomain(nodeId, domainMappings);
            domains.put(nodeId, res.domain);
            confidence.put(nodeId, res.confidence);
        }

        return new DomainResult(domains, confidence);
    }

    private Resolution resolveDomain(String nodeId, Map<String, List<String>> domainMappings) {
        // Tier 1: config override — exact package prefix match
        for (Map.Entry<String, List<String>> entry : domainMappings.entrySet()) {
            for (String prefix : entry.getValue()) {
                if (nodeId.startsWith(prefix)) {
                    return new Resolution(entry.getKey(), Confidence.HIGH);
                }
            }
        }

        // Tier 2: package convention — extract meaningful segment
        // Standard Java package: com.company.domain.xxx.ClassName
        // The meaningful domain segment is typically the 3rd position (index 2)
        String[] parts = nodeId.split("\\.");
        if (parts.length >= 4) {
            // Heuristic: segments after com/org/io prefix and company name
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

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.analysis.DomainDetectorTest"`
Expected: All 6 tests PASS

- [ ] **Step 6: Commit**

```
git add archon-core/src/main/java/com/archon/core/analysis/DomainResult.java archon-core/src/main/java/com/archon/core/analysis/DomainDetector.java archon-core/src/test/java/com/archon/core/analysis/DomainDetectorTest.java
git commit -m "feat: implement DomainDetector with 3-tier domain resolution"
```

---

## Task 3: CycleDetector

**Files:**
- Modify: `archon-core/src/main/java/com/archon/core/analysis/CycleDetector.java`
- Create: `archon-core/src/test/java/com/archon/core/analysis/CycleDetectorTest.java`

- [ ] **Step 1: Write the test file**

```java
// archon-core/src/test/java/com/archon/core/analysis/CycleDetectorTest.java
package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CycleDetectorTest {

    private Node node(String fqn) {
        return Node.builder().id(fqn).type(NodeType.CLASS).build();
    }

    private Edge edge(String from, String to) {
        return Edge.builder().source(from).target(to).type(EdgeType.IMPORTS).build();
    }

    @Test
    void detectCycles_noCycle_returnsEmptyList() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .addNode(node("B"))
            .addEdge(edge("A", "B"))
            .build();

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertTrue(cycles.isEmpty());
    }

    @Test
    void detectCycles_simpleCycle_detectsAB() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .addNode(node("B"))
            .addEdge(edge("A", "B"))
            .addEdge(edge("B", "A"))
            .build();

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertEquals(1, cycles.size());
        assertEquals(2, cycles.get(0).size());
    }

    @Test
    void detectCycles_longCycle_detectsABC() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .addNode(node("B"))
            .addNode(node("C"))
            .addEdge(edge("A", "B"))
            .addEdge(edge("B", "C"))
            .addEdge(edge("C", "A"))
            .build();

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertEquals(1, cycles.size());
        assertEquals(3, cycles.get(0).size());
    }

    @Test
    void detectCycles_multipleIndependentCycles_detectsBoth() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .addNode(node("B"))
            .addNode(node("C"))
            .addNode(node("D"))
            // Cycle 1: A ↔ B
            .addEdge(edge("A", "B"))
            .addEdge(edge("B", "A"))
            // Cycle 2: C ↔ D
            .addEdge(edge("C", "D"))
            .addEdge(edge("D", "C"))
            .build();

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertEquals(2, cycles.size());
    }

    @Test
    void detectCycles_emptyGraph_returnsEmptyList() {
        DependencyGraph graph = GraphBuilder.builder().build();

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertTrue(cycles.isEmpty());
    }

    @Test
    void detectCycles_diamondNoCycle_returnsEmptyList() {
        // A → B, A → C, B → D, C → D — no cycle
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .addNode(node("B"))
            .addNode(node("C"))
            .addNode(node("D"))
            .addEdge(edge("A", "B"))
            .addEdge(edge("A", "C"))
            .addEdge(edge("B", "D"))
            .addEdge(edge("C", "D"))
            .build();

        List<List<String>> cycles = new CycleDetector().detectCycles(graph);

        assertTrue(cycles.isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.analysis.CycleDetectorTest"`
Expected: FAIL — `detectCycles` throws UnsupportedOperationException

- [ ] **Step 3: Implement CycleDetector (Tarjan's SCC)**

Replace the entire `CycleDetector.java` with:

```java
// archon-core/src/main/java/com/archon/core/analysis/CycleDetector.java
package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects strongly connected components (cycles) using Tarjan's algorithm.
 * Returns SCCs with size > 1 (single-node SCCs without self-loops are not cycles).
 * O(V+E) time complexity.
 */
public class CycleDetector {

    public List<List<String>> detectCycles(DependencyGraph graph) {
        List<List<String>> cycles = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowlink = new HashMap<>();
        Set<String> onStack = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        int[] counter = {0};

        for (String nodeId : graph.getNodeIds()) {
            if (!index.containsKey(nodeId)) {
                tarjan(nodeId, graph, index, lowlink, onStack, stack, counter, cycles);
            }
        }

        return cycles;
    }

    private void tarjan(String v, DependencyGraph graph,
                        Map<String, Integer> index, Map<String, Integer> lowlink,
                        Set<String> onStack, Deque<String> stack,
                        int[] counter, List<List<String>> cycles) {
        index.put(v, counter[0]);
        lowlink.put(v, counter[0]);
        counter[0]++;
        stack.push(v);
        onStack.add(v);

        for (String w : graph.getDependencies(v)) {
            if (!index.containsKey(w)) {
                tarjan(w, graph, index, lowlink, onStack, stack, counter, cycles);
                lowlink.put(v, Math.min(lowlink.get(v), lowlink.get(w)));
            } else if (onStack.contains(w)) {
                lowlink.put(v, Math.min(lowlink.get(v), index.get(w)));
            }
        }

        if (lowlink.get(v).equals(index.get(v))) {
            List<String> scc = new ArrayList<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (!w.equals(v));

            // Only report non-trivial SCCs (size > 1 means a cycle exists)
            // Self-loops are already rejected by DependencyGraph builder
            if (scc.size() > 1) {
                cycles.add(scc);
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.analysis.CycleDetectorTest"`
Expected: All 6 tests PASS

- [ ] **Step 5: Commit**

```
git add archon-core/src/main/java/com/archon/core/analysis/CycleDetector.java archon-core/src/test/java/com/archon/core/analysis/CycleDetectorTest.java
git commit -m "feat: implement CycleDetector with Tarjan's SCC algorithm"
```

---

## Task 4: CouplingAnalyzer

**Files:**
- Modify: `archon-core/src/main/java/com/archon/core/analysis/CouplingAnalyzer.java`
- Create: `archon-core/src/test/java/com/archon/core/analysis/CouplingAnalyzerTest.java`

- [ ] **Step 1: Write the test file**

```java
// archon-core/src/test/java/com/archon/core/analysis/CouplingAnalyzerTest.java
package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CouplingAnalyzerTest {

    private Node node(String fqn) {
        return Node.builder().id(fqn).type(NodeType.CLASS).build();
    }

    private Edge edge(String from, String to) {
        return Edge.builder().source(from).target(to).type(EdgeType.IMPORTS).build();
    }

    @Test
    void findHotspots_emptyGraph_returnsEmptyList() {
        DependencyGraph graph = GraphBuilder.builder().build();

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 5);

        assertTrue(hotspots.isEmpty());
    }

    @Test
    void findHotspots_singleHotspot_returnsIt() {
        // B, C, D, E, F all depend on A → A has in-degree 5
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .addNode(node("B")).addNode(node("C")).addNode(node("D"))
            .addNode(node("E")).addNode(node("F"))
            .addEdge(edge("B", "A")).addEdge(edge("C", "A"))
            .addEdge(edge("D", "A")).addEdge(edge("E", "A"))
            .addEdge(edge("F", "A"))
            .build();

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 4);

        assertEquals(1, hotspots.size());
        assertEquals("A", hotspots.get(0).getId());
        assertEquals(5, hotspots.get(0).getInDegree());
    }

    @Test
    void findHotspots_sortedByInDegreeDescending() {
        // A has in-degree 3, B has in-degree 5
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B"))
            .addNode(node("C1")).addNode(node("C2")).addNode(node("C3"))
            .addNode(node("D1")).addNode(node("D2")).addNode(node("D3"))
            .addNode(node("D4")).addNode(node("D5"))
            // A has 3 dependents
            .addEdge(edge("C1", "A")).addEdge(edge("C2", "A")).addEdge(edge("C3", "A"))
            // B has 5 dependents
            .addEdge(edge("D1", "B")).addEdge(edge("D2", "B"))
            .addEdge(edge("D3", "B")).addEdge(edge("D4", "B")).addEdge(edge("D5", "B"))
            .build();

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 2);

        assertEquals(2, hotspots.size());
        assertEquals("B", hotspots.get(0).getId());  // in-degree 5 first
        assertEquals("A", hotspots.get(1).getId());  // in-degree 3 second
    }

    @Test
    void findHotspots_belowThreshold_filteredOut() {
        // A has in-degree 2, threshold 5
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B")).addNode(node("C"))
            .addEdge(edge("B", "A")).addEdge(edge("C", "A"))
            .build();

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 5);

        assertTrue(hotspots.isEmpty());
    }

    @Test
    void findHotspots_allNodesAreHotspots() {
        // A has in-degree 6, B has in-degree 6 (they depend on each other + 4 others)
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B"))
            .addNode(node("X1")).addNode(node("X2")).addNode(node("X3")).addNode(node("X4"))
            .addNode(node("Y1")).addNode(node("Y2")).addNode(node("Y3")).addNode(node("Y4"))
            .addEdge(edge("X1", "A")).addEdge(edge("X2", "A")).addEdge(edge("X3", "A"))
            .addEdge(edge("X4", "A")).addEdge(edge("B", "A")).addEdge(edge("Y1", "A"))
            .addEdge(edge("Y1", "B")).addEdge(edge("Y2", "B")).addEdge(edge("Y3", "B"))
            .addEdge(edge("Y4", "B")).addEdge(edge("A", "B")).addEdge(edge("X1", "B"))
            .build();

        List<Node> hotspots = new CouplingAnalyzer().findHotspots(graph, 5);

        assertEquals(2, hotspots.size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.analysis.CouplingAnalyzerTest"`
Expected: FAIL — `findHotspots` throws UnsupportedOperationException

- [ ] **Step 3: Implement CouplingAnalyzer**

Replace the entire `CouplingAnalyzer.java` with:

```java
// archon-core/src/main/java/com/archon/core/analysis/CouplingAnalyzer.java
package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Identifies coupling hotspots by counting in-degree from reverse adjacency.
 * Returns nodes with in-degree above threshold, sorted by in-degree descending.
 */
public class CouplingAnalyzer {

    /**
     * Find coupling hotspots.
     *
     * @param graph     the dependency graph (in-degrees must be computed)
     * @param threshold minimum in-degree to be considered a hotspot (default: 5)
     * @return list of hotspot nodes sorted by in-degree descending
     */
    public List<Node> findHotspots(DependencyGraph graph, int threshold) {
        return graph.getNodeIds().stream()
            .map(graph::getNode)
            .filter(java.util.Optional::isPresent)
            .map(java.util.Optional::get)
            .filter(n -> n.getInDegree() > threshold)
            .sorted(Comparator.comparingInt(Node::getInDegree).reversed())
            .collect(Collectors.toList());
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.analysis.CouplingAnalyzerTest"`
Expected: All 5 tests PASS

- [ ] **Step 5: Commit**

```
git add archon-core/src/main/java/com/archon/core/analysis/CouplingAnalyzer.java archon-core/src/test/java/com/archon/core/analysis/CouplingAnalyzerTest.java
git commit -m "feat: implement CouplingAnalyzer with in-degree hotspot detection"
```

---

## Task 5: RiskScorer

**Files:**
- Modify: `archon-core/src/main/java/com/archon/core/analysis/RiskScorer.java`
- Create: `archon-core/src/test/java/com/archon/core/analysis/RiskScorerTest.java`

- [ ] **Step 1: Write the test file**

```java
// archon-core/src/test/java/com/archon/core/analysis/RiskScorerTest.java
package com.archon.core.analysis;

import com.archon.core.graph.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RiskScorerTest {

    private final RiskScorer scorer = new RiskScorer();

    @Test
    void computeRisk_allLow_returnsLOW() {
        RiskLevel risk = scorer.computeRisk(2, 1, 1, false, false, false);
        assertEquals(RiskLevel.LOW, risk);
    }

    @Test
    void computeRisk_couplingAbove10_returnsHIGH() {
        RiskLevel risk = scorer.computeRisk(11, 1, 1, false, false, false);
        assertEquals(RiskLevel.HIGH, risk);
    }

    @Test
    void computeRisk_couplingBetween5and10_returnsMEDIUM() {
        RiskLevel risk = scorer.computeRisk(7, 1, 1, false, false, false);
        assertEquals(RiskLevel.MEDIUM, risk);
    }

    @Test
    void computeRisk_crossDomainAbove3_returnsHIGH() {
        RiskLevel risk = scorer.computeRisk(2, 3, 1, false, false, false);
        assertEquals(RiskLevel.HIGH, risk);
    }

    @Test
    void computeRisk_callDepthAbove3_returnsHIGH() {
        RiskLevel risk = scorer.computeRisk(2, 1, 3, false, false, false);
        assertEquals(RiskLevel.HIGH, risk);
    }

    @Test
    void computeRisk_criticalPath_returnsHIGH() {
        RiskLevel risk = scorer.computeRisk(2, 1, 1, true, false, false);
        assertEquals(RiskLevel.HIGH, risk);
    }

    @Test
    void computeRisk_inCycle_returnsVERY_HIGH() {
        RiskLevel risk = scorer.computeRisk(2, 1, 1, false, true, false);
        assertEquals(RiskLevel.VERY_HIGH, risk);
    }

    @Test
    void computeRisk_cycleOverridesHighCoupling() {
        // Both cycle and high coupling — cycle wins (max aggregation)
        RiskLevel risk = scorer.computeRisk(15, 1, 1, false, true, false);
        assertEquals(RiskLevel.VERY_HIGH, risk);
    }

    @Test
    void computeRisk_lowConfidence_escalatesOneLevel() {
        // MEDIUM from coupling 7 + LOW confidence → HIGH
        RiskLevel risk = scorer.computeRisk(7, 1, 1, false, false, true);
        assertEquals(RiskLevel.HIGH, risk);
    }

    @Test
    void computeRisk_lowConfidence_escalatesHIGHToVERY_HIGH() {
        // HIGH from coupling 11 + LOW confidence → VERY_HIGH
        RiskLevel risk = scorer.computeRisk(11, 1, 1, false, false, true);
        assertEquals(RiskLevel.VERY_HIGH, risk);
    }

    @Test
    void computeRisk_lowConfidence_escalatesVERY_HIGHToBLOCKED() {
        // VERY_HIGH from cycle + LOW confidence → BLOCKED
        RiskLevel risk = scorer.computeRisk(2, 1, 1, false, true, true);
        assertEquals(RiskLevel.BLOCKED, risk);
    }

    @Test
    void computeRisk_lowConfidence_LOWStaysLOW() {
        // LOW + LOW confidence → LOW (can't go below LOW)
        RiskLevel risk = scorer.computeRisk(2, 1, 1, false, false, true);
        assertEquals(RiskLevel.LOW, risk);
    }

    @Test
    void computeRisk_multipleHIGHConditions_returnsHIGH() {
        // High coupling + critical path — both HIGH, max is HIGH
        RiskLevel risk = scorer.computeRisk(11, 1, 1, true, false, false);
        assertEquals(RiskLevel.HIGH, risk);
    }

    @Test
    void computeRisk_couplingAtExactThreshold5_returnsMEDIUM() {
        RiskLevel risk = scorer.computeRisk(5, 1, 1, false, false, false);
        assertEquals(RiskLevel.MEDIUM, risk);
    }

    @Test
    void computeRisk_couplingAt4_returnsLOW() {
        RiskLevel risk = scorer.computeRisk(4, 1, 1, false, false, false);
        assertEquals(RiskLevel.LOW, risk);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.analysis.RiskScorerTest"`
Expected: FAIL — `computeRisk` throws UnsupportedOperationException

- [ ] **Step 3: Implement RiskScorer**

Replace the entire `RiskScorer.java` with:

```java
// archon-core/src/main/java/com/archon/core/analysis/RiskScorer.java
package com.archon.core.analysis;

import com.archon.core.graph.RiskLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Threshold-based risk scoring with max aggregation.
 * Escalates by one level if lowConfidence is true.
 */
public class RiskScorer {

    /**
     * Compute risk level based on multiple dimensions.
     * Aggregation: max across all applicable conditions.
     * Low confidence escalates the final result by one level.
     */
    public RiskLevel computeRisk(int inDegree, int crossDomainCount, int callDepth,
                                boolean onCriticalPath, boolean inCycle, boolean lowConfidence) {
        List<RiskLevel> levels = new ArrayList<>();

        // Coupling dimension
        if (inDegree > 10) {
            levels.add(RiskLevel.HIGH);
        } else if (inDegree >= 5) {
            levels.add(RiskLevel.MEDIUM);
        } else {
            levels.add(RiskLevel.LOW);
        }

        // Cross-domain dimension
        if (crossDomainCount >= 3) {
            levels.add(RiskLevel.HIGH);
        }

        // Call depth dimension
        if (callDepth >= 3) {
            levels.add(RiskLevel.HIGH);
        }

        // Critical path dimension
        if (onCriticalPath) {
            levels.add(RiskLevel.HIGH);
        }

        // Cycle dimension
        if (inCycle) {
            levels.add(RiskLevel.VERY_HIGH);
        }

        // Max aggregation
        RiskLevel result = levels.stream()
            .max(Enum::compareTo)
            .orElse(RiskLevel.LOW);

        // Confidence escalation: bump up one level if low confidence
        if (lowConfidence) {
            result = escalate(result);
        }

        return result;
    }

    private RiskLevel escalate(RiskLevel level) {
        return switch (level) {
            case LOW -> RiskLevel.LOW;       // Can't go below LOW
            case MEDIUM -> RiskLevel.HIGH;
            case HIGH -> RiskLevel.VERY_HIGH;
            case VERY_HIGH -> RiskLevel.BLOCKED;
            case BLOCKED -> RiskLevel.BLOCKED; // Already at max
        };
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.analysis.RiskScorerTest"`
Expected: All 15 tests PASS

- [ ] **Step 5: Commit**

```
git add archon-core/src/main/java/com/archon/core/analysis/RiskScorer.java archon-core/src/test/java/com/archon/core/analysis/RiskScorerTest.java
git commit -m "feat: implement RiskScorer with threshold-based scoring and confidence escalation"
```

---

## Task 6: ImpactPropagator

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/analysis/ImpactResult.java`
- Modify: `archon-core/src/main/java/com/archon/core/analysis/ImpactPropagator.java`
- Create: `archon-core/src/test/java/com/archon/core/analysis/ImpactPropagatorTest.java`

- [ ] **Step 1: Write the ImpactResult data class**

```java
// archon-core/src/main/java/com/archon/core/analysis/ImpactResult.java
package com.archon.core.analysis;

import com.archon.core.graph.RiskLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Result of impact propagation from a target node.
 * Contains all reached nodes, their depths and risk levels,
 * and aggregate metrics.
 */
public class ImpactResult {

    /**
     * A single node reached during impact propagation.
     */
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
```

- [ ] **Step 2: Write the test file**

```java
// archon-core/src/test/java/com/archon/core/analysis/ImpactPropagatorTest.java
package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ImpactPropagatorTest {

    private Node node(String fqn) {
        return Node.builder().id(fqn).type(NodeType.CLASS).build();
    }

    private Edge edge(String from, String to) {
        return Edge.builder().source(from).target(to).type(EdgeType.IMPORTS).build();
    }

    @Test
    void propagate_singleHop_findsDirectDependents() {
        // B depends on A → A change impacts B
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B"))
            .addEdge(edge("B", "A"))
            .build();

        ImpactResult result = new ImpactPropagator().propagate(
            graph, "A", 3, Map.of("A", "domain1", "B", "domain1"));

        assertEquals(1, result.getTotalAffected());
        assertEquals("B", result.getImpactedNodes().get(0).getNodeId());
        assertEquals(1, result.getImpactedNodes().get(0).getDepth());
    }

    @Test
    void propagate_threeHops_respectsDepthLimit() {
        // D → C → B → A (chain of dependents)
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B"))
            .addNode(node("C")).addNode(node("D"))
            .addEdge(edge("B", "A")).addEdge(edge("C", "B"))
            .addEdge(edge("D", "C"))
            .build();

        // Depth limit 2: should reach B (depth 1) and C (depth 2), but not D (depth 3)
        ImpactResult result = new ImpactPropagator().propagate(
            graph, "A", 2, Map.of("A", "d1", "B", "d1", "C", "d1", "D", "d1"));

        assertEquals(2, result.getTotalAffected());
        assertEquals(2, result.getMaxDepthReached());
    }

    @Test
    void propagate_diamondDependency_countsNodesOnce() {
        // D → B → A, D → C → A (diamond)
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B"))
            .addNode(node("C")).addNode(node("D"))
            .addEdge(edge("B", "A")).addEdge(edge("C", "A"))
            .addEdge(edge("D", "B")).addEdge(edge("D", "C"))
            .build();

        ImpactResult result = new ImpactPropagator().propagate(
            graph, "A", 3, Map.of("A", "d1", "B", "d1", "C", "d1", "D", "d1"));

        // Should find B, C, D — each once
        assertEquals(3, result.getTotalAffected());
    }

    @Test
    void propagate_isolatedNode_returnsEmpty() {
        // A has no dependents
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .build();

        ImpactResult result = new ImpactPropagator().propagate(
            graph, "A", 3, Map.of("A", "d1"));

        assertEquals(0, result.getTotalAffected());
    }

    @Test
    void propagate_targetNotFound_throwsException() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A"))
            .build();

        assertThrows(IllegalArgumentException.class, () ->
            new ImpactPropagator().propagate(graph, "NonExistent", 3, Map.of()));
    }

    @Test
    void propagate_crossDomainEdges_counted() {
        // B (domain1) depends on A (domain2) → cross-domain
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B"))
            .addEdge(edge("B", "A"))
            .build();

        ImpactResult result = new ImpactPropagator().propagate(
            graph, "A", 3, Map.of("A", "domain2", "B", "domain1"));

        assertEquals(1, result.getCrossDomainEdges());
    }

    @Test
    void propagate_sameDomainEdges_noCrossDomainCount() {
        // B and A are same domain
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B"))
            .addEdge(edge("B", "A"))
            .build();

        ImpactResult result = new ImpactPropagator().propagate(
            graph, "A", 3, Map.of("A", "domain1", "B", "domain1"));

        assertEquals(0, result.getCrossDomainEdges());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.analysis.ImpactPropagatorTest"`
Expected: FAIL — `propagate` signature mismatch, throws UnsupportedOperationException

- [ ] **Step 4: Implement ImpactPropagator**

Replace the entire `ImpactPropagator.java` with:

```java
// archon-core/src/main/java/com/archon/core/analysis/ImpactPropagator.java
package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.RiskLevel;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * BFS-based impact propagation from a target node.
 * Traverses reverse adjacency (dependents) to compute blast radius.
 * Depth-limited with cross-domain tracking.
 */
public class ImpactPropagator {

    /**
     * Propagate impact from a target node through its dependents.
     *
     * @param graph     the dependency graph
     * @param target    the starting node id (the changed node)
     * @param maxDepth  maximum traversal depth (default: 3)
     * @param domainMap node id → domain name mapping
     * @return impact result with all reached nodes and metrics
     * @throws IllegalArgumentException if target node is not in the graph
     */
    public ImpactResult propagate(DependencyGraph graph, String target, int maxDepth,
                                  Map<String, String> domainMap) {
        if (!graph.containsNode(target)) {
            throw new IllegalArgumentException(
                "Target node not found: " + target + ". Available: " + graph.getNodeIds());
        }

        List<ImpactResult.ImpactNode> impactedNodes = new java.util.ArrayList<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        Map<String, Integer> depths = new HashMap<>();

        // Initialize with direct dependents of target
        String targetDomain = domainMap.getOrDefault(target, "");
        for (String dependent : graph.getDependents(target)) {
            if (!visited.contains(dependent)) {
                visited.add(dependent);
                queue.add(dependent);
                depths.put(dependent, 1);
            }
        }

        int maxDepthReached = 0;
        int crossDomainEdges = 0;

        // BFS traversal
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depths.get(current);

            if (currentDepth > maxDepth) {
                continue;
            }

            maxDepthReached = Math.max(maxDepthReached, currentDepth);
            String currentDomain = domainMap.getOrDefault(current, "");

            // Check if this is a cross-domain edge (parent → current)
            // The edge goes from current → some parent. If domains differ, it's cross-domain.
            // We count it if current's domain differs from the node it depends on
            Set<String> deps = graph.getDependencies(current);
            for (String dep : deps) {
                String depDomain = domainMap.getOrDefault(dep, "");
                if (!currentDomain.isEmpty() && !depDomain.isEmpty()
                    && !currentDomain.equals(depDomain)) {
                    crossDomainEdges++;
                    break; // count each node's cross-domain status once
                }
            }

            RiskLevel risk = currentDepth >= 3 ? RiskLevel.HIGH
                           : currentDepth >= 2 ? RiskLevel.MEDIUM
                           : RiskLevel.LOW;

            impactedNodes.add(new ImpactResult.ImpactNode(
                current, currentDomain, currentDepth, risk));

            // Enqueue next level
            if (currentDepth < maxDepth) {
                for (String dependent : graph.getDependents(current)) {
                    if (!visited.contains(dependent)) {
                        visited.add(dependent);
                        queue.add(dependent);
                        depths.put(dependent, currentDepth + 1);
                    }
                }
            }
        }

        return new ImpactResult(target, impactedNodes, maxDepthReached, crossDomainEdges);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.analysis.ImpactPropagatorTest"`
Expected: All 7 tests PASS

- [ ] **Step 6: Commit**

```
git add archon-core/src/main/java/com/archon/core/analysis/ImpactResult.java archon-core/src/main/java/com/archon/core/analysis/ImpactPropagator.java archon-core/src/test/java/com/archon/core/analysis/ImpactPropagatorTest.java
git commit -m "feat: implement ImpactPropagator with BFS and depth-limited traversal"
```

---

## Task 7: RuleValidator

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/config/RuleViolation.java`
- Modify: `archon-core/src/main/java/com/archon/core/config/RuleValidator.java`
- Create: `archon-core/src/test/java/com/archon/core/config/RuleValidatorTest.java`

- [ ] **Step 1: Write the RuleViolation data class**

```java
// archon-core/src/main/java/com/archon/core/config/RuleViolation.java
package com.archon.core.config;

import java.util.Objects;

/**
 * Represents a rule violation found during validation.
 */
public class RuleViolation {
    private final String rule;
    private final String severity;   // "ERROR" or "WARNING"
    private final String details;

    public RuleViolation(String rule, String severity, String details) {
        this.rule = Objects.requireNonNull(rule);
        this.severity = Objects.requireNonNull(severity);
        this.details = Objects.requireNonNull(details);
    }

    public String getRule() { return rule; }
    public String getSeverity() { return severity; }
    public String getDetails() { return details; }

    @Override
    public String toString() {
        return severity + ": " + rule + " — " + details;
    }
}
```

- [ ] **Step 2: Write the test file**

```java
// archon-core/src/test/java/com/archon/core/config/RuleValidatorTest.java
package com.archon.core.config;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuleValidatorTest {

    private Node node(String fqn) {
        return Node.builder().id(fqn).type(NodeType.CLASS).build();
    }

    private Edge edge(String from, String to) {
        return Edge.builder().source(from).target(to).type(EdgeType.IMPORTS).build();
    }

    @Test
    void validate_noViolations_returnsEmptyList() {
        // Clean graph, no cycles, low coupling
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("com.a.Service")).addNode(node("com.b.Client"))
            .addEdge(edge("com.b.Client", "com.a.Service"))
            .build();

        ArchonConfig config = ArchonConfig.defaults();
        Map<String, String> domainMap = Map.of("com.a.Service", "domainA", "com.b.Client", "domainB");
        List<List<String>> cycles = List.of();

        List<RuleViolation> violations = new RuleValidator().validate(graph, config, domainMap, cycles);

        assertTrue(violations.isEmpty());
    }

    @Test
    void validate_cycleDetected_reportsViolation() {
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B"))
            .addEdge(edge("A", "B")).addEdge(edge("B", "A"))
            .build();

        ArchonConfig config = ArchonConfig.defaults();
        List<List<String>> cycles = List.of(List.of("A", "B"));

        List<RuleViolation> violations = new RuleValidator().validate(
            graph, config, Map.of("A", "d1", "B", "d1"), cycles);

        assertTrue(violations.stream().anyMatch(v -> v.getRule().equals("no_cycle")));
        assertEquals("ERROR", violations.stream()
            .filter(v -> v.getRule().equals("no_cycle"))
            .findFirst().get().getSeverity());
    }

    @Test
    void validate_maxCrossDomainExceeded_reportsViolation() {
        // A has dependents from 3 different domains
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("com.a.Core"))
            .addNode(node("com.b.One")).addNode(node("com.c.Two")).addNode(node("com.d.Three"))
            .addEdge(edge("com.b.One", "com.a.Core"))
            .addEdge(edge("com.c.Two", "com.a.Core"))
            .addEdge(edge("com.d.Three", "com.a.Core"))
            .build();

        ArchonConfig config = ArchonConfig.defaults(); // maxCrossDomain = 2
        Map<String, String> domainMap = Map.of(
            "com.a.Core", "core",
            "com.b.One", "domain1",
            "com.c.Two", "domain2",
            "com.d.Three", "domain3"
        );

        List<RuleViolation> violations = new RuleValidator().validate(
            graph, config, domainMap, List.of());

        assertTrue(violations.stream().anyMatch(v -> v.getRule().equals("max_cross_domain")));
    }

    @Test
    void validate_forbidCoreEntityLeakage_reportsViolation() throws Exception {
        // SysUser (system domain) has a dependent from auth domain
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("com.sys.SysUser")).addNode(node("com.auth.Handler"))
            .addEdge(edge("com.auth.Handler", "com.sys.SysUser"))
            .build();

        java.nio.file.Path yaml = java.nio.file.Files.createTempFile("archon-test", ".yml");
        java.nio.file.Files.writeString(yaml, """
            version: 1
            rules:
              forbid_core_entity_leakage:
                - com.sys.SysUser
            """);
        ArchonConfig config = ArchonConfig.loadOrDefault(yaml);
        java.nio.file.Files.deleteIfExists(yaml);

        Map<String, String> domainMap = Map.of(
            "com.sys.SysUser", "system",
            "com.auth.Handler", "auth"
        );

        List<RuleViolation> violations = new RuleValidator().validate(
            graph, config, domainMap, List.of());

        assertTrue(violations.stream().anyMatch(v -> v.getRule().equals("forbid_core_entity_leakage")));
        assertEquals("ERROR", violations.stream()
            .filter(v -> v.getRule().equals("forbid_core_entity_leakage"))
            .findFirst().get().getSeverity());
    }

    @Test
    void validate_maxCallDepthExceeded_reportsViolation() {
        // Chain: D → C → B → A (depth 3 from A)
        DependencyGraph graph = GraphBuilder.builder()
            .addNode(node("A")).addNode(node("B"))
            .addNode(node("C")).addNode(node("D"))
            .addEdge(edge("B", "A")).addEdge(edge("C", "B"))
            .addEdge(edge("D", "C"))
            .build();

        ArchonConfig config = ArchonConfig.defaults(); // maxCallDepth = 3

        List<RuleViolation> violations = new RuleValidator().validate(
            graph, config, Map.of("A", "d1", "B", "d1", "C", "d1", "D", "d1"), List.of());

        assertTrue(violations.stream().anyMatch(v -> v.getRule().equals("max_call_depth")));
    }

    @Test
    void validate_emptyGraph_noViolations() {
        DependencyGraph graph = GraphBuilder.builder().build();
        ArchonConfig config = ArchonConfig.defaults();

        List<RuleViolation> violations = new RuleValidator().validate(
            graph, config, Map.of(), List.of());

        assertTrue(violations.isEmpty());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.config.RuleValidatorTest"`
Expected: FAIL — `validate` signature mismatch, throws UnsupportedOperationException

- [ ] **Step 4: Implement RuleValidator**

Replace the entire `RuleValidator.java` with:

```java
// archon-core/src/main/java/com/archon/core/config/RuleValidator.java
package com.archon.core.config;

import com.archon.core.graph.DependencyGraph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates architecture rules against a dependency graph.
 * Consumes pre-computed analysis results (cycles, domain map).
 */
public class RuleValidator {

    /**
     * Validate architecture rules against the graph.
     *
     * @param graph     the dependency graph
     * @param config    architecture configuration with rule definitions
     * @param domainMap node id → domain name mapping
     * @param cycles    pre-computed cycle paths
     * @return list of rule violations
     */
    public List<RuleViolation> validate(DependencyGraph graph, ArchonConfig config,
                                         Map<String, String> domainMap,
                                         List<List<String>> cycles) {
        List<RuleViolation> violations = new ArrayList<>();

        if (config.isNoCycle()) {
            checkNoCycle(cycles, violations);
        }

        checkMaxCrossDomain(graph, config, domainMap, violations);
        checkMaxCallDepth(graph, config, violations);
        checkForbidCoreEntityLeakage(graph, config, domainMap, violations);

        return violations;
    }

    private void checkNoCycle(List<List<String>> cycles, List<RuleViolation> violations) {
        for (List<String> cycle : cycles) {
            violations.add(new RuleViolation(
                "no_cycle",
                "ERROR",
                "Cycle detected: " + String.join(" -> ", cycle) + " -> " + cycle.get(0)
            ));
        }
    }

    private void checkMaxCrossDomain(DependencyGraph graph, ArchonConfig config,
                                      Map<String, String> domainMap,
                                      List<RuleViolation> violations) {
        for (String nodeId : graph.getNodeIds()) {
            Set<String> crossDomains = new HashSet<>();
            for (String dependent : graph.getDependents(nodeId)) {
                String depDomain = domainMap.get(dependent);
                String nodeDomain = domainMap.get(nodeId);
                if (depDomain != null && nodeDomain != null && !depDomain.equals(nodeDomain)) {
                    crossDomains.add(depDomain);
                }
            }
            if (crossDomains.size() > config.getMaxCrossDomain()) {
                violations.add(new RuleViolation(
                    "max_cross_domain",
                    "WARNING",
                    nodeId + " has dependents from " + crossDomains.size()
                        + " domains (max: " + config.getMaxCrossDomain() + "): "
                        + String.join(", ", crossDomains)
                ));
            }
        }
    }

    private void checkMaxCallDepth(DependencyGraph graph, ArchonConfig config,
                                    List<RuleViolation> violations) {
        int maxDepth = config.getMaxCallDepth();
        for (String nodeId : graph.getNodeIds()) {
            int depth = computeMaxDepth(graph, nodeId, new HashSet<>());
            if (depth > maxDepth) {
                violations.add(new RuleViolation(
                    "max_call_depth",
                    "WARNING",
                    nodeId + " has a dependency chain of depth " + depth
                        + " (max: " + maxDepth + ")"
                ));
            }
        }
    }

    private int computeMaxDepth(DependencyGraph graph, String nodeId, Set<String> visited) {
        if (visited.contains(nodeId)) {
            return 0; // cycle protection
        }
        visited.add(nodeId);
        int maxChildDepth = 0;
        for (String dependent : graph.getDependents(nodeId)) {
            maxChildDepth = Math.max(maxChildDepth, computeMaxDepth(graph, dependent, visited));
        }
        visited.remove(nodeId);
        return 1 + maxChildDepth;
    }

    private void checkForbidCoreEntityLeakage(DependencyGraph graph, ArchonConfig config,
                                               Map<String, String> domainMap,
                                               List<RuleViolation> violations) {
        for (String entity : config.getForbidCoreEntityLeakage()) {
            if (!graph.containsNode(entity)) {
                continue;
            }
            String entityDomain = domainMap.get(entity);
            if (entityDomain == null) {
                continue;
            }
            Set<String> leakDomains = new HashSet<>();
            for (String dependent : graph.getDependents(entity)) {
                String depDomain = domainMap.get(dependent);
                if (depDomain != null && !depDomain.equals(entityDomain)) {
                    leakDomains.add(depDomain);
                }
            }
            if (!leakDomains.isEmpty()) {
                violations.add(new RuleViolation(
                    "forbid_core_entity_leakage",
                    "ERROR",
                    entity + " leaks to domains: " + String.join(", ", leakDomains)
                ));
            }
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat :archon-core:test --tests "com.archon.core.config.RuleValidatorTest"`
Expected: All 6 tests PASS

- [ ] **Step 6: Run all tests**

Run: `./gradlew.bat :archon-core:test`
Expected: All tests across all test classes PASS (total: ~45 tests)

- [ ] **Step 7: Commit**

```
git add archon-core/src/main/java/com/archon/core/config/RuleViolation.java archon-core/src/main/java/com/archon/core/config/RuleValidator.java archon-core/src/test/java/com/archon/core/config/RuleValidatorTest.java
git commit -m "feat: implement RuleValidator with no_cycle, max_cross_domain, forbid_core_entity_leakage"
```

---

## Final Verification

- [ ] **Step 1: Run full build**

Run: `./gradlew.bat build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Verify no UnsupportedOperationExceptions remain in core**

Run: `grep -r "UnsupportedOperationException" archon-core/src/main/`
Expected: No matches — all stubs replaced with implementations

- [ ] **Step 3: Final commit with plan**

```
git add docs/superpowers/plans/2026-04-02-core-analysis.md
git commit -m "docs: add core analysis implementation plan"
```
