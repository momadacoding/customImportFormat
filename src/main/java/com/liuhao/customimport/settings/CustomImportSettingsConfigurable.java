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
        return !myPanel.getDirectories().equals(settings.getSpecialDirectoriesList()) ||
               !myPanel.getSourceRootPatterns().equals(settings.getSourceRootPatterns());
    }

    @Override
    public void apply() throws ConfigurationException {
        if (myPanel != null) {
            CustomImportSettingsState settings = CustomImportSettingsState.getInstance();
            settings.setSpecialDirectoriesList(new ArrayList<>(myPanel.getDirectories()));
            settings.setSourceRootPatterns(new ArrayList<>(myPanel.getSourceRootPatterns()));
        }
    }

    @Override
    public void reset() {
        if (myPanel != null) {
            CustomImportSettingsState settings = CustomImportSettingsState.getInstance();
            myPanel.setDirectories(new ArrayList<>(settings.getSpecialDirectoriesList()));
            myPanel.setSourceRootPatterns(new ArrayList<>(settings.getSourceRootPatterns()));
        }
    }

    @Override
    public void disposeUIResources() {
        myPanel = null;
    }

    /**
     * Panel for the settings UI
     */
    private static class CustomImportSettingsPanel extends JPanel {
        private final DirectoryListPanel directoriesPanel;
        private final SourceRootListPanel sourceRootPanel;

        public CustomImportSettingsPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            
            directoriesPanel = new DirectoryListPanel();
            sourceRootPanel = new SourceRootListPanel();
            
            add(directoriesPanel);
            add(Box.createVerticalStrut(10));
            add(sourceRootPanel);
        }

        public List<String> getDirectories() {
            return directoriesPanel.getItems();
        }

        public void setDirectories(List<String> directories) {
            directoriesPanel.setItems(directories);
        }

        public List<String> getSourceRootPatterns() {
            return sourceRootPanel.getItems();
        }

        public void setSourceRootPatterns(List<String> patterns) {
            sourceRootPanel.setItems(patterns);
        }
    }

    private static class DirectoryListPanel extends AddEditDeleteListPanel<String> {
        public DirectoryListPanel() {
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

        public List<String> getItems() {
            Object[] items = getListItems();
            List<String> result = new ArrayList<>(items.length);
            for (Object item : items) {
                if (item instanceof String) {
                    result.add((String) item);
                }
            }
            return result;
        }

        public void setItems(List<String> items) {
            DefaultListModel model = (DefaultListModel) myListModel;
            model.removeAllElements();
            for (String item : items) {
                myListModel.addElement(item);
            }
        }
    }

    private static class SourceRootListPanel extends AddEditDeleteListPanel<String> {
        public SourceRootListPanel() {
            super("Source root patterns (fallback only)", new ArrayList<>());
            setToolTipText("<html><b>Note:</b> The plugin now uses PyCharm's configured source roots from Project Structure first.<br>" +
                    "These patterns are only used as fallback when PyCharm's source root detection fails.<br><br>" +
                    "To configure source roots properly, go to <b>File ??Project Structure ??Modules ??Sources</b><br>" +
                    "and mark your Python directories as 'Sources' (blue folder icon).<br><br>" +
                    "Fallback patterns: <code>Script/Python</code>, <code>src</code>, <code>source</code></html>");
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
                    "Enter source root pattern (use / as separator):",
                    "Source Root Pattern",
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    null,
                    initialValue
            );
            
            return result != null ? result.toString() : initialValue;
        }

        public List<String> getItems() {
            Object[] items = getListItems();
            List<String> result = new ArrayList<>(items.length);
            for (Object item : items) {
                if (item instanceof String) {
                    result.add((String) item);
                }
            }
            return result;
        }

        public void setItems(List<String> items) {
            DefaultListModel model = (DefaultListModel) myListModel;
            model.removeAllElements();
            for (String item : items) {
                myListModel.addElement(item);
            }
        }
    }
} 