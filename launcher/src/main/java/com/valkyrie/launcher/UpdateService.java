package com.valkyrie.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

final class UpdateService {
    private static final String BUNDLED_VERSION = "0.9.0-beta";
    private static final String BUNDLED_SHA256 = "b92261b851e0be1e2f4e89b7b9c3799c86b85f4a94befa3610c0d12cb466a5ec";
    record Manifest(String version, String minecraftVersion, String forgeProfile, URI jarUri, String sha256, List<String> changelog) {}

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).followRedirects(HttpClient.Redirect.NORMAL).build();

    Manifest loadManifest(LauncherConfig config) throws IOException, InterruptedException {
        String content;
        URI manifestUri;
        boolean localManifest;
        Path local = Path.of("update-manifest.json").toAbsolutePath();
        if (!config.manifestUrl.isBlank()) {
            localManifest = false;
            manifestUri = URI.create(config.manifestUrl);
            if (!"https".equalsIgnoreCase(manifestUri.getScheme())) throw new IOException("Manifest URL must use HTTPS");
            HttpRequest request = HttpRequest.newBuilder(manifestUri).timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IOException("Manifest HTTP " + response.statusCode());
            content = response.body();
        } else if (Files.isRegularFile(local)) {
            localManifest = true;
            manifestUri = local.toUri();
            content = Files.readString(local);
        } else {
            return null;
        }
        Map<String, Object> json = Json.object(Json.parse(content));
        String minecraftVersion = json.get("minecraftVersion").toString();
        if (!"1.21.10".equals(minecraftVersion)) {
            throw new IOException("Unsupported Minecraft version: " + minecraftVersion);
        }
        List<String> changes = new ArrayList<>();
        for (Object item : Json.array(json.get("changelog"))) changes.add(item.toString());
        URI jarUri = manifestUri.resolve(json.get("jarUrl").toString());
        boolean secureRemote = "https".equalsIgnoreCase(jarUri.getScheme());
        boolean localFile = localManifest && "file".equalsIgnoreCase(jarUri.getScheme());
        if (!secureRemote && !localFile) {
            throw new IOException("Client download must use HTTPS");
        }
        String sha256 = json.get("sha256").toString().toLowerCase();
        if (!sha256.matches("[0-9a-f]{64}")) throw new IOException("Manifest contains an invalid SHA-256");
        return new Manifest(json.get("version").toString(), minecraftVersion,
            json.get("forgeProfile").toString(), jarUri, sha256, List.copyOf(changes));
    }

    Path install(Manifest manifest, LauncherConfig config) throws IOException, InterruptedException {
        Path mods = config.instanceDirectory.resolve("mods");
        Files.createDirectories(mods);
        Path target = mods.resolve("valkyrieclient-" + manifest.version + ".jar");
        if (Files.isRegularFile(target) && sha256(target).equals(manifest.sha256)) return target;

        // Качаем во временную папку ВНЕ mods/: Forge сканирует mods/*.jar,
        // и оборванная загрузка (краш, потеря сети) оставила бы там битый
        // полу-jar, который игра попытается загрузить при следующем старте.
        // Папка — сосед mods/ на том же томе, чтобы ATOMIC_MOVE работал.
        Path temporary = Files.createTempFile(stagingDirectory(config), "valkyrie-download-", ".tmp");
        try {
            if ("file".equalsIgnoreCase(manifest.jarUri.getScheme())) {
                Files.copy(Path.of(manifest.jarUri), temporary, StandardCopyOption.REPLACE_EXISTING);
            } else {
                HttpRequest request = HttpRequest.newBuilder(manifest.jarUri).timeout(Duration.ofMinutes(2)).GET().build();
                HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
                if (response.statusCode() != 200) throw new IOException("Client HTTP " + response.statusCode());
            }
            if (!sha256(temporary).equals(manifest.sha256)) throw new IOException("Downloaded client checksum does not match manifest");
            Path backup = mods.resolve("valkyrie-backup");
            Files.createDirectories(backup);
            try (var files = Files.list(mods)) {
                for (Path old : files.filter(path -> path.getFileName().toString().matches("valkyrieclient-.*\\.jar") && !path.equals(target)).toList()) {
                    Files.move(old, backup.resolve(old.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    Path installBundled(LauncherConfig config) throws IOException {
        Path mods = config.instanceDirectory.resolve("mods");
        Files.createDirectories(mods);
        Path target = mods.resolve("valkyrieclient-" + BUNDLED_VERSION + ".jar");
        if (Files.isRegularFile(target) && sha256(target).equals(BUNDLED_SHA256)) return target;
        Path temporary = Files.createTempFile(stagingDirectory(config), "valkyrie-bundled-", ".tmp");
        try {
            try (InputStream input = UpdateService.class.getResourceAsStream("/bundled/valkyrieclient.jar")) {
                if (input == null) throw new IOException("Bundled Valkyrie client is missing");
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!sha256(temporary).equals(BUNDLED_SHA256)) {
                throw new IOException("Bundled Valkyrie client checksum mismatch");
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Staging-папка для загрузок: рядом с mods/, но невидима для сканера Forge. */
    private static Path stagingDirectory(LauncherConfig config) throws IOException {
        Path staging = config.instanceDirectory.resolve(".valkyrie-staging");
        Files.createDirectories(staging);
        return staging;
    }

    static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception error) {
            if (error instanceof IOException io) throw io;
            throw new IOException("SHA-256 is unavailable", error);
        }
    }
}
