package com.archon.cli;

import com.archon.core.analysis.*;
import com.archon.core.config.ArchonConfig;
import com.archon.core.coordination.DeclarationGraphBuilder;
import com.archon.core.coordination.ParseOrchestrator;
import com.archon.core.git.CliGitAdapter;
import com.archon.core.git.GitAdapter;
import com.archon.core.git.GitException;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Edge;
import com.archon.core.graph.RiskLevel;
import com.archon.core.output.AgentOutputFormatter;
import com.archon.core.plugin.BlindSpot;
import com.archon.core.plugin.DependencyDeclaration;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ModuleDeclaration;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.archon.core.plugin.PluginDiscoverer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
    name = "diff",
    description = "Diff-based change impact analysis between git refs",
    mixinStandardHelpOptions = true
)
public class DiffCommand implements Callable<Integer> {

    private static final String WORKING_TREE = "WORKING_TREE";

    @Parameters(index = "0..*", arity = "0..3", description = "Base ref, head ref, and project path (all optional)")
    List<String> params;

    @Option(names = "--ci", description = "CI mode: exit 1 on new cycles or HIGH+ risk")
    boolean ciMode;

    @Option(names = "--depth", defaultValue = "3", description = "Max impact propagation depth")
    int maxDepth;

    @Option(names = "--format", description = "Output format: text (default), agent")
    String format;

    /** Cached agent-format flag for use by printStep and other methods. */
    private boolean useAgentFormat;

    @Override
    public Integer call() {
        // Parse optional parameters
        String baseRef;
        String headRef;
        String projectPath;

        if (params == null || params.isEmpty()) {
            baseRef = "HEAD";
            headRef = WORKING_TREE;
            projectPath = ".";
        } else if (params.size() == 1) {
            baseRef = params.get(0);
            headRef = WORKING_TREE;
            projectPath = ".";
        } else if (params.size() == 2) {
            baseRef = params.get(0);
            headRef = params.get(1);
            projectPath = ".";
        } else {
            baseRef = params.get(0);
            headRef = params.get(1);
            projectPath = params.get(2);
        }

        Path root = Path.of(projectPath);
        if (!root.toFile().exists()) {
            System.err.println("Error: path does not exist: " + projectPath);
            return 1;
        }

        // Detect agent format early to suppress progress messages with ANSI codes
        useAgentFormat = "agent".equals(format) || (format == null && System.console() == null);

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

        // Crash recovery: check for stale lock file from interrupted previous run
        recoverFromCrash(repoRoot, git);

        // Resolve refs and get changed files
        boolean isWorkingTree = WORKING_TREE.equals(headRef);
        String baseSha;
        List<String> changedFiles;

        if (isWorkingTree) {
            // Working tree mode: compare HEAD vs staged + unstaged
            printStep("Resolving refs...");
            try {
                baseSha = git.resolveRef(repoRoot, baseRef);
            } catch (GitException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }
            printStep("Computing working tree changes...");
            try {
                changedFiles = git.getWorkingTreeChanges(repoRoot);
            } catch (GitException e) {
                System.err.println("Error getting working tree changes: " + e.getMessage());
                return 1;
            }
        } else {
            // Normal two-ref mode
            printStep("Resolving refs...");
            String headSha;
            try {
                baseSha = git.resolveRef(repoRoot, baseRef);
                headSha = git.resolveRef(repoRoot, headRef);
            } catch (GitException e) {
                System.err.println("Error: " + e.getMessage());
                return 1;
            }

            printStep("Computing changed files...");
            try {
                changedFiles = git.getChangedFiles(repoRoot, baseSha, headSha);
            } catch (GitException e) {
                System.err.println("Error getting changed files: " + e.getMessage());
                return 1;
            }
        }

        if (changedFiles.isEmpty()) {
            String desc = isWorkingTree ? "working tree and " + baseRef : baseRef + " and " + headRef;
            System.out.println("No changes between " + desc);
            return 0;
        }

        ArchonConfig config = ArchonConfig.loadOrDefault(root.resolve(".archon.yml"));

        // Discover plugins
        PluginDiscoverer discoverer = new PluginDiscoverer();
        List<LanguagePlugin> plugins = discoverer.discoverWithConflictCheck();

        if (plugins.isEmpty()) {
            System.err.println("Error: No language plugins found. Please ensure plugin JARs are on the classpath.");
            return 1;
        }

        // Collect all file extensions from plugins
        Set<String> extensions = plugins.stream()
            .flatMap(p -> p.fileExtensions().stream())
            .collect(Collectors.toSet());

        // Collect all source files
        List<Path> sourceFiles = AnalyzeCommand.collectSourceFilesStatic(root, extensions);

        if (sourceFiles.isEmpty()) {
            System.out.println("No source files found. Check project path.");
            return 0;
        }

        // Reset any plugin state before parsing
        plugins.forEach(LanguagePlugin::reset);

        // Parse head graph (working tree)
        printStep("Parsing head graph (" + sourceFiles.size() + " source files, "
            + changedFiles.size() + " changed)...");
        ParseOrchestrator orchestrator = new ParseOrchestrator(plugins);
        ParseContext context = new ParseContext(root, extensions);
        ParseResult headResult = orchestrator.parse(sourceFiles, context);
        DependencyGraph headGraph = headResult.getGraph();
        printStep("Head graph: " + headGraph.getNodeIds().size() + " classes, " + headGraph.edgeCount() + " edges");

        // Parse base graph (from git show for changed files + reuse head for unchanged)
        printStep("Building base graph...");
        DependencyGraph baseGraph = buildBaseGraph(git, repoRoot, root, changedFiles, headGraph, config, plugins, extensions, baseRef);
        printStep("Base graph: " + baseGraph.getNodeIds().size() + " classes, " + baseGraph.edgeCount() + " edges");

        // Diff the graphs
        printStep("Diffing graphs...");
        GraphDiffer graphDiffer = new GraphDiffer();
        GraphDiff graphDiff = graphDiffer.diff(baseGraph, headGraph);

        // Domain detection on head graph
        printStep("Detecting domains...");
        DomainDetector domainDetector = new DomainDetector();
        DomainResult domainResult = domainDetector.assignDomains(headGraph, config.getDomains());
        Map<String, String> domainMap = domainResult.getDomains();

        // Determine changed classes (union of git diff files + graph diff nodes)
        Set<String> changedClasses = new LinkedHashSet<>();
        for (String file : changedFiles) {
            int dotIndex = file.lastIndexOf('.');
            if (dotIndex > 0) {
                String ext = file.substring(dotIndex + 1);
                if (extensions.contains(ext)) {
                    // Extract node ID from file path heuristically
                    headGraph.getNodeIds().stream()
                        .filter(id -> fileMatchesNode(file, id, ext))
                        .forEach(changedClasses::add);
                }
            }
        }
        changedClasses.addAll(graphDiff.getAddedNodes());
        changedClasses.addAll(graphDiff.getRemovedNodes());
        // Classes involved in edge changes
        for (Edge e : graphDiff.getAddedEdges()) { changedClasses.add(e.getSource()); }
        for (Edge e : graphDiff.getRemovedEdges()) { changedClasses.add(e.getSource()); }

        // Risk synthesis
        printStep("Synthesizing risk...");
        RiskSynthesizer riskSynthesizer = new RiskSynthesizer();
        RiskSummary riskSummary = riskSynthesizer.synthesize(
            headGraph, domainMap, changedClasses, graphDiff, config.getCriticalPaths());

        // Impact propagation from each changed class
        printStep("Propagating impact (depth " + maxDepth + ")...");
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
        String displayHeadRef = isWorkingTree ? "working tree" : headRef;
        ChangeImpactReport report = new ChangeImpactReport(
            baseRef, displayHeadRef, changedClasses, graphDiff,
            changedClassDomains, allImpactedNodes, riskSummary);

        // Agent format output (short-circuits all other output)
        if (useAgentFormat) {
            String agentOutput = formatAgentDiff(report, headGraph, domainMap,
                headResult.getBlindSpots(), projectPath);
            System.out.println(agentOutput);
            return ciFailCheck(report) ? 1 : 0;
        }

        // Output
        printReport(report, git, repoRoot, baseSha, isWorkingTree);

        // CI mode
        if (ciMode) {
            if (ciFailCheck(report)) {
                System.out.println("\n\u001B[31mCI: FAIL — new cycles or HIGH+ risk detected\u001B[0m");
                return 1;
            }
            System.out.println("\n\u001B[32mCI: PASS\u001B[0m");
        }

        return 0;
    }

    private boolean ciFailCheck(ChangeImpactReport report) {
        RiskLevel overall = report.getRiskSummary().getOverallRisk();
        return !report.getGraphDiff().getNewCycles().isEmpty()
            || overall == RiskLevel.HIGH || overall == RiskLevel.VERY_HIGH || overall == RiskLevel.BLOCKED;
    }

    /**
     * Format diff results as compressed agent output.
     */
    private String formatAgentDiff(ChangeImpactReport report, DependencyGraph headGraph,
                                    Map<String, String> domainMap, List<BlindSpot> blindSpots,
                                    String projectPath) {
        StringBuilder sb = new StringBuilder();

        sb.append("Archon Diff: ").append(report.getBaseRef())
          .append(" -> ").append(report.getHeadRef()).append("\n");

        Set<String> added = report.getGraphDiff().getAddedNodes();
        Set<String> removed = report.getGraphDiff().getRemovedNodes();
        int modifiedCount = report.getChangedClasses().size() - added.size() - removed.size();
        if (modifiedCount < 0) modifiedCount = 0;

        sb.append("Changed: ").append(report.getChangedClasses().size())
          .append(" (").append(added.size()).append(" added, ")
          .append(removed.size()).append(" removed, ")
          .append(modifiedCount).append(" modified)\n");

        if (!report.getImpactedNodes().isEmpty()) {
            sb.append("Blast radius: ").append(report.getChangedClasses().size())
              .append(" changed -> ").append(report.getImpactedNodes().size())
              .append(" impacted (depth ").append(maxDepth).append(")\n");
        }

        // Risk
        sb.append("Risk: ").append(report.getRiskSummary().getOverallRisk()).append("\n");

        // Changed classes
        if (!report.getChangedClasses().isEmpty()) {
            sb.append("\nCHANGED:\n");
            for (String node : added) {
                sb.append("  + ").append(shortName(node)).append("\n");
            }
            for (String node : removed) {
                sb.append("  - ").append(shortName(node)).append("\n");
            }
            for (String cls : report.getChangedClasses()) {
                if (!added.contains(cls) && !removed.contains(cls)) {
                    sb.append("  ~ ").append(shortName(cls)).append("\n");
                }
            }
        }

        // New edges
        if (!report.getGraphDiff().getAddedEdges().isEmpty()) {
            sb.append("\nNEW EDGES (").append(report.getGraphDiff().getAddedEdges().size()).append("):\n");
            for (Edge edge : report.getGraphDiff().getAddedEdges().stream().limit(10).toList()) {
                sb.append("  ").append(shortName(edge.getSource())).append(" -> ")
                  .append(shortName(edge.getTarget())).append("\n");
            }
            if (report.getGraphDiff().getAddedEdges().size() > 10) {
                sb.append("  ... +").append(report.getGraphDiff().getAddedEdges().size() - 10).append(" more\n");
            }
        }

        // New cycles
        if (!report.getGraphDiff().getNewCycles().isEmpty()) {
            sb.append("\nNEW CYCLES:\n");
            for (List<String> cycle : report.getGraphDiff().getNewCycles()) {
                if (cycle.isEmpty()) continue;
                sb.append("- ").append(String.join(" -> ", cycle))
                  .append(" -> ").append(cycle.get(0)).append("\n");
            }
        }

        // Blind spots
        if (blindSpots != null && !blindSpots.isEmpty()) {
            sb.append("\nBLIND SPOTS:\n");
            Map<String, List<BlindSpot>> byType = blindSpots.stream()
                .collect(Collectors.groupingBy(BlindSpot::getType, LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<String, List<BlindSpot>> entry : byType.entrySet()) {
                sb.append("- ").append(entry.getValue().size()).append(" ").append(entry.getKey());
                String desc = entry.getValue().get(0).getDescription();
                if (desc != null && !desc.isEmpty()) {
                    sb.append(": ").append(desc);
                }
                sb.append("\n");
            }
        }

        sb.append("\nRun `archon diff` before committing to see blast radius.\n");

        return sb.toString();
    }

    private DependencyGraph buildBaseGraph(GitAdapter git, Path repoRoot, Path projectRoot,
                                            List<String> changedFiles, DependencyGraph headGraph,
                                            ArchonConfig config, List<LanguagePlugin> plugins,
                                            Set<String> extensions, String baseRef) {
        // Partition changed files into batch-parse vs per-file groups
        Map<Boolean, List<String>> partitioned = partitionChangedFiles(changedFiles, plugins, extensions);
        List<String> batchFiles = partitioned.getOrDefault(true, List.of());
        List<String> perFiles = partitioned.getOrDefault(false, List.of());

        // Identify node IDs that belong to changed files
        Set<String> changedFileNodes = new HashSet<>();
        for (String file : changedFiles) {
            int dotIndex = file.lastIndexOf('.');
            if (dotIndex > 0) {
                String ext = file.substring(dotIndex + 1);
                if (extensions.contains(ext)) {
                    headGraph.getNodeIds().stream()
                        .filter(id -> fileMatchesNode(file, id, ext))
                        .forEach(changedFileNodes::add);
                }
            }
        }

        // Step 1: Copy unchanged nodes and edges from head graph
        DependencyGraph.MutableBuilder baseBuilder = new DependencyGraph.MutableBuilder();
        for (String nodeId : headGraph.getNodeIds()) {
            if (!changedFileNodes.contains(nodeId)) {
                baseBuilder.addNode(headGraph.getNode(nodeId).orElseThrow());
            }
        }
        for (Edge edge : headGraph.getAllEdges()) {
            if (!changedFileNodes.contains(edge.getSource())
                && !changedFileNodes.contains(edge.getTarget())) {
                baseBuilder.addEdge(edge);
            }
        }

        // Step 2: Parse base versions of batch-parse plugin files via stash+checkout
        if (!batchFiles.isEmpty()) {
            DependencyGraph batchGraph = buildBaseGraphViaCheckout(
                git, repoRoot, projectRoot, batchFiles, headGraph, extensions, baseRef, plugins
            );
            if (batchGraph != null) {
                DependencyGraph.mergeInto(batchGraph, baseBuilder);
            } else {
                // Fallback: use per-file regex path for batch files
                printStep("Warning: batch-parse checkout failed, falling back to per-file parsing for batch plugins");
                perFiles = new ArrayList<>(perFiles);
                perFiles.addAll(batchFiles);
            }
        }

        // Step 3: Parse base versions of per-file plugin files using git show
        if (!perFiles.isEmpty()) {
            Map<Path, String> baseContents = new LinkedHashMap<>();
            for (String file : perFiles) {
                int dotIndex = file.lastIndexOf('.');
                if (dotIndex > 0) {
                    String ext = file.substring(dotIndex + 1);
                    if (extensions.contains(ext)) {
                        try {
                            String content = git.getFileContent(repoRoot, baseRef, file);
                            if (content != null) {
                                baseContents.put(Path.of(file), content);
                            }
                        } catch (GitException ignored) {
                            // File didn't exist in base -- it's a new file
                        }
                    }
                }
            }

            List<ModuleDeclaration> allModuleDecls = new ArrayList<>();
            List<DependencyDeclaration> allDepDecls = new ArrayList<>();

            for (Map.Entry<Path, String> entry : baseContents.entrySet()) {
                Path file = entry.getKey();
                String content = entry.getValue();
                String fileName = file.getFileName().toString();
                int dotIndex = fileName.lastIndexOf('.');
                String ext = dotIndex > 0 ? fileName.substring(dotIndex + 1) : "";

                LanguagePlugin plugin = plugins.stream()
                    .filter(p -> p.fileExtensions().contains(ext))
                    .findFirst()
                    .orElse(null);

                if (plugin != null) {
                    ParseContext context = new ParseContext(projectRoot, extensions);
                    ParseResult result = plugin.parseFromContent(
                        file.toString(),
                        content,
                        context
                    );
                    allModuleDecls.addAll(result.getModuleDeclarations());
                    allDepDecls.addAll(result.getDeclarations());
                }
            }

            DeclarationGraphBuilder.BuildResult buildResult = DeclarationGraphBuilder.build(
                allModuleDecls, allDepDecls
            );
            DependencyGraph changedGraph = buildResult.graph();
            DependencyGraph.mergeInto(changedGraph, baseBuilder);
        }

        return baseBuilder.build();
    }

    /**
     * Build the base graph for batch-parse plugins by stashing the working tree,
     * checking out the base ref, parsing the whole source tree, then restoring.
     *
     * @return the parsed graph, or null if stash/checkout fails (caller should fall back)
     */
    private DependencyGraph buildBaseGraphViaCheckout(GitAdapter git, Path repoRoot, Path projectRoot,
                                                       List<String> batchFiles, DependencyGraph headGraph,
                                                       Set<String> extensions, String baseRef,
                                                       List<LanguagePlugin> originalPlugins) {
        // Collect extensions handled by batch-parse plugins only
        Set<String> batchExtensions = new HashSet<>();
        for (LanguagePlugin plugin : originalPlugins) {
            if (plugin.supportsBatchParse()) {
                batchExtensions.addAll(plugin.fileExtensions());
            }
        }

        String savedBranch = null;
        String savedSha = null;
        String stashRef = null;
        boolean checkedOut = false;

        try {
            // Save current state
            savedBranch = git.getCurrentBranch(repoRoot);
            savedSha = git.getHeadSha(repoRoot);

            // Stash working tree changes
            stashRef = git.stashPush(repoRoot);

            // Write lock file once (after stash, so stashRef is accurate)
            // If process crashes before this point, stash is orphaned but working tree is intact
            writeRestoreLockFile(repoRoot, savedBranch, savedSha, stashRef);

            // Checkout base ref
            git.checkout(repoRoot, baseRef);
            checkedOut = true;

            // Create fresh plugin instances (originals have working-tree cache)
            PluginDiscoverer discoverer = new PluginDiscoverer();
            List<LanguagePlugin> freshPlugins = discoverer.discoverWithConflictCheck();
            freshPlugins.forEach(LanguagePlugin::reset);

            // Collect only batch-plugin source files from the base working tree
            List<Path> batchSourceFiles = AnalyzeCommand.collectSourceFilesStatic(projectRoot, batchExtensions);

            if (batchSourceFiles.isEmpty()) {
                return new DependencyGraph.MutableBuilder().build();
            }

            // Parse with fresh orchestrator
            ParseOrchestrator orchestrator = new ParseOrchestrator(freshPlugins);
            ParseContext context = new ParseContext(projectRoot, extensions);
            ParseResult baseResult = orchestrator.parse(batchSourceFiles, context);

            return baseResult.getGraph();

        } catch (Exception e) {
            printStep("Batch-parse checkout failed: " + e.getMessage());
            return null;
        } finally {
            // Always restore working tree
            if (checkedOut) {
                try {
                    String restoreRef = (savedBranch != null) ? savedBranch : savedSha;
                    if (restoreRef != null) {
                        git.checkout(repoRoot, restoreRef);
                    }
                } catch (Exception e) {
                    System.err.println("Warning: failed to restore branch after batch parse: " + e.getMessage());
                }
            }
            boolean stashPopFailed = false;
            if (stashRef != null) {
                try {
                    git.stashPop(repoRoot);
                } catch (Exception e) {
                    System.err.println("Warning: failed to restore stashed changes: " + e.getMessage());
                    System.err.println("Your changes are preserved in git stash. Run 'git stash pop' manually.");
                    stashPopFailed = true;
                }
            }
            // Only delete lock file if stash pop succeeded (or there was no stash)
            if (!stashPopFailed) {
                deleteRestoreLockFile(repoRoot);
            }
        }
    }

    /**
     * Partition changed files into batch-parse vs per-file groups based on their plugin.
     *
     * @return map with true=batch-parse files, false=per-file files
     */
    // Package-private for testability
    Map<Boolean, List<String>> partitionChangedFiles(List<String> changedFiles,
                                                              List<LanguagePlugin> plugins,
                                                              Set<String> extensions) {
        // Build extension -> isBatch map
        Map<String, Boolean> extIsBatch = new HashMap<>();
        for (LanguagePlugin plugin : plugins) {
            boolean isBatch = plugin.supportsBatchParse();
            for (String ext : plugin.fileExtensions()) {
                extIsBatch.put(ext, isBatch);
            }
        }

        Map<Boolean, List<String>> partitioned = new HashMap<>();
        partitioned.put(true, new ArrayList<>());
        partitioned.put(false, new ArrayList<>());

        for (String file : changedFiles) {
            int dotIndex = file.lastIndexOf('.');
            if (dotIndex > 0) {
                String ext = file.substring(dotIndex + 1);
                if (extensions.contains(ext)) {
                    Boolean isBatch = extIsBatch.get(ext);
                    if (isBatch != null && isBatch) {
                        partitioned.get(true).add(file);
                    } else {
                        partitioned.get(false).add(file);
                    }
                }
            }
        }

        return partitioned;
    }

    private static final String LOCK_FILE_NAME = "archon-restore.json";

    /** Returns the lock file path inside .git/ (never tracked by git, never appears in git status). */
    private Path lockFilePath(Path repoRoot) {
        return repoRoot.resolve(".git").resolve(LOCK_FILE_NAME);
    }

    // Package-private for testability
    void writeRestoreLockFile(Path repoRoot, String branch, String sha, String stashRef) {
        try {
            String branchJson = (branch != null) ? "\"" + branch + "\"" : "null";
            String stashJson = (stashRef != null) ? "\"" + stashRef + "\"" : "null";
            String timestamp = Instant.now().toString();
            String json = "{\"branch\":" + branchJson
                + ",\"sha\":\"" + sha + "\""
                + ",\"stashRef\":" + stashJson
                + ",\"timestamp\":\"" + timestamp + "\"}";
            Files.writeString(lockFilePath(repoRoot), json);
        } catch (IOException e) {
            System.err.println("Warning: failed to write restore lock file: " + e.getMessage());
        }
    }

    // Package-private for testability
    void deleteRestoreLockFile(Path repoRoot) {
        try {
            Files.deleteIfExists(lockFilePath(repoRoot));
        } catch (IOException e) {
            System.err.println("Warning: failed to delete restore lock file: " + e.getMessage());
        }
    }

    /**
     * Check for a stale lock file from a crashed previous run and restore the working tree.
     */
    private void recoverFromCrash(Path repoRoot, GitAdapter git) {
        Path lockFile = lockFilePath(repoRoot);
        if (!Files.exists(lockFile)) {
            return;
        }

        System.err.println("Warning: Previous archon run was interrupted. Restoring working tree...");

        try {
            String content = Files.readString(lockFile);
            // Simple JSON parsing without Gson dependency
            String branch = extractJsonString(content, "branch");
            String sha = extractJsonString(content, "sha");
            String stashRef = extractJsonString(content, "stashRef");

            // Restore branch/commit
            String restoreRef = (branch != null) ? branch : sha;
            if (restoreRef != null) {
                try {
                    git.checkout(repoRoot, restoreRef);
                } catch (Exception e) {
                    // Try SHA if branch fails
                    if (sha != null && !sha.equals(restoreRef)) {
                        try {
                            git.checkout(repoRoot, sha);
                        } catch (Exception ignored) {
                            System.err.println("Warning: could not restore to commit " + sha);
                        }
                    }
                }
            }

            // Pop stash if one was saved
            if (stashRef != null) {
                try {
                    git.stashPop(repoRoot);
                } catch (Exception e) {
                    System.err.println("Warning: could not restore stashed changes: " + e.getMessage());
                }
            }

            // Delete lock file
            Files.deleteIfExists(lockFile);
            System.err.println("Working tree restored successfully.");
        } catch (Exception e) {
            System.err.println("Warning: crash recovery failed: " + e.getMessage());
            System.err.println("Manual recovery may be needed. Lock file: " + lockFile);
            // Don't delete lock file on recovery failure so user can inspect
        }
    }

    /**
     * Extract a string value from simple JSON (no nested objects).
     * Returns null for null values or missing keys.
     */
    // Package-private for testability
    String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int valueStart = idx + search.length();

        // Skip whitespace
        while (valueStart < json.length() && json.charAt(valueStart) == ' ') valueStart++;

        if (valueStart >= json.length()) return null;

        if (json.startsWith("null", valueStart)) return null;
        if (json.charAt(valueStart) != '"') return null;

        valueStart++; // skip opening quote
        int valueEnd = json.indexOf('"', valueStart);
        if (valueEnd < 0) return null;

        return json.substring(valueStart, valueEnd);
    }

    private boolean fileMatchesNode(String filePath, String nodeId, String ext) {
        if ("java".equals(ext)) {
            // Convert FQCN to file path: com.example.Foo -> com/example/Foo.java
            String expected = nodeId.replace('.', '/') + ".java";
            return filePath.equals(expected) || filePath.endsWith("/" + expected);
        } else {
            // For JS/TS, node ID is the module path
            // Normalize both for comparison
            String normalizedPath = filePath.replace('\\', '/');
            String normalizedId = nodeId.replace('\\', '/');
            return normalizedPath.equals(normalizedId) || normalizedPath.endsWith("/" + normalizedId);
        }
    }

    private void printReport(ChangeImpactReport report, GitAdapter git,
                              Path repoRoot, String baseSha, boolean isWorkingTree) {
        int commitCount = -1;
        if (!isWorkingTree) {
            try {
                String headSha = git.resolveRef(repoRoot, report.getHeadRef());
                commitCount = git.getCommitCount(repoRoot, baseSha, headSha);
            } catch (GitException e) {
                commitCount = -1;
            }
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

    private String shortName(String id) {
        // Java FQCN: take class name after last dot
        int lastDot = id.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < id.length() - 1
            && Character.isUpperCase(id.charAt(lastDot + 1))) {
            return id.substring(lastDot + 1);
        }
        // JS/TS path-style: take filename after last slash
        int lastSlash = id.lastIndexOf('/');
        if (lastSlash >= 0) {
            return id.substring(lastSlash + 1);
        }
        return id;
    }

    private int countDependents(ChangeImpactReport report, String fqcn) {
        return (int) report.getImpactedNodes().stream()
            .filter(n -> n.getNodeId().equals(fqcn))
            .count();
    }

    private void printStep(String message) {
        // Suppress progress output in agent mode to prevent ANSI codes in piped output
        if (!useAgentFormat) {
            System.out.println("\u001B[2m  " + message + "\u001B[0m");
        }
    }
}
