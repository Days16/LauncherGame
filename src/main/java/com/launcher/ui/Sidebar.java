package com.launcher.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

public class Sidebar extends VBox {

    private final Runnable onHome;
    private final Runnable onSettings;
    private final Runnable onImport;
    private final Runnable onAccounts;

    public Sidebar(Runnable onHome, Runnable onSettings, Runnable onImport, Runnable onAccounts) {
        this.onHome = onHome;
        this.onSettings = onSettings;
        this.onImport = onImport;
        this.onAccounts = onAccounts;

        this.setPrefWidth(60);
        this.setStyle("-fx-background-color: #252526;");
        this.setAlignment(Pos.TOP_CENTER);
        this.setSpacing(10);
        this.setPadding(new javafx.geometry.Insets(20, 0, 0, 0));

        Button homeBtn = createNavButton("🏠");
        homeBtn.setOnAction(e -> onHome.run());

        Button settingsBtn = createNavButton("⚙");
        settingsBtn.setOnAction(e -> onSettings.run());

        Button importBtn = createNavButton("📥");
        importBtn.setOnAction(e -> onImport.run());

        Button accountsBtn = createNavButton("👤");
        accountsBtn.setOnAction(e -> onAccounts.run());

        this.getChildren().addAll(homeBtn, settingsBtn, importBtn, accountsBtn);
    }

    private Button createNavButton(String icon) {
        Button btn = new Button(icon);
        btn.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #a0a0a0;" +
                        "-fx-font-size: 20px;" +
                        "-fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setStyle(
                "-fx-background-color: #3e3e42;" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 20px;" +
                        "-fx-cursor: hand;"));
        btn.setOnMouseExited(e -> btn.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #a0a0a0;" +
                        "-fx-font-size: 20px;" +
                        "-fx-cursor: hand;"));
        return btn;
    }
}
