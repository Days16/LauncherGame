package com.launcher.ui;

import com.launcher.services.GameLaunchService;
import com.launcher.services.SessionService;
import com.launcher.services.SettingsService;
import com.launcher.services.VersionInfo;
import com.launcher.services.VersionService;
import com.launcher.services.LogService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DashboardView extends VBox {

    private final ProgressBar progressBar;
    private final Button playButton;
    private final Label statusLabel;
    private final ComboBox<VersionInfo> versionSelector;
    private List<VersionInfo> allVersions = new ArrayList<>();
    private final SessionService session = SessionService.getInstance();

    // Filters
    private final CheckBox releaseCb;
    private final CheckBox snapshotCb;
    private final CheckBox alphaCb;
    private final CheckBox modpackCb;
    private Label remoteTitle;
    private VBox remoteList;

    public DashboardView() {
        this.getStyleClass().add("dashboard");
        this.setAlignment(Pos.CENTER);

        // Hero Text
        Label title = new Label("Minecraft Java Edition");
        title.getStyleClass().add("h1");

        // Version Selector
        versionSelector = new ComboBox<>();
        versionSelector.getStyleClass().add("combo-box");
        versionSelector.setPromptText("Loading versions...");
        versionSelector.setPrefWidth(250);

        // Filters UI
        HBox filtersBox = new HBox(10);
        filtersBox.setAlignment(Pos.CENTER);

        releaseCb = new CheckBox("Releases");
        releaseCb.setSelected(true);
        releaseCb.setStyle("-fx-text-fill: white;");

        snapshotCb = new CheckBox("Snapshots");
        snapshotCb.setStyle("-fx-text-fill: white;");

        alphaCb = new CheckBox("Alpha/Beta");
        alphaCb.setStyle("-fx-text-fill: white;");

        modpackCb = new CheckBox("Modpacks");
        modpackCb.setSelected(true);
        modpackCb.setStyle("-fx-text-fill: white;");

        filtersBox.getChildren().addAll(releaseCb, snapshotCb, alphaCb, modpackCb);

        // Filter Logic
        Runnable applyFilters = () -> {
            List<VersionInfo> filtered = allVersions.stream()
                    .filter(v -> {
                        if (v.getType().equals("release"))
                            return releaseCb.isSelected();
                        if (v.getType().equals("snapshot"))
                            return snapshotCb.isSelected();
                        if (v.getType().contains("old_"))
                            return alphaCb.isSelected();
                        if (v.getType().equals("modpack"))
                            return modpackCb.isSelected();
                        return false;
                    })
                    .collect(Collectors.toList());

            versionSelector.getItems().setAll(filtered);
            if (!filtered.isEmpty()) {
                // Try to preserve selection or select first
                VersionInfo current = versionSelector.getValue();
                if (current != null && filtered.contains(current)) {
                    versionSelector.getSelectionModel().select(current);
                } else {
                    versionSelector.getSelectionModel().selectFirst();
                }
            }
        };

        releaseCb.setOnAction(e -> applyFilters.run());
        snapshotCb.setOnAction(e -> applyFilters.run());
        alphaCb.setOnAction(e -> applyFilters.run());
        modpackCb.setOnAction(e -> applyFilters.run());

        // Play Button
        playButton = new Button("PLAY");
        playButton.getStyleClass().add("play-button");

        // Progress Bar (Hidden initially)
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        progressBar.setVisible(false);

        // Status Label
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #808080;");

        // Remote Modpacks Section
        VBox remoteModpacksBox = new VBox(10);
        remoteModpacksBox.setAlignment(Pos.CENTER);
        remoteTitle = new Label("Community Modpacks");
        remoteTitle.getStyleClass().add("h2");
        remoteTitle.setVisible(false);
        remoteTitle.setManaged(false);

        remoteList = new VBox(5);
        remoteList.setAlignment(Pos.CENTER);

        remoteModpacksBox.getChildren().addAll(remoteTitle, remoteList);

        // Layout
        this.getChildren().addAll(title, versionSelector, filtersBox, playButton, statusLabel, progressBar,
                remoteModpacksBox);

        // Event Handling
        playButton.setOnAction(e -> handleLaunch());

        // Initial Load
        refreshVersions();
        refreshRemoteModpacks();
    }

    public void refreshRemoteModpacks() {
        if (remoteTitle == null || remoteList == null)
            return;

        String repoUrl = SettingsService.getInstance().getRepoUrl();
        if (repoUrl == null || repoUrl.isEmpty()) {
            remoteTitle.setVisible(false);
            remoteTitle.setManaged(false);
            remoteList.getChildren().clear();
            return;
        }

        new com.launcher.services.RemoteModpackService().fetchRemoteModpacks(repoUrl).thenAccept(modpacks -> {
            javafx.application.Platform.runLater(() -> {
                remoteList.getChildren().clear();
                if (!modpacks.isEmpty()) {
                    remoteTitle.setVisible(true);
                    remoteTitle.setManaged(true);
                    for (com.launcher.services.RemoteModpackService.RemoteModpack mp : modpacks) {
                        HBox card = new HBox(20);
                        card.setAlignment(Pos.CENTER_LEFT);
                        card.setStyle("-fx-background-color: #2d2d30; -fx-padding: 10; -fx-background-radius: 5;");
                        card.setMaxWidth(400);

                        VBox info = new VBox(5);
                        Label name = new Label(mp.name + " (" + mp.version + ")");
                        name.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
                        Label desc = new Label(mp.description);
                        desc.setStyle("-fx-text-fill: #808080; -fx-font-size: 11px;");
                        info.getChildren().addAll(name, desc);

                        Button installBtn = new Button("Install");
                        installBtn.getStyleClass().add("play-button");
                        installBtn.setStyle("-fx-font-size: 12px; -fx-padding: 5 15;");

                        // Check if already installed
                        File localDir = new File(
                                System.getProperty("user.home") + "/Documents/MinecraftLauncher/modpacks/" + mp.id);
                        if (localDir.exists()) {
                            installBtn.setText("Installed");
                            installBtn.setDisable(true);
                        }

                        installBtn.setOnAction(e -> {
                            installBtn.setDisable(true);
                            installBtn.setText("Installing...");
                            new com.launcher.services.RemoteModpackService().installModpack(mp, status -> {
                                javafx.application.Platform.runLater(() -> statusLabel.setText(status));
                            }).thenAccept(success -> {
                                javafx.application.Platform.runLater(() -> {
                                    if (success) {
                                        installBtn.setText("Installed");
                                        refreshVersions(); // Refresh version selector
                                    } else {
                                        installBtn.setDisable(false);
                                        installBtn.setText("Install");
                                    }
                                });
                            });
                        });

                        HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);
                        card.getChildren().addAll(info, installBtn);
                        remoteList.getChildren().add(card);
                    }
                } else {
                    remoteTitle.setVisible(false);
                    remoteTitle.setManaged(false);
                }
            });
        });
    }

    public void refreshVersions() {
        // Fetch Versions Async
        new VersionService().getVersions().thenAccept(versions -> {
            // Fetch Modpacks
            List<VersionInfo> modpacks = new com.launcher.services.ModpackService().getModpacksAsVersions();
            versions.addAll(0, modpacks); // Add modpacks at the top

            javafx.application.Platform.runLater(() -> {
                allVersions = versions;

                // Trigger filter update
                List<VersionInfo> filtered = allVersions.stream()
                        .filter(v -> {
                            if (v.getType().equals("release"))
                                return releaseCb.isSelected();
                            if (v.getType().equals("snapshot"))
                                return snapshotCb.isSelected();
                            if (v.getType().contains("old_"))
                                return alphaCb.isSelected();
                            if (v.getType().equals("modpack"))
                                return modpackCb.isSelected();
                            return false;
                        })
                        .collect(Collectors.toList());

                versionSelector.getItems().setAll(filtered);

                // Restore last selection
                String lastId = SettingsService.getInstance().getLastVersionId();
                if (lastId != null) {
                    for (VersionInfo v : versionSelector.getItems()) {
                        if (v.getId().equals(lastId)) {
                            versionSelector.getSelectionModel().select(v);
                            break;
                        }
                    }
                }

                if (versionSelector.getSelectionModel().getSelectedItem() == null && !filtered.isEmpty()) {
                    versionSelector.getSelectionModel().selectFirst();
                }
            });
        });
    }

    private Process gameProcess;

    private void handleLaunch() {
        if (gameProcess != null && gameProcess.isAlive()) {
            // STOP Logic
            gameProcess.destroy();
            playButton.setText("PLAY");
            playButton.setStyle(""); // Reset style
            statusLabel.setText("Game stopped.");
            gameProcess = null;
            return;
        }

        VersionInfo selectedVersion = versionSelector.getValue();
        if (selectedVersion == null) {
            statusLabel.setText("Please select a version!");
            return;
        }

        if (!session.isLoggedIn()) {
            statusLabel.setText("Please login first!");
            return;
        }

        // Save selection
        SettingsService.getInstance().setLastVersionId(selectedVersion.getId());

        playButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1); // Indeterminate
        statusLabel.setText("Initializing...");

        GameLaunchService launcher = new GameLaunchService();
        launcher.launchGame(selectedVersion, session, status -> {
            javafx.application.Platform.runLater(() -> {
                statusLabel.setText(status);
                if (status.startsWith("Error")) {
                    progressBar.setProgress(0);
                    playButton.setDisable(false);
                    statusLabel.setStyle("-fx-text-fill: #e81123;");
                }
            });
        }).thenAccept(process -> {
            javafx.application.Platform.runLater(() -> {
                if (process != null) {
                    this.gameProcess = process;
                    playButton.setText("STOP");
                    playButton.setStyle("-fx-background-color: #e81123; -fx-text-fill: white;");
                    playButton.setDisable(false);
                    statusLabel.setText("Game Running...");
                    statusLabel.setStyle("-fx-text-fill: #4caf50;");
                    progressBar.setProgress(1);

                    // Reset button when game exits
                    new Thread(() -> {
                        try {
                            process.waitFor();
                            javafx.application.Platform.runLater(() -> {
                                playButton.setText("PLAY");
                                playButton.setStyle("");
                                statusLabel.setText("Game exited.");
                                gameProcess = null;
                            });
                        } catch (InterruptedException e) {
                            LogService.error("Error waiting for game process", e);
                        }
                    }).start();
                } else {
                    playButton.setDisable(false);
                }
            });
        });
    }

    public interface LaunchCallback {
        void onStatusUpdate(String status);
    }
}
