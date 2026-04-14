package com.archon.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffCommandTest {

    private DiffCommand command;

    @BeforeEach
    void setUp() {
        command = new DiffCommand();
    }

    @Nested
    @DisplayName("View Flag")
    class ViewFlag {

        @Test
        @DisplayName("view flag defaults to false")
        void viewFlag_defaultsToFalse() {
            assertFalse(command.view);
        }

        @Test
        @DisplayName("view flag can be set to true")
        void viewFlag_canBeSetToTrue() {
            command.view = true;
            assertTrue(command.view);
        }

        @Test
        @DisplayName("view flag enables web viewer mode with params list")
        void viewFlag_enablesWebViewerMode() {
            command.view = true;
            command.params = List.of("HEAD~1", "HEAD", ".");

            assertTrue(command.view, "view flag should be true");
            assertEquals(3, command.params.size());
            assertEquals("HEAD~1", command.params.get(0));
            assertEquals("HEAD", command.params.get(1));
            assertEquals(".", command.params.get(2));
        }
    }

    @Nested
    @DisplayName("Parameters")
    class Parameters {

        @Test
        @DisplayName("params list can be set with base ref only")
        void params_baseRefOnly() {
            command.params = List.of("main");
            assertEquals(List.of("main"), command.params);
        }

        @Test
        @DisplayName("params list can be set with base and head ref")
        void params_baseAndHeadRef() {
            command.params = List.of("main", "develop");
            assertEquals(List.of("main", "develop"), command.params);
        }

        @Test
        @DisplayName("params list can be set with all three args")
        void params_allThree() {
            command.params = List.of("main", "develop", "/path/to/project");
            assertEquals(List.of("main", "develop", "/path/to/project"), command.params);
        }

        @Test
        @DisplayName("params list defaults to null (zero-arg mode)")
        void params_defaultsToNull() {
            assertNull(command.params);
        }
    }
}
