# TODOS.md — Archon Deferred Work

## 1. Cross-language edge detection via API path matching
- **What:** Auto-detect edges between Java backend and JS frontend by matching REST endpoint paths (from @RequestMapping, @GetMapping) against fetch/axios URL patterns.
- **Why:** The "killer feature" — no tool today answers "if I change this Java endpoint, which React components break?"
- **Status:** DEFERRED — Requires dedicated design doc. Foundation (unified graph, multi-language SPI) is in place since v0.3.
- **Pros:** Unique capability, differentiates from all existing tools
- **Cons:** Complex — requires Java annotation extraction, JS HTTP call extraction, confidence scoring, cross-language blind spot reporting. Needs its own design doc.

## 2. Plugin SDK quickstart — "build a plugin in 30 minutes" tutorial
- **What:** Step-by-step tutorial walking through building a minimal language plugin using the declaration-based SPI (`ModuleDeclaration`, `DependencyDeclaration`).
- **Why:** Plugin SDK is the open-source attractor. A tutorial lowers the barrier to community contributions.
- **Status:** DEFERRED — SPI stabilized in v0.6 (declaration-based). Good time to write this when community interest emerges.
- **Pros:** Attracts contributors; demonstrates SPI simplicity
- **Cons:** Takes effort to write and maintain as SPI evolves

## 3. Real Python codebase validation target
- **What:** Validate archon-python against a real-world Python codebase with 500+ modules.
- **Why:** 20-25 tests against synthetic fixtures aren't enough. Java was validated against RuoYi (1043 classes), JS/TS against geditor-ui-react (375 modules). Python needs the same.
- **Status:** AVAILABLE — User has local Python projects for validation. Larger targets (500+ modules) can be found after initial validation succeeds.
- **Pros:** Proves the tool works on real code, not just toy examples.
- **Cons:** Small projects may not expose all edge cases.

## ~~4. AnalyzeCommand JSON output~~ — DONE
- **Resolved in v0.7.0.0:** `--format agent` flag provides structured JSON output via `AgentOutputFormatter` with node metadata (PageRank, betweenness, impact score, risk level).
- **Resolved in v0.7.1.0:** `view` command removed. `analyze --format agent` is now the sole JSON output path.

## ~~5. Browser automation tests for DOM+SVG visualization~~ — OBSOLETE
- **archon-viz module removed in v0.7.1.0.** Visualization is no longer a built-in feature.

## ~~6. Quick Start Docs (Java 17 Prerequisite)~~ — DONE
- **Resolved:** README.md lists Java 17 as prerequisite in Quick Start section.

## ~~7. Error Handling (Java Version Check + Browser Compatibility)~~ — OBSOLETE
- **ViewCommand removed in v0.7.1.0.** This TODO referenced ViewCommand.java and archon-viz-web.

## ~~8. Upgrade Path (CHANGELOG.md + --version Flag)~~ — DONE
- **Resolved:** CHANGELOG.md maintained since v0.1. `--version` flag available via picocli `mixinStandardHelpOptions`.

## ~~9. Documentation Gaps (TROUBLESHOOTING.md + API Docs + CI Examples)~~ — PARTIALLY OBSOLETE
- **ViewServer API removed with archon-viz.** The CI examples TODO (#11 GitHub Action) remains relevant.
- **Remaining gap:** TROUBLESHOOTING.md with common issues (JDK 1.8 on PATH, Spring DI requires compiled classes, diff lock file recovery).

## 10. DiffCommand `--json` flag
- **What:** Add a simpler `--json` flag alongside `--format agent` for lean diff output (just added/removed/changed edges without full graph metadata).
- **Status:** LOW PRIORITY — `--format agent` in v0.7.0.0 covers the AI integration use case. A leaner JSON format would be nice-to-have for CI scripting.
- **Context:** The Claude Code skill uses `--format agent` which works well. A simpler format would reduce output size for CI pipelines.

## 11. GitHub Action for archon diff on PRs
- **What:** Build a GitHub Action that runs `archon diff` on every pull request and posts the impact report as a PR comment.
- **Why:** Zero-friction adoption — dependency analysis becomes part of code review automatically.
- **Status:** DEFERRED — After dogfooding the Claude Code skill.
- **Pros:** Automatic on every PR; team visibility
- **Cons:** Docker packaging needed; CI/CD setup; may be slow for large repos

## 12. Community Setup (CONTRIBUTING.md + Issue Templates)
- **What:** Create CONTRIBUTING.md with development setup, add GitHub issue templates (bug report, feature request), enable GitHub Discussions.
- **Status:** DEFERRED — Low priority until open-source launch.
- **Pros:** Attracts contributors; professional project appearance.
- **Cons:** Community management overhead.
