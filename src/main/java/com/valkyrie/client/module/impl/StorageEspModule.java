package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import java.util.List;

/** Минималистичная карта ближайших хранилищ. */
public final class StorageEspModule extends Module {
    public final Setting.Mode style;
    public final Setting.Slider range;
    public final Setting.Slider maxMarkers;
    public final Setting.Slider opacity;
    public final Setting.Bool labels;
    public final Setting.Bool chests;
    public final Setting.Bool enderChests;
    public final Setting.Bool barrels;
    public final Setting.Bool shulkers;
    public final Setting.Bool furnaces;
    public final Setting.Bool hoppers;

    public StorageEspModule() {
        super("Storage ESP", "nearby containers", Category.VISUALS);
        style = add(new Setting.Mode("style", "Style", List.of("Corners", "Markers"), "Corners"));
        range = add(new Setting.Slider("range", "Range", 72.0f, 16.0f, 128.0f, Setting.Slider::meters));
        maxMarkers = add(new Setting.Slider("maxmarkers", "Markers", 48.0f, 8.0f, 96.0f, Setting.Slider::plain));
        opacity = add(new Setting.Slider("opacity", "Opacity", 0.56f, 0.10f, 0.90f, Setting.Slider::percent));
        labels = add(new Setting.Bool("labels", "Labels", "name + distance", true));
        chests = add(new Setting.Bool("chests", "Chests", "normal + trapped", true));
        enderChests = add(new Setting.Bool("enderchests", "Ender chests", "ender storage", true));
        barrels = add(new Setting.Bool("barrels", "Barrels", "barrels", true));
        shulkers = add(new Setting.Bool("shulkers", "Shulkers", "all colors", true));
        furnaces = add(new Setting.Bool("furnaces", "Furnaces", "furnace + smoker + blast", false));
        hoppers = add(new Setting.Bool("hoppers", "Hoppers", "hoppers", false));
    }
}
