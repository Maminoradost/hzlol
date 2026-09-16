package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;

/** Экипировка с прочностью над хотбаром. */
public final class ArmorModule extends Module {
    public ArmorModule() {
        super("Armor", "durability", Category.HUD, true);
    }
}
