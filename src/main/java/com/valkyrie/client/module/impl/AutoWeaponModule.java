package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;

/** Перед автоматической атакой выбирает лучший меч или топор в hotbar. */
public final class AutoWeaponModule extends Module {
    public AutoWeaponModule() {
        super("AutoWeapon", "best hotbar weapon", Category.COMBAT);
    }
}
