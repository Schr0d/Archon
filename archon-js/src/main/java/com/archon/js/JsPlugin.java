package com.archon.js;

import com.archon.core.plugin.BlindSpot;
import com.archon.core.plugin.Confidence;
import com.archon.core.plugin.DependencyDeclaration;
import com.archon.core.plugin.EdgeType;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ModuleDeclaration;
import com.archon.core.plugin.NodeType;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LanguagePlugin implementation for JavaScript/TypeScript using dependency-cruiser.
 *
 * <p>Uses a lazy batch subprocess pattern: on the first {@link #parseFromContent} call,
 * spawns {@code dependency-cruiser} for the entire source root, caches the JSON results,
 * and returns declarations from the cache on subsequent calls.
 *
 * <p>When the {@code content} parameter is null or empty (analyze mode), dependency-cruiser
 * reads source files directly from disk. When content is provided (diff base graph via
 * git show), it is parsed directly with regex since dependency-cruiser can only analyze
 * the filesystem, which contains working-tree data.
 *
 * <p>Node IDs use the "js:" namespace prefix with project-relative paths,
 * e.g. {@code js:src/components/Header.vue}.
 *
 * <p>Filters out:
 * <ul>
 *   <li>Node.js built-in modules (fs, path, http, etc.)</li>
 *   <li>node_modules dependencies</li>
 * </ul>
 *
 * <p>Reports unresolved modules as blind spots.
 *
 * <p><b>Thread safety:</b> This class is NOT thread-safe. It maintains mutable state
 * (cached results/errors) designed for single-threaded use by {@code ParseOrchestrator}.
 */
public class JsPlugin implements LanguagePlugin {

    private static final String NAMESPACE = "js";
    private static final long SUBPROCESS_TIMEOUT_SECONDS = 120;

    private static final Set<String> EXTENSIONS = Set.of(
        "js", "ts", "tsx", "vue", "jsx", "mjs", "cjs"
    );

    private static final Set<String> NODE_BUILTINS = Set.of(
        "assert", "buffer", "child_process", "cluster", "console", "constants",
        "crypto", "dgram", "dns", "domain", "events", "fs", "http", "https",
        "module", "net", "os", "path", "perf_hooks", "process", "punycode",
        "querystring", "readline", "repl", "stream", "string_decoder", "sys",
        "timers", "tls", "tty", "url", "util", "v8", "vm", "worker_threads", "zlib"
    );

    /** Regex for extracting ES module import paths. */
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
        "import\\s+(?:type\\s+)?(?:\\{[^}]*\\}|\\*\\s+as\\s+\\w+|\\w+)" +
        "(?:\\s*,\\s*(?:\\{[^}]*\\}|\\*\\s+as\\s+\\w+|\\w+))*\\s+from\\s+['\"]([^'\"]+)['\"]"
    );
    private static final Pattern REEXPORT_PATTERN = Pattern.compile(
        "export\\s+(?:\\{[^}]*\\}|\\*\\s+as\\s+\\w+)\\s+from\\s+['\"]([^'\"]+)['\"]"
    );
    /** Regex for extracting <script> or <script setup> section from Vue SFC. */
    private static final Pattern VUE_SCRIPT_PATTERN = Pattern.compile(
        "<script(?:\\s+setup)?(?:\\s+[^>]*)?>([\\s\\S]*?)</script>",
        Pattern.CASE_INSENSITIVE
    );

    /** Cached results keyed by project-relative path (e.g. "src/components/Header.vue"). */
    private Map<String, ModuleResult> cachedResults;

    /** Cached error state from a failed subprocess run. */
    private List<String> cachedErrors;

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    @Override
    public void reset() {
        cachedResults = null;
        cachedErrors = null;
    }

    @Override
    public ParseResult parseFromContent(String filePath, String content, ParseContext context) {
        // When content is provided AND the cache is already populated, this is a diff
        // base graph call (content from git show, cache has working-tree data).
        // Parse the provided content directly with regex instead of returning stale cache data.
        if (content != null && !content.isEmpty() && cachedResults != null) {
            return parseFromProvidedContent(filePath, content, context);
        }

        List<String> parseErrors = new ArrayList<>();
        List<BlindSpot> blindSpots = new ArrayList<>();
        Set<String> sourceModules = new LinkedHashSet<>();
        List<ModuleDeclaration> moduleDecls = new ArrayList<>();
        List<DependencyDeclaration> depDecls = new ArrayList<>();

        // Lazy batch: populate cache on first call
        if (cachedResults == null && cachedErrors == null) {
            populateCache(context.getSourceRoot());
        }

        // If cache population failed, return the cached error
        if (cachedErrors != null && cachedResults == null) {
            parseErrors.addAll(cachedErrors);
            return new ParseResult(sourceModules, blindSpots, parseErrors, moduleDecls, depDecls);
        }

        // Map absolute filePath to project-relative path
        String relativePath = toRelativePath(filePath, context.getSourceRoot());

        // Look up cached result
        ModuleResult moduleResult = cachedResults.get(relativePath);
        if (moduleResult == null) {
            // File not in dependency-cruiser output: return empty ParseResult
            return new ParseResult(sourceModules, blindSpots, parseErrors, moduleDecls, depDecls);
        }

        // Build prefixed ID
        String prefixedId = NAMESPACE + ":" + relativePath;
        sourceModules.add(prefixedId);

        // Module declaration
        moduleDecls.add(new ModuleDeclaration(
            prefixedId,
            NodeType.MODULE,
            relativePath,
            Confidence.HIGH
        ));

        // Dependency declarations
        for (String dep : moduleResult.resolvedDependencies) {
            depDecls.add(new DependencyDeclaration(
                prefixedId,
                NAMESPACE + ":" + dep,
                EdgeType.IMPORTS,
                Confidence.HIGH,
                "import " + dep,
                false
            ));
        }

        // Blind spots for unresolved modules
        for (String unresolved : moduleResult.unresolvedDependencies) {
            blindSpots.add(new BlindSpot(
                "UnresolvedModule",
                relativePath,
                "Could not resolve module: " + unresolved
            ));
        }

        return new ParseResult(sourceModules, blindSpots, parseErrors, moduleDecls, depDecls);
    }

    /**
     * Parses JS/TS content provided directly (e.g. from git show for diff base graph).
     * Uses regex extraction since dependency-cruiser can only analyze the filesystem.
     * Imports are resolved relative to the file's location within the source root.
     */
    private ParseResult parseFromProvidedContent(String filePath, String content,
                                                  ParseContext context) {
        List<BlindSpot> blindSpots = new ArrayList<>();
        Set<String> sourceModules = new LinkedHashSet<>();
        List<ModuleDeclaration> moduleDecls = new ArrayList<>();
        List<DependencyDeclaration> depDecls = new ArrayList<>();

        String relativePath = toRelativePath(filePath, context.getSourceRoot());
        String prefixedId = NAMESPACE + ":" + relativePath;
        sourceModules.add(prefixedId);

        moduleDecls.add(new ModuleDeclaration(
            prefixedId, NodeType.MODULE, relativePath, Confidence.MEDIUM
        ));

        // For Vue files, extract the <script> section first
        String contentToParse = content;
        if (relativePath.endsWith(".vue")) {
            Matcher scriptMatcher = VUE_SCRIPT_PATTERN.matcher(content);
            if (scriptMatcher.find()) {
                contentToParse = scriptMatcher.group(1);
            } else {
                // No <script> block, can't extract deps
                return new ParseResult(sourceModules, blindSpots, new ArrayList<>(), moduleDecls, depDecls);
            }
        }

        // Extract import specifiers via regex
        Set<String> rawImports = new LinkedHashSet<>();
        Matcher importMatcher = IMPORT_PATTERN.matcher(contentToParse);
        while (importMatcher.find()) {
            rawImports.add(importMatcher.group(1));
        }
        Matcher reexportMatcher = REEXPORT_PATTERN.matcher(contentToParse);
        while (reexportMatcher.find()) {
            rawImports.add(reexportMatcher.group(1));
        }

        Path sourceRoot = context.getSourceRoot();
        Path fileDir = sourceRoot.resolve(relativePath).getParent();

        for (String rawImport : rawImports) {
            // Skip non-relative imports (node_modules, bare specifiers)
            if (!rawImport.startsWith(".")) {
                if (!isBuiltin(rawImport)) {
                    blindSpots.add(new BlindSpot(
                        "UnresolvedModule", relativePath,
                        "Non-relative import (not resolved in content mode): " + rawImport
                    ));
                }
                continue;
            }

            // Resolve relative import to project-relative path
            String resolved = resolveRelativeImport(rawImport, fileDir, sourceRoot);
            if (resolved != null) {
                depDecls.add(new DependencyDeclaration(
                    prefixedId,
                    NAMESPACE + ":" + resolved,
                    EdgeType.IMPORTS,
                    Confidence.MEDIUM,
                    "import " + rawImport,
                    false
                ));
            }
        }

        return new ParseResult(sourceModules, blindSpots, new ArrayList<>(), moduleDecls, depDecls);
    }

    /**
     * Resolves a relative import specifier (e.g. "./utils", "../components/Header")
     * to a project-relative path. Tries common extensions (.js, .ts, .tsx, .vue, .jsx)
     * and index files.
     */
    String resolveRelativeImport(String importSpecifier, Path fileDir, Path sourceRoot) {
        try {
            Path resolved = fileDir.resolve(importSpecifier).normalize();
            String rel = sourceRoot.toAbsolutePath().relativize(resolved).toString().replace('\\', '/');

            // Check if it already has a known extension
            for (String ext : EXTENSIONS) {
                if (rel.endsWith("." + ext)) {
                    return rel;
                }
            }

            // Try adding extensions
            for (String ext : EXTENSIONS) {
                String candidate = rel + "." + ext;
                if (sourceRoot.resolve(candidate).toFile().exists()) {
                    return candidate;
                }
            }

            // Try index files
            for (String ext : EXTENSIONS) {
                String candidate = rel + "/index." + ext;
                if (sourceRoot.resolve(candidate).toFile().exists()) {
                    return candidate;
                }
            }

            // Return without extension as best guess (dependency-cruiser usually resolves these)
            return rel;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Populates the cache by spawning dependency-cruiser for the source root.
     * On failure, sets cachedErrors instead of cachedResults.
     */
    private void populateCache(Path sourceRoot) {
        try {
            // Check for npx availability before attempting subprocess
            if (!isNpxAvailable()) {
                throw new NodeNotFoundException("npx not found on PATH");
            }

            String jsonOutput = runDependencyCruiser(sourceRoot);
            cachedResults = parseDependencyCruiserJson(jsonOutput, sourceRoot);
            cachedErrors = null;
        } catch (NodeNotFoundException e) {
            cachedErrors = List.of(
                "Error: JS/TS project detected but Node.js not found.\n" +
                "Install Node.js 16+: https://nodejs.org\n" +
                "Then re-run: archon analyze ."
            );
        } catch (SubprocessException e) {
            cachedErrors = List.of(e.getMessage());
        } catch (Exception e) {
            cachedErrors = List.of("JS plugin error: " + e.getMessage());
        }
    }

    /**
     * Spawns dependency-cruiser as a subprocess and returns the JSON output.
     *
     * <p>Protected to allow test subclasses to override and return mock data.
     *
     * @param sourceRoot the project root to analyze
     * @return raw JSON string from dependency-cruiser
     * @throws Exception if the subprocess fails
     */
    protected String runDependencyCruiser(Path sourceRoot) throws Exception {
        Path tempFile = Files.createTempFile("archon-depcruise-", ".json");
        try {
            ProcessBuilder pb = buildProcess(sourceRoot, tempFile);
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Close stdin to prevent subprocess from hanging waiting for input
            process.getOutputStream().close();

            // Read stderr asynchronously to avoid blocking the timeout
            // (readAllBytes before waitFor can hang if npx writes verbose output)
            StringBuilder stderrBuilder = new StringBuilder();
            Thread stderrReader = new Thread(() -> {
                try {
                    byte[] bytes = process.getErrorStream().readAllBytes();
                    stderrBuilder.append(new String(bytes));
                } catch (Exception ignored) {}
            });
            stderrReader.setDaemon(true);
            stderrReader.start();

            boolean finished = process.waitFor(SUBPROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new SubprocessException(
                    "dependency-cruiser timed out after " + SUBPROCESS_TIMEOUT_SECONDS + " seconds"
                );
            }

            // Wait for stderr reader to finish (it should complete quickly after process exits)
            stderrReader.join(5000);
            String stderr = stderrBuilder.toString();

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String snippet = stderr.length() > 500 ? stderr.substring(0, 500) : stderr;
                throw new SubprocessException(
                    "dependency-cruiser exited with code " + exitCode + ": " + snippet
                );
            }

            String output = Files.readString(tempFile);
            // Validate it looks like JSON before returning
            if (output.isBlank()) {
                throw new SubprocessException(
                    "dependency-cruiser returned empty output"
                );
            }
            return output;
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Builds the ProcessBuilder for running dependency-cruiser.
     * Handles Windows vs Unix command differences.
     */
    private ProcessBuilder buildProcess(Path sourceRoot, Path tempFile) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return new ProcessBuilder(
                "cmd", "/c", "npx", "-y", "dependency-cruiser@latest",
                sourceRoot.toString(),
                "--no-config",
                "--output-type", "json",
                "--output-to", tempFile.toString()
            );
        } else {
            return new ProcessBuilder(
                "npx", "-y", "dependency-cruiser@latest",
                sourceRoot.toString(),
                "--no-config",
                "--output-type", "json",
                "--output-to", tempFile.toString()
            );
        }
    }

    /**
     * Parses dependency-cruiser JSON output into a map keyed by project-relative path.
     *
     * <p>Expected format (from dependency-cruiser docs):
     * <pre>{@code
     * {
     *   "modules": [
     *     {
     *       "source": "src/App.js",
     *       "dependencies": [
     *         { "resolved": "src/utils.js", "module": "es6", "coreModule": false,
     *           "couldNotResolve": false, "dependencyTypes": ["local"] },
     *         { "resolved": "fs", "module": "fs", "coreModule": true,
     *           "couldNotResolve": false, "dependencyTypes": ["core"] },
     *         { "resolved": "node_modules/vue/index.js", "module": "vue",
     *           "coreModule": false, "dependencyTypes": ["npm"] },
     *         { "module": "unknown-lib", "couldNotResolve": true }
     *       ]
     *     }
     *   ]
     * }
     * }</pre>
     */
    Map<String, ModuleResult> parseDependencyCruiserJson(String json, Path sourceRoot) {
        Map<String, ModuleResult> results = new HashMap<>();

        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return results;
        }

        JsonObject rootObj = root.getAsJsonObject();
        JsonElement modulesElement = rootObj.get("modules");
        if (modulesElement == null || !modulesElement.isJsonArray()) {
            return results;
        }

        JsonArray modules = modulesElement.getAsJsonArray();
        for (JsonElement moduleElement : modules) {
            if (!moduleElement.isJsonObject()) continue;
            JsonObject module = moduleElement.getAsJsonObject();

            String source = getStringField(module, "source");
            if (source == null || source.isEmpty()) continue;

            // Skip core module entries (fs, path, etc.) that appear as top-level modules
            if (getBooleanField(module, "coreModule")) {
                continue;
            }

            // Normalize path separators
            source = source.replace('\\', '/');

            Set<String> resolvedDeps = new LinkedHashSet<>();
            Set<String> unresolvedDeps = new LinkedHashSet<>();

            JsonElement depsElement = module.get("dependencies");
            if (depsElement != null && depsElement.isJsonArray()) {
                JsonArray deps = depsElement.getAsJsonArray();
                for (JsonElement depElement : deps) {
                    if (!depElement.isJsonObject()) continue;
                    JsonObject dep = depElement.getAsJsonObject();

                    // Skip core modules (fs, path, etc.) using dependency-cruiser's own flag
                    if (getBooleanField(dep, "coreModule")) {
                        continue;
                    }

                    // Check for unresolvable
                    if (getBooleanField(dep, "couldNotResolve")) {
                        String moduleSpecifier = getStringField(dep, "module");
                        if (moduleSpecifier != null && !isBuiltin(moduleSpecifier)) {
                            unresolvedDeps.add(moduleSpecifier);
                        }
                        continue;
                    }

                    String resolved = getStringField(dep, "resolved");
                    if (resolved == null || resolved.isEmpty()) continue;

                    // Normalize
                    resolved = resolved.replace('\\', '/');

                    // Skip node_modules
                    if (resolved.startsWith("node_modules/") || resolved.contains("/node_modules/")) {
                        continue;
                    }

                    // Skip builtins (fallback in case coreModule flag is absent)
                    if (isBuiltinPath(resolved)) {
                        continue;
                    }

                    resolvedDeps.add(resolved);
                }
            }

            results.put(source, new ModuleResult(resolvedDeps, unresolvedDeps));
        }

        return results;
    }

    /**
     * Converts an absolute file path to a project-relative path using the source root.
     */
    String toRelativePath(String filePath, Path sourceRoot) {
        try {
            Path absolute = Path.of(filePath).toAbsolutePath();
            Path root = sourceRoot.toAbsolutePath();
            if (absolute.startsWith(root)) {
                String relative = root.relativize(absolute).toString().replace('\\', '/');
                return relative;
            }
        } catch (Exception e) {
            // Fall through
        }
        // Fallback: return as-is with normalized separators
        return filePath.replace('\\', '/');
    }

    private static String getStringField(JsonObject obj, String field) {
        JsonElement element = obj.get(field);
        return (element != null && !element.isJsonNull()) ? element.getAsString() : null;
    }

    private static boolean getBooleanField(JsonObject obj, String field) {
        JsonElement element = obj.get(field);
        return (element != null && !element.isJsonNull()) && element.getAsBoolean();
    }

    private static boolean isBuiltin(String moduleSpecifier) {
        if (moduleSpecifier == null) return false;
        // Strip any scoped package prefix or subpath
        String base = moduleSpecifier.split("/")[0];
        return NODE_BUILTINS.contains(base);
    }

    private static boolean isBuiltinPath(String resolvedPath) {
        // Some setups resolve builtins to paths like "node:fs" or just "fs"
        if (resolvedPath.startsWith("node:")) return true;
        String base = resolvedPath.split("/")[0];
        return NODE_BUILTINS.contains(base);
    }

    /**
     * Check if npx is available on the PATH.
     * Protected for testability.
     */
    protected boolean isNpxAvailable() {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "where", "npx");
            } else {
                pb = new ProcessBuilder("which", "npx");
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(5, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Internal representation of a single module's parsed result.
     */
    static final class ModuleResult {
        final Set<String> resolvedDependencies;
        final Set<String> unresolvedDependencies;

        ModuleResult(Set<String> resolvedDependencies, Set<String> unresolvedDependencies) {
            this.resolvedDependencies = resolvedDependencies;
            this.unresolvedDependencies = unresolvedDependencies;
        }
    }

    /**
     * Exception thrown when the dependency-cruiser subprocess fails.
     */
    static final class SubprocessException extends Exception {
        SubprocessException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when Node.js/npx is not found.
     */
    static final class NodeNotFoundException extends Exception {
        NodeNotFoundException(String message) {
            super(message);
        }
    }
}
