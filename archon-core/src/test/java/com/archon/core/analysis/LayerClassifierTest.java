package com.archon.core.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

/**
 * Tests for LayerClassifier.
 */
class LayerClassifierTest {

    private LayerClassifier defaultClassifier;
    private LayerClassifier customClassifier;

    @BeforeEach
    void setUp() {
        defaultClassifier = new LayerClassifier();
        // Custom pattern: "Handler" → CONTROLLER (overrides default behavior)
        customClassifier = new LayerClassifier(Map.of("Handler", ArchLayer.CONTROLLER));
    }

    @Test
    void testControllerClassification() {
        assertEquals(ArchLayer.CONTROLLER, defaultClassifier.classify("com.example.web.UserController"));
        assertEquals(ArchLayer.CONTROLLER, defaultClassifier.classify("com.example.api.UserResource"));
        assertEquals(ArchLayer.CONTROLLER, defaultClassifier.classify("com.example.rest.UserEndpoint"));
    }

    @Test
    void testServiceClassification() {
        assertEquals(ArchLayer.SERVICE, defaultClassifier.classify("com.example.service.UserService"));
        assertEquals(ArchLayer.SERVICE, defaultClassifier.classify("com.example.service.UserServiceImpl"));
        assertEquals(ArchLayer.SERVICE, defaultClassifier.classify("com.example.manager.DataManager"));
    }

    @Test
    void testRepositoryClassification() {
        assertEquals(ArchLayer.REPOSITORY, defaultClassifier.classify("com.example.dao.UserMapper"));
        assertEquals(ArchLayer.REPOSITORY, defaultClassifier.classify("com.example.repository.UserRepository"));
        assertEquals(ArchLayer.REPOSITORY, defaultClassifier.classify("com.example.dao.UserDao"));
    }

    @Test
    void testDomainClassification() {
        assertEquals(ArchLayer.DOMAIN, defaultClassifier.classify("com.example.entity.UserEntity"));
        assertEquals(ArchLayer.DOMAIN, defaultClassifier.classify("com.example.domain.UserDomain"));
        assertEquals(ArchLayer.DOMAIN, defaultClassifier.classify("com.example.model.UserModel"));
        assertEquals(ArchLayer.DOMAIN, defaultClassifier.classify("com.example.vo.UserVO"));
        assertEquals(ArchLayer.DOMAIN, defaultClassifier.classify("com.example.dto.UserDTO"));
    }

    @Test
    void testConfigClassification() {
        assertEquals(ArchLayer.CONFIG, defaultClassifier.classify("com.example.config.AppConfig"));
        assertEquals(ArchLayer.CONFIG, defaultClassifier.classify("com.example.config.DatabaseConfiguration"));
        assertEquals(ArchLayer.CONFIG, defaultClassifier.classify("com.example.props.AppProperties"));
    }

    @Test
    void testUtilClassification() {
        assertEquals(ArchLayer.UTIL, defaultClassifier.classify("com.example.util.StringUtils"));
        assertEquals(ArchLayer.UTIL, defaultClassifier.classify("com.example.utils.CollectionUtils"));
        assertEquals(ArchLayer.UTIL, defaultClassifier.classify("com.example.helper.JsonHelper"));
        assertEquals(ArchLayer.UTIL, defaultClassifier.classify("com.example.constants.AppConstants"));
    }

    @Test
    void testUnknownClassification() {
        assertEquals(ArchLayer.UNKNOWN, defaultClassifier.classify("com.example.App"));
        assertEquals(ArchLayer.UNKNOWN, defaultClassifier.classify("com.example.Main"));
        assertEquals(ArchLayer.UNKNOWN, defaultClassifier.classify("com.example.handler.RequestHandler")); // "Handler" not in default patterns
    }

    @Test
    void testNullAndEmptyInput() {
        assertEquals(ArchLayer.UNKNOWN, defaultClassifier.classify(null));
        assertEquals(ArchLayer.UNKNOWN, defaultClassifier.classify(""));
    }

    @Test
    void testInnerClassClassification() {
        assertEquals(ArchLayer.CONTROLLER, defaultClassifier.classify("com.example.web.UserController$Builder"));
        assertEquals(ArchLayer.SERVICE, defaultClassifier.classify("com.example.service.UserService$Inner"));
        assertEquals(ArchLayer.REPOSITORY, defaultClassifier.classify("com.example.dao.UserMapper$1")); // Anonymous inner class
    }

    @Test
    void testCustomPatternsOverrideDefaults() {
        // Custom pattern: "Handler" → CONTROLLER
        assertEquals(ArchLayer.CONTROLLER, customClassifier.classify("com.example.handler.RequestHandler"));

        // Default patterns should still work for non-custom cases
        assertEquals(ArchLayer.CONTROLLER, customClassifier.classify("com.example.web.UserController"));
        assertEquals(ArchLayer.SERVICE, customClassifier.classify("com.example.service.UserService"));
    }

    @Test
    void testSimpleClassNameWithoutPackage() {
        assertEquals(ArchLayer.CONTROLLER, defaultClassifier.classify("UserController"));
        assertEquals(ArchLayer.SERVICE, defaultClassifier.classify("UserService"));
        assertEquals(ArchLayer.UNKNOWN, defaultClassifier.classify("App"));
    }

    @Test
    void testFqcnWithMultipleDots() {
        assertEquals(ArchLayer.CONTROLLER, defaultClassifier.classify("com.example.module.web.api.UserController"));
        assertEquals(ArchLayer.SERVICE, defaultClassifier.classify("org.company.project.service.internal.UserService"));
    }

    @Test
    void testCustomPatternsNullMap() {
        LayerClassifier classifierWithNull = new LayerClassifier(null);
        assertEquals(ArchLayer.CONTROLLER, classifierWithNull.classify("com.example.web.UserController"));
        assertEquals(ArchLayer.UNKNOWN, classifierWithNull.classify("com.example.handler.RequestHandler"));
    }
}
