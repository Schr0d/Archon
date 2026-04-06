package com.archon.cli;

import com.archon.viz.ViewCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "archon",
    description = "Archon — Domain Analyzer & Dependency Guard",
    subcommands = {
        AnalyzeCommand.class,
        ImpactCommand.class,
        EcpCommand.class,
        CheckCommand.class,
        DiffCommand.class,
        ViewCommand.class
    },
    mixinStandardHelpOptions = true,
    version = "0.5.0.0"
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
