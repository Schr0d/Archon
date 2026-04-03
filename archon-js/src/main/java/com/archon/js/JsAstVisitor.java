package com.archon.js;

import com.archon.core.plugin.BlindSpot;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Walks Closure Compiler AST to extract ES module dependencies.
 *
 * <p>Detects:
 * <ul>
 *   <li>Static imports: import { X } from './y'</li>
 *   <li>Type-only imports: import type { X } from './y' (via regex fallback)</li>
 *   <li>Re-exports: export { X } from './y'</li>
 *   <li>Dynamic imports: import(path) - reported as blind spot</li>
 * </ul>
 */
public class JsAstVisitor {

    private static final Pattern IMPORT_PATTERN = Pattern.compile(
        "import\\s+(?:(type)\\s+)?(?:\\{[^}]*\\}|\\*\\s+as\\s+\\w+|\\w+)(?:\\s*,\\s*(?:\\{[^}]*\\}|\\*\\s+as\\s+\\w+|\\w+))*\\s+from\\s+['\"]([^'\"]+)['\"]"
    );
    private static final Pattern REEXPORT_PATTERN = Pattern.compile(
        "export\\s+(?:\\{[^}]*\\}|\\*\\s+as\\s+\\w+)\\s+from\\s+['\"]([^'\"]+)['\"]"
    );
    private static final Pattern DYNAMIC_IMPORT_PATTERN = Pattern.compile(
        "import\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)"
    );
    private static final Pattern REQUIRE_PATTERN = Pattern.compile(
        "(?:require|define)\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)"
    );

    private final ModulePathResolver pathResolver;

    public JsAstVisitor() {
        this.pathResolver = new ModulePathResolver();
    }

    /**
     * Result of AST walk containing extracted dependencies.
     */
    public record VisitResult(
        Set<String> modules,
        List<ImportInfo> imports,
        List<BlindSpot> blindSpots,
        String moduleType
    ) {}

    /**
     * Information about a single import statement.
     */
    public record ImportInfo(
        String fromModule,
        String resolvedPath,
        String importType  // IMPORTS, TYPE_IMPORT, REEXPORTS
    ) {}

    /**
     * Extract dependencies from source content.
     * Uses regex-based extraction for ES modules and CommonJS.
     * Full Closure Compiler AST walking is deferred due to type-only import limitations.
     *
     * @param content Source file content
     * @param filePath Path to the source file
     * @param sourceRoot Project source root
     * @return VisitResult with extracted modules, imports, and blind spots
     */
    public VisitResult extractDependencies(
        String content,
        String filePath,
        Path sourceRoot
    ) {
        Set<String> modules = new LinkedHashSet<>();
        List<ImportInfo> imports = new ArrayList<>();
        List<BlindSpot> blindSpots = new ArrayList<>();

        // Extract module name from file path
        String moduleName = extractModuleName(filePath, sourceRoot);
        modules.add(moduleName);

        // Extract ES module imports
        Matcher importMatcher = IMPORT_PATTERN.matcher(content);
        while (importMatcher.find()) {
            String isTypeOnly = importMatcher.group(1);
            String rawPath = importMatcher.group(2);
            String importType = (isTypeOnly != null) ? "TYPE_IMPORT" : "IMPORTS";

            var resolved = pathResolver.resolve(rawPath, filePath, sourceRoot);
            if (resolved.isPresent()) {
                // Convert to namespace-prefixed module ID
                String targetModule = pathToModule(resolved.get(), sourceRoot);
                imports.add(new ImportInfo(moduleName, targetModule, importType));
            }
            // External packages are skipped
        }

        // Extract re-exports
        Matcher reexportMatcher = REEXPORT_PATTERN.matcher(content);
        while (reexportMatcher.find()) {
            String rawPath = reexportMatcher.group(1);
            var resolved = pathResolver.resolve(rawPath, filePath, sourceRoot);
            if (resolved.isPresent()) {
                String targetModule = pathToModule(resolved.get(), sourceRoot);
                imports.add(new ImportInfo(moduleName, targetModule, "REEXPORTS"));
            }
        }

        // Extract dynamic imports (blind spots)
        Matcher dynamicMatcher = DYNAMIC_IMPORT_PATTERN.matcher(content);
        while (dynamicMatcher.find()) {
            String rawPath = dynamicMatcher.group(1);
            blindSpots.add(new BlindSpot(
                "DynamicImport",
                moduleName,
                "Dynamic import: " + rawPath
            ));
        }

        // Extract CommonJS require (blind spots)
        Matcher requireMatcher = REQUIRE_PATTERN.matcher(content);
        while (requireMatcher.find()) {
            String rawPath = requireMatcher.group(1);
            blindSpots.add(new BlindSpot(
                "CommonJS",
                moduleName,
                "CommonJS require: " + rawPath
            ));
        }

        return new VisitResult(
            modules,
            imports,
            blindSpots,
            detectModuleType(filePath)
        );
    }

    /**
     * Extract module name from file path.
     */
    private String extractModuleName(String filePath, Path sourceRoot) {
        try {
            Path relative = sourceRoot.relativize(Path.of(filePath));
            return relative.toString()
                .replace(".js", "")
                .replace(".jsx", "")
                .replace(".ts", "")
                .replace(".tsx", "")
                .replace("\\", "/");
        } catch (Exception e) {
            return filePath;
        }
    }

    /**
     * Convert resolved file path to module ID.
     */
    private String pathToModule(Path path, Path sourceRoot) {
        try {
            Path absolute = path.toAbsolutePath();
            Path relative = sourceRoot.toAbsolutePath().relativize(absolute);
            return "js:" + relative.toString()
                .replace(".js", "")
                .replace(".jsx", "")
                .replace(".ts", "")
                .replace(".tsx", "")
                .replace("\\", "/");
        } catch (Exception e) {
            return "js:" + path;
        }
    }

    /**
     * Detect module type based on file extension.
     */
    private String detectModuleType(String filePath) {
        if (filePath.endsWith(".ts") || filePath.endsWith(".tsx")) {
            return "typescript";
        }
        if (filePath.endsWith(".vue")) {
            return "vue";
        }
        return "javascript";
    }
}
