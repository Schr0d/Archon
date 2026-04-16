---
name: archon
description: Multi-language dependency impact analysis for Java, JS/TS, and Python codebases. Answers "if I change this, what breaks?" Run /archon diff before refactoring, /archon analyze for full dependency maps.
---

# Archon — Dependency Impact Analysis

Archon is a deterministic multi-language dependency analyzer. It parses Java, JavaScript/TypeScript, and Python codebases, builds a dependency graph, and computes impact analysis.

## User-invocable

When the user types `/archon`, run this skill.

## Arguments

- `/archon diff` — Analyze uncommitted changes and show blast radius
- `/archon analyze [path]` — Full dependency analysis of a codebase
- `/archon setup` — One-time JDK detection and JAR build
- `/archon upgrade` — Update skill files from GitHub (reverse inheritance)

## How It Works

### Step 0: Setup and update check

Before any command, check for updates and verify Archon is available:

```bash
_UPD=$(~/.claude/skills/archon/bin/archon-update-check 2>/dev/null || true)
[ -n "$_UPD" ] && echo "$_UPD" || true
source ~/.claude/skills/archon/bin/archon-detect 2>/dev/null
echo "JAVA: ${ARCHON_JAVA_HOME:-NOT_FOUND}"
```

If output shows:
- **`JAR_MISMATCH <expected> <actual>`** — The cached JAR is stale. Clear it: `sed -i 's/, "jarPath":"[^"]*"//' ~/.archon/config.json`. The next run will find the correct JAR.
- **`UPGRADE_AVAILABLE <old> <new>`** — A newer version exists on GitHub. Run `/archon upgrade` to update skill files from the repo.
- **`JDK_MISSING`** — The previously cached JDK 17 path is no longer valid. Run `/archon setup` to re-detect.
- **`JUST_UPGRADED <old> <new>`** — Inform the user: "Archon updated to v<new> (from v<old>)."

If `ARCHON_JAVA_HOME` is NOT_FOUND, run the setup flow (see `/archon setup` below).

### `/archon diff` — Impact analysis of uncommitted changes

This command shows the blast radius of your current working tree changes.

**Step 1: Get changed files**

```bash
git diff --name-only HEAD 2>/dev/null
```

If not in a git repo or no changed files, say "No uncommitted changes detected. Use `/archon analyze` for a full report." and STOP.

**Step 2: Get the dependency graph**

```bash
~/.claude/skills/archon/bin/archon-run analyze . --format agent 2>/dev/null
```

Save the JSON output. This is a compressed format with indexed arrays. Parse it as follows:

**JSON structure:**
- `v` — format version (currently "1.0.0")
- `n` — node count, `e` — edge count, `cc` — connected components
- `domains` — ordered array of domain names (indexed 0..K)
- `nodes` — array of `[id, domainIdx, pageRank_x10000, risk, bridge, hotspot]`
  - `id` — fully qualified class/module name (string)
  - `domainIdx` — index into `domains[]` array
  - `pageRank_x10000` — PageRank score * 10000 (divide by 10000 for actual value)
  - `risk` — 0=low, 1=medium, 2=high
  - `bridge` — 1 if this node is on a bridge edge (removal disconnects graph)
  - `hotspot` — 1 if this is a high-impact change hotspot
- `edges` — array of `[srcIdx, tgtIdx]` — src depends on tgt (both are indices into `nodes[]`)
- `bridges` — edges whose removal disconnects the graph (indexed pairs)
- `bs` — blind spot counts by type (e.g., `{"UnresolvedModule": 302}`)
- `cycles` — detected dependency cycles (each is an array of node IDs)

**To compute blast radius:** Build a reverse index from node name → node array index. For each changed node, find its index. Scan `edges[]` for all `[src, tgt]` where `tgt == changedIdx` — those `src` nodes depend on the changed node. Repeat transitively.

**Tier auto-scaling:** Tier 1 (<200 nodes): full graph. Tier 2 (200-500): summary + hotspots, no edge list. Tier 3 (500+): summary with capped lists + hint to use `--target`. For Tier 2/3, use `--target <class>` to get a focused Tier 1 subgraph.

**Step 3: Match changed files to nodes**

For each changed file, map it to node IDs (the first element in each `nodes[]` array):
- Java: `src/main/java/com/example/Foo.java` → node `com.example.Foo`
- JS/TS: strip extension, match against node IDs
- Python: module path matching
- If a changed file doesn't match any node, note it as "not in dependency graph" (might be config, docs, etc.)

**Step 4: Compute impact**

For each changed node, trace through the edges array to find:
- **P0 (Direct):** The changed nodes themselves
- **P1 (Transitive):** Nodes that depend on P0 nodes (scan edges for `[src, tgt]` where `tgt` is a P0 node index, then recurse on the found `src` indices)
- **P2 (Domain):** Other nodes sharing the same `domainIdx` as any P0/P1 node

**Step 5: Format the impact report**

Output a markdown report in this format:

```markdown
## Archon Impact Report

**Scope:** N files changed across M domains
**Risk:** Low / Medium / High (based on cross-domain edges and hotspot involvement)

### Direct Impact (P0)
- `com.archon.core.graph.DependencyGraph` — changed directly
- `com.archon.cli.AnalyzeCommand` — changed directly

### Transitive Impact (P1)
- `com.archon.java.JavaPlugin` (depends on DependencyGraph)
- `com.archon.cli.DiffCommand` (depends on DependencyGraph)

### Hotspot Involvement
- `com.archon.core.graph.DependencyGraph` — PageRank: 0.082, bridge node

### Blind Spots
Archon detects static dependencies and Spring DI patterns (@Autowired, @Resource, constructor injection). These patterns are NOT detected:
- Reflection-based calls (Java: Class.forName, method.invoke)
- Dynamic imports (JS: import(), Python: importlib)
- Event-driven coupling (no static import)
```

**Step 6: Actionable advice**

Based on the impact analysis, add a section with:
- Safest refactoring path (which files can change with minimal blast radius)
- Risk areas (cross-domain dependencies that would be affected)
- Testing recommendations (which test suites should be run)

### `/archon analyze [path]` — Full dependency analysis

Runs a full analysis and presents a structured report.

**Step 1: Run analysis**

```bash
~/.claude/skills/archon/bin/archon-run analyze . --format agent 2>/dev/null
```

If `[path]` is provided, use it instead of `.`.

**Step 2: Parse and format**

Output a structured report:

```markdown
## Archon Dependency Analysis

**Project:** [path]
**Nodes:** N classes/modules
**Edges:** M dependencies
**Domains:** K detected
**Cycles:** None / N detected

### Domain Map
| Domain | Nodes | Edges | Health |
|--------|-------|-------|--------|
| core   | 12    | 34    | Good   |
| cli    | 8     | 15    | Good   |
| java   | 6     | 18    | Good   |

### Hotspots (top 5 by PageRank)
1. `com.archon.core.graph.DependencyGraph` — PR: 0.082, bridge, inDegree: 12
2. `com.archon.core.analysis.AnalysisPipeline` — PR: 0.064, inDegree: 8

### Cross-Domain Dependencies
- `cli → core`: 8 edges
- `java → core`: 3 edges
- `js → core`: 2 edges

### Cycles
(if any cycles detected, list them)

### Recommendations
- [Specific actionable recommendations based on the graph structure]
```

### `/archon setup` — One-time setup

Detects JDK 17 and verifies the JAR works.

**Step 1: Detect JDK**

```bash
source ~/.claude/skills/archon/bin/archon-detect
```

**Step 2: Find or build JAR**

```bash
~/.claude/skills/archon/bin/archon-run --version
```

If this fails, try building:

```bash
export JAVA_HOME="$ARCHON_JAVA_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :archon-cli:shadowJar --quiet
```

**Step 3: Verify**

```bash
~/.claude/skills/archon/bin/archon-run analyze . --quiet 2>&1 | head -5
```

Report success or failure.

### `/archon upgrade` — Reverse inheritance from GitHub

Updates the skill files at `~/.claude/skills/archon/` from the latest release on GitHub. This is the reverse-inheritance mechanism: the repo is the source of truth, and the local skill installation pulls updates.

**Step 1: Check current version**

```bash
~/.claude/skills/archon/bin/archon-update-check --force 2>/dev/null || true
```

If output shows `UPGRADE_AVAILABLE <old> <new>`, proceed with upgrade.

**Step 2: Download updated skill files from GitHub**

The skill files live at `https://github.com/Schr0d/Archon/tree/main/.claude/skills/archon/`. Download each file:

```bash
SKILL_DIR=~/.claude/skills/archon
REPO_BASE="https://raw.githubusercontent.com/Schr0d/Archon/main/.claude/skills/archon"

# Download core files
curl -sf -o "$SKILL_DIR/SKILL.md" "$REPO_BASE/SKILL.md" 2>/dev/null
curl -sf -o "$SKILL_DIR/bin/archon-detect" "$REPO_BASE/bin/archon-detect" 2>/dev/null
curl -sf -o "$SKILL_DIR/bin/archon-run" "$REPO_BASE/bin/archon-run" 2>/dev/null
curl -sf -o "$SKILL_DIR/bin/archon-update-check" "$REPO_BASE/bin/archon-update-check" 2>/dev/null
chmod +x "$SKILL_DIR/bin/"*
```

**Step 3: Clear stale caches**

```bash
# State directory (self-contained, no external dependencies)
STATE_DIR="$HOME/.archon"

# Clear stale JAR cache (forces re-discovery)
sed -i 's/, "jarPath":"[^"]*"//' "$STATE_DIR/config.json" 2>/dev/null || true
# Clear update cache
rm -f "$STATE_DIR/update-cache" 2>/dev/null || true
# Write "just upgraded" marker
OLD_VER="<old version from Step 1>"
NEW_VER="<new version from Step 1>"
mkdir -p "$STATE_DIR"
echo "$OLD_VER" > "$STATE_DIR/just-upgraded"
```

**Step 4: Verify**

```bash
~/.claude/skills/archon/bin/archon-update-check --force 2>/dev/null
```

Should output nothing or `JUST_UPGRADED <old> <new>`. Tell the user: "Archon skill updated to v<new>. Run `/archon setup` if you also need a new JAR."

## Important Rules

- **Always use `~/.claude/skills/archon/bin/archon-run` as the CLI wrapper.** It handles JDK detection and JAR finding.
- **Use `analyze --format agent` for JSON output.** Produces structured JSON with node metadata for AI consumption.
- **The JSON output has nodes and edges.** Use these to compute impact, not the text output.
- **Match files to nodes carefully.** Java uses FQCN (com.example.Foo), JS uses module paths, Python uses dotted imports.
- **Blind spots matter.** Always include the blind spots section so the user knows what Archon cannot detect.
- **Be concrete.** Reference actual node IDs, actual edge counts, actual domain names from the graph data.
- **Keep it fast.** After setup, subsequent runs should complete in under 10 seconds. If analysis takes longer, suggest analyzing a subdirectory.
