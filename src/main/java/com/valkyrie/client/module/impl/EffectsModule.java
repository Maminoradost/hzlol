package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;

/** Активные зелья с оставшимся временем. */
public final class EffectsModule extends Module {
    public EffectsModule() {
        super("Effects", "potions", Category.HUD, true);
    }
}
