package com.launcher.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.launcher.util.Constants;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class InstanceMetadataService {
    private static InstanceMetadataService instance;
    private final Gson gson = new Gson();
    private final File metadataFile = new File(Constants.GAME_DIR + "/instances.json");
    private Map<String, InstanceMetadata> metadata = new HashMap<>();

    public static class InstanceMetadata {
        public String customName;
        public String versionId;
        public String type;

        public InstanceMetadata(String customName, String versionId, String type) {
            this.customName = customName;
            this.versionId = versionId;
            this.type = type;
        }
    }

    private InstanceMetadataService() {
        load();
    }

    public static InstanceMetadataService getInstance() {
        if (instance == null) {
            instance = new InstanceMetadataService();
        }
        return instance;
    }

    private void load() {
        if (metadataFile.exists()) {
            try (FileReader reader = new FileReader(metadataFile)) {
                Type type = new TypeToken<Map<String, InstanceMetadata>>() {
                }.getType();
                metadata = gson.fromJson(reader, type);
                if (metadata == null) {
                    metadata = new HashMap<>();
                }
            } catch (Exception e) {
                LogService.error("Failed to load instance metadata", e);
                metadata = new HashMap<>();
            }
        }
    }

    private void save() {
        try {
            metadataFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(metadataFile)) {
                gson.toJson(metadata, writer);
            }
        } catch (Exception e) {
            LogService.error("Failed to save instance metadata", e);
        }
    }

    public void setInstanceName(String versionId, String customName, String type) {
        metadata.put(versionId, new InstanceMetadata(customName, versionId, type));
        save();
    }

    public String getInstanceName(String versionId) {
        InstanceMetadata meta = metadata.get(versionId);
        return meta != null ? meta.customName : versionId;
    }

    public void removeInstance(String versionId) {
        metadata.remove(versionId);
        save();
    }

    public boolean hasCustomName(String versionId) {
        return metadata.containsKey(versionId);
    }
}
