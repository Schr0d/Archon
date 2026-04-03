# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project: Archon

**Domain Analyzer & Dependency Guard** — an engineering decision tool for understanding system structure, evaluating change impact, and controlling architectural complexity. Positioned as a "structure observability + assessment + control" layer.

## Current Status

**v0.4.0.0 — Multi-language support + security hardening. Production-ready.**

Multi-language architecture via ServiceLoader-based SPI:
- **Java plugin** — JavaParser-based, handles .java files with FQCN node IDs
- **JavaScript/TypeScript plugin** — Closure Compiler-based, handles .js/.jsx/.ts/.tsx files with POSIX path node IDs
- **Vue support** — Single File Component (.vue) with `<script>` extraction

Four CLI commands functional:
- `analyze` — structural analysis (cycles, hotspots, domains, blind spots)
- `impact <class> <path>` — BFS impact propagation with risk levels
- `check <path>` — rule validation with CI mode
- `diff <base> <head> <path>` — git-aware change impact analysis

Validated on real projects:
- Java: 1043-class RuoYi monolith (geditor-api)
- JavaScript/TypeScript: geditor-ui-react (375 modules), geditor-ui-vue (489 files)

Security hardening (v0.4.0.0):
- Namespace collision detection with exception throwing
- File size validation before parsing (1MB limit)
- Plugin state cleanup interface
- Malformed namespace prefix validation

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
archon-core/     — Language-agnostic graph model, analysis engines, config, SPI
archon-java/     — JavaParser-based Java parser plugin
archon-js/       — Closure Compiler-based JavaScript/TypeScript parser plugin
archon-cli/      — picocli CLI with shadow JAR packaging
archon-test/     — Shared test fixtures
```

### Core Engines (implemented)
- **DependencyGraph** — MutableBuilder pattern, frozen after build. Nodes + directed edges. Namespace prefix stripping for multi-language graphs.
- **CycleDetector** — DFS-based cycle detection on directed graph
- **CouplingAnalyzer** — In-degree hotspot detection
- **DomainDetector** — Package-based domain assignment (3rd segment heuristic)
- **ImpactPropagator** — BFS propagation with depth limit, risk-weighted, cross-domain tracking
- **RiskScorer** — Threshold-based scoring (coupling, cross-domain, depth, critical path)
- **RuleValidator** — Validates: no_cycle, max_cross_domain, max_call_depth, forbid_core_entity_leakage
- **BlindSpotDetector** — Heuristic detection of reflection, dynamic proxy, event-driven patterns
- **ArchonConfig** — YAML config loader with defaults (`.archon.yml`)
- **ParseOrchestrator** — Multi-plugin coordination with two-phase graph construction (nodes, then edges)

### Language Plugin SPI

**LanguagePlugin interface** — ServiceLoader-based plugin architecture for multi-language parsing:
- Plugins discovered via `META-INF/services/com.archon.core.plugin.LanguagePlugin`
- Namespace prefixing required (e.g., `java:`, `js:`) for node ID isolation
- ParseResult includes DependencyGraph and sourceModules
- Optional DomainStrategy for language-specific domain detection
- reset() interface for state cleanup between parse runs

### Java Parser Plugin
- **JavaPlugin** — LanguagePlugin implementation wrapping JavaParserPlugin
- **JavaParserPlugin** — Orchestrator: ModuleDetector → file walk → AstVisitor + BlindSpotDetector
- **AstVisitor** — Walks JavaParser AST, extracts class nodes + import/extends/implements edges
- **ImportResolver** — Resolves imports to FQCN using source tree
- **ModuleDetector** — Detects Maven/Gradle source roots
- **JavaDomainStrategy** — 3rd-segment heuristic for domain assignment

### JavaScript/TypeScript Plugin
- **JsPlugin** — LanguagePlugin implementation using Google Closure Compiler
- **JsAstVisitor** — Regex-based ES module import extraction (import/export statements)
- **ModulePathResolver** — POSIX path resolution for relative imports
- **VueFileExtractor** — Vue SFC `<script>` section extraction
- **JsDomainStrategy** — package.json workspace detection for domain assignment

### CLI Commands
```
archon analyze <path> [--json] [--dot <file>] [--verbose]
archon impact <class> <path> [--depth N]
archon check <path> [--ci]
archon diff <base> <head> <path> [--ci] [--depth N]
```

Build: `./gradlew shadowJar` → `archon-cli/build/libs/archon-0.4.0.0.jar`

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
- ~~v0.1.1: External class filtering, adaptive thresholds, smarter domains, output polish~~ (DONE - see CHANGELOG)
- ~~v0.2: Diff-based analysis, config profiles, impact weight calibration~~ (DONE - diff command)
- ~~v0.3: Multi-language SPI + JavaScript/TypeScript plugin~~ (DONE)
- ~~v0.4: Security hardening + Vue support~~ (DONE)
- v0.5: Cross-language edge detection (Java REST endpoints ↔ JS HTTP calls), Plugin SDK tutorial
- v0.6: Visualization (web UI), ECP + decision support + LLM integration

## Testing

Run all tests: `./gradlew test`
Run single module: `./gradlew :archon-java:test`
Test count: ~129 tests across all modules (including archon-js)

## Language

PRD documents are written in Chinese. The product itself targets bilingual output (Chinese + English). Follow the language convention established in surrounding code when implementing.
