package com.valkyrie.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.jar.JarFile;

final class MinecraftLauncher {
    Process launch(LauncherConfig config) throws IOException {
        return launch(config, message -> {
        }, fraction -> {
        });
    }

    /**
     * @param status   текстовая фаза для UI
     * @param progress доля 0..1, либо -1 для неопределённой фазы
     */
    Process launch(LauncherConfig config, Consumer<String> status, DoubleConsumer progress) throws IOException {
        List<String> command = command(config, status, progress);
        Path game = config.instanceDirectory.toAbsolutePath().normalize();
        Path logs = game.resolve("logs");
        Files.createDirectories(game);
        Files.createDirectories(logs);
        Path processLog = logs.resolve("minecraft-process.log");
        LauncherLog.info("Запуск Minecraft: game=" + game + ", java=" + command.getFirst());
        LauncherLog.info("Команда: " + safeCommand(command));
        Process process = new ProcessBuilder(command)
            .directory(game.toFile())
            .redirectErrorStream(true)
            .redirectOutput(processLog.toFile())
            .start();
        LauncherLog.info("Minecraft process started: pid=" + process.pid() + ", output=" + processLog);
        process.onExit().thenAccept(exited -> LauncherLog.info("Minecraft process exited: pid=" + exited.pid() + ", code=" + exited.exitValue()));
        return process;
    }

    List<String> command(LauncherConfig config) throws IOException {
        return command(config, message -> {
        }, fraction -> {
        });
    }

    List<String> command(LauncherConfig config, Consumer<String> status, DoubleConsumer progress) throws IOException {
        validateNickname(config.nickname);
        Path source = config.contentDirectory.toAbsolutePath().normalize();
        Path game = config.instanceDirectory.toAbsolutePath().normalize();
        Files.createDirectories(game);
        for (String directory : List.of("mods", "config", "logs", "crash-reports", "saves", "resourcepacks", "shaderpacks", "screenshots")) {
            Files.createDirectories(game.resolve(directory));
        }
        Path versionDirectory = source.resolve("versions").resolve(config.forgeProfile);
        Path versionJson = versionDirectory.resolve(config.forgeProfile + ".json");
        if (!Files.isRegularFile(versionJson)) throw new IOException("Forge profile not found: " + config.forgeProfile);

        Map<String, Object> version = loadVersion(source, config.forgeProfile);
        Path natives = game.resolve("natives").resolve(safeFileName(config.forgeProfile));
        Files.createDirectories(natives);
        Set<Path> classpath = new LinkedHashSet<>();
        collectLibraries(version, source, natives, classpath);

        String baseVersion = version.containsKey("jar") ? version.get("jar").toString()
            : version.getOrDefault("inheritsFrom", config.forgeProfile).toString();
        Path baseJar = source.resolve("versions").resolve(baseVersion).resolve(baseVersion + ".jar");
        Path profileJar = versionDirectory.resolve(config.forgeProfile + ".jar");
        if (Files.isRegularFile(baseJar)) classpath.add(baseJar);
        if (Files.isRegularFile(profileJar)) classpath.add(profileJar);

        Map<String, String> variables = variables(config, version, source, game, natives, classpath);
        List<String> result = new ArrayList<>();
        result.add(findJava(config, version, status, progress).toString());
        result.add("-Xms512M");
        result.add("-Xmx" + config.effectiveMemoryMb() + "M");
        result.addAll(arguments(version, "jvm", variables));
        // Mojang metadata uses versioned subdirectories (for example "java"),
        // but natives are extracted directly into our single native directory.
        replaceNativeLibraryPath(result, natives);
        if (!result.contains("-cp")) {
            result.add("-cp");
            result.add(variables.get("classpath"));
        }
        result.add(version.get("mainClass").toString());
        List<String> gameArguments = new ArrayList<>(arguments(version, "game", variables));
        if (gameArguments.isEmpty() && version.get("minecraftArguments") != null) {
            for (String argument : splitArguments(version.get("minecraftArguments").toString())) gameArguments.add(expand(argument, variables));
        }
        result.addAll(gameArguments);
        if (result.stream().anyMatch(argument -> argument.contains("${"))) {
            throw new IOException("Forge profile contains unsupported launch variables");
        }
        return List.copyOf(result);
    }

    static void replaceNativeLibraryPath(List<String> arguments, Path natives) {
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            if (argument.startsWith("-Djava.library.path=")) {
                arguments.set(index, "-Djava.library.path=" + natives);
            }
        }
    }

    private static void collectLibraries(Map<String, Object> version, Path game, Path natives, Set<Path> classpath) throws IOException {
        Object rawLibraries = version.get("libraries");
        if (!(rawLibraries instanceof List<?> libraries)) return;
        for (Object raw : libraries) {
            Map<String, Object> library = Json.object(raw);
            if (!allowed(library)) continue;
            Map<String, Object> downloads = map(library.get("downloads"));
            Map<String, Object> artifact = map(downloads.get("artifact"));
            Path artifactPath = libraryPath(game, library.get("name").toString(), artifact);
            String artifactName = artifactPath.getFileName().toString();
            boolean nativeArtifact = artifactName.contains("natives-windows");
            boolean wrongArchitecture = artifactName.contains("natives-windows-arm64") || artifactName.contains("natives-windows-x86");
            if (wrongArchitecture) continue;
            if (Files.isRegularFile(artifactPath)) {
                if (nativeArtifact) extractNatives(artifactPath, natives);
                else classpath.add(artifactPath);
            }

            Map<String, Object> nativesMap = map(library.get("natives"));
            Object classifierName = nativesMap.get("windows");
            if (classifierName == null) continue;
            String classifier = classifierName.toString().replace("${arch}", System.getProperty("os.arch").contains("64") ? "64" : "32");
            Map<String, Object> classifiers = map(downloads.get("classifiers"));
            Map<String, Object> nativeDownload = map(classifiers.get(classifier));
            Path nativeJar = libraryPath(game, library.get("name") + ":" + classifier, nativeDownload);
            if (Files.isRegularFile(nativeJar)) extractNatives(nativeJar, natives);
        }
    }

    private static Path libraryPath(Path game, String coordinate, Map<String, Object> download) {
        if (download.containsKey("path")) return game.resolve("libraries").resolve(download.get("path").toString());
        String[] parts = coordinate.split(":");
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return game.resolve("libraries").resolve(group).resolve(artifact).resolve(version).resolve(artifact + "-" + version + classifier + ".jar");
    }

    private static void extractNatives(Path jar, Path target) throws IOException {
        try (JarFile archive = new JarFile(jar.toFile())) {
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || name.startsWith("META-INF/") || !(name.endsWith(".dll") || name.endsWith(".so") || name.endsWith(".dylib"))) continue;
                Path output = target.resolve(Path.of(name).getFileName().toString());
                try (InputStream input = archive.getInputStream(entry)) {
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static List<String> arguments(Map<String, Object> version, String kind, Map<String, String> variables) {
        Map<String, Object> arguments = map(version.get("arguments"));
        Object rawArguments = arguments.get(kind);
        if (!(rawArguments instanceof List<?> items)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof String text) {
                result.add(expand(text, variables));
                continue;
            }
            Map<String, Object> entry = Json.object(item);
            if (!allowed(entry)) continue;
            Object value = entry.get("value");
            if (value instanceof List<?> values) {
                for (Object part : values) result.add(expand(part.toString(), variables));
            } else if (value != null) {
                result.add(expand(value.toString(), variables));
            }
        }
        return result;
    }

    static boolean allowed(Map<String, Object> entry) {
        Object rawRules = entry.get("rules");
        if (!(rawRules instanceof List<?> rules)) return true;
        boolean allowed = false;
        for (Object raw : rules) {
            Map<String, Object> rule = Json.object(raw);
            Map<String, Object> os = map(rule.get("os"));
            Map<String, Object> features = map(rule.get("features"));
            boolean matches = (os.isEmpty() || "windows".equals(os.get("name"))) && features.isEmpty();
            if (matches) allowed = "allow".equals(rule.get("action"));
        }
        return allowed;
    }

    private static Map<String, String> variables(LauncherConfig config, Map<String, Object> version, Path source, Path game, Path natives, Set<Path> classpath) {
        String nickname = config.nickname;
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + nickname).getBytes(StandardCharsets.UTF_8));
        String assetIndex = map(version.get("assetIndex")).getOrDefault("id", version.getOrDefault("assets", "27")).toString();
        Map<String, String> values = new HashMap<>();
        values.put("natives_directory", natives.toString());
        values.put("launcher_name", "valkyrie-launcher");
        values.put("launcher_version", LauncherVersion.VALUE);
        values.put("classpath", String.join(System.getProperty("path.separator"), classpath.stream().map(Path::toString).toList()));
        values.put("auth_player_name", nickname);
        values.put("version_name", config.forgeProfile);
        values.put("game_directory", game.toString());
        values.put("assets_root", source.resolve("assets").toString());
        values.put("assets_index_name", assetIndex);
        values.put("auth_uuid", uuid.toString().replace("-", ""));
        values.put("auth_access_token", "0");
        values.put("clientid", "0");
        values.put("auth_xuid", "0");
        values.put("user_type", "legacy");
        values.put("version_type", config.forgeProfile.equals(GameInstallationService.FORGE_PROFILE) ? "Valkyrie" : "release");
        return values;
    }

    private static String expand(String value, Map<String, String> variables) {
        String result = value;
        for (Map.Entry<String, String> variable : variables.entrySet()) result = result.replace("${" + variable.getKey() + "}", variable.getValue());
        return result;
    }

    private static Path findJava(LauncherConfig config, Map<String, Object> version,
        Consumer<String> status, DoubleConsumer progress) throws IOException {
        int required = ((Number) map(version.get("javaVersion")).getOrDefault("majorVersion", 8)).intValue();
        Path executable;
        if ("custom".equals(config.javaMode)) {
            executable = Path.of(config.customJava).toAbsolutePath().normalize();
            if (!Files.isRegularFile(executable)) throw new IOException("Выбранный Java executable не найден: " + executable);
            int actual = javaMajor(executable);
            if (actual != required) throw new IOException("Выбрана Java " + actual + ", а Minecraft " + config.selectedVersion + " требует Java " + required);
        } else {
            executable = automaticJava(required);
            if (executable == null) executable = downloadJava(config, required, status, progress);
        }
        return executable;
    }

    /** Подходящей Java в системе нет — скачиваем официальный рантайм сами. */
    private static Path downloadJava(LauncherConfig config, int required,
        Consumer<String> status, DoubleConsumer progress) throws IOException {
        status.accept("Загрузка Java " + required + "…");
        progress.accept(0);
        try {
            return new JavaRuntimeService(config.contentDirectory)
                .provide(required, false, (done, total) -> {
                    if (total <= 0) return;
                    progress.accept(Math.min(1.0, (double) done / total));
                    if (done == total || done % Math.max(1, total / 4) == 0) {
                        LauncherLog.info("Загрузка Java " + required + ": " + done + "/" + total);
                    }
                });
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Загрузка Java " + required + " прервана", error);
        } catch (IOException error) {
            throw new IOException("Minecraft " + config.selectedVersion + " требует Java " + required
                + ", и её не удалось скачать автоматически (" + error.getMessage()
                + "). Установите Java " + required + " или выберите java.exe в Settings", error);
        }
    }

    private static Path automaticJava(int required) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of(System.getProperty("java.home"), "bin", "java.exe"));
        for (String home : List.of("C:/Program Files/Java", "C:/Program Files/Eclipse Adoptium", "C:/Program Files/Microsoft",
            "C:/Program Files/Zulu", "C:/Program Files/BellSoft", "C:/Program Files (x86)/Java")) {
            Path root = Path.of(home);
            if (!Files.isDirectory(root)) continue;
            try (var directories = Files.list(root)) {
                for (Path directory : directories.toList()) candidates.add(directory.resolve("bin/java.exe"));
            } catch (IOException ignored) {
            }
        }
        for (Path candidate : candidates) {
            if (!Files.isRegularFile(candidate)) continue;
            try {
                if (javaMajor(candidate) == required) {
                    Path javaw = candidate.resolveSibling("javaw.exe");
                    return Files.isRegularFile(javaw) ? javaw : candidate;
                }
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    private static int javaMajor(Path executable) throws IOException {
        Process process = new ProcessBuilder(executable.toString(), "-version").redirectErrorStream(true).start();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("Java version check timed out: " + executable);
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("version \\\"(\\d+)(?:\\.(\\d+))?").matcher(output);
            if (!matcher.find()) throw new IOException("Не удалось определить версию Java: " + executable);
            int first = Integer.parseInt(matcher.group(1));
            return first == 1 && matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : first;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Java version check interrupted", error);
        }
    }

    private static List<String> splitArguments(String arguments) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < arguments.length(); index++) {
            char character = arguments.charAt(index);
            if (character == '"') quoted = !quoted;
            else if (Character.isWhitespace(character) && !quoted) {
                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else current.append(character);
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    private static String safeCommand(List<String> command) {
        List<String> safe = new ArrayList<>(command);
        for (int index = 0; index < safe.size() - 1; index++) {
            if ("--accessToken".equals(safe.get(index))) safe.set(index + 1, "<redacted>");
            if ("-cp".equals(safe.get(index))) safe.set(index + 1, "<classpath:" + safe.get(index + 1).split(java.util.regex.Pattern.quote(System.getProperty("path.separator"))).length + " entries>");
        }
        return String.join(" ", safe.stream().map(value -> value.contains(" ") ? '"' + value + '"' : value).toList());
    }

    private static Map<String, Object> loadVersion(Path source, String id) throws IOException {
        Path file = source.resolve("versions").resolve(id).resolve(id + ".json");
        if (!Files.isRegularFile(file)) throw new IOException("Minecraft profile not found: " + id);
        Map<String, Object> child = new HashMap<>(Json.object(Json.parse(Files.readString(file))));
        Object parentId = child.get("inheritsFrom");
        if (parentId == null) return child;
        Map<String, Object> parent = new HashMap<>(loadVersion(source, parentId.toString()));
        parent.putAll(child);
        List<Object> libraries = new ArrayList<>();
        libraries.addAll(list(loadVersion(source, parentId.toString()).get("libraries")));
        libraries.addAll(list(child.get("libraries")));
        parent.put("libraries", libraries);
        Map<String, Object> parentArguments = new HashMap<>(map(loadVersion(source, parentId.toString()).get("arguments")));
        Map<String, Object> childArguments = map(child.get("arguments"));
        for (String kind : List.of("jvm", "game")) {
            List<Object> merged = new ArrayList<>(list(parentArguments.get(kind)));
            merged.addAll(list(childArguments.get(kind)));
            parentArguments.put(kind, merged);
        }
        parent.put("arguments", parentArguments);
        return parent;
    }

    private static void validateNickname(String nickname) {
        if (nickname == null || !nickname.matches("[A-Za-z0-9_]{3,16}")) {
            throw new IllegalArgumentException("Nickname must contain 3-16 Latin letters, numbers or underscores");
        }
    }

    private static String safeFileName(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return value instanceof List<?> ? (List<Object>) value : List.of();
    }
}
