package com.archon.java;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ImportResolverTest {

    @Test
    void resolve_regularImport_returnsFqcn() {
        ImportResolver resolver = new ImportResolver(Set.of());
        Optional<String> result = resolver.resolve("com.fuwa.system.domain.SysUser");
        assertTrue(result.isPresent());
        assertEquals("com.fuwa.system.domain.SysUser", result.get());
    }

    @Test
    void resolve_staticImport_returnsDeclaringClass() {
        ImportResolver resolver = new ImportResolver(Set.of());
        Optional<String> result = resolver.resolve("com.fuwa.framework.util.ShiroUtils.getSysUser");
        assertTrue(result.isPresent());
        assertEquals("com.fuwa.framework.util.ShiroUtils", result.get());
    }

    @Test
    void resolve_wildcardImport_returnsEmpty() {
        Set<String> knownClasses = Set.of(
            "com.fuwa.system.domain.SysUser",
            "com.fuwa.system.domain.SysRole"
        );
        ImportResolver resolver = new ImportResolver(knownClasses);
        Optional<String> result = resolver.resolve("com.fuwa.system.domain.*");
        assertTrue(result.isEmpty());
    }

    @Test
    void resolve_wildcardMatch_resolvesToKnownClasses() {
        Set<String> knownClasses = Set.of(
            "com.fuwa.system.domain.SysUser",
            "com.fuwa.system.domain.SysRole"
        );
        ImportResolver resolver = new ImportResolver(knownClasses);
        Set<String> matches = resolver.resolveWildcard("com.fuwa.system.domain.*");
        assertEquals(2, matches.size());
        assertTrue(matches.contains("com.fuwa.system.domain.SysUser"));
        assertTrue(matches.contains("com.fuwa.system.domain.SysRole"));
    }

    @Test
    void resolve_nullImport_returnsEmpty() {
        ImportResolver resolver = new ImportResolver(Set.of());
        assertFalse(resolver.resolve(null).isPresent());
    }

    @Test
    void resolve_emptyImport_returnsEmpty() {
        ImportResolver resolver = new ImportResolver(Set.of());
        assertFalse(resolver.resolve("").isPresent());
    }
}
