package com.valkyrie.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.valkyrie.ValkyrieClient;
import net.minecraft.client.KeyMapping;

public final class ValkyrieKeyMappings {
    public static final KeyMapping OPEN_GUI = new KeyMapping(
        "key." + ValkyrieClient.MODID + ".open_gui",
        InputConstants.KEY_RSHIFT,
        KeyMapping.Category.MISC
    );

    private ValkyrieKeyMappings() {
    }
}

