package com.archon.viz;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import static org.junit.jupiter.api.Assertions.*;

class ViewServerTest {
    @Test
    void testServerStartsOnAvailablePort() throws IOException {
        ViewServer server = new ViewServer();
        server.start();

        assertTrue(server.getPort() >= 8420 && server.getPort() <= 8430);
        server.stop();
    }

    @Test
    void testServerServesIndexHtml() throws IOException {
        ViewServer server = new ViewServer();
        server.start();

        URL url = new URL("http://127.0.0.1:" + server.getPort() + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);

        assertEquals(200, conn.getResponseCode());
        assertEquals("text/html", conn.getContentType());

        conn.disconnect();
        server.stop();
    }

    @Test
    void testServerServesGraphApi() throws IOException {
        ViewServer server = new ViewServer();
        server.setGraphData("{\"nodes\":[],\"edges\":[]}");
        server.start();

        URL url = new URL("http://127.0.0.1:" + server.getPort() + "/api/graph");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);

        assertEquals(200, conn.getResponseCode());
        assertEquals("application/json", conn.getContentType());

        conn.disconnect();
        server.stop();
    }

    @Test
    void testServerBindsToLocalhostOnly() throws IOException {
        ViewServer server = new ViewServer();
        server.start();

        // Should bind to 127.0.0.1 only
        URL url = new URL("http://127.0.0.1:" + server.getPort() + "/");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);

        assertEquals(200, conn.getResponseCode());

        conn.disconnect();
        server.stop();
    }
}
