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

        // Title
        Label titleLabel = new Label("Minecraft Launcher");
        titleLabel.setStyle("-fx-text-fill: #a0a0a0; -fx-font-size: 12px;");

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Window Controls
        Button minimizeBtn = new Button("—");
        minimizeBtn.getStyleClass().addAll("window-button");
        minimizeBtn.setOnAction(e -> stage.setIconified(true));

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("window-button", "close");
        closeBtn.setOnAction(e -> stage.close());

        this.getChildren().addAll(titleLabel, spacer, minimizeBtn, closeBtn);

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
}
