package com.valkyrie.launcher;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Проверяет автоматическую загрузку Java: рантайм скачивается, распаковывается,
 * запускается и рапортует запрошенную мажорную версию. Это главный сценарий для
 * машины без установленной Java.
 */
public final class JavaRuntimeTest {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length > 0 ? args[0] : System.getProperty("java.io.tmpdir") + "/valkyrie-java-test")
            .toAbsolutePath().normalize();
        int major = args.length > 1 ? Integer.parseInt(args[1]) : 25;

        JavaRuntimeService service = new JavaRuntimeService(root);
        Path directory = service.runtimeDirectory(major);
        if (!directory.equals(root.resolve("runtimes").resolve(String.valueOf(major)))) {
            throw new IllegalStateException("Неожиданный каталог рантайма: " + directory);
        }

        long start = System.nanoTime();
        Path javaw = service.provide(major, false, (done, total) -> {
            if (total > 0 && done % Math.max(1, total / 4) == 0) System.out.println("  " + done + "/" + total);
        });
        long seconds = (System.nanoTime() - start) / 1_000_000_000L;

        if (!Files.isRegularFile(javaw)) throw new IllegalStateException("javaw не найден: " + javaw);
        String expected = System.getProperty("os.name", "").startsWith("Windows") ? "javaw.exe" : "java";
        if (!javaw.getFileName().toString().equals(expected)) {
            throw new IllegalStateException("Ожидался " + expected + ", получен " + javaw.getFileName());
        }

        // Консольный вариант нужен Forge installer — он должен лежать рядом.
        Path java = service.provide(major, true, (done, total) -> {});
        if (!Files.isRegularFile(java)) throw new IllegalStateException("java не найден: " + java);

        // Рантайм должен реально исполняться и быть той версии, которую просили.
        Process process = new ProcessBuilder(java.toString(), "-version").redirectErrorStream(true).start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("java -version не ответил за 60 секунд");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new IllegalStateException("java -version завершился с ошибкой: " + output);
        if (!output.contains("\"" + major) && !output.contains("\"1." + major)) {
            throw new IllegalStateException("Ожидалась Java " + major + ", получено: " + output.strip());
        }

        // Повторный вызов обязан взять готовое с диска, а не качать заново.
        long cachedStart = System.nanoTime();
        Path again = service.provide(major, false, (done, total) -> {
            throw new IllegalStateException("Повторный provide не должен скачивать файлы");
        });
        long cachedMillis = (System.nanoTime() - cachedStart) / 1_000_000L;
        if (!again.equals(javaw)) throw new IllegalStateException("Кэш вернул другой путь: " + again);
        if (cachedMillis > 5000) throw new IllegalStateException("Повторный provide занял " + cachedMillis + " мс — кэш не работает");

        System.out.println("Java runtime test passed: Java " + major + " downloaded in " + seconds + "s");
        System.out.println("  " + output.strip().lines().findFirst().orElse(""));
        System.out.println("  javaw: " + javaw);
        System.out.println("  cached lookup: " + cachedMillis + " ms");
    }
}
