# Archon

> **If I change this file, what breaks?**

[English](README.md) | [中文文档](README-zh.md)

Archon maps dependencies across your codebase and tells you the blast radius of any change. Before you touch a 1600-line Spring service with 50 `@Autowired` fields, you'll know exactly which 23 files depend on it.

One question, one tool. Deterministic, local, no cloud.

---

## What it does

```bash
# "What depends on this service?"
java -jar archon.jar analyze . --target com.example.UserService

Impact: 23 dependents across 5 domains
  com.auth.handler.TokenValidator → UserService  (cross-domain)
  com.order.processor.CheckoutFlow → UserService  (cross-domain)
  ...

# "I changed 3 files. What's the damage?"
java -jar archon.jar diff

Changed: 3 files
Blast radius: 14 files, 2 cross-domain edges, 0 cycles
  UserService → TokenValidator    [CROSS-DOMAIN]
  UserService → CheckoutFlow      [CROSS-DOMAIN]

# "Give me the full dependency map."
java -jar archon.jar analyze .

Analyzed: 127 nodes, 312 edges, 0 cycles, 5 domains
Hotspot:  com.example.UserService (inDegree: 18, risk: HIGH)
```

---

## Why this exists

I was refactoring a Spring service. 1600 lines, 50 `@Autowired` fields. I changed one private field. All tests passed. Deployed. Six services broke.

IDE dependency analysis doesn't catch Spring DI. Tests only cover happy paths. Code review can't trace 18 levels of transitive dependencies. The knowledge of "what depends on what" only exists at runtime, when it's too late.

Archon makes that knowledge visible before you change anything. It uses real parsers, not AI guessing: ArchUnit for Java bytecode (catches `@Autowired`, `@Resource`, constructor injection), dependency-cruiser for JS/TS, import parsing for Python.

---

## Quick Start

### Prerequisites

- **Java 17** (OpenJDK 17+, e.g. [Eclipse Adoptium Temurin](https://adoptium.net/))

### Install

Download the latest JAR from [releases](https://github.com/Schr0d/Archon/releases), or build from source:

```bash
./gradlew shadowJar
# Output: archon-cli/build/libs/archon-1.0.0.jar
```

### Run

```bash
# Full dependency analysis
java -jar archon.jar analyze /path/to/project

# Impact analysis — what breaks if you change a module?
java -jar archon.jar analyze /path/to/project --target com.example.Service

# Machine-readable JSON for AI tools
java -jar archon.jar analyze . --format agent

# Blast radius of uncommitted changes
java -jar archon.jar diff

# Diff between two branches
java -jar archon.jar diff main feature-branch
```

---

## For AI Agents

Archon is designed to be called by AI coding tools during the plan-execute-review loop.

**Using Claude Code?** Type `/archon diff` or `/archon analyze` for instant impact analysis. See [skill.md](skill.md).

### Plan: AI gets architectural context

```bash
java -jar archon.jar analyze . --format agent
```

Returns structured JSON with: dependency graph, node metrics (PageRank, betweenness, impact score), domain groupings, cycles, hotspots, and blind spots. The AI uses this to avoid high-risk areas and respect domain boundaries.

### Execute: AI makes constrained changes

The AI agent uses the dependency context to scope its changes, avoiding hotspots and cross-domain violations.

### Review: AI verifies before committing

```bash
java -jar archon.jar diff main HEAD
```

```
Added edges: 2
  com.auth.service → com.payment.client [CROSS-DOMAIN]
  com.payment.dao → com.database.pool   [SAME-DOMAIN]

Violations: 1
  max_cross_domain exceeded (current: 4, limit: 3)
Gate: BLOCKED
```

The AI rolls back violations and re-plans within constraints.

---

## Supported Languages

| Language | Parser | Catches |
|----------|--------|---------|
| Java | ArchUnit bytecode | Direct imports, Spring DI (`@Autowired`, `@Resource`, constructor injection) |
| JavaScript / TypeScript | dependency-cruiser | ES6 imports, CommonJS, Vue SFC, path aliases |
| Python | Import parser | `import`, `from...import`, relative imports |

Plugins use an SPI interface. New languages can be added without modifying the core.

---

## Architecture

```
archon-core/     Language-agnostic graph model, analysis engines, SPI
archon-java/     Java parser plugin (with Spring DI post-processor)
archon-js/       JavaScript/TypeScript parser plugin
archon-python/   Python import parser plugin
archon-cli/      CLI with shadow JAR packaging
```

Each plugin returns structured declarations. The core builds the graph, runs analysis (centrality, domains, cycles, hotspots), and outputs results in human or machine format.

---

## Building

```bash
./gradlew test        # 428 tests
./gradlew shadowJar   # Fat JAR at archon-cli/build/libs/archon-1.0.1.0.jar
```

---

## Roadmap

- [x] v0.1 — CLI + basic analysis
- [x] v0.2 — Diff-based analysis
- [x] v0.3 — Multi-language SPI
- [x] v0.4 — Security hardening + Vue support
- [x] v0.5 — Visualization (web UI)
- [x] v0.6 — Cross-language edge detection
- [x] v0.7 — JS/TS rewrite + Spring DI detection + command consolidation
- [x] v1.0 — Stable release: multi-language analysis, AI agent integration, compressed agent format

---

## Contributing

See [TODOS.md](TODOS.md) for deferred work and contribution opportunities.

## License

MIT

## Acknowledgments

Uses [ArchUnit](https://archunit.org/) for Java bytecode analysis (Apache 2.0).

## Links

- [skill.md](skill.md) — AI agent integration guide
- [CHANGELOG.md](CHANGELOG.md) — Version history
- [TODOS.md](TODOS.md) — Deferred work
