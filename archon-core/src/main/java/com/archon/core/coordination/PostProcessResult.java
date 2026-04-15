package com.archon.core.coordination;

import com.archon.core.plugin.BlindSpot;
import com.archon.core.plugin.DependencyDeclaration;
import java.util.List;

/**
 * Result from a plugin's postProcess() call.
 * Contains additional dependency declarations and blind spots discovered
 * during post-processing (e.g., Spring DI bytecode scanning).
 *
 * @param declarations additional dependency declarations
 * @param blindSpots   blind spots discovered during post-processing
 */
public record PostProcessResult(
    List<DependencyDeclaration> declarations,
    List<BlindSpot> blindSpots
) {
    public PostProcessResult {
        declarations = List.copyOf(declarations);
        blindSpots = List.copyOf(blindSpots);
    }

    /** Empty result with no declarations or blind spots. */
    public static PostProcessResult empty() {
        return new PostProcessResult(List.of(), List.of());
    }
}