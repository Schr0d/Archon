package com.archon.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Option;
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
        System.out.println("Archon check: " + projectPath + " (not yet implemented)");
        return 0;
    }
}
