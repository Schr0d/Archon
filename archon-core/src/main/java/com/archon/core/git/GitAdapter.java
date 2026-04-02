package com.archon.core.git;

import java.nio.file.Path;
import java.util.List;

/**
 * Interface for git operations.
 * Implementations provide access to git repository state for diff analysis.
 */
public interface GitAdapter {

    /**
     * Get list of files changed between two refs.
     * Returns relative file paths (forward slashes).
     */
    List<String> getChangedFiles(Path repoRoot, String baseRef, String headRef);

    /**
     * Get file content at a specific ref.
     * Returns null if file does not exist at that ref.
     */
    String getFileContent(Path repoRoot, String ref, String filePath);

    /**
     * Resolve a ref (branch name, tag, SHA, HEAD~3, etc.) to a commit SHA.
     * Delegates to git rev-parse.
     */
    String resolveRef(Path repoRoot, String ref);

    /**
     * Discover the git repository root from an arbitrary path.
     * Runs git rev-parse --show-toplevel.
     */
    Path discoverRepoRoot(Path path);

    /**
     * Get the number of commits between base and head.
     */
    int getCommitCount(Path repoRoot, String baseRef, String headRef);

    /**
     * Check if git is available on the system.
     */
    boolean isGitAvailable();
}
