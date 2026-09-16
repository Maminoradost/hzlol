package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;

/** Карточка текущей цели KillAura: модель, здоровье и дистанция. */
public final class TargetHudModule extends Module {
    public final Setting.Bool model;
    public final Setting.Bool distance;

    public TargetHudModule() {
        super("Target HUD", "current aura target", Category.HUD, true);
        model = add(new Setting.Bool("model", "Model", "entity preview", true));
        distance = add(new Setting.Bool("distance", "Distance", "target range", true));
    }
}
