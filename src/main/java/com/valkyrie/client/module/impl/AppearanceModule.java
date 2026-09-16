package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;
import java.util.List;

/**
 * Оформление клиента целиком: акцент и прозрачность HUD.
 * <p>
 * Не функция, а группа настроек, поэтому тумблера нет — строка только
 * раскрывается.
 */
public final class AppearanceModule extends Module {
    public final Setting.Color accent;
    public final Setting.Slider hudOpacity;
    public final Setting.Mode particles;
    public final Setting.Mode sakuraMotion;
    public final Setting.Mode quotes;
    public final Setting.Bool reducedMotion;
    public final Setting.Bool playerPreview;
    public final Setting.Mode companion;
    public final Setting.Bool idleReactions;

    public AppearanceModule() {
        super("Appearance", "accent + opacity", Category.CLIENT);
        accent = add(new Setting.Color("accent", "Accent", 0xFFE8A0B4));
        hudOpacity = add(new Setting.Slider("hudopacity", "HUD opacity", 0.85f, 0.0f, 1.0f, Setting.Slider::percent));
        particles = add(new Setting.Mode("particles", "Particles", List.of("Off", "Low", "Normal"), "Normal"));
        sakuraMotion = add(new Setting.Mode("sakuramotion", "Sakura wind", List.of("Calm", "Windy"), "Calm"));
        sakuraMotion.visibleWhen(() -> !particles.is("Off"));
        quotes = add(new Setting.Mode("quotes", "Quotes", List.of("Off", "Menu", "All"), "All"));
        reducedMotion = add(new Setting.Bool("reducedmotion", "Reduced motion", "calmer UI animation", false));
        playerPreview = add(new Setting.Bool("playerpreview", "Player preview", "cursor-following model", true));
        companion = add(new Setting.Mode("companion", "Companion", List.of("Cursor", "Context"), "Context"));
        companion.visibleWhen(playerPreview::value);
        idleReactions = add(new Setting.Bool("idlereactions", "Idle reactions", "looks around when idle", true));
        idleReactions.visibleWhen(playerPreview::value);
    }

    @Override
    public boolean hasToggle() {
        return false;
    }
}
