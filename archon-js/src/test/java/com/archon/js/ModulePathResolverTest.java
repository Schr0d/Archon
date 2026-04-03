package com.archon.js;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ModulePathResolver ES module path resolution.
 */
@DisplayName("ModulePathResolver Tests")
class ModulePathResolverTest {

    private ModulePathResolver resolver;
    private Path sourceRoot;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        resolver = new ModulePathResolver();
        // sourceRoot is the project root, not src
        // This matches typical tsconfig.json baseUrl behavior
        sourceRoot = tempDir;
    }

    @Test
    @DisplayName("External packages return empty (null)")
    void testExternalPackagesReturnEmpty() {
        // Common external packages
        assertFalse(resolver.resolve("react", "/project/src/App.tsx", sourceRoot).isPresent(),
            "react should be recognized as external");
        assertFalse(resolver.resolve("lodash", "/project/src/utils.js", sourceRoot).isPresent(),
            "lodash should be recognized as external");
        assertFalse(resolver.resolve("@angular/core", "/project/src/app.ts", sourceRoot).isPresent(),
            "@angular/core should be recognized as external");
        assertFalse(resolver.resolve("vue", "/project/src/components/Button.vue", sourceRoot).isPresent(),
            "vue should be recognized as external");
    }

    @Test
    @DisplayName("Relative imports resolve correctly")
    void testRelativeImportsResolveCorrectly() throws IOException {
        // Create test directory structure (sourceRoot is project root)
        Path srcDir = sourceRoot.resolve("src");
        Path componentsDir = srcDir.resolve("components");
        Files.createDirectories(componentsDir);

        Path utilsDir = srcDir.resolve("utils");
        Files.createDirectories(utilsDir);

        Path utilsFile = utilsDir.resolve("helpers.ts");
        Files.writeString(utilsFile, "export function help() {}");

        // Test same-directory import: ./Button
        Path fromFile = componentsDir.resolve("App.tsx");
        Path resolved = resolver.resolve("./Button", fromFile.toString(), srcDir).orElse(null);
        assertNotNull(resolved, "Same-directory import should resolve");
        assertEquals(componentsDir.resolve("Button"), resolved,
            "Should resolve to components/Button");

        // Test sibling import: ../utils/helpers
        resolved = resolver.resolve("../utils/helpers", fromFile.toString(), srcDir).orElse(null);
        assertNotNull(resolved, "Sibling import should resolve");
        assertEquals(utilsFile, resolved,
            "Should resolve to utils/helpers.ts");

        // Test nested relative: ../utils/../utils/helpers (starts with ..)
        resolved = resolver.resolve("../utils/../utils/helpers", fromFile.toString(), srcDir).orElse(null);
        assertNotNull(resolved, "Nested relative import should resolve and normalize");
        assertEquals(utilsFile, resolved,
            "Should normalize path components");
    }

    @Test
    @DisplayName("Barrel files are detected automatically")
    void testBarrelFilesAreDetected() throws IOException {
        // Create directory structure with barrel files (sourceRoot is project root)
        Path srcDir = sourceRoot.resolve("src");
        Path utilsDir = srcDir.resolve("utils");
        Files.createDirectories(utilsDir);

        Path indexTs = utilsDir.resolve("index.ts");
        Files.writeString(indexTs, "export * from './helpers';");

        Path fromFile = srcDir.resolve("App.tsx");

        // Importing ./utils should resolve to ./utils/index.ts
        Path resolved = resolver.resolve("./utils", fromFile.toString(), srcDir).orElse(null);
        assertNotNull(resolved, "Barrel import should resolve");
        assertEquals(indexTs, resolved,
            "Should resolve to utils/index.ts when importing directory");
    }

    @Test
    @DisplayName("Barrel file priority: .ts > .tsx > .js > .jsx")
    void testBarrelFilePriority() throws IOException {
        Path srcDir = sourceRoot.resolve("src");
        Path utilsDir = srcDir.resolve("utils");
        Files.createDirectories(utilsDir);

        // Create multiple barrel files
        Path indexJsx = utilsDir.resolve("index.jsx");
        Files.writeString(indexJsx, "export default () => {};");

        Path indexJs = utilsDir.resolve("index.js");
        Files.writeString(indexJs, "export default () => {};");

        Path indexTsx = utilsDir.resolve("index.tsx");
        Files.writeString(indexTsx, "export default function() {};");

        Path indexTs = utilsDir.resolve("index.ts");
        Files.writeString(indexTs, "export const x = 1;");

        Path fromFile = srcDir.resolve("App.tsx");

        // Should prefer .ts over others
        Path resolved = resolver.resolve("./utils", fromFile.toString(), srcDir).orElse(null);
        assertNotNull(resolved, "Should resolve barrel file");
        assertEquals(indexTs, resolved,
            "Should prefer index.ts over other barrel files");

        // Remove index.ts and verify it falls back to index.tsx
        Files.deleteIfExists(indexTs);
        resolved = resolver.resolve("./utils", fromFile.toString(), srcDir).orElse(null);
        assertEquals(indexTsx, resolved,
            "Should fall back to index.tsx when index.ts doesn't exist");

        // Remove index.tsx and verify it falls back to index.js
        Files.deleteIfExists(indexTsx);
        resolved = resolver.resolve("./utils", fromFile.toString(), srcDir).orElse(null);
        assertEquals(indexJs, resolved,
            "Should fall back to index.js when index.tsx doesn't exist");

        // Remove index.js and verify it falls back to index.jsx
        Files.deleteIfExists(indexJs);
        resolved = resolver.resolve("./utils", fromFile.toString(), srcDir).orElse(null);
        assertEquals(indexJsx, resolved,
            "Should fall back to index.jsx when index.js doesn't exist");
    }

    @Test
    @DisplayName("Path aliases resolve correctly")
    void testPathAliasesResolveCorrectly() throws IOException {
        // Configure path aliases like tsconfig.json
        resolver.addPathAlias("@", "src");
        resolver.addPathAlias("@components", "src/components");
        resolver.addPathAlias("@utils", "src/utils");

        // Create directory structure (sourceRoot is project root)
        Path srcDir = sourceRoot.resolve("src");
        Path componentsDir = srcDir.resolve("components");
        Files.createDirectories(componentsDir);

        Path buttonFile = componentsDir.resolve("Button.tsx");
        Files.writeString(buttonFile, "export const Button = () => {};");

        Path fromFile = srcDir.resolve("pages/Home.tsx");

        // Test @ alias: @/components/Button
        Path resolved = resolver.resolve("@/components/Button", fromFile.toString(), sourceRoot).orElse(null);
        assertNotNull(resolved, "@ alias should resolve");
        assertEquals(buttonFile, resolved,
            "Should resolve @/components/Button to src/components/Button.tsx");

        // Test @components alias: @components/Button
        resolved = resolver.resolve("@components/Button", fromFile.toString(), sourceRoot).orElse(null);
        assertNotNull(resolved, "@components alias should resolve");
        assertEquals(buttonFile, resolved,
            "Should resolve @components/Button to src/components/Button.tsx");
    }

    @Test
    @DisplayName("Path aliases with barrel files")
    void testPathAliasesWithBarrelFiles() throws IOException {
        resolver.addPathAlias("@", "src");

        Path srcDir = sourceRoot.resolve("src");
        Path utilsDir = srcDir.resolve("utils");
        Files.createDirectories(utilsDir);

        Path indexTs = utilsDir.resolve("index.ts");
        Files.writeString(indexTs, "export * from './helpers';");

        Path fromFile = srcDir.resolve("App.tsx");

        // @/utils should resolve to src/utils/index.ts
        Path resolved = resolver.resolve("@/utils", fromFile.toString(), sourceRoot).orElse(null);
        assertNotNull(resolved, "Alias with barrel should resolve");
        assertEquals(indexTs, resolved,
            "Should resolve @/utils to src/utils/index.ts");
    }

    @Test
    @DisplayName("Module ID extraction strips extensions and index")
    void testModuleIdExtraction() {
        // Test various file paths (sourceRoot is the src directory)
        Path srcRoot = Path.of("/project/src");

        assertEquals("components/Button",
            ModulePathResolver.extractModuleId(
                Path.of("/project/src/components/Button.tsx"),
                srcRoot
            ),
            "Should strip .tsx extension");

        assertEquals("utils/helpers",
            ModulePathResolver.extractModuleId(
                Path.of("/project/src/utils/helpers.js"),
                srcRoot
            ),
            "Should strip .js extension");

        assertEquals("components",
            ModulePathResolver.extractModuleId(
                Path.of("/project/src/components/index.ts"),
                srcRoot
            ),
            "Should strip /index suffix");

        assertEquals("nested/deep/module",
            ModulePathResolver.extractModuleId(
                Path.of("/project/src/nested/deep/module.tsx"),
                srcRoot
            ),
            "Should handle nested paths");
    }

    @Test
    @DisplayName("Module ID extraction normalizes path separators")
    @EnabledOnOs(OS.WINDOWS)
    void testModuleIdExtractionNormalizesSeparators() {
        // Windows path separator normalization
        String moduleId = ModulePathResolver.extractModuleId(
            Path.of("C:\\project\\src\\components\\Button.tsx"),
            Path.of("C:\\project\\src")
        );

        assertTrue(moduleId.contains("/"),
            "Should use forward slashes");
        assertFalse(moduleId.contains("\\"),
            "Should not contain backslashes");
        assertEquals("components/Button", moduleId,
            "Should normalize Windows paths to Unix-style");
    }

    @Test
    @DisplayName("Non-existent files return path without extension")
    void testNonExistentFilesReturnPath() {
        Path srcDir = sourceRoot.resolve("src");
        Path fromFile = srcDir.resolve("App.tsx");

        // Import a file that doesn't exist
        Path resolved = resolver.resolve("./MissingFile", fromFile.toString(), srcDir).orElse(null);
        assertNotNull(resolved, "Should return path even if file doesn't exist");
        assertTrue(resolved.toString().contains("MissingFile"),
            "Path should contain the import name");
    }

    @Test
    @DisplayName("Empty or null import paths return empty")
    void testEmptyOrNullImportPathsReturnEmpty() {
        assertFalse(resolver.resolve(null, "/project/src/App.tsx", sourceRoot).isPresent(),
            "null import path should return empty");
        assertFalse(resolver.resolve("", "/project/src/App.tsx", sourceRoot).isPresent(),
            "empty import path should return empty");
        assertFalse(resolver.resolve("   ", "/project/src/App.tsx", sourceRoot).isPresent(),
            "blank import path should return empty");
    }

    @Test
    @DisplayName("Longest matching alias is selected")
    void testLongestMatchingAliasIsSelected() throws IOException {
        // Configure overlapping aliases
        resolver.addPathAlias("@", "src");
        resolver.addPathAlias("@common", "src/common/components");

        Path srcDir = sourceRoot.resolve("src");
        Path commonDir = srcDir.resolve("common/components");
        Files.createDirectories(commonDir);

        Path buttonFile = commonDir.resolve("Button.tsx");
        Files.writeString(buttonFile, "export const Button = () => {};");

        Path fromFile = srcDir.resolve("App.tsx");

        // Should use @common (longer) over @ (shorter)
        Path resolved = resolver.resolve("@common/Button", fromFile.toString(), sourceRoot).orElse(null);
        assertNotNull(resolved, "Should resolve with longest matching alias");
        assertEquals(buttonFile, resolved,
            "Should prefer @common over @ for @common/Button");
    }

    @Test
    @DisplayName("Absolute paths are returned as-is")
    void testAbsolutePathsAreReturnedAsIs() {
        Path absolutePath = Path.of("/absolute/path/to/module.ts");
        Path resolved = resolver.resolve("/absolute/path/to/module.ts", "/project/src/App.tsx", sourceRoot).orElse(null);

        assertNotNull(resolved, "Absolute path should be returned");
        assertEquals(absolutePath, resolved,
            "Should return absolute path unchanged");
    }

    @Test
    @DisplayName("File extension is tried automatically")
    void testFileExtensionIsTriedAutomatically() throws IOException {
        Path srcDir = sourceRoot.resolve("src");
        Path componentsDir = srcDir.resolve("components");
        Files.createDirectories(componentsDir);

        Path buttonTs = componentsDir.resolve("Button.ts");
        Files.writeString(buttonTs, "export const Button = () => {};");

        Path fromFile = srcDir.resolve("App.tsx");

        // Import without extension should find Button.ts
        Path resolved = resolver.resolve("./components/Button", fromFile.toString(), srcDir).orElse(null);
        assertNotNull(resolved, "Should find file with extension");
        assertEquals(buttonTs, resolved,
            "Should resolve ./components/Button to components/Button.ts");
    }
}
