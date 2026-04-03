package com.archon.java;

import com.archon.core.graph.DependencyGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for JavaDomainStrategy.
 */
class JavaDomainStrategyTest {

    @Test
    void assignDomains_thirdSegmentExtraction_returnsCorrectDomains() {
        JavaDomainStrategy strategy = new JavaDomainStrategy();
        // Note: JavaDomainStrategy doesn't use the graph parameter, so we pass null
        DependencyGraph graph = null;

        Set<String> sourceModules = Set.of(
            "com.example.service.FooService",
            "com.example.repository.UserRepository",
            "com.example.controller.UserController",
            "org.myapp.config.AppConfig"
        );

        var result = strategy.assignDomains(graph, sourceModules);

        assertEquals(Optional.of(
            Map.of(
                "com.example.service.FooService", "service",
                "com.example.repository.UserRepository", "repository",
                "com.example.controller.UserController", "controller",
                "org.myapp.config.AppConfig", "config"
            )
        ), result);
    }

    @Test
    void assignDomains_singleSegment_returnsDefaultDomain() {
        JavaDomainStrategy strategy = new JavaDomainStrategy();
        DependencyGraph graph = null;

        Set<String> sourceModules = Set.of(
            "Foo",
            "Bar"
        );

        var result = strategy.assignDomains(graph, sourceModules);

        assertEquals(Optional.of(
            Map.of(
                "Foo", "default",
                "Bar", "default"
            )
        ), result);
    }

    @Test
    void assignDomains_twoSegments_returnsDefaultDomain() {
        JavaDomainStrategy strategy = new JavaDomainStrategy();
        DependencyGraph graph = null;

        Set<String> sourceModules = Set.of(
            "com.FooService",
            "example.Bar"
        );

        var result = strategy.assignDomains(graph, sourceModules);

        assertEquals(Optional.of(
            Map.of(
                "com.FooService", "default",
                "example.Bar", "default"
            )
        ), result);
    }

    @Test
    void assignDomains_withJavaNamespacePrefix_stripsPrefixAndExtractsDomain() {
        JavaDomainStrategy strategy = new JavaDomainStrategy();
        DependencyGraph graph = null;

        Set<String> sourceModules = Set.of(
            "java:com.example.service.FooService",
            "java:com.example.repository.UserRepository"
        );

        var result = strategy.assignDomains(graph, sourceModules);

        assertEquals(Optional.of(
            Map.of(
                "java:com.example.service.FooService", "service",
                "java:com.example.repository.UserRepository", "repository"
            )
        ), result);
    }

    @Test
    void assignDomains_mixedNamingConventions_handlesCorrectly() {
        JavaDomainStrategy strategy = new JavaDomainStrategy();
        DependencyGraph graph = null;

        Set<String> sourceModules = Set.of(
            "com.example.service.impl.FooServiceImpl",  // third segment → "service"
            "com.example.domain.model.User",              // third segment → "domain"
            "SingleClass",                                 // single segment → "default"
            "java:com.example.api.ApiClient"              // with prefix → "api"
        );

        var result = strategy.assignDomains(graph, sourceModules);

        assertEquals(Optional.of(
            Map.of(
                "com.example.service.impl.FooServiceImpl", "service",
                "com.example.domain.model.User", "domain",
                "SingleClass", "default",
                "java:com.example.api.ApiClient", "api"
            )
        ), result);
    }

    @Test
    void assignDomains_emptySourceModules_returnsEmptyMap() {
        JavaDomainStrategy strategy = new JavaDomainStrategy();
        DependencyGraph graph = null;

        Set<String> sourceModules = Set.of();

        var result = strategy.assignDomains(graph, sourceModules);

        assertEquals(Optional.of(Map.of()), result);
    }
}
