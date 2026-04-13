# TODOS.md — Archon Deferred Work

## 1. Cross-language edge detection via API path matching
- **What:** Auto-detect edges between Java backend and JS frontend by matching REST endpoint paths (from @RequestMapping, @GetMapping) against fetch/axios URL patterns.
- **Why:** The "killer feature" (Premise P3). No tool today answers "if I change this Java endpoint, which React components break?"
- **Status:** Deferred to v0.4
- **Pros:** Unique capability, differentiates from all existing tools
- **Cons:** Complex — requires Java annotation extraction, JS HTTP call extraction, confidence scoring, cross-language blind spot reporting. Needs its own design doc.
- **Context:** After v0.3 ships, both plugins parse into a unified graph. The missing piece is automatic edges between Java service nodes and JS component nodes. Foundation is laid; detection is the remaining work.
- **Depends on:** v0.3 SPI + JS plugin must ship first.

## 2. Plugin SDK quickstart — "build a plugin in 30 minutes" tutorial
- **What:** Step-by-step tutorial walking through building a minimal language plugin. Target: working (trivial) plugin in 30 minutes.
- **Why:** Plugin SDK is the open-source attractor (Premise P4). A dry reference doc doesn't attract contributors — a tutorial does.
- **Status:** Deferred to post-v0.3
- **Pros:** Lowers barrier to community contributions dramatically
- **Cons:** Takes effort to write and maintain as SPI evolves
- **Context:** Success criterion 3 says "a minimal stub plugin passes SpiComplianceTest." This tutorial IS that stub plugin, written out step-by-step.
- **Depends on:** v0.3 SPI must ship first.

## 3. swc4j startup validation + graceful degradation
- **What:** At CLI startup, validate swc4j native library loads. If it fails, print clear message and disable JS/TS analysis. Java still works.
- **Why:** CRITICAL GAP — swc4j is Rust JNI library, may not support all platforms (ARM Windows, older glibc). Missing native lib causes UnsatisfiedLinkError → silent CLI crash.
- **Status:** Must be addressed as part of archon-js implementation
- **Pros:** Prevents silent crash on unsupported platforms. Aligns with "Declare Uncertainty" design principle.
- **Cons:** Adds startup check even for Java-only usage (negligible cost)
- **Context:** Failure modes audit identified this as the only critical gap. UnsatisfiedLinkError is unrecoverable without a pre-check.
- **Depends on:** archon-js module must exist.

## 4. Multi-platform shadow JAR distribution for swc4j
- **What:** Shadow JAR needs platform-specific native libs (.dll/.so/.dylib) for swc4j. Either: fat JAR with all 3 bundled, or platform-specific JAR builds via GitHub Actions matrix.
- **Why:** The current tool ships as a single shadow JAR. swc4j JNI native libs break this model. Users on ARM Windows, M-series Macs, Alpine Linux will hit UnsatisfiedLinkError.
- **Status:** Deferred to archon-js packaging phase
- **Pros:** Single-command install on all platforms
- **Cons:** CI complexity (cross-platform builds or fat JAR size increase)
- **Context:** Outside voice review identified this as an unaddressed distribution gap. The graceful degradation (TODO 3) handles runtime failure, but distribution should prevent the failure in the first place.
- **Depends on:** archon-js module + swc4j dependency.

## 5. Real Python codebase validation target
- **What:** Validate archon-python against a real-world Python codebase. User has small projects available at `C:\T480\Documents\codebase\cps_prod_v6` for initial validation.
- **Why:** 20-25 tests against synthetic fixtures aren't enough. Java was validated against RuoYi (1043 classes), JS/TS against geditor-ui-react (375 modules) and geditor-ui-vue (489 files). Python needs the same. Without this, parsing correctness on real code is unproven.
- **Status:** AVAILABLE — User has local Python projects for validation. Larger targets (500+ modules) can be found after initial validation succeeds.
- **Pros:** Proves the tool works on real code, not just toy examples.
- **Cons:** Small projects may not expose all edge cases (large monorepos, complex relative imports, etc.).
- **Context:** Design step 8 includes "Test on real Python codebase." This TODO captures the validation target explicitly. Similar to TODO #6 for JS/TS.
- **Depends on:** archon-python module must parse basic Python first.

## 6. BUG: AnalyzeCommand JSON output not implemented
- **What:** `AnalyzeCommand` defines `--json`, `--with-metadata`, `--with-full-analysis` flags but doesn't implement JSON output. Flags are parsed but never used.
- **Why:** User confusion — flags exist in help text but do nothing. Found during integration testing.
- **Status:** IN PROGRESS — Being fixed in feature/ai-integration-viz-redesign PR
- **Workaround:** Use `view --format json --with-full-analysis --with-metadata` instead
- **Fix:** Implement JSON output in AnalyzeCommand using JsonSerializer from archon-viz module
- **Context:** Blocks canvas visualization testing. Needed for consistent `archon analyze --json` output.
- **File:** `archon-cli/src/main/java/com/archon/cli/AnalyzeCommand.java`

## 7. Browser automation tests for DOM+SVG visualization
- **What:** Add Playwright or similar browser automation tests for DOM+SVG graph visualization.
- **Why:** Manual testing is error-prone. Automated tests catch regressions in interactions (hover impact overlay, click drill-down, drag nodes, socket repositioning, mode transitions).
- **Status:** Deferred to post-v0.5
- **Pros:** Regression coverage for interactions, CI confidence
- **Cons:** ~400 LOC of test infrastructure, slower CI runs
- **Context:** DOM+SVG viz ships with manual test plan. Add automation if it becomes a pain point.
- **Depends on:** DOM+SVG visualization must ship first.

## 8. Quick Start Docs (Java 17 Prerequisite)
- **What:** Add Java 17 prerequisite check to README.md Quick Start section before build commands.
- **Why:** Platform engineers hitting JDK 1.8 build failures waste time debugging environment issues. Clear prerequisite documentation prevents this friction.
- **Status:** Deferred — DX review improvement
- **Pros:** Reduces onboarding friction; prevents confusing build errors; aligns with Champion tier TTHW goal.
- **Cons:** Minor documentation maintenance; adds one more step to quick start.
- **Context:** Current README shows build commands without mentioning Java 17 requirement. JDK 1.8 is default system PATH, causing cryptic compilation errors. See design doc `ThinkPad-main-treemap-block-viz-design-20260410.md` section "Quick Start Enhancement."
- **Depends on:** Documentation update only.

## 9. Error Handling (Java Version Check + Browser Compatibility)
- **What:** Add Java version check in ViewCommand and browser compatibility warnings for canvas visualization.
- **Why:** Platform engineers expect clear, actionable error messages. Current "class file version 61.0" error is cryptic and wastes debugging time.
- **Status:** Deferred — DX review improvement
- **Pros:** Better developer experience; faster issue resolution; prevents support burden.
- **Cons:** ~10 LOC additional code; requires testing version check logic.
- **Context:** See design doc `ThinkPad-main-treemap-block-viz-design-20260410.md` section "Debug: Enhanced Error Handling." Includes Java 17 check in ViewCommand with System.getProperty("java.version"), canvas support detection with fallback message, actionable error messages linking to troubleshooting docs.
- **Depends on:** ViewCommand.java + archon-viz-web/index.html changes.

## 10. Upgrade Path (CHANGELOG.md + --version Flag)
- **What:** Create CHANGELOG.md, add --version flag to CLI, document migration guide.
- **Why:** Platform engineers running dependency analysis in production need to know what changed between versions and how to upgrade safely. Current lack of changelog creates upgrade anxiety.
- **Status:** Deferred — DX review improvement
- **Pros:** Professional release hygiene; enables production confidence; supports enterprise adoption.
- **Cons:** Maintains separate document; requires discipline to update on releases.
- **Context:** See design doc `ThinkPad-main-treemap-block-viz-design-20260410.md` section "Upgrade Path Improvements." Includes CHANGELOG.md with semantic versioning sections, `archon --version` flag implementation, migration guide for breaking changes.
- **Depends on:** Documentation + ViewCommand.java changes.

## 11. Documentation Gaps (TROUBLESHOOTING.md + API Docs + CI Examples)
- **What:** Create TROUBLESHOOTING.md, add API documentation for ViewServer endpoints, include CI/CD integration examples.
- **Why:** Platform engineers integrating Archon into CI pipelines need working examples and debugging guidance. Current lack of troubleshooting docs creates friction when things go wrong.
- **Status:** Deferred — DX review improvement
- **Pros:** Self-service issue resolution; reduces support burden; enables enterprise integration.
- **Cons:** ~3 new documentation files; ongoing maintenance cost.
- **Context:** See design doc `ThinkPad-main-treemap-block-viz-design-20260410.md` section "Documentation Gaps." Includes TROUBLESHOOTING.md with common issues and solutions, ViewServer API documentation (/api/graph, /api/node, etc.), GitHub Actions and GitLab CI examples.
- **Depends on:** Documentation only; ViewServer.java already implements the API.

## 12. Fix DiffCommand --json flag
- **What:** Add `--json` and `--quiet` flags to DiffCommand.java. Reuse existing `DiffSerializer.toJson()` and `GraphDiffer` logic. ~15 lines of code.
- **Why:** DiffCommand has full diff logic but no JSON output mode. The `--json` flag is needed for the Claude Code skill to pipe structured output to Claude for interpretation. Without it, the skill would have to parse human-readable text output.
- **Status:** READY — Clear scope, existing code to reuse (`DiffSerializer`, `GraphDiffer`)
- **Pros:** Enables the entire Archon skill pipeline; minimal code change; leverages existing serialization
- **Cons:** None meaningful — this is a straightforward flag addition
- **Context:** CEO review (2026-04-13) identified this as the critical enabler. Outside voice simplified the plan to "fix this first, then wrap the CLI." File: `archon-cli/src/main/java/com/archon/cli/DiffCommand.java`
- **Depends on:** Nothing — all required code exists.

## 13. GitHub Action for archon diff on PRs
- **What:** Build a GitHub Action that runs `archon diff` on every pull request and posts the impact report as a PR comment.
- **Why:** The 10x vision is Archon running automatically, not just when invoked manually. A GitHub Action makes dependency analysis part of the code review workflow without any manual step.
- **Status:** DEFERRED — After dogfooding the Claude Code skill
- **Pros:** Zero-friction adoption; automatic on every PR; team visibility
- **Cons:** Docker packaging needed; CI/CD setup; may be slow for large repos
- **Context:** CEO plan (2026-04-13) deferred to post-dogfooding. Distribution phase 2. The skill must prove useful locally before investing in CI integration.
- **Depends on:** DiffCommand --json fix (TODO #12)

## 15. Community Setup (CONTRIBUTING.md + Issue Templates + Discussions)
- **What:** Create CONTRIBUTING.md with development setup, add GitHub issue templates (bug report, feature request), enable GitHub Discussions.
- **Why:** Platform engineers evaluating tools for enterprise adoption look for project health signals. A professional contribution workflow and active community discussions indicate long-term viability and support.
- **Status:** Deferred — DX review improvement
- **Pros:** Attracts contributors; professional project appearance; enables community support; reduces support burden through self-service issue reporting.
- **Cons:** Maintains additional documents; requires triaging Discussions and Issues; community management overhead.
- **Context:** See design doc `ThinkPad-main-treemap-block-viz-design-20260410.md` section "Community & Ecosystem Improvements." Includes CONTRIBUTING.md with dev setup guide, issue templates for bug/feature/Discord, GitHub Discussions enabled for Q&A.
- **Depends on:** GitHub repository configuration; documentation only.
