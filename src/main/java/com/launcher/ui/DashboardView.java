package com.launcher.ui;

import com.launcher.services.GameLaunchService;
import com.launcher.services.InstanceMetadataService;
import com.launcher.services.SessionService;
import com.launcher.services.SettingsService;
import com.launcher.services.VersionInfo;
import com.launcher.services.VersionService;
import com.launcher.services.ModpackService;
import com.launcher.services.RemoteModpackService;
import com.launcher.util.Constants;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DashboardView extends VBox {

    private final Label statusLabel;
    private final SessionService session = SessionService.getInstance();
    private FlowPane instancesGrid;
    private VBox remoteList;

    public DashboardView() {
        this.getStyleClass().add("dashboard");
        this.setAlignment(Pos.TOP_CENTER);
        this.setSpacing(30);
        this.setPadding(new Insets(40));

        // --- HEADER SECTION ---
        HBox header = new HBox(20);
        header.setAlignment(Pos.CENTER);

        Label title = new Label("MY INSTANCES");
        title.getStyleClass().add("h1");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addInstanceBtn = new Button("+ Add Instance");
        addInstanceBtn.getStyleClass().add("play-button");
        addInstanceBtn.setStyle("-fx-font-size: 14px; -fx-padding: 10 20;");
        addInstanceBtn.setOnAction(e -> showAddInstanceDialog());

        header.getChildren().addAll(title, spacer, addInstanceBtn);

        // --- INSTANCES GRID ---
        ScrollPane instancesScroll = new ScrollPane();
        instancesScroll.setFitToWidth(true);
        instancesScroll.setStyle(
                "-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");

        instancesGrid = new FlowPane();
        instancesGrid.setHgap(20);
        instancesGrid.setVgap(20);
        instancesGrid.setAlignment(Pos.TOP_LEFT);
        instancesGrid.setPadding(new Insets(10));

        instancesScroll.setContent(instancesGrid);
        VBox.setVgrow(instancesScroll, Priority.ALWAYS);

        // Status Label
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 12px;");
        statusLabel.setAlignment(Pos.CENTER);

        // --- REMOTE MODPACKS ---
        VBox remoteSection = new VBox(15);
        remoteSection.setAlignment(Pos.TOP_CENTER);
        remoteSection.setMaxWidth(800);

        HBox remoteHeader = new HBox(15);
        remoteHeader.setAlignment(Pos.CENTER);
        Label remoteTitle = new Label("COMMUNITY MODPACKS");
        remoteTitle.getStyleClass().add("h2");

        Button reloadBtn = new Button("RELOAD");
        reloadBtn.getStyleClass().add("secondary-button");
        reloadBtn.setStyle("-fx-font-size: 10px; -fx-padding: 5 10;");
        reloadBtn.setOnAction(e -> refreshRemoteModpacks());

        remoteHeader.getChildren().addAll(remoteTitle, reloadBtn);

        remoteList = new VBox(10);
        remoteList.setAlignment(Pos.TOP_CENTER);
        remoteList.setMaxWidth(800);

        remoteSection.getChildren().addAll(remoteHeader, remoteList);

        this.getChildren().addAll(header, instancesScroll, statusLabel, remoteSection);

        // Load instances
        refreshInstances();
        refreshRemoteModpacks();
    }

    private void showAddInstanceDialog() {
        AddInstanceDialog dialog = new AddInstanceDialog();
        dialog.showAndWait();

        VersionInfo selected = dialog.getSelectedVersion();
        if (selected != null) {
            String customName = dialog.getInstanceName();
            installInstance(selected, customName);
        }
    }

    private void installInstance(VersionInfo version, String customName) {
        if (!session.isLoggedIn()) {
            statusLabel.setText("Please login first!");
            return;
        }

        statusLabel.setText("Installing " + customName + "...");

        new GameLaunchService().launchGame(version, session, status -> {
            javafx.application.Platform.runLater(() -> {
                statusLabel.setText(status);
            });
        }).thenAccept(process -> {
            javafx.application.Platform.runLater(() -> {
                if (process != null) {
                    // Save custom name
                    InstanceMetadataService.getInstance().setInstanceName(version.getId(), customName,
                            version.getType());
                    statusLabel.setText(customName + " installed successfully!");
                    // Kill the process immediately since we just wanted to install
                    process.destroy();
                    refreshInstances();
                } else {
                    InstanceMetadataService.getInstance().setInstanceName(version.getId(), customName,
                            version.getType());
                    statusLabel.setText("Installation completed!");
                    refreshInstances();
                }
            });
        });
    }

    private void deleteInstance(VersionInfo version) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Instance");
        confirm.setHeaderText("Delete " + InstanceMetadataService.getInstance().getInstanceName(version.getId()) + "?");
        confirm.setContentText("This will delete all files for this instance.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Delete instance files
                File versionFolder;
                if ("modpack".equals(version.getType())) {
                    versionFolder = new File(Constants.GAME_DIR + "/modpacks", version.getId());
                } else {
                    versionFolder = new File(Constants.GAME_DIR + "/versions", version.getId());
                }

                if (versionFolder.exists()) {
                    deleteDirectory(versionFolder);
                }

                // Remove metadata
                InstanceMetadataService.getInstance().removeInstance(version.getId());

                statusLabel.setText("Instance deleted");
                refreshInstances();
            }
        });
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

    private void updateInstancesGrid(List<VersionInfo> versions) {
        instancesGrid.getChildren().clear();

        if (versions.isEmpty()) {
            Label emptyLabel = new Label("No instances yet. Click '+ Add Instance' to get started!");
            emptyLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 14px;");
            instancesGrid.getChildren().add(emptyLabel);
            return;
        }

        for (VersionInfo version : versions) {
            VBox card = createInstanceCard(version);
            instancesGrid.getChildren().add(card);
        }
    }

    private VBox createInstanceCard(VersionInfo version) {
        VBox card = new VBox(10);
        card.getStyleClass().add("instance-card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(200);
        card.setPrefHeight(170);

        // Edit Button (top-right) with context menu
        Button editBtn = new Button("⋮");
        editBtn.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.1); -fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 2 8; -fx-background-radius: 50%; -fx-cursor: hand;");

        ContextMenu contextMenu = new ContextMenu();
        MenuItem renameItem = new MenuItem("Rename");
        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setStyle("-fx-text-fill: #ef4444;");

        renameItem.setOnAction(e -> renameInstance(version));
        deleteItem.setOnAction(e -> deleteInstance(version));

        contextMenu.getItems().addAll(renameItem, deleteItem);
        editBtn.setOnAction(e -> contextMenu.show(editBtn, javafx.geometry.Side.BOTTOM, 0, 0));

        StackPane editContainer = new StackPane(editBtn);
        editContainer.setAlignment(Pos.TOP_RIGHT);
        editContainer.setMaxWidth(Double.MAX_VALUE);

        // Version Name (use custom name if available)
        String displayName = InstanceMetadataService.getInstance().getInstanceName(version.getId());
        Label name = new Label(displayName);
        name.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
        name.setWrapText(true);
        name.setMaxWidth(180);
        name.setAlignment(Pos.CENTER);

        // Version Type Badge
        Label type = new Label(version.getType().toUpperCase());
        type.getStyleClass().add("version-tag");
        if (version.getType().equals("release"))
            type.setStyle(
                    "-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 3 8; -fx-background-radius: 6;");
        else if (version.getType().equals("snapshot"))
            type.setStyle(
                    "-fx-background-color: #f59e0b; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 3 8; -fx-background-radius: 6;");
        else
            type.setStyle(
                    "-fx-background-color: #8b5cf6; -fx-text-fill: white; -fx-font-size: 10px; -fx-padding: 3 8; -fx-background-radius: 6;");

        // Play Button
        Button playBtn = new Button("PLAY");
        playBtn.getStyleClass().add("instance-play-button");
        playBtn.setOnAction(e -> handleLaunch(version));

        card.getChildren().addAll(editContainer, name, type, playBtn);

        return card;
    }

    private void renameInstance(VersionInfo version) {
        TextInputDialog dialog = new TextInputDialog(
                InstanceMetadataService.getInstance().getInstanceName(version.getId()));
        dialog.setTitle("Rename Instance");
        dialog.setHeaderText("Rename " + InstanceMetadataService.getInstance().getInstanceName(version.getId()));
        dialog.setContentText("New name:");

        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.trim().isEmpty()) {
                InstanceMetadataService.getInstance().setInstanceName(version.getId(), newName.trim(),
                        version.getType());
                refreshInstances();
            }
        });
    }

    public void refreshRemoteModpacks() {
        String repoUrl = SettingsService.getInstance().getRepoUrl();
        if (repoUrl == null || repoUrl.isEmpty())
            return;

        new RemoteModpackService().fetchRemoteModpacks(repoUrl).thenAccept(modpacks -> {
            javafx.application.Platform.runLater(() -> {
                remoteList.getChildren().clear();
                for (RemoteModpackService.RemoteModpack mp : modpacks) {
                    HBox card = new HBox(15);
                    card.getStyleClass().add("modpack-card");
                    card.setAlignment(Pos.CENTER_LEFT);
                    card.setPrefWidth(780);
                    card.setMaxWidth(780);

                    VBox info = new VBox(5);
                    Label name = new Label(mp.name);
                    name.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: bold;");
                    Label desc = new Label(mp.description);
                    desc.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 11px;");
                    desc.setWrapText(true);
                    desc.setMaxWidth(500);
                    info.getChildren().addAll(name, desc);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Button actionBtn = new Button("INSTALL");
                    actionBtn.getStyleClass().add("instance-play-button");
                    actionBtn.setStyle("-fx-font-size: 11px; -fx-padding: 6 15;");

                    if (new File(Constants.GAME_DIR + "/modpacks/" + mp.id).exists()) {
                        actionBtn.setText("INSTALLED");
                        actionBtn.setDisable(true);
                        actionBtn.setStyle(
                                "-fx-background-color: rgba(255,255,255,0.05); -fx-text-fill: #8e8e93; -fx-font-size: 11px; -fx-padding: 6 15;");
                    }

                    actionBtn.setOnAction(e -> {
                        actionBtn.setDisable(true);
                        actionBtn.setText("INSTALLING...");
                        new RemoteModpackService().installModpack(mp, status -> {
                            javafx.application.Platform.runLater(() -> statusLabel.setText(status));
                        }).thenAccept(success -> {
                            javafx.application.Platform.runLater(() -> {
                                if (success) {
                                    actionBtn.setText("INSTALLED");
                                    refreshInstances();
                                } else {
                                    actionBtn.setDisable(false);
                                    actionBtn.setText("INSTALL");
                                }
                            });
                        });
                    });

                    card.getChildren().addAll(info, spacer, actionBtn);
                    remoteList.getChildren().add(card);
                }
            });
        });
    }

    public void refreshInstances() {
        // Get all versions
        new VersionService().getVersions().thenAccept(allVersions -> {
            List<VersionInfo> modpacks = new ModpackService().getModpacksAsVersions();

            javafx.application.Platform.runLater(() -> {
                // Filter to only show installed instances
                List<VersionInfo> installed = new ArrayList<>();
                GameLaunchService launcher = new GameLaunchService();

                // Add installed modpacks
                installed.addAll(modpacks);

                // Add installed vanilla versions
                for (VersionInfo version : allVersions) {
                    if (launcher.isVersionInstalled(version)) {
                        installed.add(version);
                    }
                }

                updateInstancesGrid(installed);
            });
        });
    }

    private void handleLaunch(VersionInfo version) {
        if (!session.isLoggedIn()) {
            statusLabel.setText("Please login first!");
            return;
        }

        SettingsService.getInstance().setLastVersionId(version.getId());
        String displayName = InstanceMetadataService.getInstance().getInstanceName(version.getId());
        statusLabel.setText("Starting " + displayName + "...");

        new GameLaunchService().launchGame(version, session, status -> {
            javafx.application.Platform.runLater(() -> {
                statusLabel.setText(status);
            });
        }).thenAccept(process -> {
            javafx.application.Platform.runLater(() -> {
                if (process != null) {
                    statusLabel.setText(displayName + " is running!");

                    if (SettingsService.getInstance().isAutoClose()) {
                        javafx.application.Platform.exit();
                        System.exit(0);
                    }

                    new Thread(() -> {
                        try {
                            process.waitFor();
                            javafx.application.Platform.runLater(() -> {
                                statusLabel.setText("");
                            });
                        } catch (InterruptedException e) {
                        }
                    }).start();
                }
            });
        });
    }
}
