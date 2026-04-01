package com.archon.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import java.util.concurrent.Callable;

@Command(
    name = "ecp",
    description = "Generate Engineering Change Proposal for a proposed change",
    mixinStandardHelpOptions = true
)
public class EcpCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Target class (FQCN or short name)")
    private String target;

    @Parameters(index = "1", description = "Path to the project root")
    private String projectPath;

    @Override
    public Integer call() {
        System.out.println("Archon ecp: " + target + " in " + projectPath + " (not yet implemented)");
        return 0;
    }
}
