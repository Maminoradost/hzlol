package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;

/** Короткий Sakura-маркер в центре экрана при ударе KillAura. */
public final class HitMarkerModule extends Module {
    public final Setting.Slider size;

    public HitMarkerModule() {
        super("Hit marker", "attack feedback", Category.VISUALS, true);
        size = add(new Setting.Slider("size", "Size", 6.0f, 3.0f, 12.0f, Setting.Slider::plain));
    }
}
