package com.valkyrie.client.module;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.valkyrie.client.module.impl.AppearanceModule;
import com.valkyrie.client.module.impl.ArmorModule;
import com.valkyrie.client.module.impl.DarkThemeModule;
import com.valkyrie.client.module.impl.EffectsModule;
import com.valkyrie.client.module.impl.EspModule;
import com.valkyrie.client.module.impl.HitMarkerModule;
import com.valkyrie.client.module.impl.DamageTintModule;
import com.valkyrie.client.module.impl.CrosshairModule;
import com.valkyrie.client.module.impl.WorldTimeModule;
import com.valkyrie.client.module.impl.TargetGlowModule;
import com.valkyrie.client.module.impl.SakuraBloomModule;
import com.valkyrie.client.module.impl.ThreatConstellationModule;
import com.valkyrie.client.module.impl.IntentLensModule;
import com.valkyrie.client.module.impl.MemoryEchoModule;
import com.valkyrie.client.module.impl.BlockHighlightModule;
import com.valkyrie.client.module.impl.PearlPredictionModule;
import com.valkyrie.client.module.impl.ProjectileTrailsModule;
import com.valkyrie.client.module.impl.ItemAnimationsModule;
import com.valkyrie.client.module.impl.CameraEffectsModule;
import com.valkyrie.client.module.impl.StorageEspModule;
import com.valkyrie.client.module.impl.WorldColorModule;
import com.valkyrie.client.module.impl.FocusModeModule;
import com.valkyrie.client.module.impl.VisualProfilesModule;
import com.valkyrie.client.module.impl.HudEditorModule;
import com.valkyrie.client.module.impl.InfoModule;
import com.valkyrie.client.module.impl.KeystrokesModule;
import com.valkyrie.client.module.impl.KillAuraModule;
import com.valkyrie.client.module.impl.AimAssistModule;
import com.valkyrie.client.module.impl.TriggerBotModule;
import com.valkyrie.client.module.impl.AutoWeaponModule;
import com.valkyrie.client.module.impl.AutoTotemModule;
import com.valkyrie.client.module.impl.CriticalsModule;
import com.valkyrie.client.module.impl.MainMenuModule;
import com.valkyrie.client.module.impl.TracersModule;
import com.valkyrie.client.module.impl.TargetHudModule;
import com.valkyrie.client.module.impl.WatermarkModule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;

/**
 * Единый список модулей клиента.
 * <p>
 * ClickGUI, конфиг и рендер обращаются только сюда: добавление функции сводится
 * к одной строке в {@link #register()}, без правок экрана настроек и сериализации.
 */
public final class ModuleRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Порядок вставки = порядок строк в колонке. */
    private static final Map<String, Module> MODULES = new LinkedHashMap<>();
    private static final Map<Class<? extends Module>, Module> MODULES_BY_TYPE = new java.util.HashMap<>();

    private ModuleRegistry() {
    }

    public static void register() {
        if (!MODULES.isEmpty()) {
            return;
        }

        add(new WatermarkModule());
        add(new KeystrokesModule());
        add(new InfoModule());
        add(new ArmorModule());
        add(new EffectsModule());
        add(new HudEditorModule());
        add(new TargetHudModule());

        add(new EspModule());
        add(new TracersModule());
        add(new HitMarkerModule());
        add(new DamageTintModule());
        add(new CrosshairModule());
        add(new WorldTimeModule());
        add(new TargetGlowModule());
        add(new SakuraBloomModule());
        add(new ThreatConstellationModule());
        add(new IntentLensModule());
        add(new MemoryEchoModule());
        add(new BlockHighlightModule());
        add(new PearlPredictionModule());
        add(new ProjectileTrailsModule());
        add(new ItemAnimationsModule());
        add(new CameraEffectsModule());
        add(new StorageEspModule());
        add(new WorldColorModule());

        add(new KillAuraModule());
        add(new AimAssistModule());
        add(new TriggerBotModule());
        add(new AutoWeaponModule());
        add(new AutoTotemModule());
        add(new CriticalsModule());

        add(new DarkThemeModule());
        add(new AppearanceModule());
        add(new MainMenuModule());
        add(new FocusModeModule());
        add(new VisualProfilesModule());

        LOGGER.info("[Valkyrie] Registered {} modules", MODULES.size());
    }

    private static void add(Module module) {
        Module previous = MODULES.put(module.key(), module);
        MODULES_BY_TYPE.put(module.getClass(), module);
        if (previous != null) {
            LOGGER.warn("[Valkyrie] Duplicate module key '{}' — previous instance replaced", module.key());
        }
    }

    public static List<Module> all() {
        return List.copyOf(MODULES.values());
    }

    public static List<Module> byCategory(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : MODULES.values()) {
            if (module.category() == category) {
                result.add(module);
            }
        }
        return result;
    }

    public static Module get(String key) {
        return MODULES.get(key.toLowerCase(Locale.ROOT).replace(" ", ""));
    }

    /**
     * Типизированный доступ для кода рендера: модуль знает свои настройки,
     * а вызывающий не должен приводить типы вручную на каждой отрисовке.
     * <p>
     * Реестр достраивается по требованию: {@link com.valkyrie.client.gui.theme.Theme}
     * читает акцент из модуля и может быть вызван раньше инициализации клиента,
     * а падать из-за порядка загрузки интерфейс не должен.
     */
    public static <T extends Module> T get(Class<T> type) {
        T found = find(type);
        if (found != null) {
            return found;
        }
        register();
        found = find(type);
        if (found == null) {
            throw new IllegalStateException("Module not registered: " + type.getSimpleName());
        }
        return found;
    }

    private static <T extends Module> T find(Class<T> type) {
        return type.cast(MODULES_BY_TYPE.get(type));
    }

    public static boolean isEnabled(Class<? extends Module> type) {
        Module module = find(type);
        return module != null && module.isEnabled();
    }

    // ─── Конфиг ──────────────────────────────────────────────────────────────

    public static JsonObject save() {
        JsonObject json = new JsonObject();
        for (Module module : MODULES.values()) {
            json.add(module.key(), module.save());
        }
        return json;
    }

    public static void load(JsonObject json) {
        if (json == null) {
            return;
        }
        for (Module module : MODULES.values()) {
            if (json.has(module.key()) && json.get(module.key()).isJsonObject()) {
                module.load(json.getAsJsonObject(module.key()));
            }
        }
    }
}
