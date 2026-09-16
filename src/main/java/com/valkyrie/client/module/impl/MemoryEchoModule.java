package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import java.util.List;

/** Растворяющаяся визуальная память недавней траектории. */
public final class MemoryEchoModule extends Module {
    public final Setting.Mode entities;
    public final Setting.Mode style;
    public final Setting.Slider echoes;
    public final Setting.Slider lifetime;
    public final Setting.Bool movingOnly;

    public MemoryEchoModule() {
        super("Memory echo", "recent movement trail", Category.VISUALS);
        entities = add(new Setting.Mode("entities", "Entities", List.of("Target", "Self", "Both"), "Target"));
        style = add(new Setting.Mode("style", "Style", List.of("Ribbon", "Petals"), "Ribbon"));
        echoes = add(new Setting.Slider("echoes", "Echoes", 6.0f, 3.0f, 10.0f, Setting.Slider::plain));
        lifetime = add(new Setting.Slider("lifetime", "Lifetime", 1.1f, 0.4f, 2.2f, Setting.Slider::decimal));
        movingOnly = add(new Setting.Bool("movingonly", "Moving only", "hide stationary echoes", true));
    }
}
