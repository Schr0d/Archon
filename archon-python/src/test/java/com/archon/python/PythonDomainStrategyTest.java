package com.archon.python;

import com.archon.core.analysis.DomainStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@DisplayName("PythonDomainStrategy Tests")
class PythonDomainStrategyTest {

    @Test
    @DisplayName("assignDomains detects __init__.py package domains")
    void testInitPyPackageDomains() {
        PythonDomainStrategy strategy = new PythonDomainStrategy();

        Set<String> sourceModules = Set.of(
            "py:src.mypkg.__init__",
            "py:src.mypkg.utils",
            "py:src.mypkg.services.api"
        );

        Optional<Map<String, String>> domains = strategy.assignDomains(null, sourceModules);

        assertTrue(domains.isPresent(), "Should return domains");
        Map<String, String> domainMap = domains.get();

        // All should be in "mypkg" domain
        assertEquals("mypkg", domainMap.get("py:src.mypkg.__init__"));
        assertEquals("mypkg", domainMap.get("py:src.mypkg.utils"));
        assertEquals("mypkg", domainMap.get("py:src.mypkg.services.api"));
    }

    @Test
    @DisplayName("assignDomains detects src/ layout domains")
    void testSrcLayoutDomains() {
        PythonDomainStrategy strategy = new PythonDomainStrategy();

        Set<String> sourceModules = Set.of(
            "py:src.myapp.service",
            "py:src.myapp.models",
            "py:lib.utils"
        );

        Optional<Map<String, String>> domains = strategy.assignDomains(null, sourceModules);

        assertTrue(domains.isPresent(), "Should return domains");
        Map<String, String> domainMap = domains.get();

        assertEquals("myapp", domainMap.get("py:src.myapp.service"));
        assertEquals("myapp", domainMap.get("py:src.myapp.models"));
        assertEquals("lib", domainMap.get("py:lib.utils"));
    }

    @Test
    @DisplayName("assignDomains detects tests/ directory domains")
    void testTestsDirectoryDomains() {
        PythonDomainStrategy strategy = new PythonDomainStrategy();

        Set<String> sourceModules = Set.of(
            "py:tests.test_foo",
            "py:tests.integration.test_api",
            "py:src.tests.test_bar"
        );

        Optional<Map<String, String>> domains = strategy.assignDomains(null, sourceModules);

        assertTrue(domains.isPresent(), "Should return domains");
        Map<String, String> domainMap = domains.get();

        assertEquals("tests", domainMap.get("py:tests.test_foo"));
        assertEquals("tests", domainMap.get("py:tests.integration.test_api"));
        assertEquals("tests", domainMap.get("py:src.tests.test_bar"));
    }

    @Test
    @DisplayName("assignDomains uses fallback for flat layout")
    void testFlatLayoutFallback() {
        PythonDomainStrategy strategy = new PythonDomainStrategy();

        Set<String> sourceModules = Set.of(
            "py:handler",
            "py:models.user"
        );

        Optional<Map<String, String>> domains = strategy.assignDomains(null, sourceModules);

        assertTrue(domains.isPresent(), "Should return domains");
        Map<String, String> domainMap = domains.get();

        // Flat files use their directory as domain (empty = "default")
        assertEquals("default", domainMap.get("py:handler"));
        assertEquals("models", domainMap.get("py:models.user"));
    }

    @Test
    @DisplayName("assignDomains handles namespace prefixes correctly")
    void testNamespacePrefixStripping() {
        PythonDomainStrategy strategy = new PythonDomainStrategy();

        // Mix of prefixed and non-prefixed (though SPI should always prefix)
        Set<String> sourceModules = Set.of(
            "py:src.myapp.service",
            "py:tests.test_foo"
        );

        Optional<Map<String, String>> domains = strategy.assignDomains(null, sourceModules);

        assertTrue(domains.isPresent(), "Should return domains");
        Map<String, String> domainMap = domains.get();

        // Should strip "py:" prefix before processing
        assertEquals("myapp", domainMap.get("py:src.myapp.service"));
        assertEquals("tests", domainMap.get("py:tests.test_foo"));
    }
}
