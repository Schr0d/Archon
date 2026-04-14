package com.archon.core.coordination;

import com.archon.core.plugin.*;

import java.util.List;
import java.util.Set;

/**
 * Simple declaration-based test plugin.
 * Returns preconfigured ModuleDeclaration and DependencyDeclaration records.
 */
class DeclarationPlugin implements LanguagePlugin {
    private final String extension;
    private final List<ModuleDeclaration> moduleDeclarations;
    private final List<DependencyDeclaration> dependencyDeclarations;
    private final Set<String> sourceModules;

    DeclarationPlugin(
        String extension,
        List<ModuleDeclaration> moduleDeclarations,
        List<DependencyDeclaration> dependencyDeclarations,
        Set<String> sourceModules
    ) {
        this.extension = extension;
        this.moduleDeclarations = moduleDeclarations;
        this.dependencyDeclarations = dependencyDeclarations;
        this.sourceModules = sourceModules;
    }

    @Override
    public Set<String> fileExtensions() {
        return Set.of(extension);
    }

    @Override
    public ParseResult parseFromContent(String filePath, String content, ParseContext context) {
        return new ParseResult(
            sourceModules, List.of(), List.of(),
            moduleDeclarations, dependencyDeclarations
        );
    }
}
