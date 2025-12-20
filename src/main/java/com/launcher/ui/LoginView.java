package com.launcher.ui;

import com.launcher.services.MicrosoftAuthService;
import com.launcher.services.SessionService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class LoginView extends VBox {

    private final SessionService session = SessionService.getInstance();
    private final Label statusLabel;
    private final VBox loginContainer;
    private final VBox profileContainer;
    private final Label usernameLabel;

    public LoginView() {
        this.getStyleClass().add("dashboard");
        this.setAlignment(Pos.CENTER);
        this.setSpacing(30);
        this.setPadding(new Insets(40));

        Label title = new Label("ACCOUNTS");
        title.getStyleClass().add("h1");

        // --- LOGIN CONTAINER ---
        loginContainer = new VBox(25);
        loginContainer.setAlignment(Pos.CENTER);
        loginContainer.setMaxWidth(400);
        loginContainer.getStyleClass().add("modpack-card");
        loginContainer.setPadding(new Insets(40));

        // Microsoft Login (Primary)
        Button msLoginBtn = new Button("LOGIN WITH MICROSOFT");
        msLoginBtn.getStyleClass().add("play-button");
        msLoginBtn.setMaxWidth(Double.MAX_VALUE);
        msLoginBtn.setOnAction(e -> handleMicrosoftLogin());

        HBox orBox = new HBox(10);
        orBox.setAlignment(Pos.CENTER);
        Region l1 = new Region();
        HBox.setHgrow(l1, Priority.ALWAYS);
        l1.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-pref-height: 1;");
        Label orLabel = new Label("OR");
        orLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 10px; -fx-font-weight: bold;");
        Region l2 = new Region();
        HBox.setHgrow(l2, Priority.ALWAYS);
        l2.setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-pref-height: 1;");
        orBox.getChildren().addAll(l1, orLabel, l2);

        // Offline Login (Secondary)
        VBox offlineBox = new VBox(15);
        offlineBox.setAlignment(Pos.CENTER);

        TextField userField = new TextField();
        userField.setPromptText("Enter Offline Username");
        userField.getStyleClass().add("combo-box");
        userField.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-text-fill: white; -fx-padding: 12;");

        Button offlineLoginBtn = new Button("LOGIN OFFLINE");
        offlineLoginBtn.getStyleClass().add("nav-button");
        offlineLoginBtn.setAlignment(Pos.CENTER);
        offlineLoginBtn.setMaxWidth(Double.MAX_VALUE);
        offlineLoginBtn
                .setStyle("-fx-background-color: rgba(255,255,255,0.05); -fx-text-fill: white; -fx-font-weight: bold;");
        offlineLoginBtn.setOnAction(e -> handleOfflineLogin(userField.getText()));

        offlineBox.getChildren().addAll(userField, offlineLoginBtn);

        loginContainer.getChildren().addAll(new Label("Sign in to your account"), msLoginBtn, orBox, offlineBox);

        // --- PROFILE CONTAINER ---
        profileContainer = new VBox(20);
        profileContainer.setAlignment(Pos.CENTER);
        profileContainer.setVisible(false);
        profileContainer.setManaged(false);
        profileContainer.getStyleClass().add("modpack-card");
        profileContainer.setPadding(new Insets(40));
        profileContainer.setMaxWidth(400);

        usernameLabel = new Label("Steve");
        usernameLabel.getStyleClass().add("h1");
        usernameLabel.setStyle("-fx-text-fill: #10b981;");

        Button logoutBtn = new Button("LOGOUT");
        logoutBtn.getStyleClass().add("play-button");
        logoutBtn.setStyle(
                "-fx-background-color: #ef4444; -fx-effect: dropshadow(three-pass-box, rgba(239, 68, 68, 0.4), 20, 0, 0, 8);");
        logoutBtn.setOnAction(e -> handleLogout());

        profileContainer.getChildren().addAll(new Label("Currently logged in as"), usernameLabel, logoutBtn);

        // Status
        statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #8e8e93; -fx-font-size: 12px;");

        this.getChildren().addAll(title, loginContainer, profileContainer, statusLabel);

        updateUI();
    }

    private void handleMicrosoftLogin() {
        statusLabel.setText("Connecting to Microsoft...");
        loginContainer.setDisable(true);

        MicrosoftAuthService authService = new MicrosoftAuthService();
        authService.requestDeviceCode().thenAccept(response -> {
            javafx.application.Platform.runLater(() -> {
                if (response == null) {
                    statusLabel.setText("Failed to connect.");
                    loginContainer.setDisable(false);
                    return;
                }

                loginContainer.getChildren().clear();
                Label instr = new Label("1. Go to: " + response.verification_uri);
                instr.setStyle("-fx-text-fill: white;");

                TextField codeField = new TextField(response.user_code);
                codeField.setEditable(false);
                codeField.getStyleClass().add("combo-box");
                codeField.setStyle(
                        "-fx-font-size: 24px; -fx-alignment: center; -fx-text-fill: #10b981; -fx-font-weight: bold;");
                codeField.setMaxWidth(250);

                Label instr2 = new Label("2. Enter the code above.");
                instr2.setStyle("-fx-text-fill: #8e8e93;");

                ProgressBar spinner = new ProgressBar();
                spinner.setPrefWidth(200);

                Button cancelBtn = new Button("CANCEL");
                cancelBtn.getStyleClass().add("nav-button");
                cancelBtn.setAlignment(Pos.CENTER);
                cancelBtn.setOnAction(e -> {
                    updateUI();
                    loginContainer.setDisable(false);
                });

                loginContainer.getChildren().addAll(instr, codeField, instr2, spinner, cancelBtn);

                authService.pollForToken(response).thenAccept(authResult -> {
                    javafx.application.Platform.runLater(() -> {
                        if (authResult.error != null) {
                            statusLabel.setText("Login failed: " + authResult.error);
                            updateUI();
                        } else {
                            session.loginMicrosoft(authResult.username, authResult.uuid, authResult.accessToken);
                            updateUI();
                            statusLabel.setText("Logged in as " + authResult.username);
                        }
                        loginContainer.setDisable(false);
                    });
                });
            });
        });
    }

    private void handleOfflineLogin(String username) {
        if (username.trim().isEmpty()) {
            statusLabel.setText("Please enter a username.");
            return;
        }
        session.loginOffline(username);
        updateUI();
        statusLabel.setText("Logged in offline.");
    }

    private void handleLogout() {
        session.logout();
        updateUI();
        statusLabel.setText("Logged out.");
    }

    private void updateUI() {
        boolean loggedIn = session.isLoggedIn();
        loginContainer.setVisible(!loggedIn);
        loginContainer.setManaged(!loggedIn);

        profileContainer.setVisible(loggedIn);
        profileContainer.setManaged(loggedIn);

        if (loggedIn) {
            usernameLabel.setText(session.getCurrentUser().toUpperCase());
        }
    }
}
