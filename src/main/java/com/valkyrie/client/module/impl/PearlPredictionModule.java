package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;

/** Траектория жемчуга и Sakura-кольцо в точке столкновения. */
public final class PearlPredictionModule extends Module {
    public final Setting.Slider ticks;
    public final Setting.Bool onlyHolding;
    public final Setting.Bool distance;
    public final Setting.Slider opacity;

    public PearlPredictionModule() {
        super("Pearl prediction", "ender pearl trajectory", Category.VISUALS);
        ticks = add(new Setting.Slider("ticks", "Flight ticks", 90.0f, 30.0f, 160.0f, Setting.Slider::plain));
        onlyHolding = add(new Setting.Bool("onlyholding", "Only holding", "show with pearl in hand", true));
        distance = add(new Setting.Bool("distance", "Distance", "landing range label", true));
        opacity = add(new Setting.Slider("opacity", "Opacity", 0.62f, 0.15f, 0.90f, Setting.Slider::percent));
    }
}
