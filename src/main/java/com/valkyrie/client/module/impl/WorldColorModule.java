package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import java.util.List;

/** Мягкая цветокоррекция мира и погодная атмосфера без изменения level state. */
public final class WorldColorModule extends Module {
    public final Setting.Mode mood;
    public final Setting.Color customColor;
    public final Setting.Slider strength;
    public final Setting.Slider fog;
    public final Setting.Bool weatherBoost;

    public WorldColorModule() {
        super("World color", "weather ambience", Category.VISUALS);
        mood = add(new Setting.Mode("mood", "Mood", List.of("Natural", "Sakura", "Moonlit", "Ashen", "Custom"), "Sakura"));
        customColor = add(new Setting.Color("customcolor", "Custom color", 0xFFE8A0B4));
        customColor.visibleWhen(() -> mood.is("Custom"));
        strength = add(new Setting.Slider("strength", "Color strength", 0.42f, 0.0f, 1.0f, Setting.Slider::percent));
        fog = add(new Setting.Slider("fog", "Atmospheric fog", 0.18f, 0.0f, 0.65f, Setting.Slider::percent));
        weatherBoost = add(new Setting.Bool("weatherboost", "Weather ambience", "rain deepens tint + fog", true));
    }

    public int color() {
        return switch (mood.value()) {
            case "Natural" -> 0xFFBBC5CE;
            case "Moonlit" -> 0xFF788FC8;
            case "Ashen" -> 0xFF8B8589;
            case "Custom" -> customColor.value() | 0xFF000000;
            default -> 0xFFE8A0B4;
        };
    }
}
