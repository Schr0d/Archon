package com.archon.java;

import com.archon.core.graph.BlindSpot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlindSpotDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detect_reflectionClassForName_flagsBlindSpot() throws IOException {
        Path javaFile = createJavaFile("ReflectionExample.java",
            "package com.example;\n"
            + "public class ReflectionExample {\n"
            + "    void foo() {\n"
            + "        Class.forName(\"com.example.Target\");\n"
            + "    }\n"
            + "}");

        BlindSpotDetector detector = new BlindSpotDetector();
        List<BlindSpot> spots = detector.detect(javaFile);

        assertFalse(spots.isEmpty());
        assertTrue(spots.stream().anyMatch(s -> s.getType().equals("reflection")));
        assertTrue(spots.stream().anyMatch(s -> s.getPattern().contains("Class.forName")));
    }

    @Test
    void detect_methodInvoke_flagsBlindSpot() throws IOException {
        Path javaFile = createJavaFile("InvokeExample.java",
            "package com.example;\n"
            + "public class InvokeExample {\n"
            + "    void foo() throws Exception {\n"
            + "        Method m = obj.getClass().getDeclaredMethod(\"bar\");\n"
            + "        m.invoke(obj);\n"
            + "    }\n"
            + "}");

        BlindSpotDetector detector = new BlindSpotDetector();
        List<BlindSpot> spots = detector.detect(javaFile);

        assertTrue(spots.stream().anyMatch(s -> s.getType().equals("reflection")
            && s.getPattern().contains("Method.invoke")));
    }

    @Test
    void detect_eventListener_flagsBlindSpot() throws IOException {
        Path javaFile = createJavaFile("EventExample.java",
            "package com.example;\n"
            + "import org.springframework.context.event.EventListener;\n"
            + "public class EventExample {\n"
            + "    @EventListener\n"
            + "    public void handleEvent(Object event) {}\n"
            + "}");

        BlindSpotDetector detector = new BlindSpotDetector();
        List<BlindSpot> spots = detector.detect(javaFile);

        assertTrue(spots.stream().anyMatch(s -> s.getType().equals("event-driven")
            && s.getPattern().contains("@EventListener")));
    }

    @Test
    void detect_myBatisXml_flagsBlindSpot() throws IOException {
        Path resourcesDir = tempDir.resolve("src/main/resources/mapper");
        Files.createDirectories(resourcesDir);
        Files.writeString(resourcesDir.resolve("SysUserMapper.xml"),
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n"
            + "<mapper namespace=\"com.fuwa.system.mapper.SysUserMapper\">\n"
            + "  <select id=\"selectUserList\" resultType=\"SysUser\">\n"
            + "    SELECT * FROM sys_user\n"
            + "  </select>\n"
            + "</mapper>");

        BlindSpotDetector detector = new BlindSpotDetector();
        List<BlindSpot> spots = detector.detect(tempDir);

        assertTrue(spots.stream().anyMatch(s -> s.getType().equals("mybatis-xml")
            && s.getPattern().contains("SysUserMapper.xml")));
    }

    @Test
    void detect_cleanFile_noBlindSpots() throws IOException {
        Path javaFile = createJavaFile("CleanExample.java",
            "package com.example;\n"
            + "public class CleanExample {\n"
            + "    public String getName() { return \"clean\"; }\n"
            + "}");

        BlindSpotDetector detector = new BlindSpotDetector();
        List<BlindSpot> spots = detector.detect(javaFile);

        assertTrue(spots.isEmpty());
    }

    private Path createJavaFile(String name, String content) throws IOException {
        Path dir = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve(name), content);
    }
}
