package com.archon.core.git;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * CLI-based git adapter using ProcessBuilder.
 * Executes git commands via the system git binary.
 */
public class CliGitAdapter implements GitAdapter {

    private static final long TIMEOUT_SECONDS = 30;

    @Override
    public List<String> getChangedFiles(Path repoRoot, String baseRef, String headRef) {
        List<String> result = execute(
            repoRoot,
            "git", "diff", "--name-only", baseRef, headRef
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

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new GitException("Command timed out after " + TIMEOUT_SECONDS + "s: " + String.join(" ", command));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new GitException("Command failed with exit code " + exitCode + ": " + String.join(" ", command));
            }

            List<String> output = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
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
