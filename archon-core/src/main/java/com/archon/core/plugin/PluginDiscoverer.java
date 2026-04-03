package com.archon.core.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Discovers LanguagePlugin implementations via ServiceLoader.
 *
 * <p>Plugins are registered via META-INF/services/com.archon.core.plugin.LanguagePlugin.
 * Each JAR on the classpath can provide its own plugin implementation.
 *
 * <p>Usage:
 * <pre>
 * PluginDiscoverer discoverer = new PluginDiscoverer();
 * List&lt;LanguagePlugin&gt; plugins = discoverer.discoverWithConflictCheck();
 * </pre>
 *
 * @see LanguagePlugin
 */
public class PluginDiscoverer {

    /**
     * Discover all LanguagePlugin implementations on the classpath.
     * Uses ServiceLoader to find META-INF/services registrations.
     *
     * @return list of discovered plugins (empty if none found)
     */
    public List<LanguagePlugin> discover() {
        List<LanguagePlugin> plugins = new ArrayList<>();

        ServiceLoader<LanguagePlugin> loader = ServiceLoader.load(LanguagePlugin.class);
        for (LanguagePlugin plugin : loader) {
            plugins.add(plugin);
        }

        return plugins;
    }

    /**
     * Discover plugins and detect extension conflicts.
     *
     * <p>This method checks that no two plugins claim the same file extension.
     * If a conflict is detected, an IllegalStateException is thrown with details
     * about which plugins are conflicting.
     *
     * @return list of discovered plugins
     * @throws IllegalStateException if two plugins claim the same extension
     */
    public List<LanguagePlugin> discoverWithConflictCheck() {
        List<LanguagePlugin> plugins = discover();

        // Check for extension conflicts
        Map<String, LanguagePlugin> extensionMap = new HashMap<>();
        for (LanguagePlugin plugin : plugins) {
            for (String extension : plugin.fileExtensions()) {
                if (extensionMap.containsKey(extension)) {
                    throw new IllegalStateException(
                        "Extension conflict: '" + extension + "' is claimed by both " +
                        plugin.getClass().getName() + " and " +
                        extensionMap.get(extension).getClass().getName()
                    );
                }
                extensionMap.put(extension, plugin);
            }
        }

        return plugins;
    }
}
