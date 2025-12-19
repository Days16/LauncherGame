package com.launcher.services;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import com.launcher.services.VersionInfo;

public class ModpackService {

    public enum ModpackType {
        CURSEFORGE,
        MODRINTH,
        MULTIMC,
        TECHNIC,
        UNKNOWN
    }

    public ModpackType detectModpackType(File file) {
        if (!file.getName().endsWith(".zip") && !file.getName().endsWith(".mrpack")) {
            return ModpackType.UNKNOWN;
        }

        try (ZipFile zipFile = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();

                if (name.equals("manifest.json")) {
                    return ModpackType.CURSEFORGE;
                }
                if (name.equals("modrinth.index.json")) {
                    return ModpackType.MODRINTH;
                }
                if (name.equals("instance.cfg") || name.equals("mmc-pack.json")) {
                    return ModpackType.MULTIMC;
                }
                if (name.contains("bin/modpack.jar")) {
                    return ModpackType.TECHNIC;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ModpackType.UNKNOWN;
    }

    public java.util.List<VersionInfo> getModpacksAsVersions() {
        java.util.List<VersionInfo> modpacks = new java.util.ArrayList<>();
        File modpacksDir = new File(System.getProperty("user.home") + "/Documents/MinecraftLauncher/modpacks");

        if (modpacksDir.exists() && modpacksDir.isDirectory()) {
            File[] files = modpacksDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        // Treat directory name as modpack ID
                        modpacks.add(new VersionInfo(file.getName(), "modpack", null));
                    }
                }
            }
        }
        return modpacks;
    }
}
