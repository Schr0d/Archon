package com.archon.core.coordination;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.Node;
import com.archon.core.plugin.*;

import java.util.*;

/**
 * Builds a DependencyGraph from ModuleDeclaration and DependencyDeclaration lists.
 *
 * <p>Shared utility used by ParseOrchestrator, DiffCommand, and language plugins.
 * Eliminates the duplicated "declarations to graph" conversion logic.
 *
 * <p>Steps:
 * <ol>
 *   <li>Create a node for each unique ModuleDeclaration.id (dedup by ID, keep first seen)</li>
 *   <li>For each DependencyDeclaration, create an edge (skip if target not in node map, collect warning)</li>
 *   <li>Strip namespace prefixes from the final graph</li>
 * </ol>
 */
public class DeclarationGraphBuilder {

    private DeclarationGraphBuilder() {} // utility class

    /**
     * Result of building a graph from declarations.
     * Includes the built graph and any warnings generated during construction.
     */
    public static class BuildResult {
        private final DependencyGraph graph;
        private final List<String> warnings;

        BuildResult(DependencyGraph graph, List<String> warnings) {
            this.graph = graph;
            this.warnings = List.copyOf(warnings);
        }

        public DependencyGraph graph() {
            return graph;
        }

        public List<String> warnings() {
            return warnings;
        }
    }

    /**
     * Builds a DependencyGraph from collected ModuleDeclaration and DependencyDeclaration records.
     *
     * @param moduleDeclarations collected module declarations from plugins
     * @param dependencyDeclarations collected dependency declarations from plugins
     * @return BuildResult containing the graph and any warnings
     */
    public static BuildResult build(
        List<ModuleDeclaration> moduleDeclarations,
        List<DependencyDeclaration> dependencyDeclarations
    ) {
        DependencyGraph.MutableBuilder builder = new DependencyGraph.MutableBuilder();
        List<String> warnings = new ArrayList<>();

        // Phase 1: Build node map from declarations (dedup by ID, keep first seen)
        Set<String> seenIds = new HashSet<>();
        for (ModuleDeclaration decl : moduleDeclarations) {
            if (seenIds.add(decl.id())) {
                Node node = Node.builder()
                    .id(decl.id())
                    .type(mapNodeType(decl.type()))
                    .sourcePath(decl.sourcePath())
                    .confidence(mapConfidence(decl.confidence()))
                    .build();
                builder.addNode(node);
            }
        }

        // Phase 2: Build edges from declarations
        Set<String> knownNodeIds = new HashSet<>(builder.knownNodeIds());
        for (DependencyDeclaration decl : dependencyDeclarations) {
            if (!knownNodeIds.contains(decl.sourceId())) {
                warnings.add("Edge source '" + decl.sourceId() + "' not in node map; skipping edge.");
                continue;
            }
            if (!knownNodeIds.contains(decl.targetId())) {
                warnings.add("Edge target '" + decl.targetId() + "' not in node map; skipping edge from '" + decl.sourceId() + "'.");
                continue;
            }
            Edge edge = Edge.builder()
                .source(decl.sourceId())
                .target(decl.targetId())
                .type(mapEdgeType(decl.edgeType()))
                .confidence(mapConfidence(decl.confidence()))
                .evidence(decl.evidence())
                .dynamic(decl.dynamic())
                .build();
            builder.addEdge(edge);
        }

        // Strip namespace prefixes and build final graph
        DependencyGraph graph = DependencyGraph.stripNamespacePrefixesAndBuild(builder);
        return new BuildResult(graph, warnings);
    }

    // --- Enum mapping: plugin enums -> graph enums ---

    static com.archon.core.graph.NodeType mapNodeType(NodeType pluginNodeType) {
        return com.archon.core.graph.NodeType.valueOf(pluginNodeType.name());
    }

    static com.archon.core.graph.EdgeType mapEdgeType(EdgeType pluginEdgeType) {
        return com.archon.core.graph.EdgeType.valueOf(pluginEdgeType.name());
    }

    static com.archon.core.graph.Confidence mapConfidence(Confidence pluginConfidence) {
        return com.archon.core.graph.Confidence.valueOf(pluginConfidence.name());
    }
}
