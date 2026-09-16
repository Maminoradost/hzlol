package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;

/** Предпочитает настоящий vanilla jump critical без position spoof-пакетов. */
public final class CriticalsModule extends Module {
    public CriticalsModule() {
        super("Criticals", "natural jump criticals", Category.COMBAT);
    }

    @Override
    protected void onDisable() {
        com.valkyrie.client.combat.CriticalsController.reset();
    }
}
