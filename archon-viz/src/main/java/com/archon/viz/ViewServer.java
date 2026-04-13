package com.archon.viz;

import com.sun.net.httpserver.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ViewServer {
    private static final int DEFAULT_PORT = 8420;
    private static final int MAX_PORT = 8430;
    private static final int DEFAULT_IDLE_TIMEOUT_MINUTES = 30;
    private HttpServer server;
    private final int port;
    private final int idleTimeoutMinutes;
    private int actualPort;
    private long lastRequestTime;
    private ScheduledExecutorService idleChecker;

    public ViewServer() throws IOException {
        this(0, DEFAULT_IDLE_TIMEOUT_MINUTES);
    }

    public ViewServer(int port) throws IOException {
        this(port, DEFAULT_IDLE_TIMEOUT_MINUTES);
    }

    public ViewServer(int port, int idleTimeoutMinutes) throws IOException {
        this.port = port;
        this.idleTimeoutMinutes = idleTimeoutMinutes;
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
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        }));

        // Initialize last request time
        lastRequestTime = System.currentTimeMillis();

        // Start idle timeout checker
        startIdleChecker();

        // Add shutdown hook for clean shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown();
        }, "ViewServer-shutdown"));

        server.start();
    }

    public void stop() {
        shutdown();
    }

    private void shutdown() {
        if (idleChecker != null) {
            idleChecker.shutdownNow();
            idleChecker = null;
        }
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private void startIdleChecker() {
        idleChecker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        idleChecker.scheduleAtFixedRate(() -> {
            long idleTime = System.currentTimeMillis() - lastRequestTime;
            long idleTimeoutMs = idleTimeoutMinutes * 60_000L;
            if (idleTime > idleTimeoutMs) {
                System.out.println("\narchon: server idle for " + idleTimeoutMinutes + " minutes, shutting down");
                shutdown();
                System.exit(0);
            }
        }, 1, 1, TimeUnit.MINUTES);
    }

    private void recordRequest() {
        lastRequestTime = System.currentTimeMillis();
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
        recordRequest();
        String html = loadResource("archon-viewer.html");
        sendResponse(exchange, 200, "text/html", html);
    }

    private void serveGraph(HttpExchange exchange) throws IOException {
        recordRequest();
        String query = exchange.getRequestURI().getQuery();
        String response;

        if (query != null && query.startsWith("focus=")) {
            String focusId = query.substring(7); // after "focus="
            response = getFocusSubgraph(focusId);
        } else {
            // Return diffData if available (contains graph data + diff annotations), otherwise graphData
            response = diffData != null ? diffData : (graphData != null ? graphData : "{}");
        }

        sendResponse(exchange, 200, "application/json", response);
    }

    private String getFocusSubgraph(String focusId) {
        // For now, return filtered view - full implementation would use PerspectiveBuilder
        return "{\"focus\":\"" + focusId + "\",\"nodes\":[],\"edges\":[]}";
    }

    private void serveNode(HttpExchange exchange) throws IOException {
        recordRequest();
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
        recordRequest();
        sendResponse(exchange, 200, "application/json", diffData != null ? diffData : "{}");
    }

    private void serveLib(HttpExchange exchange) throws IOException {
        recordRequest();
        String path = exchange.getRequestURI().getPath();
        String libName = path.substring("/lib/".length());
        String js = loadResource("lib/" + libName);
        sendResponse(exchange, 200, "application/javascript", js);
    }

    private void serveStats(HttpExchange exchange) throws IOException {
        recordRequest();
        String stats = "{\"port\":" + actualPort + ",\"status\":\"running\"}";
        sendResponse(exchange, 200, "application/json", stats);
    }

    private void sendResponse(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bodyBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bodyBytes);
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
                Desktop.getDesktop().browse(new URI("http://127.0.0.1:" + actualPort + "/"));
            }
        } catch (Exception e) {
            System.out.println("archon: cannot open browser. View at http://127.0.0.1:" + actualPort);
        }
    }
}
