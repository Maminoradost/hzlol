package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import java.util.List;

/** Общая безопасная трансформация first-person сцены обеих рук. */
public final class ItemAnimationsModule extends Module {
    public final Setting.Mode style;
    public final Setting.Slider scale;
    public final Setting.Slider x;
    public final Setting.Slider y;
    public final Setting.Slider z;
    public final Setting.Slider swing;

    public ItemAnimationsModule() {
        super("Item animations", "first-person hand scene", Category.VISUALS);
        style = add(new Setting.Mode("style", "Style", List.of("Vanilla", "Compact", "Sakura"), "Sakura"));
        scale = add(new Setting.Slider("scale", "Scale", 0.92f, 0.65f, 1.25f, Setting.Slider::percent));
        x = add(new Setting.Slider("x", "X offset", 0.0f, -0.6f, 0.6f, Setting.Slider::decimal));
        y = add(new Setting.Slider("y", "Y offset", -0.04f, -0.6f, 0.6f, Setting.Slider::decimal));
        z = add(new Setting.Slider("z", "Z offset", 0.0f, -0.6f, 0.6f, Setting.Slider::decimal));
        swing = add(new Setting.Slider("swing", "Swing accent", 0.28f, 0.0f, 1.0f, Setting.Slider::percent));
    }
}
