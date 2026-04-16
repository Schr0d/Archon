package com.archon.java;

import com.archon.core.coordination.PostProcessResult;
import com.archon.core.plugin.*;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Scans compiled .class files for Spring DI patterns using ArchUnit.
 * Detects @Autowired fields, @Resource fields, and constructor injection.
 * Resolves interface types to their @Component/@Service implementations.
 */
public class SpringDIPostProcessor {

    private static final Set<String> BEAN_ANNOTATIONS = Set.of(
        "org.springframework.stereotype.Component",
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Repository",
        "org.springframework.stereotype.Controller",
        "org.springframework.web.bind.annotation.RestController"
    );

    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";
    private static final Set<String> RESOURCE_ANNOTATIONS = Set.of(
        "jakarta.annotation.Resource",
        "javax.annotation.Resource"
    );

    /**
     * Scan compiled classes for Spring DI patterns and return dependency declarations.
     *
     * @param allModules all module declarations from source parsing
     * @param classDir   directory containing compiled .class files
     * @return post-process result with SPRING_DI edges and blind spots
     */
    public PostProcessResult process(List<ModuleDeclaration> allModules, Path classDir) {
        if (allModules.isEmpty() || classDir == null || !classDir.toFile().isDirectory()) {
            return PostProcessResult.empty();
        }

        // Build FQCN -> module ID map for quick lookup
        Map<String, String> fqcnToModuleId = new HashMap<>();
        for (ModuleDeclaration mod : allModules) {
            String id = mod.id();
            if (id.startsWith("java:")) {
                fqcnToModuleId.put(id.substring(5), id);
            }
        }

        List<DependencyDeclaration> declarations = new ArrayList<>();
        List<BlindSpot> blindSpots = new ArrayList<>();

        try {
            JavaClasses classes = new ClassFileImporter().importPaths(classDir);

            // Find all Spring beans
            Map<String, JavaClass> beanFqcns = new HashMap<>();
            for (JavaClass jc : classes) {
                if (isBean(jc)) {
                    beanFqcns.put(jc.getName(), jc);
                }
            }

            if (beanFqcns.isEmpty()) {
                return PostProcessResult.empty();
            }

            // Build interface -> implementations map
            Map<String, List<String>> interfaceToImpls = new HashMap<>();
            for (JavaClass jc : classes) {
                if (isBean(jc)) {
                    for (JavaClass iface : jc.getAllRawInterfaces()) {
                        interfaceToImpls.computeIfAbsent(iface.getName(), k -> new ArrayList<>())
                            .add(jc.getName());
                    }
                    jc.getRawSuperclass().ifPresent(superclass -> {
                        if (!superclass.getName().equals("java.lang.Object")) {
                            interfaceToImpls.computeIfAbsent(superclass.getName(), k -> new ArrayList<>())
                                .add(jc.getName());
                        }
                    });
                }
            }

            // Scan each bean for injection points
            for (JavaClass bean : beanFqcns.values()) {
                String beanModuleId = fqcnToModuleId.get(bean.getName());
                if (beanModuleId == null) continue;

                // @Autowired and @Resource fields
                for (JavaField field : bean.getFields()) {
                    boolean isAutowired = field.getAnnotations().stream()
                        .anyMatch(a -> a.getRawType().getName().equals(AUTOWIRED));
                    boolean isResource = field.getAnnotations().stream()
                        .anyMatch(a -> RESOURCE_ANNOTATIONS.contains(a.getRawType().getName()));

                    if (isAutowired || isResource) {
                        String fieldType = field.getRawType().getName();
                        resolveDependency(beanModuleId, fieldType, fqcnToModuleId,
                            interfaceToImpls, declarations, blindSpots,
                            (isResource ? "@Resource " : "@Autowired ") + field.getName());
                    }
                }

                // Constructor injection
                bean.getConstructors().forEach(constructor -> {
                    boolean hasAutowired = constructor.getAnnotations().stream()
                        .anyMatch(a -> a.getRawType().getName().equals(AUTOWIRED));
                    boolean isSingleConstructor = bean.getConstructors().size() == 1;

                    if (hasAutowired || isSingleConstructor) {
                        for (var param : constructor.getRawParameterTypes()) {
                            String paramType = param.getName();
                            resolveDependency(beanModuleId, paramType, fqcnToModuleId,
                                interfaceToImpls, declarations, blindSpots,
                                "constructor(" + paramType + ")");
                        }
                    }
                });
            }

        } catch (Exception e) {
            blindSpots.add(new BlindSpot(
                "SPRING_DI", "class-scan",
                "Spring DI scanning failed: " + e.getMessage()
            ));
        }

        return new PostProcessResult(declarations, blindSpots);
    }

    private void resolveDependency(
        String sourceModuleId,
        String targetType,
        Map<String, String> fqcnToModuleId,
        Map<String, List<String>> interfaceToImpls,
        List<DependencyDeclaration> declarations,
        List<BlindSpot> blindSpots,
        String evidence
    ) {
        // Try interface/abstract resolution first — Spring DI resolves
        // injected interfaces to concrete @Component implementations
        List<String> impls = interfaceToImpls.getOrDefault(targetType, List.of());
        if (!impls.isEmpty()) {
            if (impls.size() == 1) {
                String implModuleId = fqcnToModuleId.get(impls.get(0));
                if (implModuleId != null) {
                    declarations.add(new DependencyDeclaration(
                        sourceModuleId, implModuleId,
                        EdgeType.SPRING_DI, Confidence.HIGH,
                        evidence, false
                    ));
                    return;
                }
            } else {
                String implNames = impls.stream()
                    .map(n -> n.substring(n.lastIndexOf('.') + 1))
                    .collect(Collectors.joining(", "));
                blindSpots.add(new BlindSpot(
                    "SPRING_DI", sourceModuleId,
                    "Ambiguous Spring DI: " + targetType + " has " + impls.size() +
                    " implementations (" + implNames + ")"
                ));
                return;
            }
        }

        // Fall back to direct class match
        String directTarget = fqcnToModuleId.get(targetType);
        if (directTarget != null) {
            declarations.add(new DependencyDeclaration(
                sourceModuleId, directTarget,
                EdgeType.SPRING_DI, Confidence.HIGH,
                evidence, false
            ));
        }
    }

    private boolean isBean(JavaClass jc) {
        return jc.getAnnotations().stream()
            .anyMatch(a -> BEAN_ANNOTATIONS.contains(a.getRawType().getName()));
    }
}
