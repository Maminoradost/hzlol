package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;

/** Визуализация направления и будущей позиции текущей цели. */
public final class IntentLensModule extends Module {
    public final Setting.Slider prediction;
    public final Setting.Bool attackWindow;
    public final Setting.Slider opacity;

    public IntentLensModule() {
        super("Intent lens", "target movement intent", Category.VISUALS);
        prediction = add(new Setting.Slider("prediction", "Prediction", 4.0f, 1.0f, 8.0f, Setting.Slider::plain));
        attackWindow = add(new Setting.Bool("attackwindow", "Attack window", "ready + in range", true));
        opacity = add(new Setting.Slider("opacity", "Opacity", 0.58f, 0.15f, 0.90f, Setting.Slider::percent));
    }
}
