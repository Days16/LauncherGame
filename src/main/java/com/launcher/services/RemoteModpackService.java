package com.launcher.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.launcher.util.Constants;

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
    private final String modpacksDir = Constants.GAME_DIR + "/modpacks";

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
                    deleteDirectory(destDir);
                }
                destDir.mkdirs();

                downloadFile(modpack.downloadUrl, zipFile);

                statusCallback.accept("Extracting...");

                // Use robust extraction logic
                com.launcher.services.ModpackService.ModpackType type = new com.launcher.services.ModpackService()
                        .detectModpackType(zipFile);
                extractModpack(zipFile, destDir, type);

                statusCallback.accept("Analyzing...");
                // Analyze modpack to get version info
                com.launcher.services.ModpackService modpackService = new com.launcher.services.ModpackService();
                com.launcher.services.ModpackService.ModpackInfo info = modpackService.analyzeModpack(zipFile);

                File jsonFile = new File(destDir, modpack.id + ".json");
                String jsonContent = null;

                if ("fabric".equalsIgnoreCase(info.modloader) && info.minecraftVersion != null
                        && info.modloaderVersion != null) {
                    String fabricUrl = "https://meta.fabricmc.net/v2/versions/loader/" + info.minecraftVersion
                            + "/" + info.modloaderVersion + "/profile/json";
                    statusCallback.accept("Downloading Fabric profile...");
                    downloadFile(fabricUrl, jsonFile);

                    // Read and modify to ensure ID is correct
                    JsonObject json;
                    try (FileReader reader = new FileReader(jsonFile)) {
                        json = gson.fromJson(reader, JsonObject.class);
                    }
                    json.addProperty("id", modpack.id);
                    json.addProperty("type", "modpack");
                    if (!json.has("inheritsFrom")) {
                        json.addProperty("inheritsFrom", info.minecraftVersion);
                    }
                    Files.writeString(jsonFile.toPath(), gson.toJson(json));
                    jsonContent = null;

                } else if ("forge".equalsIgnoreCase(info.modloader) || "neoforge".equalsIgnoreCase(info.modloader)) {
                    jsonContent = "{\"id\":\"" + modpack.id + "\", \"inheritsFrom\":\"" + info.minecraftVersion
                            + "\", \"type\":\"modpack\", \"modloader\":\"" + info.modloader
                            + "\", \"modloaderVersion\":\""
                            + info.modloaderVersion + "\"}";
                } else {
                    // Fallback to Vanilla or check for mods
                    String version = info.minecraftVersion != null ? info.minecraftVersion : modpack.minecraftVersion;
                    jsonContent = "{\"id\":\"" + modpack.id + "\", \"inheritsFrom\":\"" + version
                            + "\", \"type\":\"modpack\"}";
                }

                if (jsonContent != null) {
                    Files.writeString(jsonFile.toPath(), jsonContent);
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
                    deleteDirectory(destDir);
                }
            }
        });
    }

    private void extractModpack(File zipFile, File destDir, com.launcher.services.ModpackService.ModpackType type)
            throws Exception {
        // 1. Analyze Zip Structure to find Root Prefix
        String rootPrefix = null;
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipFile)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zf.entries();
            boolean first = true;

            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace("\\", "/");
                if (entry.isDirectory())
                    continue;

                if (name.startsWith("__MACOSX") || name.endsWith(".DS_Store"))
                    continue;

                int slashIdx = name.indexOf('/');
                if (slashIdx == -1) {
                    rootPrefix = "";
                    break;
                }

                String topLevel = name.substring(0, slashIdx + 1);

                if (first) {
                    rootPrefix = topLevel;
                    first = false;
                } else {
                    if (!name.startsWith(rootPrefix)) {
                        rootPrefix = "";
                        break;
                    }
                }
            }
        } catch (Exception e) {
            rootPrefix = "";
        }

        // 2. Extract Files
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().replace("\\", "/");

                if (entryName.startsWith("__MACOSX") || entryName.endsWith(".DS_Store"))
                    continue;

                if (rootPrefix != null && !rootPrefix.isEmpty() && entryName.startsWith(rootPrefix)) {
                    entryName = entryName.substring(rootPrefix.length());
                }

                if (entryName.isEmpty())
                    continue;

                if (type == com.launcher.services.ModpackService.ModpackType.MODRINTH) {
                    if (!entryName.startsWith("overrides/"))
                        continue;
                    entryName = entryName.substring("overrides/".length());
                } else if (type == com.launcher.services.ModpackService.ModpackType.CURSEFORGE) {
                    if (entryName.startsWith("overrides/")) {
                        entryName = entryName.substring("overrides/".length());
                    }
                }

                if (entryName.isEmpty())
                    continue;
                if (entryName.equals("manifest.json") || entryName.equals("modrinth.index.json"))
                    continue;

                File targetFile = new File(destDir, entryName);

                if (entry.isDirectory()) {
                    targetFile.mkdirs();
                } else {
                    targetFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        // 3. Download Files (for Modrinth)
        if (type == com.launcher.services.ModpackService.ModpackType.MODRINTH) {
            com.launcher.services.ModpackService modpackService = new com.launcher.services.ModpackService();
            List<com.launcher.services.ModpackService.ModpackFile> files = modpackService.getModpackFiles(zipFile);
            for (com.launcher.services.ModpackService.ModpackFile mpFile : files) {
                if (!mpFile.downloads.isEmpty()) {
                    File target = new File(destDir, mpFile.path);
                    if (!target.exists()) {
                        target.getParentFile().mkdirs();
                        try {
                            downloadFile(mpFile.downloads.get(0), target);
                        } catch (Exception e) {
                            LogService.error("Failed to download mod: " + mpFile.path);
                        }
                    }
                }
            }
        }
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
        java.net.URL url = java.net.URI.create(encodedUrl).toURL();
        try (java.io.BufferedInputStream in = new java.io.BufferedInputStream(url.openStream());
                java.io.FileOutputStream fileOutputStream = new java.io.FileOutputStream(target)) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        } catch (Exception e) {
            throw new IOException("Failed to download from " + urlStr + ": " + e.getMessage(), e);
        }
    }
}
