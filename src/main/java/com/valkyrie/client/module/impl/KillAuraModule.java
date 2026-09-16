package com.valkyrie.client.module.impl;

import com.google.gson.JsonObject;
import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import com.valkyrie.client.module.TargetFilter;
import java.util.List;

/**
 * Автоматическая атака ближайших существ.
 * <p>
 * Настройки по умолчанию рассчитаны на строгие серверные проверки: дальность в
 * пределах ванильных 3.0, человеческая скорость доворота и разброс задержек.
 * Логика вынесена в {@link com.valkyrie.client.combat.KillAuraEngine} — модуль
 * хранит только состояние настроек.
 */
public final class KillAuraModule extends Module {
    public static final String MODE_SILENT = "Silent";
    public static final String MODE_SMOOTH = "Smooth";
    public static final String MODE_SNAP = "Snap";

    public static final String PRIORITY_DISTANCE = "Distance";
    public static final String PRIORITY_HEALTH = "Health";
    public static final String PRIORITY_ANGLE = "Angle";

    public static final String TIMING_LEGACY = "Legacy";
    public static final String TIMING_MODERN = "Modern";
    public static final String TIMING_HYBRID = "Hybrid";

    public final Setting.Slider range;
    public final Setting.Mode rotation;
    public final Setting.Slider rotationSpeed;
    public final Setting.Bool randomCenter;
    public final Setting.Slider fov;
    public final Setting.Slider minCps;
    public final Setting.Slider maxCps;
    public final Setting.Mode timing;
    public final Setting.Slider cooldownThreshold;
    public final Setting.Bool wallCheck;
    public final Setting.Mode priority;
    public final Setting.Slider switchDelay;
    public final Setting.Slider predict;
    public final TargetFilter targets;

    public KillAuraModule() {
        super("KillAura", "auto attack", Category.COMBAT);

        // 3.0 — ванильная дальность взаимодействия. Выше неё атаку режут даже
        // мягкие античиты, а симуляционные (GrimAC) банят почти сразу.
        range = add(new Setting.Slider("range", "Range", 3.0f, 2.0f, 4.5f, Setting.Slider::decimal));

        rotation = add(new Setting.Mode("rotation", "Rotation", List.of(MODE_SILENT, MODE_SMOOTH, MODE_SNAP), MODE_SILENT));
        // 55°/тик — середина коридора, который проходит эвристику Grizzly и MX.
        rotationSpeed = add(new Setting.Slider("rotspeed", "Rot speed", 55.0f, 10.0f, 180.0f, Setting.Slider::plain));
        rotationSpeed.visibleWhen(() -> !rotation.is(MODE_SNAP));

        randomCenter = add(new Setting.Bool("randomcenter", "Random center", "vary aim point", true));
        fov = add(new Setting.Slider("fov", "FOV", 120.0f, 30.0f, 360.0f, Setting.Slider::plain));

        minCps = add(new Setting.Slider("mincps", "Min CPS", 8.0f, 1.0f, 20.0f, Setting.Slider::plain));
        maxCps = add(new Setting.Slider("maxcps", "Max CPS", 12.0f, 1.0f, 20.0f, Setting.Slider::plain));
        timing = add(new Setting.Mode("combatmode", "Combat timing", List.of(TIMING_LEGACY, TIMING_MODERN, TIMING_HYBRID), TIMING_MODERN));
        cooldownThreshold = add(new Setting.Slider("cooldownthreshold", "Cooldown ready", 0.92f, 0.80f, 1.0f, Setting.Slider::percent));
        cooldownThreshold.visibleWhen(() -> !timing.is(TIMING_LEGACY));
        minCps.visibleWhen(() -> !timing.is(TIMING_MODERN));
        maxCps.visibleWhen(() -> !timing.is(TIMING_MODERN));

        wallCheck = add(new Setting.Bool("wallcheck", "Wall check", "need line of sight", true));

        priority = add(new Setting.Mode("priority", "Priority", List.of(PRIORITY_DISTANCE, PRIORITY_HEALTH, PRIORITY_ANGLE), PRIORITY_DISTANCE));
        switchDelay = add(new Setting.Slider("switchdelay", "Switch delay", 4.0f, 0.0f, 20.0f, Setting.Slider::plain));
        predict = add(new Setting.Slider("predict", "Predict", 1.0f, 0.0f, 4.0f, Setting.Slider::decimal));

        targets = TargetFilter.attach(this, true, true, false);
    }

    /** Верхняя граница CPS не может оказаться ниже нижней. */
    public float effectiveMaxCps() {
        return Math.max(minCps.value(), maxCps.value());
    }

    @Override
    public void load(JsonObject json) {
        super.load(json);
        // До Valkyrie Flow использовался boolean cooldown. Сохраняем смысл старых конфигов.
        if (!json.has("combatmode") && json.has("cooldown")) {
            try {
                timing.set(json.get("cooldown").getAsBoolean() ? TIMING_MODERN : TIMING_LEGACY);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    protected void onDisable() {
        com.valkyrie.client.combat.KillAuraEngine.reset();
    }
}
