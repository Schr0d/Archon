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

    /**
     * Get list of files changed in the working tree (staged + unstaged) vs HEAD.
     * Returns relative file paths (forward slashes), deduplicated.
     */
    default List<String> getWorkingTreeChanges(Path repoRoot) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /** Stash all changes (staged, unstaged, untracked). Returns stash ref or null if nothing to stash. */
    default String stashPush(Path repoRoot) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /** Restore previously stashed changes. */
    default void stashPop(Path repoRoot) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /** Checkout a ref, updating the working tree. */
    default void checkout(Path repoRoot, String ref) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /** Get current branch name, or null if detached HEAD. */
    default String getCurrentBranch(Path repoRoot) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /** Get current HEAD commit SHA. */
    default String getHeadSha(Path repoRoot) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
