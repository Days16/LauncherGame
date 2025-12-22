package com.launcher.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.launcher.util.Constants;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GameLaunchService {

    private final Gson gson = new Gson();
    private final String gameDir = Constants.GAME_DIR;
    private final String assetsDir = gameDir + "/assets";
    private final String librariesDir = gameDir + "/libraries";
    private final String versionsDir = gameDir + "/versions";

    public CompletableFuture<Process> launchGame(VersionInfo version, SessionService session,
            LaunchCallback callback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                LogService.info("Preparing to launch " + version.getId() + "...");
                callback.onStatusUpdate("Preparing to launch " + version.getId() + "...");

                // 1. Create Directories
                new File(gameDir).mkdirs();
                new File(assetsDir).mkdirs();
                new File(librariesDir).mkdirs();
                new File(versionsDir).mkdirs();

                // 2. Download Version JSON
                File versionFolder;
                if ("modpack".equals(version.getType())) {
                    versionFolder = new File(gameDir + "/modpacks", version.getId());
                } else {
                    versionFolder = new File(versionsDir, version.getId());
                }
                versionFolder.mkdirs();
                File versionJsonFile = new File(versionFolder, version.getId() + ".json");

                callback.onStatusUpdate("Fetching version info...");
                if (!versionJsonFile.exists()) {
                    if (version.getUrl() != null) {
                        downloadFile(version.getUrl(), versionJsonFile);
                    } else {
                        throw new IOException("Version JSON not found and no URL provided for: " + version.getId());
                    }
                }

                JsonObject versionJson;
                try (FileReader reader = new FileReader(versionJsonFile)) {
                    versionJson = gson.fromJson(reader, JsonObject.class);
                }

                // Handle Inheritance (inheritsFrom)
                if (versionJson.has("inheritsFrom")) {
                    String parentId = versionJson.get("inheritsFrom").getAsString();
                    File parentFolder = new File(versionsDir, parentId);
                    parentFolder.mkdirs();
                    File parentJsonFile = new File(parentFolder, parentId + ".json");

                    if (!parentJsonFile.exists()) {
                        LogService.info("Parent version " + parentId + " not found locally. Attempting to fetch...");
                        String parentUrl = fetchBaseVersionUrl(parentId);
                        if (parentUrl != null) {
                            downloadFile(parentUrl, parentJsonFile);
                        } else {
                            throw new IOException("Could not find URL for parent version: " + parentId
                                    + ". Check internet connection.");
                        }
                    }

                    if (parentJsonFile.exists()) {
                        JsonObject parentJson;
                        try (FileReader reader = new FileReader(parentJsonFile)) {
                            parentJson = gson.fromJson(reader, JsonObject.class);
                        }
                        if (!versionJson.has("mainClass"))
                            versionJson.add("mainClass", parentJson.get("mainClass"));
                        if (!versionJson.has("minecraftArguments"))
                            versionJson.add("minecraftArguments", parentJson.get("minecraftArguments"));
                        if (!versionJson.has("arguments"))
                            versionJson.add("arguments", parentJson.get("arguments"));
                        if (!versionJson.has("assetIndex"))
                            versionJson.add("assetIndex", parentJson.get("assetIndex"));
                        if (!versionJson.has("javaVersion"))
                            versionJson.add("javaVersion", parentJson.get("javaVersion"));

                        if (parentJson.has("libraries")) {
                            JsonArray parentLibs = parentJson.getAsJsonArray("libraries");
                            if (versionJson.has("libraries")) {
                                versionJson.getAsJsonArray("libraries").addAll(parentLibs);
                            } else {
                                versionJson.add("libraries", parentLibs);
                            }
                        }

                        if (!versionJson.has("downloads") && parentJson.has("downloads")) {
                            versionJson.add("downloads", parentJson.get("downloads"));
                        }
                    }
                }

                // 3. Download Client JAR
                callback.onStatusUpdate("Downloading game client...");
                JsonObject downloads = versionJson.getAsJsonObject("downloads");
                if (downloads != null && downloads.has("client")) {
                    JsonObject clientDownload = downloads.getAsJsonObject("client");
                    String clientUrl = clientDownload.get("url").getAsString();
                    File clientJar = new File(versionFolder, version.getId() + ".jar");
                    if (!clientJar.exists()) {
                        downloadFile(clientUrl, clientJar);
                    }
                }

                // 4. Download Libraries & Extract Natives
                callback.onStatusUpdate("Downloading libraries...");
                JsonArray libraries = versionJson.getAsJsonArray("libraries");
                List<String> classpath = new ArrayList<>();
                java.util.Set<String> addedArtifacts = new java.util.HashSet<>();
                File nativesDir = new File(versionFolder, "natives");
                nativesDir.mkdirs();

                if (libraries != null) {
                    for (JsonElement libElement : libraries) {
                        JsonObject lib = libElement.getAsJsonObject();

                        if (!checkRules(lib))
                            continue;

                        // Deduplication
                        if (lib.has("name")) {
                            String name = lib.get("name").getAsString();
                            String[] parts = name.split(":");
                            if (parts.length >= 2) {
                                String key = parts[0] + ":" + parts[1];
                                if (parts.length >= 4) {
                                    key += ":" + parts[3];
                                }
                                if (addedArtifacts.contains(key)) {
                                    continue;
                                }
                                addedArtifacts.add(key);
                            }
                        }

                        // Artifact (Jar)
                        JsonObject downloadsObj = lib.getAsJsonObject("downloads");
                        if (downloadsObj != null && downloadsObj.has("artifact")) {
                            JsonObject artifact = downloadsObj.getAsJsonObject("artifact");
                            String path = artifact.get("path").getAsString();
                            String url = artifact.get("url").getAsString();
                            File libFile = new File(librariesDir, path);

                            if (!libFile.exists()) {
                                downloadFile(url, libFile);
                            }
                            classpath.add(libFile.getAbsolutePath());
                        } else if (lib.has("name")) {
                            // Maven style
                            String name = lib.get("name").getAsString();
                            String[] parts = name.split(":");
                            if (parts.length >= 3) {
                                String domain = parts[0].replace(".", "/");
                                String artifactId = parts[1];
                                String libVersion = parts[2];
                                String path = domain + "/" + artifactId + "/" + libVersion + "/" + artifactId + "-"
                                        + libVersion + ".jar";

                                File libFile = new File(librariesDir, path);
                                if (!libFile.exists()) {
                                    String baseUrl = lib.has("url") ? lib.get("url").getAsString()
                                            : "https://repo1.maven.org/maven2/";
                                    if (!baseUrl.endsWith("/"))
                                        baseUrl += "/";
                                    try {
                                        downloadFile(baseUrl + path, libFile);
                                    } catch (IOException e) {
                                        if (!baseUrl.contains("repo1.maven.org")) {
                                            try {
                                                downloadFile("https://repo1.maven.org/maven2/" + path, libFile);
                                            } catch (IOException ex) {
                                                LogService.error("Failed to download library: " + name);
                                            }
                                        }
                                    }
                                }
                                if (libFile.exists()) {
                                    classpath.add(libFile.getAbsolutePath());

                                    if (parts.length >= 4) {
                                        String classifier = parts[3];
                                        if (classifier.contains("natives")) {
                                            LogService.info("Found Maven native library: " + name);
                                            extractNatives(libFile, nativesDir);
                                        }
                                    }
                                }
                            }
                        }

                        // Classifiers (Natives)
                        if (downloadsObj != null && downloadsObj.has("classifiers")) {
                            JsonObject classifiers = downloadsObj.getAsJsonObject("classifiers");
                            for (String key : classifiers.keySet()) {
                                if (key.contains("natives-windows")) {
                                    LogService.info("Found native classifier: " + key);
                                    JsonObject nativeArtifact = classifiers.getAsJsonObject(key);
                                    String path = nativeArtifact.get("path").getAsString();
                                    String url = nativeArtifact.get("url").getAsString();
                                    File nativeFile = new File(librariesDir, path);

                                    if (!nativeFile.exists()) {
                                        downloadFile(url, nativeFile);
                                    }
                                    extractNatives(nativeFile, nativesDir);
                                }
                            }
                        }
                    }
                }

                classpath.add(new File(versionFolder, version.getId() + ".jar").getAbsolutePath());

                // 4b. Download Assets
                if (versionJson.has("assetIndex")) {
                    callback.onStatusUpdate("Downloading assets...");
                    downloadAssets(versionJson.getAsJsonObject("assetIndex"), callback);
                }

                // Debug & Emergency Native Check
                LogService.info("Natives Directory: " + nativesDir.getAbsolutePath());
                String[] nativeFiles = nativesDir.list();
                boolean hasLwjgl = false;
                if (nativeFiles != null) {
                    LogService.info("Natives found: " + nativeFiles.length);
                    for (String f : nativeFiles) {
                        LogService.info(" - " + f);
                        if (f.equals("lwjgl.dll"))
                            hasLwjgl = true;
                    }
                }

                if (!hasLwjgl) {
                    LogService.warn("lwjgl.dll missing! Attempting emergency scan of classpath...");
                    for (String cpEntry : classpath) {
                        if (cpEntry.contains("natives") && cpEntry.contains("windows")) {
                            LogService.info("Emergency extracting from: " + cpEntry);
                            extractNatives(new File(cpEntry), nativesDir);
                        }
                    }
                }

                // 5. Build Command
                callback.onStatusUpdate("Starting game...");
                String mainClass = versionJson.get("mainClass").getAsString();

                int javaMajorVersion = 8;
                if (versionJson.has("javaVersion")) {
                    javaMajorVersion = versionJson.getAsJsonObject("javaVersion").get("majorVersion").getAsInt();
                }

                SettingsService settings = SettingsService.getInstance();
                String javaPath;
                String settingsPath = settings.getJavaPath();
                if (settingsPath == null || settingsPath.isEmpty() || settingsPath.equals("java")
                        || settingsPath.contains("jdk-17")) {
                    javaPath = new JavaRuntimeService().getJavaPath(javaMajorVersion,
                            status -> callback.onStatusUpdate(status));
                } else {
                    javaPath = settingsPath;
                }

                int ram = settings.getRam();

                List<String> command = new ArrayList<>();
                command.add(javaPath);
                command.add("-Xmx" + ram + "M");
                command.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
                command.add("-cp");
                command.add(String.join(File.pathSeparator, classpath));
                command.add(mainClass);

                command.add("--version");
                command.add(version.getId());
                command.add("--gameDir");
                if ("modpack".equals(version.getType())) {
                    command.add(versionFolder.getAbsolutePath());
                } else {
                    command.add(gameDir);
                }
                command.add("--assetsDir");
                command.add(assetsDir);

                if (versionJson.has("assetIndex")) {
                    command.add("--assetIndex");
                    command.add(versionJson.getAsJsonObject("assetIndex").get("id").getAsString());
                }

                command.add("--uuid");
                command.add(session.getUuid() != null ? session.getUuid() : "00000000-0000-0000-0000-000000000000");
                command.add("--accessToken");
                command.add(session.getAccessToken() != null ? session.getAccessToken() : "0");
                command.add("--username");
                command.add(session.getCurrentUser());
                command.add("--userType");
                command.add("msa");
                command.add("--userProperties");
                command.add("{}");

                command.add("--width");
                command.add(String.valueOf(settings.getResolutionWidth()));
                command.add("--height");
                command.add(String.valueOf(settings.getResolutionHeight()));
                if (settings.isFullScreen()) {
                    command.add("--fullscreen");
                }

                ProcessBuilder pb = new ProcessBuilder(command);
                File workingDir = "modpack".equals(version.getType()) ? versionFolder : new File(gameDir);
                pb.directory(workingDir);
                pb.inheritIO();

                LogService.info("Launch Command: " + String.join(" ", command));
                LogService.info("Working Directory: " + workingDir.getAbsolutePath());
                LogService.info("Natives Path Argument: " + "-Djava.library.path=" + nativesDir.getAbsolutePath());

                Process process = pb.start();

                callback.onStatusUpdate("Game running!");
                return process;

            } catch (Exception e) {
                LogService.error("Launch failed for " + version.getId(), e);
                callback.onStatusUpdate("Error: " + e.getMessage());
                return null;
            }
        });

    }

    public boolean isVersionInstalled(VersionInfo version) {
        File versionFolder;
        if ("modpack".equals(version.getType())) {
            versionFolder = new File(gameDir + "/modpacks", version.getId());
        } else {
            versionFolder = new File(versionsDir, version.getId());
        }
        File versionJsonFile = new File(versionFolder, version.getId() + ".json");
        File clientJar = new File(versionFolder, version.getId() + ".jar");

        // For modpacks, we just check the folder/json
        if ("modpack".equals(version.getType())) {
            return versionFolder.exists() && versionJsonFile.exists();
        }

        // For regular versions, we check both json and jar
        return versionJsonFile.exists() && clientJar.exists();
    }

    private boolean checkRules(JsonObject lib) {
        if (!lib.has("rules"))
            return true;
        JsonArray rules = lib.getAsJsonArray("rules");
        boolean allow = false;
        for (JsonElement ruleElement : rules) {
            JsonObject rule = ruleElement.getAsJsonObject();
            String action = rule.get("action").getAsString();
            if (rule.has("os")) {
                String osName = rule.getAsJsonObject("os").get("name").getAsString();
                if (osName.equals("windows")) {
                    allow = action.equals("allow");
                }
            } else {
                allow = action.equals("allow");
            }
        }
        if (!allow) {
            // LogService.debug("Skipping library due to rules: " + (lib.has("name") ?
            // lib.get("name").getAsString() : "unknown"));
        }
        return allow;
    }

    private void downloadFile(String urlStr, File target) throws IOException {
        target.getParentFile().mkdirs();
        URL url = new URL(urlStr);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        conn.setInstanceFollowRedirects(true);

        int responseCode = conn.getResponseCode();
        if (responseCode != java.net.HttpURLConnection.HTTP_OK) {
            throw new IOException("Server returned HTTP response code: " + responseCode + " for URL: " + urlStr);
        }

        try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
                FileOutputStream fileOutputStream = new FileOutputStream(target)) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
    }

    private void extractNatives(File zipFile, File targetDir) {
        LogService.info("Extracting natives from: " + zipFile.getName());
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".dll") || entry.getName().endsWith(".so")
                        || entry.getName().endsWith(".dylib")) {
                    // Flatten path: use only the filename, ignore directories in the zip
                    String fileName = new File(entry.getName()).getName();
                    File target = new File(targetDir, fileName);

                    LogService.info("Extracting native: " + fileName);

                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LogService.error("Failed to extract natives from " + zipFile.getName(), e);
            e.printStackTrace();
        }
    }

    private void downloadAssets(JsonObject assetIndex, LaunchCallback callback) throws IOException {
        String id = assetIndex.get("id").getAsString();
        String url = assetIndex.get("url").getAsString();
        File indexesDir = new File(assetsDir, "indexes");
        indexesDir.mkdirs();
        File indexFile = new File(indexesDir, id + ".json");

        if (!indexFile.exists()) {
            LogService.info("Downloading asset index: " + id);
            downloadFile(url, indexFile);
        }

        JsonObject indexJson;
        try (FileReader reader = new FileReader(indexFile)) {
            indexJson = gson.fromJson(reader, JsonObject.class);
        }

        if (indexJson.has("objects")) {
            JsonObject objects = indexJson.getAsJsonObject("objects");
            File objectsDir = new File(assetsDir, "objects");
            objectsDir.mkdirs();

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            int total = objects.size();
            java.util.concurrent.atomic.AtomicInteger current = new java.util.concurrent.atomic.AtomicInteger(0);

            LogService.info("Checking " + total + " assets...");

            // Batch processing to avoid too many open connections
            List<String> keys = new ArrayList<>(objects.keySet());
            int batchSize = 50;

            for (int i = 0; i < keys.size(); i += batchSize) {
                int end = Math.min(i + batchSize, keys.size());
                List<String> batch = keys.subList(i, end);

                for (String key : batch) {
                    JsonObject obj = objects.getAsJsonObject(key);
                    String hash = obj.get("hash").getAsString();
                    String prefix = hash.substring(0, 2);
                    File file = new File(objectsDir, prefix + "/" + hash);

                    if (!file.exists()) {
                        futures.add(CompletableFuture.runAsync(() -> {
                            try {
                                String downloadUrl = "https://resources.download.minecraft.net/" + prefix + "/" + hash;
                                downloadFile(downloadUrl, file);
                                int c = current.incrementAndGet();
                                if (c % 10 == 0) {
                                    callback.onStatusUpdate("Downloading assets (" + c + "/" + total + ")...");
                                }
                            } catch (IOException e) {
                                LogService.error("Failed to download asset: " + key, e);
                            }
                        }));
                    } else {
                        current.incrementAndGet();
                    }
                }

                // Wait for batch to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                futures.clear();
                callback.onStatusUpdate("Downloading assets (" + current.get() + "/" + total + ")...");
            }
        }
    }

    private String fetchBaseVersionUrl(String versionId) {
        try {
            URL url = new URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()))) {
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
            LogService.error("Failed to fetch version manifest", e);
        }
        return null;
    }
}
