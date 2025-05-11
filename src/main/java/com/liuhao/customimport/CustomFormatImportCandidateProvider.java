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
    
    // Map to store what imports we've already handled to avoid duplicates
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
        
        // Get qualified name for the item
        QualifiedName qualifiedNameObj = QualifiedNameFinder.findShortestImportableQName(item);
        if (qualifiedNameObj == null) {
            LOG.warn("Could not determine qualified name for item: " + item.getName());
            return; // Could not determine qualified name
        }
        
        String qualifiedName = qualifiedNameObj.toString();
        LOG.info("Qualified name for item: " + qualifiedName);

        // Check if this is a qualified name like a.b.c.d with referenceName d
        if (qualifiedName.endsWith("." + referenceName)) {
            String parentQualifiedName = qualifiedName.substring(0, qualifiedName.length() - referenceName.length() - 1);
            String parentPath = parentQualifiedName.replace('.', '/');
            LOG.info("Parent path: " + parentPath);
            
            // Check if it's in a special directory that needs custom import format
            for (String specialPath : specialParentDirPaths) {
                if (parentPath.equals(specialPath) || parentPath.startsWith(specialPath + "/")) {
                    LOG.info("Matched specialPath: " + specialPath + ", using custom import format");
                    
                    // Get a unique key for this import to avoid duplicates
                    String importKey = qualifiedName + ":" + referenceName;
                    
                    // Skip if we've already handled this import
                    AtomicBoolean handled = handledImports.computeIfAbsent(importKey, k -> new AtomicBoolean(false));
                    if (handled.getAndSet(true)) {
                        LOG.info("Skipping already handled import: " + importKey);
                        return;
                    }
                    
                    // Get the file where we need to insert the import
                    PyFile pyFile = getPyFile(reference);
                    if (pyFile != null) {
                        try {
                            // Generate the correct import statement format
                            // instead of "from a.b.c import d [as d]", we want "import a.b.c.d as d"
                            PyElementGenerator generator = PyElementGenerator.getInstance(pyFile.getProject());
                            PyImportStatement importStatement = generator.createImportStatement(
                                LanguageLevel.getDefault(), qualifiedName, referenceName);
                            
                            LOG.info("Created import statement: " + importStatement.getText());
                            
                            // Insert the import into the file
                            insertImportStatement(pyFile, importStatement);
                            
                            // Also add to quickFix to handle the case when import quick fix dialog is shown
                            // Pass null as asName because we don't want PyCharm to create another alias
                            if (item instanceof PsiNamedElement) {
                                PsiNamedElement namedElement = (PsiNamedElement) item;
                                quickFix.addImport(namedElement, item, qualifiedNameObj, null);
                            }
                        } catch (Exception e) {
                            LOG.error("Error adding import", e);
                        }
                    }
                    return;
                }
            }
        } 
        // Top-level module case: qualifiedName equals referenceName (e.g. "math")
        else if (qualifiedName.equals(referenceName)) {
            for (String specialPath : specialParentDirPaths) {
                if (specialPath.isEmpty()) { // Empty path matches top-level modules
                    LOG.info("Matched top-level specialPath (empty string), using custom import format");
                    
                    // Get a unique key for this import to avoid duplicates
                    String importKey = qualifiedName + ":null";
                    
                    // Skip if we've already handled this import
                    AtomicBoolean handled = handledImports.computeIfAbsent(importKey, k -> new AtomicBoolean(false));
                    if (handled.getAndSet(true)) {
                        LOG.info("Skipping already handled import: " + importKey);
                        return;
                    }
                    
                    // Get the file where we need to insert the import
                    PyFile pyFile = getPyFile(reference);
                    if (pyFile != null) {
                        try {
                            // For top-level modules, create "import math" statement
                            PyElementGenerator generator = PyElementGenerator.getInstance(pyFile.getProject());
                            PyImportStatement importStatement = generator.createImportStatement(
                                LanguageLevel.getDefault(), qualifiedName, null);
                            
                            LOG.info("Created import statement: " + importStatement.getText());
                            
                            // Insert the import into the file
                            insertImportStatement(pyFile, importStatement);
                            
                            // Also add to quickFix for completeness
                            if (item instanceof PsiNamedElement) {
                                PsiNamedElement namedElement = (PsiNamedElement) item;
                                quickFix.addImport(namedElement, item, qualifiedNameObj, null);
                            }
                        } catch (Exception e) {
                            LOG.error("Error adding top-level import", e);
                        }
                    }
                    return;
                }
            }
        }
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
    
    /**
     * Safely insert an import statement into a Python file
     */
    private void insertImportStatement(PyFile file, PyImportStatement importStatement) {
        Project project = file.getProject();
        
        // Use WriteCommandAction to safely modify PSI
        // But be careful about threading issues
        ApplicationManager.getApplication().invokeLater(() -> {
            WriteCommandAction.runWriteCommandAction(project, "Add Custom Import", null, () -> {
                try {
                    // Find the best place to insert the import
                    PyImportStatementBase existingImport = null;
                    
                    // Get all existing imports
                    List<PyImportStatementBase> existingImports = file.getImportBlock();
                    if (!existingImports.isEmpty()) {
                        existingImport = existingImports.get(existingImports.size() - 1);
                    }
                    
                    if (existingImport != null) {
                        // Add after the last import
                        file.addAfter(importStatement, existingImport);
                        LOG.info("Added import after existing imports");
                    } else {
                        // Add at the beginning of the file, but after docstrings
                        PsiElement firstElement = file.getFirstChild();
                        
                        // Skip past docstrings and comments
                        while (firstElement instanceof PsiWhiteSpace || 
                               firstElement instanceof PsiComment ||
                               (firstElement instanceof PyExpressionStatement && 
                                ((PyExpressionStatement)firstElement).getExpression() instanceof PyStringLiteralExpression)) {
                            firstElement = firstElement.getNextSibling();
                            if (firstElement == null) {
                                // Reached end of file
                                file.add(importStatement);
                                LOG.info("Added import at end of file");
                                return;
                            }
                        }
                        
                        // Add before the first non-docstring/comment
                        file.addBefore(importStatement, firstElement);
                        LOG.info("Added import at beginning after docstrings/comments");
                    }
                } catch (Exception e) {
                    LOG.error("Error inserting import statement", e);
                }
            });
        });
    }
}