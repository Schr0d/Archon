package com.archon.core.analysis;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Classifies FQCNs into architectural layers by class name suffix.
 * Default patterns match standard Spring/Java naming conventions.
 * Custom patterns can be loaded from .archon.yml layer_patterns config.
 */
public class LayerClassifier {

    private final Map<String, ArchLayer> customPatterns;

    public LayerClassifier() {
        this.customPatterns = Map.of();
    }

    public LayerClassifier(Map<String, ArchLayer> customPatterns) {
        this.customPatterns = customPatterns != null ? customPatterns : Map.of();
    }

    /**
     * Classify a class by its FQCN.
     * Checks custom patterns first, then default suffix patterns.
     */
    public ArchLayer classify(String fqcn) {
        if (fqcn == null || fqcn.isEmpty()) {
            return ArchLayer.UNKNOWN;
        }

        // Extract simple class name (last segment)
        int lastDot = fqcn.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;

        // Check for inner class — use the outer class name for classification
        int dollarIndex = simpleName.indexOf('$');
        if (dollarIndex > 0) {
            simpleName = simpleName.substring(0, dollarIndex);
        }

        // Check custom patterns first (higher priority)
        for (Map.Entry<String, ArchLayer> entry : customPatterns.entrySet()) {
            if (simpleName.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Check default patterns
        for (ArchLayer layer : ArchLayer.values()) {
            if (layer == ArchLayer.UNKNOWN) continue;
            for (String suffix : layer.getSuffixes()) {
                if (simpleName.endsWith(suffix)) {
                    return layer;
                }
            }
        }

        return ArchLayer.UNKNOWN;
    }
}
