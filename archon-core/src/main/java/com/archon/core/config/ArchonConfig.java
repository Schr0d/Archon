package com.archon.core.config;

import java.util.List;
import java.util.Map;

/**
 * Parses .archon.yml and merges with built-in defaults.
 */
public class ArchonConfig {
    private boolean noCycle = true;
    private int maxCrossDomain = 2;
    private int maxCallDepth = 3;
    private List<String> forbidCoreEntityLeakage = List.of();
    private Map<String, List<String>> domains = Map.of();
    private List<String> criticalPaths = List.of();
    private List<String> ignore = List.of();

    public static ArchonConfig defaults() { return new ArchonConfig(); }

    public boolean isNoCycle() { return noCycle; }
    public int getMaxCrossDomain() { return maxCrossDomain; }
    public int getMaxCallDepth() { return maxCallDepth; }
    public List<String> getForbidCoreEntityLeakage() { return forbidCoreEntityLeakage; }
    public Map<String, List<String>> getDomains() { return domains; }
    public List<String> getCriticalPaths() { return criticalPaths; }
    public List<String> getIgnore() { return ignore; }
}
