package com.archon.viz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ViewCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void testViewCommandParsesProjectPath() {
        ViewCommand command = new ViewCommand();
        command.path = tempDir.toString();

        assertEquals(tempDir.toString(), command.path);
    }

    @Test
    void testViewCommandAcceptsPortOption() {
        ViewCommand command = new ViewCommand();
        command.port = 8500;

        assertEquals(8500, command.port);
    }

    @Test
    void testViewCommandAcceptsNoOpenFlag() {
        ViewCommand command = new ViewCommand();
        command.noOpen = true;

        assertTrue(command.noOpen);
    }

    @Test
    void testViewCommandDefaultsToTextFormat() {
        ViewCommand command = new ViewCommand();

        assertEquals("text", command.format);
    }

    @Test
    void testViewCommandAcceptsJsonFormat() {
        ViewCommand command = new ViewCommand();
        command.format = "json";

        assertEquals("json", command.format);
    }

    @Test
    void testViewCommandHasDefaultNullPort() {
        ViewCommand command = new ViewCommand();

        assertNull(command.port);
    }

    @Test
    void testViewCommandDefaultsNoOpenToFalse() {
        ViewCommand command = new ViewCommand();

        assertFalse(command.noOpen);
    }
}
