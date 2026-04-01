package com.archon.java;

import com.archon.core.graph.BlindSpot;
import com.archon.core.graph.DependencyGraph;
import com.archon.core.config.ArchonConfig;
import java.nio.file.Path;
import java.util.List;

/**
 * Parses Java source trees and builds a dependency graph.
 * Orchestrates: AstVisitor → SymbolSolverAdapter → BlindSpotDetector
 */
public class JavaParserPlugin {
    public ParseResult parse(Path projectRoot, ArchonConfig config) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public static class ParseResult {
        private final DependencyGraph graph;
        private final List<BlindSpot> blindSpots;
        private final List<ParseError> errors;

        public ParseResult(DependencyGraph graph, List<BlindSpot> blindSpots, List<ParseError> errors) {
            this.graph = graph;
            this.blindSpots = blindSpots;
            this.errors = errors;
        }

        public DependencyGraph getGraph() { return graph; }
        public List<BlindSpot> getBlindSpots() { return blindSpots; }
        public List<ParseError> getErrors() { return errors; }
    }

    public static class ParseError {
        private final String file;
        private final int line;
        private final String message;

        public ParseError(String file, int line, String message) {
            this.file = file;
            this.line = line;
            this.message = message;
        }

        public String getFile() { return file; }
        public int getLine() { return line; }
        public String getMessage() { return message; }
    }
}
