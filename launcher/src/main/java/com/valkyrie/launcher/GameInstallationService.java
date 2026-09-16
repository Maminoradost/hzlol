package com.valkyrie.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

final class GameInstallationService {
    static final String MINECRAFT_VERSION = "1.21.10";
    static final String FORGE_VERSION = "60.1.0";
    static final String FORGE_PROFILE = MINECRAFT_VERSION + "-forge-" + FORGE_VERSION;

    static final URI VERSION_URL = URI.create("https://piston-meta.mojang.com/v1/packages/38e4f95edc94d09a3ded1e17a78b76f9694ca3b6/1.21.10.json");
    static final String VERSION_SHA1 = "38e4f95edc94d09a3ded1e17a78b76f9694ca3b6";
    private static final URI FORGE_URL = URI.create("https://maven.minecraftforge.net/net/minecraftforge/forge/1.21.10-60.1.0/forge-1.21.10-60.1.0-installer.jar");
    private static final String FORGE_SHA1 = "27ef641cf186da66dcb75e62dc46b2fda64467fd";
    /** Forge installer для 1.21.10 работает на Java 21. */
    private static final int FORGE_INSTALLER_JAVA = 21;

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build();

    boolean installed(LauncherConfig config) {
        return installed(config, GameVersion.VALKYRIE);
    }

    boolean installed(LauncherConfig config, GameVersion selected) {
        Path content = config.contentDirectory;
        Path state = content.resolve("install-state").resolve(selected.instanceId() + ".complete");
        boolean base = Files.isRegularFile(content.resolve("versions").resolve(selected.id()).resolve(selected.id() + ".json"))
            && Files.isRegularFile(content.resolve("versions").resolve(selected.id()).resolve(selected.id() + ".jar"));
        if (!selected.valkyrie()) return base && Files.isRegularFile(state);
        return base
            && Files.isRegularFile(content.resolve("versions").resolve(FORGE_PROFILE).resolve(FORGE_PROFILE + ".json"))
            && Files.isRegularFile(content.resolve("libraries/net/minecraftforge/forge/1.21.10-60.1.0/forge-1.21.10-60.1.0-client.jar"))
            && (Files.isRegularFile(state) || Files.isRegularFile(content.resolve(".valkyrie-install-complete")));
    }

    void ensureInstalled(LauncherConfig config, Consumer<String> status) throws Exception {
        ensureInstalled(config, GameVersion.VALKYRIE, status, ignored -> {});
    }

    void ensureInstalled(LauncherConfig config, GameVersion selected, Consumer<String> status) throws Exception {
        ensureInstalled(config, selected, status, ignored -> {});
    }

    /**
     * @param status   человекочитаемая фаза установки
     * @param progress доля готовности 0..1 (или -1 для неопределённой фазы);
     *                 вызывается из рабочих потоков — UI обязан переходить
     *                 на FX-тред сам
     */
    void ensureInstalled(LauncherConfig config, GameVersion selected, Consumer<String> status, DoubleConsumer progress) throws Exception {
        if (installed(config, selected)) {
            LauncherLog.info("Игровая установка готова: " + config.contentDirectory);
            return;
        }
        Path content = config.contentDirectory;
        LauncherLog.info("Начало установки " + selected.displayName() + " в " + content);
        Files.createDirectories(content);
        Files.createDirectories(config.instanceDirectory);

        progress.accept(-1);
        status.accept("Подготавливаем Minecraft " + selected.id());
        Path versionJson = content.resolve("versions").resolve(selected.id()).resolve(selected.id() + ".json");
        download(selected.metadataUri(), versionJson, selected.metadataSha1());
        Map<String, Object> version = Json.object(Json.parse(Files.readString(versionJson)));

        // Веса фаз в общем прогрессе: библиотеки ~25%, ассеты ~65%
        // (их тысячи), Forge ~10%. Точные байты не считаем — количества
        // файлов достаточно, чтобы полоска двигалась честно.
        List<Download> files = downloads(version, content, selected.id());
        downloadAll(files, (done, total) -> {
            status.accept("Скачиваем Minecraft и библиотеки · " + done + "/" + total);
            progress.accept(0.25 * done / Math.max(1, total));
        });

        installAssets(version, content, (done, total) -> {
            status.accept("Скачиваем игровые ресурсы · " + done + "/" + total);
            progress.accept(0.25 + 0.65 * done / Math.max(1, total));
        });

        if (selected.valkyrie()) {
            progress.accept(-1);
            status.accept("Устанавливаем Forge " + FORGE_VERSION);
            installForge(content);
        }
        progress.accept(1.0);

        Path state = content.resolve("install-state").resolve(selected.instanceId() + ".complete");
        Files.createDirectories(state.getParent());
        Files.writeString(state, selected.id() + "\n" + selected.profileId() + "\n");
        LauncherLog.info("Установка завершена: " + selected.displayName());
    }

    void repair(LauncherConfig config, Consumer<String> status) throws Exception {
        repair(config, GameVersion.VALKYRIE, status, ignored -> {});
    }

    void repair(LauncherConfig config, GameVersion selected, Consumer<String> status, DoubleConsumer progress) throws Exception {
        Files.deleteIfExists(config.contentDirectory.resolve("install-state").resolve(selected.instanceId() + ".complete"));
        if (selected.valkyrie()) Files.deleteIfExists(config.contentDirectory.resolve("versions").resolve(FORGE_PROFILE).resolve(FORGE_PROFILE + ".json"));
        ensureInstalled(config, selected, status, progress);
    }

    private List<Download> downloads(Map<String, Object> version, Path content, String versionId) throws IOException {
        List<Download> result = new ArrayList<>();
        Map<String, Object> clientDownload = map(map(version.get("downloads")).get("client"));
        result.add(new Download(secureUri(clientDownload.get("url").toString()),
            content.resolve("versions").resolve(versionId).resolve(versionId + ".jar"), clientDownload.get("sha1").toString()));
        Path libraries = content.resolve("libraries");
        for (Object raw : Json.array(version.get("libraries"))) {
            Map<String, Object> library = Json.object(raw);
            if (!MinecraftLauncher.allowed(library)) continue;
            Map<String, Object> artifact = map(map(library.get("downloads")).get("artifact"));
            if (artifact.containsKey("url")) result.add(new Download(secureUri(artifact.get("url").toString()),
                resolveInside(libraries, artifact.get("path").toString()), artifact.get("sha1").toString()));
            Map<String, Object> classifiers = map(map(library.get("downloads")).get("classifiers"));
            for (Map.Entry<String, Object> classifier : classifiers.entrySet()) {
                if (!classifier.getKey().startsWith("natives-windows") || classifier.getKey().contains("arm64") || classifier.getKey().endsWith("-x86")) continue;
                Map<String, Object> nativeDownload = map(classifier.getValue());
                if (nativeDownload.containsKey("url")) result.add(new Download(secureUri(nativeDownload.get("url").toString()),
                    resolveInside(libraries, nativeDownload.get("path").toString()), nativeDownload.get("sha1").toString()));
            }
        }
        Map<String, Object> loggingFile = map(map(version.get("logging")).get("client"));
        loggingFile = map(loggingFile.get("file"));
        if (!loggingFile.isEmpty()) {
            result.add(new Download(secureUri(loggingFile.get("url").toString()),
                resolveInside(content.resolve("assets").resolve("log_configs"), loggingFile.get("id").toString()), loggingFile.get("sha1").toString()));
        }
        return result;
    }

    /**
     * Резолвит относительный путь из version-JSON строго внутри базовой папки.
     * JSON приходит из сети/кэша и не является доверенным: значение вида
     * "../../.." иначе позволило бы писать (и потом исполнять через classpath)
     * файлы за пределами игровой директории.
     */
    private static Path resolveInside(Path base, String relative) throws IOException {
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path resolved = normalizedBase.resolve(relative).normalize();
        if (!resolved.startsWith(normalizedBase)) {
            throw new IOException("Небезопасный путь в манифесте версии: " + relative);
        }
        return resolved;
    }

    /** Разрешает только HTTPS-загрузки: version-JSON может прийти из кэша и не является доверенным. */
    private static URI secureUri(String url) throws IOException {
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IOException("Загрузка разрешена только по HTTPS: " + url);
        }
        return uri;
    }

    private void installAssets(Map<String, Object> version, Path content, BiConsumer<Integer, Integer> tick) throws Exception {
        Map<String, Object> index = map(version.get("assetIndex"));
        String id = index.get("id").toString();
        Path indexFile = resolveInside(content.resolve("assets").resolve("indexes"), id + ".json");
        download(secureUri(index.get("url").toString()), indexFile, index.get("sha1").toString());
        Map<String, Object> assets = Json.object(Json.parse(Files.readString(indexFile)));
        List<Download> objects = new ArrayList<>();
        for (Object raw : Json.object(assets.get("objects")).values()) {
            Map<String, Object> object = map(raw);
            String hash = object.get("hash").toString();
            // Хэш идёт и в URL, и в путь на диске — валидируем формат,
            // прежде чем использовать как компонент пути.
            if (!hash.matches("[0-9a-f]{40}")) throw new IOException("Некорректный хэш ресурса в индексе: " + hash);
            objects.add(new Download(URI.create("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash),
                content.resolve("assets").resolve("objects").resolve(hash.substring(0, 2)).resolve(hash), hash));
        }
        downloadAll(objects, tick);
    }

    private void installForge(Path content) throws Exception {
        Path installer = content.resolve("installers").resolve("forge-" + MINECRAFT_VERSION + "-" + FORGE_VERSION + "-installer.jar");
        download(FORGE_URL, installer, FORGE_SHA1);
        Path profile = content.resolve("versions").resolve(FORGE_PROFILE).resolve(FORGE_PROFILE + ".json");
        if (Files.isRegularFile(profile)) return;
        Path profiles = content.resolve("launcher_profiles.json");
        if (!Files.isRegularFile(profiles)) Files.writeString(profiles, "{}");
        Process process = new ProcessBuilder(javaExecutable(content).toString(), "-jar", installer.toString(), "--installClient", content.toString())
            .directory(content.toFile()).redirectErrorStream(true).redirectOutput(content.resolve("forge-installer.log").toFile()).start();
        // Таймаут: зависший инсталлятор (сеть, антивирус) не должен вечно
        // держать установку — пользователь видел бы бесконечный прогресс.
        if (!process.waitFor(15, java.util.concurrent.TimeUnit.MINUTES)) {
            process.destroyForcibly();
            throw new IOException("Forge installer не завершился за 15 минут и был остановлен. Подробности: " + content.resolve("forge-installer.log"));
        }
        if (process.exitValue() != 0 || !Files.isRegularFile(profile)) {
            throw new IOException("Forge installer завершился с ошибкой. Подробности: " + content.resolve("forge-installer.log"));
        }
        LauncherLog.info("Forge installer завершён успешно: " + profile);
    }

    private void downloadAll(List<Download> downloads, BiConsumer<Integer, Integer> tick) throws Exception {
        int total = downloads.size();
        AtomicInteger done = new AtomicInteger();
        // Дросселируем колбэк: тысячи Platform.runLater на каждую мелочь
        // забили бы очередь FX-треда. Обновляем каждые ~1% или каждый файл,
        // если их мало.
        int step = Math.max(1, total / 100);
        tick.accept(0, total);
        try (var executor = Executors.newFixedThreadPool(Math.min(12, Math.max(2, total)), Thread.ofVirtual().factory())) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (Download download : downloads) {
                futures.add(executor.submit(() -> {
                    download(download.uri, download.target, download.sha1);
                    int current = done.incrementAndGet();
                    if (current % step == 0 || current == total) tick.accept(current, total);
                    return null;
                }));
            }
            for (var future : futures) future.get();
        }
    }

    private void download(URI uri, Path target, String sha1) throws IOException, InterruptedException {
        if (Files.isRegularFile(target) && digest(target).equalsIgnoreCase(sha1)) return;
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".download-", ".tmp");
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).GET().build();
            HttpResponse<Path> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofFile(temporary));
            } catch (javax.net.ssl.SSLHandshakeException error) {
                throw new IOException("Не удалось установить защищённое соединение с " + uri.getHost() + ". Проверьте дату Windows, антивирус и HTTPS-фильтрацию", error);
            }
            if (response.statusCode() != 200) throw new IOException("Download HTTP " + response.statusCode() + ": " + uri.getHost());
            if (!digest(temporary).equalsIgnoreCase(sha1)) throw new IOException("SHA-1 mismatch: " + target.getFileName());
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String digest(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception error) {
            if (error instanceof IOException io) throw io;
            throw new IOException("SHA-1 unavailable", error);
        }
    }

    /**
     * Java для запуска Forge installer. В нативной сборке java.home указывает на
     * урезанный рантайм без java.exe, поэтому проверяем его наличие и при
     * необходимости скачиваем полноценный рантайм рядом с игрой.
     */
    static Path javaExecutable(Path content) throws IOException {
        boolean windows = System.getProperty("os.name", "").startsWith("Windows");
        Path bundled = Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java");
        if (Files.isRegularFile(bundled)) return bundled;
        LauncherLog.info("java.exe отсутствует в " + bundled.getParent() + ", скачиваем рантайм для Forge installer");
        try {
            return new JavaRuntimeService(content).provide(FORGE_INSTALLER_JAVA, true,
                (done, total) -> LauncherLog.info("Загрузка Java " + FORGE_INSTALLER_JAVA + ": " + done + "/" + total));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Загрузка Java для Forge installer прервана", error);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }

    private record Download(URI uri, Path target, String sha1) {}
}
