package com.launcher.services;

public class SessionService {
    private static SessionService instance;
    private String currentUser;
    private String uuid;
    private String accessToken;
    private boolean isOffline;

    private final com.google.gson.Gson gson = new com.google.gson.Gson();
    private final java.io.File sessionFile = new java.io.File(
            System.getenv("APPDATA") + "/.minecraft/launcher_session.json");

    private SessionService() {
        loadSession();
    }

    public static SessionService getInstance() {
        if (instance == null) {
            instance = new SessionService();
        }
        return instance;
    }

    public void loginMicrosoft(String username, String uuid, String accessToken) {
        this.currentUser = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.isOffline = false;
        saveSession();
    }

    public void loginOffline(String username) {
        this.currentUser = username;
        this.isOffline = true;
        saveSession();
    }

    public void logout() {
        this.currentUser = null;
        this.uuid = null;
        this.accessToken = null;
        if (sessionFile.exists()) {
            sessionFile.delete();
        }
    }

    private void saveSession() {
        try (java.io.FileWriter writer = new java.io.FileWriter(sessionFile)) {
            SessionData data = new SessionData(currentUser, uuid, accessToken, isOffline);
            gson.toJson(data, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadSession() {
        if (!sessionFile.exists())
            return;
        try (java.io.FileReader reader = new java.io.FileReader(sessionFile)) {
            SessionData data = gson.fromJson(reader, SessionData.class);
            if (data != null) {
                this.currentUser = data.username;
                this.uuid = data.uuid;
                this.accessToken = data.accessToken;
                this.isOffline = data.isOffline;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getCurrentUser() {
        return currentUser;
    }

    public String getUuid() {
        return uuid;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    private static class SessionData {
        String username;
        String uuid;
        String accessToken;
        boolean isOffline;

        SessionData(String username, String uuid, String accessToken, boolean isOffline) {
            this.username = username;
            this.uuid = uuid;
            this.accessToken = accessToken;
            this.isOffline = isOffline;
        }
    }
}
