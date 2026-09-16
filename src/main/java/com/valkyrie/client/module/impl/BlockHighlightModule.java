package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import java.util.List;

/** Светлый Sakura-контур выбранного блока вместо ванильной чёрной рамки. */
public final class BlockHighlightModule extends Module {
    public final Setting.Mode style;
    public final Setting.Slider opacity;
    public final Setting.Bool pulse;

    public BlockHighlightModule() {
        super("Block highlight", "sakura block selection", Category.VISUALS, true);
        style = add(new Setting.Mode("style", "Style", List.of("Outline", "Fill", "Both"), "Both"));
        opacity = add(new Setting.Slider("opacity", "Opacity", 0.42f, 0.08f, 0.80f, Setting.Slider::percent));
        pulse = add(new Setting.Bool("pulse", "Pulse", "calm opacity breath", true));
    }
}
