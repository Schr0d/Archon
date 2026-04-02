package com.archon.core.analysis;

/**
 * Immutable threshold values for analysis engines.
 * Computed by ThresholdCalculator based on project size, or overridden via config.
 */
public class Thresholds {
    private final int couplingThreshold;
    private final int crossDomainMax;
    private final int maxCallDepth;
    private final int hotspotDisplayCap;

    public Thresholds(int couplingThreshold, int crossDomainMax,
                      int maxCallDepth, int hotspotDisplayCap) {
        this.couplingThreshold = couplingThreshold;
        this.crossDomainMax = crossDomainMax;
        this.maxCallDepth = maxCallDepth;
        this.hotspotDisplayCap = hotspotDisplayCap;
    }

    public int getCouplingThreshold() { return couplingThreshold; }
    public int getCrossDomainMax() { return crossDomainMax; }
    public int getMaxCallDepth() { return maxCallDepth; }
    public int getHotspotDisplayCap() { return hotspotDisplayCap; }
}
