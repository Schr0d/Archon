package com.archon.js;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;

/**
 * BLOCKING VALIDATION - Must pass before building JsPlugin.
 *
 * Validates that Google Closure Compiler can correctly parse TypeScript type-only imports:
 * - import type { X } from './foo'
 * - import { type Y } from './bar'
 *
 * If Closure Compiler loses this information in the AST, we cannot use it and must
 * switch to TypeScript Compiler API via GraalJS.
 *
 * This test parses actual TypeScript code with type-only imports and verifies the AST
 * distinguishes them from regular imports.
 */
@DisplayName("Closure Compiler Type-Only Import Validation")
class ClosureCompilerValidationTest {

    /**
     * Test that Closure Compiler preserves type-only import syntax.
     *
     * TypeScript has two syntaxes for type-only imports:
     * 1. import type { X } from './foo' - entire import is type-only
     * 2. import { type Y, Z } from './bar' - mixed import with type qualifier
     *
     * Both must be distinguished from runtime imports like:
     * 3. import { A } from './baz' - runtime import
     */
    @Test
    @DisplayName("Should distinguish type-only imports from regular imports")
    void shouldDistinguishTypeOnlyImports() {
        // TypeScript source with all three import types
        String tsCode = """
            // Type-only import (entire statement)
            import type { InterfaceX } from './foo';

            // Mixed import with type qualifier
            import { type TypeY, RuntimeZ } from './bar';

            // Regular runtime import
            import { RuntimeA, RuntimeB } from './baz';

            // Use the runtime imports to verify they're not treated as types
            export const value = null;
            """;

        Compiler compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
        options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
        options.setTypesNumericLiterals(true);

        SourceFile source = SourceFile.fromCode("test.ts", tsCode);

        // Compile to TypeScript ( Closure Compiler transpiles TS to JS)
        compiler.compile(List.of(source), List.of(), options);

        Node root = compiler.getRoot();

        // Collect all import nodes
        List<ImportInfo> imports = extractImports(root);

        // Debug: Print all imports found
        System.out.println("=== Found " + imports.size() + " imports ===");
        for (ImportInfo imp : imports) {
            System.out.println("Module: " + imp.moduleName +
                ", Token: " + imp.tokenType +
                ", IsTypeOnly: " + imp.isTypeOnly +
                ", GoogRequireType: " + imp.isGoogRequireType);
        }

        // Verify we found 3 imports
        Assertions.assertEquals(3, imports.size(),
            "Should find exactly 3 import statements, but found: " + imports.size());

        // CRITICAL VALIDATION: Check if Closure Compiler distinguishes type-only imports
        ImportInfo typeOnlyImport = imports.stream()
            .filter(i -> i.moduleName.equals("./foo"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Type-only import from './foo' not found"));

        // THIS IS THE BLOCKING VALIDATION
        // If Closure Compiler cannot distinguish type-only imports, we must use a different approach
        boolean canDistinguishTypeImports = typeOnlyImport.isTypeOnly ||
                                           typeOnlyImport.isGoogRequireType ||
                                           !typeOnlyImport.tokenType.equals(IMPORT);

        if (!canDistinguishTypeImports) {
            Assertions.fail("""
                CRITICAL FAILURE: Closure Compiler does not distinguish type-only imports!

                The AST representation of:
                  import type { InterfaceX } from './foo';

                cannot be differentiated from:
                  import { RuntimeA } from './baz';

                This means we CANNOT use Closure Compiler for TypeScript dependency analysis.
                The tool would create false dependencies from type-only imports.

                RECOMMENDED ACTION:
                Switch to TypeScript Compiler API via GraalJS, or use a regex pre-scan
                to filter type-only imports before Closure Compiler parsing.

                Validation Result: FAIL - Type-only import information is lost
                """);
        }
    }

    /**
     * Test inline type-only import syntax.
     *
     * TypeScript 3.8+ allows inline type qualifiers:
     * import { type X, type Y } from './foo'
     */
    @Test
    @DisplayName("Should handle inline type qualifiers")
    void shouldHandleInlineTypeQualifiers() {
        String tsCode = """
            import { type InterfaceX, type InterfaceY, RuntimeZ } from './foo';

            export const value = null;
            """;

        Compiler compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
        options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);

        SourceFile source = SourceFile.fromCode("test.ts", tsCode);
        compiler.compile(List.of(source), List.of(), options);

        Node root = compiler.getRoot();

        List<ImportInfo> imports = extractImports(root);

        System.out.println("=== Inline type qualifier test: " + imports.size() + " imports ===");
        for (ImportInfo imp : imports) {
            System.out.println("Module: " + imp.moduleName +
                ", Token: " + imp.tokenType);
        }

        Assertions.assertEquals(1, imports.size(),
            "Should find exactly 1 import statement");

        // For inline type qualifiers, Closure Compiler should either:
        // 1. Mark the entire import differently, OR
        // 2. Provide binding-level type information
        // 3. Split into separate import nodes
    }

    /**
     * Test that type-only imports are not confused with type assertions.
     */
    @Test
    @DisplayName("Should not confuse type-only imports with type assertions")
    void shouldNotConfuseTypeImportsWithAssertions() {
        String tsCode = """
            import type { TypeX } from './types';
            import { valueY } from './values';

            // Type assertion (not a type-only import)
            export const z = valueY as TypeX;
            """;

        Compiler compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();
        options.setLanguageIn(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);
        options.setLanguageOut(CompilerOptions.LanguageMode.ECMASCRIPT_NEXT);

        SourceFile source = SourceFile.fromCode("test.ts", tsCode);
        compiler.compile(List.of(source), List.of(), options);

        Node root = compiler.getRoot();

        List<ImportInfo> imports = extractImports(root);

        System.out.println("=== Type assertion test: " + imports.size() + " imports ===");
        for (ImportInfo imp : imports) {
            System.out.println("Module: " + imp.moduleName +
                ", Token: " + imp.tokenType);
        }

        Assertions.assertEquals(2, imports.size(),
            "Should find exactly 2 import statements");
    }

    /**
     * Extract import information from Closure Compiler AST.
     *
     * This method walks the AST and collects information about import statements.
     */
    private List<ImportInfo> extractImports(Node root) {
        List<ImportInfo> imports = new ArrayList<>();
        walkAST(root, imports, 0);
        return imports;
    }

    /**
     * Recursively walk the Closure Compiler AST to extract import information.
     */
    private void walkAST(Node node, List<ImportInfo> imports, int depth) {
        if (node == null || depth > 100) {  // Prevent infinite loops
            return;
        }

        Token token = node.getToken();

        // Check if this is an import node
        // Closure Compiler uses different token types for different import forms
        if (token == Token.IMPORT || token == Token.IMPORT_FROM ||
            token == Token.IMPORT_SPEC || token == Token.GETPROP ||
            isGoogRequireType(node)) {

            String moduleName = extractModuleName(node);
            if (moduleName != null && !moduleName.isEmpty()) {
                ImportInfo info = new ImportInfo(
                    moduleName,
                    token.toString(),
                    token == Token.IMPORT || token == Token.IMPORT_FROM,
                    isGoogRequireType(node)
                );
                imports.add(info);
            }
        }

        // Recursively process children
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            walkAST(child, imports, depth + 1);
        }
    }

    /**
     * Check if this is a goog.requireType() call (Closure Compiler's internal type-only import).
     */
    private boolean isGoogRequireType(Node node) {
        if (node == null) {
            return false;
        }

        // Check if it's a call to goog.requireType
        Token token = node.getToken();
        if (token == Token.CALL) {
            Node target = node.getFirstChild();
            if (target != null && target.isGetProp()) {
                String propName = target.getQualifiedName();
                if ("goog.requireType".equals(propName)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Extract the module name from an import node.
     */
    private String extractModuleName(Node node) {
        // Try different strategies based on node type
        Token token = node.getToken();

        // For IMPORT nodes, module name is typically in a string child
        if (token == Token.IMPORT || token == Token.IMPORT_FROM) {
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                if (child.isString()) {
                    return child.getString();
                }
            }
        }

        // For goog.requireType('module'), module name is the first string argument
        if (node.getToken() == Token.CALL) {
            Node target = node.getFirstChild();
            if (target != null && target.isGetProp()) {
                String propName = target.getQualifiedName();
                if ("goog.requireType".equals(propName)) {
                    // Get the argument
                    Node arg = target.getNext();
                    if (arg != null && arg.isString()) {
                        return arg.getString();
                    }
                }
            }
        }

        return null;
    }

    private static final String IMPORT = "IMPORT";

    /**
     * Information about an import statement extracted from the AST.
     */
    private record ImportInfo(
        String moduleName,
        String tokenType,
        boolean isImportNode,
        boolean isGoogRequireType
    ) {}
}
