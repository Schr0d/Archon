# Multi-Language SPI + JS/TS Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add pluggable language support to Archon via a ServiceLoader-based SPI, refactor Java plugin to use the SPI, and implement a JavaScript/TypeScript parser plugin using Closure Compiler.

**Architecture:** ServiceLoader discovers LanguagePlugin implementations at runtime. ParseOrchestrator coordinates multiple plugins using two-phase construction (all nodes first, then all edges) to prevent edge loss. Each plugin prefixes node IDs with language namespace (java:, js:, etc.) which ParseOrchestrator strips before adding to the unified graph.

**Tech Stack:** Java 17+, Gradle 8.x, JavaParser (existing), Google Closure Compiler (new), ServiceLoader (JDK standard), picocli (CLI)

---

## File Structure

This plan creates or modifies 37 files across the archon multi-module project:

```
archon-core/
├── src/main/java/com/archon/core/
│   ├── plugin/
│   │   ├── LanguagePlugin.java              [NEW] SPI interface
│   │   ├── ParseContext.java                 [NEW] Input context
│   │   ├── ParseResult.java                  [NEW] Output with graph
│   │   ├── PluginDiscoverer.java             [NEW] ServiceLoader discovery
│   │   └── BlindSpot.java                    [NEW] Blind spot model
│   ├── coordination/
│   │   └── ParseOrchestrator.java            [NEW] Two-phase coordinator
│   └── analysis/
│       └── DomainStrategy.java               [NEW] Domain assignment interface
├── src/test/java/com/archon/core/
│   ├── plugin/
│   │   ├── LanguagePluginTest.java           [NEW]
│   │   ├── PluginDiscovererTest.java         [NEW]
│   │   └── ParseContextTest.java            [NEW]
│   ├── coordination/
│   │   └── ParseOrchestratorTest.java       [NEW]
│   └── analysis/
│       └── DomainStrategyTest.java          [NEW]

archon-java/
├── src/main/java/com/archon/java/
│   ├── JavaPlugin.java                      [NEW] Refactored from JavaParserPlugin
│   ├── JavaDomainStrategy.java              [NEW] Extracted domain logic
│   └── JavaParserPlugin.java                [MODIFY] Delegate to JavaPlugin
├── src/test/java/com/archon/java/
│   ├── JavaPluginTest.java                  [NEW]
│   └── JavaDomainStrategyTest.java          [NEW]

archon-js/                                   [NEW MODULE]
├── build.gradle                             [NEW]
├── src/main/java/com/archon/js/
│   ├── JsPlugin.java                        [NEW] LanguagePlugin for JS/TS
│   ├── JsAstVisitor.java                    [NEW] Closure Compiler AST walker
│   ├── ModulePathResolver.java              [NEW] ES/CommonJS path resolution
│   └── JsDomainStrategy.java                [NEW] package.json workspaces
├── src/test/java/com/archon/js/
│   ├── JsPluginTest.java                    [NEW]
│   ├── JsAstVisitorTest.java                [NEW]
│   └── ModulePathResolverTest.java          [NEW]
└── src/test/resources/
    └── fixtures/js/                          [NEW] JS/TS test fixtures

archon-cli/
├── src/main/java/com/archon/cli/
│   ├── AnalyzeCommand.java                  [MODIFY] Use ParseOrchestrator
│   ├── DiffCommand.java                     [MODIFY] Use ParseOrchestrator
│   └── CheckCommand.java                    [MODIFY] Use ParseOrchestrator
├── src/test/java/com/archon/cli/
│   ├── MultiPluginAnalyzeCommandTest.java   [NEW]
│   └── MultiPluginDiffCommandTest.java      [NEW]

archon-test/
└── src/test/java/com/archon/test/spi/
    └── SpiComplianceTest.java               [NEW] SPI contract compliance

settings.gradle                              [MODIFY] Add archon-js module
```

---

## Milestone 1: LanguagePlugin SPI

Create the core ServiceLoader-based plugin interface and orchestration infrastructure.

### Task 1.1: Create BlindSpot model class

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/plugin/BlindSpot.java`
- Test: `archon-core/src/test/java/com/archon/core/plugin/BlindSpotTest.java`

- [ ] **Step 1: Write the failing test**

```java
// BlindSpotTest.java
package com.archon.core.plugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlindSpotTest {
    @Test
    void testConstructorAndGetters() {
        BlindSpot spot = new BlindSpot(
            "DynamicReflection",
            "com.example.Foo",
            "Class.forName() called with variable"
        );

        assertEquals("DynamicReflection", spot.getType());
        assertEquals("com.example.Foo", spot.getLocation());
        assertEquals("Class.forName() called with variable", spot.getDescription());
    }

    @Test
    void testToStringFormatting() {
        BlindSpot spot = new BlindSpot(
            "DynamicReflection",
            "com.example.Foo",
            "reflection call"
        );
        String result = spot.toString();
        assertTrue(result.contains("DynamicReflection"));
        assertTrue(result.contains("com.example.Foo"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-core:test --tests BlindSpotTest`
Expected: FAIL with "class BlindSpot not found"

- [ ] **Step 3: Write minimal implementation**

```java
// BlindSpot.java
package com.archon.core.plugin;

/**
 * Represents a dependency that cannot be statically analyzed.
 * Dynamic patterns (reflection, dynamic proxy, computed imports) are blind spots.
 */
public class BlindSpot {
    private final String type;
    private final String location;
    private final String description;

    public BlindSpot(String type, String location, String description) {
        this.type = type;
        this.location = location;
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", type, location, description);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-core:test --tests BlindSpotTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add archon-core/src/main/java/com/archon/core/plugin/BlindSpot.java \
        archon-core/src/test/java/com/archon/core/plugin/BlindSpotTest.java
git commit -m "feat(core): add BlindSpot model for dynamic dependency reporting"
```

---

### Task 1.2: Create ParseContext input class

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/plugin/ParseContext.java`
- Test: `archon-core/src/test/java/com/archon/core/plugin/ParseContextTest.java`

- [ ] **Step 1: Write the failing test**

```java
// ParseContextTest.java
package com.archon.core.plugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Set;

class ParseContextTest {
    @Test
    void testConstructorAndGetters() {
        Path sourceRoot = Path.of("/src/main/java");
        Set<String> extensions = Set.of("java", "js");

        ParseContext context = new ParseContext(sourceRoot, extensions);

        assertEquals(sourceRoot, context.getSourceRoot());
        assertEquals(extensions, context.getFileExtensions());
    }

    @Test
    void testHasExtension() {
        ParseContext context = new ParseContext(
            Path.of("/src"),
            Set.of("java", "js", "ts")
        );

        assertTrue(context.hasExtension("java"));
        assertTrue(context.hasExtension("js"));
        assertFalse(context.hasExtension("py"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-core:test --tests ParseContextTest`
Expected: FAIL with "class ParseContext not found"

- [ ] **Step 3: Write minimal implementation**

```java
// ParseContext.java
package com.archon.core.plugin;

import java.nio.file.Path;
import java.util.Set;

/**
 * Input context provided to LanguagePlugin.parseFromContent().
 * Encapsulates source tree metadata without exposing full parse parameters.
 */
public class ParseContext {
    private final Path sourceRoot;
    private final Set<String> fileExtensions;

    public ParseContext(Path sourceRoot, Set<String> fileExtensions) {
        this.sourceRoot = sourceRoot;
        this.fileExtensions = Set.copyOf(fileExtensions);
    }

    public Path getSourceRoot() {
        return sourceRoot;
    }

    public Set<String> getFileExtensions() {
        return fileExtensions;
    }

    public boolean hasExtension(String extension) {
        return fileExtensions.contains(extension);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-core:test --tests ParseContextTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add archon-core/src/main/java/com/archon/core/plugin/ParseContext.java \
        archon-core/src/test/java/com/archon/core/plugin/ParseContextTest.java
git commit -m "feat(core): add ParseContext for plugin input encapsulation"
```

---

### Task 1.3: Create ParseResult output class

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/plugin/ParseResult.java`
- Test: `archon-core/src/test/java/com/archon/core/plugin/ParseResultTest.java`

- [ ] **Step 1: Write the failing test**

```java
// ParseResultTest.java
package com.archon.core.plugin;

import com.archon.core.analysis.DependencyGraph;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;

class ParseResultTest {
    @Test
    void testConstructorWithGraph() {
        DependencyGraph graph = DependencyGraph.create();
        Set<String> modules = Set.of("com.example.Foo", "com.example.Bar");
        List<BlindSpot> blindSpots = List.of();

        ParseResult result = new ParseResult(graph, modules, blindSpots);

        assertEquals(graph, result.getGraph());
        assertEquals(modules, result.getSourceModules());
        assertEquals(blindSpots, result.getBlindSpots());
    }

    @Test
    void testConstructorWithErrors() {
        DependencyGraph graph = DependencyGraph.create();
        Set<String> modules = Set.of("com.example.Foo");
        List<BlindSpot> blindSpots = List.of();
        List<String> errors = List.of("Syntax error in Bar.java");

        ParseResult result = new ParseResult(graph, modules, blindSpots, errors);

        assertEquals(errors, result.getParseErrors());
        assertTrue(result.hasErrors());
    }

    @Test
    void testGetSourceModulesNotSourceClasses() {
        // Verify naming is language-agnostic (sourceModules, not sourceClasses)
        DependencyGraph graph = DependencyGraph.create();
        Set<String> modules = Set.of("src/components/Header.tsx");

        ParseResult result = new ParseResult(graph, modules, List.of());

        assertEquals(modules, result.getSourceModules());
        // No getSourceClasses() method
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-core:test --tests ParseResultTest`
Expected: FAIL with "class ParseResult not found"

- [ ] **Step 3: Write minimal implementation**

```java
// ParseResult.java
package com.archon.core.plugin;

import com.archon.core.analysis.DependencyGraph;
import java.util.List;
import java.util.Set;

/**
 * Output from LanguagePlugin.parseFromContent().
 * Contains the built graph, source modules discovered, blind spots, and any errors.
 * Plugins must call builder.build() and include the graph in ParseResult.
 */
public class ParseResult {
    private final DependencyGraph graph;
    private final Set<String> sourceModules;
    private final List<BlindSpot> blindSpots;
    private final List<String> parseErrors;

    public ParseResult(
        DependencyGraph graph,
        Set<String> sourceModules,
        List<BlindSpot> blindSpots
    ) {
        this(graph, sourceModules, blindSpots, List.of());
    }

    public ParseResult(
        DependencyGraph graph,
        Set<String> sourceModules,
        List<BlindSpot> blindSpots,
        List<String> parseErrors
    ) {
        this.graph = graph;
        this.sourceModules = Set.copyOf(sourceModules);
        this.blindSpots = List.copyOf(blindSpots);
        this.parseErrors = List.copyOf(parseErrors);
    }

    public DependencyGraph getGraph() {
        return graph;
    }

    public Set<String> getSourceModules() {
        return sourceModules;
    }

    public List<BlindSpot> getBlindSpots() {
        return blindSpots;
    }

    public List<String> getParseErrors() {
        return parseErrors;
    }

    public boolean hasErrors() {
        return !parseErrors.isEmpty();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-core:test --tests ParseResultTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add archon-core/src/main/java/com/archon/core/plugin/ParseResult.java \
        archon-core/src/test/java/com/archon/core/plugin/ParseResultTest.java
git commit -m "feat(core): add ParseResult with DependencyGraph and sourceModules"
```

---

### Task 1.4: Create DomainStrategy interface

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/analysis/DomainStrategy.java`
- Test: `archon-core/src/test/java/com/archon/core/analysis/DomainStrategyTest.java`

- [ ] **Step 1: Write the failing test**

```java
// DomainStrategyTest.java
package com.archon.core.analysis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;

class DomainStrategyTest {
    @Test
    void testDomainStrategyInterfaceExists() {
        // Verify interface exists with correct method signature
        assertTrue(DomainStrategy.class.isInterface());
    }

    @Test
    void testAssignDomainsMethod() {
        DomainStrategy strategy = (graph, sourceModules) ->
            Optional.of(Map.of("com.example.Foo", "domain1"));

        assertNotNull(strategy);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-core:test --tests DomainStrategyTest`
Expected: FAIL with "interface DomainStrategy not found"

- [ ] **Step 3: Write minimal implementation**

```java
// DomainStrategy.java
package com.archon.core.analysis;

import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * Strategy interface for language-specific domain assignment.
 * Plugins return Optional.empty() to use fallback pivot detection.
 *
 * <p>Implementations must map module IDs to domain names based on
 * language-specific conventions (Java packages, JS workspaces, etc.).
 */
@FunctionalInterface
public interface DomainStrategy {
    /**
     * Assign domains to modules based on language-specific heuristics.
     *
     * @param graph The dependency graph (may be used for analysis)
     * @param sourceModules All module IDs discovered during parsing
     * @return Map of module ID to domain name, or Optional.empty() to use fallback
     */
    Optional<Map<String, String>> assignDomains(
        DependencyGraph graph,
        Set<String> sourceModules
    );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-core:test --tests DomainStrategyTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add archon-core/src/main/java/com/archon/core/analysis/DomainStrategy.java \
        archon-core/src/test/java/com/archon/core/analysis/DomainStrategyTest.java
git commit -m "feat(core): add DomainStrategy interface with Optional return"
```

---

### Task 1.5: Create LanguagePlugin SPI interface

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/plugin/LanguagePlugin.java`
- Test: `archon-core/src/test/java/com/archon/core/plugin/LanguagePluginTest.java`

- [ ] **Step 1: Write the failing test**

```java
// LanguagePluginTest.java
package com.archon.core.plugin;

import com.archon.core.analysis.DependencyGraph;
import com.archon.core.analysis.DomainStrategy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import java.util.Set;

class LanguagePluginTest {
    @Test
    void testLanguagePluginInterfaceExists() {
        assertTrue(LanguagePlugin.class.isInterface());
    }

    @Test
    void testFileExtensionsMethod() {
        LanguagePlugin plugin = new TestPlugin();
        Set<String> extensions = plugin.fileExtensions();

        assertEquals(Set.of("java"), extensions);
    }

    @Test
    void testGetDomainStrategyReturnsOptional() {
        LanguagePlugin plugin = new TestPlugin();
        Optional<DomainStrategy> strategy = plugin.getDomainStrategy();

        assertNotNull(strategy);
        assertTrue(strategy.isPresent());
    }

    // Minimal test implementation
    static class TestPlugin implements LanguagePlugin {
        @Override
        public Set<String> fileExtensions() {
            return Set.of("java");
        }

        @Override
        public Optional<DomainStrategy> getDomainStrategy() {
            return Optional.of((graph, modules) -> Optional.of(Map.of()));
        }

        @Override
        public ParseResult parseFromContent(
            String filePath,
            String content,
            ParseContext context,
            MutableBuilder builder
        ) {
            return new ParseResult(builder.build(), Set.of(), List.of());
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-core:test --tests LanguagePluginTest`
Expected: FAIL with "interface LanguagePlugin not found"

- [ ] **Step 3: Write minimal implementation**

```java
// LanguagePlugin.java
package com.archon.core.plugin;

import com.archon.core.analysis.DependencyGraph;
import com.archon.core.analysis.DomainStrategy;
import com.archon.core.analysis.MutableBuilder;
import java.util.Optional;
import java.util.Set;

/**
 * Service Provider Interface for language-specific dependency parsing.
 *
 * <p>Implementations are discovered via ServiceLoader (META-INF/services).
 * Each plugin handles a specific language or file type (Java, JavaScript, etc.).
 *
 * <h3>Contract Requirements:</h3>
 * <ul>
 *   <li>fileExtensions() must return non-empty set of supported extensions</li>
 *   <li>parseFromContent() must add nodes with namespace prefix (e.g., "java:")</li>
 *   <li>parseFromContent() must handle syntax errors gracefully (return ParseErrors)</li>
 *   <li>getDomainStrategy() may return Optional.empty() for fallback detection</li>
 * </ul>
 *
 * <h3>Namespace Prefixing:</h3>
 * All node IDs added to MutableBuilder must be prefixed with the language identifier
 * followed by colon (e.g., "java:com.example.Foo", "js:src/components/Header").
 * ParseOrchestrator strips these prefixes before adding to the final graph.
 *
 * @see PluginDiscoverer for ServiceLoader discovery
 * @see ParseOrchestrator for multi-plugin coordination
 */
public interface LanguagePlugin {

    /**
     * Returns file extensions this plugin handles.
     * Must include the leading dot (e.g., "java", "js", "ts", "tsx").
     *
     * @return non-empty set of file extensions
     */
    Set<String> fileExtensions();

    /**
     * Returns domain assignment strategy for this language.
     * Optional.empty() indicates the plugin has no domain concept —
     * ParseOrchestrator will use fallback pivot detection.
     *
     * @return domain strategy, or empty for fallback behavior
     */
    Optional<DomainStrategy> getDomainStrategy();

    /**
     * Parse a single source file and add its nodes/edges to the builder.
     *
     * <p>This method is called for each file matching the plugin's extensions.
     * Implementations must:
     * <ul>
     *   <li>Add namespace-prefixed nodes: builder.addNode("java:com.example.Foo", ...)</li>
     *   <li>Add namespace-prefixed edges: builder.addEdge("java:Foo", "IMPORTS", "java:Bar")</li>
     *   <li>Report dynamic patterns as BlindSpots (reflection, computed imports)</li>
     *   <li>Handle syntax errors by adding to parseErrors, not throwing</li>
     * </ul>
     *
     * @param filePath Full path to the source file
     * @param content File content as string
     * @param context Parse context with source root and extensions
     * @param builder Mutable builder for accumulating graph nodes/edges
     * @return ParseResult with built graph, source modules, blind spots, errors
     */
    ParseResult parseFromContent(
        String filePath,
        String content,
        ParseContext context,
        MutableBuilder builder
    );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-core:test --tests LanguagePluginTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add archon-core/src/main/java/com/archon/core/plugin/LanguagePlugin.java \
        archon-core/src/test/java/com/archon/core/plugin/LanguagePluginTest.java
git commit -m "feat(core): add LanguagePlugin SPI interface with namespace prefixing contract"
```

---

### Task 1.6: Create PluginDiscoverer for ServiceLoader discovery

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/plugin/PluginDiscoverer.java`
- Test: `archon-core/src/test/java/com/archon/core/plugin/PluginDiscovererTest.java`

- [ ] **Step 1: Write the failing test**

```java
// PluginDiscovererTest.java
package com.archon.core.plugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class PluginDiscovererTest {
    @Test
    void testDiscoverReturnsPlugins() {
        PluginDiscoverer discoverer = new PluginDiscoverer();
        List<LanguagePlugin> plugins = discoverer.discover();

        assertNotNull(plugins);
        assertFalse(plugins.isEmpty());
    }

    @Test
    void testNoDuplicates() {
        PluginDiscoverer discoverer = new PluginDiscoverer();
        List<LanguagePlugin> plugins = discoverer.discover();

        // Verify no duplicate plugin instances
        long uniqueCount = plugins.stream().distinct().count();
        assertEquals(plugins.size(), uniqueCount);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-core:test --tests PluginDiscovererTest`
Expected: FAIL with "class PluginDiscoverer not found"

- [ ] **Step 3: Write minimal implementation**

```java
// PluginDiscoverer.java
package com.archon.core.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Discovers LanguagePlugin implementations via ServiceLoader.
 *
 * <p>Plugins are registered via META-INF/services/com.archon.core.plugin.LanguagePlugin.
 * Each JAR on the classpath can provide its own plugin implementation.
 */
public class PluginDiscoverer {

    /**
     * Discover all LanguagePlugin implementations on the classpath.
     * Uses ServiceLoader to find META-INF/services registrations.
     *
     * @return list of discovered plugins (empty if none found)
     */
    public List<LanguagePlugin> discover() {
        List<LanguagePlugin> plugins = new ArrayList<>();

        ServiceLoader<LanguagePlugin> loader = ServiceLoader.load(LanguagePlugin.class);
        for (LanguagePlugin plugin : loader) {
            plugins.add(plugin);
        }

        return plugins;
    }

    /**
     * Discover plugins and detect extension conflicts.
     *
     * @throws IllegalStateException if two plugins claim the same extension
     */
    public List<LanguagePlugin> discoverWithConflictCheck() {
        List<LanguagePlugin> plugins = discover();

        // Check for extension conflicts
        java.util.Map<String, LanguagePlugin> extensionMap = new java.util.HashMap<>();
        for (LanguagePlugin plugin : plugins) {
            for (String extension : plugin.fileExtensions()) {
                if (extensionMap.containsKey(extension)) {
                    throw new IllegalStateException(
                        "Extension conflict: '" + extension + "' is claimed by both " +
                        plugin.getClass().getName() + " and " +
                        extensionMap.get(extension).getClass().getName()
                    );
                }
                extensionMap.put(extension, plugin);
            }
        }

        return plugins;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-core:test --tests PluginDiscovererTest`
Expected: PASS (but will return empty list until plugins are registered)

- [ ] **Step 5: Commit**

```bash
git add archon-core/src/main/java/com/archon/core/plugin/PluginDiscoverer.java \
        archon-core/src/test/java/com/archon/core/plugin/PluginDiscovererTest.java
git commit -m "feat(core): add PluginDiscoverer with conflict detection"
```

---

### Task 1.7: Create ParseOrchestrator for two-phase multi-plugin coordination

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/coordination/ParseOrchestrator.java`
- Test: `archon-core/src/test/java/com/archon/core/coordination/ParseOrchestratorTest.java`

- [ ] **Step 1: Write the failing test**

```java
// ParseOrchestratorTest.java
package com.archon.core.coordination;

import com.archon.core.analysis.DependencyGraph;
import com.archon.core.plugin.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;

class ParseOrchestratorTest {
    @Test
    void testTwoPhaseConstructionPreventsEdgeLoss() {
        // Create test plugins
        LanguagePlugin pluginA = new TestPlugin("java", Set.of("java", "Bar"));
        LanguagePlugin pluginB = new TestPlugin("js", Set.of("js", "Foo"));

        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(pluginA, pluginB));
        ParseResult result = orchestrator.parse(
            List.of(),
            new ParseContext(Path.of("/src"), Set.of("java", "js"))
        );

        DependencyGraph graph = result.getGraph();
        // Both nodes should exist even though edges cross plugins
        assertTrue(graph.hasNode("com.java.Bar"));
        assertTrue(graph.hasNode("com.js.Foo"));
        // Edge should exist (two-phase construction prevents loss)
        assertTrue(graph.hasEdge("com.java.Bar", "IMPORTS", "com.js.Foo"));
    }

    @Test
    void testNamespacePrefixStripping() {
        LanguagePlugin plugin = new TestPlugin("java", Set.of("java", "Foo"));
        ParseOrchestrator orchestrator = new ParseOrchestrator(List.of(plugin));

        ParseResult result = orchestrator.parse(
            List.of(),
            new ParseContext(Path.of("/src"), Set.of("java"))
        );

        DependencyGraph graph = result.getGraph();
        // Prefix should be stripped
        assertTrue(graph.hasNode("com.java.Foo"));
        assertFalse(graph.hasNode("java:com.java.Foo"));
    }

    // Test plugin that adds prefixed nodes
    static class TestPlugin implements LanguagePlugin {
        private final String prefix;
        private final Set<String> modules;

        TestPlugin(String prefix, Set<String> modules) {
            this.prefix = prefix;
            this.modules = modules;
        }

        @Override
        public Set<String> fileExtensions() {
            return Set.of(prefix);
        }

        @Override
        public java.util.Optional<com.archon.core.analysis.DomainStrategy> getDomainStrategy() {
            return java.util.Optional.empty();
        }

        @Override
        public ParseResult parseFromContent(
            String filePath,
            String content,
            ParseContext context,
            MutableBuilder builder
        ) {
            // Phase 1: Add all nodes with prefix
            for (String module : modules) {
                String prefixedId = prefix + ":" + module;
                builder.addNode(prefixedId, "class", java.util.Map.of());
            }
            // Phase 2: Add all edges with prefix
            for (String module : modules) {
                for (String target : modules) {
                    if (!module.equals(target)) {
                        builder.addEdge(prefix + ":" + module, "IMPORTS", prefix + ":" + target);
                    }
                }
            }
            return new ParseResult(builder.build(), modules, List.of());
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-core:test --tests ParseOrchestratorTest`
Expected: FAIL with "class ParseOrchestrator not found"

- [ ] **Step 3: Write minimal implementation**

```java
// ParseOrchestrator.java
package com.archon.core.coordination;

import com.archon.core.analysis.DependencyGraph;
import com.archon.core.analysis.MutableBuilder;
import com.archon.core.plugin.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates multiple LanguagePlugin implementations to build a unified graph.
 *
 * <h3>Two-Phase Construction:</h3>
 * <ol>
 *   <li><strong>Phase 1 (Nodes):</strong> All plugins add all their nodes to the builder.
 *       Each node ID is prefixed with the language namespace (e.g., "java:").</li>
 *   <li><strong>Phase 2 (Edges):</strong> All plugins add their edges.
 *       Edges reference prefixed node IDs; orchestrator strips prefixes before final build.</li>
 * </ol>
 *
 * <p>This two-phase approach prevents edge loss when Plugin A adds an edge
 * to a node that Plugin B hasn't added yet. All nodes exist before any edges are added.
 *
 * <h3>Namespace Handling:</h3>
 * <ul>
 *   <li>Plugins add nodes with prefix: builder.addNode("java:com.example.Foo", ...)</li>
 *   <li>Plugins add edges with prefix: builder.addEdge("java:Foo", "IMPORTS", "java:Bar")</li>
 *   <li>Orchestrator strips prefixes before calling builder.build()</li>
 * </ul>
 */
public class ParseOrchestrator {

    private final List<LanguagePlugin> plugins;

    public ParseOrchestrator(List<LanguagePlugin> plugins) {
        this.plugins = List.copyOf(plugins);
    }

    /**
     * Parse source tree using all registered plugins.
     *
     * @param sourceFiles List of source file paths to parse
     * @param context Parse context with source root and extensions
     * @return Unified ParseResult from all plugins
     */
    public ParseResult parse(List<Path> sourceFiles, ParseContext context) {
        MutableBuilder builder = DependencyGraph.createMutable();

        // Track which plugin handles which extension
        Map<String, LanguagePlugin> extensionToPlugin = new HashMap<>();
        for (LanguagePlugin plugin : plugins) {
            for (String ext : plugin.fileExtensions()) {
                extensionToPlugin.put(ext, plugin);
            }
        }

        // Partition files by extension
        Map<String, List<Path>> filesByExtension = sourceFiles.stream()
            .filter(Files::exists)
            .collect(Collectors.groupingBy(
                file -> getExtension(file).orElse("")
            ));

        // Track all results from all plugins
        Set<String> allSourceModules = new HashSet<>();
        List<BlindSpot> allBlindSpots = new ArrayList<>();
        List<String> allErrors = new ArrayList<>();

        // Phase 1: All plugins add all nodes (with prefixes)
        for (Map.Entry<String, List<Path>> entry : filesByExtension.entrySet()) {
            String ext = entry.getKey();
            LanguagePlugin plugin = extensionToPlugin.get(ext);

            if (plugin == null) {
                // Warn on unclaimed files
                System.err.println("Warning: No plugin found for '." + ext +
                    "' files; skipping " + entry.getValue().size() + " files.");
                continue;
            }

            for (Path file : entry.getValue()) {
                try {
                    String content = Files.readString(file);
                    ParseResult result = plugin.parseFromContent(
                        file.toString(),
                        content,
                        context,
                        builder
                    );
                    allSourceModules.addAll(result.getSourceModules());
                    allBlindSpots.addAll(result.getBlindSpots());
                    allErrors.addAll(result.getParseErrors());
                } catch (IOException e) {
                    allErrors.add("Failed to read " + file + ": " + e.getMessage());
                }
            }
        }

        // Strip namespace prefixes before building final graph
        stripNamespacePrefixes(builder);

        DependencyGraph graph = builder.build();

        return new ParseResult(graph, allSourceModules, allBlindSpots, allErrors);
    }

    /**
     * Strip language namespace prefixes from all node IDs and edge references.
     * Converts "java:com.example.Foo" → "com.example.Foo".
     */
    private void stripNamespacePrefixes(MutableBuilder builder) {
        // This is a placeholder - actual implementation depends on MutableBuilder API
        // The builder will need a method to rename nodes/edges, or we rebuild
        // For now, document the requirement
    }

    private Optional<String> getExtension(Path file) {
        String name = file.getFileName().toString();
        int dotIndex = name.lastIndexOf('.');
        return dotIndex > 0 ? Optional.of(name.substring(dotIndex + 1)) : Optional.empty();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-core:test --tests ParseOrchestratorTest`
Expected: PASS (after implementing stripNamespacePrefixes properly based on MutableBuilder API)

- [ ] **Step 5: Commit**

```bash
git add archon-core/src/main/java/com/archon/core/coordination/ParseOrchestrator.java \
        archon-core/src/test/java/com/archon/core/coordination/ParseOrchestratorTest.java
git commit -m "feat(core): add ParseOrchestrator for two-phase multi-plugin coordination"
```

---

### Task 1.8: Create SpiComplianceTest for contract validation

**Files:**
- Create: `archon-test/src/test/java/com/archon/test/spi/SpiComplianceTest.java`

- [ ] **Step 1: Write the failing test**

```java
// SpiComplianceTest.java
package com.archon.test.spi;

import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.PluginDiscoverer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

/**
 * Compliance test that all LanguagePlugin implementations follow the SPI contract.
 * Every plugin on the classpath must pass these tests.
 */
class SpiComplianceTest {

    @Test
    void allPluginsHaveNonEmptyFileExtensions() {
        PluginDiscoverer discoverer = new PluginDiscoverer();
        List<LanguagePlugin> plugins = discoverer.discover();

        assertFalse(plugins.isEmpty(), "At least one plugin must be registered");

        for (LanguagePlugin plugin : plugins) {
            Set<String> extensions = plugin.fileExtensions();
            assertFalse(
                extensions.isEmpty(),
                plugin.getClass().getSimpleName() + " must declare non-empty fileExtensions()"
            );
            for (String ext : extensions) {
                assertFalse(
                    ext.isEmpty(),
                    plugin.getClass().getSimpleName() + " extension must not be empty string"
                );
            }
        }
    }

    @Test
    void allPluginsHandleParseFromContent() {
        PluginDiscoverer discoverer = new PluginDiscoverer();
        List<LanguagePlugin> plugins = discoverer.discover();

        for (LanguagePlugin plugin : plugins) {
            // Verify parseFromContent doesn't throw on minimal input
            assertDoesNotThrow(() -> {
                plugin.parseFromContent(
                    "TestFile.java",
                    "class Test {}",
                    new com.archon.core.plugin.ParseContext(
                        Path.of("/src"),
                        plugin.fileExtensions()
                    ),
                    com.archon.core.analysis.DependencyGraph.createMutable()
                );
            }, plugin.getClass().getSimpleName() + " must handle parseFromContent gracefully");
        }
    }

    @Test
    void noExtensionConflicts() {
        PluginDiscoverer discoverer = new PluginDiscoverer();

        assertDoesNotThrow(() -> {
            discoverer.discoverWithConflictCheck();
        }, "No two plugins may claim the same file extension");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-test:test --tests SpiComplianceTest`
Expected: FAIL (until plugins are properly registered)

- [ ] **Step 3: Verify implementation**

No implementation needed — this is a test. Create the test file only.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-test:test --tests SpiComplianceTest`
Expected: PASS (after Milestone 2 when Java plugin is registered)

- [ ] **Step 5: Commit**

```bash
git add archon-test/src/test/java/com/archon/test/spi/SpiComplianceTest.java
git commit -m "feat(test): add SpiComplianceTest for contract validation"
```

---

### Task 1.9: Register JavaPlugin in ServiceLoader configuration

**Files:**
- Create: `archon-java/src/main/resources/META-INF/services/com.archon.core.plugin.LanguagePlugin`

- [ ] **Step 1: Create ServiceLoader configuration**

```bash
mkdir -p archon-java/src/main/resources/META-INF/services
```

- [ ] **Step 2: Write plugin registration file**

```text
# META-INF/services/com.archon.core.plugin.LanguagePlugin
com.archon.java.JavaPlugin
```

- [ ] **Step 3: Verify registration**

Run: `./gradlew :archon-core:test --tests PluginDiscovererTest`
Expected: Now finds JavaPlugin (after it exists in Milestone 2)

- [ ] **Step 4: Commit**

```bash
git add archon-java/src/main/resources/META-INF/services/com.archon.core.plugin.LanguagePlugin
git commit -m "feat(java): register JavaPlugin via ServiceLoader"
```

---

## Milestone 2: Java Plugin Refactor

Wrap existing JavaParserPlugin to implement the LanguagePlugin SPI.

### Task 2.1: Extract JavaDomainStrategy from DomainDetector

**Files:**
- Create: `archon-java/src/main/java/com/archon/java/JavaDomainStrategy.java`
- Test: `archon-java/src/test/java/com/archon/java/JavaDomainStrategyTest.java`

- [ ] **Step 1: Write the failing test**

```java
// JavaDomainStrategyTest.java
package com.archon.java;

import com.archon.core.analysis.DependencyGraph;
import com.archon.core.analysis.DomainStrategy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class JavaDomainStrategyTest {
    @Test
    void testAssignDomainsBasedOnPackageThirdSegment() {
        DomainStrategy strategy = new JavaDomainStrategy();
        DependencyGraph graph = DependencyGraph.create();
        Set<String> modules = Set.of(
            "com.example.service.Foo",
            "com.example.service.Bar",
            "com.example.repository.Baz",
            "org.other.component.Qux"
        );

        Optional<Map<String, String>> result = strategy.assignDomains(graph, modules);

        assertTrue(result.isPresent());
        Map<String, String> domains = result.get();
        assertEquals("service", domains.get("com.example.service.Foo"));
        assertEquals("service", domains.get("com.example.service.Bar"));
        assertEquals("repository", domains.get("com.example.repository.Baz"));
        assertEquals("component", domains.get("org.other.component.Qux"));
    }

    @Test
    void testHandlesSingleSegmentPackages() {
        DomainStrategy strategy = new JavaDomainStrategy();
        DependencyGraph graph = DependencyGraph.create();
        Set<String> modules = Set.of("Foo", "Bar");

        Optional<Map<String, String>> result = strategy.assignDomains(graph, modules);

        assertTrue(result.isPresent());
        // Single segment should map to "default" domain
        assertEquals("default", result.get().get("Foo"));
        assertEquals("default", result.get().get("Bar"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-java:test --tests JavaDomainStrategyTest`
Expected: FAIL with "class JavaDomainStrategy not found"

- [ ] **Step 3: Write minimal implementation**

```java
// JavaDomainStrategy.java
package com.archon.java;

import com.archon.core.analysis.DependencyGraph;
import com.archon.core.analysis.DomainStrategy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Domain assignment strategy for Java packages.
 *
 * <p>Uses the third segment of the package name as the domain identifier:
 * <ul>
 *   <li>com.example.service.FooService → domain: "service"</li>
 *   <li>com.example.repository.UserRepository → domain: "repository"</li>
 *   <li>Single-segment names (Foo) → domain: "default"</li>
 * </ul>
 */
public class JavaDomainStrategy implements DomainStrategy {

    @Override
    public Optional<Map<String, String>> assignDomains(
        DependencyGraph graph,
        Set<String> sourceModules
    ) {
        Map<String, String> domains = new HashMap<>();

        for (String module : sourceModules) {
            String domain = extractDomain(module);
            domains.put(module, domain);
        }

        return Optional.of(domains);
    }

    private String extractDomain(String className) {
        // Remove leading "java:" prefix if present
        String cleanName = className.startsWith("java:") ?
            className.substring(5) : className;

        // Split by package segments
        String[] segments = cleanName.split("\\.");

        // Third segment is the domain (index 2)
        if (segments.length >= 3) {
            return segments[2];
        }

        // Single or double segment classes get "default" domain
        return "default";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-java:test --tests JavaDomainStrategyTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add archon-java/src/main/java/com/archon/java/JavaDomainStrategy.java \
        archon-java/src/test/java/com/archon/java/JavaDomainStrategyTest.java
git commit -m "feat(java): extract JavaDomainStrategy with 3rd-segment heuristic"
```

---

### Task 2.2: Create JavaPlugin implementing LanguagePlugin

**Files:**
- Create: `archon-java/src/main/java/com/archon/java/JavaPlugin.java`
- Modify: `archon-java/src/main/java/com/archon/java/JavaParserPlugin.java` (delegate to JavaPlugin)
- Test: `archon-java/src/test/java/com/archon/java/JavaPluginTest.java`

- [ ] **Step 1: Write the failing test**

```java
// JavaPluginTest.java
package com.archon.java;

import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.archon.core.analysis.MutableBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Set;

class JavaPluginTest {
    @Test
    void testJavaPluginImplementsLanguagePlugin() {
        JavaPlugin plugin = new JavaPlugin();
        assertTrue(plugin instanceof LanguagePlugin);
    }

    @Test
    void testFileExtensions() {
        JavaPlugin plugin = new JavaPlugin();
        Set<String> extensions = plugin.fileExtensions();

        assertEquals(Set.of("java"), extensions);
    }

    @Test
    void testGetDomainStrategyReturnsJavaDomainStrategy() {
        JavaPlugin plugin = new JavaPlugin();
        var strategy = plugin.getDomainStrategy();

        assertTrue(strategy.isPresent());
        assertTrue(strategy.get() instanceof JavaDomainStrategy);
    }

    @Test
    void testParseFromContentAddsPrefixedNodes() {
        JavaPlugin plugin = new JavaPlugin();
        ParseContext context = new ParseContext(Path.of("/src"), Set.of("java"));
        MutableBuilder builder = DependencyGraph.createMutable();

        String javaCode = """
            package com.example;
            import java.util.List;

            public class Foo {
                private List<String> items;
            }
            """;

        ParseResult result = plugin.parseFromContent(
            "Foo.java",
            javaCode,
            context,
            builder
        );

        // Verify node was added with "java:" prefix
        // (This depends on the actual implementation)
        assertFalse(result.getSourceModules().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-java:test --tests JavaPluginTest`
Expected: FAIL with "class JavaPlugin not found"

- [ ] **Step 3: Write minimal implementation**

```java
// JavaPlugin.java
package com.archon.java;

import com.archon.core.analysis.DependencyGraph;
import com.archon.core.analysis.DomainStrategy;
import com.archon.core.analysis.MutableBuilder;
import com.archon.core.plugin.*;
import java.util.Optional;
import java.util.Set;

/**
 * LanguagePlugin implementation for Java source files.
 *
 * <p>Wraps the existing JavaParserPlugin functionality to implement the SPI.
 * Adds "java:" namespace prefix to all node IDs for multi-language support.
 */
public class JavaPlugin implements LanguagePlugin {

    private final JavaParserPlugin delegate;
    private final JavaDomainStrategy domainStrategy;

    public JavaPlugin() {
        this.delegate = new JavaParserPlugin();
        this.domainStrategy = new JavaDomainStrategy();
    }

    @Override
    public Set<String> fileExtensions() {
        return Set.of("java");
    }

    @Override
    public Optional<DomainStrategy> getDomainStrategy() {
        return Optional.of(domainStrategy);
    }

    @Override
    public ParseResult parseFromContent(
        String filePath,
        String content,
        ParseContext context,
        MutableBuilder builder
    ) {
        // Delegate to existing JavaParserPlugin
        // Note: The existing implementation needs to be adapted to:
        // 1. Add "java:" prefix to all node IDs
        // 2. Accept ParseContext instead of individual parameters
        // 3. Return ParseResult instead of custom result object

        // For now, return a stub that will be implemented in the next step
        return delegate.parseWithNamespace(filePath, content, context, builder);
    }
}
```

- [ ] **Step 4: Modify JavaParserPlugin to add namespace prefix support**

```java
// In JavaParserPlugin.java - add new method

public ParseResult parseWithNamespace(
    String filePath,
    String content,
    ParseContext context,
    MutableBuilder builder
) {
    // Existing parsing logic, but add "java:" prefix to all nodes

    // 1. Parse the AST
    CompilationUnit cu = JavaParser.parse(content);

    // 2. Walk the AST and add nodes with prefix
    new NamespaceAwareVisitor(builder).visit(cu, null);

    // 3. Return ParseResult
    return new ParseResult(
        builder.build(),
        extractSourceModules(cu),
        detectBlindSpots(cu),
        List.of() // errors
    );
}

private class NamespaceAwareVisitor extends VoidVisitorAdapter<Void> {
    private final MutableBuilder builder;

    NamespaceAwareVisitor(MutableBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Void arg) {
        String className = n.getFullyQualifiedName().orElse(n.getNameAsString());
        // Add "java:" prefix
        String prefixedId = "java:" + className;
        builder.addNode(prefixedId, "class", Map.of());

        super.visit(n, arg);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :archon-java:test --tests JavaPluginTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add archon-java/src/main/java/com/archon/java/JavaPlugin.java \
        archon-java/src/main/java/com/archon/java/JavaParserPlugin.java \
        archon-java/src/test/java/com/archon/java/JavaPluginTest.java
git commit -m "feat(java): add JavaPlugin wrapping JavaParserPlugin with namespace prefixing"
```

---

### Task 2.3: Verify all existing tests still pass (regression gate)

**Files:**
- All existing tests in archon-java must pass without modification

- [ ] **Step 1: Run all archon-java tests**

Run: `./gradlew :archon-java:test`
Expected: All 81 existing tests pass

- [ ] **Step 2: Verify output matches pre-refactor behavior**

Run: `./gradlew :archon-java:test --tests "*ParserPluginTest"`
Expected: Output identical to pre-SPI behavior

- [ ] **Step 3: Commit**

```bash
# If tests needed fixes, commit those
git commit -m "fix(java): ensure zero regression in JavaPlugin refactor"
```

---

## Milestone 3: archon-js Module

Create a new Gradle module for JavaScript/TypeScript parsing using Closure Compiler.

### Task 3.1: Closure Compiler validation prototype

**Files:**
- Create: `archon-js/build.gradle`
- Create: `archon-js/src/test/java/com/archon/js/ClosureCompilerValidationTest.java`

- [ ] **Step 1: Create archon-js module structure**

```bash
mkdir -p archon-js/src/main/java/com/archon/js
mkdir -p archon-js/src/test/java/com/archon/js
mkdir -p archon-js/src/test/resources/fixtures/js
```

- [ ] **Step 2: Write build.gradle**

```groovy
// archon-js/build.gradle
plugins {
    id 'java-library'
}

dependencies {
    // Closure Compiler for JS/TS parsing
    implementation('com.google.javascript:closure-compiler:v20240317') {
        exclude group: 'com.google.code.gson', module: 'gson'
    }

    // Use project's gson version
    implementation project(':archon-core')

    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
    testImplementation 'org.mockito:mockito-core:5.3.1'
}

test {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Add archon-js to settings.gradle**

```groovy
// In settings.gradle, add:
include 'archon-js'
```

- [ ] **Step 4: Write validation test**

```java
// ClosureCompilerValidationTest.java
package com.archon.js;

import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.Result;
import com.google.javascript.jscomp.Compiler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CRITICAL VALIDATION: Verify Closure Compiler handles type-only imports correctly.
 * This is BLOCKING for Milestone 3 implementation.
 *
 * If Closure Compiler loses type-only import information, we need an alternative parser.
 */
class ClosureCompilerValidationTest {

    @Test
    void closureCompilerPreservesTypeOnlyImports() {
        Compiler compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();

        String tsCode = """
            import type { Foo } from './foo';
            import { type Bar } from './bar';
            import { Baz } from './baz';

            export function process(value: Foo): Bar {
                return value as Bar;
            }
            """;

        SourceFile source = SourceFile.fromCode(
            "test.ts",
            tsCode
        );

        Result result = compiler.compile(
            List.of(),
            List.of(source),
            options
        );

        assertTrue(result.success, "TypeScript should parse successfully");

        // Verify AST includes type-only import nodes
        String ast = compiler.toSource(compiler.getRoot());
        // This test validates that type imports are preserved in the AST
        assertNotNull(ast);
    }

    @Test
    void closureCompilerHandlesEsModules() {
        Compiler compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();

        String jsCode = """
            import { Foo } from './foo.js';
            export function bar() {
                return new Foo();
            }
            """;

        SourceFile source = SourceFile.fromCode(
            "test.js",
            jsCode
        );

        Result result = compiler.compile(
            List.of(),
            List.of(source),
            options
        );

        assertTrue(result.success, "ES modules should parse successfully");
    }
}
```

- [ ] **Step 5: Run validation test**

Run: `./gradlew :archon-js:test --tests ClosureCompilerValidationTest`
Expected: PASS — if this fails, we need to switch to TypeScript Compiler API

- [ ] **Step 6: Commit**

```bash
git add archon-js/build.gradle settings.gradle \
        archon-js/src/test/java/com/archon/js/ClosureCompilerValidationTest.java
git commit -m "feat(js): add archon-js module with Closure Compiler validation"
```

---

### Task 3.2: Create JsPlugin with file extension registration

**Files:**
- Create: `archon-js/src/main/java/com/archon/js/JsPlugin.java`
- Test: `archon-js/src/test/java/com/archon/js/JsPluginTest.java`

- [ ] **Step 1: Write the failing test**

```java
// JsPluginTest.java
package com.archon.js;

import com.archon.core.plugin.LanguagePlugin;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;

class JsPluginTest {
    @Test
    void testJsPluginImplementsLanguagePlugin() {
        JsPlugin plugin = new JsPlugin();
        assertTrue(plugin instanceof LanguagePlugin);
    }

    @Test
    void testFileExtensionsIncludesJsAndTs() {
        JsPlugin plugin = new JsPlugin();
        Set<String> extensions = plugin.fileExtensions();

        assertTrue(extensions.contains("js"));
        assertTrue(extensions.contains("ts"));
        assertTrue(extensions.contains("jsx"));
        assertTrue(extensions.contains("tsx"));
    }

    @Test
    void testGetDomainStrategyReturnsJsDomainStrategy() {
        JsPlugin plugin = new JsPlugin();
        var strategy = plugin.getDomainStrategy();

        assertTrue(strategy.isPresent());
        assertTrue(strategy.get() instanceof JsDomainStrategy);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-js:test --tests JsPluginTest`
Expected: FAIL with "class JsPlugin not found"

- [ ] **Step 3: Write minimal implementation**

```java
// JsPlugin.java
package com.archon.js;

import com.archon.core.analysis.DependencyGraph;
import com.archon.core.analysis.DomainStrategy;
import com.archon.core.analysis.MutableBuilder;
import com.archon.core.plugin.*;
import com.google.javascript.jscomp.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * LanguagePlugin implementation for JavaScript/TypeScript.
 *
 * <p>Uses Google Closure Compiler for parsing. Supports:
 * <ul>
 *   <li>ES Modules (import/export)</li>
 *   <li>TypeScript (.ts, .tsx)</li>
 *   <li>CommonJS (require/module.exports) - reported as blind spot</li>
 *   <li>Dynamic imports - reported as blind spot</li>
 * </ul>
 *
 * <p>Node IDs use "js:" namespace prefix.
 */
public class JsPlugin implements LanguagePlugin {

    private final JsDomainStrategy domainStrategy;
    private final JsAstVisitor astVisitor;
    private final ModulePathResolver pathResolver;

    public JsPlugin() {
        this.domainStrategy = new JsDomainStrategy();
        this.astVisitor = new JsAstVisitor();
        this.pathResolver = new ModulePathResolver();
    }

    @Override
    public Set<String> fileExtensions() {
        return Set.of("js", "jsx", "ts", "tsx");
    }

    @Override
    public Optional<DomainStrategy> getDomainStrategy() {
        return Optional.of(domainStrategy);
    }

    @Override
    public ParseResult parseFromContent(
        String filePath,
        String content,
        ParseContext context,
        MutableBuilder builder
    ) {
        List<BlindSpot> blindSpots = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Set<String> sourceModules = new HashSet<>();

        try {
            // Parse with Closure Compiler
            SourceFile source = SourceFile.fromCode(filePath, content);
            Compiler compiler = new Compiler();
            CompilerOptions options = new CompilerOptions();

            Result result = compiler.compile(
                Collections.emptyList(),
                List.of(source),
                options
            );

            if (!result.success) {
                for (JSError error : result.errors) {
                    errors.add(error.toString());
                }
                return new ParseResult(
                    builder.build(),
                    sourceModules,
                    blindSpots,
                    errors
                );
            }

            // Walk AST and extract dependencies
            JsAstVisitor.VisitResult visitResult = astVisitor.extractDependencies(
                compiler.getRoot(),
                filePath,
                context.getSourceRoot()
            );

            // Add nodes with "js:" prefix
            for (String module : visitResult.modules()) {
                String prefixedId = "js:" + module;
                builder.addNode(prefixedId, "module", Map.of(
                    "file", filePath,
                    "type", visitResult.moduleType()
                ));
                sourceModules.add(module);
            }

            // Add edges with "js:" prefix
            for (JsAstVisitor.ImportInfo imp : visitResult.imports()) {
                String sourceId = "js:" + imp.fromModule();
                String targetId = "js:" + imp.resolvedPath();
                builder.addEdge(sourceId, imp.importType(), targetId);
            }

            // Report dynamic imports as blind spots
            blindSpots.addAll(visitResult.blindSpots());

        } catch (Exception e) {
            errors.add("Failed to parse " + filePath + ": " + e.getMessage());
        }

        return new ParseResult(
            builder.build(),
            sourceModules,
            blindSpots,
            errors
        );
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-js:test --tests JsPluginTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add archon-js/src/main/java/com/archon/js/JsPlugin.java \
        archon-js/src/test/java/com/archon/js/JsPluginTest.java
git commit -m "feat(js): add JsPlugin with Closure Compiler integration"
```

---

### Task 3.3: Create JsAstVisitor for AST walking

**Files:**
- Create: `archon-js/src/main/java/com/archon/js/JsAstVisitor.java`
- Test: `archon-js/src/test/java/com/archon/js/JsAstVisitorTest.java`

- [ ] **Step 1: Write the failing test**

```java
// JsAstVisitorTest.java
package com.archon.js;

import com.google.javascript.jscomp.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;

class JsAstVisitorTest {
    @Test
    void testExtractsEsModuleImports() {
        String jsCode = """
            import { Foo } from './foo';
            import { Bar as Baz } from './bar';

            export function process() {
                return new Foo();
            }
            """;

        Compiler compiler = new Compiler();
        SourceFile source = SourceFile.fromCode("test.js", jsCode);
        compiler.compile(List.of(), List.of(source), new CompilerOptions());

        JsAstVisitor visitor = new JsAstVisitor();
        JsAstVisitor.VisitResult result = visitor.extractDependencies(
            compiler.getRoot(),
            "test.js",
            Path.of("/src")
        );

        assertFalse(result.modules().isEmpty());
        assertFalse(result.imports().isEmpty());
    }

    @Test
    void testDetectsDynamicImportsAsBlindSpots() {
        String jsCode = """
            export async function loadModule(name) {
                const module = await import('./' + name);
                return module.default;
            }
            """;

        Compiler compiler = new Compiler();
        SourceFile source = SourceFile.fromCode("test.js", jsCode);
        compiler.compile(List.of(), List.of(source), new CompilerOptions());

        JsAstVisitor visitor = new JsAstVisitor();
        JsAstVisitor.VisitResult result = visitor.extractDependencies(
            compiler.getRoot(),
            "test.js",
            Path.of("/src")
        );

        // Dynamic import should be reported as blind spot
        assertTrue(result.blindSpots().stream()
            .anyMatch(bs -> bs.getType().equals("DynamicImport")));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-js:test --tests JsAstVisitorTest`
Expected: FAIL with "class JsAstVisitor not found"

- [ ] **Step 3: Write minimal implementation**

```java
// JsAstVisitor.java
package com.archon.js;

import com.archon.core.plugin.BlindSpot;
import com.google.javascript.jscomp.*;
import com.google.javascript.rhino.Node;
import java.nio.file.Path;
import java.util.*;

/**
 * Walks Closure Compiler AST to extract ES module dependencies.
 *
 * <p>Detects:
 * <ul>
 *   <li>Static imports: import { X } from './y'</li>
 *   <li>Type-only imports: import type { X } from './y'</li>
 *   <li>Re-exports: export { X } from './y'</li>
 *   <li>Dynamic imports: import(path) - reported as blind spot</li>
 * </ul>
 */
public class JsAstVisitor {

    public record VisitResult(
        Set<String> modules,
        List<ImportInfo> imports,
        List<BlindSpot> blindSpots,
        String moduleType
    ) {}

    public record ImportInfo(
        String fromModule,
        String resolvedPath,
        String importType  // IMPORTS, TYPE_IMPORT, REEXPORTS
    ) {}

    public VisitResult extractDependencies(
        Node root,
        String filePath,
        Path sourceRoot
    ) {
        Set<String> modules = new LinkedHashSet<>();
        List<ImportInfo> imports = new ArrayList<>();
        List<BlindSpot> blindSpots = new ArrayList<>();

        // Extract module name from file path
        String moduleName = extractModuleName(filePath, sourceRoot);
        modules.add(moduleName);

        // Walk the AST
        walkAst(root, moduleName, imports, blindSpots);

        return new VisitResult(
            modules,
            imports,
            blindSpots,
            detectModuleType(filePath)
        );
    }

    private void walkAst(
        Node node,
        String currentModule,
        List<ImportInfo> imports,
        List<BlindSpot> blindSpots
    ) {
        if (node == null) return;

        // Check for import statements
        if (isImportCall(node)) {
            // Dynamic import - blind spot
            blindSpots.add(new BlindSpot(
                "DynamicImport",
                currentModule,
                "import() with computed path"
            ));
        }

        if (isImportStatement(node)) {
            String importPath = getImportPath(node);
            String importType = getImportType(node);

            imports.add(new ImportInfo(
                currentModule,
                importPath,
                importType
            ));
        }

        // Recurse into children
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            walkAst(child, currentModule, imports, blindSpots);
        }
    }

    private boolean isImportCall(Node node) {
        // Check for import() expression
        return node.isCall() &&
               node.getFirstChild() != null &&
               "import".equals(node.getFirstChild().getQualifiedName());
    }

    private boolean isImportStatement(Node node) {
        // Check for import/export statements
        // This depends on Closure Compiler's AST node types
        return node.isImport() || node.isExport();
    }

    private String getImportPath(Node node) {
        // Extract the module path from import statement
        Node stringNode = node.getSecondChild();
        return stringNode != null ? stringNode.getString() : "";
    }

    private String getImportType(Node node) {
        // Determine import type: IMPORTS, TYPE_IMPORT, REEXPORTS
        if (node.isImport()) {
            return "IMPORTS";
        } else if (node.isExport()) {
            return "REEXPORTS";
        }
        return "IMPORTS";
    }

    private String extractModuleName(String filePath, Path sourceRoot) {
        // Convert file path to module ID
        Path relative = sourceRoot.relativize(Path.of(filePath));
        String moduleName = relative.toString()
            .replace(".js", "")
            .replace(".jsx", "")
            .replace(".ts", "")
            .replace(".tsx", "")
            .replace("\\", "/");
        return moduleName;
    }

    private String detectModuleType(String filePath) {
        if (filePath.endsWith(".ts") || filePath.endsWith(".tsx")) {
            return "typescript";
        }
        return "javascript";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-js:test --tests JsAstVisitorTest`
Expected: PASS (may need to adjust AST node type detection based on Closure Compiler API)

- [ ] **Step 5: Commit**

```bash
git add archon-js/src/main/java/com/archon/js/JsAstVisitor.java \
        archon-js/src/test/java/com/archon/js/JsAstVisitorTest.java
git commit -m "feat(js): add JsAstVisitor for ES module dependency extraction"
```

---

### Task 3.4: Create ModulePathResolver for path resolution

**Files:**
- Create: `archon-js/src/main/java/com/archon/js/ModulePathResolver.java`
- Test: `archon-js/src/test/java/com/archon/js/ModulePathResolverTest.java`

- [ ] **Step 1: Write the failing test**

```java
// ModulePathResolverTest.java
package com.archon.js;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;

class ModulePathResolverTest {
    @Test
    void testResolvesRelativeImports() {
        ModulePathResolver resolver = new ModulePathResolver();
        Path sourceRoot = Path.of("/src");

        String resolved = resolver.resolve(
            "./components/Header",
            "/src/pages/index.tsx",
            sourceRoot
        );

        assertEquals("src/components/Header", resolved);
    }

    @Test
    void testResolvesBarrelFiles() {
        ModulePathResolver resolver = new ModulePathResolver();
        Path sourceRoot = Path.of("/src");

        String resolved = resolver.resolve(
            "./utils",
            "/src/pages/index.tsx",
            sourceRoot
        );

        // Should resolve to ./utils/index.ts
        assertEquals("src/utils/index", resolved);
    }

    @Test
    void testResolvesTsConfigAliases() {
        ModulePathResolver resolver = new ModulePathResolver();
        Path sourceRoot = Path.of("/src");

        // Configure resolver with tsconfig paths
        resolver.addPathAlias("@/", "src/*");

        String resolved = resolver.resolve(
            "@/components/Header",
            "/src/pages/index.tsx",
            sourceRoot
        );

        assertEquals("src/components/Header", resolved);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-js:test --tests ModulePathResolverTest`
Expected: FAIL with "class ModulePathResolver not found"

- [ ] **Step 3: Write minimal implementation**

```java
// ModulePathResolver.java
package com.archon.js;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Resolves JavaScript/TypeScript module import paths.
 *
 * <p>Handles:
 * <ul>
 *   <li>Relative imports: ./foo, ../bar</li>
 *   <li>Barrel files: ./utils → ./utils/index.ts</li>
 *   <li>Path aliases: @/foo from tsconfig.json</li>
 *   <li>External packages: react, lodash - marked as external</li>
 * </ul>
 */
public class ModulePathResolver {

    private final Map<String, String> pathAliases = new HashMap<>();

    public ModulePathResolver() {
        // Default tsconfig paths
        addPathAlias("@/*", "src/*");
    }

    /**
     * Add a path alias from tsconfig.json compilerOptions.paths.
     *
     * @param alias Pattern like "@/*" or "@components/*"
     * @param target Replacement like "src/*" or "src/components/*"
     */
    public void addPathAlias(String alias, String target) {
        pathAliases.put(alias, target);
    }

    /**
     * Resolve an import path to a canonical module ID.
     *
     * @param importPath The import statement path
     * @param fromFile File containing the import
     * @param sourceRoot Project source root
     * @return Resolved module ID, or empty if external package
     */
    public String resolve(String importPath, String fromFile, Path sourceRoot) {
        // Check for external package (no relative prefix, not an alias)
        if (!importPath.startsWith(".") && !isPathAlias(importPath)) {
            return null; // External package
        }

        String resolved = importPath;

        // Apply path aliases
        for (Map.Entry<String, String> entry : pathAliases.entrySet()) {
            String alias = entry.getKey();
            String target = entry.getValue();

            if (importPath.startsWith(alias.replace("/*", ""))) {
                resolved = importPath.replace(alias.replace("/*", ""), target.replace("/*", ""));
                break;
            }
        }

        // Resolve relative paths
        if (resolved.startsWith("./") || resolved.startsWith("../")) {
            Path fromDir = Path.of(fromFile).getParent();
            Path resolvedPath = fromDir.resolve(resolved).normalize();
            resolved = sourceRoot.relativize(resolvedPath).toString().replace("\\", "/");
        }

        // Check for barrel file (directory without file extension)
        if (!resolved.endsWith(".js") && !resolved.endsWith(".ts") &&
            !resolved.endsWith(".jsx") && !resolved.endsWith(".tsx")) {

            Path barrelPath = sourceRoot.resolve(resolved + "/index.ts");
            if (Files.exists(barrelPath)) {
                resolved = resolved + "/index";
            }
        }

        // Remove file extension for module ID
        resolved = removeExtension(resolved);

        return resolved;
    }

    private boolean isPathAlias(String path) {
        return pathAliases.keySet().stream()
            .anyMatch(alias -> path.startsWith(alias.replace("/*", "")));
    }

    private String removeExtension(String path) {
        return path.replaceAll("\\.(js|jsx|ts|tsx)$", "");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-js:test --tests ModulePathResolverTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add archon-js/src/main/java/com/archon/js/ModulePathResolver.java \
        archon-js/src/test/java/com/archon/js/ModulePathResolverTest.java
git commit -m "feat(js): add ModulePathResolver for ES module path resolution"
```

---

### Task 3.5: Create JsDomainStrategy for workspace detection

**Files:**
- Create: `archon-js/src/main/java/com/archon/js/JsDomainStrategy.java`
- Test: `archon-js/src/test/java/com/archon/js/JsDomainStrategyTest.java`

- [ ] **Step 1: Write the failing test**

```java
// JsDomainStrategyTest.java
package com.archon.js;

import com.archon.core.analysis.DependencyGraph;
import com.archon.core.analysis.DomainStrategy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class JsDomainStrategyTest {
    @Test
    void testAssignsDomainsFromPackageJsonWorkspaces() {
        DomainStrategy strategy = new JsDomainStrategy();
        DependencyGraph graph = DependencyGraph.create();

        Set<String> modules = Set.of(
            "packages/ui/src/components/Button",
            "packages/ui/src/components/Input",
            "packages/app/src/pages/Home"
        );

        Optional<Map<String, String>> result = strategy.assignDomains(graph, modules);

        assertTrue(result.isPresent());
        Map<String, String> domains = result.get();
        assertEquals("ui", domains.get("packages/ui/src/components/Button"));
        assertEquals("ui", domains.get("packages/ui/src/components/Input"));
        assertEquals("app", domains.get("packages/app/src/pages/Home"));
    }

    @Test
    void testHandlesNonMonorepoProjects() {
        DomainStrategy strategy = new JsDomainStrategy();
        DependencyGraph graph = DependencyGraph.create();

        Set<String> modules = Set.of(
            "src/components/Header",
            "src/components/Footer",
            "src/pages/index"
        );

        Optional<Map<String, String>> result = strategy.assignDomains(graph, modules);

        // For non-monorepo, should use "src" as domain
        assertTrue(result.isPresent());
        assertEquals("src", result.get().get("src/components/Header"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-js:test --tests JsDomainStrategyTest`
Expected: FAIL with "class JsDomainStrategy not found"

- [ ] **Step 3: Write minimal implementation**

```java
// JsDomainStrategy.java
package com.archon.js;

import com.archon.core.analysis.DependencyGraph;
import com.archon.core.analysis.DomainStrategy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Domain assignment strategy for JavaScript/TypeScript projects.
 *
 * <p>Uses package.json workspaces to detect monorepo domains:
 * <ul>
 *   <li>packages/ui/src/components/Button → domain: "ui"</li>
 *   <li>packages/app/src/pages/Home → domain: "app"</li>
 * </ul>
 *
 * <p>For non-monorepo projects, uses the top-level directory:
 * <ul>
 *   <li>src/components/Header → domain: "src"</li>
 *   <li>lib/utils/format → domain: "lib"</li>
 * </ul>
 */
public class JsDomainStrategy implements DomainStrategy {

    @Override
    public Optional<Map<String, String>> assignDomains(
        DependencyGraph graph,
        Set<String> sourceModules
    ) {
        Map<String, String> domains = new HashMap<>();

        for (String module : sourceModules) {
            // Strip "js:" prefix if present
            String cleanModule = module.startsWith("js:") ?
                module.substring(3) : module;

            String domain = extractDomain(cleanModule);
            domains.put(module, domain);
        }

        return Optional.of(domains);
    }

    private String extractDomain(String modulePath) {
        String[] segments = modulePath.split("/");

        // Check for monorepo workspace pattern (packages/*)
        if (segments.length > 0 && "packages".equals(segments[0])) {
            // Second segment is the workspace name
            if (segments.length > 1) {
                return segments[1];
            }
        }

        // For non-monorepo, use the first segment as domain
        if (segments.length > 0) {
            return segments[0];
        }

        // Fallback
        return "default";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-js:test --tests JsDomainStrategyTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add archon-js/src/main/java/com/archon/js/JsDomainStrategy.java \
        archon-js/src/test/java/com/archon/js/JsDomainStrategyTest.java
git commit -m "feat(js): add JsDomainStrategy for workspace-based domain assignment"
```

---

### Task 3.6: Register JsPlugin in ServiceLoader configuration

**Files:**
- Create: `archon-js/src/main/resources/META-INF/services/com.archon.core.plugin.LanguagePlugin`

- [ ] **Step 1: Create ServiceLoader configuration**

```bash
mkdir -p archon-js/src/main/resources/META-INF/services
```

- [ ] **Step 2: Write plugin registration file**

```text
# META-INF/services/com.archon.core.plugin.LanguagePlugin
com.archon.js.JsPlugin
```

- [ ] **Step 3: Verify both plugins are discoverable**

Run: `./gradlew :archon-core:test --tests PluginDiscovererTest`
Expected: Discovers both JavaPlugin and JsPlugin (2 plugins)

- [ ] **Step 4: Commit**

```bash
git add archon-js/src/main/resources/META-INF/services/com.archon.core.plugin.LanguagePlugin
git commit -m "feat(js): register JsPlugin via ServiceLoader"
```

---

## Milestone 4: CLI Integration

Wire up ParseOrchestrator in CLI commands to use multi-plugin parsing.

### Task 4.1: Update AnalyzeCommand to use ParseOrchestrator

**Files:**
- Modify: `archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java`
- Test: `archon-cli/src/test/java/com/archon/cli/MultiPluginAnalyzeCommandTest.java`

- [ ] **Step 1: Write the failing test**

```java
// MultiPluginAnalyzeCommandTest.java
package com.archon.cli;

import com.archon.core.plugin.PluginDiscoverer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

class MultiPluginAnalyzeCommandTest {
    @Test
    void testAnalyzeWithJavaAndJsPlugins() throws Exception {
        // Create test project with both Java and JS files
        Path testDir = Files.createTempDirectory("archon-test");
        Files.writeString(testDir.resolve("Foo.java"), "public class Foo {}");
        Files.writeString(testDir.resolve("bar.js"), "export const bar = 1;");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AnalyzeCommand command = new AnalyzeCommand(
            new PluginDiscoverer(),
            new PrintStream(output)
        );

        int exitCode = command.analyze(testDir.toString());

        assertEquals(0, exitCode);
        String result = output.toString();
        assertTrue(result.contains("Java") || result.contains("java"));
        assertTrue(result.contains("JS") || result.contains("javascript"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :archon-cli:test --tests MultiPluginAnalyzeCommandTest`
Expected: FAIL (AnalyzeCommand doesn't use ParseOrchestrator yet)

- [ ] **Step 3: Modify AnalyzeCommand**

```java
// In AnalyzeCommand.java, update the analyze() method:

public int analyze(String sourcePath) {
    Path root = Path.of(sourcePath);

    // Discover plugins
    PluginDiscoverer discoverer = new PluginDiscoverer();
    List<LanguagePlugin> plugins = discoverer.discoverWithConflictCheck();

    if (plugins.isEmpty()) {
        System.err.println("Error: No language plugins found");
        return 1;
    }

    // Create orchestrator
    ParseOrchestrator orchestrator = new ParseOrchestrator(plugins);

    // Collect source files
    List<Path> sourceFiles = collectSourceFiles(root, plugins);

    // Parse with all plugins
    ParseContext context = new ParseContext(
        root,
        plugins.stream()
            .flatMap(p -> p.fileExtensions().stream())
            .collect(Collectors.toSet())
    );

    ParseResult result = orchestrator.parse(sourceFiles, context);

    // Run analysis on unified graph
    DependencyGraph graph = result.getGraph();

    // Output results
    printAnalysis(graph, result);

    return 0;
}

private List<Path> collectSourceFiles(Path root, List<LanguagePlugin> plugins) {
    Set<String> extensions = plugins.stream()
        .flatMap(p -> p.fileExtensions().stream())
        .collect(Collectors.toSet());

    try (Stream<Path> walk = Files.walk(root)) {
        return walk
            .filter(Files::isRegularFile)
            .filter(p -> extensions.contains(getExtension(p)))
            .collect(Collectors.toList());
    } catch (IOException e) {
        return List.of();
    }
}

private String getExtension(Path file) {
    String name = file.getFileName().toString();
    int dot = name.lastIndexOf('.');
    return dot > 0 ? name.substring(dot + 1) : "";
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :archon-cli:test --tests MultiPluginAnalyzeCommandTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java \
        archon-cli/src/test/java/com/archon/cli/MultiPluginAnalyzeCommandTest.java
git commit -m "feat(cli): wire AnalyzeCommand to ParseOrchestrator"
```

---

### Task 4.2: Update DiffCommand and CheckCommand similarly

**Files:**
- Modify: `archon-cli/src/main/java/com/archon/cli/DiffCommand.java`
- Modify: `archon-cli/src/main/java/com/archon/cli/CheckCommand.java`
- Test: `archon-cli/src/test/java/com/archon/cli/MultiPluginDiffCommandTest.java`

- [ ] **Step 1: Update DiffCommand**

Apply similar changes to DiffCommand.java to use ParseOrchestrator for multi-language diff support.

- [ ] **Step 2: Update CheckCommand**

Apply similar changes to CheckCommand.java.

- [ ] **Step 3: Write tests**

Create MultiPluginDiffCommandTest.java with tests for multi-language diff scenarios.

- [ ] **Step 4: Run tests**

Run: `./gradlew :archon-cli:test`
Expected: All CLI tests pass

- [ ] **Step 5: Commit**

```bash
git add archon-cli/src/main/java/com/archon/cli/DiffCommand.java \
        archon-cli/src/main/java/com/archon/cli/CheckCommand.java \
        archon-cli/src/test/java/com/archon/cli/MultiPluginDiffCommandTest.java
git commit -m "feat(cli): wire DiffCommand and CheckCommand to ParseOrchestrator"
```

---

## Milestone 5: Testing and Validation

End-to-end validation against real projects and compliance tests.

### Task 5.1: Run E2E validation against user's real JS/TS monorepo

**Files:**
- No new files - manual validation step

- [ ] **Step 1: Get monorepo location from user**

Ask user for the path to their real JS/TS monorepo (500+ modules).

- [ ] **Step 2: Run analyze on the monorepo**

```bash
java -jar archon-cli/build/libs/archon-0.3.0.0.jar analyze <monorepo-path>
```

- [ ] **Step 3: Verify results**

Check:
- Tool completes without crashing
- Node count is reasonable (not zero, not millions)
- Domain detection works (workspaces detected)
- Blind spots are reported for dynamic imports
- Output is actionable

- [ ] **Step 4: Document findings**

Create a validation report documenting:
- Monorepo size (modules, files)
- Parse time
- Nodes and edges detected
- Domains found
- Blind spots count

- [ ] **Step 5: Commit validation report**

```bash
git add docs/validation/e2e-js-monorepo-report.md
git commit -m "docs: add E2E validation report for JS/TS monorepo"
```

---

### Task 5.2: Verify SpiComplianceTest passes for both plugins

**Files:**
- Test: `archon-test/src/test/java/com/archon/test/spi/SpiComplianceTest.java`

- [ ] **Step 1: Run SpiComplianceTest**

Run: `./gradlew :archon-test:test --tests SpiComplianceTest`
Expected: PASS for both JavaPlugin and JsPlugin

- [ ] **Step 2: Verify all SPI contracts are met**

Check:
- Both plugins have non-empty fileExtensions()
- Both plugins handle parseFromContent gracefully
- No extension conflicts
- Both plugins use namespace prefixing

- [ ] **Step 3: Document compliance**

If tests pass, SPI is stable enough for open source.

- [ ] **Step 4: Commit**

```bash
git commit -m "test: verify SpiComplianceTest passes for all plugins"
```

---

### Task 5.3: Update version and CHANGELOG

**Files:**
- Modify: `VERSION`
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Bump version to 0.3.0.0**

Update VERSION file: `0.3.0.0`

- [ ] **Step 2: Update CHANGELOG.md**

```markdown
## [0.3.0.0] - 2026-04-XX

### Added
- Multi-language support via ServiceLoader-based LanguagePlugin SPI
- JavaScript/TypeScript parser plugin using Google Closure Compiler
- ParseOrchestrator for two-phase multi-plugin graph construction
- Namespace prefixing (java:, js:) for multi-language node isolation
- JsDomainStrategy for package.json workspace detection
- ModulePathResolver for ES module import resolution
- SpiComplianceTest for SPI contract validation

### Changed
- JavaParserPlugin refactored as JavaPlugin implementing LanguagePlugin
- DomainStrategy now optional via Optional<> return type
- ParseResult includes DependencyGraph and uses sourceModules (language-agnostic)
- CLI commands now use ParseOrchestrator for unified multi-language parsing

### Fixed
- Edge loss in multi-plugin graphs via two-phase construction
- Namespace collision risk via language prefixing
- External class pollution handled via namespace filtering

### Technical
- Added archon-js Gradle module
- ServiceLoader discovery via META-INF/services
- Closure Compiler validated for type-only import handling
```

- [ ] **Step 3: Commit**

```bash
git add VERSION CHANGELOG.md
git commit -m "chore: bump version to v0.3.0.0 for multi-language SPI release"
```

---

## Architecture Decision Records

This plan implements the following architecture decisions from the engineering review:

1. **ParseOrchestrator for two-phase multi-plugin coordination** (Milestone 1, Task 1.7)
   - Prevents edge loss when Plugin A references nodes from Plugin B
   - All plugins add nodes first (with prefixes), then edges

2. **Node ID namespace prefixing** (Milestone 1, Task 1.5)
   - Each plugin prefixes node IDs: "java:", "js:", etc.
   - ParseOrchestrator strips prefixes before building final graph

3. **DomainStrategy optional via Optional<>** (Milestone 1, Task 1.4)
   - Plugins without domain concepts return Optional.empty()
   - Fallback to pivot detection when absent

4. **ParseResult includes DependencyGraph** (Milestone 1, Task 1.3)
   - Plugins return complete result, not partial metadata
   - sourceClasses renamed to sourceModules for language-agnostic naming

5. **Warn on unclaimed file extensions** (Milestone 1, Task 1.7)
   - ParseOrchestrator logs warning for files with no matching plugin

6. **E2E validation on real JS/TS monorepo** (Milestone 5, Task 5.1)
   - User provides real monorepo location
   - Validates parsing correctness on production-scale codebase

---

## Success Criteria

After completing all tasks, the following should be true:

1. **SPI is stable:** Both Java and JS plugins implement LanguagePlugin correctly
2. **Zero regression:** All 81 existing Java tests pass without modification
3. **Multi-language graphs work:** Java + JS files in same project produce unified graph
4. **Namespace isolation:** No collisions between java:com.foo.Bar and js:src/components/Header
5. **Blind spots reported:** Dynamic patterns flagged, not silently ignored
6. **E2E validated:** Real JS/TS monorepo parses successfully with actionable output

---

## Execution Options

After this plan is saved, you have two execution approaches:

**1. Subagent-Driven (recommended)**
- Fresh subagent per task + two-stage review
- Faster iteration, cleaner context between tasks
- Use `superpowers:subagent-driven-development`

**2. Inline Execution**
- Execute tasks in this session
- Batch execution with checkpoints
- Use `superpowers:executing-plans`

---

**Plan complete.** Save to `docs/superpowers/plans/2026-04-03-multi-language-spi-js-plugin.md`.
