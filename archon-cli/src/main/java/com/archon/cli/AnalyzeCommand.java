package com.archon.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;
import java.util.concurrent.Callable;

@Command(
    name = "analyze",
    description = "Full structural analysis of a Java project",
    mixinStandardHelpOptions = true
)
public class AnalyzeCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Path to the project root")
    private String projectPath;

    @Option(names = "--json", description = "Output machine-readable JSON")
    private boolean json;

    @Option(names = "--dot", description = "Export Graphviz DOT to file")
    private String dotFile;

    @Option(names = "--verbose", description = "Show detailed parsing logs")
    private boolean verbose;

    @Override
    public Integer call() {
        System.out.println("Archon analyze: " + projectPath + " (not yet implemented)");
        return 0;
    }
}
