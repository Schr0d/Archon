package com.archon.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
        @DisplayName("view flag enables web viewer mode")
        void viewFlag_enablesWebViewerMode() {
            command.view = true;
            command.baseRef = "HEAD~1";
            command.headRef = "HEAD";
            command.projectPath = ".";

            assertTrue(command.view, "view flag should be true");
            assertEquals("HEAD~1", command.baseRef);
            assertEquals("HEAD", command.headRef);
            assertEquals(".", command.projectPath);
        }
    }

    @Nested
    @DisplayName("Parameters")
    class Parameters {

        @Test
        @DisplayName("baseRef can be set")
        void baseRef_canBeSet() {
            command.baseRef = "main";
            assertEquals("main", command.baseRef);
        }

        @Test
        @DisplayName("headRef can be set")
        void headRef_canBeSet() {
            command.headRef = "develop";
            assertEquals("develop", command.headRef);
        }

        @Test
        @DisplayName("projectPath can be set")
        void projectPath_canBeSet() {
            command.projectPath = "/path/to/project";
            assertEquals("/path/to/project", command.projectPath);
        }
    }
}
