package com.archon.python;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PythonStdlib Tests")
class PythonStdlibTest {

    @Test
    @DisplayName("isStdlib returns true for known stdlib modules")
    void testIsStdlibReturnsTrueForKnownModules() {
        assertTrue(PythonStdlib.isStdlib("os"), "os should be stdlib");
        assertTrue(PythonStdlib.isStdlib("sys"), "sys should be stdlib");
        assertTrue(PythonStdlib.isStdlib("json"), "json should be stdlib");
        assertTrue(PythonStdlib.isStdlib("pathlib"), "pathlib should be stdlib");
    }

    @Test
    @DisplayName("isStdlib returns false for third-party modules")
    void testIsStdlibReturnsFalseForThirdParty() {
        assertFalse(PythonStdlib.isStdlib("numpy"), "numpy should not be stdlib");
        assertFalse(PythonStdlib.isStdlib("pandas"), "pandas should not be stdlib");
        assertFalse(PythonStdlib.isStdlib("requests"), "requests should not be stdlib");
    }

    @Test
    @DisplayName("isStdlib returns false for local modules")
    void testIsStdlibReturnsFalseForLocalModules() {
        assertFalse(PythonStdlib.isStdlib("src.utils.helpers"), "local module should not be stdlib");
        assertFalse(PythonStdlib.isStdlib("myapp.service"), "local module should not be stdlib");
    }

    @Test
    @DisplayName("isStdlib is case-insensitive")
    void testIsStdlibIsCaseInsensitive() {
        assertTrue(PythonStdlib.isStdlib("OS"), "OS should match os (case-insensitive)");
        assertTrue(PythonStdlib.isStdlib("SYS"), "SYS should match sys (case-insensitive)");
        assertTrue(PythonStdlib.isStdlib("Json"), "Json should match json (case-insensitive)");
    }

    @Test
    @DisplayName("isStdlib extracts base module from dotted names")
    void testIsStdlibExtractsBaseModuleFromDottedNames() {
        // Dotted module names should still match their base module
        assertTrue(PythonStdlib.isStdlib("os.path"), "os.path should match os");
        assertTrue(PythonStdlib.isStdlib("os.path.sys"), "os.path.sys should match os");
        assertTrue(PythonStdlib.isStdlib("sys.path"), "sys.path should match sys");
        assertTrue(PythonStdlib.isStdlib("json.decoder"), "json.decoder should match json");
        assertTrue(PythonStdlib.isStdlib("pathlib.Path"), "pathlib.Path should match pathlib");

        // Third-party dotted modules should not match
        assertFalse(PythonStdlib.isStdlib("numpy.core"), "numpy.core should not match");
        assertFalse(PythonStdlib.isStdlib("pandas.core.frame"), "pandas.core.frame should not match");
    }

    @Test
    @DisplayName("isStdlib handles whitespace and edge cases")
    void testIsStdlibHandlesWhitespaceAndEdgeCases() {
        assertFalse(PythonStdlib.isStdlib("   "), "whitespace-only should not be stdlib");
        assertFalse(PythonStdlib.isStdlib(""), "empty string should not be stdlib");
        assertFalse(PythonStdlib.isStdlib(null), "null should not be stdlib");
        assertFalse(PythonStdlib.isStdlib("."), "single dot should not be stdlib");
        assertFalse(PythonStdlib.isStdlib(".."), "double dot should not be stdlib");
    }
}
