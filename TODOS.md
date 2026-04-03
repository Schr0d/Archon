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

## 5. ParseOrchestrator merge ordering — nodes before edges
- **What:** ParseOrchestrator must add ALL nodes from all plugins to the MutableBuilder BEFORE adding any edges. MutableBuilder.addEdge() silently drops edges to missing targets.
- **Why:** If two plugins produce graphs independently, edges from plugin A that reference nodes from plugin B get silently lost unless all nodes exist first. This is a correctness bug, not a performance issue.
- **Status:** RESOLVED — Engineering review approved adding ParseOrchestrator with two-phase construction. Part of Milestone 1.
- **Pros:** Correct multi-plugin graph construction
- **Cons:** Two-phase construction (all nodes, then all edges) is slightly more complex than single-pass
- **Context:** Outside voice caught this concrete correctness issue. DiffCommand already uses a manual two-step merge for single-plugin — this extends the pattern to multi-plugin. Eng review decision: add ParseOrchestrator.
- **Depends on:** ParseOrchestrator must exist.

## 6. Real JS/TS monorepo validation target
- **What:** Identify a real-world JS/TS monorepo (500+ modules) to validate archon-js against. Equivalent to the 1043-class RuoYi monolith used for Java validation.
- **Why:** 41 tests against synthetic fixtures aren't enough. Java was validated against a real monolith. JS needs the same. Without this, parsing correctness on real code is unproven.
- **Status:** AVAILABLE — User has real JS/TS monorepo + Vue2 legacy system for validation. Location to be provided.
- **Pros:** Proves the tool works on real code, not just toy examples
- **Cons:** May need permission or may not find a suitable open-source target
- **Context:** Outside voice review: "The plan does not name a single real JS/TS project to validate against." Eng review decision: require E2E validation target.
- **Depends on:** archon-js module must parse basic JS first.

## 7. Open source timing — SPI stability concern
- **What:** Before open-sourcing, validate the SPI is not Java-shaped by having at least one external user try to build a plugin. If the SPI is wrong, changing it post-open-source breaks the plugin SDK story.
- **Why:** With only one user (the author), the SPI reflects assumptions from one perspective. The second language (JS) validates the SPI design, but a third party building a third language is the real test.
- **Status:** Strategic consideration, not a blocking TODO
- **Pros:** Prevents API churn that damages open-source credibility
- **Cons:** Delays open source, may be unnecessary if SPI is already good enough
- **Context:** Outside voice strategic disagreement. Recommendation: open source after JS plugin works, but label the SPI as @Beta/unstable until a third-party plugin exists.
- **Depends on:** v0.3 SPI + JS plugin shipped.

## 8. Node ID namespace prefixing
- **What:** Add language identifier prefixes to node IDs in the SPI (e.g., `java:com.foo.Bar`, `js:src/components/Header`). ParseOrchestrator strips prefixes before adding to graph.
- **Why:** Prevents namespace collision across language plugins. Java uses FQCNs (dot-separated), JS uses POSIX paths (slash-separated). Future plugins might use overlapping conventions.
- **Status:** Must implement in Milestone 1 (LanguagePlugin SPI)
- **Pros:** Namespace isolation enforced at SPI boundary. Future-proof against new language plugins.
- **Cons:** Prefix stripping adds complexity to ParseOrchestrator. Prefixes must be documented for plugin authors.
- **Context:** Eng review identified this as a correctness issue: "If a future Python plugin uses dot-separated module paths, we could get collisions with Java FQCNs." Decision: add prefixing now rather than defer.
- **Depends on:** ParseOrchestrator exists (Milestone 1).

## 9. Optional DomainStrategy
- **What:** LanguagePlugin.getDomainStrategy() returns `Optional<DomainStrategy>`. Empty means use fallback pivot detection. Plugins without domain concepts aren't forced to implement.
- **Why:** Not all languages have domain semantics. A hypothetical plugin for a language with flat module structure shouldn't be required to implement domain detection.
- **Status:** Must implement in Milestone 1 (LanguagePlugin SPI)
- **Pros:** Flexibility for future plugins. Fallback behavior (pivot detection) already exists.
- **Cons:** Plugins can skip domain detection, potentially losing useful analysis. Optional adds API surface area.
- **Context:** Eng review questioned whether DomainStrategy should be required or optional. Decision: optional via `Optional<>` for maximum flexibility.
- **Depends on:** DomainStrategy interface exists (Milestone 1).

## 10. Graph in ParseResult
- **What:** ParseResult includes the built `DependencyGraph`, not just metadata. Rename `sourceClasses` field to `sourceModules` for language-agnostic naming.
- **Why:** Current design has awkward hand-off: plugin modifies MutableBuilder, caller extracts graph. Including graph in ParseResult creates cleaner API. `sourceClasses` is Java-specific naming.
- **Status:** Must implement in Milestone 1 (SPI interface)
- **Pros:** Cleaner API — plugins return complete result, not partial metadata. Generic naming scales to all languages.
- **Cons:** ParseResult becomes heavier (includes graph reference). Plugins must call `builder.build()` before returning.
- **Context:** Eng review identified API inconsistency: "sourceClasses in ParseResult but sourceTreeModules in ParseContext." Decision: clean up the API, include graph in result.
- **Depends on:** ParseResult interface exists (Milestone 1).

## 11. Warn on unclaimed file extensions
- **What:** When source files have no matching plugin (e.g., .py files with no Python plugin), log a warning: "No plugin found for .py files; skipping N files."
- **Why:** Users should know when source files are being silently skipped. Silent skipping hides data and creates false confidence.
- **Status:** Must implement in CLI integration (Milestone 4)
- **Pros:** User knows what's being analyzed and what's being skipped. Clearer diagnostics.
- **Cons:** Warning spam for projects with many ignored file types. Verbose mode alternative considered but rejected in favor of always-warn.
- **Context:** Eng review asked: "If a project has .py files but no Python plugin, should we warn or skip silently?" Decision: warn on unclaimed files.
- **Depends on:** PluginDiscoverer exists (Milestone 2).

## 12. Closure Compiler validation milestone (Milestone 2.5)
- **What:** Before implementing archon-js (Milestone 3), prototype parsing a TypeScript file with `import type { X } from './foo'` and `import { type Y } from './bar'` using Closure Compiler. Verify the AST represents type-only imports correctly.
- **Why:** If Closure Compiler loses type-only import information, the JS plugin will produce incorrect graphs. Must validate before building on top of it.
- **Status:** BLOCKING for Milestone 3 (archon-js implementation)
- **Pros:** Prevents building on incorrect assumptions. Early failure mode if Closure Compiler is wrong choice.
- **Cons:** Delays Milestone 3 by ~1 day for validation prototype. If Closure Compiler fails, need alternative parser (TypeScript Compiler API via GraalJS).
- **Context:** Design chose Closure Compiler for "pure Java, no native dependencies" but acknowledged type-only import accuracy concern. Eng review: add as blocking validation.
- **Depends on:** Closure Compiler dependency added to project.

## 13. Real Python codebase validation target
- **What:** Validate archon-python against a real-world Python codebase. User has small projects available at `C:\T480\Documents\codebase\cps_prod_v6` for initial validation.
- **Why:** 20-25 tests against synthetic fixtures aren't enough. Java was validated against RuoYi (1043 classes), JS/TS against geditor-ui-react (375 modules) and geditor-ui-vue (489 files). Python needs the same. Without this, parsing correctness on real code is unproven.
- **Status:** AVAILABLE — User has local Python projects for validation. Larger targets (500+ modules) can be found after initial validation succeeds.
- **Pros:** Proves the tool works on real code, not just toy examples.
- **Cons:** Small projects may not expose all edge cases (large monorepos, complex relative imports, etc.).
- **Context:** Design step 8 includes "Test on real Python codebase." This TODO captures the validation target explicitly. Similar to TODO #6 for JS/TS.
- **Depends on:** archon-python module must parse basic Python first.

---

## Completed

### v0.4.0.0 (2026-04-03)

**Security & Robustness Fixes:**
- Namespace collision detection (throws exception instead of silent data loss)
- File size validation before parsing (prevents OOM)
- Plugin reset interface for state cleanup
- Malformed namespace prefix validation

### v0.3.0.0 (2026-04-03)

**Milestone 1: Multi-Language SPI + JS Plugin**
- **Item 5:** ParseOrchestrator merge ordering — two-phase construction (nodes before edges) ✓
- **Item 8:** Node ID namespace prefixing (java:, js:) ✓
- **Item 9:** Optional DomainStrategy via Optional<> ✓
- **Item 10:** Graph included in ParseResult, sourceModules naming ✓
- **Item 11:** Warn on unclaimed file extensions in ParseOrchestrator ✓
- **Item 12:** Closure Compiler validation milestone (ClosureCompilerValidationTest) ✓
- **Item 6:** Real JS/TS monorepo validation (geditor-ui-react: 375 modules, geditor-ui-vue: 489 files) ✓
