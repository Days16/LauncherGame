package com.launcher.services;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JavaRuntimeService {

    private static final String BASE_URL = "https://api.adoptium.net/v3/binary/latest/%d/ga/windows/x64/jre/hotspot/normal/eclipse";
    private final String runtimesDir = System.getenv("APPDATA") + "/.minecraft/runtimes";

    public String getJavaPath(int majorVersion, Consumer<String> statusCallback) {
        File javaDir = new File(runtimesDir, "java-" + majorVersion);
        File javaExe = new File(javaDir, "bin/java.exe");

        // Check if exists
        if (javaExe.exists()) {
            return javaExe.getAbsolutePath();
        }

        // Download
        statusCallback.accept("Downloading Java " + majorVersion + " Runtime...");
        try {
            javaDir.mkdirs();
            String downloadUrl = String.format(BASE_URL, majorVersion);
            File zipFile = new File(runtimesDir, "java-" + majorVersion + ".zip");

            downloadFile(downloadUrl, zipFile);

            // Unzip
            statusCallback.accept("Installing Java " + majorVersion + "...");
            unzip(zipFile, javaDir);

            // Cleanup
            zipFile.delete();

            // Find java.exe (it might be in a subfolder after unzip)
            return findJavaExe(javaDir);

        } catch (Exception e) {
            e.printStackTrace();
            statusCallback.accept("Failed to download Java: " + e.getMessage());
            // Fallback to system java
            return "java";
        }
    }

    private void downloadFile(String urlStr, File target) throws IOException {
        URL url = new URL(urlStr);
        try (BufferedInputStream in = new BufferedInputStream(url.openStream());
                FileOutputStream fileOutputStream = new FileOutputStream(target)) {
            byte dataBuffer[] = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead);
            }
        }
    }

    private void unzip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(destDir, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
        }
    }

    private File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    private String findJavaExe(File dir) {
        // BFS or DFS to find bin/java.exe
        // The zip usually contains a root folder like 'jdk-21.0.1+12-jre'
        try {
            return Files.walk(dir.toPath())
                    .filter(p -> p.toString().endsWith("bin\\java.exe") || p.toString().endsWith("bin/java.exe"))
                    .findFirst()
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .orElse("java");
        } catch (IOException e) {
            e.printStackTrace();
            return "java";
        }
    }
}
