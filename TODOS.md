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
- **Status:** Must be addressed in ParseOrchestrator implementation
- **Pros:** Correct multi-plugin graph construction
- **Cons:** Two-phase construction (all nodes, then all edges) is slightly more complex than single-pass
- **Context:** Outside voice caught this concrete correctness issue. DiffCommand already uses a manual two-step merge for single-plugin — this extends the pattern to multi-plugin.
- **Depends on:** ParseOrchestrator must exist.

## 6. Real JS/TS monorepo validation target
- **What:** Identify a real-world JS/TS monorepo (500+ modules) to validate archon-js against. Equivalent to the 1043-class RuoYi monolith used for Java validation.
- **Why:** 41 tests against synthetic fixtures aren't enough. Java was validated against a real monolith. JS needs the same. Without this, parsing correctness on real code is unproven.
- **Status:** Needs research — identify candidate project
- **Pros:** Proves the tool works on real code, not just toy examples
- **Cons:** May need permission or may not find a suitable open-source target
- **Context:** Outside voice review: "The plan does not name a single real JS/TS project to validate against." Candidates: Vercel's Next.js monorepo, Google's angular/angular, any pnpm workspace monorepo.
- **Depends on:** archon-js module must parse basic JS first.

## 7. Open source timing — SPI stability concern
- **What:** Before open-sourcing, validate the SPI is not Java-shaped by having at least one external user try to build a plugin. If the SPI is wrong, changing it post-open-source breaks the plugin SDK story.
- **Why:** With only one user (the author), the SPI reflects assumptions from one perspective. The second language (JS) validates the SPI design, but a third party building a third language is the real test.
- **Status:** Strategic consideration, not a blocking TODO
- **Pros:** Prevents API churn that damages open-source credibility
- **Cons:** Delays open source, may be unnecessary if SPI is already good enough
- **Context:** Outside voice strategic disagreement. Recommendation: open source after JS plugin works, but label the SPI as @Beta/unstable until a third-party plugin exists.
- **Depends on:** v0.3 SPI + JS plugin shipped.
