package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;

/** Мягкий экранный halo вокруг текущей цели KillAura. */
public final class TargetGlowModule extends Module {
    public final Setting.Slider opacity;

    public TargetGlowModule() {
        super("Target glow", "aura target halo", Category.VISUALS, true);
        opacity = add(new Setting.Slider("opacity", "Opacity", 0.42f, 0.10f, 0.80f, Setting.Slider::percent));
    }
}
