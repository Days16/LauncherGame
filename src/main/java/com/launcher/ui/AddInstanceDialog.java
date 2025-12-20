package com.launcher.ui;

import com.launcher.services.GameLaunchService;
import com.launcher.services.VersionInfo;
import com.launcher.services.VersionService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;
import java.util.stream.Collectors;

public class AddInstanceDialog extends Stage {

    private ListView<VersionInfo> versionList;
    private VersionInfo selectedVersion;
    private TextField instanceNameField;
    private CheckBox releaseFilter;
    private CheckBox snapshotFilter;
    private List<VersionInfo> allVersions;

    public AddInstanceDialog() {
        this.initModality(Modality.APPLICATION_MODAL);
        this.setTitle("Add New Instance");
        this.setWidth(600);
        this.setHeight(650);

        VBox root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: #0f0f11;");

        // Title
        Label title = new Label("Create New Instance");
        title.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold;");

        // Instance Name Section
        VBox nameSection = new VBox(8);
        nameSection.setAlignment(Pos.CENTER_LEFT);
        nameSection.setMaxWidth(550);
        Label nameLabel = new Label("Instance Name");
        nameLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 12px; -fx-font-weight: 600;");
        instanceNameField = new TextField();
        instanceNameField.setPromptText("My Awesome Instance");
        instanceNameField.setStyle(
                "-fx-background-color: #1c1c1e; -fx-text-fill: white; -fx-prompt-text-fill: #8e8e93; -fx-padding: 10; -fx-background-radius: 8; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 8;");
        instanceNameField.setPrefHeight(40);
        nameSection.getChildren().addAll(nameLabel, instanceNameField);

        // Version Selection Section
        VBox versionSection = new VBox(8);
        versionSection.setAlignment(Pos.CENTER_LEFT);
        versionSection.setMaxWidth(550);

        HBox versionHeader = new HBox(15);
        versionHeader.setAlignment(Pos.CENTER_LEFT);
        Label versionLabel = new Label("Minecraft Version");
        versionLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 12px; -fx-font-weight: 600;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Filters
        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER);
        releaseFilter = new CheckBox("Releases");
        releaseFilter.setSelected(true);
        releaseFilter.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 11px;");
        snapshotFilter = new CheckBox("Snapshots");
        snapshotFilter.setSelected(false);
        snapshotFilter.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 11px;");
        filters.getChildren().addAll(releaseFilter, snapshotFilter);

        versionHeader.getChildren().addAll(versionLabel, spacer, filters);

        // Version List
        versionList = new ListView<>();
        versionList.setPrefHeight(350);
        versionList.setStyle(
                "-fx-background-color: #1c1c1e; -fx-border-color: rgba(255,255,255,0.1); -fx-border-radius: 10; -fx-background-radius: 10;");
        VBox.setVgrow(versionList, Priority.ALWAYS);

        versionList.setCellFactory(lv -> new ListCell<VersionInfo>() {
            @Override
            protected void updateItem(VersionInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    HBox cell = new HBox(12);
                    cell.setAlignment(Pos.CENTER_LEFT);
                    cell.setStyle("-fx-padding: 8;");

                    Label name = new Label(item.getId());
                    name.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;");

                    Label type = new Label(item.getType().toUpperCase());
                    type.setStyle(
                            "-fx-font-size: 9px; -fx-padding: 3 8; -fx-background-radius: 6; -fx-font-weight: bold;");
                    if (item.getType().equals("release"))
                        type.setStyle(type.getStyle() + "-fx-background-color: #10b981; -fx-text-fill: white;");
                    else
                        type.setStyle(type.getStyle() + "-fx-background-color: #f59e0b; -fx-text-fill: white;");

                    Region cellSpacer = new Region();
                    HBox.setHgrow(cellSpacer, Priority.ALWAYS);

                    cell.getChildren().addAll(name, type, cellSpacer);

                    if (new GameLaunchService().isVersionInstalled(item)) {
                        Label installed = new Label("✓ INSTALLED");
                        installed.setStyle("-fx-text-fill: #10b981; -fx-font-size: 10px; -fx-font-weight: bold;");
                        cell.getChildren().add(installed);
                    }

                    setGraphic(cell);

                    // Selection styling
                    if (isSelected()) {
                        setStyle("-fx-background-color: rgba(16, 185, 129, 0.2); -fx-background-radius: 8;");
                    } else {
                        setStyle("-fx-background-color: transparent;");
                    }
                }
            }
        });

        versionList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && instanceNameField.getText().isEmpty()) {
                instanceNameField.setText(newVal.getId());
            }
        });

        versionSection.getChildren().addAll(versionHeader, versionList);

        // Buttons
        HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(10, 0, 0, 0));

        Button addButton = new Button("Create Instance");
        addButton.getStyleClass().add("play-button");
        addButton.setStyle("-fx-font-size: 14px; -fx-padding: 12 40; -fx-background-radius: 10;");
        addButton.setOnAction(e -> {
            selectedVersion = versionList.getSelectionModel().getSelectedItem();
            if (selectedVersion != null) {
                this.close();
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().add("secondary-button");
        cancelButton.setStyle("-fx-font-size: 14px; -fx-padding: 12 40; -fx-background-radius: 10;");
        cancelButton.setOnAction(e -> {
            selectedVersion = null;
            this.close();
        });

        buttons.getChildren().addAll(cancelButton, addButton);

        root.getChildren().addAll(title, nameSection, versionSection, buttons);
        this.setScene(new javafx.scene.Scene(root));

        // Load versions
        loadVersions();

        // Filter listeners
        releaseFilter.setOnAction(e -> applyFilters());
        snapshotFilter.setOnAction(e -> applyFilters());
    }

    private void loadVersions() {
        new VersionService().getVersions().thenAccept(versions -> {
            javafx.application.Platform.runLater(() -> {
                allVersions = versions;
                applyFilters();
            });
        });
    }

    private void applyFilters() {
        if (allVersions == null)
            return;

        List<VersionInfo> filtered = allVersions.stream()
                .filter(v -> {
                    if (v.getType().equals("release"))
                        return releaseFilter.isSelected();
                    if (v.getType().equals("snapshot"))
                        return snapshotFilter.isSelected();
                    return false;
                })
                .collect(Collectors.toList());

        versionList.getItems().setAll(filtered);
        if (!filtered.isEmpty()) {
            versionList.getSelectionModel().selectFirst();
        }
    }

    public VersionInfo getSelectedVersion() {
        return selectedVersion;
    }

    public String getInstanceName() {
        String name = instanceNameField.getText().trim();
        return name.isEmpty() ? (selectedVersion != null ? selectedVersion.getId() : "New Instance") : name;
    }
}
