package com.archon.cli;

import com.archon.core.analysis.CycleDetector;
import com.archon.core.analysis.DomainDetector;
import com.archon.core.analysis.DomainResult;
import com.archon.core.config.ArchonConfig;
import com.archon.core.config.RuleValidator;
import com.archon.core.config.RuleViolation;
import com.archon.core.graph.DependencyGraph;
import com.archon.java.JavaParserPlugin;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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

        // Parse
        JavaParserPlugin plugin = new JavaParserPlugin();
        JavaParserPlugin.ParseResult result = plugin.parse(root, config);
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

        // Validate rules
        RuleValidator validator = new RuleValidator();
        List<RuleViolation> violations = validator.validate(graph, config, domainMap, cycles);

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
