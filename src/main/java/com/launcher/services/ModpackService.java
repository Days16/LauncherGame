package com.launcher.services;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import com.launcher.util.Constants;

public class ModpackService {

    public enum ModpackType {
        CURSEFORGE,
        MODRINTH,
        MULTIMC,
        TECHNIC,
        UNKNOWN
    }

    public static class ModpackInfo {
        public String name;
        public String minecraftVersion;
        public String modloader; // "forge", "fabric", "quilt", "neoforge"
        public String modloaderVersion;
    }

    public ModpackInfo analyzeModpack(File file) {
        ModpackInfo info = new ModpackInfo();
        info.name = file.getName().replace(".zip", "").replace(".mrpack", "");
        LogService.info("Analyzing modpack: " + file.getName());

        try (ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            ZipEntry manifestEntry = null;
            ZipEntry modrinthEntry = null;

            // Search for manifest files anywhere in the zip
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace("\\", "/"); // Normalize

                // We want the file itself, not a directory
                if (entry.isDirectory())
                    continue;

                // Check for manifest.json (CurseForge)
                // We prefer the one at root, but if not, take the first one found
                if (name.endsWith("manifest.json")) {
                    // If we haven't found one yet, or if this one is at root (shorter path), take
                    // it
                    if (manifestEntry == null
                            || (name.equals("manifest.json") && !manifestEntry.getName().equals("manifest.json"))) {
                        manifestEntry = entry;
                    }
                }

                // Check for modrinth.index.json (Modrinth)
                if (name.endsWith("modrinth.index.json")) {
                    if (modrinthEntry == null || (name.equals("modrinth.index.json")
                            && !modrinthEntry.getName().equals("modrinth.index.json"))) {
                        modrinthEntry = entry;
                    }
                }
            }

            // Process CurseForge manifest
            if (manifestEntry != null) {
                LogService.info("Found manifest.json: " + manifestEntry.getName());
                try (java.io.InputStream is = zipFile.getInputStream(manifestEntry);
                        java.io.InputStreamReader reader = new java.io.InputStreamReader(is)) {
                    com.google.gson.JsonObject json = new com.google.gson.Gson().fromJson(reader,
                            com.google.gson.JsonObject.class);

                    if (json.has("minecraft")) {
                        com.google.gson.JsonObject mc = json.getAsJsonObject("minecraft");
                        info.minecraftVersion = mc.get("version").getAsString();
                        LogService.info("Minecraft Version: " + info.minecraftVersion);

                        if (mc.has("modLoaders")) {
                            com.google.gson.JsonArray loaders = mc.getAsJsonArray("modLoaders");
                            if (loaders.size() > 0) {
                                com.google.gson.JsonObject loader = loaders.get(0).getAsJsonObject();
                                String id = loader.get("id").getAsString().toLowerCase();
                                LogService.info("Modloader ID: " + id);
                                // Format: forge-x.y.z or fabric-x.y.z or neoforge-x.y.z
                                if (id.startsWith("forge-")) {
                                    info.modloader = "forge";
                                    info.modloaderVersion = id.substring(6);
                                } else if (id.startsWith("fabric-")) {
                                    info.modloader = "fabric";
                                    info.modloaderVersion = id.substring(7);
                                } else if (id.startsWith("neoforge-")) {
                                    info.modloader = "neoforge";
                                    info.modloaderVersion = id.substring(9);
                                } else if (id.startsWith("quilt-")) {
                                    info.modloader = "quilt";
                                    info.modloaderVersion = id.substring(6);
                                } else {
                                    // Fallback: try to guess or just use the ID
                                    info.modloader = id;
                                }
                            }
                        }
                    }
                    if (json.has("name")) {
                        info.name = json.get("name").getAsString();
                    }
                    return info;
                }
            }

            // Process Modrinth index
            if (modrinthEntry != null) {
                LogService.info("Found modrinth.index.json: " + modrinthEntry.getName());
                try (java.io.InputStream is = zipFile.getInputStream(modrinthEntry);
                        java.io.InputStreamReader reader = new java.io.InputStreamReader(is)) {
                    com.google.gson.JsonObject json = new com.google.gson.Gson().fromJson(reader,
                            com.google.gson.JsonObject.class);

                    if (json.has("dependencies")) {
                        com.google.gson.JsonObject deps = json.getAsJsonObject("dependencies");
                        LogService.info("Dependencies: " + deps.toString());
                        if (deps.has("minecraft")) {
                            info.minecraftVersion = deps.get("minecraft").getAsString();
                        }
                        if (deps.has("fabric-loader")) {
                            info.modloader = "fabric";
                            info.modloaderVersion = deps.get("fabric-loader").getAsString();
                        } else if (deps.has("forge")) {
                            info.modloader = "forge";
                            info.modloaderVersion = deps.get("forge").getAsString();
                        } else if (deps.has("quilt-loader")) {
                            info.modloader = "quilt";
                            info.modloaderVersion = deps.get("quilt-loader").getAsString();
                        }
                    }
                    if (json.has("name")) {
                        info.name = json.get("name").getAsString();
                    }
                    LogService.info(
                            "Analysis Result - Loader: " + info.modloader + ", Version: " + info.modloaderVersion);
                    return info;
                }
            }

        } catch (Exception e) {
            LogService.error("Failed to analyze modpack", e);
            e.printStackTrace();
        }

        return info;
    }

    public ModpackType detectModpackType(File file) {
        if (!file.getName().endsWith(".zip") && !file.getName().endsWith(".mrpack")) {
            return ModpackType.UNKNOWN;
        }

        try (ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.equals("manifest.json")) {
                    return ModpackType.CURSEFORGE;
                }
                if (name.equals("modrinth.index.json")) {
                    return ModpackType.MODRINTH;
                }
                if (name.equals("instance.cfg") || name.equals("mmc-pack.json")) {
                    return ModpackType.MULTIMC;
                }
                if (name.contains("bin/modpack.jar")) {
                    return ModpackType.TECHNIC;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ModpackType.UNKNOWN;
    }

    public java.util.List<VersionInfo> getModpacksAsVersions() {
        java.util.List<VersionInfo> modpacks = new java.util.ArrayList<>();
        File modpacksDir = new File(Constants.GAME_DIR + "/modpacks");

        if (modpacksDir.exists() && modpacksDir.isDirectory()) {
            File[] files = modpacksDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        // Only add if the modpack has a valid JSON file
                        File jsonFile = new File(file, file.getName() + ".json");
                        if (jsonFile.exists()) {
                            modpacks.add(new VersionInfo(file.getName(), "modpack", null));
                        }
                    }
                }
            }
        }
        return modpacks;
    }

    public static class ModpackFile {
        public String path;
        public java.util.List<String> downloads;
        public int fileSize;
        public java.util.Map<String, String> hashes;
    }

    public java.util.List<ModpackFile> getModpackFiles(File file) {
        java.util.List<ModpackFile> files = new java.util.ArrayList<>();
        try (ZipFile zipFile = new ZipFile(file)) {
            ZipEntry modrinthEntry = zipFile.getEntry("modrinth.index.json");
            if (modrinthEntry != null) {
                try (java.io.InputStream is = zipFile.getInputStream(modrinthEntry);
                        java.io.InputStreamReader reader = new java.io.InputStreamReader(is)) {
                    com.google.gson.JsonObject json = new com.google.gson.Gson().fromJson(reader,
                            com.google.gson.JsonObject.class);
                    if (json.has("files")) {
                        com.google.gson.JsonArray filesArray = json.getAsJsonArray("files");
                        for (com.google.gson.JsonElement element : filesArray) {
                            com.google.gson.JsonObject obj = element.getAsJsonObject();
                            ModpackFile mpFile = new ModpackFile();
                            mpFile.path = obj.get("path").getAsString();
                            mpFile.downloads = new java.util.ArrayList<>();
                            for (com.google.gson.JsonElement url : obj.getAsJsonArray("downloads")) {
                                mpFile.downloads.add(url.getAsString());
                            }
                            files.add(mpFile);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return files;
    }
}
