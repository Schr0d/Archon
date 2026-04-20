package com.archon.python;

import com.archon.core.plugin.BlindSpot;
import com.archon.core.plugin.Confidence;
import com.archon.core.plugin.DependencyDeclaration;
import com.archon.core.plugin.EdgeType;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ModuleDeclaration;
import com.archon.core.plugin.NodeType;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * LanguagePlugin implementation for Python.
 *
 * <p>Uses regex-based parsing for Python import statements. Supports:
 * <ul>
 *   <li>Simple imports: {@code import foo}</li>
 *   <li>Imports with aliases: {@code import foo as bar}</li>
 *   <li>From imports: {@code from foo import bar}</li>
 *   <li>Relative imports: {@code from . import sibling}, {@code from .. import parent}</li>
 *   <li>Type stub files: .pyi files</li>
 * </ul>
 *
 * <p>Filters standard library modules from the dependency graph.
 *
 * <p>Node IDs use "py:" namespace prefix (e.g., "py:src.utils.helpers").
 */
public class PythonPlugin implements LanguagePlugin {

    private static final String NAMESPACE = "py";
    private static final Set<String> EXTENSIONS = Set.of("py", "pyi", "pyw");

    // Maximum file size to parse - prevents OOM on malformed files
    private static final long MAX_FILE_SIZE = ParseContext.MAX_FILE_SIZE;

    private final PythonModuleResolver moduleResolver;

    // Cache: parent directory → computed Python source root (avoids repeated filesystem checks)
    private final Map<Path, Path> sourceRootCache = new HashMap<>();

    public PythonPlugin() {
        this.moduleResolver = new PythonModuleResolver();
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    @Override
    public ParseResult parseFromContent(
        String filePath,
        String content,
        ParseContext context
    ) {
        List<String> parseErrors = new ArrayList<>();
        List<BlindSpot> blindSpots = new ArrayList<>();
        Set<String> sourceModules = new HashSet<>();
        List<ModuleDeclaration> moduleDecls = new ArrayList<>();
        List<DependencyDeclaration> depDecls = new ArrayList<>();

        // Check file size to prevent OOM on malformed inputs
        if (content.length() > MAX_FILE_SIZE) {
            parseErrors.add(filePath + ":0 - File too large to parse (" +
                (content.length() / 1024) + " KB, max " + (MAX_FILE_SIZE / 1024) + " KB)");
            return new ParseResult(
                sourceModules,
                blindSpots,
                parseErrors,
                moduleDecls,
                depDecls
            );
        }

        try {
            // Detect Python source root (handles src/ layout vs flat layout)
            Path pythonSourceRoot = findPythonSourceRoot(filePath, context.getSourceRoot());

            // Extract module name from file path
            String moduleName = extractModuleName(filePath, pythonSourceRoot);
            String prefixedId = NAMESPACE + ":" + moduleName;
            sourceModules.add(prefixedId);

            // Collect module declaration
            moduleDecls.add(new ModuleDeclaration(
                prefixedId,
                NodeType.MODULE,
                filePath,
                Confidence.HIGH
            ));

            // Extract imports using PythonImportExtractor
            Set<String> imports = PythonImportExtractor.extractImports(content);

            // Collect dependency declarations for each import
            for (String importModule : imports) {
                String targetModuleName = importModule;
                String evidence = importModule;

                // Check if it's a relative import (starts with dots)
                if (importModule.startsWith(".")) {
                    // Resolve relative import
                    String currentPackage = extractPackage(filePath, pythonSourceRoot);
                    Optional<String> resolved = moduleResolver.resolve(
                        importModule,
                        currentPackage,
                        pythonSourceRoot.toString()
                    );

                    if (resolved.isPresent()) {
                        targetModuleName = resolved.get();
                    } else {
                        // Relative import resolution failed — report as blind spot
                        blindSpots.add(new BlindSpot(
                            "RelativeImportNotFound",
                            moduleName,
                            "Could not resolve relative import: " + importModule
                        ));
                        continue; // Skip adding edge
                    }
                }

                // Filter out stdlib modules
                if (PythonStdlib.isStdlib(targetModuleName)) {
                    continue;
                }

                // Add namespace prefix to target
                // Convert dot notation (from imports) to slash notation (to match node IDs from file paths)
                String targetId = NAMESPACE + ":" + targetModuleName.replace(".", "/");

                depDecls.add(new DependencyDeclaration(
                    prefixedId,
                    targetId,
                    EdgeType.IMPORTS,
                    Confidence.HIGH,
                    evidence,
                    false
                ));
            }

        } catch (Exception e) {
            parseErrors.add(filePath + ":0 - Failed to parse: " + e.getMessage());
        }

        return new ParseResult(
            sourceModules,
            blindSpots,
            parseErrors,
            moduleDecls,
            depDecls
        );
    }

    /**
     * Detect the Python source root for a file by walking up from its parent
     * directory, following __init__.py chains. The directory just above the
     * topmost __init__.py is the Python package root.
     *
     * <p>Examples:
     * <ul>
     *   <li>src/playbuddy/game/x.py → game/ has __init__.py, playbuddy/ has __init__.py,
     *       src/ does NOT → returns src/</li>
     *   <li>playbuddy/game/x.py → returns projectRoot (flat layout)</li>
     *   <li>main.py → returns projectRoot (no package)</li>
     * </ul>
     *
     * @param filePath the Python file path
     * @param projectRoot the project root directory
     * @return the Python source root (package root for this file)
     */
    private Path findPythonSourceRoot(String filePath, Path projectRoot) {
        try {
            Path parentDir = Path.of(filePath).toAbsolutePath().getParent();
            Path absoluteProjectRoot = projectRoot.toAbsolutePath();

            if (parentDir == null || !parentDir.startsWith(absoluteProjectRoot)) {
                return projectRoot;
            }

            // Check cache
            Path cached = sourceRootCache.get(parentDir);
            if (cached != null) {
                return cached;
            }

            // Walk up from parent, following __init__.py chains
            Path lastWithInit = null;
            Path dir = parentDir;
            while (dir != null && dir.startsWith(absoluteProjectRoot) && !dir.equals(absoluteProjectRoot)) {
                if (Files.exists(dir.resolve("__init__.py"))) {
                    lastWithInit = dir;
                    dir = dir.getParent();
                } else {
                    break;
                }
            }

            // The Python source root is the parent of the topmost directory with __init__.py
            Path pythonSourceRoot = (lastWithInit != null) ? lastWithInit.getParent() : projectRoot;

            sourceRootCache.put(parentDir, pythonSourceRoot);
            return pythonSourceRoot;
        } catch (Exception e) {
            return projectRoot;
        }
    }

    /**
     * Extract module name from file path relative to the Python source root.
     *
     * <p>For src/ layout: src/playbuddy/game/x.py → playbuddy/game/x
     * <p>For flat layout: playbuddy/game/x.py → playbuddy/game/x
     *
     * @param filePath the full path to the Python file
     * @param sourceRoot the Python source root (detected via __init__.py chains)
     * @return the module name (without namespace prefix)
     */
    private String extractModuleName(String filePath, Path sourceRoot) {
        try {
            Path absolutePath = Path.of(filePath).toAbsolutePath();
            Path relativePath = sourceRoot.toAbsolutePath().relativize(absolutePath);
            String moduleName = relativePath.toString()
                .replace(".py", "")
                .replace(".pyi", "")
                .replace(".pyw", "")
                .replace("\\", "/");

            // Ensure we don't return an empty module name
            if (moduleName == null || moduleName.trim().isEmpty()) {
                throw new IllegalArgumentException("Module name is empty after relativization");
            }

            return moduleName;
        } catch (Exception e) {
            // Fallback to filename only
            String fileName = Path.of(filePath).getFileName().toString();
            String moduleName = fileName.replaceAll("\\.(py|pyi|pyw)$", "");

            // Final fallback: use a default name if still empty
            if (moduleName == null || moduleName.trim().isEmpty()) {
                moduleName = "unknown_module";
            }

            return moduleName;
        }
    }

    /**
     * Extract package name from file path for relative import resolution.
     *
     * @param filePath the full path to the Python file
     * @param sourceRoot the source root directory
     * @return the package name (e.g., "playbuddy.game" or "" for root)
     */
    private String extractPackage(String filePath, Path sourceRoot) {
        try {
            Path absolutePath = Path.of(filePath).toAbsolutePath();
            Path relativePath = sourceRoot.toAbsolutePath().relativize(absolutePath);

            // Remove filename and extension, keep directory path
            Path dirPath = relativePath.getParent();
            if (dirPath == null) {
                return ""; // Root level
            }

            // Convert path separators to dots for package name
            return dirPath.toString().replace("\\", ".").replace("/", ".");
        } catch (Exception e) {
            return ""; // Fallback to empty
        }
    }

    /**
     * Reset the internal state. Should be called before parsing a new project.
     */
    @Override
    public void reset() {
        moduleResolver.reset();
        sourceRootCache.clear();
    }
}
