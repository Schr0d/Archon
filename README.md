# Archon

**Domain Analyzer & Dependency Guard** — An engineering decision tool for understanding system structure, evaluating change impact, and controlling architectural complexity.

## What It Does

Archon analyzes codebases to help engineers make informed decisions:

- **Structure Analysis** — Detect cycles, hotspots, and domain boundaries
- **Impact Propagation** — Predict how changes ripple through the system
- **Rule Validation** — Enforce architectural constraints with CI integration
- **Diff Analysis** — Git-aware change impact between refs

## Quick Start

### Installation

Download the latest shadow JAR from [releases](https://github.com/yourname/archon/releases):

```bash
# Or build from source
./gradlew shadowJar
```

### Basic Usage

```bash
# Analyze a Java project
java -jar archon-cli/build/libs/archon-0.4.0.0.jar analyze /path/to/project

# Check impact of changing a specific class
java -jar archon.jar impact com.example.Service /path/to/project

# Validate against architectural rules
java -jar archon.jar check /path/to/project --ci

# Diff-based impact analysis between git refs
java -jar archon.jar diff main HEAD /path/to/project
```

## Features

### Multi-Language Support

Archon uses a plugin architecture for language extensibility:

| Language | Plugin | Status |
|----------|--------|--------|
| Java | JavaParser-based | Built-in |
| JavaScript/TypeScript | Closure Compiler | Built-in |
| Vue | SFC script extraction | Built-in |

### Analysis Capabilities

- **Cycle Detection** — Find circular dependencies
- **Hotspot Analysis** — Identify high-coupling nodes
- **Domain Detection** — Automatic domain assignment
- **Blind Spot Reporting** — Flag dynamic patterns (reflection, EventBus)
- **Risk Scoring** — Quantitative risk assessment (LOW/MEDIUM/HIGH/VERY_HIGH/BLOCKED)

### CLI Commands

```
archon analyze <path> [--json] [--dot <file>] [--verbose]
archon impact <class> <path> [--depth N]
archon check <path> [--ci]
archon diff <base> <head> <path> [--ci] [--depth N]
```

## Architecture

### Modules

```
archon-core/     — Language-agnostic graph model, analysis engines, SPI
archon-java/     — Java parser plugin
archon-js/       — JavaScript/TypeScript parser plugin
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

# Output: archon-cli/build/libs/archon-0.4.0.0.jar
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
- [ ] v0.5 — Cross-language edge detection (Java REST ↔ JS HTTP)
- [ ] v0.6 — Visualization (web UI)

## Contributing

See [TODOS.md](TODOS.md) for deferred work and contribution opportunities.

## License

[Specify your license here]

## Links

- [CHANGELOG.md](CHANGELOG.md) — Version history
- [TODOS.md](TODOS.md) — Deferred work
