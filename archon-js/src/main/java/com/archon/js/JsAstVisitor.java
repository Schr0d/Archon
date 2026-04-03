package com.archon.js;

import com.archon.core.plugin.BlindSpot;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.rhino.Node;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Walks Closure Compiler AST to extract ES module dependencies.
 *
 * <p>TODO: Full implementation in Task 3.3.
 * This is a stub to allow JsPlugin to compile.
 *
 * <p>Will detect:
 * <ul>
 *   <li>Static imports: import { X } from './y'</li>
 *   <li>Type-only imports: import type { X } from './y'</li>
 *   <li>Re-exports: export { X } from './y'</li>
 *   <li>Dynamic imports: import(path) - reported as blind spot</li>
 * </ul>
 */
public class JsAstVisitor {

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
     * Extract dependencies from Closure Compiler AST.
     *
     * @param root Root node from compiler.getRoot()
     * @param filePath Path to the source file
     * @param sourceRoot Project source root
     * @return VisitResult with extracted modules, imports, and blind spots
     */
    public VisitResult extractDependencies(
        Node root,
        String filePath,
        Path sourceRoot
    ) {
        Set<String> modules = new LinkedHashSet<>();
        List<ImportInfo> imports = new ArrayList<>();
        List<BlindSpot> blindSpots = new ArrayList<>();

        // Extract module name from file path
        String moduleName = extractModuleName(filePath, sourceRoot);
        modules.add(moduleName);

        // TODO: Walk AST and extract imports (Task 3.3)
        blindSpots.add(new BlindSpot(
            "NotImplemented",
            moduleName,
            "JsAstVisitor.extractDependencies not yet implemented - see Task 3.3"
        ));

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
     * Detect module type based on file extension.
     */
    private String detectModuleType(String filePath) {
        if (filePath.endsWith(".ts") || filePath.endsWith(".tsx")) {
            return "typescript";
        }
        return "javascript";
    }
}
