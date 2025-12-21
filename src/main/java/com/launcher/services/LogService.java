package com.launcher.services;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogService {
    private static final String LOG_DIR = System.getProperty("user.home") + "/Documents/MinecraftLauncher/logs";
    private static final String LOG_FILE = LOG_DIR + "/launcher.log";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    static {
        try {
            new File(LOG_DIR).mkdirs();
        } catch (Exception e) {
            System.err.println("Failed to create log directory: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void info(String message) {
        log("INFO", message);
    }

    public static void error(String message) {
        log("ERROR", message);
    }

    public static void warn(String message) {
        log("WARN", message);
    }

    public static void error(String message, Throwable throwable) {
        log("ERROR", message);
        if (throwable != null) {
            try (FileWriter fw = new FileWriter(LOG_FILE, true);
                    PrintWriter pw = new PrintWriter(fw)) {
                throwable.printStackTrace(pw);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static synchronized void log(String level, String message) {
        String timestamp = DATE_FORMAT.format(new Date());
        String logEntry = String.format("[%s] [%s] %s%n", timestamp, level, message);

        System.out.print(logEntry); // Also print to console

        try (FileWriter fw = new FileWriter(LOG_FILE, true);
                PrintWriter pw = new PrintWriter(fw)) {
            pw.print(logEntry);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getLogPath() {
        return LOG_FILE;
    }
}
