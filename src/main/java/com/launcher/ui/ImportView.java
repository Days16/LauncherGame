package com.launcher.ui;

import com.launcher.services.ModpackService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import java.io.File;
import java.util.List;

public class ImportView extends VBox {

    private final ModpackService modpackService = new ModpackService();
    private final Label statusLabel;
    private final ProgressBar progressBar;
    private final String gameDir = System.getProperty("user.home") + "/Documents/MinecraftLauncher";

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
            ModpackService.ModpackType type = modpackService.detectModpackType(file);

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

                File jsonFile = new File(destDir, folderName + ".json");
                if (!jsonFile.exists()) {
                    java.nio.file.Files.writeString(jsonFile.toPath(),
                            "{\"id\":\"" + folderName
                                    + "\", \"mainClass\":\"net.minecraft.client.main.Main\", \"type\":\"modpack\"}");
                }

                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("INSTALLED: " + folderName.toUpperCase());
                    statusLabel.setStyle("-fx-text-fill: #10b981;");
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                });

            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("INSTALLATION FAILED: " + e.getMessage().toUpperCase());
                    statusLabel.setStyle("-fx-text-fill: #ef4444;");
                    progressBar.setVisible(false);
                    progressBar.setManaged(false);
                });
            }
        }).start();
    }
}
