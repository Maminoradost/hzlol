package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;

/** Светлый адаптивный прицел вместо ванильной текстуры. */
public final class CrosshairModule extends Module {
    public final Setting.Slider size;
    public final Setting.Slider gap;
    public final Setting.Bool dynamic;

    public CrosshairModule() {
        super("Crosshair", "adaptive aim point", Category.VISUALS, true);
        size = add(new Setting.Slider("size", "Size", 4.0f, 2.0f, 9.0f, Setting.Slider::plain));
        gap = add(new Setting.Slider("gap", "Gap", 3.0f, 1.0f, 7.0f, Setting.Slider::plain));
        dynamic = add(new Setting.Bool("dynamic", "Dynamic", "target + cooldown response", true));
    }
}
