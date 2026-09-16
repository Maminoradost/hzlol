package com.valkyrie.client.render;

import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.WorldTimeModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/** Save/set/restore визуального времени строго вокруг world-render кадра. */
public final class VisualTime {
    private static ClientLevel savedLevel;
    private static long savedDayTime;

    private VisualTime() {
    }

    public static void beforeRender() {
        WorldTimeModule module = ModuleRegistry.get(WorldTimeModule.class);
        ClientLevel level = Minecraft.getInstance().level;
        if (!module.isEnabled() || level == null || savedLevel != null) {
            return;
        }
        savedLevel = level;
        savedDayTime = level.getDayTime();
        level.getLevelData().setDayTime(module.dayTime());
    }

    public static void afterRender() {
        if (savedLevel == null) {
            return;
        }
        // Не пишем в новый мир после перехода между измерениями.
        if (Minecraft.getInstance().level == savedLevel) {
            savedLevel.getLevelData().setDayTime(savedDayTime);
        }
        savedLevel = null;
    }

    public static void reset() {
        savedLevel = null;
    }
}
