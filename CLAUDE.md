# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project: Archon

**Domain Analyzer & Dependency Guard** — an engineering decision tool for understanding system structure, evaluating change impact, and controlling architectural complexity. Positioned as a "structure observability + assessment + control" layer.

## Current Status

**v0.1 — CLI + basic analysis. Working on real projects.**

Successfully tested against a 1043-class RuoYi monolith (geditor-api). All three CLI commands functional:
- `analyze` — structural analysis (cycles, hotspots, domains, blind spots)
- `impact <class> <path>` — BFS impact propagation with risk levels
- `check` — rule validation with CI mode

Known issues being addressed in v0.1.1:
- External classes (JDK, Spring) pollute hotspots and inflate warnings
- Fixed thresholds don't scale across project sizes
- Domain detection over-segments (71 domains for a 5-module project)
- Output is too verbose for large projects

Design doc for v0.1.1 fixes: `~/.gstack/projects/Archon/ThinkPad-master-design-20260402-133035.md`

### Product Specs
- `PRD_v0.4.md` — Systems Engineering Edition (core capabilities, risk model, roadmap)
- `PRD_extend.md` — Trust & ECP Edition v0.5 (confidence scoring, blind spots, LLM boundaries)

## Design Principles (from PRD)

These are non-negotiable architectural constraints:

1. **Deterministic First**: Dependency graphs and call graphs are objective facts. Core structure analysis must be static analysis only — LLM must never generate or modify dependency graphs, calculate impact, or change rules.
2. **Declare Uncertainty**: The tool must be able to say "I don't know." Dynamic calls (reflection, EventBus, dynamic proxy, agent prompt flows) must be flagged as Blind Spots with confidence ratings. Never mislead by pretending omniscience.
3. **Human-in-the-loop**: The tool provides structure and assessment; humans make final decisions.
4. **Taste → Constraint**: Every engineering judgment must eventually crystallize into enforceable rules.

## Architecture

### Gradle Multi-Module Structure
```
archon-core/     — Language-agnostic graph model, analysis engines, config
archon-java/     — JavaParser-based Java parser plugin
archon-cli/      — picocli CLI with shadow JAR packaging
archon-test/     — Shared test fixtures
```

### Core Engines (implemented)
- **DependencyGraph** — MutableBuilder pattern, frozen after build. Nodes + directed edges.
- **CycleDetector** — DFS-based cycle detection on directed graph
- **CouplingAnalyzer** — In-degree hotspot detection
- **DomainDetector** — Package-based domain assignment (3rd segment heuristic)
- **ImpactPropagator** — BFS propagation with depth limit, risk-weighted, cross-domain tracking
- **RiskScorer** — Threshold-based scoring (coupling, cross-domain, depth, critical path)
- **RuleValidator** — Validates: no_cycle, max_cross_domain, max_call_depth, forbid_core_entity_leakage
- **BlindSpotDetector** — Heuristic detection of reflection, dynamic proxy, event-driven patterns
- **ArchonConfig** — YAML config loader with defaults (`.archon.yml`)

### Java Parser Plugin
- **JavaParserPlugin** — Orchestrator: ModuleDetector → file walk → AstVisitor + BlindSpotDetector
- **AstVisitor** — Walks JavaParser AST, extracts class nodes + import/extends/implements edges
- **ImportResolver** — Resolves imports to FQCN using source tree
- **ModuleDetector** — Detects Maven/Gradle source roots
- **SymbolSolverAdapter** — JavaParser symbol solving with fallback (currently unused in main flow)

### CLI Commands
```
archon analyze <path> [--json] [--dot <file>] [--verbose]
archon impact <class> <path> [--depth N]
archon check <path> [--ci]
```

Build: `./gradlew shadowJar` → `archon-cli/build/libs/archon-0.1.0.jar`

### Risk Model (Quantitative)
| Dimension | Threshold |
|-----------|-----------|
| Coupling (in-degree) | >10 → HIGH, 5-10 → MEDIUM |
| Cross-domain count | >=3 → HIGH |
| Call depth | >=3 → HIGH |
| Critical path | auth/payment → HIGH |
| Cycle | YES → VERY HIGH (BLOCK) |
| Low confidence | +1 risk level |

Output levels: LOW / MEDIUM / HIGH / VERY HIGH / BLOCKED

### LLM Integration Boundaries
- **Allowed**: ECP text generation, risk explanation, refactoring suggestions
- **Forbidden**: Generating dependency graphs, calculating impact, modifying rules
- Principle: LLM = advisor, not judge

### Roadmap
- ~~v0.1: CLI + basic analysis~~ (DONE)
- v0.1.1: External class filtering, adaptive thresholds, smarter domains, output polish (IN PROGRESS)
- v0.2: Diff-based analysis, config profiles, impact weight calibration
- v0.3: Visualization (web UI)
- v0.4: ECP + decision support + LLM integration

## Testing

Run all tests: `./gradlew test`
Run single module: `./gradlew :archon-java:test`
Test count: ~81 tests across all modules

## Language

PRD documents are written in Chinese. The product itself targets bilingual output (Chinese + English). Follow the language convention established in surrounding code when implementing.
