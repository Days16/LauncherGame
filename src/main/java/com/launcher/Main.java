package com.launcher;

import com.launcher.ui.CustomTitleBar;
import com.launcher.ui.DashboardView;
import com.launcher.ui.ImportView;
import com.launcher.ui.LoginView;
import com.launcher.ui.SettingsView;
import com.launcher.ui.Sidebar;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class Main extends Application {

    private BorderPane root;
    private DashboardView dashboardView;
    private SettingsView settingsView;
    private ImportView importView;
    private LoginView loginView;

    @Override
    public void start(Stage primaryStage) {
        System.out.println("Application starting...");
        root = new BorderPane();

        // Custom Title Bar
        CustomTitleBar titleBar = new CustomTitleBar(primaryStage);
        root.setTop(titleBar);

        // Views
        dashboardView = new DashboardView();
        settingsView = new SettingsView();
        importView = new ImportView();
        loginView = new LoginView();

        // Sidebar with Navigation Logic
        Sidebar sidebar = new Sidebar(
                () -> {
                    dashboardView.refreshVersions();
                    dashboardView.refreshRemoteModpacks();
                    root.setCenter(dashboardView);
                }, // On Home
                () -> root.setCenter(settingsView), // On Settings
                () -> root.setCenter(importView), // On Import
                () -> root.setCenter(loginView) // On Accounts
        );
        root.setLeft(sidebar);

        // Default View
        root.setCenter(dashboardView);

        // Scene
        Scene scene = new Scene(root, 1000, 700);
        scene.setFill(Color.TRANSPARENT);

        // Load CSS
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());

        // Stage Configuration
        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setTitle("Antigravity Launcher");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
