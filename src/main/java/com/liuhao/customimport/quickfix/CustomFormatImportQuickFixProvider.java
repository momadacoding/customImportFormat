package com.liuhao.customimport.quickfix;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.inspections.PyUnresolvedReferenceQuickFixProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.liuhao.customimport.settings.CustomImportSettingsState;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class CustomFormatImportQuickFixProvider implements PyUnresolvedReferenceQuickFixProvider {
    private static final Logger LOG = Logger.getInstance(CustomFormatImportQuickFixProvider.class);
    
    static {
        LOG.info("CustomFormatImportQuickFixProvider class loaded!");
    }
    
    public CustomFormatImportQuickFixProvider() {
        LOG.info("CustomFormatImportQuickFixProvider instance created!");
    }

    @Override
    public void registerQuickFixes(@NotNull PsiReference psiReference, @NotNull List<LocalQuickFix> list) {
        LOG.info("=== CustomFormatImportQuickFixProvider.registerQuickFixes called ===");
        LOG.info("Thread: " + Thread.currentThread().getName());
        LOG.info("PsiReference: " + psiReference);
        LOG.info("PsiReference element: " + psiReference.getElement());
        LOG.info("Current quick fixes list size: " + list.size());
        
        // Log all existing quick fixes for debugging
        for (int i = 0; i < list.size(); i++) {
            LocalQuickFix fix = list.get(i);
            LOG.info("Existing quick fix " + i + ": " + fix.getClass().getSimpleName() + " - " + fix.getName());
        }
        
        if (!(psiReference.getElement() instanceof PyReferenceExpression)) {
            LOG.info("Element is not PyReferenceExpression, skipping. Element type: " + 
                    (psiReference.getElement() != null ? psiReference.getElement().getClass().getSimpleName() : "null"));
            return;
        }
        
        PyReferenceExpression refExpr = (PyReferenceExpression) psiReference.getElement();
        Project project = refExpr.getProject();
        PsiFile psiFile = refExpr.getContainingFile();
        
        LOG.info("PyReferenceExpression: " + refExpr);
        LOG.info("Project: " + project.getName());
        LOG.info("Containing file: " + (psiFile != null ? psiFile.getName() : "null"));

        if (!(psiFile instanceof PyFile)) {
            LOG.warn("Containing file is not PyFile, skipping. File type: " + 
                    (psiFile != null ? psiFile.getClass().getSimpleName() : "null"));
            return;
        }

        String referenceName = refExpr.getName();
        LOG.info("Reference name: '" + referenceName + "'");
        if (referenceName == null || referenceName.isEmpty()) {
            LOG.warn("Reference name is null or empty, skipping");
            return;
        }

        CustomImportSettingsState settingsState = CustomImportSettingsState.getInstance();
        LOG.info("Settings state instance: " + settingsState);
        List<String> specialParentDirPaths = settingsState.getSpecialDirectoriesList();
        LOG.info("Special directories configured: " + specialParentDirPaths);
        LOG.info("Special directories size: " + specialParentDirPaths.size());
        if (specialParentDirPaths.isEmpty()) {
            LOG.warn("No special directories configured - plugin will not provide any custom import fixes");
            return;
        }

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        LOG.info("Using search scope: " + scope);
        
        LOG.info("Calling findCandidateImport...");
        CandidateImport candidate = findCandidateImport(project, referenceName, specialParentDirPaths, scope, refExpr);

        if (candidate != null) {
            LOG.info("Found candidate import: " + candidate.fullQualifiedName + 
                    (candidate.aliasName != null ? " as " + candidate.aliasName : ""));
            AAA_CustomImportQuickFix quickFix = new AAA_CustomImportQuickFix(refExpr, candidate.fullQualifiedName, candidate.aliasName);
            list.add(quickFix);
            LOG.info("Added custom import fix to list. New list size: " + list.size());
            LOG.info("Quick fix name: " + quickFix.getName());
        } else {
            LOG.warn("No candidate import found for reference: " + referenceName);
        }
        
        LOG.info("=== registerQuickFixes completed ===");
    }

    private static class CandidateImport {
        final String fullQualifiedName;
        final String aliasName;

        CandidateImport(String fullQualifiedName, String aliasName) {
            this.fullQualifiedName = fullQualifiedName;
            this.aliasName = aliasName;
        }
    }

    @Nullable
    private CandidateImport findCandidateImport(@NotNull Project project,
                                                @NotNull String referenceName,
                                                @NotNull List<String> specialParentDirPaths,
                                                @NotNull GlobalSearchScope scope,
                                                @NotNull PyReferenceExpression refExpr) {
        LOG.info("--- findCandidateImport started ---");
        LOG.info("Reference name: " + referenceName);
        LOG.info("Special parent directories: " + specialParentDirPaths);
        
        // Check if reference has a qualifier (e.g., module.function)
        if (refExpr.getQualifier() != null) {
            LOG.info("Reference has qualifier: " + refExpr.getQualifier());
            PsiElement qualifier = refExpr.getQualifier();
            if (qualifier instanceof PyReferenceExpression) {
                PsiElement resolvedQualifier = ((PyReferenceExpression) qualifier).getReference().resolve();
                LOG.info("Resolved qualifier: " + resolvedQualifier + 
                        " (type: " + (resolvedQualifier != null ? resolvedQualifier.getClass().getSimpleName() : "null") + ")");
                if (resolvedQualifier instanceof PyImportedModule || resolvedQualifier instanceof PyFile) {
                    LOG.info("Qualifier already resolved, skipping custom import");
                    return null; // Already resolved, skip custom import
                }
            }
        } else {
            LOG.info("Reference has no qualifier");
        }

        // Search for files with the reference name
        LOG.info("Searching for files named: " + referenceName + ".py");
        PsiFile[] filesArray = FilenameIndex.getFilesByName(project, referenceName + ".py", scope);
        LOG.info("Found " + filesArray.length + " files in project scope");
        
        for (int i = 0; i < filesArray.length; i++) {
            PsiFile file = filesArray[i];
            LOG.info("File " + i + ": " + file.getName() + " (" + file.getClass().getSimpleName() + ") at " + 
                    (file.getVirtualFile() != null ? file.getVirtualFile().getPath() : "unknown path"));
        }
        
        // Try alternative search with allScope if projectScope found nothing useful
        if (filesArray.length == 0 || (filesArray.length > 0 && !(filesArray[0] instanceof PyFile))) {
            LOG.info("Trying alternative search with allScope...");
            GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
            PsiFile[] alternativeFiles = FilenameIndex.getFilesByName(project, referenceName + ".py", allScope);
            LOG.info("Found " + alternativeFiles.length + " files in all scope");
            
            for (PsiFile altFile : alternativeFiles) {
                LOG.info("Alternative file: " + altFile.getName() + " (" + altFile.getClass().getSimpleName() + ") at " + 
                        (altFile.getVirtualFile() != null ? altFile.getVirtualFile().getPath() : "unknown path"));
                if (altFile instanceof PyFile && scope.contains(altFile.getVirtualFile())) {
                    LOG.info("Found better match in alternative search, replacing");
                    // Replace with better match
                    if (filesArray.length > 0) {
                        filesArray[0] = altFile;
                    } else {
                        filesArray = new PsiFile[]{altFile};
                    }
                    break;
                }
            }
        }

        // Process found files
        LOG.info("Processing " + filesArray.length + " found files...");
        for (int i = 0; i < filesArray.length; i++) {
            PsiFile psiFile = filesArray[i];
            LOG.info("Processing file " + i + ": " + psiFile.getName());
            
            if (psiFile instanceof PyFile) {
                LOG.info("File is PyFile, calling checkItem...");
                CandidateImport candidate = checkItem(psiFile, referenceName, specialParentDirPaths);
                if (candidate != null) {
                    LOG.info("Found candidate from PyFile: " + candidate.fullQualifiedName);
                    return candidate;
                } else {
                    LOG.info("No candidate found from PyFile");
                }
            } else if (psiFile.getFileType().getName().equals("Python")) {
                LOG.info("File is Python type but not PyFile, trying manual check...");
                // Handle case where PyCharm doesn't recognize Python file correctly
                VirtualFile virtualFile = psiFile.getVirtualFile();
                if (virtualFile != null) {
                    CandidateImport candidate = checkItemManually(virtualFile, referenceName, specialParentDirPaths, project);
                    if (candidate != null) {
                        LOG.info("Found candidate from manual check: " + candidate.fullQualifiedName);
                        return candidate;
                    } else {
                        LOG.info("No candidate found from manual check");
                    }
                } else {
                    LOG.warn("VirtualFile is null for Python file");
                }
            } else {
                LOG.info("File is not Python type: " + psiFile.getFileType().getName());
            }
        }

        // Search for directories (packages) with the reference name
        LOG.info("Searching for directories named: " + referenceName);
        Collection<VirtualFile> virtualDirs = FilenameIndex.getVirtualFilesByName(referenceName, true, scope);
        LOG.info("Found " + virtualDirs.size() + " directories");
        
        for (VirtualFile vDir : virtualDirs) {
            LOG.info("Processing directory: " + vDir.getPath());
            if (vDir.isDirectory()) {
                PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(vDir);
                VirtualFile initFile = vDir.findChild("__init__.py");
                LOG.info("Directory has __init__.py: " + (initFile != null));
                
                if (psiDirectory != null && initFile != null) {
                    LOG.info("Directory is valid package, calling checkItem...");
                    CandidateImport candidate = checkItem(psiDirectory, referenceName, specialParentDirPaths);
                    if (candidate != null) {
                        LOG.info("Found candidate from directory: " + candidate.fullQualifiedName);
                        return candidate;
                    } else {
                        LOG.info("No candidate found from directory");
                    }
                } else {
                    LOG.info("Directory is not valid package (psiDirectory: " + psiDirectory + ", __init__.py: " + initFile + ")");
                }
            } else {
                LOG.info("VirtualFile is not a directory");
            }
        }
        
        LOG.info("No candidate import found");
        LOG.info("--- findCandidateImport completed ---");
        return null;
    }


    @Nullable
    private CandidateImport checkItem(@NotNull PsiFileSystemItem item, @NotNull String referenceName, @NotNull List<String> specialParentDirPaths) {
        LOG.info("--- checkItem started ---");
        LOG.info("Item: " + item.getName() + " (" + item.getClass().getSimpleName() + ")");
        LOG.info("Item path: " + (item.getVirtualFile() != null ? item.getVirtualFile().getPath() : "unknown"));
        LOG.info("Reference name: " + referenceName);
        
        // Get project and virtual file first
        VirtualFile virtualFile = item.getVirtualFile();
        Project project = null;
        if (item instanceof PsiFile) {
            project = ((PsiFile) item).getProject();
        } else if (item instanceof PsiDirectory) {
            project = ((PsiDirectory) item).getProject();
        }
        
        // First try manual calculation using source roots (more reliable for our use case)
        if (virtualFile != null && project != null) {
            LOG.info("Trying manual qualified name calculation using source roots...");
            CandidateImport candidate = calculateQualifiedNameFromSourceRootsWithMatching(virtualFile, referenceName, specialParentDirPaths, project);
            if (candidate != null) {
                LOG.info("Found match using manual source root calculation");
                return candidate;
            } else {
                LOG.info("No match found using manual calculation, trying PyCharm's built-in finder...");
            }
        }
        
        // Fallback to PyCharm's built-in qualified name finder
        QualifiedName qualifiedNameObj = QualifiedNameFinder.findShortestImportableQName(item);
        if (qualifiedNameObj != null) {
            String qualifiedName = qualifiedNameObj.toString();
            LOG.info("PyCharm qualified name: " + qualifiedName);
            
            CandidateImport candidate = checkQualifiedNameAgainstSpecialDirs(qualifiedName, referenceName, specialParentDirPaths);
            if (candidate != null) {
                LOG.info("Found match using PyCharm qualified name");
                return candidate;
            } else {
                LOG.info("No match found using PyCharm qualified name either");
            }
        } else {
            LOG.warn("Could not determine qualified name using PyCharm's finder for item: " + item.getName());
        }
        
        if (virtualFile == null) {
            LOG.warn("VirtualFile is null for item");
        }
        if (project == null) {
            LOG.warn("Could not get project from item");
        }

        LOG.info("No candidate found for item");
        LOG.info("--- checkItem completed ---");
        return null;
    }

    /**
     * Manual qualified name calculation when PyCharm doesn't recognize the file as Python
     */
    @Nullable
    private CandidateImport checkItemManually(@NotNull VirtualFile virtualFile, 
                                             @NotNull String referenceName, 
                                             @NotNull List<String> specialParentDirPaths,
                                             @NotNull Project project) {
        LOG.info("--- checkItemManually started ---");
        LOG.info("Virtual file: " + virtualFile.getPath());
        LOG.info("Reference name: " + referenceName);
        
        try {
            CandidateImport result = calculateQualifiedNameFromSourceRootsWithMatching(virtualFile, referenceName, specialParentDirPaths, project);
            if (result != null) {
                LOG.info("Found candidate import: " + result.fullQualifiedName + " as " + result.aliasName);
                return result;
            } else {
                LOG.info("No candidate import found for file: " + virtualFile.getPath());
            }
        } catch (Exception e) {
            LOG.warn("Error in manual qualified name calculation", e);
        }
        
        LOG.info("--- checkItemManually completed ---");
        return null;
    }

    /**
     * Calculate qualified name using PyCharm's configured source roots and find one that matches special directories
     */
    @Nullable
    private CandidateImport calculateQualifiedNameFromSourceRootsWithMatching(@NotNull VirtualFile virtualFile,
                                                                             @NotNull String referenceName,
                                                                             @NotNull List<String> specialParentDirPaths,
                                                                             @NotNull Project project) {
        // Get all source roots from project structure
        VirtualFile[] sourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
        LOG.info("Found " + sourceRoots.length + " source roots in project");
        for (VirtualFile root : sourceRoots) {
            LOG.info("Source root: " + root.getPath());
        }
        
        if (sourceRoots.length == 0) {
            LOG.warn("No source roots found in project! Please configure source roots in Project Structure ??Modules ??Sources");
        }
        
        String filePath = virtualFile.getPath();
        LOG.info("Calculating qualified name for file: " + filePath);
        
        // Collect all matching source roots and their qualified names
        List<SourceRootMatch> matches = new ArrayList<>();
        
        for (VirtualFile sourceRoot : sourceRoots) {
            String sourceRootPath = sourceRoot.getPath();
            if (filePath.startsWith(sourceRootPath)) {
                // Calculate relative path from this source root
                String relativePath = filePath.substring(sourceRootPath.length());
                if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                    relativePath = relativePath.substring(1);
                }
                
                if (relativePath.endsWith(".py")) {
                    // Remove .py extension and convert path separators to dots
                    relativePath = relativePath.substring(0, relativePath.length() - 3);
                    String qualifiedName = relativePath.replace('/', '.').replace('\\', '.');
                    matches.add(new SourceRootMatch(sourceRootPath, qualifiedName));
                    LOG.info("Potential match - Source root: " + sourceRootPath + " ??" + qualifiedName);
                }
            }
        }
        
        // Sort by source root path length (longest first) to prefer more specific roots
        // This ensures that if both "Python" and "Python/Client" are source roots,
        // we'll use "Python/Client" which gives us the correct qualified name
        matches.sort((a, b) -> Integer.compare(b.sourceRootPath.length(), a.sourceRootPath.length()));
        
        // Try each match to see if it produces a valid candidate import, starting with most specific
        for (SourceRootMatch match : matches) {
            LOG.info("Testing qualified name: " + match.qualifiedName + " from source root: " + match.sourceRootPath);
            CandidateImport candidate = checkQualifiedNameAndFilePathAgainstSpecialDirs(
                match.qualifiedName, referenceName, specialParentDirPaths, virtualFile, project);
            if (candidate != null) {
                LOG.info("Found matching candidate with source root: " + match.sourceRootPath + " ??" + match.qualifiedName);
                return candidate;
            }
        }
        
        LOG.info("No source root produced a matching qualified name, trying fallback patterns");
        return null;
    }

    /**
     * Calculate qualified name using PyCharm's configured source roots (original method for fallback)
     */
    @Nullable
    private String calculateQualifiedNameFromSourceRoots(@NotNull VirtualFile virtualFile, @NotNull Project project) {
        // Get all source roots from project structure
        VirtualFile[] sourceRoots = ProjectRootManager.getInstance(project).getContentSourceRoots();
        LOG.info("Found " + sourceRoots.length + " source roots in project");
        for (VirtualFile root : sourceRoots) {
            LOG.info("Source root: " + root.getPath());
        }
        
        String filePath = virtualFile.getPath();
        LOG.info("Calculating qualified name for file: " + filePath);
        
        // Collect all matching source roots and their qualified names
        List<SourceRootMatch> matches = new ArrayList<>();
        
        for (VirtualFile sourceRoot : sourceRoots) {
            String sourceRootPath = sourceRoot.getPath();
            if (filePath.startsWith(sourceRootPath)) {
                // Calculate relative path from this source root
                String relativePath = filePath.substring(sourceRootPath.length());
                if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                    relativePath = relativePath.substring(1);
                }
                
                if (relativePath.endsWith(".py")) {
                    // Remove .py extension and convert path separators to dots
                    relativePath = relativePath.substring(0, relativePath.length() - 3);
                    String qualifiedName = relativePath.replace('/', '.').replace('\\', '.');
                    matches.add(new SourceRootMatch(sourceRootPath, qualifiedName));
                    LOG.info("Potential match - Source root: " + sourceRootPath + " ??" + qualifiedName);
                }
            }
        }
        
        // Sort by source root path length (longest first) to prefer more specific roots
        matches.sort((a, b) -> Integer.compare(b.sourceRootPath.length(), a.sourceRootPath.length()));
        
        // Return the first (most specific) match
        if (!matches.isEmpty()) {
            SourceRootMatch bestMatch = matches.get(0);
            LOG.info("Using PyCharm source root: " + bestMatch.sourceRootPath + " ??" + bestMatch.qualifiedName);
            return bestMatch.qualifiedName;
        }
        
        // Fallback: try with configured patterns for backward compatibility
        return calculateQualifiedNameFromConfiguredPatterns(virtualFile, project);
    }
    
    private static class SourceRootMatch {
        final String sourceRootPath;
        final String qualifiedName;
        
        SourceRootMatch(String sourceRootPath, String qualifiedName) {
            this.sourceRootPath = sourceRootPath;
            this.qualifiedName = qualifiedName;
        }
    }

    /**
     * Fallback method using configured source root patterns for backward compatibility
     */
    @Nullable
    private String calculateQualifiedNameFromConfiguredPatterns(@NotNull VirtualFile virtualFile, @NotNull Project project) {
        String filePath = virtualFile.getPath();
        VirtualFile projectRoot = project.getBaseDir();
        if (projectRoot == null) {
            return null;
        }
        
        String projectPath = projectRoot.getPath();
        if (!filePath.startsWith(projectPath)) {
            return null;
        }
        
        String relativePath = filePath.substring(projectPath.length());
        String sourcePath = null;
        
        // Get configured source root patterns
        List<String> sourceRootPatterns = CustomImportSettingsState.getInstance().getSourceRootPatterns();
        
        // Find the Python source root using configured patterns
        for (String pattern : sourceRootPatterns) {
            String searchPattern = "/" + pattern + "/";
            int patternIndex = relativePath.indexOf(searchPattern);
            if (patternIndex >= 0) {
                sourcePath = relativePath.substring(patternIndex + searchPattern.length());
                break;
            }
        }
        
        // Fallback: use the relative path directly if no pattern matched
        if (sourcePath == null) {
            sourcePath = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        }
        
        if (sourcePath != null && sourcePath.endsWith(".py")) {
            // Remove .py extension and convert path separators to dots
            sourcePath = sourcePath.substring(0, sourcePath.length() - 3);
            String qualifiedName = sourcePath.replace('/', '.').replace('\\', '.');
            LOG.info("Using fallback pattern calculation ??" + qualifiedName);
            return qualifiedName;
        }
        
        return null;
    }
    
    /**
     * Check a manually calculated qualified name and file path against special directories
     */
    @Nullable
    private CandidateImport checkQualifiedNameAndFilePathAgainstSpecialDirs(@NotNull String qualifiedName,
                                                                           @NotNull String referenceName,
                                                                           @NotNull List<String> specialParentDirPaths,
                                                                           @NotNull VirtualFile virtualFile,
                                                                           @NotNull Project project) {
        LOG.info("Checking qualified name: " + qualifiedName + " against special directories: " + specialParentDirPaths);
        
        // Get the file system path relative to project root
        VirtualFile projectRoot = project.getBaseDir();
        String fileSystemPath = null;
        if (projectRoot != null) {
            String projectPath = projectRoot.getPath();
            String filePath = virtualFile.getPath();
            if (filePath.startsWith(projectPath)) {
                String relativePath = filePath.substring(projectPath.length());
                if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                    relativePath = relativePath.substring(1);
                }
                // Remove the filename to get the directory path
                int lastSlash = Math.max(relativePath.lastIndexOf('/'), relativePath.lastIndexOf('\\'));
                if (lastSlash >= 0) {
                    fileSystemPath = relativePath.substring(0, lastSlash).replace('\\', '/');
                }
            }
        }
        
        LOG.info("File system path relative to project: " + fileSystemPath);
        
        // Check if the file system path matches any special directory
        if (fileSystemPath != null) {
            for (String specialPath : specialParentDirPaths) {
                LOG.info("Checking if file system path '" + fileSystemPath + "' matches special path '" + specialPath + "'");
                if (fileSystemPath.equals(specialPath) || fileSystemPath.startsWith(specialPath + "/")) {
                    LOG.info("File system path match found! Creating candidate import: " + qualifiedName + " as " + referenceName);
                    return new CandidateImport(qualifiedName, referenceName);
                }
            }
        }
        
        // Fallback to the original logic for backward compatibility
        return checkQualifiedNameAgainstSpecialDirs(qualifiedName, referenceName, specialParentDirPaths);
    }

    /**
     * Check a manually calculated qualified name against special directories (original method for fallback)
     */
    @Nullable
    private CandidateImport checkQualifiedNameAgainstSpecialDirs(@NotNull String qualifiedName,
                                                                @NotNull String referenceName,
                                                                @NotNull List<String> specialParentDirPaths) {
        LOG.info("Checking qualified name: " + qualifiedName + " against special directories: " + specialParentDirPaths);
        
        // First condition: qualifiedName ends with "." + referenceName
        if (qualifiedName.endsWith("." + referenceName)) {
            String parentQualifiedName = qualifiedName.substring(0, qualifiedName.length() - referenceName.length() - 1);
            String parentPath = parentQualifiedName.replace('.', '/');
            LOG.info("Parent path: " + parentPath + " (from qualified name: " + qualifiedName + ")");
            
            for (String specialPath : specialParentDirPaths) {
                LOG.info("Checking if parent path '" + parentPath + "' matches special path '" + specialPath + "'");
                if (parentPath.equals(specialPath) || parentPath.startsWith(specialPath + "/")) {
                    LOG.info("Match found! Creating candidate import: " + qualifiedName + " as " + referenceName);
                    return new CandidateImport(qualifiedName, referenceName);
                }
            }
        }
        // Second condition: qualifiedName equals referenceName OR ends with "." + referenceName
        else if (qualifiedName.equals(referenceName) || qualifiedName.endsWith("." + referenceName)) {
            String parentPath = "";
            if (qualifiedName.contains(".")) {
                parentPath = qualifiedName.substring(0, qualifiedName.lastIndexOf('.')).replace('.', '/');
            }

            for (String specialPath : specialParentDirPaths) {
                // Handle empty special path with top-level modules
                if (specialPath.isEmpty() && !parentPath.contains("/")) {
                    return new CandidateImport(qualifiedName, null);
                }
                
                // Handle non-empty parent paths
                if (!parentPath.isEmpty()) {
                    if (parentPath.equals(specialPath) || parentPath.startsWith(specialPath + "/")) {
                        if (qualifiedName.equals(referenceName) && specialPath.isEmpty()) {
                            return new CandidateImport(qualifiedName, null);
                        }
                        if (qualifiedName.endsWith("." + referenceName)) {
                            return new CandidateImport(qualifiedName, referenceName);
                        }
                        return new CandidateImport(qualifiedName, referenceName);
                    }
                }
            }
        }

        return null;
    }

    private static class AAA_CustomImportQuickFix implements LocalQuickFix, HighPriorityAction {
        private final String fullQualifiedName;
        private final String aliasName;
        private final PyReferenceExpression myElement;

        AAA_CustomImportQuickFix(PyReferenceExpression element, String fullQualifiedName, @Nullable String aliasName) {
            this.fullQualifiedName = fullQualifiedName;
            this.aliasName = aliasName;
            this.myElement = element;
        }

        @Nls(capitalization = Nls.Capitalization.Sentence)
        @NotNull
        @Override
        public String getName() {
            if (aliasName != null) {
                return "AAA Import as 'import " + fullQualifiedName + " as " + aliasName + "'";
            } else {
                return "AAA Import as 'import " + fullQualifiedName + "'";
            }
        }

        @Nls(capitalization = Nls.Capitalization.Sentence)
        @NotNull
        @Override
        public String getFamilyName() {
            return "AAA Custom Python Import Formatter";
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            if (element == null || !element.isValid()) {
                LOG.warn("Element is null or invalid, aborting fix");
                return;
            }
            
            PyFile pyFile = (PyFile) element.getContainingFile();
            if (pyFile == null) {
                LOG.warn("Could not get PyFile from element, aborting fix");
                return;
            }

            // Store the original cursor position and document length before any modifications
            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            int originalCaretOffset = -1;
            int originalDocumentLength = -1;
            if (editor != null && editor.getDocument() == pyFile.getViewProvider().getDocument()) {
                originalCaretOffset = editor.getCaretModel().getOffset();
                originalDocumentLength = editor.getDocument().getTextLength();
            }
            
            Runnable psiModificationLogic = () -> {
                try {
                    PyElementGenerator generator = PyElementGenerator.getInstance(project);
                    LanguageLevel languageLevel = LanguageLevel.forElement(pyFile);
                    PyImportStatementBase customImportStatement;

                    QualifiedName qNameForImport = QualifiedName.fromDottedString(fullQualifiedName);

                    if (aliasName != null && !Objects.equals(qNameForImport.getLastComponent(), aliasName)) {
                        customImportStatement = generator.createImportStatement(languageLevel, fullQualifiedName, aliasName);
                    } else if (aliasName != null && Objects.equals(qNameForImport.getLastComponent(), aliasName)) {
                        customImportStatement = generator.createImportStatement(languageLevel, fullQualifiedName, aliasName);
                    } else {
                        customImportStatement = generator.createImportStatement(languageLevel, fullQualifiedName, null);
                    }

                    PsiElement anchor = AddImportHelper.getFileInsertPosition(pyFile);
                    if (anchor != null) {
                        PsiElement parent = anchor.getParent();
                        if (anchor == pyFile.getFirstChild() && !(anchor instanceof PyImportStatementBase)) {
                            parent.addBefore(customImportStatement, anchor);
                            parent.addAfter(PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n"), customImportStatement);
                        } else {
                            PsiElement addedImport = parent.addAfter(customImportStatement, anchor);
                            parent.addAfter(PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n"), addedImport);
                        }
                    } else {
                        pyFile.add(customImportStatement);
                        pyFile.add(PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n"));
                    }

                } catch (Exception e) {
                    LOG.error("Error applying custom import quick fix: " + e.getMessage(), e);
                }
            };
            
            if (IntentionPreviewUtils.isPreviewElement(pyFile)) {
                psiModificationLogic.run();
            } else {
                WriteCommandAction.runWriteCommandAction(project, psiModificationLogic);
                
                // Restore the original cursor position after PSI modifications
                if (editor != null && originalCaretOffset >= 0 && originalDocumentLength >= 0) {
                    // Ensure all PSI changes are committed to the document
                    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
                    
                    try {
                        // Calculate the offset adjustment due to added import statements
                        int currentDocumentLength = editor.getDocument().getTextLength();
                        int lengthDifference = currentDocumentLength - originalDocumentLength;
                        
                        // Adjust the cursor position by the amount of text that was added
                        int adjustedOffset = originalCaretOffset + lengthDifference;
                        
                        // Ensure the adjusted offset is within document bounds
                        adjustedOffset = Math.max(0, Math.min(adjustedOffset, currentDocumentLength));
                        
                        // Restore the cursor to the adjusted position
                        editor.getCaretModel().moveToOffset(adjustedOffset);
                        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                        
                        LOG.info("Restored cursor position to offset: " + adjustedOffset + 
                                " (original: " + originalCaretOffset + ", length diff: " + lengthDifference + ")");
                    } catch (Exception e) {
                        LOG.warn("Failed to restore caret position", e);
                        // Fallback: just ensure the cursor is visible
                        try {
                            editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                        } catch (Exception fallbackException) {
                            LOG.warn("Even fallback cursor positioning failed", fallbackException);
                        }
                    }
                }
            }
        }
    }
} 