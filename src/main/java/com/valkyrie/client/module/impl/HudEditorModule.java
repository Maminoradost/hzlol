package com.valkyrie.client.module.impl;

import com.valkyrie.client.gui.ValkyrieHudEditorScreen;
import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import net.minecraft.client.Minecraft;

/**
 * Открывает редактор расположения блоков HUD.
 * <p>
 * Действие, а не состояние: тумблера нет, клик по строке сразу открывает экран.
 */
public final class HudEditorModule extends Module {
    public HudEditorModule() {
        super("Edit layout", "drag blocks", Category.HUD);
    }

    @Override
    public boolean hasToggle() {
        return false;
    }

    @Override
    public boolean isAction() {
        return true;
    }

    @Override
    public void runAction() {
        Minecraft.getInstance().setScreen(new ValkyrieHudEditorScreen());
    }
}
