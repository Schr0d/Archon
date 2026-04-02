package com.archon.java;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.EdgeType;
import com.archon.core.graph.Node;
import com.archon.core.config.ArchonConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JavaParserPluginTest {

    @TempDir
    Path tempDir;

    private ArchonConfig defaultConfig = ArchonConfig.defaults();

    @Test
    void parse_singleJavaFile_buildsGraphWithNode() throws IOException {
        createProject("src/main/java/com/example/Simple.java",
            "package com.example;\npublic class Simple {}");

        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult result = plugin.parse(tempDir, defaultConfig);

        assertEquals(1, result.getGraph().nodeCount());
        assertTrue(result.getGraph().getNode("com.example.Simple").isPresent());
    }

    @Test
    void parse_twoFilesWithImport_buildsEdge() throws IOException {
        createProject("src/main/java/com/example/Service.java",
            "package com.example;\npublic class Service {}");
        createProject("src/main/java/com/example/Controller.java",
            "package com.example;\n"
            + "import com.example.Service;\n"
            + "public class Controller {\n"
            + "    Service service;\n"
            + "}");

        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult result = plugin.parse(tempDir, defaultConfig);

        DependencyGraph graph = result.getGraph();
        assertEquals(2, graph.nodeCount());
        assertTrue(graph.getEdge("com.example.Controller", "com.example.Service").isPresent());
    }

    @Test
    void parse_invalidJavaFile_skipsAndRecordsError() throws IOException {
        createProject("src/main/java/com/example/Broken.java",
            "this is not valid java {{}}");
        createProject("src/main/java/com/example/Valid.java",
            "package com.example;\npublic class Valid {}");

        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult result = plugin.parse(tempDir, defaultConfig);

        assertEquals(1, result.getGraph().nodeCount());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).getFile().contains("Broken.java"));
    }

    @Test
    void parse_emptyProject_returnsEmptyGraph() throws IOException {
        Files.createDirectories(tempDir.resolve("src/main/java"));

        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult result = plugin.parse(tempDir, defaultConfig);

        assertEquals(0, result.getGraph().nodeCount());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void parse_withBlindSpot_flagsReflection() throws IOException {
        createProject("src/main/java/com/example/Reflect.java",
            "package com.example;\n"
            + "public class Reflect {\n"
            + "    void foo() throws Exception {\n"
            + "        Class.forName(\"com.example.Target\");\n"
            + "    }\n"
            + "}");

        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult result = plugin.parse(tempDir, defaultConfig);

        assertFalse(result.getBlindSpots().isEmpty());
        assertTrue(result.getBlindSpots().stream()
            .anyMatch(bs -> bs.getType().equals("reflection")));
    }

    private void createProject(String relativePath, String content) throws IOException {
        Path file = tempDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
