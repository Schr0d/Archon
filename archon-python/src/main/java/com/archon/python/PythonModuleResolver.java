package com.archon.python;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves Python relative import paths to fully qualified module names.
 *
 * <p>Handles relative imports like:
 * <ul>
 *   <li>{@code from . import sibling} → current package + "sibling"</li>
 *   <li>{@code from .. import parent} → parent package + "parent"</li>
 *   <li>{@code from ... import grandparent} → grandparent package + "grandparent"</li>
 *   <li>{@code from .sub import func} → current package + "sub"</li>
 * </ul>
 *
 * <p>Performs filesystem validation to ensure the target module exists.
 * Uses ancestral search to find common test directories at parent levels.
 */
public class PythonModuleResolver {

    private Path sourceRoot;

    /**
     * Sets the source root for filesystem resolution.
     *
     * @param sourceRoot the root directory of the Python project
     */
    public void setSourceRoot(Path sourceRoot) {
        this.sourceRoot = sourceRoot;
    }

    /**
     * Resolves a relative import to a fully qualified module name.
     *
     * <p>Resolution process:
     * <ol>
     *   <li>Parse the relative module path (e.g., "..utils" → parent, ".sub" → current.sub)</li>
     *   <li>Construct the target module name</li>
     *   <li>Perform filesystem check (optional, based on configuration)</li>
     *   <li>Return empty if resolution fails (above root or file not found)</li>
     * </ol>
     *
     * @param relativeModule the relative module path (e.g., "..utils", ".sibling")
     * @param currentPackage the current package (e.g., "src.service", "myapp")
     * @param filePath the full path to the current file (used for filesystem checks)
     * @return the resolved module name, or empty if resolution fails
     */
    public Optional<String> resolveModule(
        String relativeModule,
        String currentPackage,
        Path filePath
    ) {
        if (relativeModule == null || relativeModule.isEmpty()) {
            return Optional.empty();
        }

        // Count leading dots to determine depth
        // Note: In Python, '.' = same level, '..' = go up one, '...' = go up two
        // So the number of levels to go up is (dots - 1)
        int levelsUp = 0;
        int i = 0;
        while (i < relativeModule.length() && relativeModule.charAt(i) == '.') {
            i++;
        }
        levelsUp = i - 1;  // dots - 1 = levels to go up

        if (i == 0) {
            // Not a relative import
            return Optional.empty();
        }

        // Extract the subpackage after the dots (if any)
        String subpackage = "";
        if (i < relativeModule.length()) {
            String remaining = relativeModule.substring(i);
            if (remaining.startsWith(".")) {
                subpackage = remaining.substring(1); // Remove the extra dot
            } else {
                subpackage = remaining;
            }
        }

        // Handle empty package case
        if (currentPackage == null || currentPackage.isEmpty()) {
            if (levelsUp > 0) {
                // Can't go up from empty package
                return Optional.empty();
            }
            // At root, just use subpackage
            return validateWithAncestralSearch(subpackage, filePath);
        }

        // Navigate up the package hierarchy
        String[] packageParts = currentPackage.split("\\.");

        // Keep only the parts that remain after going up 'levelsUp' levels
        String[] targetParts;
        if (levelsUp >= packageParts.length) {
            // Going to root level or above
            targetParts = new String[0];
        } else {
            targetParts = new String[packageParts.length - levelsUp];
            System.arraycopy(packageParts, 0, targetParts, 0, targetParts.length);
        }

        // Add subpackage
        String targetPackage;
        if (targetParts.length == 0) {
            targetPackage = subpackage;
        } else {
            if (subpackage.isEmpty()) {
                targetPackage = String.join(".", targetParts);
            } else {
                targetPackage = String.join(".", targetParts) + "." + subpackage;
            }
        }

        // Perform filesystem check with ancestral search
        return validateWithAncestralSearch(targetPackage, filePath);
    }

    /**
     * Validates that the target module exists on the filesystem.
     * Uses ancestral search to find the module at parent levels.
     *
     * @param targetPackage the target package (e.g., "src.service", "tests")
     * @param currentFilePath the full path to the current file
     * @return the validated module name, or empty if not found
     */
    private Optional<String> validateWithAncestralSearch(String targetPackage, Path currentFilePath) {
        if (sourceRoot == null) {
            // No filesystem check configured, return the name as-is
            return Optional.of(targetPackage);
        }

        // Extract module name from package (last segment)
        String[] parts = targetPackage.split("\\.");
        String moduleName = parts[parts.length - 1];

        // Try current directory first
        Path currentDir = currentFilePath.getParent();
        if (currentDir != null) {
            Path moduleFile = currentDir.resolve(moduleName + ".py");
            Path moduleInit = currentDir.resolve("__init__.py");

            if (Files.exists(moduleFile)) {
                return Optional.of(targetPackage);
            }
        }

        // Ancestral search: check parent directories for tests/
        Path dir = currentDir;
        for (int level = 0; level < 3; level++) {
            if (dir == null || !Files.exists(dir)) {
                break;
            }

            // Check for tests/ directory
            Path testsDir = dir.resolve("tests");
            if (Files.exists(testsDir)) {
                Path moduleFile = testsDir.resolve(moduleName + ".py");

                if (Files.exists(moduleFile)) {
                    // Found it! Construct the package path
                    String testsPackage = buildPackagePath(dir.resolve("tests"), sourceRoot);
                    // Add the module name to the package path
                    if (!testsPackage.isEmpty()) {
                        testsPackage = testsPackage + "." + moduleName;
                    } else {
                        testsPackage = moduleName;
                    }
                    return Optional.of(testsPackage);
                }
            }

            dir = dir.getParent();
        }

        // Not found
        return Optional.empty();
    }

    /**
     * Builds a package name from a directory path relative to source root.
     *
     * @param directory the directory path (e.g., /project/src/tests)
     * @param sourceRoot the source root (e.g., /project/src)
     * @return the package name (e.g., "tests")
     */
    private String buildPackagePath(Path directory, Path sourceRoot) {
        try {
            Path relativePath = sourceRoot.relativize(directory);
            return relativePath.toString().replace("/", ".");
        } catch (IllegalArgumentException e) {
            // Not relative, use directory name
            return directory.getFileName().toString();
        }
    }

    /**
     * Resets the resolver state between parse runs.
     */
    public void reset() {
        this.sourceRoot = null;
    }
}
