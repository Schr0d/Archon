package com.archon.java;

import com.archon.core.coordination.PostProcessResult;
import com.archon.core.plugin.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SpringDIPostProcessorTest {

    private static final Path TEST_CLASSES = Path.of("build/classes/java/test").toAbsolutePath();

    private static ModuleDeclaration mod(String id) {
        return new ModuleDeclaration(id, NodeType.CLASS, null, Confidence.HIGH);
    }

    @Test
    void detectsAutowiredFieldInjection() {
        List<ModuleDeclaration> modules = List.of(
            mod("java:com.archon.java.fixtures.OrderController"),
            mod("java:com.archon.java.fixtures.OrderService"),
            mod("java:com.archon.java.fixtures.OrderServiceImpl")
        );

        SpringDIPostProcessor processor = new SpringDIPostProcessor();
        PostProcessResult result = processor.process(modules, TEST_CLASSES);

        var autowiredEdges = result.declarations().stream()
            .filter(d -> d.edgeType() == EdgeType.SPRING_DI)
            .collect(Collectors.toList());

        assertTrue(autowiredEdges.stream().anyMatch(d ->
            d.sourceId().contains("OrderController") && d.targetId().contains("OrderServiceImpl")
        ), "Should detect @Autowired OrderService -> OrderServiceImpl. Got: " + autowiredEdges);
    }

    @Test
    void detectsResourceFieldInjection() {
        List<ModuleDeclaration> modules = List.of(
            mod("java:com.archon.java.fixtures.NotificationService"),
            mod("java:com.archon.java.fixtures.OrderService"),
            mod("java:com.archon.java.fixtures.OrderServiceImpl")
        );

        SpringDIPostProcessor processor = new SpringDIPostProcessor();
        PostProcessResult result = processor.process(modules, TEST_CLASSES);

        var resourceEdges = result.declarations().stream()
            .filter(d -> d.edgeType() == EdgeType.SPRING_DI)
            .filter(d -> d.sourceId().contains("NotificationService"))
            .collect(Collectors.toList());

        assertFalse(resourceEdges.isEmpty(),
            "Should detect @Resource injection in NotificationService");
    }

    @Test
    void detectsConstructorInjection() {
        List<ModuleDeclaration> modules = List.of(
            mod("java:com.archon.java.fixtures.PaymentService"),
            mod("java:com.archon.java.fixtures.OrderService"),
            mod("java:com.archon.java.fixtures.OrderServiceImpl")
        );

        SpringDIPostProcessor processor = new SpringDIPostProcessor();
        PostProcessResult result = processor.process(modules, TEST_CLASSES);

        var constructorEdges = result.declarations().stream()
            .filter(d -> d.edgeType() == EdgeType.SPRING_DI)
            .filter(d -> d.sourceId().contains("PaymentService"))
            .collect(Collectors.toList());

        assertFalse(constructorEdges.isEmpty(),
            "Should detect constructor injection in PaymentService");
    }

    @Test
    void ambiguousImplementationProducesBlindSpot() {
        List<ModuleDeclaration> modules = List.of(
            mod("java:com.archon.java.fixtures.AmbiguousService"),
            mod("java:com.archon.java.fixtures.UserRepository"),
            mod("java:com.archon.java.fixtures.UserRepositoryImpl")
        );

        SpringDIPostProcessor processor = new SpringDIPostProcessor();
        PostProcessResult result = processor.process(modules, TEST_CLASSES);

        boolean hasAmbiguousBlindSpot = result.blindSpots().stream()
            .anyMatch(bs -> bs.getDescription().toLowerCase().contains("ambiguous"));
        assertTrue(hasAmbiguousBlindSpot,
            "Should report blind spot for ambiguous interface resolution. Blind spots: " + result.blindSpots());
    }

    @Test
    void returnsEmptyWhenNoClassDirectory() {
        List<ModuleDeclaration> modules = List.of(mod("java:com.example.Foo"));

        SpringDIPostProcessor processor = new SpringDIPostProcessor();
        PostProcessResult result = processor.process(modules, Path.of("/nonexistent/path"));

        assertTrue(result.declarations().isEmpty());
        assertTrue(result.blindSpots().isEmpty());
    }

    @Test
    void returnsEmptyWhenNoSpringBeans() {
        List<ModuleDeclaration> modules = List.of(
            mod("java:com.archon.core.plugin.LanguagePlugin")
        );

        SpringDIPostProcessor processor = new SpringDIPostProcessor();
        PostProcessResult result = processor.process(modules, TEST_CLASSES);

        var springEdges = result.declarations().stream()
            .filter(d -> d.edgeType() == EdgeType.SPRING_DI)
            .collect(Collectors.toList());
        assertTrue(springEdges.stream().noneMatch(d -> d.sourceId().contains("LanguagePlugin")));
    }
}
