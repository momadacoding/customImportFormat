package com.liuhao.customimport;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.liuhao.customimport.settings.CustomImportSettingsState;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Collection;

// Import for preview check
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;

public class CustomFormatImportFix extends BaseIntentionAction implements HighPriorityAction {
    private static final Logger LOG = Logger.getInstance(CustomFormatImportFix.class);

    private String detectedFullQualifiedName;
    private String detectedAliasName; // This is the referenceName for aliased imports

    public CustomFormatImportFix() {
        // Text will be set dynamically in isAvailable
    }

    @NotNull
    @Override
    public String getFamilyName() {
        return "Custom Python Import Formatter";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile) || editor == null) {
            return false;
        }
        // When generating a preview, the file is a copy and might not be physical.
        // We should avoid checks that rely on the file being part of the physical project structure if not needed.
        // However, FilenameIndex and QualifiedNameFinder should work on the copy's content.

        int offset = editor.getCaretModel().getOffset();
        PsiElement elementAtCaret = file.findElementAt(offset);
        PyReferenceExpression refExpr = PsiTreeUtil.getParentOfType(elementAtCaret, PyReferenceExpression.class);

        if (refExpr == null || refExpr.getReference().resolve() != null) {
            return false; 
        }

        String referenceName = refExpr.getName();
        if (referenceName == null || referenceName.isEmpty()) {
            return false;
        }

        List<String> specialParentDirPaths = CustomImportSettingsState.getInstance().getSpecialDirectoriesList();
        if (specialParentDirPaths.isEmpty()) {
            return false;
        }

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project); // Scope should be okay for preview
        boolean foundCandidateInSpecialPath = false;

        // 1. Look for modules (files like referenceName.py)
        PsiFile[] filesArray = FilenameIndex.getFilesByName(project, referenceName + ".py", scope);
        for (PsiFile psiFile : filesArray) {
            if (psiFile instanceof PyFile) {
                if (checkItem(psiFile, referenceName, specialParentDirPaths)) {
                    foundCandidateInSpecialPath = true;
                    break;
                }
            }
        }

        if (foundCandidateInSpecialPath) {
            // setText is a side-effect on the action itself, which is fine.
            if (this.detectedAliasName != null) {
                 setText("Import as 'import " + this.detectedFullQualifiedName + " as " + this.detectedAliasName + "'");
            } else {
                 setText("Import as 'import " + this.detectedFullQualifiedName + "'");
            }
            return true;
        }

        // 2. Look for packages (directories like referenceName/__init__.py)
        Collection<VirtualFile> virtualDirs = FilenameIndex.getVirtualFilesByName(referenceName, true, scope);
        for (VirtualFile vDir : virtualDirs) {
            if (vDir.isDirectory()) {
                PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(vDir);
                if (psiDirectory != null && psiDirectory.findFile("__init__.py") != null) {
                    if (checkItem(psiDirectory, referenceName, specialParentDirPaths)) {
                        foundCandidateInSpecialPath = true;
                        break;
                    }
                }
            }
        }
        
        if (foundCandidateInSpecialPath) {
             if (this.detectedAliasName != null) {
                setText("Import as 'import " + this.detectedFullQualifiedName + " as " + this.detectedAliasName + "'");
            } else { 
                 setText("Import as 'import " + this.detectedFullQualifiedName + "'");
            }
            return true;
        }

        return false;
    }

    private boolean checkItem(@NotNull PsiFileSystemItem item, @NotNull String referenceName, @NotNull List<String> specialParentDirPaths) {
        QualifiedName qualifiedNameObj = QualifiedNameFinder.findShortestImportableQName(item);
        if (qualifiedNameObj == null) {
            return false;
        }
        String qualifiedName = qualifiedNameObj.toString();

        if (qualifiedName.endsWith("." + referenceName)) {
            String parentQualifiedName = qualifiedName.substring(0, qualifiedName.length() - referenceName.length() - 1);
            String parentPath = parentQualifiedName.replace('.', '/');
            for (String specialPath : specialParentDirPaths) {
                if (parentPath.equals(specialPath) || parentPath.startsWith(specialPath + "/")) {
                    this.detectedFullQualifiedName = qualifiedName;
                    this.detectedAliasName = referenceName; 
                    return true;
                }
            }
        }
        else if (qualifiedName.equals(referenceName)) {
            for (String specialPath : specialParentDirPaths) {
                if (specialPath.isEmpty()) { 
                    this.detectedFullQualifiedName = qualifiedName;
                    this.detectedAliasName = null; 
                    return true;
                }
            }
        }
        return false;
    }


    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        if (!(file instanceof PyFile) || this.detectedFullQualifiedName == null) {
            return;
        }
        PyFile pyFile = (PyFile) file;
        final String qualifiedName = this.detectedFullQualifiedName;
        final String alias = this.detectedAliasName;

        // The core logic now uses AddImportHelper
        Runnable psiModificationLogic = () -> {
            try {
                // Use AddImportHelper to add the import. It requires the containing file,
                // the qualified name string, and the desired alias string.
                // We want the "import a.b.c.d as d" format, which AddImportHelper doesn't directly create.
                // AddImportHelper primarily creates "from a.b.c import d" or "import d".
                // Therefore, we still need to generate the specific statement and insert it manually,
                // but we can try to use AddImportHelper's anchor finding logic.

                PyElementGenerator generator = PyElementGenerator.getInstance(project);
                LanguageLevel languageLevel = LanguageLevel.forElement(pyFile);
                PyImportStatement customImportStatement = generator.createImportStatement(
                        languageLevel, qualifiedName, alias); // Generate the desired statement

                // Try to find a good anchor using AddImportHelper logic indirectly
                // Find the optimal anchor element for adding the import statement
                PsiElement anchor = AddImportHelper.getFileInsertPosition(pyFile);
                
                if (anchor != null) {
                    // Insert the generated statement relative to the anchor
                    PsiElement parent = anchor.getParent();
                    if (anchor == pyFile.getFirstChild() && !(anchor instanceof PyImportStatementBase)) {
                        // If anchor is the first non-import element, insert before it
                        parent.addBefore(customImportStatement, anchor);
                        parent.addAfter(PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n"), customImportStatement);
                    } else {
                        // Otherwise, insert after the anchor (typically the last import)
                        parent.addAfter(customImportStatement, anchor);
                         parent.addAfter(PsiParserFacade.getInstance(project).createWhiteSpaceFromText("\n"), anchor);
                    }
                } else {
                    // Fallback: add to the end of the file if no anchor found (e.g., empty file)
                    pyFile.add(customImportStatement);
                }
                LOG.info("Custom import statement operation attempted: " + customImportStatement.getText());

            } catch (Exception e) {
                LOG.error("Error applying custom import fix: " + e.getMessage(), e);
            }
        };

        if (IntentionPreviewUtils.isPreviewElement(pyFile)) {
            psiModificationLogic.run();
        } else {
            WriteCommandAction.runWriteCommandAction(project, getText(), null, psiModificationLogic, pyFile);
        }
    }
} 