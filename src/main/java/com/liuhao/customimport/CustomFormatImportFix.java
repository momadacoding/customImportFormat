package com.liuhao.customimport;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CustomFormatImportFix extends BaseIntentionAction {
    private static final Logger LOG = Logger.getInstance(CustomFormatImportFix.class);

    private final String fullQualifiedName;
    private final String aliasName; // This is the 'referenceName' or null for top-level

    public CustomFormatImportFix(@NotNull String fullQualifiedName, @Nullable String aliasName) {
        this.fullQualifiedName = fullQualifiedName;
        this.aliasName = aliasName;

        if (aliasName != null) {
            setText("Import as 'import " + fullQualifiedName + " as " + aliasName + "'");
        } else {
            setText("Import as 'import " + fullQualifiedName + "'");
        }
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "Custom Python Import";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return file instanceof PyFile && file.isValid() && file.isWritable();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!(file instanceof PyFile)) {
            return;
        }
        PyFile pyFile = (PyFile) file;
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        LanguageLevel languageLevel = LanguageLevel.forElement(pyFile);
        PyImportStatement customImportStatement = generator.createImportStatement(
                languageLevel, this.fullQualifiedName, this.aliasName);

        performDirectImportInsertion(project, pyFile, customImportStatement);
    }

    /**
     * Static method to perform the import insertion.
     * This will be called by CustomFormatImportCandidateProvider.
     */
    public static void performDirectImportInsertion(
            @NotNull Project project,
            @NotNull PyFile pyFile,
            @NotNull PyImportStatement customImportStatement) {

        ApplicationManager.getApplication().invokeLater(() -> {
            WriteCommandAction.runWriteCommandAction(project, "Add Custom Import", null, () -> {
                try {
                    doActuallyInsertStatement(pyFile, customImportStatement);
                } catch (Exception e) {
                    LOG.error("Error applying custom import fix directly", e);
                }
            });
        });
    }

    /**
     * Inserts an import statement into a Python file at an appropriate location.
     * Made static to be callable from performDirectImportInsertion helper.
     */
    private static void doActuallyInsertStatement(@NotNull PyFile file, @NotNull PyImportStatement importStatement) {
        // Find the best place to insert the import
        PyImportStatementBase lastExistingImport = null;
        List<PyImportStatementBase> existingImports = file.getImportBlock();
        if (existingImports != null && !existingImports.isEmpty()) {
            lastExistingImport = existingImports.get(existingImports.size() - 1);
        }

        PsiElement anchor = null;
        boolean insertAfter = false;

        if (lastExistingImport != null) {
            anchor = lastExistingImport;
            insertAfter = true;
            LOG.info("Found existing imports, planning to add after: " + anchor.getText());
        } else {
            // Add at the beginning of the file, but after docstrings and module-level comments/directives
            PsiElement firstChild = file.getFirstChild();
            PsiElement potentialAnchor = firstChild;

            while (potentialAnchor != null) {
                if (potentialAnchor instanceof PsiWhiteSpace || potentialAnchor instanceof PsiComment) {
                    potentialAnchor = potentialAnchor.getNextSibling();
                } else if (potentialAnchor instanceof PyExpressionStatement &&
                           ((PyExpressionStatement) potentialAnchor).getExpression() instanceof PyStringLiteralExpression) {
                    // This is likely a module docstring
                    potentialAnchor = potentialAnchor.getNextSibling();
                } else if (potentialAnchor instanceof PyFromImportStatement && "__future__".equals(((PyFromImportStatement)potentialAnchor).getImportSourceQName().toString())) {
                    // This is a __future__ import, should go after these
                     potentialAnchor = potentialAnchor.getNextSibling();
                }
                else {
                    break; // Found a non-docstring/comment/future import, insert before this
                }
            }
            anchor = potentialAnchor;
            insertAfter = false; // Insert before this element or at the start if anchor is null (empty file)
            if (anchor != null) {
                LOG.info("No existing regular imports. Planning to add before: " + anchor.getText());
            } else {
                LOG.info("No existing imports or other elements. Planning to add at the start of the file.");
            }
        }

        if (anchor == null) { // Empty file or only whitespace/comments
            file.add(importStatement);
            LOG.info("Added import at the beginning of the file (anchor was null).");
        } else if (insertAfter) {
            file.addAfter(importStatement, anchor);
            PsiElement newline = PsiParserFacade.getInstance(file.getProject()).createWhiteSpaceFromText("\n");
            file.addAfter(newline, anchor); // Add newline after previous import if adding after
            LOG.info("Added import after existing import: " + anchor.getText());
        } else {
            file.addBefore(importStatement, anchor);
            PsiElement newline = PsiParserFacade.getInstance(file.getProject()).createWhiteSpaceFromText("\n");
            file.addAfter(newline, importStatement); // Add newline after our new import
            LOG.info("Added import at beginning (after docstrings/comments/future) before: " + anchor.getText());
        }
    }
} 