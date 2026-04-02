package com.archon.java;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves import declarations to fully qualified class names.
 */
public class ImportResolver {
    private final Set<String> knownClasses;

    public ImportResolver(Set<String> knownClasses) {
        this.knownClasses = knownClasses != null ? knownClasses : Collections.emptySet();
    }

    /**
     * Resolves an import statement to a FQCN.
     * Regular import: returns the full path.
     * Static import: returns the declaring class (everything before the last dot-segment).
     * Wildcard: returns empty (use resolveWildcard instead).
     */
    public Optional<String> resolve(String importStatement) {
        if (importStatement == null || importStatement.isBlank()) {
            return Optional.empty();
        }
        if (importStatement.endsWith(".*")) {
            return Optional.empty();
        }
        // Check if it looks like a static import (contains a method name)
        // Simple heuristic: if the last segment starts with lowercase, it's likely static
        int lastDot = importStatement.lastIndexOf('.');
        if (lastDot > 0) {
            String lastSegment = importStatement.substring(lastDot + 1);
            if (Character.isLowerCase(lastSegment.charAt(0)) && !importStatement.contains("$")) {
                // Likely a static import — return the declaring class
                return Optional.of(importStatement.substring(0, lastDot));
            }
        }
        return Optional.of(importStatement);
    }

    /**
     * Resolves a wildcard import to all matching known classes.
     * e.g., "com.fuwa.system.domain.*" → all classes in that package.
     */
    public Set<String> resolveWildcard(String wildcardImport) {
        if (wildcardImport == null || !wildcardImport.endsWith(".*")) {
            return Collections.emptySet();
        }
        String prefix = wildcardImport.substring(0, wildcardImport.length() - 1); // "com.fuwa.system.domain."
        return knownClasses.stream()
            .filter(cls -> cls.startsWith(prefix))
            .collect(Collectors.toSet());
    }
}
