package com.archon.java;

import com.archon.core.plugin.BlindSpot;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Heuristic detection of dynamic dependencies:
 * reflection, event-driven, dynamic-proxy, and MyBatis XML.
 */
public class BlindSpotDetector {

    public List<BlindSpot> detect(Path sourcePath) {
        List<BlindSpot> spots = new ArrayList<>();
        if (!Files.exists(sourcePath)) {
            return spots;
        }

        if (Files.isRegularFile(sourcePath) && sourcePath.toString().endsWith(".java")) {
            scanJavaFile(sourcePath, spots);
        } else if (Files.isDirectory(sourcePath)) {
            scanDirectoryForJava(sourcePath, spots);
            scanForMyBatisXml(sourcePath, spots);
        }

        return spots;
    }

    private void scanDirectoryForJava(Path dir, List<BlindSpot> spots) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".java")) {
                        scanJavaFile(file, spots);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Skip directories that can't be walked
        }
    }

    private void scanJavaFile(Path javaFile, List<BlindSpot> spots) {
        try {
            String content = Files.readString(javaFile);
            String filePath = javaFile.toString();

            int lineNumber = 1;
            for (String line : content.split("\n")) {
                checkLineForPatterns(line, filePath, lineNumber, spots);
                lineNumber++;
            }
        } catch (IOException e) {
            // Skip files that can't be read
        }
    }

    private void checkLineForPatterns(String line, String file, int lineNumber,
                                       List<BlindSpot> spots) {
        String trimmed = line.trim();

        if (trimmed.contains("Class.forName")) {
            spots.add(new BlindSpot("reflection", file + ":" + lineNumber, "Class.forName"));
        }
        if (trimmed.contains("Method.invoke") || trimmed.matches(".*\\w\\.invoke\\s*\\(.*")) {
            spots.add(new BlindSpot("reflection", file + ":" + lineNumber, "Method.invoke"));
        }
        if (trimmed.contains("getDeclaredMethod")) {
            spots.add(new BlindSpot("reflection", file + ":" + lineNumber, "getDeclaredMethod"));
        }
        if (trimmed.contains("getDeclaredField")) {
            spots.add(new BlindSpot("reflection", file + ":" + lineNumber, "getDeclaredField"));
        }

        if (trimmed.contains("@EventListener")) {
            spots.add(new BlindSpot("event-driven", file + ":" + lineNumber, "@EventListener"));
        }
        if (trimmed.contains("@Subscribe")) {
            spots.add(new BlindSpot("event-driven", file + ":" + lineNumber, "@Subscribe"));
        }
        if (trimmed.contains("ApplicationEventPublisher")) {
            spots.add(new BlindSpot("event-driven", file + ":" + lineNumber, "ApplicationEventPublisher"));
        }
        if (trimmed.contains("publishEvent")) {
            spots.add(new BlindSpot("event-driven", file + ":" + lineNumber, "publishEvent"));
        }

        if (trimmed.contains("Proxy.newProxyInstance")) {
            spots.add(new BlindSpot("dynamic-proxy", file + ":" + lineNumber, "Proxy.newProxyInstance"));
        }
    }

    private void scanForMyBatisXml(Path dir, List<BlindSpot> spots) {
        PathMatcher mapperMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*Mapper.xml");
        PathMatcher daoMatcher = FileSystems.getDefault().getPathMatcher("glob:**/*Dao.xml");
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (mapperMatcher.matches(file) || daoMatcher.matches(file)) {
                        spots.add(new BlindSpot(
                            "mybatis-xml",
                            file.toString(),
                            file.getFileName().toString()
                        ));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // Skip directories that can't be walked
        }
    }
}
