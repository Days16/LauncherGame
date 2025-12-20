package com.launcher.ui;

import com.launcher.services.SettingsService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;

public class SettingsView extends VBox {

    public SettingsView() {
        this.getStyleClass().add("dashboard");
        this.setAlignment(Pos.TOP_LEFT);
        this.setSpacing(30);
        this.setPadding(new Insets(40));

        SettingsService settings = SettingsService.getInstance();

        Label title = new Label("SETTINGS");
        title.getStyleClass().add("h1");

        VBox content = new VBox(25);
        content.setAlignment(Pos.TOP_LEFT);
        content.setMaxWidth(600);

        // --- RAM ALLOCATION ---
        VBox ramSection = createSection("RAM ALLOCATION", "Select or adjust the maximum memory for the game.");

        Label ramValue = new Label(settings.getRam() + " MB");
        ramValue.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold; -fx-font-size: 16px;");

        Slider ramSlider = new Slider(1024, 32768, settings.getRam());
        ramSlider.getStyleClass().add("ram-slider");
        ramSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int val = newVal.intValue();
            ramValue.setText(val + " MB");
            settings.setRam(val);
        });

        HBox ramButtons = new HBox(10);
        ramButtons.getChildren().addAll(
                createRamButton("4GB", 4096, ramSlider),
                createRamButton("8GB", 8192, ramSlider),
                createRamButton("16GB", 16384, ramSlider),
                createRamButton("32GB", 32768, ramSlider));

        ramSection.getChildren().addAll(ramValue, ramSlider, ramButtons);

        // --- GAME RESOLUTION ---
        VBox resSection = createSection("GAME RESOLUTION", "Set the default window size and display mode.");

        HBox resInputs = new HBox(15);
        resInputs.setAlignment(Pos.CENTER_LEFT);

        TextField widthField = createNumberField(String.valueOf(settings.getResolutionWidth()), "Width");
        widthField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty())
                settings.setResolutionWidth(Integer.parseInt(newVal));
        });

        Label xLabel = new Label("×");
        xLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 18px;");

        TextField heightField = createNumberField(String.valueOf(settings.getResolutionHeight()), "Height");
        heightField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.isEmpty())
                settings.setResolutionHeight(Integer.parseInt(newVal));
        });

        CheckBox fullScreenCb = new CheckBox("FULLSCREEN");
        fullScreenCb.setSelected(settings.isFullScreen());
        fullScreenCb.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
        fullScreenCb.setOnAction(e -> settings.setFullScreen(fullScreenCb.isSelected()));

        resInputs.getChildren().addAll(widthField, xLabel, heightField, fullScreenCb);
        resSection.getChildren().add(resInputs);

        // --- LAUNCHER BEHAVIOR ---
        VBox behaviorSection = createSection("LAUNCHER BEHAVIOR",
                "Configure how the launcher acts during game sessions.");

        CheckBox autoCloseCb = new CheckBox("AUTO-CLOSE LAUNCHER AFTER START");
        autoCloseCb.setSelected(settings.isAutoClose());
        autoCloseCb.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");
        autoCloseCb.setOnAction(e -> settings.setAutoClose(autoCloseCb.isSelected()));

        behaviorSection.getChildren().add(autoCloseCb);

        // --- ADVANCED ---
        VBox advancedSection = createSection("ADVANCED", "Java runtime and repository configuration.");

        TextField javaPathField = new TextField(settings.getJavaPath());
        javaPathField.setPromptText("Java Path...");
        javaPathField.getStyleClass().add("combo-box");
        javaPathField.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-text-fill: white; -fx-padding: 10;");
        javaPathField.textProperty().addListener((obs, oldVal, newVal) -> settings.setJavaPath(newVal));

        TextField repoUrlField = new TextField(settings.getRepoUrl());
        repoUrlField.getStyleClass().add("combo-box");
        repoUrlField.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-text-fill: white; -fx-padding: 10;");
        repoUrlField.textProperty().addListener((obs, oldVal, newVal) -> settings.setRepoUrl(newVal));

        advancedSection.getChildren().addAll(new Label("JAVA PATH"), javaPathField, new Label("REPO URL"),
                repoUrlField);

        content.getChildren().addAll(ramSection, resSection, behaviorSection, advancedSection);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle(
                "-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        this.getChildren().addAll(title, scrollPane);
    }

    private Button createRamButton(String label, int value, Slider slider) {
        Button btn = new Button(label);
        btn.getStyleClass().add("nav-button");
        btn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.05); -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 8 15;");
        btn.setOnAction(e -> slider.setValue(value));
        return btn;
    }

    private TextField createNumberField(String value, String prompt) {
        TextField field = new TextField(value);
        field.setPromptText(prompt);
        field.setPrefWidth(80);
        field.getStyleClass().add("combo-box");
        field.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-text-fill: white; -fx-padding: 8;");
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                field.setText(newVal.replaceAll("[^\\d]", ""));
            }
        });
        return field;
    }

    private VBox createSection(String title, String description) {
        VBox section = new VBox(12);
        section.getStyleClass().add("modpack-card");
        section.setPadding(new Insets(20));

        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("h2");

        Label lblDesc = new Label(description);
        lblDesc.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 11px;");

        section.getChildren().addAll(lblTitle, lblDesc);
        return section;
    }
}
