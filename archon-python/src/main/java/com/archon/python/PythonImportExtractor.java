package com.archon.python;

import java.util.LinkedHashSet;
import java.util.Set;
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
 *   <li>From imports with multiple items: {@code from foo import bar, baz}</li>
 * </ul>
 *
 * <p>Blind spots (not extracted):
 * <ul>
 *   <li>Wildcard imports: {@code from foo import *} - cannot determine what's imported</li>
 *   <li>Dynamic imports: {@code importlib.import_module("foo")} - runtime resolution</li>
 *   <li>Relative imports: {@code from . import sibling} - handled by Task 4</li>
 * </ul>
 *
 * <p>Regex patterns are compiled once as static final fields for performance.
 */
public class PythonImportExtractor {

    // Pattern: import foo [as bar], baz [, ...]
    // Matches any import statement starting with "import"
    // Note: Java regex doesn't support repeated capture groups, so we extract modules via string parsing
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
        "^import\\s+([\\w.]+(?:\\s*,\\s*[\\w.]+)*(?:\\s+as\\s+\\w+)?)"
    );

    // Pattern: from foo import bar [, baz, ...]
    // Matches any from-import statement
    private static final Pattern FROM_IMPORT_PATTERN = Pattern.compile(
        "^from\\s+([\\w.]+)\\s+import\\s+(\\S+(?:\\s*,\\s*\\S+)*)"
    );

    /**
     * Extracts import statements from Python source code.
     *
     * @param content the Python source code
     * @return set of module names (e.g., "os", "pathlib", "sys")
     */
    public static Set<String> extractImports(String content) {
        Set<String> imports = new LinkedHashSet<>();

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

            // Try import pattern: import foo [, bar, ...]
            Matcher matcher = IMPORT_PATTERN.matcher(line);
            if (matcher.matches()) {
                // Extract the modules part after "import" and parse each module
                String modulesPart = line.substring(line.indexOf("import") + 6).trim();
                String[] moduleList = modulesPart.split(",");
                for (String module : moduleList) {
                    module = module.trim();
                    // Extract just the module name before any "as" alias
                    if (module.contains(" as ")) {
                        module = module.substring(0, module.indexOf(" as ")).trim();
                    }
                    if (!module.isEmpty()) {
                        imports.add(module);
                    }
                }
                continue;
            }

            // Try from import pattern: from foo import bar [, baz, ...]
            matcher = FROM_IMPORT_PATTERN.matcher(line);
            if (matcher.matches()) {
                // Group 1 is the source module (e.g., "foo" in "from foo import bar")
                String module = matcher.group(1);
                imports.add(module);
                continue;
            }

            // Note: Wildcard imports (from foo import *) are not extracted
            // Note: Dynamic imports (importlib.import_module, __import__) are not detected
            // Note: Relative imports (from . import sibling) are handled by Task 4
        }

        return imports;
    }
}
