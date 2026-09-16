package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import com.valkyrie.client.module.TargetFilter;

/** Радиальная карта угроз в виде Sakura-созвездия вокруг прицела. */
public final class ThreatConstellationModule extends Module {
    public final Setting.Slider radius;
    public final Setting.Slider range;
    public final Setting.Slider maxMarkers;
    public final Setting.Bool pulse;
    public final Setting.Bool groups;
    public final Setting.Bool targetConnection;
    public final TargetFilter targets;

    public ThreatConstellationModule() {
        super("Threat constellation", "radial threat map", Category.VISUALS);
        radius = add(new Setting.Slider("radius", "Radius", 54.0f, 32.0f, 96.0f, Setting.Slider::plain));
        range = add(new Setting.Slider("range", "Range", 48.0f, 12.0f, 128.0f, Setting.Slider::meters));
        maxMarkers = add(new Setting.Slider("maxmarkers", "Markers", 14.0f, 4.0f, 30.0f, Setting.Slider::plain));
        pulse = add(new Setting.Bool("pulse", "Threat pulse", "nearby enemies breathe", true));
        groups = add(new Setting.Bool("groups", "Constellations", "connect nearby markers", true));
        targetConnection = add(new Setting.Bool("targetline", "Target connection", "line to aura target", true));
        targets = TargetFilter.attach(this, true, true, false);
    }
}
