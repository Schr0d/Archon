# Archon

**Domain Analyzer & Dependency Guard** — An engineering decision tool for understanding system structure, evaluating change impact, and controlling architectural complexity.

## What It Does

Archon analyzes codebases to help engineers make informed decisions:

- **Structure Analysis** — Detect cycles, hotspots, and domain boundaries
- **Impact Propagation** — Predict how changes ripple through the system
- **Rule Validation** — Enforce architectural constraints with CI integration
- **Diff Analysis** — Git-aware change impact between refs
- **Interactive Visualization** — Web-based graph viewer with pan/zoom and filtering

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
java -jar archon-cli/build/libs/archon-0.5.0.0.jar view /path/to/project

# Analyze with terminal output
java -jar archon-cli/build/libs/archon-0.5.0.0.jar analyze /path/to/project

# Export static HTML diagram
java -jar archon.jar view /path/to/project --export diagram.html

# Diff with web viewer (red=removed, green=added, yellow=changed)
java -jar archon.jar diff main HEAD /path/to/project --view

# Check impact of changing a specific module (Java class, TS module, or Python file)
java -jar archon.jar impact com.example.Service /path/to/project

# Validate against architectural rules
java -jar archon.jar check /path/to/project --ci

# Export to DOT or Mermaid formats
java -jar archon.jar analyze /path/to/project --dot graph.dot
java -jar archon.jar analyze /path/to/project --mermaid diagram.mmd
```

## Features

### Interactive Web Visualization [EXPERIMENTAL]

New in v0.5 — Archon includes an interactive web viewer (experimental):

**Working features:**
- Static HTML export with offline support (no internet required)
- JSON output format for programmatic access
- Mermaid and DOT export formats
- **Left sidebar navigation tree** for domain hierarchy exploration
- **Hotspot indicators** showing classes with high dependency counts (⭐ for 10+ dependencies)
- **Accessibility improvements** — 44px minimum button height, WCAG AA text contrast, motion reduction support
- **Intro hint overlay** for first-time users

**Known limitations:**
- Hierarchical domain/class visualization has rendering issues
- Domain bounding boxes may overlap in complex graphs
- Expand/collapse interaction needs refinement
- Layout algorithm (dagre.js) produces inconsistent results

**Recommended usage:**
- Use `--format json` for programmatic data access
- Use `--mermaid` or `--dot` for external visualization tools
- Use `--export` for static HTML (works offline)

For production use, prefer JSON output and integrate with your preferred visualization tool.

### Multi-Language Support

Archon uses a plugin architecture for language extensibility:

| Language | Plugin | Status |
|----------|--------|--------|
| Java | JavaParser-based | Built-in |
| JavaScript/TypeScript | Closure Compiler | Built-in |
| Python | Regex-based import parser | Built-in |
| Vue | SFC script extraction | Built-in |

### Analysis Capabilities

- **Cycle Detection** — Find circular dependencies
- **Hotspot Analysis** — Identify high-coupling nodes
- **Domain Detection** — Automatic domain assignment
- **Blind Spot Reporting** — Flag dynamic patterns (reflection, EventBus)
- **Risk Scoring** — Quantitative risk assessment (LOW/MEDIUM/HIGH/VERY_HIGH/BLOCKED)

### CLI Commands

```
archon view <path> [--port] [--no-open] [--export <file>] [--idle-timeout <min>]
archon analyze <path> [--json] [--dot <file>] [--mermaid <file>] [--verbose]
archon impact <module> <path> [--depth N]
archon check <path> [--ci]
archon diff <base> <head> <path> [--ci] [--depth N] [--view]
```

## Architecture

### Modules

```
archon-core/     — Language-agnostic graph model, analysis engines, SPI
archon-java/     — Java parser plugin
archon-js/       — JavaScript/TypeScript parser plugin
archon-python/   — Python import parser plugin
archon-viz/      — Web visualization and export formats
archon-cli/      — CLI with shadow JAR packaging
archon-test/     — Shared test fixtures
```

### Design Principles

1. **Deterministic First** — Static analysis only. LLMs assist but never modify dependency graphs.
2. **Declare Uncertainty** — Dynamic patterns are flagged as blind spots, not silently ignored.
3. **Human-in-the-loop** — The tool provides assessment; humans make decisions.
4. **Taste → Constraint** — Engineering judgments crystallize into enforceable rules.

## Building

```bash
# Run all tests
./gradlew test

# Build shadow JAR
./gradlew shadowJar

# Output: archon-cli/build/libs/archon-0.5.0.0.jar
```

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

## Roadmap

- [x] v0.1 — CLI + basic analysis
- [x] v0.2 — Diff-based analysis
- [x] v0.3 — Multi-language SPI
- [x] v0.4 — Security hardening + Vue support
- [x] v0.5 — Visualization (web UI)
- [ ] v0.6 — Cross-language edge detection (Java REST ↔ JS HTTP)

## Contributing

See [TODOS.md](TODOS.md) for deferred work and contribution opportunities.

## License

MIT

## Acknowledgments

The web viewer adapts the approach of [oh-my-mermaid](https://github.com/oh-my-mermaid/oh-my-mermaid) (MIT licensed).

## Links

- [CHANGELOG.md](CHANGELOG.md) — Version history
- [TODOS.md](TODOS.md) — Deferred work
