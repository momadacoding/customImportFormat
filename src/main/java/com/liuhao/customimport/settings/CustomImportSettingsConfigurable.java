package com.liuhao.customimport.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.AddEditDeleteListPanel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings UI for the Custom Import plugin.
 */
public class CustomImportSettingsConfigurable implements Configurable {
    private CustomImportSettingsPanel myPanel;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Custom Import Patterns";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        myPanel = new CustomImportSettingsPanel();
        return myPanel;
    }

    @Override
    public boolean isModified() {
        if (myPanel == null) {
            return false;
        }
        CustomImportSettingsState settings = CustomImportSettingsState.getInstance();
        return !myPanel.getDirectories().equals(settings.getSpecialDirectoriesList());
    }

    @Override
    public void apply() throws ConfigurationException {
        if (myPanel != null) {
            CustomImportSettingsState settings = CustomImportSettingsState.getInstance();
            settings.setSpecialDirectoriesList(new ArrayList<>(myPanel.getDirectories()));
        }
    }

    @Override
    public void reset() {
        if (myPanel != null) {
            CustomImportSettingsState settings = CustomImportSettingsState.getInstance();
            myPanel.setDirectories(new ArrayList<>(settings.getSpecialDirectoriesList()));
        }
    }

    @Override
    public void disposeUIResources() {
        myPanel = null;
    }

    /**
     * Panel for the settings UI
     */
    private static class CustomImportSettingsPanel extends AddEditDeleteListPanel<String> {
        public CustomImportSettingsPanel() {
            super("Special directories for custom import format", new ArrayList<>());
            setToolTipText("<html>For directories listed here, Python imports will be in the format:<br>" +
                    "<code>import a.b.c.d as d</code> instead of <code>from a.b.c import d</code><br><br>" +
                    "Enter directory paths in the format: <code>a/b/c</code> (using forward slashes)<br>" +
                    "Use an empty string to apply to top-level modules</html>");
        }

        @Override
        protected String findItemToAdd() {
            return showEditDialog("");
        }

        @Override
        protected String editSelectedItem(String item) {
            return showEditDialog(item);
        }

        private String showEditDialog(String initialValue) {
            Object result = JOptionPane.showInputDialog(
                    this,
                    "Enter directory path (use / as separator):",
                    "Directory Path",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    initialValue
            );
            
            return result != null ? result.toString() : initialValue;
        }

        public List<String> getDirectories() {
            // Convert Object[] to List<String>
            Object[] items = getListItems();
            List<String> result = new ArrayList<>(items.length);
            for (Object item : items) {
                if (item instanceof String) {
                    result.add((String) item);
                }
            }
            return result;
        }

        public void setDirectories(List<String> directories) {
            // Clear all elements from the list model
            DefaultListModel model = (DefaultListModel) myListModel;
            model.removeAllElements();
            
            // Add each directory using myListModel
            for (String directory : directories) {
                myListModel.addElement(directory);
            }
        }
    }
} 