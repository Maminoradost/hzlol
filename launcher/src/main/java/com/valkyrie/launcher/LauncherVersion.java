package com.valkyrie.launcher;

/**
 * Версия лаунчера для показа в интерфейсе и в аргументах запуска игры.
 *
 * <p>Единственный источник — атрибут {@code Implementation-Version} манифеста
 * собранного jar, который Gradle заполняет из {@code launcherVersion}. Раньше
 * строка "1.0.6" была продублирована в титульной строке, в разделе About и в
 * {@link MinecraftLauncher}; такие копии уже расходились с релизными
 * артефактами, поэтому новых литералов с версией быть не должно.
 *
 * <p>При запуске из каталога классов (IDE, smokeTest) манифеста нет — тогда
 * возвращается {@code "dev"}: это честнее подставленного номера релиза.
 */
final class LauncherVersion {
    private LauncherVersion() {}

    static final String VALUE = resolve();

    private static String resolve() {
        String version = LauncherVersion.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "dev" : version;
    }
}
