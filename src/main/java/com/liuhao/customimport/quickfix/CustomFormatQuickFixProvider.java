package com.liuhao.customimport.quickfix;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
//import com.jetbrains.python.codeInsight.imports.PyUnresolvedReferenceQuickFixProvider;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.inspections.PyUnresolvedReferenceQuickFixProvider;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.liuhao.customimport.CustomFormatImportFix;
import com.liuhao.customimport.ImportCandidate;
import com.liuhao.customimport.ImportCandidateFinder;
import com.liuhao.customimport.settings.CustomImportSettingsState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Provider for custom import quick fixes for unresolved Python references.
 * This implements the Python-specific PyUnresolvedReferenceQuickFixProvider interface.
 */
public class CustomFormatQuickFixProvider implements PyUnresolvedReferenceQuickFixProvider {

    private static final Logger LOG = Logger.getInstance(CustomFormatQuickFixProvider.class);

    @Override
    public void registerQuickFixes(@NotNull PsiReference psiReference, @NotNull List<LocalQuickFix> list) {
        // Only work with Python references
        if (!(psiReference.getElement() instanceof PyReferenceExpression)) {
            LOG.debug("Not a PyReferenceExpression, skipping");
            return;
        }

        PyReferenceExpression referenceExpression = (PyReferenceExpression) psiReference.getElement();
        
        // Skip any references that are already resolved
        if (psiReference.resolve() != null) {
            LOG.debug("Reference already resolved, skipping: " + referenceExpression.getText());
            return;
        }

        String referenceName = referenceExpression.getReferencedName();
        if (referenceName == null) {
            LOG.debug("Reference name is null, skipping");
            return;
        }

        Project project = referenceExpression.getProject();
        LOG.info("Processing unresolved reference: " + referenceName + " in " + 
                (referenceExpression.getContainingFile() != null ? 
                 referenceExpression.getContainingFile().getName() : "unknown file"));

        // Start with existing fixes count for debugging
        int initialFixesCount = list.size();
        LOG.debug("Initial fix list size before our additions: " + initialFixesCount);
        if (initialFixesCount > 0) {
            for (int i = 0; i < Math.min(initialFixesCount, 5); i++) {
                LOG.debug("  Existing fix [" + i + "]: " + list.get(i).getName());
            }
        }

        // Find potential import candidates using our finder
        List<ImportCandidate> candidates = ImportCandidateFinder.findCandidates(project, referenceName);

        if (candidates.isEmpty()) {
            LOG.debug("No import candidates found for " + referenceName);
            return;
        }

        // Get special directories from settings
        List<String> specialDirectories = CustomImportSettingsState.getInstance().getSpecialDirectoriesList();
        
        LOG.info("Found " + candidates.size() + " candidates for " + referenceName);
        
        // Track all the fixes we add for this reference
        List<CustomFormatImportFix> ourAddedFixes = new ArrayList<>();

        // Add our custom fixes to the provided list
        for (ImportCandidate candidate : candidates) {
            String qualifiedName = candidate.getQualifiedName().toString();
            LOG.debug("Processing candidate: " + qualifiedName);
            
            // Check if this candidate is in one of our special directories
            boolean isSpecialDir = false;
            for (String dir : specialDirectories) {
                // Convert qualified name path format to directory path format
                String dirStylePath = qualifiedName.replace('.', '/');
                String candidatePath = "";
                
                // Handle case where qualified name may have multiple components
                if (qualifiedName.contains(".")) {
                    // For modules inside packages, we want the parent path
                    candidatePath = qualifiedName.substring(0, qualifiedName.lastIndexOf('.')).replace('.', '/');
                } else {
                    // For top-level modules, use the empty path
                    candidatePath = "";
                }
                
                LOG.debug("Checking if candidate path '" + candidatePath + "' matches special dir '" + dir + "'");
                if (dir.isEmpty() || // empty dir matches top-level modules
                    candidatePath.equals(dir) || // exact match
                    candidatePath.startsWith(dir + "/")) { // subdirectory match
                    isSpecialDir = true;
                    LOG.debug("MATCH: Candidate " + qualifiedName + " matches special directory: " + dir);
                    break;
                }
            }
            
            if (isSpecialDir) {
                LOG.info("Creating HIGH PRIORITY fix for " + qualifiedName);
                CustomFormatImportFix fix = new CustomFormatImportFix(
                    referenceExpression,
                    candidate.getQualifiedName(),
                    referenceName,
                    true // High priority for special directory matches
                );
                
                // Add our fix to the BEGINNING of the list to ensure it appears first
                list.add(0, fix);
                LOG.info("Added high priority fix at beginning of list: " + fix.getText());
                ourAddedFixes.add(fix);
            } else {
                // For non-special directories, we don't add any fix - let the default system handle it
                LOG.debug("NOT special directory: " + qualifiedName + " - skipping custom fix");
            }
        }
        
        // Log the final list state for debugging
        LOG.debug("Final fix list size: " + list.size() + " (we added " + ourAddedFixes.size() + " fixes)");
        if (!list.isEmpty()) {
            for (int i = 0; i < Math.min(list.size(), 5); i++) {
                LOG.debug("  Final list item [" + i + "]: " + list.get(i).getName());
            }
        }
    }

}