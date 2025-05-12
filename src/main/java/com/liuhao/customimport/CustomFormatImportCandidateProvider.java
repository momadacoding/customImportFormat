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

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Provides standard import candidates. The custom formatting is handled by CustomFormatImportFix IntentionAction.
 */
public class CustomFormatImportCandidateProvider implements PyImportCandidateProvider {
    private static final Logger LOG = Logger.getInstance(CustomFormatImportCandidateProvider.class);
    
    @Override
    public void addImportCandidates(@NotNull PsiReference reference, @NotNull String name, 
                                    @NotNull AutoImportQuickFix quickFix) {
        PsiElement referenceElement = reference.getElement();
        Project project = referenceElement.getProject();
        String referenceName = name;

        LOG.info("addImportCandidates called for referenceName: " + referenceName);

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        // 1. Look for modules (files like referenceName.py)
        PsiFile[] filesArray = FilenameIndex.getFilesByName(project, referenceName + ".py", scope);
        LOG.debug("Found " + filesArray.length + " files for name: " + referenceName + ".py");
        for (PsiFile psiFile : filesArray) {
            if (psiFile instanceof PyFile) {
                LOG.debug("Processing PyFile for standard import: " + psiFile.getVirtualFile().getPath());
                addStandardImportCandidate(psiFile, referenceName, quickFix, referenceElement);
            }
        }

        // 2. Look for packages (directories like referenceName/__init__.py)
        Collection<VirtualFile> virtualDirs = FilenameIndex.getVirtualFilesByName(referenceName, true, scope);
        LOG.debug("Found " + virtualDirs.size() + " virtualDirs for name: " + referenceName);
        for (VirtualFile vDir : virtualDirs) {
            if (vDir.isDirectory()) {
                PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(vDir);
                if (psiDirectory != null && psiDirectory.findFile("__init__.py") != null) {
                    LOG.debug("Processing package directory for standard import: " + vDir.getPath());
                    addStandardImportCandidate(psiDirectory, referenceName, quickFix, referenceElement);
                }
            }
        }
    }

    private void addStandardImportCandidate(@NotNull PsiFileSystemItem item,
                                            @NotNull String referenceName, 
                                            @NotNull AutoImportQuickFix quickFix,
                                            @NotNull PsiElement referenceElement) {
        
        QualifiedName qualifiedNameObj = QualifiedNameFinder.findShortestImportableQName(item);
        if (qualifiedNameObj == null) {
            LOG.warn("Could not determine qualified name for item: " + item.getName() + " for standard import.");
            return;
        }
        
        if (item instanceof PsiNamedElement) {
            if (!referenceElement.isValid()) {
                LOG.warn("Reference element is no longer valid. Skipping addImport.");
                return;
            }
            quickFix.addImport((PsiNamedElement) item, item, qualifiedNameObj, null);
            LOG.info("Offered standard import candidate: " + qualifiedNameObj.toString() + " for " + referenceName);
        } else {
            LOG.warn("Item " + item.getName() + " is not a PsiNamedElement, cannot add standard import.");
        }
    }
}