package com.liuhao.customimport;

import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyFromImportStatement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Make it a QuickFix based on the element, high priority
public class CustomFormatImportFix extends LocalQuickFixOnPsiElement implements HighPriorityAction {
    private static final Logger LOG = Logger.getInstance(CustomFormatImportFix.class);

    private final QualifiedName importPath; // e.g., "my.module" or null for root
    private final String importElementName; // e.g., "my_function" or "*"
    private final String customFormatString; // e.g., "from {} import {}" or "from {} import *"
    private final boolean isHighPriority; // Whether this should be a high priority fix

    /**
     * Constructor with explicit priority control
     */
    public CustomFormatImportFix(@NotNull PsiElement element, @NotNull QualifiedName qName, 
                               @NotNull String elementName, boolean isHighPriority) {
        super(element); // Pass the element where the fix is applied
        
        // Handle case where qName represents a top-level module/package directly
        if (qName.getComponentCount() == 1 && qName.getLastComponent().equals(elementName)) {
             this.importPath = null; // Indicates top-level import needed
        } else {
             this.importPath = qName.removeLastComponent(); // Path to the module/package
        }
        this.importElementName = elementName; // The specific thing being imported or "*"
        this.isHighPriority = isHighPriority;

        // Determine the custom format (replace with actual logic based on settings/rules)
        if ("*".equals(elementName)) {
            this.customFormatString = "from {} import *  # custom rule"; 
        } else if (this.importPath == null) {
            this.customFormatString = "import {}  # custom rule"; // Format for top-level import
        }
        else {
            this.customFormatString = "from {} import {}  # custom rule";
        }
        
        LOG.debug("CustomFormatImportFix created for element: " + element.getText() + 
                 ", qName: " + qName + ", derived path: " + importPath + 
                 ", name: " + elementName + ", high priority: " + isHighPriority);
    }

    /**
     * Constructor with default priority (for backward compatibility)
     */
    public CustomFormatImportFix(@NotNull PsiElement element, @NotNull QualifiedName qName, 
                               @NotNull String elementName) {
        this(element, qName, elementName, true); // Default to high priority
    }

    @Override
    public @NotNull String getText() {
        // Text displayed in the quick fix list
        String displayPath = importPath != null ? importPath.toString() : "<root>";
        String priority = isHighPriority ? "[HIGH] " : ""; // Visual indicator in text
        return String.format("%sImport '%s' from '%s' (Custom Format)", 
                           priority, importElementName, displayPath);
    }

    @Override
    public @NotNull String getFamilyName() {
        // Group similar fixes
        return isHighPriority ? "Custom Import Formatter (High Priority)" : "Custom Import Formatter";
    }
    
    /**
     * Used by the IDE to determine ordering of quick fixes. Lower values appear first.
     */
    @Override
    public Priority getPriority() {
        PriorityAction.Priority var10000 = Priority.TOP;
        return isHighPriority ? var10000 : Priority.HIGH; // High negative priority appears first
    }
    
    // We implement invoke() for LocalQuickFixOnPsiElement
    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
        LOG.debug("invoke called for CustomFormatImportFix for element " + startElement.getText());
        
        // Use WriteCommandAction to modify the PSI tree
        // Pass family name to group undo actions
        WriteCommandAction.runWriteCommandAction(project, getFamilyName(), null, this::performImport, file); 
    }

    // isAvailable is inherited from LocalQuickFixOnPsiElement and checks element validity.

    private void performImport() {
        PsiElement element = getStartElement(); // The unresolved reference element
        if (element == null || !element.isValid()) {
             LOG.warn("Element is no longer valid in performImport.");
             return;
        }
        Project project = element.getProject();
        PsiFile file = element.getContainingFile();

        if (file == null) {
             LOG.warn("Containing file is null.");
             return;
        }

        LOG.info("Attempting to add custom import for: " + importElementName + " from " + (importPath != null ? importPath : "<root>"));

        // Generate the custom import statement text
        String importStatementText;
        try {
            if ("*".equals(importElementName)) {
                if (importPath == null || importPath.getComponentCount() == 0) {
                     LOG.warn("Cannot generate 'import *' from root.");
                     return; // Or handle differently
                }
                 // Format string like "from {} import * # custom rule"
                importStatementText = String.format(customFormatString.replace("{}", "%s"), importPath.toString());
            } else if (importPath == null) {
                // Format string like "import {} # custom rule"
                importStatementText = String.format(customFormatString.replace("{}", "%s"), importElementName);
            }
            else {
                 // Format string like "from {} import {} # custom rule"
                 importStatementText = String.format(customFormatString.replace("{}", "%s"), importPath.toString(), importElementName);
            }
        } catch (java.util.IllegalFormatException e) {
             LOG.error("Error formatting custom import string: '" + customFormatString + 
                       "' with path='" + importPath + "', name='" + importElementName + "'", e);
             return;
        }


        LOG.debug("Generated import statement text: " + importStatementText);
        
        // Add the import statement using AddImportHelper for correct placement
        // Fix for error #1: USER_IMPORT doesn't exist in your version, using NORMAL instead
        // AddImportHelper.ImportPriority priority = AddImportHelper.ImportPriority.USER_IMPORT;
        
        // It seems AddImportHelper doesn't directly take a raw string. 
        // We need to create a PSI element for the import statement first.
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        try {
            // Fix for error #2: Use the correct method to create an import statement
            // The createImportStatementFromText doesn't exist in your version
            PyImportStatementBase newImportStmt;
            
            // Create the appropriate type of import statement based on the format
            if (importPath == null) {
                // Simple import: "import modulename"
                newImportStmt = elementGenerator.createFromText(
                    LanguageLevel.forElement(file),
                    PyImportStatement.class,
                    importStatementText
                );
            } else {
                // From import: "from module import name"
                newImportStmt = elementGenerator.createFromText(
                    LanguageLevel.forElement(file),
                    PyFromImportStatement.class,
                    importStatementText
                );
            }
            
            if (newImportStmt == null) {
                LOG.error("Failed to generate PSI for import statement: " + importStatementText);
                return;
            }
            
            LOG.debug("Generated PSI Import Statement: " + newImportStmt.getText());

            // Fix for error #3: The addImport signature we tried doesn't exist either
            // Let's directly add the import statement to the file
            PsiElement anchor = AddImportHelper.getFileInsertPosition(file);
            if (anchor != null) {
                file.addBefore(newImportStmt, anchor);
                LOG.info("Successfully added import statement through manual insertion.");
            } else {
                // If we can't find a good position, add it to the beginning
                file.addAfter(newImportStmt, file.getFirstChild());
                LOG.info("Added import at the beginning of the file.");
            }

        } catch (Exception e) {
            // Catch broader exceptions during PSI generation or adding
            LOG.error("Error generating or adding import statement: " + importStatementText, e);
        }
    }

    // We don't need isAvailable/startInWriteAction from BaseIntentionAction
    // LocalQuickFixOnPsiElement handles availability check.
    // Write action is handled by invoke calling runWriteCommandAction.
    
     /**
     * Required for LocalQuickFixOnPsiElement, but we handle modifications in invoke.
     * Can potentially be used for preview.
     */
    @Override
    public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
        // The file itself needs to be writable to add imports.
        return currentFile;
    }
} 