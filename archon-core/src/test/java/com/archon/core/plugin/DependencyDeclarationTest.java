package com.archon.core.plugin;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DependencyDeclarationTest {

    @Test
    void testValidConstruction() {
        DependencyDeclaration dd = new DependencyDeclaration(
            "java:com.example.Foo",
            "java:com.example.Bar",
            EdgeType.IMPORTS,
            Confidence.HIGH,
            "import com.example.Foo",
            false
        );

        assertEquals("java:com.example.Foo", dd.sourceId());
        assertEquals("java:com.example.Bar", dd.targetId());
        assertEquals(EdgeType.IMPORTS, dd.edgeType());
        assertEquals(Confidence.HIGH, dd.confidence());
        assertEquals("import com.example.Foo", dd.evidence());
        assertFalse(dd.dynamic());
    }

    @Test
    void testNullSourceIdThrowsNPE() {
        assertThrows(NullPointerException.class, () -> new DependencyDeclaration(
            null,
            "java:com.example.Bar",
            EdgeType.IMPORTS,
            Confidence.HIGH,
            null,
            false
        ));
    }

    @Test
    void testNullTargetIdThrowsNPE() {
        assertThrows(NullPointerException.class, () -> new DependencyDeclaration(
            "java:com.example.Foo",
            null,
            EdgeType.IMPORTS,
            Confidence.HIGH,
            null,
            false
        ));
    }

    @Test
    void testNullEdgeTypeThrowsNPE() {
        assertThrows(NullPointerException.class, () -> new DependencyDeclaration(
            "java:com.example.Foo",
            "java:com.example.Bar",
            null,
            Confidence.HIGH,
            null,
            false
        ));
    }

    @Test
    void testBlankSourceIdThrowsIAE() {
        assertThrows(IllegalArgumentException.class, () -> new DependencyDeclaration(
            "   ",
            "java:com.example.Bar",
            EdgeType.IMPORTS,
            Confidence.HIGH,
            null,
            false
        ));
    }

    @Test
    void testBlankTargetIdThrowsIAE() {
        assertThrows(IllegalArgumentException.class, () -> new DependencyDeclaration(
            "java:com.example.Foo",
            "   ",
            EdgeType.IMPORTS,
            Confidence.HIGH,
            null,
            false
        ));
    }

    @Test
    void testEmptySourceIdThrowsIAE() {
        assertThrows(IllegalArgumentException.class, () -> new DependencyDeclaration(
            "",
            "java:com.example.Bar",
            EdgeType.IMPORTS,
            Confidence.HIGH,
            null,
            false
        ));
    }

    @Test
    void testEmptyTargetIdThrowsIAE() {
        assertThrows(IllegalArgumentException.class, () -> new DependencyDeclaration(
            "java:com.example.Foo",
            "",
            EdgeType.IMPORTS,
            Confidence.HIGH,
            null,
            false
        ));
    }

    @Test
    void testNullableEvidence() {
        DependencyDeclaration dd = new DependencyDeclaration(
            "java:com.example.Foo",
            "java:com.example.Bar",
            EdgeType.EXTENDS,
            Confidence.MEDIUM,
            null,
            false
        );
        assertNull(dd.evidence());
    }

    @Test
    void testDynamicFlagTrue() {
        DependencyDeclaration dd = new DependencyDeclaration(
            "java:com.example.Foo",
            "java:com.example.Bar",
            EdgeType.USES,
            Confidence.LOW,
            "Class.forName()",
            true
        );
        assertTrue(dd.dynamic());
    }

    @Test
    void testDynamicFlagFalseByDefault() {
        DependencyDeclaration dd = new DependencyDeclaration(
            "java:com.example.Foo",
            "java:com.example.Bar",
            EdgeType.IMPORTS,
            Confidence.HIGH,
            "import com.example.Bar",
            false
        );
        assertFalse(dd.dynamic());
    }

    @Test
    void testRecordAccessors() {
        DependencyDeclaration dd = new DependencyDeclaration(
            "js:src/components/Header",
            "js:src/utils/api",
            EdgeType.CALLS,
            Confidence.HIGH,
            "api.fetch()",
            false
        );

        assertEquals("js:src/components/Header", dd.sourceId());
        assertEquals("js:src/utils/api", dd.targetId());
        assertEquals(EdgeType.CALLS, dd.edgeType());
        assertEquals(Confidence.HIGH, dd.confidence());
        assertEquals("api.fetch()", dd.evidence());
    }

    @Test
    void testAllEdgeTypes() {
        for (EdgeType type : EdgeType.values()) {
            DependencyDeclaration dd = new DependencyDeclaration(
                "java:Foo",
                "java:Bar",
                type,
                Confidence.HIGH,
                null,
                false
            );
            assertEquals(type, dd.edgeType());
        }
    }

    @Test
    void testAllConfidenceLevels() {
        for (Confidence c : Confidence.values()) {
            DependencyDeclaration dd = new DependencyDeclaration(
                "java:Foo",
                "java:Bar",
                EdgeType.IMPORTS,
                c,
                null,
                false
            );
            assertEquals(c, dd.confidence());
        }
    }

    @Test
    void testRecordEquality() {
        DependencyDeclaration dd1 = new DependencyDeclaration(
            "java:Foo", "java:Bar", EdgeType.IMPORTS, Confidence.HIGH, null, false
        );
        DependencyDeclaration dd2 = new DependencyDeclaration(
            "java:Foo", "java:Bar", EdgeType.IMPORTS, Confidence.HIGH, null, false
        );
        assertEquals(dd1, dd2);
        assertEquals(dd1.hashCode(), dd2.hashCode());
    }

    @Test
    void testRecordInequality() {
        DependencyDeclaration dd1 = new DependencyDeclaration(
            "java:Foo", "java:Bar", EdgeType.IMPORTS, Confidence.HIGH, null, false
        );
        DependencyDeclaration dd2 = new DependencyDeclaration(
            "java:Foo", "java:Baz", EdgeType.IMPORTS, Confidence.HIGH, null, false
        );
        assertNotEquals(dd1, dd2);
    }
}
