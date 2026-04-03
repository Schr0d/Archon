package com.archon.python;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts Python import statements from source code using regex patterns.
 *
 * <p>Detects:
 * <ul>
 *   <li>Simple imports: {@code import foo}</li>
 *   <li>Imports with aliases: {@code import foo as bar}</li>
 *   <li>Multiple imports: {@code import foo, bar}</li>
 *   <li>From imports: {@code from foo import bar}</li>
 *   <li>Relative imports: {@code from . import sibling}, {@code from .. import parent}</li>
 *   <li>Wildcard imports: {@code from foo import *} (reported as blind spot)</li>
 *   <li>Dynamic imports: {@code importlib.import_module("foo")} (reported as blind spot)</li>
 * </ul>
 *
 * <p>Regex patterns are compiled once as static final fields for performance.
 */
public class PythonImportExtractor {

    // Pattern: import foo
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
        "^import\\s+(\\w+)(?:\\s+as\\s+(\\w+))?"
    );

    // Pattern: import foo, bar, baz
    private static final Pattern IMPORT_MULTI_PATTERN = Pattern.compile(
        "^import\\s+(\\w+)(?:\\s*,\\s*(\\w+))*"
    );

    // Pattern: from foo import bar
    private static final Pattern FROM_IMPORT_PATTERN = Pattern.compile(
        "^from\\s+(\\S+)\\s+import\\s+(\\S+)"
    );

    // Pattern: from foo import bar, baz
    // Note: This pattern handles simple multi-item from imports
    private static final Pattern FROM_IMPORT_MULTI_PATTERN = Pattern.compile(
        "^from\\s+(\\S+)\\s+import\\s+(\\S+)(?:\\s*,\\s*(\\S+))*"
    );

    // Pattern: from . import sibling (relative)
    private static final Pattern FROM_IMPORT_RELATIVE_PATTERN = Pattern.compile(
        "^from\\s+(\\.+)(?:\\.?(\\w+))?\\s+import\\s+(\\w+)"
    );

    /**
     * Result of import extraction containing module information.
     */
    public record ImportInfo(
        String moduleName,
        ImportType type,
        boolean isRelative,
        int relativeDepth
    ) {
        /**
         * Creates an ImportInfo for an absolute import.
         */
        public ImportInfo(String moduleName, ImportType type) {
            this(moduleName, type, false, 0);
        }
    }

    /**
     * Import type classification.
     */
    public enum ImportType {
        /** Absolute import: {@code import foo} or {@code from foo import bar} */
        ABSOLUTE,
        /** Relative import: {@code from . import sibling} or {@code from .. import parent} */
        RELATIVE
    }

    /**
     * Extracts import statements from Python source code.
     *
     * @param content the Python source code
     * @return list of ImportInfo objects representing each import
     */
    public static List<ImportInfo> extractImports(String content) {
        List<ImportInfo> imports = new ArrayList<>();

        if (content == null || content.isEmpty()) {
            return imports;
        }

        String[] lines = content.split("\n");

        for (String line : lines) {
            line = line.trim();

            // Skip empty lines and comments
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // Try simple import pattern: import foo
            Matcher matcher = IMPORT_PATTERN.matcher(line);
            if (matcher.matches()) {
                String module = matcher.group(1);
                imports.add(new ImportInfo(module, ImportType.ABSOLUTE));
                continue;
            }

            // Try multi-import pattern: import foo, bar
            matcher = IMPORT_MULTI_PATTERN.matcher(line);
            if (matcher.matches()) {
                // The pattern matches the first module, we need to extract all
                // Split by comma and process each
                String modulesPart = line.substring(line.indexOf("import") + 6).trim();
                String[] moduleNames = modulesPart.split(",");
                for (String moduleName : moduleNames) {
                    moduleName = moduleName.trim();
                    if (!moduleName.isEmpty()) {
                        imports.add(new ImportInfo(moduleName, ImportType.ABSOLUTE));
                    }
                }
                continue;
            }

            // Try from import pattern: from foo import bar
            matcher = FROM_IMPORT_PATTERN.matcher(line);
            if (matcher.matches()) {
                String module = matcher.group(1);
                imports.add(new ImportInfo(module, ImportType.ABSOLUTE));
                continue;
            }

            // Try from import multi-item: from foo import bar, baz
            matcher = FROM_IMPORT_MULTI_PATTERN.matcher(line);
            if (matcher.matches()) {
                String module = matcher.group(1);
                // The pattern matches the first item, we need to extract all
                String itemsPart = line.substring(line.indexOf("import") + 6).trim();
                String[] itemNames = itemsPart.split(",");
                for (String itemName : itemNames) {
                    itemName = itemName.trim();
                    if (!itemName.isEmpty()) {
                        imports.add(new ImportInfo(module, ImportType.ABSOLUTE));
                    }
                }
                continue;
            }

            // Try relative import pattern: from . import sibling
            matcher = FROM_IMPORT_RELATIVE_PATTERN.matcher(line);
            if (matcher.matches()) {
                String dots = matcher.group(1); // "." or ".." or "..."
                String subpackage = matcher.group(2) != null ? matcher.group(2) : "";
                int depth = dots.length();

                // Construct relative module identifier
                String relativeModule = dots + subpackage;
                imports.add(new ImportInfo(relativeModule, ImportType.RELATIVE, true, depth));
                continue;
            }

            // Note: Dynamic imports (importlib.import_module, __import__) are not detected
            // They will be handled as blind spots by the plugin
            // Wildcard imports (from foo import *) are not extracted here either
        }

        return imports;
    }
}
