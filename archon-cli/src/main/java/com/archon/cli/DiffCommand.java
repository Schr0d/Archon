package com.archon.cli;

import com.archon.core.analysis.*;
import com.archon.core.config.ArchonConfig;
import com.archon.core.git.CliGitAdapter;
import com.archon.core.git.GitAdapter;
import com.archon.core.git.GitException;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.RiskLevel;
import com.archon.java.JavaParserPlugin;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
    name = "diff",
    description = "Diff-based change impact analysis between git refs",
    mixinStandardHelpOptions = true
)
public class DiffCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Base git ref (branch, tag, or SHA)")
    private String baseRef;

    @Parameters(index = "1", description = "Head git ref (branch, tag, or SHA)")
    private String headRef;

    @Parameters(index = "2", description = "Path to the project root")
    private String projectPath;

    @Option(names = "--ci", description = "CI mode: exit 1 on new cycles or HIGH+ risk")
    private boolean ciMode;

    @Option(names = "--depth", defaultValue = "3", description = "Max impact propagation depth")
    private int maxDepth;

    @Override
    public Integer call() {
        Path root = Path.of(projectPath);
        if (!root.toFile().exists()) {
            System.err.println("Error: path does not exist: " + projectPath);
            return 1;
        }

        GitAdapter git = new CliGitAdapter();
        if (!git.isGitAvailable()) {
            System.err.println("Error: git not found. Install git or use analyze/impact/check without diff.");
            return 1;
        }

        Path repoRoot;
        try {
            repoRoot = git.discoverRepoRoot(root);
        } catch (GitException e) {
            System.err.println("Error: not a git repository: " + projectPath);
            return 1;
        }

        // Resolve refs
        String baseSha, headSha;
        try {
            baseSha = git.resolveRef(repoRoot, baseRef);
            headSha = git.resolveRef(repoRoot, headRef);
        } catch (GitException e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }

        // Get changed files
        List<String> changedFiles;
        try {
            changedFiles = git.getChangedFiles(repoRoot, baseSha, headSha);
        } catch (GitException e) {
            System.err.println("Error getting changed files: " + e.getMessage());
            return 1;
        }

        if (changedFiles.isEmpty()) {
            System.out.println("No changes between " + baseRef + " and " + headRef);
            return 0;
        }

        ArchonConfig config = ArchonConfig.loadOrDefault(root.resolve(".archon.yml"));

        // Parse head graph (working tree)
        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult headResult = plugin.parse(root, config);
        DependencyGraph headGraph = headResult.getGraph();

        // Parse base graph (from git show for changed files + reuse head for unchanged)
        DependencyGraph baseGraph = buildBaseGraph(git, repoRoot, root, changedFiles, headGraph, config, plugin);

        // Diff the graphs
        GraphDiffer graphDiffer = new GraphDiffer();
        GraphDiff graphDiff = graphDiffer.diff(baseGraph, headGraph);

        // Domain detection on head graph
        DomainDetector domainDetector = new DomainDetector();
        DomainResult domainResult = domainDetector.assignDomains(headGraph, config.getDomains());
        Map<String, String> domainMap = domainResult.getDomains();

        // Determine changed classes (union of git diff files + graph diff nodes)
        Set<String> changedClasses = new LinkedHashSet<>();
        for (String file : changedFiles) {
            if (file.endsWith(".java")) {
                // Extract FQCN from file path heuristically
                headGraph.getNodeIds().stream()
                    .filter(id -> fileEndsWithClass(file, id))
                    .forEach(changedClasses::add);
            }
        }
        changedClasses.addAll(graphDiff.getAddedNodes());
        changedClasses.addAll(graphDiff.getRemovedNodes());
        // Classes involved in edge changes
        for (Edge e : graphDiff.getAddedEdges()) { changedClasses.add(e.getSource()); }
        for (Edge e : graphDiff.getRemovedEdges()) { changedClasses.add(e.getSource()); }

        // Risk synthesis
        RiskSynthesizer riskSynthesizer = new RiskSynthesizer();
        RiskSummary riskSummary = riskSynthesizer.synthesize(
            headGraph, domainMap, changedClasses, graphDiff, config.getCriticalPaths());

        // Impact propagation from each changed class
        ImpactPropagator propagator = new ImpactPropagator();
        List<ImpactResult.ImpactNode> allImpactedNodes = new ArrayList<>();
        Set<String> seenNodes = new HashSet<>();
        for (String changedClass : changedClasses) {
            if (headGraph.containsNode(changedClass)) {
                try {
                    ImpactResult impact = propagator.propagate(headGraph, changedClass, maxDepth, domainMap);
                    for (ImpactResult.ImpactNode node : impact.getImpactedNodes()) {
                        if (seenNodes.add(node.getNodeId())) {
                            allImpactedNodes.add(node);
                        }
                    }
                } catch (IllegalArgumentException ignored) {
                    // Class not in graph — skip
                }
            }
        }

        // Changed class domains
        Map<String, String> changedClassDomains = new LinkedHashMap<>();
        for (String cls : changedClasses) {
            changedClassDomains.put(cls, domainMap.getOrDefault(cls, "unknown"));
        }

        // Build report
        ChangeImpactReport report = new ChangeImpactReport(
            baseRef, headRef, changedClasses, graphDiff,
            changedClassDomains, allImpactedNodes, riskSummary);

        // Output
        printReport(report, git, repoRoot, baseSha, headSha);

        // CI mode
        if (ciMode) {
            RiskLevel overall = report.getRiskSummary().getOverallRisk();
            if (!report.getGraphDiff().getNewCycles().isEmpty()
                || overall == RiskLevel.HIGH || overall == RiskLevel.VERY_HIGH || overall == RiskLevel.BLOCKED) {
                System.out.println("\n\u001B[31mCI: FAIL — new cycles or HIGH+ risk detected\u001B[0m");
                return 1;
            }
            System.out.println("\n\u001B[32mCI: PASS\u001B[0m");
        }

        return 0;
    }

    private DependencyGraph buildBaseGraph(GitAdapter git, Path repoRoot, Path projectRoot,
                                            List<String> changedFiles, DependencyGraph headGraph,
                                            ArchonConfig config, JavaParserPlugin plugin) {
        // Get base content for changed Java files only
        Map<Path, String> baseContents = new LinkedHashMap<>();
        for (String file : changedFiles) {
            if (file.endsWith(".java")) {
                try {
                    String content = git.getFileContent(repoRoot, baseRef, file);
                    if (content != null) {
                        baseContents.put(Path.of(file), content);
                    }
                } catch (GitException ignored) {
                    // File didn't exist in base — it's a new file
                }
            }
        }

        // Source classes = head graph classes (for unchanged files) + FQCNs from base content
        Set<String> sourceClasses = new HashSet<>(headGraph.getNodeIds());
        // FQCNs from base content will be added by parseFromContent automatically

        if (baseContents.isEmpty()) {
            // No Java files changed — base graph is same as head
            return headGraph;
        }

        JavaParserPlugin.ParseResult baseResult = plugin.parseFromContent(baseContents, sourceClasses);
        return baseResult.getGraph();
    }

    private boolean fileEndsWithClass(String filePath, String fqcn) {
        // Convert FQCN to file path: com.example.Foo -> com/example/Foo.java
        String expected = fqcn.replace('.', '/') + ".java";
        return filePath.equals(expected) || filePath.endsWith("/" + expected);
    }

    private void printReport(ChangeImpactReport report, GitAdapter git,
                              Path repoRoot, String baseSha, String headSha) {
        int commitCount;
        try {
            commitCount = git.getCommitCount(repoRoot, baseSha, headSha);
        } catch (GitException e) {
            commitCount = -1;
        }

        String commitInfo = commitCount >= 0 ? " (" + commitCount + " commits)" : "";

        System.out.println("Changes: " + report.getBaseRef() + " \u2192 " + report.getHeadRef() + commitInfo);

        Set<String> added = report.getGraphDiff().getAddedNodes();
        Set<String> removed = report.getGraphDiff().getRemovedNodes();
        int modifiedCount = report.getChangedClasses().size() - added.size() - removed.size();
        if (modifiedCount < 0) modifiedCount = 0;

        System.out.println("Changed classes: " + report.getChangedClasses().size()
            + " (" + added.size() + " added, " + removed.size() + " removed, "
            + modifiedCount + " modified)");

        if (!report.getChangedClasses().isEmpty()) {
            System.out.println();
            System.out.println("Graph changes:");
            for (String node : added) {
                System.out.println("  + " + shortName(node) + " (new)");
            }
            for (String node : removed) {
                System.out.println("  - " + shortName(node) + " (removed)");
            }
            for (String cls : report.getChangedClasses()) {
                if (!added.contains(cls) && !removed.contains(cls)) {
                    System.out.println("  ~ " + shortName(cls) + " (modified)");
                }
            }
        }

        if (!report.getGraphDiff().getAddedEdges().isEmpty()) {
            System.out.println();
            System.out.println("New edges: " + report.getGraphDiff().getAddedEdges().size());
            for (Edge edge : report.getGraphDiff().getAddedEdges().stream().limit(10).toList()) {
                System.out.println("  " + shortName(edge.getSource()) + " -> "
                    + shortName(edge.getTarget()) + " (" + edge.getType() + ")");
            }
            if (report.getGraphDiff().getAddedEdges().size() > 10) {
                System.out.println("  ... and " + (report.getGraphDiff().getAddedEdges().size() - 10) + " more");
            }
        }

        if (!report.getGraphDiff().getRemovedEdges().isEmpty()) {
            System.out.println();
            System.out.println("Removed edges: " + report.getGraphDiff().getRemovedEdges().size());
            for (Edge edge : report.getGraphDiff().getRemovedEdges().stream().limit(10).toList()) {
                System.out.println("  " + shortName(edge.getSource()) + " -> "
                    + shortName(edge.getTarget()) + " (" + edge.getType() + ")");
            }
            if (report.getGraphDiff().getRemovedEdges().size() > 10) {
                System.out.println("  ... and " + (report.getGraphDiff().getRemovedEdges().size() - 10) + " more");
            }
        }

        System.out.println();
        System.out.println("New cycles: " + report.getGraphDiff().getNewCycles().size());
        System.out.println("Fixed cycles: " + report.getGraphDiff().getFixedCycles().size());

        if (!report.getImpactedNodes().isEmpty()) {
            System.out.println();
            System.out.println("Impact radius:");
            System.out.println("  " + report.getChangedClasses().size() + " changed classes \u2192 "
                + report.getImpactedNodes().size() + " downstream dependents (depth " + maxDepth + ")");

            // Cross-domain count
            Set<String> changedDomains = new HashSet<>(report.getChangedClassDomains().values());
            if (changedDomains.size() > 1) {
                System.out.println("  Cross-domain: " + changedDomains.size() + " domains affected");
            }
        }

        // Risk
        RiskSummary risk = report.getRiskSummary();
        String riskColor = risk.getOverallRisk() == RiskLevel.LOW ? "\u001B[32m"
                         : risk.getOverallRisk() == RiskLevel.MEDIUM ? "\u001B[33m"
                         : "\u001B[31m";
        System.out.println();
        System.out.println("Risk: " + riskColor + risk.getOverallRisk() + "\u001B[0m");

        // Per-class risk highlights
        for (Map.Entry<String, RiskLevel> entry : risk.getPerClassRisk().entrySet()) {
            if (entry.getValue().ordinal() >= RiskLevel.MEDIUM.ordinal()) {
                String cls = shortName(entry.getKey());
                String domain = report.getChangedClassDomains().getOrDefault(entry.getKey(), "");
                String domainTag = domain.isEmpty() ? "" : ", " + domain;
                int dependents = countDependents(report, entry.getKey());
                System.out.println("  - " + cls + " modified (" + dependents + " dependents" + domainTag + ")");
            }
        }

        if (risk.getNewCycleCount() == 0) {
            System.out.println("  - No new cycles \u2713");
        }
        if (risk.getCrossDomainEdgeChanges() > 0) {
            System.out.println("  - " + risk.getCrossDomainEdgeChanges() + " cross-domain edge changes");
        }

        // CI verdict
        if (ciMode) {
            System.out.println();
            if (risk.getOverallRisk().ordinal() >= RiskLevel.HIGH.ordinal()
                || risk.getNewCycleCount() > 0) {
                System.out.println("CI: would exit 1 (new cycles or HIGH risk)");
            } else {
                System.out.println("CI: would exit 0 (no new cycles, no HIGH risk)");
            }
        }
    }

    private String shortName(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
    }

    private int countDependents(ChangeImpactReport report, String fqcn) {
        return (int) report.getImpactedNodes().stream()
            .filter(n -> n.getNodeId().equals(fqcn))
            .count();
    }
}
