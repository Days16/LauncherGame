package com.launcher.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class RemoteModpackService {

    private final Gson gson = new Gson();
    private final String modpacksDir = System.getProperty("user.home") + "/Documents/MinecraftLauncher/modpacks";

    public static class RemoteModpack {
        public String id;
        public String name;
        public String version;
        public String minecraftVersion;
        public String description;
        public String downloadUrl;
        public String iconUrl;

        public RemoteModpack(String id, String name, String version, String minecraftVersion, String description,
                String downloadUrl, String iconUrl) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.minecraftVersion = minecraftVersion;
            this.description = description;
            this.downloadUrl = downloadUrl;
            this.iconUrl = iconUrl;
        }
    }

    public CompletableFuture<List<RemoteModpack>> fetchRemoteModpacks(String repoUrl) {
        return CompletableFuture.supplyAsync(() -> {
            List<RemoteModpack> modpacks = new ArrayList<>();
            if (repoUrl == null || repoUrl.isEmpty())
                return modpacks;

            try {
                // Handle potential spaces in repoUrl
                String encodedUrl = repoUrl.replace(" ", "%20");
                URL url = java.net.URI.create(encodedUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    JsonObject json = gson.fromJson(reader, JsonObject.class);
                    JsonArray array = json.getAsJsonArray("modpacks");
                    for (JsonElement element : array) {
                        JsonObject obj = element.getAsJsonObject();
                        modpacks.add(new RemoteModpack(
                                obj.get("id").getAsString(),
                                obj.get("name").getAsString(),
                                obj.get("version").getAsString(),
                                obj.has("minecraftVersion") ? obj.get("minecraftVersion").getAsString() : "1.20.1",
                                obj.get("description").getAsString(),
                                obj.get("downloadUrl").getAsString(),
                                obj.has("iconUrl") ? obj.get("iconUrl").getAsString() : null));
                    }
                }
            } catch (Exception e) {
                LogService.error("Failed to fetch remote modpacks from " + repoUrl, e);
            }
            return modpacks;
        });
    }

    public CompletableFuture<Boolean> installModpack(RemoteModpack modpack,
            java.util.function.Consumer<String> statusCallback) {
        return CompletableFuture.supplyAsync(() -> {
            File destDir = new File(modpacksDir, modpack.id);
            File zipFile = new File(modpacksDir, modpack.id + ".zip");
            boolean success = false;

            try {
                statusCallback.accept("Downloading " + modpack.name + "...");
                if (destDir.exists()) {
                    // Clean up old failed installation if any
                    deleteDirectory(destDir);
                }
                destDir.mkdirs();

                downloadFile(modpack.downloadUrl, zipFile);

                statusCallback.accept("Extracting...");
                unzip(zipFile, destDir);

                statusCallback.accept("Fetching base version config (" + modpack.minecraftVersion + ")...");
                // Fetch base version JSON from Mojang
                String baseVersionUrl = fetchBaseVersionUrl(modpack.minecraftVersion);
                File jsonFile = new File(destDir, modpack.id + ".json");

                if (baseVersionUrl != null) {
                    downloadFile(baseVersionUrl, jsonFile);
                    // Modify the ID in the JSON to match the modpack ID
                    try (FileReader reader = new FileReader(jsonFile)) {
                        JsonObject versionJson = gson.fromJson(reader, JsonObject.class);
                        versionJson.addProperty("id", modpack.id);
                        versionJson.addProperty("type", "modpack");
                        Files.writeString(jsonFile.toPath(), gson.toJson(versionJson));
                    }
                } else {
                    // Fallback to dummy if Mojang fetch fails
                    Files.writeString(jsonFile.toPath(),
                            "{\"id\":\"" + modpack.id
                                    + "\", \"mainClass\":\"net.minecraft.client.main.Main\", \"type\":\"modpack\", \"libraries\":[]}");
                }

                LogService.info("Successfully installed modpack: " + modpack.name);
                success = true;
                statusCallback.accept("Installed!");
                return true;
            } catch (Exception e) {
                LogService.error("Failed to install modpack: " + modpack.name, e);
                statusCallback.accept("Failed: " + e.getMessage());
                return false;
            } finally {
                if (zipFile.exists()) {
                    zipFile.delete();
                }
                if (!success && destDir.exists()) {
                    // Cleanup failed installation
                    deleteDirectory(destDir);
                }
            }
        });
    }

    private String fetchBaseVersionUrl(String versionId) {
        try {
            URL url = new URL("https://piston-meta.mojang.com/mc/game/version_manifest.json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                JsonArray versions = json.getAsJsonArray("versions");
                for (JsonElement e : versions) {
                    JsonObject v = e.getAsJsonObject();
                    if (v.get("id").getAsString().equals(versionId)) {
                        return v.get("url").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void deleteDirectory(File directory) {
        File[] allContents = directory.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directory.delete();
    }

    private void downloadFile(String urlStr, File target) throws IOException {
        // Handle potential spaces in urlStr
        String encodedUrl = urlStr.replace(" ", "%20");
        URL url = java.net.URI.create(encodedUrl).toURL();
        try (BufferedInputStream in = new BufferedInputStream(url.openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(target)) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (Exception e) {
            throw new IOException("Failed to download from " + urlStr + ": " + e.getMessage(), e);
        }
    }

    private void unzip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = new File(destDir, zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }
    }
}
