package com.archon.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import java.util.concurrent.Callable;

@Command(
    name = "impact",
    description = "Impact analysis for a specific target class",
    mixinStandardHelpOptions = true
)
public class ImpactCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Target class (FQCN or short name)")
    private String target;

    @Parameters(index = "1", description = "Path to the project root")
    private String projectPath;

    @Override
    public Integer call() {
        System.out.println("Archon impact: " + target + " in " + projectPath + " (not yet implemented)");
        return 0;
    }
}
