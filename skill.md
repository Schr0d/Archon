# Archon Claude Code Skill

Archon ships a native Claude Code skill at `~/.claude/skills/archon/` that wraps the CLI for impact analysis directly inside your AI development workflow.

## Installation

The skill auto-installs when you run `/archon` in Claude Code. It needs JDK 17 (auto-detected).

## Commands

### `/archon diff` — Impact analysis of uncommitted changes

Shows the blast radius of your current working tree changes. Run this before refactoring to understand what breaks.

**What it does:**
1. Runs `git diff --name-only HEAD` to find changed files
2. Runs `archon view . --format json --with-metadata --with-full-analysis` for the full dependency graph
3. Matches changed files to graph nodes (Java FQCN, JS module paths, Python dotted imports)
4. Computes impact tiers: P0 (direct), P1 (transitive dependents), P2 (same domain)
5. Outputs a markdown report with risk level, hotspot involvement, and blind spots

### `/archon analyze [path]` — Full dependency analysis

Runs a full analysis and presents a structured report with domain map, hotspots, cross-domain warnings, and cycles.

### `/archon setup` — One-time JDK detection

Detects JDK 17 on your system and verifies the shadow JAR works. Run once per machine.

### `/archon upgrade` — Update skill files from GitHub

Downloads the latest skill files from the repo. The repo is the source of truth — local skill files pull updates via reverse inheritance.

## How It Works

The skill uses three components:

- **`SKILL.md`** — Skill definition with `/archon diff`, `/archon analyze`, `/archon setup` commands
- **`bin/archon-detect`** — Cross-platform JDK 17 detection (Windows/macOS/Linux). Caches result at `~/.archon/config.json`
- **`bin/archon-run`** — JAR invocation wrapper. Auto-finds or builds the shadow JAR. Handles JDK selection.
- **`bin/archon-update-check`** — Periodic version check. Detects JAR version mismatch, GitHub remote updates, and JDK availability changes. All state in `~/.archon/` with zero external dependencies.

## Key Technical Notes

- **Use `view --format json`, not `analyze --json`.** The analyze --json flag is not yet implemented (see TODOS.md #6). View with --format json returns the same data.
- **JSON output contains:** `nodes[]` (with id, domain, metadata.metrics), `edges[]` (source, target), `domains`, `cycles`, `hotspots`
- **Node matching:** Java uses FQCN (`com.example.Foo`), JS uses module paths, Python uses dotted imports
- **Blind spots:** Always included in reports. Archon detects static dependencies only. Reflection, dynamic imports, Spring DI, and event-driven coupling are NOT detected.

## Example: `/archon diff` Output

```markdown
## Archon Impact Report

**Scope:** 2 files changed across 1 domain
**Risk:** Medium — cross-domain dependency affected

### Direct Impact (P0)
- `com.archon.core.graph.DependencyGraph` — changed directly

### Transitive Impact (P1)
- `com.archon.viz.JsonSerializer` (depends on DependencyGraph)
- `com.archon.java.JavaPlugin` (depends on DependencyGraph)

### Cross-Domain Warnings
- `viz → core`: ViewCommand depends on AnalysisPipeline

### Hotspot Involvement
- `com.archon.core.graph.DependencyGraph` — PageRank: 0.082, bridge node

### Blind Spots
- Reflection-based calls (Class.forName, method.invoke)
- Dynamic imports (import(), importlib)
- Spring bean injection (@Autowired)
```

## File Location

```
~/.claude/skills/archon/
  SKILL.md                  — Skill definition
  bin/
    archon-detect           — JDK 17 auto-detection
    archon-run              — JAR invocation wrapper
    archon-update-check     — Periodic version check
```

## See Also

- [README.md](README.md) — Full project documentation
- [CHANGELOG.md](CHANGELOG.md) — Version history
- [TODOS.md](TODOS.md) — Deferred work and contribution opportunities
