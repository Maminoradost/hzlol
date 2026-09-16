package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import com.valkyrie.client.module.TargetFilter;

/** Атакует только живую цель под настоящим ванильным прицелом. */
public final class TriggerBotModule extends Module {
    public final Setting.Slider cooldown;
    public final TargetFilter targets;

    public TriggerBotModule() {
        super("TriggerBot", "attack under crosshair", Category.COMBAT);
        cooldown = add(new Setting.Slider("cooldown", "Cooldown ready", 0.92f, 0.80f, 1.0f, Setting.Slider::percent));
        targets = TargetFilter.attach(this, true, true, false);
    }
}
