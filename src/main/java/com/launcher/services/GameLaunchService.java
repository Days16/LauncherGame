package com.launcher.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.launcher.ui.DashboardView;
import com.launcher.services.JavaRuntimeService;

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
    private final String gameDir = System.getProperty("user.home") + "/Documents/MinecraftLauncher"; // New path in
                                                                                                     // Documents
    private final String assetsDir = gameDir + "/assets";
    private final String librariesDir = gameDir + "/libraries";
    private final String versionsDir = gameDir + "/versions";

    public CompletableFuture<Process> launchGame(VersionInfo version, SessionService session,
            DashboardView.LaunchCallback callback) {
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
                    versionFolder = new File(System.getenv("APPDATA") + "/.minecraft/modpacks", version.getId());
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

                JsonObject versionJson = gson.fromJson(new FileReader(versionJsonFile), JsonObject.class);

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
                File nativesDir = new File(versionFolder, "natives");
                nativesDir.mkdirs();

                if (libraries != null) {
                    for (JsonElement libElement : libraries) {
                        JsonObject lib = libElement.getAsJsonObject();

                        // Check rules
                        if (!checkRules(lib))
                            continue;

                        JsonObject downloadsObj = lib.getAsJsonObject("downloads");
                        if (downloadsObj != null) {
                            // Artifact (Jar)
                            if (downloadsObj.has("artifact")) {
                                JsonObject artifact = downloadsObj.getAsJsonObject("artifact");
                                String path = artifact.get("path").getAsString();
                                String url = artifact.get("url").getAsString();
                                File libFile = new File(librariesDir, path);

                                if (!libFile.exists()) {
                                    downloadFile(url, libFile);
                                }
                                classpath.add(libFile.getAbsolutePath());
                            }

                            // Classifiers (Natives)
                            if (downloadsObj.has("classifiers")) {
                                JsonObject classifiers = downloadsObj.getAsJsonObject("classifiers");
                                if (classifiers.has("natives-windows")) {
                                    JsonObject nativeArtifact = classifiers.getAsJsonObject("natives-windows");
                                    String path = nativeArtifact.get("path").getAsString();
                                    String url = nativeArtifact.get("url").getAsString();
                                    File nativeFile = new File(librariesDir, path);

                                    if (!nativeFile.exists()) {
                                        downloadFile(url, nativeFile);
                                    }

                                    // Extract
                                    extractNatives(nativeFile, nativesDir);
                                }
                            }
                        }
                    }
                }

                // Add client jar to classpath
                classpath.add(new File(versionFolder, version.getId() + ".jar").getAbsolutePath());

                // 5. Build Command
                callback.onStatusUpdate("Starting game...");
                String mainClass = versionJson.get("mainClass").getAsString();

                // Determine Java Version
                int javaMajorVersion = 8; // Default
                if (versionJson.has("javaVersion")) {
                    javaMajorVersion = versionJson.getAsJsonObject("javaVersion").get("majorVersion").getAsInt();
                }

                SettingsService settings = SettingsService.getInstance();
                String javaPath;

                // Use managed runtime if settings path is default or empty, otherwise use
                // settings
                String settingsPath = settings.getJavaPath();
                if (settingsPath == null || settingsPath.isEmpty() || settingsPath.equals("java")
                        || settingsPath.contains("jdk-17")) {
                    // Auto-resolve
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

                // Game Arguments
                command.add("--version");
                command.add(version.getId());
                command.add("--gameDir");
                command.add(gameDir);
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

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(new File(gameDir));
                pb.inheritIO();
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
        return allow;
    }

    private void downloadFile(String urlStr, File target) throws IOException {
        target.getParentFile().mkdirs();
        URL url = new URL(urlStr);
        try (BufferedInputStream in = new BufferedInputStream(url.openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(target)) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
    }

    private void extractNatives(File zipFile, File targetDir) {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".dll") || entry.getName().endsWith(".so")
                        || entry.getName().endsWith(".dylib")) {
                    File target = new File(targetDir, entry.getName());
                    if (!target.getParentFile().exists())
                        target.getParentFile().mkdirs();

                    try (FileOutputStream fos = new FileOutputStream(target)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
