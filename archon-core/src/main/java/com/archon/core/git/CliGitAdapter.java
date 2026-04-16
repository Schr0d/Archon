package com.archon.core.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * CLI-based git adapter using ProcessBuilder.
 * Executes git commands via the system git binary.
 */
public class CliGitAdapter implements GitAdapter {

    private static final long TIMEOUT_SECONDS = 60;

    @Override
    public List<String> getChangedFiles(Path repoRoot, String baseRef, String headRef) {
        // Use diff-tree which is faster than diff for commit ranges
        List<String> result = execute(
            repoRoot,
            "git", "diff-tree", "--no-commit-id", "--name-only", "-r", baseRef, headRef
        );
        // Filter empty lines
        List<String> files = new ArrayList<>();
        for (String line : result) {
            if (!line.trim().isEmpty()) {
                files.add(line.trim());
            }
        }
        return files;
    }

    @Override
    public String getFileContent(Path repoRoot, String ref, String filePath) {
        try {
            List<String> result = execute(
                repoRoot,
                "git", "show", ref + ":" + filePath
            );
            return String.join("\n", result);
        } catch (GitException e) {
            // File doesn't exist at this ref
            return null;
        }
    }

    @Override
    public String resolveRef(Path repoRoot, String ref) {
        List<String> result = execute(
            repoRoot,
            "git", "rev-parse", ref
        );
        if (result.isEmpty()) {
            throw new GitException("Cannot resolve ref: " + ref);
        }
        return result.get(0).trim();
    }

    @Override
    public Path discoverRepoRoot(Path path) {
        List<String> result = execute(
            path,
            "git", "rev-parse", "--show-toplevel"
        );
        if (result.isEmpty()) {
            throw new GitException("Cannot discover repository root from: " + path);
        }
        return Path.of(result.get(0).trim());
    }

    @Override
    public int getCommitCount(Path repoRoot, String baseRef, String headRef) {
        List<String> result = execute(
            repoRoot,
            "git", "rev-list", "--count", baseRef + ".." + headRef
        );
        if (result.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(result.get(0).trim());
        } catch (NumberFormatException e) {
            throw new GitException("Cannot parse commit count: " + result.get(0), e);
        }
    }

    @Override
    public boolean isGitAvailable() {
        try {
            execute(Path.of("."), "git", "--version");
            return true;
        } catch (GitException e) {
            return false;
        }
    }

    @Override
    public List<String> getWorkingTreeChanges(Path repoRoot) {
        // Get unstaged changes vs HEAD
        List<String> unstaged = execute(
            repoRoot,
            "git", "diff", "--name-only", "HEAD"
        );
        // Get staged changes vs HEAD
        List<String> staged = execute(
            repoRoot,
            "git", "diff", "--cached", "--name-only", "HEAD"
        );
        // Deduplicate
        Set<String> all = new LinkedHashSet<>();
        for (String line : unstaged) {
            if (!line.trim().isEmpty()) all.add(line.trim());
        }
        for (String line : staged) {
            if (!line.trim().isEmpty()) all.add(line.trim());
        }
        return new ArrayList<>(all);
    }

    @Override
    public String stashPush(Path repoRoot) {
        List<String> output = execute(
            repoRoot,
            "git", "stash", "push", "--include-untracked"
        );
        // git stash returns exit code 0 even when tree is clean
        // Must check output text to distinguish
        for (String line : output) {
            if (line.contains("No local changes to save")) {
                return null;
            }
        }
        return "stash@{0}";
    }

    @Override
    public void stashPop(Path repoRoot) {
        execute(
            repoRoot,
            "git", "stash", "pop"
        );
    }

    @Override
    public void checkout(Path repoRoot, String ref) {
        execute(
            repoRoot,
            "git", "checkout", ref
        );
    }

    @Override
    public String getCurrentBranch(Path repoRoot) {
        List<String> result = execute(
            repoRoot,
            "git", "rev-parse", "--abbrev-ref", "HEAD"
        );
        if (result.isEmpty()) {
            throw new GitException("Cannot determine current branch");
        }
        String branch = result.get(0).trim();
        // "HEAD" means detached HEAD
        return "HEAD".equals(branch) ? null : branch;
    }

    @Override
    public String getHeadSha(Path repoRoot) {
        return resolveRef(repoRoot, "HEAD");
    }

    /**
     * Execute a git command and return the output lines.
     *
     * @param workingDir working directory for the command
     * @param command command and arguments
     * @return list of output lines
     * @throws GitException if the command fails
     */
    private List<String> execute(Path workingDir, String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workingDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Drain output concurrently to prevent pipe buffer deadlock.
            // Without this, waitFor blocks forever if the subprocess writes
            // more than the OS pipe buffer (~4KB on Windows) before exiting.
            List<String> output = new ArrayList<>();
            Thread readerThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.add(line);
                    }
                } catch (IOException ignored) {
                    // Stream closed on destroyForcibly — expected on timeout
                }
            });
            readerThread.setDaemon(true);
            readerThread.start();

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                readerThread.join(2000);
                throw new GitException("Command timed out after " + TIMEOUT_SECONDS + "s: " + String.join(" ", command));
            }

            readerThread.join(5000);

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new GitException("Command failed with exit code " + exitCode + ": " + String.join(" ", command));
            }

            return output;

        } catch (IOException e) {
            throw new GitException("Failed to execute command: " + String.join(" ", command), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException("Command interrupted: " + String.join(" ", command), e);
        }
    }
}
