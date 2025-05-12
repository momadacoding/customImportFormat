package com.liuhao.customimport;

import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.QualifiedName;
import org.jetbrains.annotations.NotNull;

public class ImportCandidate {
    private final PsiFileSystemItem item;
    private final QualifiedName qualifiedName;

    public ImportCandidate(@NotNull PsiFileSystemItem item, @NotNull QualifiedName qualifiedName) {
        this.item = item;
        this.qualifiedName = qualifiedName;
    }

    @NotNull
    public PsiFileSystemItem getItem() {
        return item;
    }

    @NotNull
    public QualifiedName getQualifiedName() {
        return qualifiedName;
    }
} 