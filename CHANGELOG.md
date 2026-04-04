# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0.1] - 2026-04-03

### Added
- GitHub Actions CI/CD workflows (build.yml, release.yml)
- Automated testing on push and pull requests
- Automated GitHub releases on version tags

### Fixed
- Gradle wrapper execute permission in CI workflows

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
