package com.archon.core.plugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModuleDeclarationTest {

    @Test
    void testValidConstruction() {
        ModuleDeclaration md = new ModuleDeclaration(
            "java:com.example.Foo",
            NodeType.CLASS,
            "src/main/java/com/example/Foo.java",
            Confidence.HIGH
        );

        assertEquals("java:com.example.Foo", md.id());
        assertEquals(NodeType.CLASS, md.type());
        assertEquals("src/main/java/com/example/Foo.java", md.sourcePath());
        assertEquals(Confidence.HIGH, md.confidence());
    }

    @Test
    void testNullIdThrowsNPE() {
        assertThrows(NullPointerException.class, () -> new ModuleDeclaration(
            null,
            NodeType.CLASS,
            "path",
            Confidence.HIGH
        ));
    }

    @Test
    void testNullTypeThrowsNPE() {
        assertThrows(NullPointerException.class, () -> new ModuleDeclaration(
            "java:com.example.Foo",
            null,
            "path",
            Confidence.HIGH
        ));
    }

    @Test
    void testBlankIdThrowsIAE() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleDeclaration(
            "   ",
            NodeType.CLASS,
            "path",
            Confidence.HIGH
        ));
    }

    @Test
    void testEmptyIdThrowsIAE() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleDeclaration(
            "",
            NodeType.CLASS,
            "path",
            Confidence.HIGH
        ));
    }

    @Test
    void testNullableSourcePath() {
        ModuleDeclaration md = new ModuleDeclaration(
            "java:com.example.Foo",
            NodeType.CLASS,
            null,
            Confidence.HIGH
        );
        assertNull(md.sourcePath());
    }

    @Test
    void testRecordAccessors() {
        ModuleDeclaration md = new ModuleDeclaration(
            "js:src/components/Header",
            NodeType.MODULE,
            "src/components/Header.tsx",
            Confidence.HIGH
        );

        assertEquals("js:src/components/Header", md.id());
        assertEquals(NodeType.MODULE, md.type());
        assertEquals("src/components/Header.tsx", md.sourcePath());
        assertEquals(Confidence.HIGH, md.confidence());
    }

    @Test
    void testAllNodeTypes() {
        for (NodeType type : NodeType.values()) {
            ModuleDeclaration md = new ModuleDeclaration(
                "java:Foo",
                type,
                null,
                Confidence.HIGH
            );
            assertEquals(type, md.type());
        }
    }

    @Test
    void testAllConfidenceLevels() {
        for (Confidence c : Confidence.values()) {
            ModuleDeclaration md = new ModuleDeclaration(
                "py:myapp.utils",
                NodeType.MODULE,
                null,
                c
            );
            assertEquals(c, md.confidence());
        }
    }

    @Test
    void testRecordEquality() {
        ModuleDeclaration md1 = new ModuleDeclaration(
            "java:Foo", NodeType.CLASS, "Foo.java", Confidence.HIGH
        );
        ModuleDeclaration md2 = new ModuleDeclaration(
            "java:Foo", NodeType.CLASS, "Foo.java", Confidence.HIGH
        );
        assertEquals(md1, md2);
        assertEquals(md1.hashCode(), md2.hashCode());
    }

    @Test
    void testRecordInequality() {
        ModuleDeclaration md1 = new ModuleDeclaration(
            "java:Foo", NodeType.CLASS, "Foo.java", Confidence.HIGH
        );
        ModuleDeclaration md2 = new ModuleDeclaration(
            "java:Bar", NodeType.CLASS, "Bar.java", Confidence.HIGH
        );
        assertNotEquals(md1, md2);
    }

    @Test
    void testPythonModule() {
        ModuleDeclaration md = new ModuleDeclaration(
            "py:myapp.models.user",
            NodeType.MODULE,
            "myapp/models/user.py",
            Confidence.HIGH
        );
        assertEquals("py:myapp.models.user", md.id());
        assertEquals(NodeType.MODULE, md.type());
    }
}
