package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;

/** Временно убирает небоевые HUD-элементы, не меняя их сохранённые состояния. */
public final class FocusModeModule extends Module {
    public FocusModeModule() {
        super("Focus mode", "combat-only HUD", Category.CLIENT);
    }
}
