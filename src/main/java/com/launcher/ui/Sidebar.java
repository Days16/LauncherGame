package com.launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import java.util.ArrayList;
import java.util.List;

public class Sidebar extends VBox {

    private final List<Button> navButtons = new ArrayList<>();

    public Sidebar(Runnable onHome, Runnable onSettings, Runnable onImport, Runnable onAccounts) {
        this.getStyleClass().add("sidebar");
        this.setPrefWidth(200);
        this.setAlignment(Pos.TOP_LEFT);
        this.setSpacing(5);
        this.setPadding(new Insets(30, 15, 15, 15));

        Button homeBtn = createNavButton("🏠  Instances", onHome);
        Button importBtn = createNavButton("📥  Import Modpack", onImport);
        Button accountsBtn = createNavButton("👤  Accounts", onAccounts);
        Button settingsBtn = createNavButton("⚙  Settings", onSettings);

        // Add a spacer to push settings to the bottom
        VBox spacer = new VBox();
        VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        this.getChildren().addAll(homeBtn, importBtn, accountsBtn, spacer, settingsBtn);

        // Set home as active by default
        setActive(homeBtn);
    }

    private Button createNavButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);

        btn.setOnAction(e -> {
            setActive(btn);
            action.run();
        });

        navButtons.add(btn);
        return btn;
    }

    private void setActive(Button activeBtn) {
        for (Button btn : navButtons) {
            btn.getStyleClass().remove("active");
        }
        activeBtn.getStyleClass().add("active");
    }
}
