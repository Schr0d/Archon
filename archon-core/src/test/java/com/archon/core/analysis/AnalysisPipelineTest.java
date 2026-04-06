package com.archon.core.analysis;

import com.archon.core.graph.DependencyGraph;
import com.archon.core.graph.Node;
import com.archon.core.plugin.BlindSpot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnalysisPipelineTest {
    @Test
    void testRunReturnsAnalysisResultWithAllFields() {
        Path testRoot = Path.of("src/test/resources/test-project");
        AnalysisResult result = AnalysisPipeline.run(testRoot);

        assertNotNull(result.graph(), "graph should not be null");
        assertNotNull(result.domains(), "domains should not be null");
        assertNotNull(result.cycles(), "cycles should not be null");
        assertNotNull(result.hotspots(), "hotspots should not be null");
        assertNotNull(result.blindSpots(), "blindSpots should not be null");
        assertNotNull(result.thresholds(), "thresholds should not be null");
    }
}
