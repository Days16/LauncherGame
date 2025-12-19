package com.launcher.services;

public class VersionInfo {
    private final String id;
    private final String type;
    private final String url;

    public VersionInfo(String id, String type, String url) {
        this.id = id;
        this.type = type;
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public String toString() {
        return id + " (" + type + ")";
    }
}
