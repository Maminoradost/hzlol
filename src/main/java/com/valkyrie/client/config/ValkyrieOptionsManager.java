package com.valkyrie.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.logging.LogUtils;
import com.valkyrie.ValkyrieClient;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.Setting;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

/**
 * Чтение и запись {@code config/valkyrieclient.json}.
 * <p>
 * Файл состоит из двух частей: плоские поля {@link ValkyrieOptions} (раскладка)
 * и объект {@code modules} с состоянием реестра.
 */
public final class ValkyrieOptionsManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Повышается при изменении семантики конфига. */
    private static final int CONFIG_VERSION = 10;

    private static final String MODULES_KEY = "modules";

    public static final ValkyrieOptions OPTIONS = new ValkyrieOptions();

    private ValkyrieOptionsManager() {
    }

    public static void load() {
        Path path = getConfigPath();
        if (!Files.exists(path)) {
            OPTIONS.configVersion = CONFIG_VERSION;
            save();
            LOGGER.info("[Valkyrie] Created default config at {}", path);
            return;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject raw = GSON.fromJson(json, JsonObject.class);
            if (raw == null) {
                LOGGER.warn("[Valkyrie] Config {} is empty, keeping defaults", path);
                return;
            }

            ValkyrieOptions loaded = GSON.fromJson(raw, ValkyrieOptions.class);
            if (loaded != null) {
                copyInto(loaded, OPTIONS);
            }

            if (raw.has(MODULES_KEY) && raw.get(MODULES_KEY).isJsonObject()) {
                ModuleRegistry.load(raw.getAsJsonObject(MODULES_KEY));
            }

            migrate(OPTIONS, raw);
            LOGGER.info("[Valkyrie] Config loaded from {} (version={})", path, OPTIONS.configVersion);
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warn("[Valkyrie] Failed to read config {}, using defaults", path, e);
        }
    }

    /** Перечитывает конфиг с диска, чтобы правки файла применялись без перезапуска. */
    public static void reloadFromDisk() {
        load();
    }

    public static void save() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            OPTIONS.configVersion = CONFIG_VERSION;
            JsonObject json = GSON.toJsonTree(OPTIONS).getAsJsonObject();
            json.add(MODULES_KEY, ModuleRegistry.save());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(json), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("[Valkyrie] Failed to write config {}", path, e);
        }
    }

    private static Path getConfigPath() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameDirectory.toPath()
            .resolve("config")
            .resolve(ValkyrieClient.MODID + ".json");
    }

    /**
     * Копирует все публичные нестатические поля через рефлексию, поэтому новое
     * поле в {@link ValkyrieOptions} не нужно дублировать здесь вручную.
     */
    private static void copyInto(ValkyrieOptions from, ValkyrieOptions to) {
        for (Field field : ValkyrieOptions.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            try {
                field.set(to, field.get(from));
            } catch (IllegalAccessException e) {
                LOGGER.warn("[Valkyrie] Could not copy config field {}", field.getName(), e);
            }
        }
    }

    /**
     * Переносит значения из конфигов прошлых версий.
     *
     * @param raw исходный JSON — нужен, чтобы прочитать поля, которых уже нет в классе
     */
    private static void migrate(ValkyrieOptions options, JsonObject raw) {
        if (options.configVersion >= CONFIG_VERSION) {
            return;
        }

        int from = options.configVersion;
        if (from < 8) {
            LOGGER.info("[Valkyrie] Migrating config v{} -> v{}: renaming ClickGUI panel positions", from, CONFIG_VERSION);
            options.panelHudX = legacyInt(raw, options.panelHudX, "catHudX");
            options.panelHudY = legacyInt(raw, options.panelHudY, "catHudY");
        }
        if (from < 10) {
            migrateToModules(options, raw, from);
        }

        options.configVersion = CONFIG_VERSION;
        save();
    }

    /**
     * v10 перенёс настройки функций из плоских полей в реестр модулей.
     * <p>
     * Без переноса пользователь потерял бы всю конфигурацию: имена полей
     * изменились, и Gson просто оставил бы значения по умолчанию.
     */
    private static void migrateToModules(ValkyrieOptions options, JsonObject raw, int from) {
        LOGGER.info("[Valkyrie] Migrating config v{} -> v{}: moving settings into module registry", from, CONFIG_VERSION);

        moveBool(raw, "hudEnabled", "watermark", null);
        moveBool(raw, "keystrokesEnabled", "keystrokes", null);
        moveBool(raw, "armorEnabled", "armor", null);
        moveBool(raw, "effectsEnabled", "effects", null);
        moveBool(raw, "customMainMenuEnabled", "mainmenu", null);
        moveBool(raw, "darkTheme", "darkmode", null);

        // Info раньше был двумя независимыми флагами без общего выключателя.
        moveBool(raw, "coordsEnabled", "info", "coords");
        moveBool(raw, "sessionEnabled", "info", "session");
        Module info = ModuleRegistry.get("info");
        if (info != null && (raw.has("coordsEnabled") || raw.has("sessionEnabled"))) {
            info.setEnabledSilently(readBool(raw, "coordsEnabled", true) || readBool(raw, "sessionEnabled", true));
        }

        moveBool(raw, "tracersEnabled", "tracers", null);
        moveColor(raw, "tracerColor", "tracers", "color");
        moveFloat(raw, "tracerOpacity", "tracers", "opacity");
        moveFloat(raw, "tracerRange", "tracers", "range");

        moveBool(raw, "espEnabled", "esp", null);
        moveBool(raw, "espBoxes", "esp", "boxes");
        moveBool(raw, "espNametags", "esp", "nametags");
        moveBool(raw, "espHealthBars", "esp", "health");
        moveBool(raw, "espOffscreen", "esp", "offscreen");
        moveColor(raw, "espColor", "esp", "color");
        moveFloat(raw, "espOpacity", "esp", "opacity");
        moveFloat(raw, "espRange", "esp", "range");

        // Фильтр целей был общим — раздаём его обоим модулям.
        for (String module : new String[] {"tracers", "esp"}) {
            moveBool(raw, "tracerPlayers", module, "players");
            moveBool(raw, "tracerHostiles", module, "hostiles");
            moveBool(raw, "tracerPassives", module, "passives");
        }

        moveColor(raw, "accentColor", "appearance", "accent");
        moveFloat(raw, "hudOpacity", "appearance", "hudopacity");

        // Колонки Tracers/Targets/Theme схлопнулись в Visuals и Client:
        // старые координаты указывали бы в неверные места, поэтому сбрасываем.
        options.panelVisualsX = ValkyrieOptions.UNSET;
        options.panelVisualsY = ValkyrieOptions.UNSET;
        options.panelClientX = ValkyrieOptions.UNSET;
        options.panelClientY = ValkyrieOptions.UNSET;
    }

    /**
     * Переносит булево поле.
     *
     * @param settingKey ключ настройки внутри модуля; {@code null} — состояние самого модуля
     */
    private static void moveBool(JsonObject raw, String legacyKey, String moduleKey, String settingKey) {
        if (!raw.has(legacyKey)) {
            return;
        }
        Module module = ModuleRegistry.get(moduleKey);
        if (module == null) {
            return;
        }
        try {
            boolean value = raw.get(legacyKey).getAsBoolean();
            if (settingKey == null) {
                module.setEnabledSilently(value);
            } else if (findSetting(module, settingKey) instanceof Setting.Bool bool) {
                bool.set(value);
            }
        } catch (RuntimeException ignored) {
            // Битое значение — остаётся значение по умолчанию.
        }
    }

    private static void moveFloat(JsonObject raw, String legacyKey, String moduleKey, String settingKey) {
        if (!raw.has(legacyKey)) {
            return;
        }
        Module module = ModuleRegistry.get(moduleKey);
        if (module == null) {
            return;
        }
        try {
            if (findSetting(module, settingKey) instanceof Setting.Slider slider) {
                slider.set(raw.get(legacyKey).getAsFloat());
            }
        } catch (RuntimeException ignored) {
            // Битое значение — остаётся значение по умолчанию.
        }
    }

    private static void moveColor(JsonObject raw, String legacyKey, String moduleKey, String settingKey) {
        if (!raw.has(legacyKey)) {
            return;
        }
        Module module = ModuleRegistry.get(moduleKey);
        if (module == null) {
            return;
        }
        try {
            if (findSetting(module, settingKey) instanceof Setting.Color color) {
                color.set(raw.get(legacyKey).getAsInt());
            }
        } catch (RuntimeException ignored) {
            // Битое значение — остаётся значение по умолчанию.
        }
    }

    private static Setting<?> findSetting(Module module, String key) {
        for (Setting<?> setting : module.settings()) {
            if (setting.key().equals(key)) {
                return setting;
            }
        }
        return null;
    }

    private static boolean readBool(JsonObject raw, String key, boolean fallback) {
        JsonElement element = raw.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Читает значение из старого JSON, если текущее поле ещё не задано. */
    private static int legacyInt(JsonObject raw, int current, String legacyKey) {
        if (current != ValkyrieOptions.UNSET || raw == null || !raw.has(legacyKey)) {
            return current;
        }
        try {
            return raw.get(legacyKey).getAsInt();
        } catch (RuntimeException e) {
            return current;
        }
    }
}
