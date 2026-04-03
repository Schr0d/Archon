package com.archon.cli;

import com.archon.core.analysis.CycleDetector;
import com.archon.core.analysis.DomainDetector;
import com.archon.core.analysis.DomainResult;
import com.archon.core.analysis.ThresholdCalculator;
import com.archon.core.analysis.Thresholds;
import com.archon.core.config.ArchonConfig;
import com.archon.core.config.RuleValidator;
import com.archon.core.config.RuleViolation;
import com.archon.core.coordination.ParseOrchestrator;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.plugin.LanguagePlugin;
import com.archon.core.plugin.ParseContext;
import com.archon.core.plugin.ParseResult;
import com.archon.core.plugin.PluginDiscoverer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
    name = "check",
    description = "Run architecture rule validation",
    mixinStandardHelpOptions = true
)
public class CheckCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Path to the project root")
    private String projectPath;

    @Option(names = "--ci", description = "CI mode: exit 1 on any violation (no ANSI)")
    private boolean ci;

    @Override
    public Integer call() {
        Path root = Path.of(projectPath);
        if (!root.toFile().exists()) {
            System.err.println("Error: path does not exist: " + projectPath);
            return 1;
        }

        ArchonConfig config = ArchonConfig.loadOrDefault(root.resolve(".archon.yml"));

        // Discover plugins and collect source files
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
        plugins.forEach(p -> {
            if (p instanceof com.archon.java.JavaPlugin) {
                ((com.archon.java.JavaPlugin) p).reset();
            }
        });

        // Parse with orchestrator
        ParseOrchestrator orchestrator = new ParseOrchestrator(plugins);
        ParseContext context = new ParseContext(root, extensions);
        ParseResult result = orchestrator.parse(sourceFiles, context);
        DependencyGraph graph = result.getGraph();

        if (graph.nodeCount() == 0) {
            System.out.println("No classes found. Check project path.");
            return 0;
        }

        // Detect cycles (needed for rule validation)
        CycleDetector cycleDetector = new CycleDetector();
        List<List<String>> cycles = cycleDetector.detectCycles(graph);

        // Detect domains
        DomainDetector domainDetector = new DomainDetector();
        DomainResult domainResult = domainDetector.assignDomains(graph, config.getDomains());
        Map<String, String> domainMap = domainResult.getDomains();

        // Compute adaptive thresholds
        long distinctDomains = domainMap.values().stream().distinct().count();
        Thresholds thresholds = ThresholdCalculator.calculate(graph.nodeCount(), (int) distinctDomains);

        // Validate rules
        RuleValidator validator = new RuleValidator();
        List<RuleViolation> violations = validator.validate(graph, config, domainMap, cycles, thresholds);

        // Output
        if (violations.isEmpty()) {
            System.out.println("All rules passed. No violations found.");
            return 0;
        }

        long errors = violations.stream().filter(v -> "ERROR".equals(v.getSeverity())).count();
        long warnings = violations.size() - errors;

        System.out.println("Rule violations: " + violations.size()
            + " (" + errors + " errors, " + warnings + " warnings)");
        System.out.println();

        for (RuleViolation v : violations) {
            String icon = "ERROR".equals(v.getSeverity())
                ? (ci ? "ERROR" : "\u001B[31m✗ ERROR\u001B[0m")
                : (ci ? "WARN " : "\u001B[33m⚠ WARN\u001B[0m");
            System.out.println("  " + icon + " [" + v.getRule() + "] " + v.getDetails());
        }

        // Exit 1 in CI mode if any ERROR violations
        if (ci && errors > 0) {
            return 1;
        }

        return 0;
    }
}
