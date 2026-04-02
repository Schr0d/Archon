package com.archon.core.analysis;

/**
 * Architectural layer classification for Java classes.
 * Determined by class name suffix patterns.
 */
public enum ArchLayer {
    CONTROLLER("Controller", "Resource", "Endpoint"),
    SERVICE("Service", "ServiceImpl", "Manager"),
    REPOSITORY("Mapper", "Repository", "Dao"),
    DOMAIN("Entity", "Domain", "Model", "VO", "DTO"),
    CONFIG("Config", "Configuration", "Properties"),
    UTIL("Util", "Utils", "Helper", "Constants"),
    UNKNOWN();

    private final String[] suffixes;

    ArchLayer(String... suffixes) {
        this.suffixes = suffixes;
    }

    public String[] getSuffixes() {
        return suffixes;
    }
}
