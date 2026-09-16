package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;

/** Мягкая рамка экрана при получении урона. */
public final class DamageTintModule extends Module {
    public final Setting.Slider opacity;

    public DamageTintModule() {
        super("Damage tint", "soft damage frame", Category.VISUALS, true);
        opacity = add(new Setting.Slider("opacity", "Opacity", 0.28f, 0.05f, 0.65f, Setting.Slider::percent));
    }
}
