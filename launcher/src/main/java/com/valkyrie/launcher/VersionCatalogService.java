package com.valkyrie.launcher;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class VersionCatalogService {
    private static final URI MANIFEST = URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    private static final Path CACHE = LauncherConfig.DIRECTORY.resolve("cache/version_manifest_v2.json");
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();

    List<GameVersion> load() throws IOException, InterruptedException {
        String content;
        try {
            HttpRequest request = HttpRequest.newBuilder(MANIFEST).timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IOException("Version manifest HTTP " + response.statusCode());
            content = response.body();
            Files.createDirectories(CACHE.getParent());
            Files.writeString(CACHE, content, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            if (!Files.isRegularFile(CACHE)) throw error;
            content = Files.readString(CACHE, StandardCharsets.UTF_8);
            LauncherLog.info("Используется сохранённый каталог версий Mojang");
        }

        List<GameVersion> result = new ArrayList<>();
        result.add(GameVersion.VALKYRIE);
        for (Object raw : Json.array(Json.object(Json.parse(content)).get("versions"))) {
            Map<String, Object> version = Json.object(raw);
            String id = version.get("id").toString();
            String type = version.get("type").toString();
            if (!"release".equals(type) && !"snapshot".equals(type)) continue;
            result.add(new GameVersion(id, type, URI.create(version.get("url").toString()), version.get("sha1").toString(), false));
        }
        return List.copyOf(result);
    }
}
