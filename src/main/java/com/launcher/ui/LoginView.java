package com.launcher.ui;

import com.launcher.services.MicrosoftAuthService;
import com.launcher.services.SessionService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class LoginView extends VBox {

    private final SessionService session = SessionService.getInstance();
    private final Label statusLabel;
    private final VBox loginContainer;
    private final VBox profileContainer;
    private final Label usernameLabel;

    public LoginView() {
        this.getStyleClass().add("dashboard");
        this.setAlignment(Pos.CENTER);
        this.setSpacing(20);

        Label title = new Label("Accounts");
        title.getStyleClass().add("h1");

        // --- Login Container ---
        loginContainer = new VBox(15);
        loginContainer.setAlignment(Pos.CENTER);
        loginContainer.setMaxWidth(300);
        loginContainer.setStyle("-fx-background-color: #2d2d30; -fx-padding: 30; -fx-background-radius: 10;");

        // Microsoft Login
        Button msLoginBtn = new Button("Login with Microsoft");
        msLoginBtn.setStyle(
                "-fx-background-color: #00a4ef; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 10 20; " +
                        "-fx-cursor: hand;");
        msLoginBtn.setMaxWidth(Double.MAX_VALUE);
        msLoginBtn.setOnAction(e -> handleMicrosoftLogin());

        Separator sep = new Separator();

        // Offline Login
        TextField userField = new TextField();
        userField.setPromptText("Username (Offline)");
        userField.setStyle("-fx-background-color: #3e3e42; -fx-text-fill: white; -fx-padding: 10;");

        Button offlineLoginBtn = new Button("Login Offline");
        offlineLoginBtn.setStyle(
                "-fx-background-color: #606060; " +
                        "-fx-text-fill: white; " +
                        "-fx-padding: 8 15; " +
                        "-fx-cursor: hand;");
        offlineLoginBtn.setMaxWidth(Double.MAX_VALUE);
        offlineLoginBtn.setOnAction(e -> handleOfflineLogin(userField.getText()));

        loginContainer.getChildren().addAll(new Label("Sign in to play"), msLoginBtn, sep, userField, offlineLoginBtn);

        // --- Profile Container (Hidden by default) ---
        profileContainer = new VBox(15);
        profileContainer.setAlignment(Pos.CENTER);
        profileContainer.setVisible(false);
        profileContainer.setManaged(false);

        usernameLabel = new Label("Steve");
        usernameLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: white;");

        Button logoutBtn = new Button("Logout");
        logoutBtn.setStyle("-fx-background-color: #e81123; -fx-text-fill: white; -fx-cursor: hand;");
        logoutBtn.setOnAction(e -> handleLogout());

        profileContainer.getChildren().addAll(new Label("Currently logged in as:"), usernameLabel, logoutBtn);

        // Status
        statusLabel = new Label("");

        this.getChildren().addAll(title, loginContainer, profileContainer, statusLabel);

        updateUI();
    }

    private void handleMicrosoftLogin() {
        statusLabel.setText("Connecting to Microsoft...");
        statusLabel.setStyle("-fx-text-fill: #a0a0a0;");

        loginContainer.setDisable(true);

        MicrosoftAuthService authService = new MicrosoftAuthService();
        authService.requestDeviceCode().thenAccept(response -> {
            javafx.application.Platform.runLater(() -> {
                if (response == null) {
                    statusLabel.setText("Failed to connect.");
                    statusLabel.setStyle("-fx-text-fill: #e81123;");
                    loginContainer.setDisable(false);
                    return;
                }

                // Show Code
                loginContainer.getChildren().clear();

                Label instr = new Label("1. Go to: " + response.verification_uri);
                instr.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");

                TextField codeField = new TextField(response.user_code);
                codeField.setEditable(false);
                codeField.setStyle(
                        "-fx-font-size: 24px; -fx-alignment: center; -fx-background-color: #3e3e42; -fx-text-fill: #4caf50;");
                codeField.setMaxWidth(200);

                Label instr2 = new Label("2. Enter the code above.");
                instr2.setStyle("-fx-text-fill: #a0a0a0;");

                ProgressBar spinner = new ProgressBar();

                Button cancelBtn = new Button("Cancel");
                cancelBtn.setOnAction(e -> {
                    updateUI(); // Reset
                    loginContainer.setDisable(false);
                });

                loginContainer.getChildren().addAll(instr, codeField, instr2, spinner, cancelBtn);

                // Poll
                authService.pollForToken(response).thenAccept(authResult -> {
                    javafx.application.Platform.runLater(() -> {
                        if (authResult.error != null) {
                            statusLabel.setText("Login failed: " + authResult.error);
                            statusLabel.setStyle("-fx-text-fill: #e81123;");
                            updateUI(); // Reset
                        } else {
                            session.loginMicrosoft(authResult.username, authResult.uuid, authResult.accessToken);
                            updateUI();
                            statusLabel.setText("Logged in as " + authResult.username);
                            statusLabel.setStyle("-fx-text-fill: #4caf50;");
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
            statusLabel.setStyle("-fx-text-fill: #e81123;");
            return;
        }
        session.loginOffline(username);
        updateUI();
        statusLabel.setText("Logged in offline.");
        statusLabel.setStyle("-fx-text-fill: #a0a0a0;");
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
            usernameLabel.setText(session.getCurrentUser());
        }
    }
}
