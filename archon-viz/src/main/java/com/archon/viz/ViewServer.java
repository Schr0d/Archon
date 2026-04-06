package com.archon.viz;

import com.sun.net.httpserver.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class ViewServer {
    private static final int DEFAULT_PORT = 8420;
    private static final int MAX_PORT = 8430;
    private HttpServer server;
    private final int port;
    private int actualPort;

    public ViewServer() throws IOException {
        this.port = 0; // Will find available port in start()
    }

    public ViewServer(int port) throws IOException {
        this.port = port;
    }

    public void start() throws IOException {
        int startPort = port == 0 ? DEFAULT_PORT : port;
        actualPort = findAvailablePort(startPort);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", actualPort), 0);
        server.createContext("/", this::serveIndex);
        server.createContext("/api/graph", this::serveGraph);
        server.createContext("/api/node/", this::serveNode);
        server.createContext("/api/diff", this::serveDiff);
        server.createContext("/api/stats", this::serveStats);
        server.createContext("/lib/", this::serveLib);
        server.setExecutor(null); // creates a default executor

        // Add shutdown hook for clean shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (server != null) {
                server.stop(0);
            }
        }, "ViewServer-shutdown"));

        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    public int getPort() {
        return actualPort;
    }

    private HttpHandler graphHandler;
    private HttpHandler diffHandler;

    public void setGraphData(String jsonData) {
        this.graphData = jsonData;
    }

    public void setDiffData(String jsonData) {
        this.diffData = jsonData;
    }

    private String graphData;
    private String diffData;

    private int findAvailablePort(int startPort) throws IOException {
        int maxPort = port == 0 ? MAX_PORT : startPort;
        for (int p = startPort; p <= maxPort; p++) {
            try (ServerSocket socket = new ServerSocket(p, 50, InetAddress.getByName("127.0.0.1"))) {
                // If we can bind, the port is available
                return p;
            } catch (IOException e) {
                // Port in use, try next
            }
        }
        throw new IOException("All ports " + startPort + "-" + maxPort + " in use");
    }

    private void serveIndex(HttpExchange exchange) throws IOException {
        String html = loadResource("archon-viewer.html");
        sendResponse(exchange, 200, "text/html", html);
    }

    private void serveGraph(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String response;

        if (query != null && query.startsWith("focus=")) {
            String focusId = query.substring(7); // after "focus="
            response = getFocusSubgraph(focusId);
        } else {
            response = graphData != null ? graphData : "{}";
        }

        sendResponse(exchange, 200, "application/json", response);
    }

    private String getFocusSubgraph(String focusId) {
        // For now, return filtered view - full implementation would use PerspectiveBuilder
        return "{\"focus\":\"" + focusId + "\",\"nodes\":[],\"edges\":[]}";
    }

    private void serveNode(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String nodeId = path.substring("/api/node/".length());

        // Parse graphData to find node - for now return enhanced stub
        String nodeDetails = "{"
            + "\"id\":\"" + nodeId + "\","
            + "\"type\":\"CLASS\","
            + "\"domain\":\"unknown\","
            + "\"dependencies\":[],"
            + "\"dependents\":[]"
            + "}";

        sendResponse(exchange, 200, "application/json", nodeDetails);
    }

    private void serveDiff(HttpExchange exchange) throws IOException {
        sendResponse(exchange, 200, "application/json", diffData != null ? diffData : "{}");
    }

    private void serveLib(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String libName = path.substring("/lib/".length());
        String js = loadResource("lib/" + libName);
        sendResponse(exchange, 200, "application/javascript", js);
    }

    private void serveStats(HttpExchange exchange) throws IOException {
        String stats = "{\"port\":" + port + ",\"status\":\"running\"}";
        sendResponse(exchange, 200, "application/json", stats);
    }

    private void sendResponse(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length());
        OutputStream os = exchange.getResponseBody();
        os.write(body.getBytes(StandardCharsets.UTF_8));
        os.close();
    }

    private String loadResource(String name) throws IOException {
        InputStream is = getClass().getClassLoader().getResourceAsStream(name);
        if (is == null) {
            throw new IOException("Resource not found: " + name);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }
    }

    public void openBrowser() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI("http://127.0.0.1:" + port + "/"));
            }
        } catch (Exception e) {
            System.out.println("archon: cannot open browser. View at http://127.0.0.1:" + port);
        }
    }
}
