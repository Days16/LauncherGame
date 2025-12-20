package com.launcher.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class CustomTitleBar extends HBox {

    private double xOffset = 0;
    private double yOffset = 0;

    public CustomTitleBar(Stage stage) {
        this.getStyleClass().add("title-bar");
        this.setAlignment(Pos.CENTER_LEFT);
        this.setPrefHeight(40);

        // Logo / Title
        Label titleLabel = new Label("ANTIGRAVITY LAUNCHER");
        titleLabel.setStyle(
                "-fx-text-fill: #10b981; -fx-font-size: 11px; -fx-font-weight: 800; -fx-letter-spacing: 1px;");

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Window Controls
        HBox controls = new HBox(5);
        controls.setAlignment(Pos.CENTER_RIGHT);

        Button minimizeBtn = createControlButton("—", () -> stage.setIconified(true));
        Button closeBtn = createControlButton("✕", () -> stage.close());
        closeBtn.getStyleClass().add("close-btn");

        controls.getChildren().addAll(minimizeBtn, closeBtn);

        this.getChildren().addAll(titleLabel, spacer, controls);

        // Dragging Logic
        this.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        this.setOnMouseDragged(event -> {
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
    }

    private Button createControlButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().add("window-button");
        btn.setOnAction(e -> action.run());
        btn.setPrefSize(30, 30);
        btn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #8e8e93; -fx-font-size: 12px; -fx-cursor: hand; -fx-background-radius: 5;");
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: rgba(255,255,255,0.05); -fx-text-fill: white; -fx-font-size: 12px; -fx-cursor: hand; -fx-background-radius: 5;"));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #8e8e93; -fx-font-size: 12px; -fx-cursor: hand; -fx-background-radius: 5;"));
        return btn;
    }
}
