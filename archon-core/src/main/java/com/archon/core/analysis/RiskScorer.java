package com.archon.core.analysis;

import com.archon.core.graph.RiskLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Threshold-based risk scoring with max aggregation.
 * Escalates by one level if lowConfidence is true.
 */
public class RiskScorer {

    public RiskLevel computeRisk(int inDegree, int crossDomainCount, int callDepth,
                                boolean onCriticalPath, boolean inCycle, boolean lowConfidence) {
        List<RiskLevel> levels = new ArrayList<>();

        // Coupling dimension
        if (inDegree > 10) {
            levels.add(RiskLevel.HIGH);
        } else if (inDegree >= 5) {
            levels.add(RiskLevel.MEDIUM);
        } else {
            levels.add(RiskLevel.LOW);
        }

        // Cross-domain dimension
        if (crossDomainCount >= 3) {
            levels.add(RiskLevel.HIGH);
        }

        // Call depth dimension
        if (callDepth >= 3) {
            levels.add(RiskLevel.HIGH);
        }

        // Critical path dimension
        if (onCriticalPath) {
            levels.add(RiskLevel.HIGH);
        }

        // Cycle dimension
        if (inCycle) {
            levels.add(RiskLevel.VERY_HIGH);
        }

        // Max aggregation
        RiskLevel result = levels.stream()
            .max(Enum::compareTo)
            .orElse(RiskLevel.LOW);

        // Confidence escalation
        if (lowConfidence) {
            result = escalate(result);
        }

        return result;
    }

    private RiskLevel escalate(RiskLevel level) {
        return switch (level) {
            case LOW -> RiskLevel.LOW;
            case MEDIUM -> RiskLevel.HIGH;
            case HIGH -> RiskLevel.VERY_HIGH;
            case VERY_HIGH -> RiskLevel.BLOCKED;
            case BLOCKED -> RiskLevel.BLOCKED;
        };
    }
}
