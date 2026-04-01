# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project: Archon

**Domain Analyzer & Dependency Guard** — an engineering decision tool for understanding system structure, evaluating change impact, and controlling architectural complexity. Positioned as a "structure observability + assessment + control" layer.

## Current Status

Pre-implementation. Product specification exists in two PRD documents:
- `PRD_v0.4.md` — Systems Engineering Edition (core capabilities, risk model, roadmap)
- `PRD_extend.md` — Trust & ECP Edition v0.5 (confidence scoring, blind spots, LLM boundaries)

## Design Principles (from PRD)

These are non-negotiable architectural constraints:

1. **Deterministic First**: Dependency graphs and call graphs are objective facts. Core structure analysis must be static analysis only — LLM must never generate or modify dependency graphs, calculate impact, or change rules.
2. **Declare Uncertainty**: The tool must be able to say "I don't know." Dynamic calls (reflection, EventBus, dynamic proxy, agent prompt flows) must be flagged as Blind Spots with confidence ratings. Never mislead by pretending omniscience.
3. **Human-in-the-loop**: The tool provides structure and assessment; humans make final decisions.
4. **Taste → Constraint**: Every engineering judgment must eventually crystallize into enforceable rules.

## Planned Architecture

### Core Engines
- **Structure Modeling**: Builds dependency graph and domain graph from static analysis. Multi-level: class / module / domain.
- **Blind Spot Detection**: Identifies reflection, dynamic loading, event-driven, MyBatis XML mappers, and agent flow dependencies that static analysis cannot resolve. Outputs confidence: LOW with reason.
- **Cycle Detection**: Cycle = VERY HIGH risk. Interactive mode: report + continue analysis. CI mode (`--ci`): blocks with exit 1. ECP blocks only if target is IN a cycle.
- **Impact Propagation Engine**: BFS-based, N-degree propagation (default 3). Risk-weighted using domain/coupling/critical-path weights. Diff-based analysis (Git diff → impact radius).
- **Domain Boundary Analyzer**: Automatic domain identification, boundary violation detection, domain confidence scoring, boundary heatmap.
- **ECP Engine**: Generates Engineering Change Proposals with impact summary, risk level, confidence, blind spots, and recommendations.
- **Rule System**: Enforces architecture constraints (no_cycle, max_cross_domain, max_call_depth, forbid_core_entity_leakage). CI-integratable.

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

### Planned Roadmap
- v0.1: CLI + basic analysis
- v0.2: Impact + Rule system
- v0.3: Visualization
- v0.4: ECP + decision support

## Language

PRD documents are written in Chinese. The product itself targets bilingual output (Chinese + English). Follow the language convention established in surrounding code when implementing.
