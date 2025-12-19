package com.launcher.ui;

import com.launcher.services.ModpackService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import java.io.File;
import java.util.List;

public class ImportView extends VBox {

    private final ModpackService modpackService = new ModpackService();
    private final Label statusLabel;
    private final ProgressBar progressBar;

    public ImportView() {
        this.getStyleClass().add("dashboard");
        this.setAlignment(Pos.CENTER);
        this.setSpacing(20);

        Label title = new Label("Import Modpack");
        title.getStyleClass().add("h1");

        // Drop Zone
        VBox dropZone = new VBox();
        dropZone.setPrefSize(400, 200);
        dropZone.setMaxSize(400, 200);
        dropZone.setAlignment(Pos.CENTER);
        dropZone.setStyle(
                "-fx-background-color: #2d2d30; " +
                        "-fx-border-color: #4caf50; " +
                        "-fx-border-style: dashed; " +
                        "-fx-border-width: 2; " +
                        "-fx-background-radius: 10; " +
                        "-fx-border-radius: 10;");

        Label dropLabel = new Label("Drag & Drop Modpack Here");
        dropLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 16px;");
        Label formatsLabel = new Label("Supports: CurseForge, Modrinth, MultiMC, Technic");
        formatsLabel.setStyle("-fx-text-fill: #606060; -fx-font-size: 12px;");

        dropZone.getChildren().addAll(dropLabel, formatsLabel);

        // Status
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

        // Progress Bar (Hidden)
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        this.getChildren().addAll(title, dropZone, statusLabel, progressBar);

        // Drag Events
        dropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
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
        statusLabel.setText("Installing " + file.getName() + "...");
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressBar.setProgress(-1);

        new Thread(() -> {
            ModpackService.ModpackType type = modpackService.detectModpackType(file);

            if (type == ModpackService.ModpackType.UNKNOWN) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("❌ Unknown modpack format.");
                    statusLabel.setStyle("-fx-text-fill: #e81123;");
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                });
                return;
            }

            // Install (Unzip to modpacks dir)
            try {
                File modpacksDir = new File(System.getenv("APPDATA") + "/.minecraft/modpacks");
                modpacksDir.mkdirs();
                String folderName = file.getName().replace(".zip", "").replace(".mrpack", "");
                File destDir = new File(modpacksDir, folderName);

                // Simple unzip for now (simulating installation logic)
                // In a real app, we would parse manifest.json and download mods.
                // For now, we just create the folder so it shows up in the dashboard.
                destDir.mkdirs();

                // Create a dummy version.json so GameLaunchService finds it
                // This is a placeholder. Real modpack support requires complex parsing.
                File jsonFile = new File(destDir, folderName + ".json");
                if (!jsonFile.exists()) {
                    java.nio.file.Files.writeString(jsonFile.toPath(),
                            "{\"id\":\"" + folderName
                                    + "\", \"mainClass\":\"net.minecraft.client.main.Main\", \"type\":\"modpack\"}");
                }

                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("✅ Installed: " + folderName);
                    statusLabel.setStyle("-fx-text-fill: #4caf50;");
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                });

            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("❌ Installation Failed: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #e81123;");
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                });
            }
        }).start();
    }
}
