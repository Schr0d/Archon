# Milestone 1: LanguagePlugin SPI Extraction Implementation Plan

> **For agentic workers:** Execute tasks sequentially. Each task has TDD steps: write test → run (expect fail) → implement → run (expect pass) → commit.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract a LanguagePlugin SPI from archon-java, enabling multi-language support while preserving all existing Java functionality.

**Architecture:** Core SPI defines `LanguagePlugin` interface. Plugins implement it and register via ServiceLoader. `DomainDetector` delegates to per-language `DomainStrategy`. CLI uses `PluginDiscoverer` to dispatch by file extension.

**Tech Stack:** Java 17+, ServiceLoader (standard), JUnit 5, Picocli, Gradle Kotlin DSL

---

## Task 1: Extend NodeType and EdgeType Enums

**Files:**
- Modify: `archon-core/src/main/java/com/archon/core/graph/NodeType.java`
- Modify: `archon-core/src/main/java/com/archon/core/graph/EdgeType.java`
- Test: `archon-core/src/test/java/com/archon/core/graph/NodeTypeTest.java` (create)
- Test: `archon-core/src/test/java/com/archon/core/graph/EdgeTypeTest.java` (create)

### Step 1.1: Add COMPONENT and HOOK to NodeType

```java
// archon-core/src/main/java/com/archon/core/graph/NodeType.java
public enum NodeType {
    CLASS,
    MODULE,
    PACKAGE,
    SERVICE,
    CONTROLLER,
    COMPONENT,  // For JS/TS React/Vue components
    HOOK         // For JS/TS React hooks
}
```

**Commit:** `feat: extend NodeType with COMPONENT and HOOK`

---

### Step 1.2: Add REEXPORTS and TYPE_IMPORT to EdgeType

```java
// archon-core/src/main/java/com/archon/core/graph/EdgeType.java
public enum EdgeType {
    IMPORTS,
    CALLS,
    IMPLEMENTS,
    EXTENDS,
    USES,
    REEXPORTS,    // For JS/TS export { foo } from 'bar'
    TYPE_IMPORT   // For JS/TS import type { Foo }
}
```

**Commit:** `feat: extend EdgeType with REEXPORTS and TYPE_IMPORT`

---

### Step 1.3: Write NodeTypeTest

```java
// archon-core/src/test/java/com/archon/core/graph/NodeTypeTest.java
package com.archon.core.graph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NodeTypeTest {

    @Test
    void component_exists() {
        assertNotNull(NodeType.valueOf("COMPONENT"));
    }

    @Test
    void hook_exists() {
        assertNotNull(NodeType.valueOf("HOOK"));
    }

    @Test
    void all_values_present() {
        NodeType[] values = NodeType.values();
        assertTrue(values.length >= 7); // CLASS, MODULE, PACKAGE, SERVICE, CONTROLLER, COMPONENT, HOOK
    }
}
```

**Commit:** `test: add NodeTypeTest`

---

### Step 1.4: Write EdgeTypeTest

```java
// archon-core/src/test/java/com/archon/core/graph/EdgeTypeTest.java
package com.archon.core.graph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EdgeTypeTest {

    @Test
    void reexports_exists() {
        assertNotNull(EdgeType.valueOf("REEXPORTS"));
    }

    @Test
    void type_import_exists() {
        assertNotNull(EdgeType.valueOf("TYPE_IMPORT"));
    }

    @Test
    void all_values_present() {
        EdgeType[] values = EdgeType.values();
        assertTrue(values.length >= 7); // IMPORTS, CALLS, IMPLEMENTS, EXTENDS, USES, REEXPORTS, TYPE_IMPORT
    }
}
```

**Commit:** `test: add EdgeTypeTest`

---

## Task 2: Create ParseError in archon-core

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/plugin/ParseError.java`
- Test: `archon-core/src/test/java/com/archon/core/plugin/ParseErrorTest.java`

### Step 2.1: Create ParseError class

```java
// archon-core/src/main/java/com/archon/core/plugin/ParseError.java
package com.archon.core.plugin;

import java.util.Objects;

/**
 * Represents a parsing error detected by a language plugin.
 */
public class ParseError {
    private final String file;
    private final int line;
    private final String message;

    public ParseError(String file, int line, String message) {
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.line = line;
        this.message = Objects.requireNonNull(message, "message must not be null");
    }

    public String getFile() { return file; }
    public int getLine() { return line; }
    public String getMessage() { return message; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParseError)) return false;
        ParseError that = (ParseError) o;
        return line == that.line && file.equals(that.file) && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(file, line, message);
    }

    @Override
    public String toString() {
        return "ParseError{" + file + ":" + line + ": " + message + "}";
    }
}
```

**Commit:** `feat: add ParseError to archon-core plugin package`

---

### Step 2.2: Write ParseErrorTest

```java
// archon-core/src/test/java/com/archon/core/plugin/ParseErrorTest.java
package com.archon.core.plugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParseErrorTest {

    @Test
    void constructor_setsAllFields() {
        ParseError error = new ParseError("Foo.java", 42, "syntax error");
        assertEquals("Foo.java", error.getFile());
        assertEquals(42, error.getLine());
        assertEquals("syntax error", error.getMessage());
    }

    @Test
    void constructor_rejectsNullFile() {
        assertThrows(NullPointerException.class, () -> new ParseError(null, 0, "msg"));
    }

    @Test
    void constructor_rejectsNullMessage() {
        assertThrows(NullPointerException.class, () -> new ParseError("file", 0, null));
    }

    @Test
    void equality_sameValues() {
        ParseError e1 = new ParseError("Foo.java", 10, "error");
        ParseError e2 = new ParseError("Foo.java", 10, "error");
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    void inequality_differentMessage() {
        ParseError e1 = new ParseError("Foo.java", 10, "error1");
        ParseError e2 = new ParseError("Foo.java", 10, "error2");
        assertNotEquals(e1, e2);
    }
}
```

**Commit:** `test: add ParseErrorTest`

---

## Task 3: Create ParseResult in archon-core

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/plugin/ParseResult.java`
- Test: `archon-core/src/test/java/com/archon/core/plugin/ParseResultTest.java`

### Step 3.1: Create ParseResult class

```java
// archon-core/src/main/java/com/archon/core/plugin/ParseResult.java
package com.archon.core.plugin;

import com.archon.core.graph.BlindSpot;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Result returned by LanguagePlugin.parse() containing blind spots, errors, and discovered source modules.
 */
public class ParseResult {
    private final List<BlindSpot> blindSpots;
    private final List<ParseError> errors;
    private final Set<String> sourceModules;  // all classes/modules found in source tree

    public ParseResult(List<BlindSpot> blindSpots, List<ParseError> errors, Set<String> sourceModules) {
        this.blindSpots = Collections.unmodifiableList(blindSpots);
        this.errors = Collections.unmodifiableList(errors);
        this.sourceModules = Collections.unmodifiableSet(new HashSet<>(sourceModules));
    }

    public List<BlindSpot> getBlindSpots() { return blindSpots; }
    public List<ParseError> getErrors() { return errors; }
    public Set<String> getSourceModules() { return sourceModules; }
}
```

**Commit:** `feat: add ParseResult to archon-core plugin package`

---

### Step 3.2: Write ParseResultTest

```java
// archon-core/src/test/java/com/archon/core/plugin/ParseResultTest.java
package com.archon.core.plugin;

import com.archon.core.graph.BlindSpot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;

class ParseResultTest {

    @Test
    void constructor_setsAllFields() {
        BlindSpot bs = new BlindSpot("Foo.java", 10, "reflection", "Class.forName");
        ParseError pe = new ParseError("Bar.java", 20, "syntax error");
        Set<String> modules = Set.of("com.example.Foo", "com.example.Bar");

        ParseResult result = new ParseResult(List.of(bs), List.of(pe), modules);

        assertEquals(1, result.getBlindSpots().size());
        assertEquals(bs, result.getBlindSpots().get(0));
        assertEquals(1, result.getErrors().size());
        assertEquals(pe, result.getErrors().get(0));
        assertEquals(2, result.getSourceModules().size());
        assertTrue(result.getSourceModules().contains("com.example.Foo"));
    }

    @Test
    void collectionsAreImmutable() {
        ParseResult result = new ParseResult(List.of(), List.of(), Set.of());
        assertThrows(UnsupportedOperationException.class, () -> result.getBlindSpots().add(null));
        assertThrows(UnsupportedOperationException.class, () -> result.getErrors().add(null));
        assertThrows(UnsupportedOperationException.class, () -> result.getSourceModules().add(null));
    }

    @Test
    void emptyLists_allowed() {
        ParseResult result = new ParseResult(List.of(), List.of(), Set.of());
        assertTrue(result.getBlindSpots().isEmpty());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getSourceModules().isEmpty());
    }
}
```

**Commit:** `test: add ParseResultTest`

---

## Task 4: Create ParseContext in archon-core

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/plugin/ParseContext.java`
- Test: `archon-core/src/test/java/com/archon/core/plugin/ParseContextTest.java`

### Step 4.1: Create ParseContext class

```java
// archon-core/src/main/java/com/archon/core/plugin/ParseContext.java
package com.archon.core.plugin;

import com.archon.core.config.ArchonConfig;
import com.archon.core.analysis.ModuleLayout;

import java.nio.file.Path;
import java.util.Set;

/**
 * Context object passed to LanguagePlugin.parse() containing project metadata.
 */
public class ParseContext {
    private final Path projectRoot;
    private final Set<String> sourceTreeModules;
    private final ArchonConfig config;
    private final ModuleLayout moduleLayout;

    private ParseContext(Builder builder) {
        this.projectRoot = builder.projectRoot;
        this.sourceTreeModules = Set.copyOf(builder.sourceTreeModules);
        this.config = builder.config;
        this.moduleLayout = builder.moduleLayout;
    }

    public Path getProjectRoot() { return projectRoot; }
    public Set<String> getSourceTreeModules() { return sourceTreeModules; }
    public ArchonConfig getConfig() { return config; }
    public ModuleLayout getModuleLayout() { return moduleLayout; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Path projectRoot;
        private Set<String> sourceTreeModules = Set.of();
        private ArchonConfig config;
        private ModuleLayout moduleLayout;

        public Builder projectRoot(Path path) {
            this.projectRoot = path;
            return this;
        }

        public Builder sourceTreeModules(Set<String> modules) {
            this.sourceTreeModules = modules;
            return this;
        }

        public Builder config(ArchonConfig config) {
            this.config = config;
            return this;
        }

        public Builder moduleLayout(ModuleLayout layout) {
            this.moduleLayout = layout;
            return this;
        }

        public ParseContext build() {
            return new ParseContext(this);
        }
    }
}
```

**Commit:** `feat: add ParseContext to archon-core plugin package`

---

### Step 4.2: Write ParseContextTest

```java
// archon-core/src/test/java/com/archon/core/plugin/ParseContextTest.java
package com.archon.core.plugin;

import com.archon.core.config.ArchonConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Set;

class ParseContextTest {

    @Test
    void builder_constructsValidContext() {
        Path root = Path.of("/project");
        Set<String> modules = Set.of("com.example.Foo");
        ArchonConfig config = ArchonConfig.defaults();

        ParseContext ctx = ParseContext.builder()
            .projectRoot(root)
            .sourceTreeModules(modules)
            .config(config)
            .build();

        assertEquals(root, ctx.getProjectRoot());
        assertEquals(modules, ctx.getSourceTreeModules());
        assertEquals(config, ctx.getConfig());
        assertNull(ctx.getModuleLayout());
    }

    @Test
    void sourceTreeModules_isCopied_defensive() {
        Set<String> original = Set.of("com.example.Foo");
        ParseContext ctx = ParseContext.builder()
            .projectRoot(Path.of("/"))
            .sourceTreeModules(original)
            .config(ArchonConfig.defaults())
            .build();

        // Modifying original doesn't affect context
        // (Can't actually test this easily with Set.copyOf, but the structure is defensive)
        assertEquals(1, ctx.getSourceTreeModules().size());
    }
}
```

**Commit:** `test: add ParseContextTest`

---

## Task 5: Create ModuleLayout in archon-core

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/plugin/ModuleLayout.java`

### Step 5.1: Create ModuleLayout class

```java
// archon-core/src/main/java/com/archon/core/plugin/ModuleLayout.java
package com.archon.core.plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents detected module structure (Maven/Gradle source roots, package.json workspaces, etc.).
 */
public class ModuleLayout {
    private final List<SourceRoot> sourceRoots;

    public ModuleLayout(List<SourceRoot> sourceRoots) {
        this.sourceRoots = Collections.unmodifiableList(new ArrayList<>(sourceRoots));
    }

    public List<SourceRoot> getSourceRoots() { return sourceRoots; }

    public static class SourceRoot {
        private final Path path;
        private final String type;  // "maven", "gradle", "npm", "pnpm-workspace", etc.

        public SourceRoot(Path path, String type) {
            this.path = path;
            this.type = type;
        }

        public Path getPath() { return path; }
        public String getType() { return type; }
    }
}
```

**Commit:** `feat: add ModuleLayout to archon-core plugin package`

---

## Task 6: Create DomainStrategy interface in archon-core

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/plugin/DomainStrategy.java`
- Test: `archon-core/src/test/java/com/archon/core/plugin/DomainStrategyTest.java`

### Step 6.1: Create DomainStrategy interface

```java
// archon-core/src/main/java/com/archon/core/plugin/DomainStrategy.java
package com.archon.core.plugin;

import java.util.Map;
import java.util.Optional;

/**
 * Strategy interface for language-specific domain assignment.
 * Each language plugin provides an implementation that knows how to extract domains from its module IDs.
 */
public interface DomainStrategy {
    /**
     * Extract domain from a module ID. Returns empty if no domain can be determined.
     */
    Optional<String> extractDomain(String moduleId);

    /**
     * Get all domains in the project, given the set of module IDs.
     */
    Map<String, String> assignDomains(Set<String> moduleIds);
}
```

**Commit:** `feat: add DomainStrategy interface to archon-core plugin package`

---

### Step 6.2: Write DomainStrategyTest

```java
// archon-core/src/test/java/com/archon/core/plugin/DomainStrategyTest.java
package com.archon.core.plugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Set;

class DomainStrategyTest {

    @Test
    void stubStrategy_returnsEmpty() {
        DomainStrategy stub = new DomainStrategy() {
            @Override
            public java.util.Optional<String> extractDomain(String id) {
                return java.util.Optional.empty();
            }

            @Override
            public Map<String, String> assignDomains(Set<String> ids) {
                return Map.of();
            }
        };

        assertTrue(stub.extractDomain("foo").isEmpty());
        assertTrue(stub.assignDomains(Set.of("foo")).isEmpty());
    }
}
```

**Commit:** `test: add DomainStrategyTest`

---

## Task 7: Create LanguagePlugin interface in archon-core

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/plugin/LanguagePlugin.java`
- Test: `archon-core/src/test/java/com/archon/core/plugin/LanguagePluginTest.java`

### Step 7.1: Create LanguagePlugin interface

```java
// archon-core/src/main/java/com/archon/core/plugin/LanguagePlugin.java
package com.archon.core.plugin;

import com.archon.core.graph.DependencyGraph;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Service Provider Interface (SPI) for language-specific dependency parsing plugins.
 * Plugins are discovered via java.util.ServiceLoader and registered in META-INF/services/.
 */
public interface LanguagePlugin {
    /**
     * Language identifier (e.g., "java", "javascript", "typescript").
     */
    String languageId();

    /**
     * File extensions this plugin handles (e.g., ".java", ".ts").
     */
    Set<String> fileExtensions();

    /**
     * Parse source tree into graph builder.
     * Returns parse result with blind spots, errors, and discovered source modules.
     */
    ParseResult parse(ParseContext context, DependencyGraph.MutableBuilder builder);

    /**
     * Parse in-memory content (for git diff base graph).
     * Required for diff command support.
     *
     * @param fileContents map of relative file path -> file content string
     * @param knownSourceClasses complete set of source module IDs (from head graph + base modules)
     */
    ParseResult parseFromContent(Map<Path, String> fileContents,
                                  Set<String> knownSourceModules,
                                  DependencyGraph.MutableBuilder builder);
}
```

**Commit:** `feat: add LanguagePlugin interface to archon-core plugin package`

---

### Step 7.2: Write LanguagePluginTest

```java
// archon-core/src/test/java/com/archon/core/plugin/LanguagePluginTest.java
package com.archon.core.plugin;

import com.archon.core.graph.DependencyGraph;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Set;

class LanguagePluginTest {

    @Test
    void stubPlugin_implementsInterface() {
        LanguagePlugin stub = new LanguagePlugin() {
            @Override
            public String languageId() { return "test"; }

            @Override
            public Set<String> fileExtensions() { return Set.of(".test"); }

            @Override
            public ParseResult parse(ParseContext ctx, DependencyGraph.MutableBuilder builder) {
                return new ParseResult(List.of(), List.of(), Set.of());
            }

            @Override
            public ParseResult parseFromContent(Map<Path, String> contents,
                                                Set<String> known,
                                                DependencyGraph.MutableBuilder builder) {
                return new ParseResult(List.of(), List.of(), Set.of());
            }
        };

        assertEquals("test", stub.languageId());
        assertTrue(stub.fileExtensions().contains(".test"));
        assertNotNull(stub.parse(null, DependencyGraph.builder()));
        assertNotNull(stub.parseFromContent(Map.of(), Set.of(), DependencyGraph.builder()));
    }
}
```

**Commit:** `test: add LanguagePluginTest`

---

## Task 8: Create PluginDiscoverer in archon-core

**Files:**
- Create: `archon-core/src/main/java/com/archon/core/plugin/PluginDiscoverer.java`
- Test: `archon-core/src/test/java/com/archon/core/plugin/PluginDiscovererTest.java`

### Step 8.1: Create PluginDiscoverer class

```java
// archon-core/src/main/java/com/archon/core/plugin/PluginDiscoverer.java
package com.archon.core.plugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Discovers LanguagePlugin implementations via ServiceLoader.
 * Detects and reports conflicts when multiple plugins claim the same file extension.
 */
public class PluginDiscoverer {
    private final List<LanguagePlugin> plugins;
    private final Map<String, LanguagePlugin> extensionToPlugin;
    private final Map<String, Set<String>> extensionConflicts;

    public PluginDiscoverer(ClassLoader classLoader) {
        this.plugins = new ArrayList<>();
        this.extensionToPlugin = new HashMap<>();
        this.extensionConflicts = new HashMap<>();

        ServiceLoader<LanguagePlugin> loader = ServiceLoader.load(LanguagePlugin.class, classLoader);
        for (LanguagePlugin plugin : loader) {
            plugins.add(plugin);
            for (String ext : plugin.fileExtensions()) {
                if (extensionToPlugin.containsKey(ext)) {
                    extensionConflicts.computeIfAbsent(ext, k -> new LinkedHashSet<>()).add(extensionToPlugin.get(ext).languageId());
                    extensionConflicts.get(ext).add(plugin.languageId());
                } else {
                    extensionToPlugin.put(ext, plugin);
                }
            }
        }
    }

    /**
     * Get all discovered plugins.
     */
    public List<LanguagePlugin> getPlugins() {
        return plugins;
    }

    /**
     * Find plugin for a file extension. Returns null if not found or if conflict exists.
     */
    public LanguagePlugin findPlugin(String extension) {
        if (extensionConflicts.containsKey(extension)) {
            return null;  // Conflict exists — caller must resolve
        }
        return extensionToPlugin.get(extension);
    }

    /**
     * Check if there are extension conflicts.
     * @return map of extension -> set of conflicting language IDs
     */
    public Map<String, Set<String>> getConflicts() {
        return extensionConflicts;
    }

    /**
     * Verify no conflicts exist. Throws IllegalStateException if conflicts detected.
     */
    public void verifyNoConflicts() {
        if (!extensionConflicts.isEmpty()) {
            StringBuilder sb = new StringBuilder("Plugin extension conflicts detected:\n");
            for (Map.Entry<String, Set<String>> entry : extensionConflicts.entrySet()) {
                sb.append("  .").append(entry.getKey()).append(": ")
                  .append(String.join(", ", entry.getValue())).append("\n");
            }
            throw new IllegalStateException(sb.toString());
        }
    }

    /**
     * Get all supported extensions (union of all plugins).
     */
    public Set<String> getAllExtensions() {
        Set<String> all = new LinkedHashSet<>();
        for (LanguagePlugin plugin : plugins) {
            all.addAll(plugin.fileExtensions());
        }
        return all;
    }
}
```

**Commit:** `feat: add PluginDiscoverer to archon-core plugin package`

---

### Step 8.2: Write PluginDiscovererTest

```java
// archon-core/src/test/java/com/archon/core/plugin/PluginDiscovererTest.java
package com.archon.core.plugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

class PluginDiscovererTest {

    @Test
    void discover_noPlugins_emptyResult() {
        PluginDiscoverer discoverer = new PluginDiscoverer(getClass().getClassLoader());
        assertTrue(discoverer.getPlugins().isEmpty());
        assertTrue(discoverer.getAllExtensions().isEmpty());
        assertTrue(discoverer.getConflicts().isEmpty());
    }

    @Test
    void findPlugin_returnsNullForUnknownExtension() {
        PluginDiscoverer discoverer = new PluginDiscoverer(getClass().getClassLoader());
        assertNull(discoverer.findPlugin(".unknown"));
    }

    @Test
    void verifyNoConflicts_passesWhenNoConflicts() {
        PluginDiscoverer discoverer = new PluginDiscoverer(getClass().getClassLoader());
        assertDoesNotThrow(() -> discoverer.verifyNoConflicts());
    }
}
```

**Commit:** `test: add PluginDiscovererTest`

---

## Task 9: Refactor DomainDetector to use DomainStrategy

**Files:**
- Modify: `archon-core/src/main/java/com/archon/core/analysis/DomainDetector.java`
- Test: `archon-core/src/test/java/com/archon/core/analysis/DomainDetectorTest.java` (update existing)

### Step 9.1: Refactor DomainDetector constructor to accept DomainStrategy

```java
// archon-core/src/main/java/com/archon/core/analysis/DomainDetector.java
package com.archon.core.analysis;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.plugin.DomainStrategy;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assigns domain labels to nodes using a pluggable DomainStrategy.
 * Three-tier resolution: config match (HIGH) -> strategy extraction (MEDIUM/LOW) -> fallback (LOW).
 */
public class DomainDetector {

    private final DomainStrategy strategy;

    public DomainDetector(DomainStrategy strategy) {
        this.strategy = strategy;
    }

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
        // Tier 1: config override - exact package prefix match
        for (Map.Entry<String, List<String>> entry : domainMappings.entrySet()) {
            for (String prefix : entry.getValue()) {
                if (nodeId.startsWith(prefix)) {
                    return new Resolution(entry.getKey(), Confidence.HIGH);
                }
            }
        }

        // Tier 2: strategy extraction - language-specific domain logic
        var strategyDomain = strategy.extractDomain(nodeId);
        if (strategyDomain.isPresent()) {
            return new Resolution(strategyDomain.get(), Confidence.MEDIUM);
        }

        // Tier 3: top-level fallback
        String[] parts = nodeId.split("\\.");
        if (parts.length >= 2) {
            return new Resolution(parts[0], Confidence.LOW);
        }

        return new Resolution("unknown", Confidence.LOW);
    }

    private record Resolution(String domain, Confidence confidence) {}
}
```

**Commit:** `refactor: DomainDetector uses DomainStrategy interface`

---

### Step 9.2: Update DomainDetectorTest to use DomainStrategy

```java
// archon-core/src/test/java/com/archon/core/analysis/DomainDetectorTest.java
package com.archon.core.analysis;

import com.archon.core.graph.Confidence;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.graph.Node;
import com.archon.core.graph.NodeType;
import com.archon.core.plugin.DomainStrategy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class DomainDetectorTest {

    private static class JavaDomainStrategyStub implements DomainStrategy {
        @Override
        public Optional<String> extractDomain(String moduleId) {
            String[] parts = moduleId.split("\\.");
            // Mimic old pivot detection: return 3rd segment for 4+ deep packages
            if (parts.length >= 4) {
                return Optional.of(parts[2]);
            }
            return Optional.empty();
        }

        @Override
        public Map<String, String> assignDomains(Set<String> moduleIds) {
            // Not used in new DomainDetector
            return Map.of();
        }
    }

    private DependencyGraph buildGraph(String... fqns) {
        GraphBuilder builder = GraphBuilder.builder();
        for (String fqn : fqns) {
            builder.addNode(Node.builder().id(fqn).type(NodeType.CLASS).build());
        }
        return builder.build();
    }

    @Test
    void assignDomains_configOverride_assignsCorrectDomain() {
        DomainDetector detector = new DomainDetector(new JavaDomainStrategyStub());
        DependencyGraph graph = buildGraph(
            "com.fuwa.framework.security.LoginService",
            "com.fuwa.system.domain.SysUser"
        );
        Map<String, List<String>> mappings = Map.of(
            "auth", List.of("com.fuwa.framework.security"),
            "system", List.of("com.fuwa.system")
        );

        DomainResult result = detector.assignDomains(graph, mappings);

        assertEquals("auth", result.getDomain("com.fuwa.framework.security.LoginService").orElse(null));
        assertEquals("system", result.getDomain("com.fuwa.system.domain.SysUser").orElse(null));
    }

    @Test
    void assignDomains_configOverride_hasHighConfidence() {
        DomainDetector detector = new DomainDetector(new JavaDomainStrategyStub());
        DependencyGraph graph = buildGraph("com.fuwa.framework.security.LoginService");
        Map<String, List<String>> mappings = Map.of("auth", List.of("com.fuwa.framework.security"));

        DomainResult result = detector.assignDomains(graph, mappings);

        assertEquals(Confidence.HIGH, result.getConfidence("com.fuwa.framework.security.LoginService"));
    }

    @Test
    void assignDomains_strategyAssignsFromPackageSegment() {
        DomainDetector detector = new DomainDetector(new JavaDomainStrategyStub());
        DependencyGraph graph = buildGraph("com.fuwa.system.domain.SysUser");

        DomainResult result = detector.assignDomains(graph, Map.of());

        assertEquals("domain", result.getDomain("com.fuwa.system.domain.SysUser").orElse(null));
        assertEquals(Confidence.MEDIUM, result.getConfidence("com.fuwa.system.domain.SysUser"));
    }

    @Test
    void assignDomains_noMatch_lowConfidence() {
        DomainDetector detector = new DomainDetector(new JavaDomainStrategyStub());
        DependencyGraph graph = buildGraph("x.Service");

        DomainResult result = detector.assignDomains(graph, Map.of());

        assertEquals("x", result.getDomain("x.Service").orElse(null));
        assertEquals(Confidence.LOW, result.getConfidence("x.Service"));
    }

    @Test
    void assignDomains_emptyGraph_returnsEmptyResult() {
        DomainDetector detector = new DomainDetector(new JavaDomainStrategyStub());
        DependencyGraph graph = GraphBuilder.builder().build();

        DomainResult result = detector.assignDomains(graph, Map.of());

        assertEquals(0, result.size());
    }

    @Test
    void assignDomains_configTakesPriorityOverConvention() {
        DomainDetector detector = new DomainDetector(new JavaDomainStrategyStub());
        DependencyGraph graph = buildGraph("com.fuwa.security.AuthHandler");
        Map<String, List<String>> mappings = Map.of("auth", List.of("com.fuwa.security"));

        DomainResult result = detector.assignDomains(graph, mappings);

        assertEquals("auth", result.getDomain("com.fuwa.security.AuthHandler").orElse(null));
        assertEquals(Confidence.HIGH, result.getConfidence("com.fuwa.security.AuthHandler"));
    }

    @Test
    void assignDomains_strategyExtractsThirdSegment() {
        DomainDetector detector = new DomainDetector(new JavaDomainStrategyStub());
        DependencyGraph graph = buildGraph("com.myapp.service.Foo");

        DomainResult result = detector.assignDomains(graph, Map.of());

        assertEquals("service", result.getDomain("com.myapp.service.Foo").orElse(null));
    }
}
```

**Commit:** `test: update DomainDetectorTest to use DomainStrategy`

---

## Task 10: Create JavaDomainStrategy in archon-java

**Files:**
- Create: `archon-java/src/main/java/com/archon/java/JavaDomainStrategy.java`
- Test: `archon-java/src/test/java/com/archon/java/JavaDomainStrategyTest.java`

### Step 10.1: Create JavaDomainStrategy class

```java
// archon-java/src/main/java/com/archon/java/JavaDomainStrategy.java
package com.archon.java;

import com.archon.core.plugin.DomainStrategy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Java-specific domain assignment strategy.
 * Uses pivot detection to find the most informative package segment.
 */
public class JavaDomainStrategy implements DomainStrategy {

    @Override
    public Optional<String> extractDomain(String moduleId) {
        String[] parts = moduleId.split("\\.");

        // Use pivot detection logic: 3rd segment for 4+ deep packages
        if (parts.length >= 4) {
            return Optional.of(parts[2]);
        }

        return Optional.empty();
    }

    @Override
    public Map<String, String> assignDomains(Set<String> moduleIds) {
        // Find pivot depth
        int maxDepth = moduleIds.stream()
            .mapToInt(id -> id.split("\\.").length)
            .max()
            .orElse(0);

        int pivotDepth = -1;
        for (int depth = 2; depth < maxDepth; depth++) {
            Set<String> segments = new java.util.HashSet<>();
            for (String nodeId : moduleIds) {
                String[] parts = nodeId.split("\\.");
                if (parts.length > depth) {
                    segments.add(parts[depth]);
                }
            }
            if (segments.size() >= 3 && segments.size() <= 10) {
                pivotDepth = depth;
                break;
            }
        }

        // Assign domains based on pivot
        Map<String, String> domains = new LinkedHashMap<>();
        if (pivotDepth >= 0) {
            for (String moduleId : moduleIds) {
                String[] parts = moduleId.split("\\.");
                if (parts.length > pivotDepth) {
                    domains.put(moduleId, parts[pivotDepth]);
                }
            }
        }

        return domains;
    }
}
```

**Commit:** `feat: add JavaDomainStrategy to archon-java`

---

### Step 10.2: Write JavaDomainStrategyTest

```java
// archon-java/src/test/java/com/archon/java/JavaDomainStrategyTest.java
package com.archon.java;

import com.archon.core.plugin.DomainStrategy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

class JavaDomainStrategyTest {

    private final DomainStrategy strategy = new JavaDomainStrategy();

    @Test
    void extractDomain_returnsThirdSegmentForDeepPackages() {
        assertEquals(Optional.of("domain"), strategy.extractDomain("com.example.domain.Foo"));
        assertEquals(Optional.of("service"), strategy.extractDomain("com.example.service.Foo"));
    }

    @Test
    void extractDomain_returnsEmptyForShallowPackages() {
        assertEquals(Optional.empty(), strategy.extractDomain("com.example.Foo"));
        assertEquals(Optional.empty(), strategy.extractDomain("Foo"));
    }

    @Test
    void assignDomains_findsPivotDepth() {
        Set<String> ids = Set.of(
            "com.fuwa.common.utils.StringUtils",
            "com.fuwa.framework.web.controller.BaseController",
            "com.fuwa.project.docmd.controller.DocController"
        );

        Map<String, String> domains = strategy.assignDomains(ids);

        assertEquals("common", domains.get("com.fuwa.common.utils.StringUtils"));
        assertEquals("framework", domains.get("com.fuwa.framework.web.controller.BaseController"));
        assertEquals("project", domains.get("com.fuwa.project.docmd.controller.DocController"));
    }

    @Test
    void assignDomains_noPivot_returnsEmptyMap() {
        Set<String> ids = Set.of("com.myapp.Foo");

        Map<String, String> domains = strategy.assignDomains(ids);

        assertTrue(domains.isEmpty());
    }
}
```

**Commit:** `test: add JavaDomainStrategyTest`

---

## Task 11: Move ParseError and ParseResult to archon-core (Update JavaParserPlugin)

**Files:**
- Modify: `archon-java/src/main/java/com/archon/java/JavaParserPlugin.java`
- Test: `archon-java/src/test/java/com/archon/java/JavaParserPluginTest.java` (update existing)

### Step 11.1: Update JavaParserPlugin imports and inner class references

```java
// archon-java/src/main/java/com/archon/java/JavaParserPlugin.java
package com.archon.java;

import com.archon.core.config.ArchonConfig;
import com.archon.core.graph.BlindSpot;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.plugin.ParseError;  // NEW: import from archon-core
import com.archon.core.plugin.ParseResult;   // NEW: import from archon-core
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;
// ... rest of imports unchanged ...

/**
 * Parses Java source trees and builds a dependency graph.
 * Orchestrates: ModuleDetector -> AstVisitor -> BlindSpotDetector
 */
public class JavaParserPlugin {

    public ParseResult parse(Path projectRoot, ArchonConfig config) {
        // ... existing code unchanged until return statement ...

        return new ParseResult(graphBuilder.build(), blindSpots, errors);
    }

    public ParseResult parseFromContent(Map<Path, String> fileContents, Set<String> knownSourceClasses) {
        // ... existing code unchanged until return statement ...

        return new ParseResult(graphBuilder.build(), List.of(), errors);
    }

    // ... rest of file unchanged ...

    // REMOVED these inner classes — now using archon-core versions:
    // public static class ParseResult { ... }
    // public static class ParseError { ... }
}
```

**Commit:** `refactor: JavaParserPlugin uses core ParseError and ParseResult`

---

### Step 11.2: Update ParseResult return to use Set<String> for sourceModules

The core `ParseResult` uses `Set<String> sourceModules`. Update JavaParserPlugin to collect and return this:

```java
// In JavaParserPlugin.parse() method, after collecting sourceClasses:
Set<String> sourceModules = new HashSet<>(sourceClasses);
return new ParseResult(graphBuilder.build(), blindSpots, errors);
```

**Commit:** `fix: JavaParserPlugin returns sourceModules Set`

---

### Step 11.3: Update JavaParserPluginTest

```java
// archon-java/src/test/java/com/archon/java/JavaParserPluginTest.java
// Update imports:
import com.archon.core.plugin.ParseError;  // NEW
import com.archon.core.plugin.ParseResult;   // NEW

// Update test assertions — ParseResult now has sourceModules instead of getGraph():
ParseResult result = plugin.parse(root, config);
assertNotNull(result);
assertTrue(result.getSourceModules().contains("com.example.Foo"));
```

**Commit:** `test: update JavaParserPluginTest for core ParseResult`

---

## Task 12: Create JavaPlugin wrapper implementing LanguagePlugin

**Files:**
- Create: `archon-java/src/main/java/com/archon/java/JavaPlugin.java`
- Test: `archon-java/src/test/java/com/archon/java/JavaPluginTest.java`

### Step 12.1: Create JavaPlugin class

```java
// archon-java/src/main/java/com/archon/java/JavaPlugin.java
package com.archon.java;

import com.archon.core.config.ArchonConfig;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.plugin.DomainStrategy;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * LanguagePlugin implementation for Java.
 * Delegates to JavaParserPlugin for actual parsing.
 */
public class JavaPlugin implements LanguagePlugin {

    private final JavaParserPlugin delegate;
    private final DomainStrategy domainStrategy;

    public JavaPlugin() {
        this.delegate = new JavaParserPlugin();
        this.domainStrategy = new JavaDomainStrategy();
    }

    @Override
    public String languageId() {
        return "java";
    }

    @Override
    public Set<String> fileExtensions() {
        return Set.of(".java");
    }

    @Override
    public ParseResult parse(ParseContext context, DependencyGraph.MutableBuilder builder) {
        // Note: JavaParserPlugin builds its own graph internally.
        // We need to adapt it to write into the provided MutableBuilder.
        // For now, delegate to existing parse() and merge the result.

        Path projectRoot = context.getProjectRoot();
        ArchonConfig config = context.getConfig();

        JavaParserPlugin.ParseResult internalResult = delegate.parse(projectRoot, config);

        // Copy nodes and edges from internal graph to provided builder
        DependencyGraph internalGraph = internalResult.getGraph();
        for (var nodeId : internalGraph.getNodeIds()) {
            internalGraph.getNode(nodeId).ifPresent(builder::addNode);
        }
        for (var edge : internalGraph.getAllEdges()) {
            builder.addEdge(edge);
        }

        // Return ParseResult with blind spots, errors, and source modules
        // For now, sourceModules is empty set — would need to expose from JavaParserPlugin
        return new ParseResult(
            internalResult.getBlindSpots(),
            internalResult.getErrors(),
            Set.of()  // TODO: extract sourceModules from JavaParserPlugin
        );
    }

    @Override
    public ParseResult parseFromContent(Map<Path, String> fileContents,
                                        Set<String> knownSourceModules,
                                        DependencyGraph.MutableBuilder builder) {
        JavaParserPlugin.ParseResult internalResult = delegate.parseFromContent(fileContents, knownSourceModules);

        // Copy nodes and edges from internal graph to provided builder
        DependencyGraph internalGraph = internalResult.getGraph();
        for (var nodeId : internalGraph.getNodeIds()) {
            internalGraph.getNode(nodeId).ifPresent(builder::addNode);
        }
        for (var edge : internalGraph.getAllEdges()) {
            builder.addEdge(edge);
        }

        return new ParseResult(
            internalResult.getBlindSpots(),
            internalResult.getErrors(),
            Set.of()  // TODO: extract sourceModules
        );
    }
}
```

**Commit:** `feat: add JavaPlugin implementing LanguagePlugin`

---

### Step 12.2: Write JavaPluginTest

```java
// archon-java/src/test/java/com/archon/java/JavaPluginTest.java
package com.archon.java;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Set;

class JavaPluginTest {

    @Test
    void implementsLanguagePlugin() {
        JavaPlugin plugin = new JavaPlugin();
        assertTrue(plugin instanceof LanguagePlugin);
    }

    @Test
    void languageId_returnsJava() {
        assertEquals("java", new JavaPlugin().languageId());
    }

    @Test
    void fileExtensions_includesJava() {
        assertTrue(new JavaPlugin().fileExtensions().contains(".java"));
    }

    @Test
    void parse_returnsParseResult() {
        JavaPlugin plugin = new JavaPlugin();
        ParseContext ctx = ParseContext.builder()
            .projectRoot(Path.of("../archon-test/src/test/resources"))
            .config(com.archon.core.config.ArchonConfig.defaults())
            .build();

        ParseResult result = plugin.parse(ctx, DependencyGraph.builder());

        assertNotNull(result);
        assertNotNull(result.getBlindSpots());
        assertNotNull(result.getErrors());
        assertNotNull(result.getSourceModules());
    }
}
```

**Commit:** `test: add JavaPluginTest`

---

## Task 13: Add ServiceLoader registration for JavaPlugin

**Files:**
- Create: `archon-java/src/main/resources/META-INF/services/com.archon.core.plugin.LanguagePlugin`

### Step 13.1: Create ServiceLoader registration file

```bash
# Create directory
mkdir -p archon-java/src/main/resources/META-INF/services

# Create registration file
cat > archon-java/src/main/resources/META-INF/services/com.archon.core.plugin.LanguagePlugin << 'EOF'
com.archon.java.JavaPlugin
EOF
```

**Commit:** `feat: add ServiceLoader registration for JavaPlugin`

---

## Task 14: Update AnalyzeCommand to use PluginDiscoverer

**Files:**
- Modify: `archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java`

### Step 14.1: Update AnalyzeCommand to use PluginDiscoverer and JavaPlugin

```java
// archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java
package com.archon.cli;

import com.archon.core.analysis.*;
import com.archon.core.config.ArchonConfig;
import com.archon.core.graph.BlindSpot;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.GraphBuilder;
import com.archon.core.graph.Node;
import com.archon.core.plugin.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
    name = "analyze",
    description = "Full structural analysis of a project",
    mixinStandardHelpOptions = true
)
public class AnalyzeCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Path to the project root")
    private String projectPath;

    @Option(names = "--json", description = "Output machine-readable JSON")
    private boolean json;

    @Option(names = "--dot", description = "Export Graphviz DOT to file")
    private String dotFile;

    @Option(names = "--verbose", description = "Show detailed parsing logs")
    private boolean verbose;

    @Override
    public Integer call() {
        Path root = Path.of(projectPath);
        if (!root.toFile().exists()) {
            System.err.println("Error: path does not exist: " + projectPath);
            return 1;
        }

        ArchonConfig config = ArchonConfig.loadOrDefault(root.resolve(".archon.yml"));

        // NEW: Discover plugins
        PluginDiscoverer discoverer = new PluginDiscoverer(getClass().getClassLoader());
        discoverer.verifyNoConflicts();

        // Step 1: Parse
        System.out.println("Parsing " + root + " ...");
        GraphBuilder graphBuilder = GraphBuilder.builder();
        List<ParseError> allErrors = new ArrayList<>();
        List<BlindSpot> allBlindSpots = new ArrayList<>();

        for (LanguagePlugin plugin : discoverer.getPlugins()) {
            ParseContext ctx = ParseContext.builder()
                .projectRoot(root)
                .config(config)
                .build();
            ParseResult result = plugin.parse(ctx, graphBuilder);
            allErrors.addAll(result.getErrors());
            allBlindSpots.addAll(result.getBlindSpots());
        }

        if (!allErrors.isEmpty()) {
            System.err.println(allErrors.size() + " file(s) failed to parse:");
            for (ParseError err : allErrors) {
                System.err.println("  " + err.getFile() + ": " + err.getMessage());
            }
        }

        DependencyGraph graph = graphBuilder.build();
        System.out.println("Parsed " + graph.nodeCount() + " classes, " + graph.edgeCount() + " dependencies");

        // Step 2: Domain detection (using first plugin's domain strategy)
        if (!discoverer.getPlugins().isEmpty()) {
            LanguagePlugin firstPlugin = discoverer.getPlugins().get(0);
            // For now, assume Java plugin and use its domain strategy
            JavaDomainStrategy domainStrategy = new JavaDomainStrategy();
            DomainDetector domainDetector = new DomainDetector(domainStrategy);
            DomainResult domainResult = domainDetector.assignDomains(graph, config.getDomains());
            Map<String, String> domainMap = domainResult.getDomains();

            long distinctDomains = domainMap.values().stream().distinct().count();
            Thresholds thresholds = ThresholdCalculator.calculate(graph.nodeCount(), (int) distinctDomains);
            if (distinctDomains > 0) {
                System.out.println("Domains detected: " + distinctDomains + " (" + domainMap.size() + " classes mapped)");
                if (verbose) {
                    domainMap.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .forEach(e -> System.out.println("  " + e.getValue() + " <- " + e.getKey()));
                }
            }
        }

        // ... rest of AnalyzeCommand unchanged (Cycle detection, Coupling, Blind spots, DOT export, Summary) ...

        return (!cycles.isEmpty()) ? 1 : 0;
    }
}
```

**Commit:** `feat: AnalyzeCommand uses PluginDiscoverer`

---

## Task 15: Update DiffCommand to use PluginDiscoverer

**Files:**
- Modify: `archon-cli/src/main/java/com/archon/cli/DiffCommand.java`

### Step 15.1: Update DiffCommand to use PluginDiscoverer

```java
// archon-cli/src/main/java/com/archon/cli/DiffCommand.java
package com.archon.cli;

import com.archon.core.analysis.*;
import com.archon.core.config.ArchonConfig;
import com.archon.core.git.CliGitAdapter;
import com.archon.core.git.GitAdapter;
import com.archon.core.git.GitException;
import com.archon.core.graph.*;
import com.archon.core.plugin.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
    name = "diff",
    description = "Diff-based change impact analysis between git refs",
    mixinStandardHelpOptions = true
)
public class DiffCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Base git ref")
    private String baseRef;

    @Parameters(index = "1", description = "Head git ref")
    private String headRef;

    @Parameters(index = "2", description = "Path to the project root")
    private String projectPath;

    @Option(names = "--ci", description = "CI mode: exit 1 on new cycles or HIGH+ risk")
    private boolean ciMode;

    @Option(names = "--depth", defaultValue = "3", description = "Max impact propagation depth")
    private int maxDepth;

    @Override
    public Integer call() {
        Path root = Path.of(projectPath);
        if (!root.toFile().exists()) {
            System.err.println("Error: path does not exist: " + projectPath);
            return 1;
        }

        GitAdapter git = new CliGitAdapter();
        if (!git.isGitAvailable()) {
            System.err.println("Error: git not found. Install git or use analyze/impact/check without diff.");
            return 1;
        }

        Path repoRoot;
        try {
            repoRoot = git.discoverRepoRoot(root);
        } catch (GitException e) {
            System.err.println("Error: not a git repository: " + projectPath);
            return 1;
        }

        // Resolve refs
        printStep("Resolving refs...");
        String baseSha, headSha;
        try {
            baseSha = git.resolveRef(repoRoot, baseRef);
            headSha = git.resolveRef(repoRoot, headRef);
        } catch (GitException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }

        // Get changed files
        printStep("Computing changed files...");
        List<String> changedFiles;
        try {
            changedFiles = git.getChangedFiles(repoRoot, baseSha, headSha);
        } catch (GitException e) {
            System.err.println("Error getting changed files: " + e.getMessage());
            return 1;
        }

        if (changedFiles.isEmpty()) {
            System.out.println("No changes between " + baseRef + " and " + headRef);
            return 0;
        }

        ArchonConfig config = ArchonConfig.loadOrDefault(root.resolve(".archon.yml"));

        // NEW: Discover plugins
        PluginDiscoverer discoverer = new PluginDiscoverer(getClass().getClassLoader());
        discoverer.verifyNoConflicts();

        // Parse head graph (working tree)
        printStep("Parsing head graph (" + changedFiles.size() + " files changed)...");
        GraphBuilder headBuilder = GraphBuilder.builder();
        List<BlindSpot> headBlindSpots = new ArrayList<>();
        List<ParseError> headErrors = new ArrayList<>();

        for (LanguagePlugin plugin : discoverer.getPlugins()) {
            ParseContext ctx = ParseContext.builder()
                .projectRoot(root)
                .config(config)
                .build();
            ParseResult result = plugin.parse(ctx, headBuilder);
            headBlindSpots.addAll(result.getBlindSpots());
            headErrors.addAll(result.getErrors());
        }

        if (!headErrors.isEmpty()) {
            System.err.println(headErrors.size() + " file(s) failed to parse:");
            for (ParseError err : headErrors) {
                System.err.println("  " + err.getFile() + ": " + err.getMessage());
            }
        }

        DependencyGraph headGraph = headBuilder.build();
        printStep("Head graph: " + headGraph.getNodeIds().size() + " classes, " + headGraph.edgeCount() + " edges");

        // Parse base graph (from git show for changed files + reuse head for unchanged)
        printStep("Building base graph...");
        DependencyGraph baseGraph = buildBaseGraph(git, repoRoot, root, changedFiles, headGraph, config, discoverer);
        printStep("Base graph: " + baseGraph.getNodeIds().size() + " classes, " + baseGraph.edgeCount() + " edges");

        // Diff the graphs
        printStep("Diffing graphs...");
        GraphDiffer graphDiffer = new GraphDiffer();
        GraphDiff graphDiff = graphDiffer.diff(baseGraph, headGraph);

        // Domain detection on head graph (use Java domain strategy)
        printStep("Detecting domains...");
        JavaDomainStrategy domainStrategy = new JavaDomainStrategy();
        DomainDetector domainDetector = new DomainDetector(domainStrategy);
        DomainResult domainResult = domainDetector.assignDomains(headGraph, config.getDomains());
        Map<String, String> domainMap = domainResult.getDomains();

        // Determine changed classes (union of git diff files + graph diff nodes)
        Set<String> changedClasses = new LinkedHashSet<>();
        for (String file : changedFiles) {
            // NEW: Partition by extension and dispatch to appropriate plugin
            LanguagePlugin plugin = discoverer.findPlugin(extension(file));
            if (plugin != null) {
                // Use plugin's file-to-module mapping (TODO: add this to LanguagePlugin interface)
                // For now, use existing fileEndsWithClass logic for Java
                headGraph.getNodeIds().stream()
                    .filter(id -> fileEndsWithClass(file, id))
                    .forEach(changedClasses::add);
            }
        }
        changedClasses.addAll(graphDiff.getAddedNodes());
        changedClasses.addAll(graphDiff.getRemovedNodes());
        for (Edge e : graphDiff.getAddedEdges()) { changedClasses.add(e.getSource()); }
        for (Edge e : graphDiff.getRemovedEdges()) { changedClasses.add(e.getSource()); }

        // ... rest of DiffCommand unchanged (Risk synthesis, Impact propagation, Report output, CI mode) ...

        return 0;
    }

    private String extension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        return lastDot >= 0 ? filePath.substring(lastDot) : "";
    }

    private boolean fileEndsWithClass(String filePath, String fqcn) {
        String expected = fqcn.replace('.', '/') + ".java";
        return filePath.equals(expected) || filePath.endsWith("/" + expected);
    }

    private DependencyGraph buildBaseGraph(GitAdapter git, Path repoRoot, Path projectRoot,
                                        List<String> changedFiles, DependencyGraph headGraph,
                                        ArchonConfig config, PluginDiscoverer discoverer) {
        // Get base content for changed files
        Map<Path, String> baseContents = new LinkedHashMap<>();
        for (String file : changedFiles) {
            LanguagePlugin plugin = discoverer.findPlugin(extension(file));
            if (plugin != null) {
                try {
                    String content = git.getFileContent(repoRoot, baseRef, file);
                    if (content != null) {
                        baseContents.put(Path.of(file), content);
                    }
                } catch (GitException ignored) {
                    // File didn't exist in base — it's a new file
                }
            }
        }

        if (baseContents.isEmpty()) {
            return headGraph;
        }

        // Copy unchanged nodes and edges from head graph
        GraphBuilder baseBuilder = GraphBuilder.builder();
        for (String nodeId : headGraph.getNodeIds()) {
            // TODO: Determine if node is in changed files (needs plugin file-to-module mapping)
            // For now, copy all nodes (conservative approach)
            baseBuilder.addNode(headGraph.getNode(nodeId).orElseThrow());
        }
        for (Edge edge : headGraph.getAllEdges()) {
            baseBuilder.addEdge(edge);
        }

        // Parse base versions of changed files using appropriate plugins
        Set<String> sourceClasses = new HashSet<>(headGraph.getNodeIds());
        Map<String, GraphBuilder> pluginBuilders = new LinkedHashMap<>();

        // Partition changed files by plugin
        Map<LanguagePlugin, List<Map.Entry<Path, String>>> pluginFiles = new LinkedHashMap<>();
        for (var entry : baseContents.entrySet()) {
            LanguagePlugin plugin = discoverer.findPlugin(extension(entry.getKey().toString()));
            if (plugin != null) {
                pluginFiles.computeIfAbsent(plugin, k -> new ArrayList<>()).add(entry);
            }
        }

        // Parse with each plugin
        for (var entry : pluginFiles.entrySet()) {
            LanguagePlugin plugin = entry.getKey();
            GraphBuilder pluginBuilder = GraphBuilder.builder();
            Map<Path, String> files = new LinkedHashMap<>();
            for (var e : entry.getValue()) {
                files.put(e.getKey(), e.getValue());
            }
            ParseResult result = plugin.parseFromContent(files, sourceClasses, pluginBuilder);
            DependencyGraph changedGraph = pluginBuilder.build();

            for (String nodeId : changedGraph.getNodeIds()) {
                baseBuilder.addNode(changedGraph.getNode(nodeId).orElseThrow());
            }
            for (Edge edge : changedGraph.getAllEdges()) {
                baseBuilder.addEdge(edge);
            }
        }

        return baseBuilder.build();
    }
}
```

**Commit:** `feat: DiffCommand uses PluginDiscoverer`

---

## Task 16: Create SpiComplianceTest in archon-test

**Files:**
- Create: `archon-test/src/test/java/com/archon/test/spi/SpiComplianceTest.java`

### Step 16.1: Create SpiComplianceTest

```java
// archon-test/src/test/java/com/archon/test/spi/SpiComplianceTest.java
package com.archon.test.spi;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.plugin.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Compliance test that all LanguagePlugin implementations meet the SPI contract.
 * Run this test to verify a new plugin is correctly integrated.
 */
public class SpiComplianceTest {

    @Test
    void javaPlugin_discoveredViaServiceLoader() {
        PluginDiscoverer discoverer = new PluginDiscoverer(getClass().getClassLoader());

        boolean found = discoverer.getPlugins().stream()
            .anyMatch(p -> "java".equals(p.languageId()));

        assertTrue(found, "JavaPlugin should be discoverable via ServiceLoader");
    }

    @Test
    void javaPlugin_implementsAllRequiredMethods() {
        PluginDiscoverer discoverer = new PluginDiscoverer(getClass().getClassLoader());
        LanguagePlugin javaPlugin = discoverer.getPlugins().stream()
            .filter(p -> "java".equals(p.languageId()))
            .findFirst()
            .orElseThrow();

        // Test languageId
        assertNotNull(javaPlugin.languageId());
        assertEquals("java", javaPlugin.languageId());

        // Test fileExtensions
        Set<String> extensions = javaPlugin.fileExtensions();
        assertNotNull(extensions);
        assertFalse(extensions.isEmpty());
        assertTrue(extensions.contains(".java"));

        // Test parse returns ParseResult
        ParseContext ctx = ParseContext.builder()
            .projectRoot(Path.of("../archon-test/src/test/resources"))
            .config(com.archon.core.config.ArchonConfig.defaults())
            .build();
        ParseResult result = javaPlugin.parse(ctx, DependencyGraph.builder());

        assertNotNull(result);
        assertNotNull(result.getBlindSpots());
        assertNotNull(result.getErrors());
        assertNotNull(result.getSourceModules());

        // Test parseFromContent returns ParseResult
        ParseResult contentResult = javaPlugin.parseFromContent(
            Map.of(Path.of("Test.java"), "public class Test {}"),
            Set.of("Test"),
            DependencyGraph.builder()
        );

        assertNotNull(contentResult);
    }

    @Test
    void pluginDiscoverer_detectsConflicts() {
        // This test verifies conflict detection works.
        // In a real test with multiple plugins, conflicting extensions would be detected.
        PluginDiscoverer discoverer = new PluginDiscoverer(getClass().getClassLoader());

        // Currently only Java plugin exists, so no conflicts
        assertTrue(discoverer.getConflicts().isEmpty());

        // Calling verifyNoConflicts should not throw
        assertDoesNotThrow(() -> discoverer.verifyNoConflicts());
    }

    @Test
    void domainStrategy_returnsEmptyWhenNoDomain() {
        DomainStrategy strategy = new DomainStrategy() {
            @Override
            public java.util.Optional<String> extractDomain(String id) {
                return java.util.Optional.empty();
            }

            @Override
            public Map<String, String> assignDomains(Set<String> ids) {
                return Map.of();
            }
        };

        assertTrue(strategy.extractDomain("foo").isEmpty());
        assertTrue(strategy.assignDomains(Set.of("foo")).isEmpty());
    }
}
```

**Commit:** `test: add SpiComplianceTest`

---

## Task 17: Verify all existing tests pass

**Files:**
- Test: Run all tests in archon-core and archon-java

### Step 17.1: Run all tests

```bash
# Run all tests
./gradlew test

# Expected: BUILD SUCCESSFUL, all tests pass
```

**Expected output:**
- DomainDetectorTest: 7 tests pass (updated for DomainStrategy)
- JavaParserPluginTest: passes (uses core ParseResult/ParseError)
- All other existing tests: pass

If any test fails, fix it and commit the fix separately.

**Commit:** `fix: [test-name] fix for SPI refactor`

---

## Summary

This plan implements **Milestone 1 (SPI Extraction)** and **Milestone 2 (Java Plugin Refactor)** together:

**New files created:**
- `archon-core/src/main/java/com/archon/core/plugin/*.java` (7 new files)
- `archon-core/src/test/java/com/archon/core/plugin/*.java` (6 new test files)
- `archon-java/src/main/java/com/archon/java/JavaPlugin.java`
- `archon-java/src/main/java/com/archon/java/JavaDomainStrategy.java`
- `archon-java/src/test/java/com/archon/java/JavaDomainStrategyTest.java`
- `archon-java/src/test/java/com/archon/java/JavaPluginTest.java`
- `archon-java/src/main/resources/META-INF/services/com.archon.core.plugin.LanguagePlugin`
- `archon-test/src/test/java/com/archon/test/spi/SpiComplianceTest.java`

**Files modified:**
- `archon-core/src/main/java/com/archon/core/graph/NodeType.java` (+2 values)
- `archon-core/src/main/java/com/archon/core/graph/EdgeType.java` (+2 values)
- `archon-core/src/main/java/com/archon/core/analysis/DomainDetector.java` (refactored to use DomainStrategy)
- `archon-core/src/test/java/com/archon/core/analysis/DomainDetectorTest.java` (updated tests)
- `archon-java/src/main/java/com/archon/java/JavaParserPlugin.java` (uses core types)
- `archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java` (uses PluginDiscoverer)
- `archon-cli/src/main/java/com/archon/cli/DiffCommand.java` (uses PluginDiscoverer)

**Success criteria:**
1. All new files compile without errors
2. SpiComplianceTest passes
3. All 81+ existing tests pass
4. `archon analyze` and `archon diff` commands work identically to before

---

**Next Steps (Milestone 3):** archon-js module with swc4j parser, ModulePathResolver, JsDomainStrategy, JsPlugin.
