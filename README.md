# Archon

> **Structure-Constrained AI Refactoring Pipeline**

[English](README.md) | [中文文档](README-zh.md)

Archon is a system that integrates architectural analysis directly into AI-driven code modification workflows.

It turns refactoring from an ad-hoc, model-driven process into a **structured, verifiable, and feedback-controlled pipeline**.

---

## Problem

Modern AI coding tools are powerful, but unstable in large-scale systems:

- They modify code without understanding system boundaries
- Refactoring decisions are opaque and non-deterministic
- Reviews happen too late, often after structural damage is done
- Architecture knowledge is not explicitly used as a constraint

**Result:** AI-assisted refactoring becomes fast but unsafe.

---

## Solution

Archon introduces a structured pipeline that tightly couples structural analysis with code modification:

### 1. Pre-Analysis (Plan Stage)

Before any code change, perform structural analysis of the repository:

- Module boundaries
- Dependency graph
- Risk hotspots
- Impact surfaces of target changes

This becomes **explicit input context for the AI**.

### 2. Constrained Execution (Act Stage)

The AI operates under structural constraints:

- Scoped context windows
- Explicit change intent (diff-oriented execution)
- Architectural boundary awareness

This turns generation into **bounded transformation**.

### 3. Pre-Merge Verification (Review Stage)

Before merging, perform a second-pass evaluation:

- Diff-based structural impact analysis
- Cross-module dependency validation
- Consistency checks against pre-analysis snapshot

This acts as an **automated architecture-aware review layer**.

---

## Core Idea

> Move architecture from documentation → runtime constraint system

Archon is not just a code analyzer. It is a **closed-loop control system for AI-driven code evolution**.

---

## Quick Start

### Installation

Download the latest shadow JAR from [releases](https://github.com/Schr0d/Archon/releases):

```bash
# Or build from source
./gradlew shadowJar
```

### Basic Usage

```bash
# Interactive web visualization (opens browser)
java -jar archon.jar view /path/to/project

# Analyze with terminal output
java -jar archon.jar analyze /path/to/project

# Export static HTML diagram
java -jar archon.jar view /path/to/project --export diagram.html

# Diff with web viewer (red=removed, green=added, yellow=changed)
java -jar archon.jar diff main HEAD /path/to/project --view

# Check impact of changing a specific module
java -jar archon.jar impact com.example.Service /path/to/project

# Validate against architectural rules
java -jar archon.jar check /path/to/project --ci
```

---

## AI Agent Workflow

Archon is designed to be called by AI agents during the development loop. Here's how it integrates:

### Stage 1: Plan — AI Gets Architectural Context

Archon provides three output tiers for AI integration:

#### Tier 1: Default Output (Lightweight)
```bash
# Basic JSON output with graph structure
$ java -jar archon.jar view . --format json
```

#### Tier 2: Full Analysis (AI-Enhanced Metadata)
```bash
# Include metadata field with impact scores, risk levels, and issue flags
$ java -jar archon.jar view . --format json --with-metadata

# Include full centrality metrics (PageRank, betweenness, closeness, etc.)
$ java -jar archon.jar view . --format json --with-metadata --with-full-analysis
```

#### Tier 3: On-Demand Query
```bash
# Query specific metrics for specific nodes (coming soon)
$ java -jar archon.jar query --centrality com.example.MyClass
```

**JSON Schema (Tier 2 with --with-full-analysis):**

```json
{
  "$schema": "archon-metadata-v1",
  "version": "1.0.0",
  "nodes": [
    {
      "id": "com.archon.core.graph.DependencyGraph",
      "domain": "core",
      "inDegree": 18,
      "outDegree": 3,
      "metadata": {
        "metrics": {
          "fanIn": 18,
          "fanOut": 3,
          "pageRank": 0.087,
          "betweenness": 0.034,
          "closeness": 0.125,
          "impactScore": 0.087,
          "riskLevel": "medium"
        },
        "issues": {
          "hotspot": true,
          "cycle": false,
          "blindSpots": [],
          "bridge": false
        }
      }
    }
  ],
  "fullAnalysis": {
    "connectedComponents": 1,
    "bridges": []
  }
}
```

**AI uses this to:**
- Avoid high-risk hotspots
- Respect domain boundaries
- Stay within safe change surfaces
- Declare uncertainty for blind spots
- **NEW:** Use centrality metrics to identify critical nodes (high PageRank = high impact)
- **NEW:** Understand bridge nodes (removal increases fragmentation)

---

### Stage 2: Execute — AI Makes Constrained Changes

```python
# AI agent internal state
archon_context = load("archon-context.json")

# Agent plans refactoring with constraints
def plan_refactoring(target):
    if target in archon_context["hotspots"]:
        return f"SKIP: {target} is HIGH-RISK hotspot (inDegree: 18)"

    impact = archon.impact(target)
    if impact.cross_domain > 3:
        return f"SKIP: {target} affects {impact.cross_domain} domains"

    return generate_safe_refactoring_plan(target, archon_context)
```

**AI is constrained by:**
- Pre-calculated impact surface
- Domain boundary rules
- Risk hotspot avoidance
- Explicit scope limits

---

### Stage 3: Review — AI Verifies Structural Integrity

```bash
# Agent validates changes before committing
$ java -jar archon.jar diff main HEAD . --ci
```

The review gate returns:

```
=== Structural Impact Review ===

Added edges: 2
  com.auth.service → com.payment.client [CROSS-DOMAIN] ⚠️
  com.payment.dao → com.database.pool [SAME-DOMAIN]

Removed edges: 1
  com.auth.util → com.logging.helper

Violations: 1
  ✗ max_cross_domain exceeded (current: 4, limit: 3)
    → com.auth.service → com.payment.client

Gate: BLOCKED
```

**AI responds:**
- Rollback cross-domain violation
- Re-plan within architectural constraints
- Re-verify with clean diff

---

## Full Workflow Diagram

```mermaid
sequenceDiagram
    participant AI as AI Agent
    participant Archon as Archon
    participant Repo as Codebase

    Note over AI,Archon: PLAN STAGE
    AI->>Archon: analyze . --json
    Archon-->>AI: {domains, hotspots, cycles}
    AI->>AI: Generate constrained plan

    Note over AI,Repo: EXECUTE STAGE
    AI->>Repo: Make scoped changes
    AI->>AI: Respect boundaries from context

    Note over AI,Archon: REVIEW STAGE
    AI->>Archon: diff main HEAD . --ci
    Archon-->>AI: {violations, gate_status}

    alt Gate: PASS
        AI->>Repo: Commit changes
    else Gate: BLOCKED
        AI->>AI: Fix violations
    end
```

---

## Key Properties

- **Constrained AI behavior** — not free-form generation
- **Architecture-aware context injection**
- **Plan → Execute → Verify loop**
- **Diff-level structural review**
- **Deterministic validation on top of probabilistic models**

---

## Multi-Language Support

| Language | Parser | Status |
|----------|--------|--------|
| Java | Reflection-based | Built-in |
| JavaScript/TypeScript | Closure Compiler | Built-in |
| Python | Import parser | Built-in |
| Vue | SFC script extraction | Built-in |

---

## Use Cases

- Large monorepo refactoring
- Service boundary cleanup
- Dependency cycle removal
- Gradual architecture migration
- AI-assisted code review augmentation

---

## CLI Commands

```
archon view <path> [--port] [--no-open] [--export <file>] [--idle-timeout <min>]
archon analyze <path> [--json] [--dot <file>] [--mermaid <file>] [--verbose]
archon impact <module> <path> [--depth N]
archon check <path> [--ci]
archon diff <base> <head> <path> [--ci] [--depth N] [--view]
```

---

## Configuration

Create `.archon.yml` in your project root:

```yaml
rules:
  no_cycle: true
  max_cross_domain: 3
  max_call_depth: 3
  forbid_core_entity_leakage: true

critical_paths:
  - com.example.auth
  - com.example.payment

domains:
  com.example.*:
    - ".*\\.service\\..*"
```

---

## Design Philosophy

- Structure is more important than code
- Constraints improve model reliability
- AI should operate inside a system, not replace it
- Refactoring is a controlled transformation process, not a creative act

---

## Architecture

```
archon-core/     — Language-agnostic graph model, analysis engines, SPI
archon-java/     — Java parser plugin
archon-js/       — JavaScript/TypeScript parser plugin
archon-python/   — Python import parser plugin
archon-viz/      — Web visualization and export formats
archon-cli/      — CLI with shadow JAR packaging
archon-test/     — Shared test fixtures
```

---

## Building

```bash
# Run all tests
./gradlew test

# Build shadow JAR
./gradlew shadowJar

# Output: archon-cli/build/libs/archon-<version>.jar
```

---

## Roadmap

- [x] v0.1 — CLI + basic analysis
- [x] v0.2 — Diff-based analysis
- [x] v0.3 — Multi-language SPI
- [x] v0.4 — Security hardening + Vue support
- [x] v0.5 — Visualization (web UI)
- [ ] v0.6 — Cross-language edge detection
- [ ] v1.0 — Full AI-refactoring pipeline integration

---

## Status

Experimental / early-stage system design

Not a coding assistant — a **code evolution control system**

---

## Contributing

See [TODOS.md](TODOS.md) for deferred work and contribution opportunities.

## License

MIT

## Acknowledgments

The web viewer adapts the approach of [oh-my-mermaid](https://github.com/oh-my-mermaid/oh-my-mermaid) (MIT licensed).

## Links

- [skill.md](skill.md) — AI agent integration guide
- [CHANGELOG.md](CHANGELOG.md) — Version history
- [TODOS.md](TODOS.md) — Deferred work
