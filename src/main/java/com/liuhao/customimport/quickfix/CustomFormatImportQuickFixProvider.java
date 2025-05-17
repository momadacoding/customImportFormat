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

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class CustomFormatImportQuickFixProvider implements PyUnresolvedReferenceQuickFixProvider {
    private static final Logger LOG = Logger.getInstance(CustomFormatImportQuickFixProvider.class);

    @Override
    public void registerQuickFixes(@NotNull PsiReference psiReference, @NotNull List<LocalQuickFix> list) {
        if (!(psiReference.getElement() instanceof PyReferenceExpression)) {
            return;
        }
        PyReferenceExpression refExpr = (PyReferenceExpression) psiReference.getElement();
        Project project = refExpr.getProject();
        PsiFile psiFile = refExpr.getContainingFile();

        if (!(psiFile instanceof PyFile)) {
            return;
        }

        String referenceName = refExpr.getName();
        if (referenceName == null || referenceName.isEmpty()) {
            return;
        }

        List<String> specialParentDirPaths = CustomImportSettingsState.getInstance().getSpecialDirectoriesList();
        if (specialParentDirPaths.isEmpty()) {
            return;
        }

        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        CandidateImport candidate = findCandidateImport(project, referenceName, specialParentDirPaths, scope, refExpr);

        if (candidate != null) {
            //list.clear();
            list.add(new AAA_CustomImportQuickFix(refExpr, candidate.fullQualifiedName, candidate.aliasName));
        }
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
        if (refExpr.getQualifier() != null) {
            PsiElement qualifier = refExpr.getQualifier();
            if (qualifier instanceof PyReferenceExpression) {
                PsiElement resolvedQualifier = ((PyReferenceExpression) qualifier).getReference().resolve();
                if (resolvedQualifier instanceof PyImportedModule || resolvedQualifier instanceof PyFile) {
                    return null;
                }
            }
        }


        PsiFile[] filesArray = FilenameIndex.getFilesByName(project, referenceName + ".py", scope);
        for (PsiFile pyFile : filesArray) {
            if (pyFile instanceof PyFile) {
                CandidateImport candidate = checkItem(pyFile, referenceName, specialParentDirPaths);
                if (candidate != null) return candidate;
            }
        }

        Collection<VirtualFile> virtualDirs = FilenameIndex.getVirtualFilesByName(referenceName, true, scope);
        for (VirtualFile vDir : virtualDirs) {
            if (vDir.isDirectory()) {
                PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(vDir);
                if (psiDirectory != null && psiDirectory.findFile("__init__.py") != null) {
                    CandidateImport candidate = checkItem(psiDirectory, referenceName, specialParentDirPaths);
                    if (candidate != null) return candidate;
                }
            }
        }
        return null;
    }


    @Nullable
    private CandidateImport checkItem(@NotNull PsiFileSystemItem item, @NotNull String referenceName, @NotNull List<String> specialParentDirPaths) {
        QualifiedName qualifiedNameObj = QualifiedNameFinder.findShortestImportableQName(item);
        if (qualifiedNameObj == null) {
            return null;
        }
        String qualifiedName = qualifiedNameObj.toString();

        if (qualifiedName.endsWith("." + referenceName)) {
            String parentQualifiedName = qualifiedName.substring(0, qualifiedName.length() - referenceName.length() - 1);
            String parentPath = parentQualifiedName.replace('.', '/');
            for (String specialPath : specialParentDirPaths) {
                if (parentPath.equals(specialPath) || parentPath.startsWith(specialPath + "/")) {
                    return new CandidateImport(qualifiedName, referenceName);
                }
            }
        }
        else if (qualifiedName.equals(referenceName) || qualifiedName.endsWith("." + referenceName)) {
             String parentPath = "";
             if (qualifiedName.contains(".")) {
                 parentPath = qualifiedName.substring(0, qualifiedName.lastIndexOf('.')).replace('.', '/');
             }

            for (String specialPath : specialParentDirPaths) {
                if (specialPath.isEmpty() && !parentPath.contains("/")) {
                    return new CandidateImport(qualifiedName, null);
                }
                if (!parentPath.isEmpty() && (parentPath.equals(specialPath) || parentPath.startsWith(specialPath + "/"))) {
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
                return;
            }
            PyFile pyFile = (PyFile) element.getContainingFile();
            if (pyFile == null) {
                return;
            }

            Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
            int caretOffset = -1;
            if (editor != null && editor.getDocument() == pyFile.getViewProvider().getDocument()) {
                caretOffset = editor.getCaretModel().getOffset();
            }

            int elementStartOffset = element.getTextRange().getStartOffset();
            int elementEndOffset = element.getTextRange().getEndOffset();
            
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
                    }
                    else {
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
                    LOG.info("Custom import statement applied: " + customImportStatement.getText());

                } catch (Exception e) {
                    LOG.error("Error applying custom import quick fix: " + e.getMessage(), e);
                }
            };
            
            if (IntentionPreviewUtils.isPreviewElement(pyFile)) {
                psiModificationLogic.run();
            } else {
                WriteCommandAction.runWriteCommandAction(project, psiModificationLogic);
                
                if (editor != null && caretOffset >= 0) {
                    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
                    
                    PsiElement updatedElement = null;
                    try {
                        PsiElement elementAt = pyFile.findElementAt(elementStartOffset);
                        if (elementAt != null) {
                            updatedElement = elementAt;
                            while (updatedElement != null && !(updatedElement instanceof PyReferenceExpression)) {
                                updatedElement = updatedElement.getParent();
                            }
                        }
                    } catch (Exception e) {
                        LOG.warn("Error finding updated element after import fix", e);
                    }
                    
                    if (updatedElement != null) {
                        int newOffset = updatedElement.getTextRange().getEndOffset();
                        editor.getCaretModel().moveToOffset(newOffset);
                        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                    } else {
                        try {
                            editor.getCaretModel().moveToOffset(caretOffset);
                            editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
                        } catch (Exception e) {
                            LOG.warn("Failed to restore caret position", e);
                        }
                    }
                }
            }
        }
    }
} 