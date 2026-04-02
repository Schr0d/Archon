# Java Parser Plugin Design Spec

Date: 2026-04-02
Module: archon-java
Plan Reference: IMPLEMENTATION_PLAN_v0.1.md Step 7

## Goal

Parse Java source trees into DependencyGraph instances using JavaParser with symbol solving. Extract class-level nodes, dependency edges, and blind spots from real Java projects.

## Components

### 1. JavaParserPlugin (orchestrator)

Entry point. Takes a project root path and ArchonConfig, returns ParseResult.

```
parse(projectRoot, config) → ParseResult
  1. ModuleDetector.detectModules(projectRoot) → source roots
  2. For each .java file in source roots:
     a. Parse with JavaParser → CompilationUnit
     b. AstVisitor.visit(cu, graphBuilder) → adds nodes + edges
     c. SymbolSolverAdapter.solve(cu) → call edges (optional enhancement)
     d. On error: skip file, add ParseError
  3. BlindSpotDetector.detect(sourcePath) → blind spots
  4. Build and return ParseResult(graph, blindSpots, errors)
```

ParseResult contains: DependencyGraph graph, List<BlindSpot> blindSpots, List<ParseError> errors.

### 2. AstVisitor

Walks a JavaParser CompilationUnit. Extracts:
- Class/interface/enum declarations → Node (type=CLASS, id=FQCN)
- Import statements → Edge (IMPORTS, HIGH confidence)
- extends clauses → Edge (EXTENDS, HIGH confidence)
- implements clauses → Edge (IMPLEMENTS, HIGH confidence)
- Method calls (when symbol solved) → Edge (CALLS, confidence from solver)

Signature changes from stub: `visit(CompilationUnit cu, GraphBuilder graphBuilder)` — uses proper JavaParser types, not Object.

### 3. ImportResolver

Resolves import declarations to fully qualified class names.
- Regular imports: `com.fuwa.system.domain.SysUser` → exact FQCN
- Wildcard imports: `com.fuwa.system.domain.*` → resolve against available classes
- Static imports: `com.fuwa.framework.util.ShiroUtils.getSysUser` → resolve to declaring class

Returns Optional<String> (empty if unresolved).

### 4. SymbolSolverAdapter

Per-file symbol solving. Wraps JavaParser's SymbolSolver.
- Configure solver with project source roots + classpath
- Per-file timeout: 5 seconds
- On success: returns true, caller can extract CALLS edges with HIGH confidence
- On failure: returns false, fall back to import-level MEDIUM confidence edges

### 5. ModuleDetector

Auto-detects Maven/Gradle multi-module project structure.
- Scan for pom.xml with <modules> → extract module names + source roots
- Scan for build.gradle with subprojects → extract module names + source roots
- For each module: locate src/main/java
- If no build files found: treat entire project as single module, use src/main/java as root

Returns List<SourceRoot> where SourceRoot has moduleName + Path to source directory.

### 6. BlindSpotDetector

Two-phase detection:

**Phase 1: AST pattern matching** — scan parsed CompilationUnits for:
- `Class.forName(...)` / `Class.forName` → reflection
- `Method.invoke(...)` / `.getDeclaredMethod(...)` → reflection
- `@EventListener` / `@Subscribe` → event-driven
- `ApplicationEventPublisher.publishEvent(...)` → event-driven
- `Proxy.newProxyInstance(...)` → dynamic-proxy

**Phase 2: File scan** — glob for MyBatis XML:
- `**/*Mapper.xml` → blind spot per mapper file (type=mybatis-xml)
- `**/*Dao.xml` → blind spot per DAO file

All flagged with confidence LOW.

## Data Flow

```
projectRoot
  → ModuleDetector → List<SourceRoot>
  → for each SourceRoot:
      for each .java file:
        JavaParser.parse(file) → CompilationUnit
        → AstVisitor.visit(cu, graphBuilder)
        → SymbolSolverAdapter.solve(cu)
        → on error: ParseError
  → BlindSpotDetector.detect(projectRoot) → List<BlindSpot>
  → graphBuilder.build() → DependencyGraph
  → ParseResult(graph, blindSpots, errors)
```

## Error Handling

- Parse failure on single file: skip file, add ParseError, continue
- Symbol solver timeout: fall back to import-only analysis
- No Java files found: return empty graph with no errors
- Invalid project root: throw exception (caller handles)

## Testing Strategy

- Unit tests per component using small Java fixture strings
- AstVisitor: test with hand-crafted CompilationUnit objects
- ImportResolver: test regular, wildcard, static imports
- ModuleDetector: test with temp directory structures (pom.xml, build.gradle)
- BlindSpotDetector: test AST patterns + MyBatis XML file scan
- Integration: JavaParserPlugin end-to-end on multi-file temp project

## Confidence Model

| Source | Confidence | Edge Type |
|--------|-----------|-----------|
| Symbol-solved method call | HIGH | CALLS |
| Import statement | HIGH | IMPORTS |
| extends/implements | HIGH | EXTENDS/IMPLEMENTS |
| Import-level fallback (no symbol solving) | MEDIUM | IMPORTS |
| Blind spot detection | LOW | N/A (BlindSpot) |

## Dependencies

- archon-core (project dependency)
- com.github.javaparser:javaparser-symbol-solver-core:3.26.+

## Out of Scope (v0.1)

- Method-level granularity (deferred to v0.2)
- Lombok code generation handling
- Spring annotation-based dependency injection (future blind spot)
- Gradle Kotlin DSL build file parsing (Maven pom.xml only for v0.1)
