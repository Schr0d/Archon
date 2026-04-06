package com.archon.viz;

import com.archon.core.analysis.AnalysisPipeline;
import com.archon.core.analysis.AnalysisResult;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.io.PrintWriter;
import java.nio.file.Files;
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

    @Option(names = {"--export"}, description = "Export to static HTML file (no server)")
    String exportFile;

    @Option(names = {"--idle-timeout"}, description = "Server idle timeout in minutes (default: 30)")
    Integer idleTimeoutMinutes;

    @CommandLine.Parameters(index = "0", description = "Path to analyze", paramLabel = "PATH")
    String path;

    @Override
    public Integer call() throws Exception {
        // Parse the project using shared pipeline
        AnalysisResult result = AnalysisPipeline.run(Paths.get(path));

        // Serialize graph data
        JsonSerializer serializer = new JsonSerializer();
        String graphJson = serializer.toJson(result.graph(), result.domains(),
            result.cycles(), result.hotspots(), result.blindSpots());

        // Handle --export flag (static HTML)
        if (exportFile != null) {
            exportStaticHtml(graphJson, exportFile);
            System.out.println("archon: exported to " + exportFile);
            return 0;
        }

        // Print terminal output for text mode
        if ("text".equals(format)) {
            PrintWriter writer = new PrintWriter(System.out, true);
            TerminalRenderer renderer = new TerminalRenderer(result, writer);
            renderer.render();
        } else if ("json".equals(format)) {
            System.out.println(graphJson);
            // JSON format: don't start server, just exit
            return 0;
        }

        // Start web server (only for text mode or when no format specified)
        ViewServer server = createServer();
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

    private ViewServer createServer() throws Exception {
        if (port != null && idleTimeoutMinutes != null) {
            return new ViewServer(port, idleTimeoutMinutes);
        } else if (port != null) {
            return new ViewServer(port);
        } else if (idleTimeoutMinutes != null) {
            return new ViewServer(0, idleTimeoutMinutes);
        } else {
            return new ViewServer();
        }
    }

    private void exportStaticHtml(String graphJson, String outputFile) throws Exception {
        // Load the viewer HTML template
        String html = loadResource("archon-viewer.html");

        // Inject the graph data as inline JSON
        String dataScript = "<script>window.GRAPH_DATA = " + graphJson + ";</script>";

        // Replace local lib/dagre.min.js with CDN version for static export
        // Using cdnjs.cloudflare.com for dagre
        String cdnScript = "<script src=\"https://cdn.jsdelivr.net/npm/dagre@0.8.5/dist/dagre.min.js\"></script>";
        html = html.replace("<script src=\"/lib/dagre.min.js\"></script>", cdnScript);

        // Inject data before closing head
        html = html.replace("</head>", dataScript + "</head>");

        // Replace API calls with inline data
        html = html.replace("fetch('/api/graph')", "Promise.resolve(window.GRAPH_DATA)");

        // Add a note about CDN requirement
        String warning = "<!-- Note: Static export requires internet connection for dagre.js CDN. Use --view mode for offline viewing. -->";
        html = html.replace("<head>", warning + "\n<head>");

        // Write to file
        try {
            Files.writeString(Paths.get(outputFile), html);
        } catch (Exception e) {
            System.err.println("Error: Failed to write export file: " + e.getMessage());
            throw e;
        }
    }

    private String loadResource(String name) throws Exception {
        try (var is = getClass().getClassLoader().getResourceAsStream(name)) {
            if (is == null) {
                throw new Exception("Resource not found: " + name);
            }
            // Read with UTF-8 encoding to preserve characters
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
