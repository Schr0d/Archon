# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.6.0.2] - 2026-04-14

### Added
- **Declaration-based plugin SPI** — You can now write language plugins that return `ModuleDeclaration` and `DependencyDeclaration` records instead of building graphs directly. Graph construction is centralized in `DeclarationGraphBuilder`, so new plugins have less boilerplate and fewer places to go wrong.
- **Namespace prefix validation** — Plugin IDs without a namespace prefix (e.g. `java:`, `py:`) now fail fast with a clear error instead of silently dropping edges.
- **BlindSpot equality** — `BlindSpot` instances can be collected in `Set` and `Map` (added `equals()`/`hashCode()`).
- **DeclarationGraphBuilder** — Shared utility for converting declaration lists into a `DependencyGraph`. Warnings go into the result object instead of `System.err`.
- **Tests** — 233 tests passing. Added `DeclarationGraphBuilderTest` (8 tests), `DependencyGraphTest` additions for `mergeInto`/`knownNodeIds`, `ParseResultTest` additions for null graph contract. `EdgeSkipWarningTest` now verifies warnings via parse errors instead of stderr capture.

### Changed
- **ParseOrchestrator** collects edge-skip warnings in the result's error list instead of printing to `System.err`. Easier to test, easier to consume.
- **DiffCommand** uses the shared `DeclarationGraphBuilder` instead of its own duplicated conversion logic (~40 lines removed).
- **JavaParserPlugin** uses `DeclarationGraphBuilder`, removing its private `buildGraphFromDeclarations` method.
- **ModuleDetector** moved from `archon-java` to `archon-core/util`. The CLI no longer has a compile-time dependency on the Java plugin (changed to `runtimeOnly`), and `AnalysisPipeline` no longer needs reflection to access it.

### Removed
- **DomainStrategy interface** and all implementations (`JavaDomainStrategy`, `JsDomainStrategy`, `PythonDomainStrategy`). Dead code that was never called in the production pipeline.
- **GraphBuilder** class. Dead code, superseded by `DependencyGraph.MutableBuilder`.
- **Duplicate enum types** in the `plugin` package. Now mapped through shared `DeclarationGraphBuilder` enum mappers so plugin code uses its own enums and the builder translates.

## [0.6.0.1] - 2026-04-13

### Added
- **Self-contained update check** — The Archon skill now checks for version drift automatically on every invocation
  - Detects JAR version mismatch (VERSION file vs cached jarPath)
  - Checks GitHub remote for newer releases (two-phase cache: 60min/720min TTL)
  - Verifies JDK 17 is still available
  - Snooze with escalating backoff (24h, 48h, 7d) when updates are deferred
  - All state in `~/.archon/` with zero external dependencies
- **`/archon upgrade`** — Pull skill file updates from GitHub via reverse inheritance

### Changed
- **skill.md** updated: removed stale `~/.gstack/` references, added update check and upgrade docs
- **.gitignore** updated: `.claude/skills/archon/` is now tracked for reverse inheritance

## [0.6.0.0] - 2026-04-13

### Added
- **Claude Code Skill** — Archon now ships a native AI agent integration at `~/.claude/skills/archon/`
  - `/archon diff` — Blast radius analysis of uncommitted changes with P0/P1/P2 impact tiers
  - `/archon analyze` — Full dependency map with hotspots, cycles, and blind spot reporting
  - `/archon setup` — One-time JDK 17 detection and shadow JAR bootstrap
  - Cross-platform JDK 17 auto-detection (Windows/macOS/Linux) with result caching
  - Auto-build fallback: finds or builds shadow JAR transparently
  - Structured JSON → Markdown report pipeline for AI consumption
- **Impact propagation engine** — BFS-based transitive dependency analysis
  - P0 (direct): files you actually changed
  - P1 (transitive): modules that depend on what you changed
  - P2 (domain): same-domain modules that may be affected
  - Hotspot involvement detection (PageRank, bridge node warnings)
  - Cross-domain dependency warnings
- **Blind spot reporting** — Explicit declaration of what Archon cannot detect
  - Reflection-based calls (Class.forName, method.invoke)
  - Dynamic imports (import(), importlib)
  - Spring bean injection (@Autowired)
  - Event-driven coupling

### Changed
- **skill.md** rewritten from generic AI agent guide to actual Claude Code skill documentation
- **README.md** updated: added Java 17 prerequisite, corrected CLI commands to match actual implementation, added Claude Code Integration section
- **README-zh.md** parallel Chinese updates for Quick Start, CLI commands, and skill integration
- Removed references to unimplemented features (.archon.yml config, `check --ci` command)
- TODOS.md: AnalyzeCommand --json officially deferred (view --format json covers all use cases)

## [0.5.0.0] - 2026-04-13

### Added
- **Web Visualization** — `archon view` command with interactive DOM+SVG dependency graph viewer
  - Two-mode navigation: domain overview (UML package grid) → module drill-down (boundary container)
  - Impact analysis overlay: hover any class to see P0/P1/P2 blast radius with BFS propagation
  - Detail panel with metrics (PageRank, betweenness, closeness), badges (hotspot, bridge), and blast radius breakdown
  - Draggable class nodes with socket system for cross-boundary edge connections
  - SVG arrow rendering with L-shape polyline routing
  - Static HTML export works offline, JSON output for programmatic access
  - Mermaid and DOT export formats for external tools
- **Left sidebar navigation** with domain hierarchy and hotspot indicators
- **Web Diff Viewer** — `archon diff --view` opens browser with red/green/yellow diff visualization
- **Mermaid Export** — `archon analyze --mermaid diagram.mmd` exports to Mermaid flowchart format
- **Terminal Visualization** — Structured text output with domain grouping and formatting
- **Idle Timeout** — Web server auto-stops after 30 minutes of inactivity (configurable via `--idle-timeout`)
- **Shared Analysis Pipeline** — Extracted AnalysisPipeline for reuse across commands
- **New Module: archon-viz** — Visualization module with JSON serializers and web server

### Changed
- Rendering layer rewritten from canvas (d3 + dagre-d3) to DOM+SVG for reliable interactive visualization
- ViewServer binds to 127.0.0.1 only (security: never 0.0.0.0)
- DotExporter reimplemented using centralized class in archon-core
- Port auto-selection now works correctly in all scenarios (8420-8430 fallback)
- Design system: Geist font stack, sky blue accent (#38bdf8), no ambient animations

### Fixed
- Interactive viewer rendering issues resolved by replacing canvas with DOM+SVG
- ViewServer port bug: browser now opens correct URL when auto-port selection happens
- ViewServer: proper request tracking for idle timeout functionality
- ViewCommand: server no longer starts when `--format json` is specified

### Removed
- Canvas renderer (graphCanvas.ts), d3, and dagre-d3 dependencies

### Acknowledgments
- The web viewer adapts the approach of [oh-my-mermaid](https://github.com/oh-my-mermaid/oh-my-mermaid) (MIT licensed)

## [0.4.0.3] - 2026-04-04

### Fixed
- `archon impact` command now works for TypeScript/Python modules, not just Java classes
- Module resolution supports path-based matching (slashes) alongside Java FQCN matching (dots)
- Namespace prefixes (js:, py:, java:) correctly stripped before suffix matching
- Error message changed from "class not found" to "module not found" for multi-language clarity

## [0.4.0.2] - 2026-04-04

### Fixed
- Gradle multi-module project detection now correctly identifies root-level projects with settings.gradle
- JavaParser configured to Java 17 language level for better syntax support (records, switch expressions)
- Edge-adding bug in JavaPlugin fixed — now checks node existence before creating edges
- Shadow JAR version reading improved — VERSION file now read dynamically instead of hardcoding

### Technical
- JavaParser updated to 3.28+ for improved Java syntax parsing
- ModuleDetector enhanced with 80+ lines of multi-module Gradle project detection logic

## [0.4.0.1] - 2026-04-03

### Added
- GitHub Actions CI/CD workflows (build.yml, release.yml)
- Automated testing on push and pull requests
- Automated GitHub releases on version tags

### Fixed
- Gradle wrapper execute permission in CI workflows

## [0.4.0.0] - 2026-04-03

### Added
- Namespace collision detection - throws exception on conflicting unprefixed IDs
- File size validation before parsing (1MB limit enforced at file system level)
- Default reset() method in LanguagePlugin interface for state cleanup between runs
- Validation for malformed namespace prefixes (empty prefix, trailing colon)

### Fixed
- Namespace collision now throws exception instead of silently dropping nodes (prevents data loss)
- File size check moved before content reading (prevents OOM on large files)
- Plugin reset now called on all plugins via interface method (not just JavaPlugin)
- Malformed namespace prefixes now rejected with clear error messages

## [0.3.0.0] - 2026-04-03

### Added
- Multi-language support via ServiceLoader-based LanguagePlugin SPI
- JavaScript/TypeScript parser plugin using Google Closure Compiler
- Vue Single File Component (.vue) support with <script> extraction
- ParseOrchestrator for two-phase multi-plugin graph construction
- Namespace prefixing (java:, js:) for multi-language node isolation
- JsDomainStrategy for package.json workspace detection
- ModulePathResolver for ES module import resolution
- VueFileExtractor for Vue SFC script section extraction
- SpiComplianceTest for SPI contract validation
- PluginDiscoverer for automatic LanguagePlugin discovery

### Changed
- JavaParserPlugin refactored as JavaPlugin implementing LanguagePlugin
- DomainStrategy now optional via Optional<> return type
- ParseResult includes DependencyGraph and uses sourceModules (language-agnostic)
- CLI commands now use ParseOrchestrator for unified multi-language parsing
- BlindSpot moved to com.archon.core.plugin package

### Fixed
- Edge loss in multi-plugin graphs via two-phase construction
- Namespace collision risk via language prefixing
- External class pollution handled via namespace filtering
- ES module import extraction now functional via regex-based parsing (JsAstVisitor)
- archon-js module now included in CLI shadow JAR for runtime discovery

### Technical
- Added archon-js Gradle module with Closure Compiler dependency
- ServiceLoader discovery via META-INF/services/com.archon.core.plugin.LanguagePlugin
- Closure Compiler validation for type-only import handling (workaround documented)
- Namespace prefix stripping in ParseOrchestrator for unified graph building

## [0.1.1] - 2026-04-02

### Added
- External class filtering for hotspot analysis
- Adaptive thresholds based on project size
- Smarter domain detection with configurable depth

### Changed
- Output format reduced verbosity for large projects
- Domain segmentation over-segmentation fixed

## [0.1.0] - 2026-04-01

### Added
- Initial release with Java dependency analysis
- CLI commands: analyze, impact, check
- Cycle detection, hotspot analysis, domain detection
- Blind spot detection for dynamic patterns

