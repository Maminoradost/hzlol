package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import java.util.List;

/** Фирменный цветок лепестков в точке удара и убийства. */
public final class SakuraBloomModule extends Module {
    public final Setting.Mode amount;
    public final Setting.Bool hitBloom;
    public final Setting.Bool killBloom;
    public final Setting.Bool wind;

    public SakuraBloomModule() {
        super("Sakura bloom", "petals on combat", Category.VISUALS, true);
        amount = add(new Setting.Mode("amount", "Amount", List.of("Low", "Normal", "Rich"), "Normal"));
        hitBloom = add(new Setting.Bool("hit", "Hit bloom", "petals on attack", true));
        killBloom = add(new Setting.Bool("kill", "Kill bloom", "large finishing bloom", true));
        wind = add(new Setting.Bool("wind", "Wind", "inherit sakura drift", true));
    }

    public int petalCount(boolean kill) {
        int base = switch (amount.value()) {
            case "Low" -> 6;
            case "Rich" -> 15;
            default -> 10;
        };
        return kill ? Math.min(48, base * 2 + 4) : base;
    }
}
