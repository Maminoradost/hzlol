package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;

/** Перекладывает тотем в offhand только при опасном эффективном здоровье. */
public final class AutoTotemModule extends Module {
    public final Setting.Slider health;

    public AutoTotemModule() {
        super("AutoTotem", "emergency offhand", Category.COMBAT);
        health = add(new Setting.Slider("health", "Health", 8.0f, 2.0f, 20.0f, value -> Math.round(value) + " hp"));
    }
}
