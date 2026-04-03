package com.archon.js;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts JavaScript/TypeScript code from Vue Single File Components (.vue).
 *
 * <p>Vue SFC format:
 * <pre>
 * &lt;template&gt;...&lt;/template&gt;
 * &lt;script setup lang="ts"&gt;...&lt;/script&gt;
 * &lt;style&gt;...&lt;/style&gt;
 * </pre>
 *
 * <p>Handles:
 * <ul>
 *   <li>Regular &lt;script&gt; tags</li>
 *   <li>&lt;script setup&gt; syntax</li>
 *   <li>lang attribute (ts, js)</li>
 *   <li>Multiple script blocks (uses first non-empty one)</li>
 * </ul>
 */
public class VueFileExtractor {

    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
        "<script\\b[^>]*>([\\s\\S]*?)</script>",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SETUP_PATTERN = Pattern.compile(
        "<script\\s+setup\\b[^>]*>([\\s\\S]*?)</script>",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Result of Vue file extraction.
     */
    public record ExtractionResult(
        String scriptContent,
        String language,  // "typescript" or "javascript"
        boolean isSetup
    ) {}

    /**
     * Extracts script content from a Vue Single File Component.
     *
     * @param vueContent The full .vue file content
     * @return ExtractionResult with script content, or null if no script found
     */
    public ExtractionResult extractScript(String vueContent) {
        if (vueContent == null || vueContent.isBlank()) {
            return null;
        }

        // First try to find <script setup>
        Matcher setupMatcher = SETUP_PATTERN.matcher(vueContent);
        if (setupMatcher.find()) {
            String content = setupMatcher.group(1).trim();
            if (!content.isEmpty()) {
                return new ExtractionResult(
                    content,
                    detectLanguage(vueContent, setupMatcher.group(0)),
                    true
                );
            }
        }

        // Fall back to regular <script>
        Matcher scriptMatcher = SCRIPT_PATTERN.matcher(vueContent);
        while (scriptMatcher.find()) {
            String content = scriptMatcher.group(1).trim();
            if (!content.isEmpty()) {
                return new ExtractionResult(
                    content,
                    detectLanguage(vueContent, scriptMatcher.group(0)),
                    false
                );
            }
        }

        // No script block found
        return null;
    }

    /**
     * Detects the script language from the lang attribute.
     * Defaults to "javascript".
     */
    private String detectLanguage(String fullContent, String scriptTag) {
        // Check for lang attribute in the script tag
        Pattern langPattern = Pattern.compile("lang\\s*=\\s*[\"']([^\"']+)[\"']");
        Matcher langMatcher = langPattern.matcher(scriptTag);
        if (langMatcher.find()) {
            String lang = langMatcher.group(1).toLowerCase();
            if (lang.equals("ts") || lang.startsWith("type")) {
                return "typescript";
            }
        }
        return "javascript";
    }
}
