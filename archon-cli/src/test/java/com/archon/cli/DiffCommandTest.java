package com.archon.cli;

import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DiffCommandTest {

    private DiffCommand command;

    @BeforeEach
    void setUp() {
        command = new DiffCommand();
    }

    // ---- Existing tests ----

    @Nested
    @DisplayName("View Flag")
    class ViewFlag {

        @Test
        @DisplayName("view flag defaults to false")
        void viewFlag_defaultsToFalse() {
            assertFalse(command.view);
        }

        @Test
        @DisplayName("view flag can be set to true")
        void viewFlag_canBeSetToTrue() {
            command.view = true;
            assertTrue(command.view);
        }

        @Test
        @DisplayName("view flag enables web viewer mode with params list")
        void viewFlag_enablesWebViewerMode() {
            command.view = true;
            command.params = List.of("HEAD~1", "HEAD", ".");

            assertTrue(command.view, "view flag should be true");
            assertEquals(3, command.params.size());
            assertEquals("HEAD~1", command.params.get(0));
            assertEquals("HEAD", command.params.get(1));
            assertEquals(".", command.params.get(2));
        }
    }

    @Nested
    @DisplayName("Parameters")
    class Parameters {

        @Test
        @DisplayName("params list can be set with base ref only")
        void params_baseRefOnly() {
            command.params = List.of("main");
            assertEquals(List.of("main"), command.params);
        }

        @Test
        @DisplayName("params list can be set with base and head ref")
        void params_baseAndHeadRef() {
            command.params = List.of("main", "develop");
            assertEquals(List.of("main", "develop"), command.params);
        }

        @Test
        @DisplayName("params list can be set with all three args")
        void params_allThree() {
            command.params = List.of("main", "develop", "/path/to/project");
            assertEquals(List.of("main", "develop", "/path/to/project"), command.params);
        }

        @Test
        @DisplayName("params list defaults to null (zero-arg mode)")
        void params_defaultsToNull() {
            assertNull(command.params);
        }
    }

    // ---- New tests for batch-parse partition logic ----

    /** A plugin that supports batch parsing (like JsPlugin). */
    static class BatchPlugin implements LanguagePlugin {
        @Override
        public Set<String> fileExtensions() { return Set.of("js", "ts", "jsx", "tsx"); }

        @Override
        public ParseResult parseFromContent(String filePath, String content, ParseContext context) {
            return new ParseResult(Set.of(), List.of(), List.of());
        }

        @Override
        public boolean supportsBatchParse() { return true; }
    }

    /** A plugin that does NOT support batch parsing (like JavaPlugin). */
    static class PerFilePlugin implements LanguagePlugin {
        @Override
        public Set<String> fileExtensions() { return Set.of("java"); }

        @Override
        public ParseResult parseFromContent(String filePath, String content, ParseContext context) {
            return new ParseResult(Set.of(), List.of(), List.of());
        }

        @Override
        public boolean supportsBatchParse() { return false; }
    }

    @Nested
    @DisplayName("partitionChangedFiles")
    class PartitionChangedFiles {

        @Test
        @DisplayName("JS files go to batch partition")
        void testPartitionChangedFilesBatchJs() {
            List<LanguagePlugin> plugins = List.of(new BatchPlugin(), new PerFilePlugin());
            Set<String> extensions = Set.of("js", "ts", "java");

            Map<Boolean, List<String>> result = command.partitionChangedFiles(
                List.of("src/App.js", "src/utils.ts"),
                plugins, extensions
            );

            assertEquals(2, result.get(true).size(), "Both JS/TS files should be batch");
            assertTrue(result.get(true).contains("src/App.js"));
            assertTrue(result.get(true).contains("src/utils.ts"));
            assertTrue(result.get(false).isEmpty(), "No per-file entries expected");
        }

        @Test
        @DisplayName("Java files go to per-file partition")
        void testPartitionChangedFilesPerFileJava() {
            List<LanguagePlugin> plugins = List.of(new BatchPlugin(), new PerFilePlugin());
            Set<String> extensions = Set.of("js", "ts", "java");

            Map<Boolean, List<String>> result = command.partitionChangedFiles(
                List.of("src/main/Foo.java", "src/main/Bar.java"),
                plugins, extensions
            );

            assertEquals(2, result.get(false).size(), "Both Java files should be per-file");
            assertTrue(result.get(false).contains("src/main/Foo.java"));
            assertTrue(result.get(false).contains("src/main/Bar.java"));
            assertTrue(result.get(true).isEmpty(), "No batch entries expected");
        }

        @Test
        @DisplayName("Mixed files are partitioned correctly")
        void testPartitionChangedFilesMixed() {
            List<LanguagePlugin> plugins = List.of(new BatchPlugin(), new PerFilePlugin());
            Set<String> extensions = Set.of("js", "ts", "java");

            Map<Boolean, List<String>> result = command.partitionChangedFiles(
                List.of("src/App.js", "src/main/Foo.java", "src/utils.ts", "src/main/Bar.java"),
                plugins, extensions
            );

            List<String> batch = result.get(true);
            List<String> perFile = result.get(false);

            assertEquals(2, batch.size(), "JS/TS files should be batch");
            assertTrue(batch.contains("src/App.js"));
            assertTrue(batch.contains("src/utils.ts"));

            assertEquals(2, perFile.size(), "Java files should be per-file");
            assertTrue(perFile.contains("src/main/Foo.java"));
            assertTrue(perFile.contains("src/main/Bar.java"));
        }

        @Test
        @DisplayName("Files with unknown extensions are ignored")
        void testPartitionChangedFilesIgnoresUnknownExtensions() {
            List<LanguagePlugin> plugins = List.of(new BatchPlugin(), new PerFilePlugin());
            Set<String> extensions = Set.of("js", "ts", "java");

            Map<Boolean, List<String>> result = command.partitionChangedFiles(
                List.of("README.md", "build.gradle.kts", "src/App.js"),
                plugins, extensions
            );

            List<String> batch = result.get(true);
            List<String> perFile = result.get(false);

            assertEquals(1, batch.size(), "Only JS file should be in batch");
            assertTrue(batch.contains("src/App.js"));
            assertTrue(perFile.isEmpty(), "Unknown extensions should not appear in per-file");
        }

        @Test
        @DisplayName("Empty file list returns empty partitions")
        void testPartitionChangedFilesEmpty() {
            List<LanguagePlugin> plugins = List.of(new BatchPlugin());
            Set<String> extensions = Set.of("js");

            Map<Boolean, List<String>> result = command.partitionChangedFiles(
                List.of(), plugins, extensions
            );

            assertTrue(result.get(true).isEmpty());
            assertTrue(result.get(false).isEmpty());
        }
    }

    @Nested
    @DisplayName("extractJsonString")
    class ExtractJsonString {

        @Test
        @DisplayName("extracts string value from JSON")
        void testExtractJsonString() {
            String json = "{\"branch\":\"main\",\"sha\":\"abc123\",\"stashRef\":null}";

            assertEquals("main", command.extractJsonString(json, "branch"));
            assertEquals("abc123", command.extractJsonString(json, "sha"));
            assertNull(command.extractJsonString(json, "stashRef"));
        }

        @Test
        @DisplayName("returns null for missing key")
        void testExtractJsonStringMissingKey() {
            String json = "{\"branch\":\"main\"}";
            assertNull(command.extractJsonString(json, "sha"));
        }

        @Test
        @DisplayName("handles empty JSON object")
        void testExtractJsonStringEmpty() {
            assertNull(command.extractJsonString("{}", "branch"));
        }

        @Test
        @DisplayName("handles null value in JSON")
        void testExtractJsonStringNullValue() {
            String json = "{\"branch\":null,\"sha\":\"abc\"}";
            assertNull(command.extractJsonString(json, "branch"));
            assertEquals("abc", command.extractJsonString(json, "sha"));
        }

        @Test
        @DisplayName("handles string value with whitespace after colon")
        void testExtractJsonStringWithWhitespace() {
            String json = "{\"branch\": \"feature/test\", \"sha\":\"def456\"}";
            assertEquals("feature/test", command.extractJsonString(json, "branch"));
            assertEquals("def456", command.extractJsonString(json, "sha"));
        }
    }

    @Nested
    @DisplayName("Lock File Lifecycle")
    class LockFileLifecycle {

        @Test
        @DisplayName("writeRestoreLockFile creates file and deleteRestoreLockFile removes it")
        void testWriteAndDeleteLockFile(@TempDir Path tempDir) throws Exception {
            command.writeRestoreLockFile(tempDir, "main", "abc123def456", "stash@{0}");

            Path lockFile = tempDir.resolve(".archon-restore.json");
            assertTrue(Files.exists(lockFile), "Lock file should be created");

            String content = Files.readString(lockFile);
            assertTrue(content.contains("\"branch\":\"main\""), "Should contain branch");
            assertTrue(content.contains("\"sha\":\"abc123def456\""), "Should contain sha");
            assertTrue(content.contains("\"stashRef\":\"stash@{0}\""), "Should contain stashRef");
            assertTrue(content.contains("\"timestamp\":"), "Should contain timestamp");

            command.deleteRestoreLockFile(tempDir);
            assertFalse(Files.exists(lockFile), "Lock file should be deleted");
        }

        @Test
        @DisplayName("writeRestoreLockFile with null branch writes null")
        void testWriteLockFileNullBranch(@TempDir Path tempDir) throws Exception {
            command.writeRestoreLockFile(tempDir, null, "abc123", null);

            Path lockFile = tempDir.resolve(".archon-restore.json");
            assertTrue(Files.exists(lockFile));

            String content = Files.readString(lockFile);
            assertTrue(content.contains("\"branch\":null"), "Branch should be null");
            assertTrue(content.contains("\"stashRef\":null"), "stashRef should be null");
            assertTrue(content.contains("\"sha\":\"abc123\""), "SHA should be present");
        }

        @Test
        @DisplayName("deleteRestoreLockFile is safe when file does not exist")
        void testDeleteLockFileNoop(@TempDir Path tempDir) {
            Path lockFile = tempDir.resolve(".archon-restore.json");
            assertFalse(Files.exists(lockFile));

            // Should not throw
            assertDoesNotThrow(() -> command.deleteRestoreLockFile(tempDir));
        }
    }
}
