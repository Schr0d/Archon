package com.archon.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    public static ArchonConfig defaults() {
        return new ArchonConfig();
    }

    /**
     * Load config from file. Throws on missing or invalid file.
     */
    public static ArchonConfig load(Path configPath) {
        try {
            String yaml = Files.readString(configPath);
            return parseYaml(yaml);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config file: " + configPath, e);
        }
    }

    /**
     * Load config from file, returning defaults if file is missing.
     * Throws on invalid YAML.
     */
    public static ArchonConfig loadOrDefault(Path configPath) {
        if (!Files.exists(configPath)) {
            return defaults();
        }
        return load(configPath);
    }

    @SuppressWarnings("unchecked")
    private static ArchonConfig parseYaml(String yaml) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        Map<String, Object> root;
        try {
            root = mapper.readValue(yaml, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Invalid YAML in config: " + e.getMessage(), e);
        }

        ArchonConfig config = new ArchonConfig();

        Map<String, Object> rules = (Map<String, Object>) root.get("rules");
        if (rules != null) {
            if (rules.containsKey("no_cycle")) {
                config.noCycle = (Boolean) rules.get("no_cycle");
            }
            if (rules.containsKey("max_cross_domain")) {
                config.maxCrossDomain = ((Number) rules.get("max_cross_domain")).intValue();
            }
            if (rules.containsKey("max_call_depth")) {
                config.maxCallDepth = ((Number) rules.get("max_call_depth")).intValue();
            }
            if (rules.containsKey("forbid_core_entity_leakage")) {
                config.forbidCoreEntityLeakage = new ArrayList<>((List<String>) rules.get("forbid_core_entity_leakage"));
            }
        }

        Map<String, List<String>> domains = (Map<String, List<String>>) root.get("domains");
        if (domains != null) {
            config.domains = new LinkedHashMap<>(domains);
        }

        List<String> criticalPaths = (List<String>) root.get("critical_paths");
        if (criticalPaths != null) {
            config.criticalPaths = new ArrayList<>(criticalPaths);
        }

        List<String> ignore = (List<String>) root.get("ignore");
        if (ignore != null) {
            config.ignore = new ArrayList<>(ignore);
        }

        return config;
    }

    public boolean isNoCycle() { return noCycle; }
    public int getMaxCrossDomain() { return maxCrossDomain; }
    public int getMaxCallDepth() { return maxCallDepth; }
    public List<String> getForbidCoreEntityLeakage() { return forbidCoreEntityLeakage; }
    public Map<String, List<String>> getDomains() { return domains; }
    public List<String> getCriticalPaths() { return criticalPaths; }
    public List<String> getIgnore() { return ignore; }
}
