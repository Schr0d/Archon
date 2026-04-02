package com.archon.core.analysis;

/**
 * Computes adaptive thresholds based on project size.
 * Larger projects get more relaxed thresholds to avoid flooding the user with warnings.
 */
public class ThresholdCalculator {

    public static Thresholds calculate(int nodeCount, int domainCount) {
        int couplingThreshold = Math.max(3, nodeCount / 100);
        int crossDomainMax = Math.max(2, (int) Math.ceil(domainCount * 0.3));
        int maxCallDepth = 4;
        int hotspotDisplayCap = Math.min(20, Math.max(5, nodeCount / 10));

        return new Thresholds(couplingThreshold, crossDomainMax, maxCallDepth, hotspotDisplayCap);
    }
}
