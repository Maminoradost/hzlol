package com.valkyrie.launcher;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

final class LauncherConfig {
    static final Path DIRECTORY = Path.of(System.getenv().getOrDefault("APPDATA", System.getProperty("user.home")), "ValkyrieLauncher");
    private static final Path FILE = DIRECTORY.resolve("launcher.properties");

    String nickname = "Player";
    Path contentDirectory = DIRECTORY.resolve("game");
    Path instanceDirectory = DIRECTORY.resolve("game");
    String forgeProfile = GameInstallationService.FORGE_PROFILE;
    String manifestUrl = "";
    int memoryMb = 4096;
    boolean automaticMemory = true;
    String selectedVersion = GameInstallationService.MINECRAFT_VERSION;
    boolean selectedValkyrie = true;
    String javaMode = "automatic";
    String customJava = "";
    String theme = "light";
    boolean reducedMotion;
    boolean uiSounds = true;
    boolean changelogOpen;
    boolean needsMigration;

    static LauncherConfig load() {
        LauncherConfig config = new LauncherConfig();
        if (!Files.isRegularFile(FILE)) {
            config.migrateGameLayout();
            return config;
        }
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            values.load(reader);
            config.nickname = values.getProperty("nickname", config.nickname);
            if (values.containsKey("sourceGameDirectory") || values.containsKey("gameDirectory")) config.needsMigration = true;
            config.forgeProfile = GameInstallationService.FORGE_PROFILE;
            config.manifestUrl = values.getProperty("manifestUrl", "").trim();
            config.memoryMb = Integer.parseInt(values.getProperty("memoryMb", "4096"));
            config.automaticMemory = Boolean.parseBoolean(values.getProperty("automaticMemory", "true"));
            config.selectedVersion = values.getProperty("selectedVersion", GameInstallationService.MINECRAFT_VERSION);
            config.selectedValkyrie = Boolean.parseBoolean(values.getProperty("selectedValkyrie", "true"));
            config.javaMode = values.getProperty("javaMode", "automatic");
            config.customJava = values.getProperty("customJava", "");
            // Значение уходит в CSS-класс "theme-<...>", поэтому допускаются только
            // известные темы: посторонняя строка дала бы селектор, который ни с чем
            // не совпадает, и окно молча осталось бы светлым.
            config.theme = "dark".equals(values.getProperty("theme", "light")) ? "dark" : "light";
            config.reducedMotion = Boolean.parseBoolean(values.getProperty("reducedMotion", "false"));
            config.uiSounds = Boolean.parseBoolean(values.getProperty("uiSounds", "true"));
            config.changelogOpen = Boolean.parseBoolean(values.getProperty("changelogOpen", "false"));
            if (values.containsKey("background")) {
                config.theme = "light";
                config.needsMigration = true;
            }
        } catch (IOException | RuntimeException ignored) {
        }
        config.migrateGameLayout();
        config.select(config.selectedValkyrie
            ? GameVersion.VALKYRIE
            : new GameVersion(config.selectedVersion, "release", null, "", false));
        return config;
    }

    void save() throws IOException {
        Files.createDirectories(DIRECTORY);
        Properties values = new Properties();
        values.setProperty("nickname", nickname);
        values.setProperty("forgeProfile", forgeProfile);
        values.setProperty("manifestUrl", manifestUrl);
        values.setProperty("memoryMb", Integer.toString(memoryMb));
        values.setProperty("automaticMemory", Boolean.toString(automaticMemory));
        values.setProperty("selectedVersion", selectedVersion);
        values.setProperty("selectedValkyrie", Boolean.toString(selectedValkyrie));
        values.setProperty("javaMode", javaMode);
        values.setProperty("customJava", customJava);
        values.setProperty("theme", theme);
        values.setProperty("reducedMotion", Boolean.toString(reducedMotion));
        values.setProperty("uiSounds", Boolean.toString(uiSounds));
        values.setProperty("changelogOpen", Boolean.toString(changelogOpen));
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary)) {
            values.store(writer, "Valkyrie Launcher");
        }
        try {
            Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    void select(GameVersion version) {
        selectedVersion = version.id();
        selectedValkyrie = version.valkyrie();
        forgeProfile = version.profileId();
        contentDirectory = DIRECTORY.resolve("game");
        instanceDirectory = version.valkyrie() ? contentDirectory : DIRECTORY.resolve("instances").resolve(version.instanceId());
    }

    int effectiveMemoryMb() {
        if (!automaticMemory) return Math.max(1024, memoryMb);
        long systemMb = com.sun.management.OperatingSystemMXBean.class.cast(
            java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize() / 1024 / 1024;
        if (systemMb <= 4096) return 2048;
        if (systemMb <= 8192) return 3072;
        if (systemMb <= 16384) return 4096;
        return 6144;
    }

    private void migrateGameLayout() {
        Path game = DIRECTORY.resolve("game");
        try {
            mergeDirectory(DIRECTORY.resolve("content"), game);
            mergeDirectory(DIRECTORY.resolve("instance"), game);
            contentDirectory = game;
            instanceDirectory = game;
        } catch (IOException error) {
            LauncherLog.error("Не удалось объединить старые игровые директории в " + game, error);
        }
    }

    private static void mergeDirectory(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source) || source.equals(target)) return;
        Files.createDirectories(target);
        try (var children = Files.list(source)) {
            for (Path child : children.toList()) {
                Path destination = target.resolve(child.getFileName());
                if (Files.isDirectory(child) && Files.isDirectory(destination)) mergeDirectory(child, destination);
                else if (!Files.exists(destination)) Files.move(child, destination);
            }
        }
        try (var remaining = Files.list(source)) {
            if (remaining.findAny().isEmpty()) {
                Files.deleteIfExists(source);
            } else {
                Path backup = target.resolve("migration-backup").resolve(source.getFileName());
                Files.createDirectories(backup.getParent());
                mergeDirectory(source, backup);
            }
        }
    }
}
