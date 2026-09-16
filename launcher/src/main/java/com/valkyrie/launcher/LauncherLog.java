package com.valkyrie.launcher;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class LauncherLog {
    private static final Path FILE = LauncherConfig.DIRECTORY.resolve("logs").resolve("launcher.log");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private LauncherLog() {}

    static void info(String message) {
        write("INFO", message, null);
    }

    static void error(String message, Throwable error) {
        write("ERROR", message, error);
    }

    private static synchronized void write(String level, String message, Throwable error) {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.isRegularFile(FILE) && Files.size(FILE) > 2 * 1024 * 1024) {
                Files.move(FILE, FILE.resolveSibling("launcher-previous.log"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            StringBuilder entry = new StringBuilder()
                .append('[').append(TIME.format(LocalDateTime.now())).append("] [")
                .append(level).append("] ").append(message).append(System.lineSeparator());
            if (error != null) {
                StringWriter trace = new StringWriter();
                error.printStackTrace(new PrintWriter(trace));
                entry.append(trace);
            }
            Files.writeString(FILE, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
