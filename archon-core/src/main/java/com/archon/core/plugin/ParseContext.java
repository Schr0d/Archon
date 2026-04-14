package com.archon.core.plugin;

import java.nio.file.Path;
import java.util.Set;

/**
 * Input context provided to LanguagePlugin.parseFromContent().
 * Encapsulates source tree metadata without exposing full parse parameters.
 */
public class ParseContext {

    /** Maximum file size for parsing (1 MB). */
    public static final long MAX_FILE_SIZE = 1024L * 1024L;

    private final Path sourceRoot;
    private final Set<String> fileExtensions;

    public ParseContext(Path sourceRoot, Set<String> fileExtensions) {
        this.sourceRoot = sourceRoot;
        this.fileExtensions = Set.copyOf(fileExtensions);
    }

    public Path getSourceRoot() {
        return sourceRoot;
    }

    public Set<String> getFileExtensions() {
        return fileExtensions;
    }

    public boolean hasExtension(String extension) {
        return fileExtensions.contains(extension);
    }
}
