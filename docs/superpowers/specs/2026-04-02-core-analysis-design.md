# Core Analysis Logic — Design Spec

Generated: 2026-04-02
Source: IMPLEMENTATION_PLAN_v0.1.md Steps 3-4
Status: APPROVED (derived from reviewed plan)

---

## Scope

Implement the core analysis engines in `archon-core` and complete configuration support. This covers Steps 3-4 of the v0.1 implementation plan. The graph model (Step 2) is already complete.

### In Scope
- Configuration YAML parsing (`ArchonConfig` enhancement)
- `RuleValidator` — evaluate rules against graph
- `CycleDetector` — Tarjan's SCC algorithm
- `CouplingAnalyzer` — in-degree counting, hotspot identification
- `DomainDetector` — package convention + config override + fallback
- `RiskScorer` — threshold-based risk assessment with confidence escalation
- `ImpactPropagator` — BFS with depth limit, risk-weighted propagation
- Unit tests for all engines (30+ tests)

### Out of Scope (deferred to subsequent steps)
- Step 5: Visualization (AsciiRenderer, DotExporter, JsonSerializer)
- Step 6: ECP Generator
- Step 7: archon-java parser plugin
- Step 8: CLI command wiring
- Steps 9-10: Test fixtures and integration/E2E tests

---

## Existing Infrastructure

The graph model is fully implemented:
- `DependencyGraph` — immutable adjacency list with forward/reverse maps, node/edge metadata
- `Node` — id (FQCN), type, domain, sourcePath, tags, confidence, in/out degrees
- `Edge` — source, target, type (IMPORTS/CALLS/IMPLEMENTS/EXTENDS/USES), confidence, dynamic, evidence
- `BlindSpot` — file, line, type, pattern, confidence (always LOW)
- `ArchonConfig` — data class with all config fields, `defaults()` factory

All analysis stubs currently throw `UnsupportedOperationException`.

---

## Components

### 1. ArchonConfig — YAML Parsing

Enhance existing `ArchonConfig` to parse `.archon.yml` and merge with defaults.

**Config format:**
```yaml
version: 1
rules:
  no_cycle: true
  max_cross_domain: 2
  max_call_depth: 3
  forbid_core_entity_leakage:
    - com.fuwa.system.domain.SysUser
domains:
  auth: ["com.fuwa.framework.security"]
  system: ["com.fuwa.system"]
  shared: ["com.fuwa.shared"]
critical_paths:
  - auth
  - payment
ignore:
  - "**/generated/**"
```

**Behavior:**
- Missing `.archon.yml` → use defaults silently
- Invalid YAML → parse error with line number, throw descriptive exception
- Config values override defaults; unspecified fields keep defaults

**Tests:** Parse valid YAML, missing file → defaults, invalid YAML → error, override behavior

### 2. CycleDetector — Tarjan's SCC

**Algorithm:** Tarjan's strongly connected components. O(V+E).

**Input:** `DependencyGraph`
**Output:** `List<List<String>>` — each inner list is a cycle path (node ids)

**Behavior:**
- Self-loops (A→A) are detected as single-node cycles
- Non-trivial cycles (A→B→C→A) returned as full paths
- Empty graph → empty list
- Acyclic graph → empty list
- Multiple disconnected cycles → all returned

**Tests:** No cycle, simple A↔B, long cycle (3+), multiple independent cycles, self-loop, empty graph

### 3. CouplingAnalyzer — In-Degree Hotspots

**Algorithm:** Count in-degree from reverse adjacency map. Sort descending.

**Input:** `DependencyGraph`, optional threshold (default 5)
**Output:** `List<Node>` sorted by in-degree descending, filtered above threshold

**Behavior:**
- In-degree = number of distinct classes that depend on this node
- Hotspot threshold default: 5 (configurable via config)
- Returns empty list if no hotspots
- Each returned node has `inDegree` populated

**Tests:** Empty graph, single hotspot, sorted order, below threshold filtered out, all hotspots

### 4. DomainDetector — Package-Based Domain Assignment

**Three-tier resolution for each node:**

| Tier | Source | Confidence |
|------|--------|------------|
| 1 | `.archon.yml` domains section (exact package prefix match) | HIGH |
| 2 | Package naming convention (extract segment after known prefix) | MEDIUM |
| 3 | Top-level package segment as fallback | LOW |

**Algorithm:**
1. For each node, check config domain mapping for exact prefix match
2. If no match, extract domain from package convention (e.g., `com.fuwa.system.domain.SysUser` → `system`)
3. If still no match, use top-level segment with LOW confidence
4. Detect "wrong package" violations (e.g., controller in wrong domain package)

**Input:** `DependencyGraph`, `ArchonConfig`
**Output:** `Map<String, String>` (node id → domain name), updated nodes with domain + confidence

**Tests:** Config override match, package convention match, no match → LOW confidence, wrong package detection

### 5. RiskScorer — Threshold-Based Assessment

**Risk dimensions and thresholds:**

| Dimension | Threshold | Risk Level |
|-----------|-----------|------------|
| Coupling (in-degree) | >10 | HIGH |
| Coupling (in-degree) | 5-10 | MEDIUM |
| Cross-domain count | >=3 | HIGH |
| Call depth | >=3 | HIGH |
| Critical path (auth/payment) | any | HIGH |
| Cycle detected | yes | VERY_HIGH |
| Low confidence | any | +1 level escalation |

**Aggregation:** Max across all applicable conditions. Confidence escalation applies after max.

**Input:** Node, DependencyGraph, analysis context (cycles, domain info)
**Output:** `RiskLevel`

**Output levels:** LOW, MEDIUM, HIGH, VERY_HIGH, BLOCKED

**Tests:** All threshold combinations, max aggregation, confidence escalation, LOW baseline

### 6. ImpactPropagator — BFS with Depth Limit

**Algorithm:** BFS from target node via reverse adjacency (dependents), depth-limited.

**Parameters:**
- Default depth: 3 (configurable via config)
- Traversal direction: reverse adjacency (who depends on me = blast radius)

**Input:** `DependencyGraph`, target node id, depth limit
**Output:** Impact result containing:
- All reached nodes with their depth and risk level
- Cross-domain edges traversed
- Blind spots in path
- Max depth reached
- Total affected count

**Behavior:**
- Target not found → error with fuzzy-match suggestions
- Isolated node (no dependents) → single-node result
- Diamond dependencies (A depends on B and C, both depend on D) → D appears at max depth
- Depth exceeded → nodes beyond limit not included

**Tests:** Single hop, 3 hops, depth limit exceeded, diamond dependency, isolated node, risk scoring of reached nodes

### 7. RuleValidator — Rule Enforcement

**Rules:**

| Rule | Check | Fail Condition |
|------|-------|----------------|
| `no_cycle` | CycleDetector results | Any cycle found |
| `max_cross_domain` | Per-node cross-domain count | Any node exceeds limit |
| `max_call_depth` | ImpactPropagator max depth | Any propagation exceeds limit |
| `forbid_core_entity_leakage` | Cross-domain dependents of listed entities | Any listed entity has cross-domain dependent |

**Input:** `DependencyGraph`, `ArchonConfig`, analysis results (cycles, domains)
**Output:** `List<RuleViolation>` with rule name, severity, details

**Tests:** All rules pass, each rule individually violated, mixed pass/fail

---

## Data Flow

```
DependencyGraph (from parser, already built)
    │
    ├──→ DomainDetector ──→ node domains assigned
    │
    ├──→ CycleDetector ──→ cycle paths
    │
    ├──→ CouplingAnalyzer ──→ hotspot list
    │
    ├──→ RiskScorer ──→ per-node risk levels
    │
    ├──→ ImpactPropagator ──→ impact radius (on demand, per target)
    │
    └──→ RuleValidator ──→ rule violations
```

DomainDetector runs first (other engines need domain info). CycleDetector and CouplingAnalyzer are independent of each other. RiskScorer consumes results from all others. ImpactPropagator is invoked on-demand per target. RuleValidator consumes results from all engines.

---

## Error Handling

| Codepath | Failure | Behavior |
|----------|---------|----------|
| ArchonConfig.load() | Missing file | Use defaults silently |
| ArchonConfig.load() | Invalid YAML | Parse error with line number |
| CycleDetector | Empty graph | Return empty list |
| ImpactPropagator | Target not found | Error with fuzzy-match suggestions |
| RiskScorer | Node missing metrics | Compute on-the-fly, log warning |
| DomainDetector | No package info | Top-level fallback with LOW confidence |

---

## Performance Targets

From the implementation plan's budget for 1000 classes:

| Engine | Budget |
|--------|--------|
| Cycle detection (Tarjan's) | 1s |
| Coupling analysis | 1s |
| Domain detection | 0.5s |
| Risk scoring | 0.5s |
| Impact propagation | 1s |
| **Total analysis** | **~4s** |

---

## Implementation Order

1. `ArchonConfig` YAML parsing enhancement
2. `DomainDetector` (other engines need domain info)
3. `CycleDetector`
4. `CouplingAnalyzer`
5. `RiskScorer`
6. `ImpactPropagator`
7. `RuleValidator`
8. Unit tests for each (TDD: write tests alongside implementation)

---

## Acceptance Criteria

Core analysis engines are complete when:
1. All engines process a `DependencyGraph` without throwing `UnsupportedOperationException`
2. Each engine has 5+ unit tests covering normal + edge cases
3. CycleDetector correctly identifies cycles in a test graph
4. ImpactPropagator produces correct BFS traversal with depth limiting
5. RiskScorer applies all thresholds with max aggregation
6. DomainDetector assigns domains via all three tiers
7. RuleValidator evaluates all four rule types
8. `./gradlew :archon-core:test` passes
