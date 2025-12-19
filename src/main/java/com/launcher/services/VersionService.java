package com.launcher.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VersionService {

    private static final String MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest.json";
    private final Gson gson = new Gson();

    public CompletableFuture<List<VersionInfo>> getVersions() {
        return CompletableFuture.supplyAsync(() -> {
            List<VersionInfo> versions = new ArrayList<>();
            try {
                URL url = new URL(MANIFEST_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                JsonObject json = gson.fromJson(reader, JsonObject.class);
                reader.close();

                JsonArray versionArray = json.getAsJsonArray("versions");
                for (JsonElement element : versionArray) {
                    JsonObject versionObj = element.getAsJsonObject();
                    String type = versionObj.get("type").getAsString();
                    String id = versionObj.get("id").getAsString();
                    String vUrl = versionObj.get("url").getAsString();

                    versions.add(new VersionInfo(id, type, vUrl));
                }
            } catch (Exception e) {
                e.printStackTrace();
                versions.add(new VersionInfo("Error", "release", ""));
            }
            return versions;
        });
    }
}
