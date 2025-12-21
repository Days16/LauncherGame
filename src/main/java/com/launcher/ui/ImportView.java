package com.launcher.ui;

import com.launcher.services.ModpackService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import java.io.File;
import java.util.List;

public class ImportView extends VBox {

    private final ModpackService modpackService = new ModpackService();
    private final Label statusLabel;
    private final ProgressBar progressBar;
    private final String gameDir = com.launcher.util.Constants.GAME_DIR;

    public ImportView() {
        this.getStyleClass().add("dashboard");
        this.setAlignment(Pos.CENTER);
        this.setSpacing(30);
        this.setPadding(new Insets(40));

        Label title = new Label("IMPORT MODPACK");
        title.getStyleClass().add("h1");

        // --- DROP ZONE ---
        VBox dropZone = new VBox(15);
        dropZone.setPrefSize(500, 250);
        dropZone.setMaxSize(500, 250);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.getStyleClass().add("modpack-card");
        dropZone.setStyle(
                "-fx-border-color: rgba(16, 185, 129, 0.3); -fx-border-style: dashed; -fx-border-width: 2; -fx-border-radius: 20;");

        Label dropLabel = new Label("DRAG & DROP MODPACK HERE");
        dropLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");

        Label formatsLabel = new Label("Supports: CurseForge, Modrinth, MultiMC, Technic");
        formatsLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 12px;");

        dropZone.getChildren().addAll(dropLabel, formatsLabel);

        // Status
        statusLabel = new Label("READY TO IMPORT");
        statusLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 12px; -fx-font-weight: bold;");

        // Progress Bar
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        this.getChildren().addAll(title, dropZone, statusLabel, progressBar);

        // Drag Events
        dropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
                dropZone.setStyle(
                        "-fx-border-color: #10b981; -fx-border-style: dashed; -fx-border-width: 2; -fx-border-radius: 20; -fx-background-color: rgba(16, 185, 129, 0.05);");
            }
            event.consume();
        });

        dropZone.setOnDragExited(event -> {
            dropZone.setStyle(
                    "-fx-border-color: rgba(16, 185, 129, 0.3); -fx-border-style: dashed; -fx-border-width: 2; -fx-border-radius: 20;");
            event.consume();
        });

        dropZone.setOnDragDropped(event -> {
            boolean success = false;
            if (event.getDragboard().hasFiles()) {
                List<File> files = event.getDragboard().getFiles();
                if (!files.isEmpty()) {
                    handleFileDrop(files.get(0));
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void handleFileDrop(File file) {
        statusLabel.setText("INSTALLING " + file.getName().toUpperCase() + "...");
        statusLabel.setStyle("-fx-text-fill: #10b981;");
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressBar.setProgress(-1);

        new Thread(() -> {
            com.launcher.services.LogService.info("Starting import for: " + file.getAbsolutePath());
            ModpackService.ModpackType type = modpackService.detectModpackType(file);
            com.launcher.services.LogService.info("Detected type: " + type);

            if (type == ModpackService.ModpackType.UNKNOWN) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("UNKNOWN MODPACK FORMAT");
                    statusLabel.setStyle("-fx-text-fill: #ef4444;");
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                });
                return;
            }

            try {
                File modpacksDir = new File(gameDir + "/modpacks");
                modpacksDir.mkdirs();
                String folderName = file.getName().replace(".zip", "").replace(".mrpack", "");
                File destDir = new File(modpacksDir, folderName);
                destDir.mkdirs();

                // Extract modpack contents
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("EXTRACTING " + folderName.toUpperCase() + "...");
                });

                extractModpack(file, destDir, type);

                // Analyze modpack to get version info
                ModpackService.ModpackInfo info = modpackService.analyzeModpack(file);
                com.launcher.services.LogService
                        .info("Analyzed Info - MC: " + info.minecraftVersion + ", Loader: " + info.modloader);

                // Create JSON file
                File jsonFile = new File(destDir, folderName + ".json");

                // Logic to regenerate JSON if needed
                boolean needsUpdate = !jsonFile.exists();
                if (jsonFile.exists()) {
                    try {
                        String content = java.nio.file.Files.readString(jsonFile.toPath());
                        if (content.contains("\"mainClass\":\"net.minecraft.client.main.Main\"")
                                && info.modloader != null) {
                            needsUpdate = true;
                        }
                    } catch (Exception e) {
                        needsUpdate = true;
                    }
                }

                if (needsUpdate) {
                    com.launcher.services.LogService.info("Generating instance.json...");
                    String jsonContent = null;

                    if ("fabric".equalsIgnoreCase(info.modloader) && info.minecraftVersion != null
                            && info.modloaderVersion != null) {
                        String fabricUrl = "https://meta.fabricmc.net/v2/versions/loader/" + info.minecraftVersion
                                + "/" + info.modloaderVersion + "/profile/json";
                        statusLabel.setText("DOWNLOADING FABRIC PROFILE...");
                        com.launcher.services.LogService.info("Downloading Fabric profile from: " + fabricUrl);
                        downloadFile(fabricUrl, jsonFile);

                        // Read and modify to ensure ID is correct
                        com.google.gson.JsonObject json;
                        try (java.io.FileReader reader = new java.io.FileReader(jsonFile)) {
                            json = new com.google.gson.Gson().fromJson(reader, com.google.gson.JsonObject.class);
                        }
                        json.addProperty("id", folderName);
                        json.addProperty("type", "modpack");
                        if (!json.has("inheritsFrom")) {
                            json.addProperty("inheritsFrom", info.minecraftVersion);
                        }
                        java.nio.file.Files.writeString(jsonFile.toPath(), new com.google.gson.Gson().toJson(json));
                        jsonContent = null; // Already written

                    } else if ("forge".equalsIgnoreCase(info.modloader)
                            || "neoforge".equalsIgnoreCase(info.modloader)) {
                        com.launcher.services.LogService.info("Configuring for Forge/NeoForge...");
                        jsonContent = "{\"id\":\"" + folderName + "\", \"inheritsFrom\":\"" + info.minecraftVersion
                                + "\", \"type\":\"modpack\", \"modloader\":\"" + info.modloader
                                + "\", \"modloaderVersion\":\""
                                + info.modloaderVersion + "\"}";
                    } else {
                        // FALLBACK: Check if mods folder exists and has files
                        File modsDir = new File(destDir, "mods");
                        boolean hasFabricMod = false;

                        if (modsDir.exists() && modsDir.isDirectory() && modsDir.list().length > 0) {
                            com.launcher.services.LogService.warn(
                                    "Modloader not detected in manifest, but mods folder found. Checking for Fabric...");

                            File[] modFiles = modsDir.listFiles();
                            if (modFiles != null) {
                                for (File mod : modFiles) {
                                    if (mod.getName().endsWith(".jar")) {
                                        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(mod)) {
                                            if (zf.getEntry("fabric.mod.json") != null) {
                                                hasFabricMod = true;
                                                break;
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }
                                }
                            }
                        }

                        if (hasFabricMod) {
                            com.launcher.services.LogService
                                    .info("Detected fabric.mod.json in mods. Forcing Fabric.");
                            String loaderVer = "0.15.7"; // Hardcoded fallback
                            String fabricUrl = "https://meta.fabricmc.net/v2/versions/loader/"
                                    + info.minecraftVersion
                                    + "/" + loaderVer + "/profile/json";
                            downloadFile(fabricUrl, jsonFile);

                            // Fix ID
                            com.google.gson.JsonObject json;
                            try (java.io.FileReader reader = new java.io.FileReader(jsonFile)) {
                                json = new com.google.gson.Gson().fromJson(reader,
                                        com.google.gson.JsonObject.class);
                            }
                            json.addProperty("id", folderName);
                            json.addProperty("type", "modpack");
                            if (!json.has("inheritsFrom")) {
                                json.addProperty("inheritsFrom", info.minecraftVersion);
                            }
                            java.nio.file.Files.writeString(jsonFile.toPath(),
                                    new com.google.gson.Gson().toJson(json));
                            jsonContent = null;
                        } else {
                            com.launcher.services.LogService
                                    .warn("Unknown modloader and no Fabric mod detected. Defaulting to Vanilla.");
                            String version = info.minecraftVersion != null ? info.minecraftVersion
                                    : "latest-release";
                            jsonContent = "{\"id\":\"" + folderName + "\", \"inheritsFrom\":\"" + version
                                    + "\", \"type\":\"modpack\"}";
                        }
                    }

                    if (jsonContent != null) {
                        java.nio.file.Files.writeString(jsonFile.toPath(), jsonContent);
                    }
                }

                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("INSTALLED: " + folderName.toUpperCase());
                    statusLabel.setStyle("-fx-text-fill: #10b981;");
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                });

            } catch (Exception e) {
                e.printStackTrace();
                com.launcher.services.LogService.error("Import failed", e);
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("INSTALLATION FAILED: " + e.getMessage().toUpperCase());
                    statusLabel.setStyle("-fx-text-fill: #ef4444;");
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                });
            }
        }).start();
    }

    private void extractModpack(File zipFile, File destDir, ModpackService.ModpackType type) throws Exception {
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

                // Ignore Mac metadata
                if (name.startsWith("__MACOSX") || name.endsWith(".DS_Store"))
                    continue;

                // Determine the top-level folder for this entry
                int slashIdx = name.indexOf('/');
                if (slashIdx == -1) {
                    // File at root, so no common root folder possible
                    rootPrefix = "";
                    break;
                }

                String topLevel = name.substring(0, slashIdx + 1);

                if (first) {
                    rootPrefix = topLevel;
                    first = false;
                } else {
                    if (!name.startsWith(rootPrefix)) {
                        rootPrefix = ""; // Mixed roots or different top-level folder
                        break;
                    }
                }
            }
        } catch (Exception e) {
            com.launcher.services.LogService.error("Failed to analyze zip structure", e);
            rootPrefix = "";
        }

        if (rootPrefix != null && !rootPrefix.isEmpty()) {
            com.launcher.services.LogService.info("Detected common root folder in modpack: " + rootPrefix);
        } else {
            rootPrefix = "";
        }

        // 2. Extract Files
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName().replace("\\", "/"); // Normalize slashes

                if (entryName.startsWith("__MACOSX") || entryName.endsWith(".DS_Store")) {
                    continue;
                }

                // Strip root prefix if detected
                if (!rootPrefix.isEmpty() && entryName.startsWith(rootPrefix)) {
                    entryName = entryName.substring(rootPrefix.length());
                }

                if (entryName.isEmpty())
                    continue;

                // For Modrinth, only extract overrides (usually in "overrides" folder)
                // But if we stripped the root, "overrides" might now be at the top
                if (type == ModpackService.ModpackType.MODRINTH) {
                    if (!entryName.startsWith("overrides/")) {
                        continue;
                    }
                    // Strip "overrides/" prefix to place contents directly in instance root
                    entryName = entryName.substring("overrides/".length());
                } else if (type == ModpackService.ModpackType.CURSEFORGE) {
                    // CurseForge usually has "overrides" folder too
                    if (entryName.startsWith("overrides/")) {
                        entryName = entryName.substring("overrides/".length());
                    }
                    // If it's not in overrides, it might be a manifest or other metadata,
                    // or it might be a non-standard pack.
                    // If it's a "Server Pack", mods are at root (or under rootPrefix).
                    // Let's extract everything that isn't metadata to be safe,
                    // but prioritize "overrides" content mapping to root.
                }

                if (entryName.isEmpty())
                    continue;

                // Skip manifest/metadata files from being extracted as game files
                if (entryName.equals("manifest.json") || entryName.equals("modrinth.index.json")) {
                    continue;
                }

                File targetFile = new File(destDir, entryName);

                if (entry.isDirectory()) {
                    targetFile.mkdirs();
                } else {
                    targetFile.getParentFile().mkdirs();
                    try (java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile)) {
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
        if (type == ModpackService.ModpackType.MODRINTH) {
            List<ModpackService.ModpackFile> files = modpackService.getModpackFiles(zipFile);
            int total = files.size();
            int current = 0;

            for (ModpackService.ModpackFile mpFile : files) {
                current++;
                final int progress = current;
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("DOWNLOADING MODS (" + progress + "/" + total + ")...");
                });

                if (!mpFile.downloads.isEmpty()) {
                    File target = new File(destDir, mpFile.path);
                    if (!target.exists()) {
                        try {
                            downloadFile(mpFile.downloads.get(0), target);
                        } catch (Exception e) {
                            com.launcher.services.LogService.error("Failed to download mod: " + mpFile.path);
                        }
                    }
                }
            }
        }
    }

    private void downloadFile(String urlStr, File target) throws Exception {
        java.net.URL url = new java.net.URL(urlStr);
        try (java.io.InputStream in = url.openStream();
                java.io.FileOutputStream fos = new java.io.FileOutputStream(target)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) > 0) {
                fos.write(buffer, 0, len);
            }
        }
    }
}
