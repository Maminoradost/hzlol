package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import com.valkyrie.client.module.TargetFilter;

/** Рамки, неймтеги и полосы здоровья существ. */
public final class EspModule extends Module {
    public final Setting.Bool boxes;
    public final Setting.Bool nametags;
    public final Setting.Bool healthBars;
    public final Setting.Bool offscreen;
    public final Setting.Color color;
    public final Setting.Slider opacity;
    public final Setting.Slider range;
    public final TargetFilter targets;

    public EspModule() {
        super("ESP", "entity boxes", Category.VISUALS);
        boxes = add(new Setting.Bool("boxes", "Boxes", "hitbox corners", true));
        nametags = add(new Setting.Bool("nametags", "Nametags", "name + range", true));
        healthBars = add(new Setting.Bool("health", "Health", "side bar", true));
        offscreen = add(new Setting.Bool("offscreen", "Off-screen", "edge markers", false));
        // Маркер за экраном — это прижатый неймтег, без него настройка бессмысленна.
        offscreen.visibleWhen(nametags::value);
        color = add(new Setting.Color("color", "Color", 0xFFE8B4C8));
        opacity = add(new Setting.Slider("opacity", "Opacity", 0.85f, 0.05f, 1.0f, Setting.Slider::percent));
        range = add(new Setting.Slider("range", "Range", 64.0f, 8.0f, 128.0f, Setting.Slider::meters));
        targets = TargetFilter.attach(this, true, true, false);
    }
}
