package com.archon.java;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Auto-detects Maven/Gradle multi-module project structure.
 * Returns source roots for each detected module.
 */
public class ModuleDetector {

    private static final Pattern MAVEN_MODULE_PATTERN = Pattern.compile("<module>([^<]+)</module>");

    public List<SourceRoot> detectModules(Path projectRoot) {
        Path pomFile = projectRoot.resolve("pom.xml");
        if (Files.exists(pomFile)) {
            return detectMavenModules(projectRoot, pomFile);
        }
        // Fallback: treat as single module
        Path defaultSourceRoot = projectRoot.resolve("src/main/java");
        if (Files.isDirectory(defaultSourceRoot)) {
            return List.of(new SourceRoot("", defaultSourceRoot));
        }
        return Collections.emptyList();
    }

    private List<SourceRoot> detectMavenModules(Path projectRoot, Path pomFile) {
        List<String> moduleNames = parseMavenModules(pomFile);
        if (moduleNames.isEmpty()) {
            // Single module project
            Path defaultSourceRoot = projectRoot.resolve("src/main/java");
            if (Files.isDirectory(defaultSourceRoot)) {
                return List.of(new SourceRoot("", defaultSourceRoot));
            }
            return Collections.emptyList();
        }

        List<SourceRoot> roots = new ArrayList<>();
        for (String moduleName : moduleNames) {
            Path sourceDir = projectRoot.resolve(moduleName).resolve("src/main/java");
            if (Files.isDirectory(sourceDir)) {
                roots.add(new SourceRoot(moduleName, sourceDir));
            }
        }
        return roots;
    }

    private List<String> parseMavenModules(Path pomFile) {
        try {
            String content = Files.readString(pomFile);
            List<String> modules = new ArrayList<>();
            Matcher matcher = MAVEN_MODULE_PATTERN.matcher(content);
            while (matcher.find()) {
                modules.add(matcher.group(1).trim());
            }
            return modules;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    public static class SourceRoot {
        private final String moduleName;
        private final Path path;

        public SourceRoot(String moduleName, Path path) {
            this.moduleName = moduleName;
            this.path = path;
        }

        public String getModuleName() { return moduleName; }
        public Path getPath() { return path; }
    }
}
