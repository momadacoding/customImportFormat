package com.liuhao.customimport.settings;

import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings state for the Custom Import plugin.
 * Stores a list of directory patterns where the custom import format should be applied.
 */
@State(
    name = "CustomImportSettings",
    storages = {@Storage("customImportSettings.xml")}
)
public class CustomImportSettingsState implements PersistentStateComponent<CustomImportSettingsState> {
    private static final Logger LOG = Logger.getInstance(CustomImportSettingsState.class);
    
    // List of directory paths where custom import format should be applied
    private List<String> specialDirectoriesList = new ArrayList<>();
    
    // List of source root patterns to search for when calculating qualified names
    private List<String> sourceRootPatterns = new ArrayList<>();

    // Default settings
    public CustomImportSettingsState() {
        // Example: Add some default directories - you can add your specific directories here
        specialDirectoriesList.add("a/b/c");
        // Add an empty string if you want to handle top-level modules
        specialDirectoriesList.add("");
        
        // Default source root patterns (fallback only - PyCharm's source roots are used first)
        // These are only used when PyCharm's source root detection fails
        sourceRootPatterns.add("Script/Python");
        sourceRootPatterns.add("src");
        sourceRootPatterns.add("source");
    }

    /**
     * Gets the instance of this state for the application
     */
    public static CustomImportSettingsState getInstance() {
        return ServiceManager.getService(CustomImportSettingsState.class);
    }

    @Nullable
    @Override
    public CustomImportSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull CustomImportSettingsState state) {
        LOG.info("Loading custom import settings: " + state.specialDirectoriesList);
        XmlSerializerUtil.copyBean(state, this);
    }

    /**
     * Gets the list of special directories where custom import should be applied
     */
    @NotNull
    public List<String> getSpecialDirectoriesList() {
        return specialDirectoriesList;
    }

    /**
     * Sets the list of special directories
     */
    public void setSpecialDirectoriesList(@NotNull List<String> specialDirectoriesList) {
        LOG.info("Updating special directories: " + specialDirectoriesList);
        this.specialDirectoriesList = specialDirectoriesList;
    }

    /**
     * Adds a new directory to the list
     */
    public void addSpecialDirectory(@NotNull String directoryPath) {
        if (!specialDirectoriesList.contains(directoryPath)) {
            specialDirectoriesList.add(directoryPath);
            LOG.info("Added special directory: " + directoryPath);
        }
    }

    /**
     * Removes a directory from the list
     */
    public void removeSpecialDirectory(@NotNull String directoryPath) {
        boolean removed = specialDirectoriesList.remove(directoryPath);
        if (removed) {
            LOG.info("Removed special directory: " + directoryPath);
        }
    }

    /**
     * Gets the list of source root patterns for qualified name calculation
     */
    @NotNull
    public List<String> getSourceRootPatterns() {
        return sourceRootPatterns;
    }

    /**
     * Sets the list of source root patterns
     */
    public void setSourceRootPatterns(@NotNull List<String> sourceRootPatterns) {
        LOG.info("Updating source root patterns: " + sourceRootPatterns);
        this.sourceRootPatterns = sourceRootPatterns;
    }

    /**
     * Adds a new source root pattern to the list
     */
    public void addSourceRootPattern(@NotNull String pattern) {
        if (!sourceRootPatterns.contains(pattern)) {
            sourceRootPatterns.add(pattern);
            LOG.info("Added source root pattern: " + pattern);
        }
    }

    /**
     * Removes a source root pattern from the list
     */
    public void removeSourceRootPattern(@NotNull String pattern) {
        boolean removed = sourceRootPatterns.remove(pattern);
        if (removed) {
            LOG.info("Removed source root pattern: " + pattern);
        }
    }
} 