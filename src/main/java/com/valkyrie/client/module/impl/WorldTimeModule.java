package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import java.util.List;

/** Визуальный пресет времени суток без изменения серверного мира. */
public final class WorldTimeModule extends Module {
    public final Setting.Mode time;

    public WorldTimeModule() {
        super("World time", "visual sky preset", Category.VISUALS);
        time = add(new Setting.Mode("time", "Time", List.of("Day", "Sunset", "Night", "Midnight"), "Sunset"));
    }

    public long dayTime() {
        return switch (time.value()) {
            case "Day" -> 1_000L;
            case "Night" -> 13_000L;
            case "Midnight" -> 18_000L;
            default -> 12_000L;
        };
    }
}
