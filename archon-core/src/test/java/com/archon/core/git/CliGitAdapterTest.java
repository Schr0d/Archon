package com.archon.core.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CliGitAdapterTest {

    private final CliGitAdapter adapter = new CliGitAdapter();

    @Test
    void testIsGitAvailable() {
        assertTrue(adapter.isGitAvailable(), "git should be available on the system");
    }

    @Test
    void testDiscoverRepoRoot(@TempDir Path tempDir) throws Exception {
        // Initialize a git repository
        runGitCommand(tempDir, "git", "init");

        Path discoveredRoot = adapter.discoverRepoRoot(tempDir);

        assertEquals(tempDir.toAbsolutePath().normalize().toString(),
                    discoveredRoot.toAbsolutePath().normalize().toString());
    }

    @Test
    void testGetChangedFiles(@TempDir Path tempDir) throws Exception {
        // Initialize repository
        runGitCommand(tempDir, "git", "init");
        configureGit(tempDir);

        // Create initial commit
        Path file1 = tempDir.resolve("test.txt");
        Files.writeString(file1, "initial content");
        runGitCommand(tempDir, "git", "add", "test.txt");
        runGitCommand(tempDir, "git", "commit", "-m", "Initial commit");

        // Modify file
        Files.writeString(file1, "modified content");
        runGitCommand(tempDir, "git", "add", "test.txt");
        runGitCommand(tempDir, "git", "commit", "-m", "Modify file");

        // Get changed files between first and second commit
        List<String> changedFiles = adapter.getChangedFiles(tempDir, "HEAD~1", "HEAD");

        assertEquals(1, changedFiles.size());
        assertTrue(changedFiles.contains("test.txt"));
    }

    @Test
    void testGetFileContent(@TempDir Path tempDir) throws Exception {
        // Initialize repository
        runGitCommand(tempDir, "git", "init");
        configureGit(tempDir);

        // Create and commit a file
        Path file1 = tempDir.resolve("content.txt");
        String content = "Hello, World!";
        Files.writeString(file1, content);
        runGitCommand(tempDir, "git", "add", "content.txt");
        runGitCommand(tempDir, "git", "commit", "-m", "Add content");

        // Get file content at HEAD
        String retrievedContent = adapter.getFileContent(tempDir, "HEAD", "content.txt");

        assertEquals(content, retrievedContent);
    }

    @Test
    void testGetFileContent_FileDoesNotExist_ReturnsNull(@TempDir Path tempDir) throws Exception {
        // Initialize repository
        runGitCommand(tempDir, "git", "init");
        configureGit(tempDir);

        // Create an empty commit
        Path dummyFile = tempDir.resolve("dummy.txt");
        Files.writeString(dummyFile, "dummy");
        runGitCommand(tempDir, "git", "add", "dummy.txt");
        runGitCommand(tempDir, "git", "commit", "-m", "Initial commit");

        // Try to get content of a file that doesn't exist
        String content = adapter.getFileContent(tempDir, "HEAD", "nonexistent.txt");

        assertNull(content);
    }

    @Test
    void testResolveRef(@TempDir Path tempDir) throws Exception {
        // Initialize repository
        runGitCommand(tempDir, "git", "init");
        configureGit(tempDir);

        // Create initial commit
        Path file1 = tempDir.resolve("test.txt");
        Files.writeString(file1, "content");
        runGitCommand(tempDir, "git", "add", "test.txt");
        runGitCommand(tempDir, "git", "commit", "-m", "Initial commit");

        // Resolve HEAD to SHA
        String sha = adapter.resolveRef(tempDir, "HEAD");

        assertNotNull(sha);
        assertEquals(40, sha.length()); // Git SHA-1 is 40 hex characters
        assertTrue(sha.matches("[a-f0-9]+"));
    }

    @Test
    void testGetCommitCount(@TempDir Path tempDir) throws Exception {
        // Initialize repository
        runGitCommand(tempDir, "git", "init");
        configureGit(tempDir);

        // Create initial commit
        Path file1 = tempDir.resolve("test.txt");
        Files.writeString(file1, "initial");
        runGitCommand(tempDir, "git", "add", "test.txt");
        runGitCommand(tempDir, "git", "commit", "-m", "Commit 1");

        // Create second commit
        Files.writeString(file1, "modified");
        runGitCommand(tempDir, "git", "add", "test.txt");
        runGitCommand(tempDir, "git", "commit", "-m", "Commit 2");

        // Get commit count between HEAD~1 and HEAD
        int count = adapter.getCommitCount(tempDir, "HEAD~1", "HEAD");

        assertEquals(1, count);
    }

    // Helper methods

    private void configureGit(Path tempDir) throws Exception {
        // Configure git user for commits
        runGitCommand(tempDir, "git", "config", "user.name", "Test User");
        runGitCommand(tempDir, "git", "config", "user.email", "test@example.com");
    }

    private void runGitCommand(Path dir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        process.waitFor();

        // Read output to avoid blocking
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
                // discard output
            }
        }
    }
}
