# JSON-First Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable clean JSON output for AI agent integration by wiring the existing JsonSerializer to the CLI and adding error/confidence visibility.

**Architecture:**
- Add `errors` field to JsonSerializer for parse error reporting
- Wire `--json` flag in AnalyzeCommand to call JsonSerializer
- Move progress messages from stdout to stderr
- Add `--quiet` flag to suppress progress messages
- Add confidence summary to terminal output
- Add `--summary` flag for condensed human-readable output

**Tech Stack:** Java 17, picocli (CLI), Jackson (JSON), JUnit 5 (testing)

---

## File Structure

**New files:**
- `archon-cli/src/test/java/com/archon/cli/AnalyzeCommandTest.java` - CLI tests for new flags

**Modified files:**
- `archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java` - Add --json/--quiet/--summary wiring, move progress to stderr, add confidence summary
- `archon-core/src/main/java/com/archon/core/output/JsonSerializer.java` - Add errors field to full serialization method
- `archon-core/src/test/java/com/archon/core/output/JsonSerializerTest.java` - Test errors field
- `skill.md` - Document JSON schema and agent usage

---

## Task 1: Add Errors Field to JsonSerializer

**Files:**
- Modify: `archon-core/src/main/java/com/archon/core/output/JsonSerializer.java:63-102`
- Test: `archon-core/src/test/java/com/archon/core/output/JsonSerializerTest.java`

- [ ] **Step 1: Write failing test for errors field**

Create a new test method in `JsonSerializerTest.java`:

```java
@Test
public void testToJsonWithErrors() {
    // Given: a graph with parse errors
    DependencyGraph graph = createTestGraph();
    Map<String, String> domains = Map.of("com.example.Foo", "CORE");
    List<List<String>> cycles = List.of();
    List<Node> hotspots = List.of();
    List<BlindSpot> blindSpots = List.of();
    List<String> errors = List.of("Failed to parse com/example/Bar.java:42", "Unexpected token in com/example/Baz.java");

    // When: serializing with errors
    JsonSerializer serializer = new JsonSerializer();
    String json = serializer.toJson(graph, domains, cycles, hotspots, blindSpots, errors);

    // Then: errors field should be present
    ObjectMapper mapper = new ObjectMapper();
    var root = mapper.readTree(json);
    assertTrue(root.has("errors"));
    assertEquals(2, root.get("errors").size());
    assertEquals("Failed to parse com/example/Bar.java:42", root.get("errors").get(0).asText());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew archon-core:test --tests JsonSerializerTest.testToJsonWithErrors`
Expected: FAIL with "errors field not found" or method signature mismatch

- [ ] **Step 3: Add errors parameter to toJson method**

Add a new method overload in `JsonSerializer.java` after the existing `toJson` method:

```java
public String toJson(DependencyGraph graph, Map<String, String> domains,
                     List<List<String>> cycles, List<Node> hotspots,
                     List<BlindSpot> blindSpots, List<String> errors) {
    try {
        ObjectNode root = mapper.createObjectNode();
        root.put("graph", mapper.readTree(toJson(graph)));

        // Domains
        ObjectNode domainsObj = root.putObject("domains");
        for (Map.Entry<String, Set<String>> entry : groupByDomain(domains).entrySet()) {
            domainsObj.put(entry.getKey(), mapper.valueToTree(entry.getValue()));
        }

        // Cycles
        ArrayNode cyclesArray = root.putArray("cycles");
        for (List<String> cycle : cycles) {
            cyclesArray.add(mapper.valueToTree(cycle));
        }

        // Hotspots
        ArrayNode hotspotsArray = root.putArray("hotspots");
        for (Node hotspot : hotspots) {
            hotspotsArray.add(hotspot.getId());
        }

        // Blind spots
        ArrayNode blindSpotsArray = root.putArray("blindSpots");
        for (BlindSpot spot : blindSpots) {
            ObjectNode spotObj = mapper.createObjectNode();
            spotObj.put("type", spot.getType());
            spotObj.put("location", spot.getLocation());
            spotObj.put("description", spot.getDescription());
            blindSpotsArray.add(spotObj);
        }

        // Errors (NEW)
        ArrayNode errorsArray = root.putArray("errors");
        for (String error : errors) {
            errorsArray.add(error);
        }

        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    } catch (Exception e) {
        throw new RuntimeException("Failed to serialize analysis result to JSON", e);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew archon-core:test --tests JsonSerializerTest.testToJsonWithErrors`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add archon-core/src/main/java/com/archon/core/output/JsonSerializer.java
git add archon-core/src/test/java/com/archon/core/output/JsonSerializerTest.java
git commit -m "feat(json): add errors field to JsonSerializer for parse error reporting

Enables AI agents to detect and report parse failures in JSON output."
```

---

## Task 2: Add --quiet Flag to AnalyzeCommand

**Files:**
- Modify: `archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java:43-54`

- [ ] **Step 1: Add quiet field to AnalyzeCommand**

Add the field after the `verbose` field (around line 54):

```java
@Option(names = "--quiet", description = "Suppress progress messages (errors still shown)")
private boolean quiet;
```

- [ ] **Step 2: Run existing tests to verify no regression**

Run: `./gradlew archon-cli:test`
Expected: All existing tests PASS

- [ ] **Step 3: Commit**

```bash
git add archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java
git commit -m "feat(cli): add --quiet flag to suppress progress messages

Useful for AI agent integration where only results are needed."
```

---

## Task 3: Move Progress Messages to stderr

**Files:**
- Modify: `archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java:82-106`

- [ ] **Step 1: Write test for stderr separation**

Create a new test file `archon-cli/src/test/java/com/archon/cli/AnalyzeCommandTest.java`:

```java
package com.archon.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyzeCommandTest {

    @Test
    public void testProgressGoesToStderr(@TempDir Path tempDir) throws Exception {
        // Given: a small Java project
        Path testProject = TestProjectCreator.createSmallProject(tempDir);

        AnalyzeCommand command = new CommandLine(AnalyzeCommand.class)
            .getCommand();
        command.projectPath = testProject.toString();

        // Capture stdout and stderr separately
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout));
        System.setErr(new PrintStream(stderr));

        // When: running analyze
        Integer exitCode = command.call();

        // Then: progress should be in stderr, not stdout
        String stderrOutput = stderr.toString();
        String stdoutOutput = stdout.toString();

        assertTrue(stderrOutput.contains("Parsing"),
            "Progress messages should go to stderr");
        assertFalse(stdoutOutput.contains("Parsing"),
            "Progress messages should NOT go to stdout");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew archon-cli:test --tests AnalyzeCommandTest`
Expected: FAIL - progress currently goes to stdout

- [ ] **Step 3: Change progress output to use stderr**

In `AnalyzeCommand.java`, change line 92:

```java
// OLD: System.out.println("Parsing " + root + " (" + sourceFiles.size() + " files) ...");
System.err.println("Parsing " + root + " (" + sourceFiles.size() + " files) ...");
```

Change line 105:

```java
// OLD: System.out.println("Parsed " + graph.nodeCount() + " classes, " + graph.edgeCount() + " dependencies");
System.err.println("Parsed " + graph.nodeCount() + " classes, " + graph.edgeCount() + " dependencies");
```

Change line 115:

```java
// OLD: System.out.println("Domains detected: " + distinctDomains + " (" + domainMap.size() + " classes mapped)");
System.err.println("Domains detected: " + distinctDomains + " (" + domainMap.size() + " classes mapped)");
```

- [ ] **Step 4: Respect --quiet flag**

Wrap all progress messages in quiet check:

```java
if (!quiet) {
    System.err.println("Parsing " + root + " (" + sourceFiles.size() + " files) ...");
}
```

Do the same for other progress messages at lines 105 and 115.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew archon-cli:test --tests AnalyzeCommandTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java
git add archon-cli/src/test/java/com/archon/cli/AnalyzeCommandTest.java
git commit -m "refactor(cli): move progress messages to stderr

Enables clean JSON output to stdout for AI agent parsing.
Progress messages now go to stderr, which agents typically ignore."
```

---

## Task 4: Wire --json Flag in AnalyzeCommand

**Files:**
- Modify: `archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java:195-200`

- [ ] **Step 1: Write test for --json output**

Add to `AnalyzeCommandTest.java`:

```java
@Test
public void testJsonOutputContainsAllFields(@TempDir Path tempDir) throws Exception {
    // Given: a small Java project
    Path testProject = TestProjectCreator.createSmallProject(tempDir);

    // When: running with --json
    AnalyzeCommand command = new CommandLine(AnalyzeCommand.class)
        .getCommand();
    command.projectPath = testProject.toString();
    command.json = true;

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(stdout));

    command.call();

    // Then: output should be valid JSON with all required fields
    String jsonOutput = stdout.toString();

    ObjectMapper mapper = new ObjectMapper();
    var root = mapper.readTree(jsonOutput);

    assertTrue(root.has("graph"), "JSON should contain 'graph' field");
    assertTrue(root.has("domains"), "JSON should contain 'domains' field");
    assertTrue(root.has("cycles"), "JSON should contain 'cycles' field");
    assertTrue(root.has("hotspots"), "JSON should contain 'hotspots' field");
    assertTrue(root.has("blindSpots"), "JSON should contain 'blindSpots' field");
    assertTrue(root.has("errors"), "JSON should contain 'errors' field");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew archon-cli:test --tests AnalyzeCommandTest.testJsonOutputContainsAllFields`
Expected: FAIL - --json flag does nothing currently

- [ ] **Step 3: Import JsonSerializer**

Add to imports at top of `AnalyzeCommand.java`:

```java
import com.archon.core.output.JsonSerializer;
```

- [ ] **Step 4: Add JSON output logic before summary**

Insert this code at line 195 (before the "// Summary" section):

```java
// Step 7: JSON output (before terminal output)
if (json) {
    JsonSerializer serializer = new JsonSerializer();
    String output = serializer.toJson(graph, domainMap, cycles, hotspots, blindSpots, result.getParseErrors());
    System.out.println(output);
    return !cycles.isEmpty() ? 1 : 0;
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew archon-cli:test --tests AnalyzeCommandTest.testJsonOutputContainsAllFields`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java
git add archon-cli/src/test/java/com/archon/cli/AnalyzeCommandTest.java
git commit -m "feat(cli): implement --json flag using existing JsonSerializer

Clean JSON output to stdout for AI agent integration.
All progress messages go to stderr."
```

---

## Task 5: Add Confidence Summary to Terminal Output

**Files:**
- Modify: `archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java:107-122`

- [ ] **Step 1: Write test for confidence summary**

Add to `AnalyzeCommandTest.java`:

```java
@Test
public void testConfidenceSummaryInOutput(@TempDir Path tempDir) throws Exception {
    // Given: a project with domains
    Path testProject = TestProjectCreator.createProjectWithDomains(tempDir);

    AnalyzeCommand command = new CommandLine(AnalyzeCommand.class)
        .getCommand();
    command.projectPath = testProject.toString();
    command.quiet = true;

    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    System.setErr(new PrintStream(stderr));

    command.call();

    // Then: output should contain confidence summary
    String output = stderr.toString();
    assertTrue(output.contains("Confidence:"),
        "Output should contain confidence summary");
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew archon-cli:test --tests AnalyzeCommandTest.testConfidenceSummaryInOutput`
Expected: FAIL - no confidence summary currently

- [ ] **Step 3: Add confidence summary after domains detected**

After line 115 in `AnalyzeCommand.java`, add:

```java
// Confidence summary
Map<com.archon.core.graph.Confidence, Long> confidenceCounts = domainResult.getConfidence().values().stream()
    .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
if (!quiet && distinctDomains > 0) {
    System.err.print("Confidence: ");
    List<String> confParts = new ArrayList<>();
    for (com.archon.core.graph.Confidence conf : com.archon.core.graph.Confidence.values()) {
        long count = confidenceCounts.getOrDefault(conf, 0L);
        if (count > 0) {
            confParts.add(conf.name() + " (" + count + ")");
        }
    }
    System.err.println(String.join(", ", confParts));
    if (confidenceCounts.getOrDefault(com.archon.core.graph.Confidence.LOW, 0L) > 0) {
        System.err.println("Run with --verbose to see LOW confidence assignments");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew archon-cli:test --tests AnalyzeCommandTest.testConfidenceSummaryInOutput`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java
git add archon-cli/src/test/java/com/archon/cli/AnalyzeCommandTest.java
git commit -m "feat(cli): add confidence summary to terminal output

Surfaces existing confidence levels from DomainDetector.
Users can now see how many domain assignments are uncertain."
```

---

## Task 6: Add --summary Flag

**Files:**
- Modify: `archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java:43-54`

- [ ] **Step 1: Add summary and summary-limit fields**

Add after the `quiet` field:

```java
@Option(names = "--summary", description = "Show condensed summary for human review")
private boolean summary;

@Option(names = "--summary-limit", defaultValue = "20", description = "Max items in summary (default: 20)")
private int summaryLimit;
```

- [ ] **Step 2: Write test for summary output**

Add to `AnalyzeCommandTest.java`:

```java
@Test
public void testSummaryOutputIsCondensed(@TempDir Path tempDir) throws Exception {
    // Given: a project with many nodes
    Path testProject = TestProjectCreator.createLargeProject(tempDir, 50);

    AnalyzeCommand command = new CommandLine(AnalyzeCommand.class)
        .getCommand();
    command.projectPath = testProject.toString();
    command.summary = true;
    command.summaryLimit = 10;

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(stdout));

    command.call();

    // Then: output should be condensed
    String output = stdout.toString();
    assertTrue(output.contains("==="), "Summary should have header");
    assertTrue(output.contains("Top"), "Summary should show top items");
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew archon-cli:test --tests AnalyzeCommandTest.testSummaryOutputIsCondensed`
Expected: FAIL - --summary not implemented

- [ ] **Step 4: Add summary output method**

Add a new method at the end of `AnalyzeCommand.java`:

```java
private void printSummary(DependencyGraph graph, Map<String, String> domainMap,
                         List<List<String>> cycles, List<Node> hotspots,
                         int limit) {
    System.out.println("=== SUMMARY ===");

    // Domains summary
    Map<String, Long> domainCounts = domainMap.values().stream()
        .collect(Collectors.groupingBy(d -> d, Collectors.counting()));
    System.out.println("\nDomains: " + domainCounts.size());
    int shown = 0;
    for (var entry : domainCounts.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .toList()) {
        if (shown >= 10) {
            System.out.println("└── " + (domainCounts.size() - shown) + " more...");
            break;
        }
        System.out.println("├── " + entry.getKey() + ": " + entry.getValue() + " classes");
        shown++;
    }

    // Hotspots summary
    System.out.println("\nTop " + Math.min(limit, hotspots.size()) + " Hotspots (by in-degree):");
    shown = 0;
    for (Node hotspot : hotspots.stream()
            .sorted((a, b) -> Integer.compare(b.getInDegree(), a.getInDegree()))
            .toList()) {
        if (shown >= limit) break;
        System.out.println("├── #" + (shown + 1) + ": " + hotspot.getId() + " (in: " + hotspot.getInDegree() + ")");
        shown++;
    }
    if (hotspots.size() > limit) {
        System.out.println("└── " + (hotspots.size() - limit) + " more...");
    }

    // Cycles summary
    System.out.println("\nCycles: " + cycles.size() + " total");
    int cyclesShown = Math.min(10, cycles.size());
    for (int i = 0; i < cyclesShown; i++) {
        List<String> cycle = cycles.get(i);
        System.out.println("└── " + String.join(" -> ", cycle));
    }
    if (cycles.size() > 10) {
        System.out.println("└── (showing first " + cyclesShown + " of " + cycles.size() + ")");
    }

    System.out.println("\nUse --verbose for full output");
}
```

- [ ] **Step 5: Call summary output**

Add at line 195 (before JSON output check):

```java
if (summary) {
    printSummary(graph, domainMap, cycles, hotspots, summaryLimit);
    return !cycles.isEmpty() ? 1 : 0;
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew archon-cli:test --tests AnalyzeCommandTest.testSummaryOutputIsCondensed`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java
git add archon-cli/src/test/java/com/archon/cli/AnalyzeCommandTest.java
git commit -m "feat(cli): add --summary flag for condensed human output

Large projects produce too much output for human review.
Summary mode shows top N items with configurable limit."
```

---

## Task 7: Update skill.md Documentation

**Files:**
- Modify: `skill.md`

- [ ] **Step 1: Add JSON schema section**

Add after "Expected Output Format" section:

```markdown
## JSON Schema (analyze --json)

```json
{
  "graph": {
    "nodes": [
      {
        "id": "com.example.Foo",
        "type": "CLASS",
        "domain": "CORE",
        "sourcePath": "com/example/Foo.java",
        "confidence": "HIGH"
      }
    ],
    "edges": [
      {
        "source": "com.example.Foo",
        "target": "com.example.Bar",
        "type": "IMPORTS",
        "confidence": "HIGH",
        "dynamic": false
      }
    ],
    "stats": {
      "nodeCount": 80,
      "edgeCount": 124
    }
  },
  "domains": {
    "CORE": ["com.example.core.Foo", "com.example.core.Bar"],
    "UTILS": ["com.example.utils.Helper"]
  },
  "cycles": [
    ["com.example.A", "com.example.B", "com.example.A"]
  ],
  "hotspots": [
    "com.example.core.DependencyGraph",
    "com.example.core.Parser"
  ],
  "blindSpots": [
    {
      "type": "DYNAMIC_IMPORT",
      "location": "com/example/Loader.js",
      "description": "require() with variable cannot be statically analyzed"
    }
  ],
  "errors": [
    "Failed to parse com/example/Broken.java:42"
  ]
}
```

**Field descriptions:**
- `nodes[].confidence` - HIGH/MEDIUM/LOW based on detection certainty
- `edges[].dynamic` - true if edge is from dynamic code (reflection, etc.)
- `errors` - Parse errors that occurred during analysis
```

- [ ] **Step 2: Update agent integration pattern**

Update the "AI Agent Integration Pattern" section:

```markdown
## AI Agent Integration Pattern

### Recommended CLI Flags

For agents, use: `--json --quiet`

```bash
java -jar archon.jar analyze <path> --json --quiet
```

- `--json`: Clean JSON to stdout, no other output
- `--quiet`: Suppress progress messages (still shows errors to stderr)

### Error Handling

1. **Exit code 0**: Analysis successful, no cycles
2. **Exit code 1**: Cycles detected OR parse errors present
3. **Exit code 2**: Analysis error (e.g., path not found)

Always check the `errors` field in JSON output for parse failures.
```

- [ ] **Step 3: Update examples**

Update the example in "Plan Phase":

```python
# Agent: Get architectural context
result = subprocess.run(
    ["java", "-jar", "archon.jar", "analyze", path, "--json", "--quiet"],
    capture_output=True,
    text=True
)

if result.returncode == 2:
    return "Error: " + result.stderr.strip()

context = json.loads(result.stdout)

# Check for parse errors
if context["errors"]:
    return f"Warning: {len(context['errors'])} files failed to parse"
```

- [ ] **Step 4: Commit**

```bash
git add skill.md
git commit -m "docs: update skill.md with JSON schema and agent usage

Documents the --json --quiet pattern for AI integration.
Includes full JSON schema and error handling examples."
```

---

## Task 8: Integration Test

**Files:**
- Create: `archon-cli/src/test/java/com/archon/cli/AnalyzeCommandIntegrationTest.java`

- [ ] **Step 1: Write end-to-end integration test**

Create `AnalyzeCommandIntegrationTest.java`:

```java
package com.archon.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AnalyzeCommandIntegrationTest {

    @Test
    public void testFullJsonWorkflow(@TempDir Path tempDir) throws Exception {
        // Given: a realistic Java project
        Path testProject = TestProjectCreator.createRealisticProject(tempDir);

        // When: running analyze with --json --quiet
        AnalyzeCommand command = new CommandLine(AnalyzeCommand.class)
            .getCommand();
        command.projectPath = testProject.toString();
        command.json = true;
        command.quiet = true;

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout));
        System.setErr(new PrintStream(stderr));

        Integer exitCode = command.call();

        // Then: should have clean JSON output
        String jsonOutput = stdout.toString();
        String errorOutput = stderr.toString();

        // Verify no pollution
        assertFalse(jsonOutput.startsWith("Parsing"),
            "JSON should not start with progress messages");

        // Verify valid JSON
        com.fasterxml.jackson.databind.ObjectMapper mapper =
            new com.fasterxml.jackson.databind.ObjectMapper();
        var root = mapper.readTree(jsonOutput);

        // Verify all fields present
        assertTrue(root.has("graph"));
        assertTrue(root.has("domains"));
        assertTrue(root.has("cycles"));
        assertTrue(root.has("hotspots"));
        assertTrue(root.has("blindSpots"));
        assertTrue(root.has("errors"));

        System.out.println("Integration test PASSED");
    }
}
```

- [ ] **Step 2: Run integration test**

Run: `./gradlew archon-cli:test --tests AnalyzeCommandIntegrationTest`
Expected: PASS

- [ ] **Step 3: Manual verification**

Run manually to verify output format:

```bash
./gradlew archon-cli:shadowJar
java -jar archon-cli/build/libs/archon-*.jar analyze /path/to/test/project --json --quiet
```

Verify:
1. stdout contains only JSON
2. stderr is empty (or contains only actual errors)
3. JSON is valid (pipe to `jq .`)

- [ ] **Step 4: Commit**

```bash
git add archon-cli/src/test/java/com/archon/cli/AnalyzeCommandIntegrationTest.java
git commit -m "test(cli): add integration test for JSON output workflow

Verifies clean JSON output with no stderr pollution."
```

---

## Self-Review Checklist

After completing all tasks, verify:

- [ ] **Spec coverage**: All 4 phases from design spec are implemented
  - Phase 1: JSON output ✓ (Tasks 1, 2, 3, 4)
  - Phase 2: Confidence visibility ✓ (Task 5)
  - Phase 3: Summary mode ✓ (Task 6)
  - Phase 4: Documentation ✓ (Task 7)

- [ ] **No placeholders**: All steps have actual code
- [ ] **Type consistency**: Method signatures match across tasks
- [ ] **Tests pass**: All new and existing tests pass
- [ ] **Manual verified**: JSON output is clean and parseable
