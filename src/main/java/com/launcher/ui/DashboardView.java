package com.launcher.ui;

import com.launcher.services.GameLaunchService;
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
import javafx.util.Callback;

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
    private final CheckBox modpackCb;

    private VBox remoteList;
    private Label selectedVersionLabel;
    private Label selectedTypeLabel;

    public DashboardView() {
        this.getStyleClass().add("dashboard");
        this.setAlignment(Pos.TOP_CENTER);
        this.setSpacing(30);
        this.setPadding(new Insets(40));

        // --- HERO SECTION ---
        VBox hero = new VBox(10);
        hero.setAlignment(Pos.CENTER);

        selectedVersionLabel = new Label("Select a Version");
        selectedVersionLabel.getStyleClass().add("h1");

        selectedTypeLabel = new Label("READY TO PLAY");
        selectedTypeLabel.getStyleClass().add("h2");

        hero.getChildren().addAll(selectedTypeLabel, selectedVersionLabel);

        // --- CONTROLS SECTION ---
        VBox controls = new VBox(20);
        controls.setAlignment(Pos.CENTER);

        // Version Selector & Filters
        HBox selectorRow = new HBox(15);
        selectorRow.setAlignment(Pos.CENTER);

        versionSelector = new ComboBox<>();
        versionSelector.setPrefWidth(300);
        versionSelector.setPromptText("Loading versions...");

        // Filter Toggles (Minimalist)
        HBox filters = new HBox(15);
        filters.setAlignment(Pos.CENTER);
        releaseCb = createFilterChip("Releases", true);
        modpackCb = createFilterChip("Modpacks", true);
        filters.getChildren().addAll(releaseCb, modpackCb);

        selectorRow.getChildren().addAll(versionSelector, filters);

        // Play Button
        playButton = new Button("PLAY");
        playButton.getStyleClass().add("play-button");

        // Progress & Status
        VBox statusBox = new VBox(10);
        statusBox.setAlignment(Pos.CENTER);
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setVisible(false);
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 12px;");
        statusBox.getChildren().addAll(progressBar, statusLabel);

        controls.getChildren().addAll(selectorRow, playButton, statusBox);

        // --- REMOTE MODPACKS ---
        VBox remoteSection = new VBox(20);
        remoteSection.setAlignment(Pos.TOP_CENTER);

        HBox remoteHeader = new HBox(15);
        remoteHeader.setAlignment(Pos.CENTER);
        Label remoteTitle = new Label("EXPLORE COMMUNITY MODPACKS");
        remoteTitle.getStyleClass().add("h2");

        Button reloadBtn = new Button("RELOAD");
        reloadBtn.getStyleClass().add("secondary-button"); // Assuming a secondary-button style exists or adding one
        reloadBtn.setStyle("-fx-font-size: 10px; -fx-padding: 5 10;");
        reloadBtn.setOnAction(e -> refreshRemoteModpacks());

        remoteHeader.getChildren().addAll(remoteTitle, reloadBtn);

        remoteList = new VBox(15);
        remoteList.setAlignment(Pos.TOP_CENTER);
        ScrollPane scrollPane = new ScrollPane(remoteList);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle(
                "-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        remoteSection.getChildren().addAll(remoteHeader, scrollPane);
        VBox.setVgrow(remoteSection, Priority.ALWAYS);

        this.getChildren().addAll(hero, controls, remoteSection);

        // --- LOGIC & EVENTS ---
        setupVersionSelector();
        playButton.setOnAction(e -> handleLaunch());

        Runnable applyFilters = () -> {
            List<VersionInfo> filtered = allVersions.stream()
                    .filter(v -> {
                        if (v.getType().equals("release"))
                            return releaseCb.isSelected();
                        if (v.getType().equals("modpack"))
                            return modpackCb.isSelected();
                        return false;
                    })
                    .collect(Collectors.toList());
            versionSelector.getItems().setAll(filtered);
            if (!filtered.isEmpty() && versionSelector.getSelectionModel().getSelectedItem() == null) {
                versionSelector.getSelectionModel().selectFirst();
            }
        };

        releaseCb.setOnAction(e -> applyFilters.run());
        modpackCb.setOnAction(e -> applyFilters.run());

        versionSelector.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectedVersionLabel.setText(newVal.getId());
                selectedTypeLabel.setText(newVal.getType().toUpperCase() + " VERSION");
            }
        });

        refreshVersions();
        refreshRemoteModpacks();
    }

    private CheckBox createFilterChip(String text, boolean selected) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected(selected);
        cb.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 12px;");
        return cb;
    }

    private void setupVersionSelector() {
        versionSelector.setCellFactory(new Callback<ListView<VersionInfo>, ListCell<VersionInfo>>() {
            @Override
            public ListCell<VersionInfo> call(ListView<VersionInfo> param) {
                return new ListCell<VersionInfo>() {
                    @Override
                    protected void updateItem(VersionInfo item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setGraphic(null);
                        } else {
                            HBox cell = new HBox(10);
                            cell.setAlignment(Pos.CENTER_LEFT);

                            Label name = new Label(item.getId());
                            name.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

                            Label type = new Label(item.getType().toUpperCase());
                            type.getStyleClass().add("version-tag");
                            if (item.getType().equals("release"))
                                type.setStyle("-fx-background-color: #10b981;");
                            else if (item.getType().equals("snapshot"))
                                type.setStyle("-fx-background-color: #f59e0b;");
                            else
                                type.setStyle("-fx-background-color: #8b5cf6;");

                            Region spacer = new Region();
                            HBox.setHgrow(spacer, Priority.ALWAYS);

                            cell.getChildren().addAll(name, type, spacer);

                            if (new GameLaunchService().isVersionInstalled(item)) {
                                Label check = new Label("✓");
                                check.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                                cell.getChildren().add(check);
                            }

                            setGraphic(cell);
                        }
                    }
                };
            }
        });
        versionSelector.setButtonCell(versionSelector.getCellFactory().call(null));
    }

    public void refreshRemoteModpacks() {
        String repoUrl = SettingsService.getInstance().getRepoUrl();
        if (repoUrl == null || repoUrl.isEmpty())
            return;

        new RemoteModpackService().fetchRemoteModpacks(repoUrl).thenAccept(modpacks -> {
            javafx.application.Platform.runLater(() -> {
                remoteList.getChildren().clear();
                for (RemoteModpackService.RemoteModpack mp : modpacks) {
                    HBox card = new HBox(20);
                    card.getStyleClass().add("modpack-card");
                    card.setAlignment(Pos.CENTER_LEFT);
                    card.setMaxWidth(600);

                    VBox info = new VBox(5);
                    Label name = new Label(mp.name);
                    name.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
                    Label desc = new Label(mp.description);
                    desc.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 12px;");
                    info.getChildren().addAll(name, desc);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Button actionBtn = new Button("INSTALL");
                    actionBtn.getStyleClass().add("play-button");
                    actionBtn.setStyle("-fx-font-size: 12px; -fx-padding: 8 20;");

                    if (new File(Constants.GAME_DIR + "/modpacks/" + mp.id)
                            .exists()) {
                        actionBtn.setText("INSTALLED");
                        actionBtn.setDisable(true);
                        actionBtn.setStyle(
                                "-fx-background-color: rgba(255,255,255,0.05); -fx-text-fill: #8e8e93; -fx-font-size: 12px; -fx-padding: 8 20;");
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
                                    refreshVersions();
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

    public void refreshVersions() {
        new VersionService().getVersions().thenAccept(versions -> {
            List<VersionInfo> modpacks = new ModpackService().getModpacksAsVersions();
            versions.addAll(0, modpacks);
            javafx.application.Platform.runLater(() -> {
                allVersions = versions;
                List<VersionInfo> filtered = allVersions.stream()
                        .filter(v -> {
                            if (v.getType().equals("release"))
                                return releaseCb.isSelected();
                            if (v.getType().equals("modpack"))
                                return modpackCb.isSelected();
                            return false;
                        })
                        .collect(Collectors.toList());
                versionSelector.getItems().setAll(filtered);

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
            gameProcess.destroy();
            playButton.setText("PLAY");
            playButton.getStyleClass().remove("stop-button");
            gameProcess = null;
            return;
        }

        VersionInfo selectedVersion = versionSelector.getValue();
        if (selectedVersion == null)
            return;
        if (!session.isLoggedIn()) {
            statusLabel.setText("Please login first!");
            return;
        }

        SettingsService.getInstance().setLastVersionId(selectedVersion.getId());
        playButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(-1);
        statusLabel.setText("Initializing...");

        new GameLaunchService().launchGame(selectedVersion, session, status -> {
            javafx.application.Platform.runLater(() -> {
                statusLabel.setText(status);
                if (status.startsWith("Error")) {
                    progressBar.setProgress(0);
                    playButton.setDisable(false);
                }
            });
        }).thenAccept(process -> {
            javafx.application.Platform.runLater(() -> {
                if (process != null) {
                    this.gameProcess = process;
                    playButton.setText("STOP");
                    playButton.setStyle("-fx-background-color: #ef4444;");
                    playButton.setDisable(false);
                    progressBar.setProgress(1);

                    if (SettingsService.getInstance().isAutoClose()) {
                        javafx.application.Platform.exit();
                        System.exit(0);
                    }

                    new Thread(() -> {
                        try {
                            process.waitFor();
                            javafx.application.Platform.runLater(() -> {
                                playButton.setText("PLAY");
                                playButton.setStyle("");
                                gameProcess = null;
                            });
                        } catch (InterruptedException e) {
                        }
                    }).start();
                } else {
                    playButton.setDisable(false);
                }
            });
        });
    }
}
