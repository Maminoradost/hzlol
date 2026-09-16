package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;

/** Индикатор нажатых клавиш движения и кнопок мыши. */
public final class KeystrokesModule extends Module {
    public KeystrokesModule() {
        super("Keystrokes", "WASD + mouse", Category.HUD, true);
    }
}
