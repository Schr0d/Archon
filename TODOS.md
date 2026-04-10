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

## 7. Browser automation tests for canvas visualization
- **What:** Add Playwright or similar browser automation tests for canvas graph visualization.
- **Why:** Manual testing is error-prone. Automated tests catch regressions in interactions (hover, click, pan, zoom, drill-down).
- **Status:** Deferred to post-v0.5
- **Pros:** Regression coverage for interactions, CI confidence
- **Cons:** ~400 LOC of test infrastructure, slower CI runs
- **Context:** Canvas viz ships with manual test plan. Add automation if it becomes a pain point.
- **Depends on:** Canvas visualization must ship first.
