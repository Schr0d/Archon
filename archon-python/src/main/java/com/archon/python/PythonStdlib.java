package com.archon.python;

import java.util.Set;

/**
 * Standard library module names for Python 3.10+.
 *
 * <p>Used to filter external dependencies from the dependency graph.
 * Python's standard library includes ~300 modules. This set contains
 * the most commonly used modules.
 *
 * <p>Module list sourced from Python 3.10 standard library documentation.
 */
public class PythonStdlib {

    /**
     * Commonly used standard library modules. This is a curated subset
     * of the full ~300 module stdlib. Add more as needed.
     */
    private static final Set<String> STDLIB_MODULES = Set.of(
        // Core modules
        "sys", "os", "pathlib", "io", "re", "json", "collections",
        "itertools", "functools", "operator", "datetime", "time",
        "random", "math", "statistics", "decimal", "fractions",
        "typing", "dataclasses", "enum", "contextlib",

        // File system
        "shutil", "tempfile", "glob", "fnmatch",

        // Text processing
        "string", "textwrap", "difflib", "unicodedata",
        "stringprep", "readline", "rlcompleter",

        // Data structures
        "array", "weakref", "types", "copy", "pprint",

        // Concurrency
        "threading", "multiprocessing", "concurrent.futures", "asyncio",
        "queue", "select", "subprocess",

        // Networking
        "urllib", "http", "socket", "ssl", "email", "smtp",

        // Web/Internet
        "http.server", "http.client", "xml", "html.parser",
        "xml.etree.ElementTree", "csv",

        // Testing
        "unittest", "doctest", "mock", "pytest",

        // Logging
        "logging", "logging.config",

        // Type stubs
        "typing_extensions"
    );

    /**
     * Checks if a module name belongs to Python's standard library.
     *
     * @param moduleName the module name to check (e.g., "os", "sys.path", "numpy")
     * @return true if the module is in the standard library, false otherwise
     */
    public static boolean isStdlib(String moduleName) {
        if (moduleName == null || moduleName.isEmpty()) {
            return false;
        }

        // Extract base module (before first dot)
        // "sys.path" -> "sys", "os.path" -> "os"
        String baseModule = moduleName.split("\\.")[0].toLowerCase();

        return STDLIB_MODULES.contains(baseModule);
    }

    /**
     * Returns the full set of standard library module names.
     * Used for testing and validation.
     *
     * @return immutable set of stdlib module names
     */
    public static Set<String> getStdlibModules() {
        return Set.copyOf(STDLIB_MODULES);
    }
}
