package com.archon.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "archon",
    description = "Archon — Domain Analyzer & Dependency Guard",
    subcommands = {
        AnalyzeCommand.class,
        ImpactCommand.class,
        EcpCommand.class,
        CheckCommand.class
    },
    mixinStandardHelpOptions = true,
    version = "0.1.0"
)
public class ArchonCli implements Runnable {
    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ArchonCli()).execute(args);
        System.exit(exitCode);
    }
}
