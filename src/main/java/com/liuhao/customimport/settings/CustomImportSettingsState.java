package com.liuhao.customimport.settings;

import com.intellij.openapi.components.*;
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
    // List of directory paths where custom import format should be applied
    private List<String> specialDirectoriesList = new ArrayList<>();

    // Default settings
    public CustomImportSettingsState() {
        // Example: Add some default directories - you can add your specific directories here
        specialDirectoriesList.add("a/b/c");
        // Add an empty string if you want to handle top-level modules
        specialDirectoriesList.add("");
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
        this.specialDirectoriesList = specialDirectoriesList;
    }

    /**
     * Adds a new directory to the list
     */
    public void addSpecialDirectory(@NotNull String directoryPath) {
        if (!specialDirectoriesList.contains(directoryPath)) {
            specialDirectoriesList.add(directoryPath);
        }
    }

    /**
     * Removes a directory from the list
     */
    public void removeSpecialDirectory(@NotNull String directoryPath) {
        specialDirectoriesList.remove(directoryPath);
    }
} 