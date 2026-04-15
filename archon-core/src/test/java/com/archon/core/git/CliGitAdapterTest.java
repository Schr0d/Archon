package com.archon.core.git;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Test
    void testGetWorkingTreeChanges_unstagedFile(@TempDir Path tempDir) throws Exception {
        // Initialize repository
        runGitCommand(tempDir, "git", "init");
        configureGit(tempDir);

        // Create and commit a file
        Path file1 = tempDir.resolve("app.java");
        Files.writeString(file1, "initial");
        runGitCommand(tempDir, "git", "add", "app.java");
        runGitCommand(tempDir, "git", "commit", "-m", "Initial commit");

        // Modify file without staging
        Files.writeString(file1, "modified");

        List<String> changes = adapter.getWorkingTreeChanges(tempDir);

        assertEquals(1, changes.size());
        assertTrue(changes.contains("app.java"));
    }

    @Test
    void testGetWorkingTreeChanges_stagedFile(@TempDir Path tempDir) throws Exception {
        // Initialize repository
        runGitCommand(tempDir, "git", "init");
        configureGit(tempDir);

        // Create and commit a file
        Path file1 = tempDir.resolve("app.java");
        Files.writeString(file1, "initial");
        runGitCommand(tempDir, "git", "add", "app.java");
        runGitCommand(tempDir, "git", "commit", "-m", "Initial commit");

        // Modify and stage
        Files.writeString(file1, "modified");
        runGitCommand(tempDir, "git", "add", "app.java");

        List<String> changes = adapter.getWorkingTreeChanges(tempDir);

        assertEquals(1, changes.size());
        assertTrue(changes.contains("app.java"));
    }

    @Test
    void testGetWorkingTreeChanges_noChanges(@TempDir Path tempDir) throws Exception {
        // Initialize repository
        runGitCommand(tempDir, "git", "init");
        configureGit(tempDir);

        // Create and commit a file (clean working tree)
        Path file1 = tempDir.resolve("app.java");
        Files.writeString(file1, "initial");
        runGitCommand(tempDir, "git", "add", "app.java");
        runGitCommand(tempDir, "git", "commit", "-m", "Initial commit");

        List<String> changes = adapter.getWorkingTreeChanges(tempDir);

        assertTrue(changes.isEmpty());
    }

    // ---- Branch, SHA, Stash, Checkout tests ----

    @Nested
    @DisplayName("getCurrentBranch")
    class GetCurrentBranch {

        @Test
        @DisplayName("returns branch name when on a branch")
        void testGetCurrentBranchOnBranch(@TempDir Path tempDir) throws Exception {
            runGitCommand(tempDir, "git", "init");
            configureGit(tempDir);
            createInitialCommit(tempDir);

            // On default branch (main or master depending on git version)
            String branch = adapter.getCurrentBranch(tempDir);
            assertNotNull(branch, "Should return branch name when on a branch");
            assertTrue(branch.equals("main") || branch.equals("master"),
                "Expected main or master, got: " + branch);
        }

        @Test
        @DisplayName("returns null when in detached HEAD state")
        void testGetCurrentBranchDetached(@TempDir Path tempDir) throws Exception {
            Path repo = setupRepoWithCommit(tempDir);
            String sha = adapter.getHeadSha(repo);

            // Checkout the SHA directly to enter detached HEAD
            runGitCommand(repo, "git", "checkout", sha);

            String branch = adapter.getCurrentBranch(repo);
            assertNull(branch, "Should return null for detached HEAD");
        }
    }

    @Nested
    @DisplayName("getHeadSha")
    class GetHeadSha {

        @Test
        @DisplayName("returns valid 40-character SHA-1")
        void testGetHeadSha(@TempDir Path tempDir) throws Exception {
            setupRepoWithCommit(tempDir);

            String sha = adapter.getHeadSha(tempDir);
            assertNotNull(sha);
            assertEquals(40, sha.length(), "SHA should be 40 hex characters");
            assertTrue(sha.matches("[a-f0-9]+"), "SHA should be hex: " + sha);
        }
    }

    @Nested
    @DisplayName("stashPush / stashPop")
    class StashOperations {

        @Test
        @DisplayName("stashPush returns stash ref when working tree is dirty")
        void testStashPushDirtyTree(@TempDir Path tempDir) throws Exception {
            Path repo = setupRepoWithCommit(tempDir);

            // Modify file to create dirty tree
            Files.writeString(repo.resolve("test.txt"), "modified content");

            String stashRef = adapter.stashPush(repo);
            assertNotNull(stashRef, "stashPush should return stash ref for dirty tree");

            // Working tree should now be clean
            List<String> changes = adapter.getWorkingTreeChanges(repo);
            assertTrue(changes.isEmpty(), "Working tree should be clean after stash");
        }

        @Test
        @DisplayName("stashPush returns null when working tree is clean")
        void testStashPushCleanTree(@TempDir Path tempDir) throws Exception {
            setupRepoWithCommit(tempDir);

            String stashRef = adapter.stashPush(tempDir);
            assertNull(stashRef, "stashPush should return null for clean tree");
        }

        @Test
        @DisplayName("stashPop restores stashed changes")
        void testStashPopRestoresChanges(@TempDir Path tempDir) throws Exception {
            Path repo = setupRepoWithCommit(tempDir);

            // Modify, stash, then pop
            String modifiedContent = "modified and restored";
            Files.writeString(repo.resolve("test.txt"), modifiedContent);

            String stashRef = adapter.stashPush(repo);
            assertNotNull(stashRef, "Stash should succeed");

            adapter.stashPop(repo);

            // Content should be restored
            String restored = Files.readString(repo.resolve("test.txt"));
            assertEquals(modifiedContent, restored, "stashPop should restore original content");
        }

        @Test
        @DisplayName("stashPush includes untracked files")
        void testStashPushIncludesUntracked(@TempDir Path tempDir) throws Exception {
            Path repo = setupRepoWithCommit(tempDir);

            // Create a new untracked file
            Files.writeString(repo.resolve("newfile.txt"), "untracked content");

            String stashRef = adapter.stashPush(repo);
            assertNotNull(stashRef, "stashPush should include untracked files");

            // Untracked file should be gone after stash
            assertFalse(Files.exists(repo.resolve("newfile.txt")),
                "Untracked file should be stashed");
        }
    }

    @Nested
    @DisplayName("checkout")
    class Checkout {

        @Test
        @DisplayName("checkout switches branches and updates working tree")
        void testCheckoutBranch(@TempDir Path tempDir) throws Exception {
            Path repo = setupRepoWithCommit(tempDir);

            // Create a second commit with different content
            Files.writeString(repo.resolve("test.txt"), "second version");
            runGitCommand(repo, "git", "add", "test.txt");
            runGitCommand(repo, "git", "commit", "-m", "second commit");

            // Get the first commit SHA
            String firstSha = adapter.resolveRef(repo, "HEAD~1");

            // Checkout first commit - content should revert
            adapter.checkout(repo, firstSha);
            String content = Files.readString(repo.resolve("test.txt"));
            assertEquals("hello", content, "Checkout should restore old content");
        }

        @Test
        @DisplayName("checkout throws on invalid ref")
        void testCheckoutInvalidRef(@TempDir Path tempDir) throws Exception {
            setupRepoWithCommit(tempDir);

            assertThrows(GitException.class, () ->
                adapter.checkout(tempDir, "nonexistent-branch-name-xyz"),
                "Checkout of invalid ref should throw GitException"
            );
        }
    }

    // ---- Shared test setup helpers ----

    private Path setupRepoWithCommit(Path tempDir) throws Exception {
        runGitCommand(tempDir, "git", "init");
        configureGit(tempDir);
        createInitialCommit(tempDir);
        return tempDir;
    }

    private void createInitialCommit(Path dir) throws Exception {
        Files.writeString(dir.resolve("test.txt"), "hello");
        runGitCommand(dir, "git", "add", "test.txt");
        runGitCommand(dir, "git", "commit", "-m", "initial");
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
