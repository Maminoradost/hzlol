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
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Скачивает и распаковывает JRE нужной мажорной версии, чтобы Minecraft
 * запускался на машине без установленной Java.
 *
 * Основной источник — официальный манифест Mojang (piston-meta): те же CDN и
 * SHA-1, что уже используются для игры. Если для требуемой версии компонента
 * нет, используется Adoptium.
 */
final class JavaRuntimeService {
    private static final String MOJANG_ALL =
        "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final Path root;

    JavaRuntimeService(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** Каталог конкретного рантайма: <root>/runtimes/<major>. */
    Path runtimeDirectory(int major) {
        return root.resolve("runtimes").resolve(String.valueOf(major));
    }

    /**
     * Возвращает java-исполняемый файл нужной версии, скачивая рантайм при
     * необходимости. Повторный вызов не качает ничего: результат кэшируется на
     * диске, а готовность отмечается файлом .ready.
     *
     * @param console true — нужен java.exe (консольный, для Forge installer),
     *                false — javaw.exe (без окна консоли, для запуска игры)
     */
    Path provide(int major, boolean console, BiConsumer<Integer, Integer> tick) throws IOException, InterruptedException {
        Path directory = runtimeDirectory(major);
        Path ready = directory.resolve(".ready");
        Path executable = executableIn(directory, console);
        if (Files.isRegularFile(ready) && Files.isRegularFile(executable)) return executable;

        LauncherLog.info("Java " + major + " не найдена локально, скачиваем рантайм в " + directory);
        Files.createDirectories(directory);
        try {
            installFromMojang(major, directory, tick);
        } catch (IOException mojangError) {
            LauncherLog.info("Mojang java-runtime недоступен для Java " + major + " (" + mojangError.getMessage() + "), пробуем Adoptium");
            installFromAdoptium(major, directory, tick);
        }

        executable = executableIn(directory, console);
        if (!Files.isRegularFile(executable)) {
            throw new IOException("Рантайм Java " + major + " скачан, но " + executable.getFileName() + " не найден в " + directory);
        }
        makeExecutable(directory);
        Files.writeString(ready, "java " + major + System.lineSeparator());
        LauncherLog.info("Java " + major + " готова: " + executable);
        return executable;
    }

    /** Ищет bin/<name> на любой глубине — у Mojang путь плоский, у Adoptium вложен в jdk-XX/. */
    private static Path executableIn(Path directory, boolean console) {
        String name = windows() ? (console ? "java.exe" : "javaw.exe") : "java";
        Path direct = directory.resolve("bin").resolve(name);
        if (Files.isRegularFile(direct)) return direct;
        if (!Files.isDirectory(directory)) return direct;
        try (var stream = Files.walk(directory, 4)) {
            return stream
                .filter(path -> path.getFileName() != null && path.getFileName().toString().equals(name))
                .filter(path -> path.getParent() != null && path.getParent().getFileName() != null
                    && path.getParent().getFileName().toString().equals("bin"))
                .findFirst()
                .orElse(direct);
        } catch (IOException ignored) {
            return direct;
        }
    }

    // ---------------------------------------------------------------- Mojang

    private void installFromMojang(int major, Path directory, BiConsumer<Integer, Integer> tick) throws IOException, InterruptedException {
        Map<String, Object> all = Json.object(Json.parse(text(URI.create(MOJANG_ALL))));
        Map<String, Object> platform = Json.object(all.get(mojangPlatform()));
        if (platform == null || platform.isEmpty()) throw new IOException("Нет платформы " + mojangPlatform() + " в манифесте Mojang");

        String manifestUrl = null;
        for (Map.Entry<String, Object> entry : platform.entrySet()) {
            List<Object> variants = Json.array(entry.getValue());
            if (variants == null || variants.isEmpty()) continue;
            Map<String, Object> variant = Json.object(variants.getFirst());
            Map<String, Object> versionInfo = Json.object(variant.get("version"));
            if (versionInfo == null) continue;
            Object name = versionInfo.get("name");
            if (name == null || majorOf(name.toString()) != major) continue;
            Map<String, Object> manifest = Json.object(variant.get("manifest"));
            if (manifest == null || manifest.get("url") == null) continue;
            manifestUrl = manifest.get("url").toString();
            LauncherLog.info("Mojang java-runtime: компонент " + entry.getKey() + " версии " + name);
            break;
        }
        if (manifestUrl == null) throw new IOException("Компонент для Java " + major + " отсутствует");

        Map<String, Object> files = Json.object(Json.object(Json.parse(text(URI.create(manifestUrl)))).get("files"));
        if (files == null || files.isEmpty()) throw new IOException("Пустой манифест рантайма Java " + major);

        List<Download> downloads = new ArrayList<>();
        for (Map.Entry<String, Object> entry : files.entrySet()) {
            Map<String, Object> item = Json.object(entry.getValue());
            if (item == null) continue;
            Path target = resolveSafely(directory, entry.getKey());
            String type = String.valueOf(item.get("type"));
            if ("directory".equals(type)) {
                Files.createDirectories(target);
                continue;
            }
            if (!"file".equals(type)) continue;
            Map<String, Object> raw = Json.object(Json.object(item.get("downloads")).get("raw"));
            if (raw == null || raw.get("url") == null || raw.get("sha1") == null) continue;
            downloads.add(new Download(URI.create(raw.get("url").toString()), target, raw.get("sha1").toString()));
        }
        if (downloads.isEmpty()) throw new IOException("В манифесте Java " + major + " нет файлов");
        downloadAll(downloads, tick);
    }

    private static String mojangPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        boolean arm = arch.contains("aarch64") || arch.contains("arm");
        boolean bit64 = arch.contains("64");
        if (os.startsWith("windows")) return arm ? "windows-arm64" : bit64 ? "windows-x64" : "windows-x86";
        if (os.contains("mac") || os.contains("darwin")) return arm ? "mac-os-arm64" : "mac-os";
        return arm ? "linux" : bit64 ? "linux" : "linux-i386";
    }

    // -------------------------------------------------------------- Adoptium

    private void installFromAdoptium(int major, Path directory, BiConsumer<Integer, Integer> tick) throws IOException, InterruptedException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String adoptOs = os.startsWith("windows") ? "windows" : os.contains("mac") || os.contains("darwin") ? "mac" : "linux";
        String adoptArch = arch.contains("aarch64") || arch.contains("arm") ? "aarch64" : arch.contains("64") ? "x64" : "x86";
        URI uri = URI.create("https://api.adoptium.net/v3/binary/latest/" + major
            + "/ga/" + adoptOs + "/" + adoptArch + "/jre/hotspot/normal/eclipse");

        tick.accept(0, 1);
        Files.createDirectories(directory);
        Path archive = Files.createTempFile(directory, ".runtime-", adoptOs.equals("windows") ? ".zip" : ".tar.gz");
        try {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(20)).GET().build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(archive));
            if (response.statusCode() != 200) {
                throw new IOException("Adoptium HTTP " + response.statusCode() + " для Java " + major);
            }
            if (adoptOs.equals("windows")) unzip(archive, directory);
            else untarGz(archive, directory);
            tick.accept(1, 1);
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    // ------------------------------------------------------------- служебное

    private void downloadAll(List<Download> downloads, BiConsumer<Integer, Integer> tick) throws IOException, InterruptedException {
        int total = downloads.size();
        AtomicInteger done = new AtomicInteger();
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
            for (var future : futures) {
                try {
                    future.get();
                } catch (java.util.concurrent.ExecutionException error) {
                    Throwable cause = error.getCause();
                    if (cause instanceof IOException io) throw io;
                    throw new IOException("Не удалось скачать рантайм Java", cause);
                }
            }
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
                throw new IOException("Не удалось установить защищённое соединение с " + uri.getHost()
                    + ". Проверьте дату Windows, антивирус и HTTPS-фильтрацию", error);
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

    private String text(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(2)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + ": " + uri.getHost());
        return response.body();
    }

    /** Защита от Zip Slip: запись обязана остаться внутри каталога рантайма. */
    private static Path resolveSafely(Path directory, String relative) throws IOException {
        Path target = directory.resolve(relative).normalize();
        if (!target.startsWith(directory)) throw new IOException("Недопустимый путь в архиве: " + relative);
        return target;
    }

    private static void unzip(Path archive, Path directory) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path target = resolveSafely(directory, stripRoot(entry.getName()));
                if (entry.isDirectory() || target.equals(directory)) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void untarGz(Path archive, Path directory) throws IOException {
        // Внешний tar есть в macOS и Linux по умолчанию; проще и надёжнее, чем
        // тянуть отдельную библиотеку ради не-Windows ветки.
        Files.createDirectories(directory);
        try {
            Process process = new ProcessBuilder("tar", "-xzf", archive.toString(), "-C", directory.toString(), "--strip-components=1")
                .redirectErrorStream(true).start();
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IOException("Распаковка рантайма Java не завершилась за 10 минут");
            }
            if (process.exitValue() != 0) throw new IOException("tar завершился с кодом " + process.exitValue());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Распаковка рантайма прервана", error);
        }
    }

    /** Adoptium кладёт всё в jdk-XX/; убираем верхний каталог, чтобы bin/ был на месте. */
    private static String stripRoot(String name) {
        int slash = name.indexOf('/');
        return slash >= 0 && slash + 1 < name.length() ? name.substring(slash + 1) : name;
    }

    private static void makeExecutable(Path directory) {
        if (windows()) return;
        Path bin = directory.resolve("bin");
        if (!Files.isDirectory(bin)) return;
        try (var stream = Files.walk(directory, 4)) {
            stream.filter(path -> path.getParent() != null && path.getParent().getFileName() != null
                    && path.getParent().getFileName().toString().equals("bin"))
                .forEach(path -> path.toFile().setExecutable(true, false));
        } catch (IOException ignored) {
            // Права — не повод падать: запуск всё равно будет проверен ниже.
        }
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /** "21.0.7" -> 21, "1.8.0_402" -> 8, "8u51-cacert462b08" -> 8. */
    static int majorOf(String version) {
        String value = version.trim();
        if (value.startsWith("1.")) value = value.substring(2);
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) end++;
        if (end == 0) return -1;
        try {
            return Integer.parseInt(value.substring(0, end));
        } catch (NumberFormatException error) {
            return -1;
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

    private record Download(URI uri, Path target, String sha1) {}
}
