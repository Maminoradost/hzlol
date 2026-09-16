package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import java.util.List;

/** Растворяющиеся следы существующих снарядов. */
public final class ProjectileTrailsModule extends Module {
    public final Setting.Mode style;
    public final Setting.Slider lifetime;
    public final Setting.Slider range;
    public final Setting.Bool pearls;
    public final Setting.Bool arrows;
    public final Setting.Bool throwables;
    public final Setting.Bool tridents;

    public ProjectileTrailsModule() {
        super("Projectile trails", "projectile movement trails", Category.VISUALS);
        style = add(new Setting.Mode("style", "Style", List.of("Ribbon", "Echo", "Petals"), "Ribbon"));
        lifetime = add(new Setting.Slider("lifetime", "Lifetime", 1.4f, 0.4f, 3.0f, Setting.Slider::decimal));
        range = add(new Setting.Slider("range", "Range", 96.0f, 24.0f, 192.0f, Setting.Slider::meters));
        pearls = add(new Setting.Bool("pearls", "Pearls", "ender pearls", true));
        arrows = add(new Setting.Bool("arrows", "Arrows", "arrows and spectral arrows", true));
        throwables = add(new Setting.Bool("throwables", "Throwables", "snowballs and potions", true));
        tridents = add(new Setting.Bool("tridents", "Tridents", "thrown tridents", true));
    }
}
