package com.launcher.services;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Properties;

public class SettingsService {
    private static SettingsService instance;
    private final Properties props = new Properties();
    private final File settingsFile = new File(
            System.getProperty("user.home") + "/Documents/AntigravityLauncher/launcher_settings.properties");

    private SettingsService() {
        load();
    }

    public static SettingsService getInstance() {
        if (instance == null) {
            instance = new SettingsService();
        }
        return instance;
    }

    private void load() {
        try {
            if (settingsFile.exists()) {
                props.load(new FileReader(settingsFile));
            }
        } catch (Exception e) {
            LogService.error("Failed to load settings from " + settingsFile.getAbsolutePath(), e);
        }
    }

    public void save() {
        try {
            settingsFile.getParentFile().mkdirs();
            props.store(new FileWriter(settingsFile), "Antigravity Launcher Settings");
        } catch (Exception e) {
            LogService.error("Failed to save settings to " + settingsFile.getAbsolutePath(), e);
        }
    }

    public String getJavaPath() {
        return props.getProperty("javaPath", "java");
    }

    public void setJavaPath(String path) {
        props.setProperty("javaPath", path);
        save();
    }

    public int getRam() {
        return Integer.parseInt(props.getProperty("ram", "4096"));
    }

    public void setRam(int ram) {
        props.setProperty("ram", String.valueOf(ram));
        save();
    }

    public String getLastVersionId() {
        return props.getProperty("lastVersionId");
    }

    public void setLastVersionId(String versionId) {
        props.setProperty("lastVersionId", versionId);
        save();
    }

    public String getRepoUrl() {
        return props.getProperty("repoUrl", "https://modpack-server.vercel.app/modpacks.json");
    }

    public void setRepoUrl(String url) {
        props.setProperty("repoUrl", url);
        save();
    }

    public int getResolutionWidth() {
        return Integer.parseInt(props.getProperty("resolutionWidth", "854"));
    }

    public void setResolutionWidth(int width) {
        props.setProperty("resolutionWidth", String.valueOf(width));
        save();
    }

    public int getResolutionHeight() {
        return Integer.parseInt(props.getProperty("resolutionHeight", "480"));
    }

    public void setResolutionHeight(int height) {
        props.setProperty("resolutionHeight", String.valueOf(height));
        save();
    }

    public boolean isFullScreen() {
        return Boolean.parseBoolean(props.getProperty("fullScreen", "false"));
    }

    public void setFullScreen(boolean fullScreen) {
        props.setProperty("fullScreen", String.valueOf(fullScreen));
        save();
    }

    public boolean isAutoClose() {
        return Boolean.parseBoolean(props.getProperty("autoClose", "false"));
    }

    public void setAutoClose(boolean autoClose) {
        props.setProperty("autoClose", String.valueOf(autoClose));
        save();
    }
}
