package com.archon.java;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ClassDirectoryFinder {

    private ClassDirectoryFinder() {}

    public static List<Path> findClassDirectories(Path projectRoot) {
        List<Path> classDirs = new ArrayList<>();

        // Maven single-module: target/classes/
        addIfExists(projectRoot.resolve("target").resolve("classes"), classDirs);
        // Gradle single-module: build/classes/java/main/
        addIfExists(projectRoot.resolve("build").resolve("classes").resolve("java").resolve("main"), classDirs);

        // Multi-module: scan immediate subdirectories
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(projectRoot, Files::isDirectory)) {
            for (Path subdir : stream) {
                String name = subdir.getFileName().toString();
                if (name.startsWith(".") || name.equals("target") || name.equals("build")) continue;

                addIfExists(subdir.resolve("target").resolve("classes"), classDirs);
                addIfExists(subdir.resolve("build").resolve("classes").resolve("java").resolve("main"), classDirs);

                // One more level for nested multi-module
                try (DirectoryStream<Path> nested = Files.newDirectoryStream(subdir, Files::isDirectory)) {
                    for (Path nestedDir : nested) {
                        String nestedName = nestedDir.getFileName().toString();
                        if (nestedName.startsWith(".") || nestedName.equals("target") || nestedName.equals("build")) continue;
                        addIfExists(nestedDir.resolve("target").resolve("classes"), classDirs);
                    }
                }
            }
        } catch (IOException e) {
            // Return what we have
        }

        return classDirs;
    }

    private static void addIfExists(Path path, List<Path> results) {
        if (Files.isDirectory(path)) {
            results.add(path.toAbsolutePath());
        }
    }
}