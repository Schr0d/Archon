package com.archon.viz;

import com.archon.core.analysis.AnalysisPipeline;
import com.archon.core.analysis.AnalysisResult;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Command(name = "view", mixinStandardHelpOptions = true, version = "Archon 0.5.0.0",
       description = "Visualize dependency graph in terminal and web viewer")
public class ViewCommand implements Callable<Integer> {

    @Option(names = {"--port"}, description = "Port for web server (default: 8420-8430)")
    Integer port;

    @Option(names = {"--no-open"}, description = "Don't open browser automatically")
    boolean noOpen;

    @Option(names = {"--format"}, description = "Output format: text or json (default: text)")
    String format = "text";

    @CommandLine.Parameters(index = "0", description = "Path to analyze", paramLabel = "PATH")
    String path;

    @Override
    public Integer call() throws Exception {
        // Parse the project using shared pipeline
        AnalysisResult result = AnalysisPipeline.run(Paths.get(path));

        // Print terminal output
        if ("text".equals(format)) {
            PrintWriter writer = new PrintWriter(System.out, true);
            TerminalRenderer renderer = new TerminalRenderer(result, writer);
            renderer.render();
        } else if ("json".equals(format)) {
            JsonSerializer serializer = new JsonSerializer();
            String json = serializer.toJson(result.graph(), result.domains(),
                result.cycles(), result.hotspots(), result.blindSpots());
            System.out.println(json);
        }

        // Start web server
        ViewServer server = port != null ? new ViewServer(port) : new ViewServer();
        PerspectiveBuilder builder = new PerspectiveBuilder(result.graph(), result.domains());

        // Serialize graph data for server
        JsonSerializer serializer = new JsonSerializer();
        String graphJson = serializer.toJson(result.graph(), result.domains(),
            result.cycles(), result.hotspots(), result.blindSpots());
        server.setGraphData(graphJson);

        server.start();

        // Open browser unless --no-open flag
        if (!noOpen) {
            server.openBrowser();
        }

        System.out.println("archon: viewer running at http://127.0.0.1:" + server.getPort() + "/");
        System.out.println("Press Ctrl+C to stop");

        // Keep server running until interrupt
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            server.stop();
        }

        return 0;
    }
}
