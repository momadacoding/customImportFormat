package com.liuhao.customimport;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.QualifiedName;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.application.ApplicationManager;
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.codeInsight.imports.PyImportCandidateProvider;
import com.liuhao.customimport.settings.CustomImportSettingsState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides custom import candidates for specific directories.
 * This provider suggests import statements in the format:
 * `import a.b.c.d as d` instead of the standard PyCharm format `from a.b.c import d`
 */
public class CustomFormatImportCandidateProvider implements PyImportCandidateProvider {
    private static final Logger LOG = Logger.getInstance(CustomFormatImportCandidateProvider.class);
    
    private static final Map<String, AtomicBoolean> handledImports = new HashMap<>();

    @Override
    public void addImportCandidates(@NotNull PsiReference reference, @NotNull String name, 
                                    @NotNull AutoImportQuickFix quickFix) {
        PsiElement referenceElement = reference.getElement();
        Project project = referenceElement.getProject();
        String referenceName = name;

        LOG.info("addImportCandidates called for referenceName: " + referenceName);

        // Get the special directories from settings
        List<String> specialParentDirPaths = CustomImportSettingsState.getInstance().getSpecialDirectoriesList();
        if (specialParentDirPaths.isEmpty()) {
            LOG.info("No special directories configured, returning early.");
            return; // No special directories configured
        }

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        // 1. Look for modules (files like d.py)
        PsiFile[] filesArray = FilenameIndex.getFilesByName(project, referenceName + ".py", scope);
        LOG.info("Found " + filesArray.length + " files for name: " + referenceName + ".py");
        for (PsiFile psiFile : filesArray) {
            if (psiFile instanceof PyFile) {
                LOG.info("Processing PyFile: " + psiFile.getVirtualFile().getPath());
                processPotentialCandidate(psiFile, referenceName, specialParentDirPaths, quickFix, reference);
            }
        }

        // 2. Look for packages (directories like d/__init__.py)
        Collection<VirtualFile> virtualDirs = FilenameIndex.getVirtualFilesByName(referenceName, true, scope);
        LOG.info("Found " + virtualDirs.size() + " virtualDirs for name: " + referenceName);
        for (VirtualFile vDir : virtualDirs) {
            if (vDir.isDirectory()) {
                PsiDirectory psiDirectory = referenceElement.getManager().findDirectory(vDir);
                if (psiDirectory != null && psiDirectory.findFile("__init__.py") != null) {
                    LOG.info("Processing package directory: " + vDir.getPath());
                    processPotentialCandidate(psiDirectory, referenceName, specialParentDirPaths, quickFix, reference);
                }
            }
        }
    }

    private void processPotentialCandidate(@NotNull PsiFileSystemItem item, 
                                          @NotNull String referenceName, 
                                          @NotNull List<String> specialParentDirPaths,
                                          @NotNull AutoImportQuickFix quickFix,
                                          @NotNull PsiReference reference) {
        LOG.info("processPotentialCandidate: item=" + item.getName() + ", referenceName=" + referenceName);
        
        QualifiedName qualifiedNameObj = QualifiedNameFinder.findShortestImportableQName(item);
        if (qualifiedNameObj == null) {
            LOG.warn("Could not determine qualified name for item: " + item.getName());
            return;
        }
        String qualifiedName = qualifiedNameObj.toString();
        LOG.info("Qualified name for item: " + qualifiedName);

        PyFile pyFile = getPyFile(reference);
        if (pyFile == null) {
            LOG.warn("Could not get PyFile from reference.");
            return;
        }
        Project project = pyFile.getProject();

        // Case 1: Module/package import like a.b.c.d, referenceName is d
        if (qualifiedName.endsWith("." + referenceName)) {
            String parentQualifiedName = qualifiedName.substring(0, qualifiedName.length() - referenceName.length() - 1);
            String parentPath = parentQualifiedName.replace('.', '/');
            LOG.info("Parent path for " + qualifiedName + ": " + parentPath);

            for (String specialPath : specialParentDirPaths) {
                if (parentPath.equals(specialPath) || parentPath.startsWith(specialPath + "/")) {
                    LOG.info("Matched specialPath: " + specialPath + " for " + qualifiedName + ", using custom import format.");
                    
                    String importKey = qualifiedName + ":" + referenceName; 
                    AtomicBoolean handled = handledImports.computeIfAbsent(importKey, k -> new AtomicBoolean(false));
                    if (handled.getAndSet(true)) {
                        LOG.info("Skipping already handled import (proactively initiated): " + importKey);
                        // Still add to quickFix list in case the proactive insert failed or user wants to see it
                        // or if IDE needs it for some internal state.
                        if (item instanceof PsiNamedElement) {
                            quickFix.addImport((PsiNamedElement) item, item, qualifiedNameObj, referenceName);
                        }
                        return;
                    }
                    
                    PyElementGenerator generator = PyElementGenerator.getInstance(project);
                    PyImportStatement importStatement = generator.createImportStatement(
                        LanguageLevel.forElement(pyFile), qualifiedName, referenceName); // alias is referenceName
                    
                    LOG.info("Generated custom import statement: " + importStatement.getText());
                    CustomFormatImportFix.performDirectImportInsertion(project, pyFile, importStatement);
                    
                    // Add to quickFix list so user sees an option and IDE knows about the attempt.
                    // The proactive insert should ideally make this resolve correctly.
                    if (item instanceof PsiNamedElement) {
                        quickFix.addImport((PsiNamedElement) item, item, qualifiedNameObj, referenceName);
                        LOG.info("Added to quickFix: " + qualifiedName + " as " + referenceName);
                    }
                    return; 
                }
            }
        } 
        // Case 2: Top-level module case (e.g., qualifiedName is "math", referenceName is "math")
        else if (qualifiedName.equals(referenceName)) {
            for (String specialPath : specialParentDirPaths) {
                if (specialPath.isEmpty()) { // Empty path in settings matches top-level modules
                    LOG.info("Matched top-level specialPath (empty string) for " + qualifiedName + ", using custom import format.");
                    
                    String importKey = qualifiedName + ":null"; // null for no alias
                    AtomicBoolean handled = handledImports.computeIfAbsent(importKey, k -> new AtomicBoolean(false));
                    if (handled.getAndSet(true)) {
                        LOG.info("Skipping already handled top-level import (proactively initiated): " + importKey);
                        if (item instanceof PsiNamedElement) {
                            quickFix.addImport((PsiNamedElement) item, item, qualifiedNameObj, null);
                        }
                        return;
                    }

                    PyElementGenerator generator = PyElementGenerator.getInstance(project);
                    PyImportStatement importStatement = generator.createImportStatement(
                        LanguageLevel.forElement(pyFile), qualifiedName, null); // No alias

                    LOG.info("Generated custom top-level import statement: " + importStatement.getText());
                    CustomFormatImportFix.performDirectImportInsertion(project, pyFile, importStatement);
                    
                    if (item instanceof PsiNamedElement) {
                        quickFix.addImport((PsiNamedElement) item, item, qualifiedNameObj, null); // No alias
                        LOG.info("Added to quickFix top-level: " + qualifiedName);
                    }
                    return;
                }
            }
        }
        
        // If it's not a special path, we don't add anything to the quickFix here.
        // PyCharm's other providers or default behavior will handle it.
        // LOG.debug("No special path match for: " + qualifiedName + ", referenceName: " + referenceName);
    }
    
    /**
     * Get the PyFile containing the reference
     */
    private @Nullable PyFile getPyFile(@NotNull PsiReference reference) {
        PsiElement element = reference.getElement();
        if (element != null) {
            PsiFile file = element.getContainingFile();
            if (file instanceof PyFile) {
                return (PyFile) file;
            }
        }
        return null;
    }
}