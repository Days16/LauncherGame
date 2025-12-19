package com.launcher.ui;

import com.launcher.services.SettingsService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class SettingsView extends VBox {

    public SettingsView() {
        this.getStyleClass().add("dashboard"); // Reuse dashboard padding
        this.setAlignment(Pos.TOP_LEFT);
        this.setSpacing(20);
        this.setPadding(new Insets(40));

        SettingsService settings = SettingsService.getInstance();

        Label title = new Label("Settings");
        title.getStyleClass().add("h1");

        // RAM Allocation
        VBox ramSection = new VBox(10);
        Label ramLabel = new Label("RAM Allocation (MB)");
        ramLabel.getStyleClass().add("h2");

        Slider ramSlider = new Slider(1024, 8192, settings.getRam());
        ramSlider.setShowTickLabels(true);
        ramSlider.setShowTickMarks(true);
        ramSlider.setMajorTickUnit(1024);

        Label ramValue = new Label(settings.getRam() + " MB");
        ramSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            ramValue.setText(String.format("%.0f MB", newVal));
            settings.setRam(newVal.intValue());
        });

        ramSection.getChildren().addAll(ramLabel, ramSlider, ramValue);

        // Java Path
        VBox javaSection = new VBox(10);
        Label javaLabel = new Label("Java Executable Path");
        javaLabel.getStyleClass().add("h2");

        TextField javaPathField = new TextField(settings.getJavaPath());
        javaPathField.setPromptText("Path to javaw.exe (e.g. C:\\Program Files\\Java\\jdk-21\\bin\\javaw.exe)");
        javaPathField.setStyle(
                "-fx-background-color: #2d2d30; " +
                        "-fx-text-fill: white; " +
                        "-fx-padding: 10;");

        javaPathField.textProperty().addListener((obs, oldVal, newVal) -> {
            settings.setJavaPath(newVal);
        });

        javaSection.getChildren().addAll(javaLabel, javaPathField);

        // Repo URL
        VBox repoSection = new VBox(10);
        Label repoLabel = new Label("Modpack Repository URL");
        repoLabel.getStyleClass().add("h2");

        TextField repoUrlField = new TextField(settings.getRepoUrl());
        repoUrlField.setPromptText("URL to modpacks.json (e.g. https://gist.github.com/.../raw/modpacks.json)");
        repoUrlField.setStyle(
                "-fx-background-color: #2d2d30; " +
                        "-fx-text-fill: white; " +
                        "-fx-padding: 10;");

        repoUrlField.textProperty().addListener((obs, oldVal, newVal) -> {
            settings.setRepoUrl(newVal);
        });

        Button resetRepoBtn = new Button("Reset to Default");
        resetRepoBtn.setStyle("-fx-background-color: #3e3e42; -fx-text-fill: white; -fx-padding: 5 10;");
        resetRepoBtn.setOnAction(e -> {
            String defaultUrl = "https://modpack-server.vercel.app/modpacks.json";
            repoUrlField.setText(defaultUrl);
            settings.setRepoUrl(defaultUrl);
        });

        repoSection.getChildren().addAll(repoLabel, repoUrlField, resetRepoBtn);

        this.getChildren().addAll(title, ramSection, javaSection, repoSection);
    }
}
