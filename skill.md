# Archon AI Skill

## Overview

Archon can be invoked as a skill by AI agents during the development loop to provide architectural context and validation.

---

## When to Use

Invoke this skill **before** making code changes and **after** making changes:

### Before Changes (Plan Phase)
- When planning a refactoring
- When modifying high-risk modules
- When crossing domain boundaries
- When evaluating impact surfaces

### After Changes (Review Phase)
- Before committing code
- Before creating a PR
- When validating architectural integrity
- When checking for introduced violations

---

## Skill Invocation

### Analyze Repository

```bash
java -jar archon.jar analyze <path> [--json] [--verbose]
```

**Returns:** Domain boundaries, hotspots, cycles, blind spots

**Use for:** Understanding architectural context before planning changes

---

### Impact Analysis

```bash
java -jar archon.jar impact <module> <path> [--depth N]
```

**Returns:** Affected nodes, cross-domain edges, risk levels

**Use for:** Evaluating blast radius before modifying specific modules

---

### Diff Validation

```bash
java -jar archon.jar diff <base> <head> <path> [--ci]
```

**Returns:** Added/removed edges, violations, gate status (PASS/BLOCKED)

**Use for:** Pre-commit validation, PR review gate

---

### Rule Checking

```bash
java -jar archon.jar check <path> [--ci]
```

**Returns:** Rule violations, architectural constraints

**Use for:** CI/CD integration, automated quality gates

---

## AI Agent Integration Pattern

### 1. Plan Phase

```python
# Agent: Get architectural context
context = archon.analyze(path, format="json")

# Check if target is safe to modify
if target_module in context["hotspots"]:
    return f"WARNING: {target_module} is HIGH-RISK hotspot"

# Plan within architectural constraints
plan = generate_plan(context["domains"], context["boundaries"])
```

### 2. Execute Phase

```python
# Agent: Make changes respecting constraints
for change in plan:
    if crosses_domain_boundary(change, context):
        review_change_architecture(change)
    apply_change(change)
```

### 3. Review Phase

```python
# Agent: Validate before committing
review = archon.diff(base="HEAD~1", head="HEAD", path=".", ci=True)

if review["gate"] == "BLOCKED":
    handle_violations(review["violations"])
    return "Fix required before commit"

return "Changes validated, safe to commit"
```

---

## Expected Output Format

### Analyze (JSON)

```json
{
  "domains": [
    {"name": "core", "nodes": 45, "boundaries": ["com.archon.core.*"]}
  ],
  "hotspots": [
    {"node": "com.archon.core.graph.DependencyGraph", "inDegree": 18, "risk": "HIGH"}
  ],
  "cycles": [],
  "blindSpots": [
    {"type": "CommonJS", "count": 624, "file": "dagre.min"}
  ]
}
```

### Diff (CI Mode)

```
=== Structural Impact Review ===
Violations: 1
  ✗ max_cross_domain exceeded (current: 4, limit: 3)
Gate: BLOCKED
```

---

## Best Practices for AI Agents

1. **Always analyze before planning** — Get architectural context first
2. **Check hotspots before modifying** — High-risk nodes require extra caution
3. **Validate after changes** — Use diff as a review gate
4. **Respect domain boundaries** — Cross-domain changes need architectural review
5. **Declare uncertainty** — Blind spots should be flagged, not ignored

---

## Exit Codes

- `0` — Success, no violations
- `1` — Violations found (in CI mode)
- `2` — Analysis error

---

## See Also

- [README.md](README.md) — Full documentation
- [TODOS.md](TODOS.md) — Deferred work
