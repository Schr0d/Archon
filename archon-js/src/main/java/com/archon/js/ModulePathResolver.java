package com.archon.js;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves JavaScript/TypeScript module import paths to absolute file paths.
 *
 * <p>Handles:
 * <ul>
 *   <li>Relative imports: ./foo, ../bar</li>
 *   <li>Barrel files: ./utils → ./utils/index.ts</li>
 *   <li>Path aliases: @/foo from tsconfig.json</li>
 *   <li>External packages: react, lodash - marked as external (returns null)</li>
 * </ul>
 */
public class ModulePathResolver {

    private final Map<String, String> pathAliases = new HashMap<>();

    /**
     * Resolves an import path to an absolute file path.
     *
     * @param importPath The import statement (e.g., "./foo", "react", "@/utils")
     * @param fromFile The absolute path of the file containing the import
     * @param sourceRoot The project source root directory
     * @return Optional containing the resolved absolute path, or empty if external package
     */
    public Optional<Path> resolve(String importPath, String fromFile, Path sourceRoot) {
        if (importPath == null || importPath.isBlank()) {
            return Optional.empty();
        }

        // Check for external packages (no relative prefix, not an alias)
        if (isExternalPackage(importPath)) {
            return Optional.empty();
        }

        // Handle path aliases (e.g., @/foo, @components/bar)
        if (isPathAlias(importPath)) {
            return resolvePathAlias(importPath, sourceRoot);
        }

        // Handle relative imports (./foo, ../bar)
        if (isRelativeImport(importPath)) {
            return resolveRelativeImport(importPath, fromFile);
        }

        // Absolute path (shouldn't normally happen in ES modules)
        return Optional.of(Path.of(importPath));
    }

    /**
     * Adds a path alias mapping from tsconfig.json.
     *
     * @param alias The alias prefix (e.g., "@", "@components")
     * @param target The target path (e.g., "src", "src/components")
     */
    public void addPathAlias(String alias, String target) {
        pathAliases.put(alias, target);
    }

    /**
     * Checks if an import path is an external package.
     * External packages have no relative prefix and are not registered aliases.
     */
    private boolean isExternalPackage(String importPath) {
        return !importPath.startsWith("./") &&
               !importPath.startsWith("../") &&
               !isPathAlias(importPath) &&
               !importPath.startsWith("/");
    }

    /**
     * Checks if an import path uses a registered path alias.
     */
    private boolean isPathAlias(String importPath) {
        return pathAliases.keySet().stream()
            .anyMatch(alias -> importPath.startsWith(alias + "/") || importPath.equals(alias));
    }

    /**
     * Checks if an import path is a relative import.
     */
    private boolean isRelativeImport(String importPath) {
        return importPath.startsWith("./") || importPath.startsWith("../");
    }

    /**
     * Resolves a path alias to an absolute path.
     */
    private Optional<Path> resolvePathAlias(String importPath, Path sourceRoot) {
        // Find the matching alias (longest match first)
        String matchedAlias = null;
        for (String alias : pathAliases.keySet()) {
            if (importPath.startsWith(alias + "/") || importPath.equals(alias)) {
                if (matchedAlias == null || alias.length() > matchedAlias.length()) {
                    matchedAlias = alias;
                }
            }
        }

        if (matchedAlias == null) {
            return Optional.empty();
        }

        String target = pathAliases.get(matchedAlias);
        String remainingPath = importPath.substring(matchedAlias.length());

        // Strip leading slash from remaining path if present
        if (remainingPath.startsWith("/")) {
            remainingPath = remainingPath.substring(1);
        }

        // Construct the full path
        Path resolvedPath = sourceRoot.resolve(target).resolve(remainingPath);

        // Check for barrel file (index.ts, index.js, etc.)
        return checkForBarrelFile(resolvedPath);
    }

    /**
     * Resolves a relative import to an absolute path.
     */
    private Optional<Path> resolveRelativeImport(String importPath, String fromFile) {
        try {
            Path fromDir = Path.of(fromFile).getParent();
            Path resolvedPath = fromDir.resolve(importPath).normalize();

            // Check for barrel file (index.ts, index.js, etc.)
            return checkForBarrelFile(resolvedPath);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Checks if the resolved path points to a directory and looks for a barrel file.
     * If it's a directory, appends index.ts, index.js, index.jsx, or index.tsx.
     * If it's a file with extension, strips the extension for module ID purposes.
     */
    private Optional<Path> checkForBarrelFile(Path path) {
        if (Files.isDirectory(path)) {
            // Check for barrel files in order of preference
            Path indexTs = path.resolve("index.ts");
            Path indexTsx = path.resolve("index.tsx");
            Path indexJs = path.resolve("index.js");
            Path indexJsx = path.resolve("index.jsx");

            if (Files.exists(indexTs)) {
                return Optional.of(indexTs);
            } else if (Files.exists(indexTsx)) {
                return Optional.of(indexTsx);
            } else if (Files.exists(indexJs)) {
                return Optional.of(indexJs);
            } else if (Files.exists(indexJsx)) {
                return Optional.of(indexJsx);
            }
            // Directory exists but no barrel file found - return the directory path
            return Optional.of(path);
        }

        // It's a file path - check if it exists
        if (Files.exists(path)) {
            return Optional.of(path);
        }

        // File doesn't exist - might be a module without extension
        // Try common extensions
        for (String ext : new String[]{".ts", ".tsx", ".js", ".jsx"}) {
            Path withExt = path.resolveSibling(path.getFileName() + ext);
            if (Files.exists(withExt)) {
                return Optional.of(withExt);
            }
        }

        // Return the path as-is (might not exist yet)
        return Optional.of(path);
    }

    /**
     * Extracts a module ID from an absolute path.
     * Strips the source root and file extension.
     *
     * @param absolutePath The absolute path to the module
     * @param sourceRoot The project source root
     * @return The module ID (e.g., "components/Button" from "/project/src/components/Button.tsx")
     */
    public static String extractModuleId(Path absolutePath, Path sourceRoot) {
        try {
            Path relative = sourceRoot.relativize(absolutePath);
            String pathStr = relative.toString()
                .replace(".jsx", "")  // Replace longer extensions first
                .replace(".tsx", "")
                .replace(".js", "")
                .replace(".ts", "")
                .replace("\\", "/");

            // Handle index files - use parent directory name
            if (pathStr.endsWith("/index")) {
                pathStr = pathStr.substring(0, pathStr.length() - 6);
            }

            return pathStr;
        } catch (Exception e) {
            return absolutePath.toString()
                .replace(".jsx", "")  // Replace longer extensions first
                .replace(".tsx", "")
                .replace(".js", "")
                .replace(".ts", "")
                .replace("\\", "/");
        }
    }
}
