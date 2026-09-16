package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import com.valkyrie.client.module.TargetFilter;

/** Мягко помогает довести ручной прицел; не атакует самостоятельно. */
public final class AimAssistModule extends Module {
    public final Setting.Slider range;
    public final Setting.Slider fov;
    public final Setting.Slider speed;
    public final TargetFilter targets;

    public AimAssistModule() {
        super("Aim Assist", "gentle manual aim", Category.COMBAT);
        range = add(new Setting.Slider("range", "Range", 4.0f, 2.0f, 6.0f, Setting.Slider::decimal));
        fov = add(new Setting.Slider("fov", "FOV", 70.0f, 20.0f, 140.0f, Setting.Slider::plain));
        speed = add(new Setting.Slider("speed", "Assist speed", 18.0f, 4.0f, 45.0f, Setting.Slider::plain));
        targets = TargetFilter.attach(this, true, true, false);
    }
}
