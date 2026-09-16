package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import com.valkyrie.client.module.TargetFilter;

/** Линии от центра экрана к целям. */
public final class TracersModule extends Module {
    public final Setting.Bool distanceLabels;
    public final Setting.Color color;
    public final Setting.Slider opacity;
    public final Setting.Slider range;
    public final TargetFilter targets;

    public TracersModule() {
        super("Tracers", "from crosshair", Category.VISUALS);
        distanceLabels = add(new Setting.Bool("labels", "Distance", "range pills", true));
        color = add(new Setting.Color("color", "Color", 0xFFE8B4C8));
        opacity = add(new Setting.Slider("opacity", "Opacity", 0.72f, 0.05f, 1.0f, Setting.Slider::percent));
        range = add(new Setting.Slider("range", "Range", 96.0f, 16.0f, 160.0f, Setting.Slider::meters));
        targets = TargetFilter.attach(this, true, true, false);
    }
}
