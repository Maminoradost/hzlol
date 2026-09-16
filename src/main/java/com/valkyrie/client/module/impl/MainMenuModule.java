package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;

/** Замена ванильного главного меню экраном Valkyrie. */
public final class MainMenuModule extends Module {
    public MainMenuModule() {
        super("Main menu", "custom screen", Category.CLIENT, true);
    }
}
