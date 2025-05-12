package com.liuhao.customimport;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.diagnostic.Logger;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ImportCandidateFinder {

    private static final Logger LOG = Logger.getInstance(ImportCandidateFinder.class);

    public static List<ImportCandidate> findCandidates(@NotNull Project project, @NotNull String referenceName) {
        List<ImportCandidate> candidates = new ArrayList<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);

        LOG.debug("Searching for import candidates for: " + referenceName);

        // 1. Look for modules (files like referenceName.py)
        PsiFile[] filesArray = FilenameIndex.getFilesByName(project, referenceName + ".py", scope);
        LOG.debug("Found " + filesArray.length + " files for name: " + referenceName + ".py");
        for (PsiFile psiFile : filesArray) {
            if (psiFile instanceof PyFile) {
                addCandidate(psiFile, candidates, psiFile.getName());
            }
        }

        // 2. Look for packages (directories like referenceName/__init__.py)
        Collection<VirtualFile> virtualDirs = FilenameIndex.getVirtualFilesByName(referenceName, true, scope);
        LOG.debug("Found " + virtualDirs.size() + " virtualDirs for name: " + referenceName);
        for (VirtualFile vDir : virtualDirs) {
            if (vDir.isDirectory()) {
                PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(vDir);
                if (psiDirectory != null && psiDirectory.findFile("__init__.py") != null) {
                   addCandidate(psiDirectory, candidates, psiDirectory.getName());
                }
            }
        }
        
        LOG.debug("Found " + candidates.size() + " total candidates for " + referenceName);
        return candidates;
    }

    private static void addCandidate(@NotNull PsiFileSystemItem item, 
                                     @NotNull List<ImportCandidate> candidates,
                                     @NotNull String itemName) {
                                         
        QualifiedName qualifiedNameObj = QualifiedNameFinder.findShortestImportableQName(item);
        if (qualifiedNameObj != null) {
            candidates.add(new ImportCandidate(item, qualifiedNameObj));
            LOG.debug("Added candidate: " + qualifiedNameObj + " from item " + itemName);
        } else {
            LOG.warn("Could not determine qualified name for item: " + itemName);
        }
    }
} 