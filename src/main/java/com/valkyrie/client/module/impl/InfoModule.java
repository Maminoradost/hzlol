package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;

/** Панель со сведениями об игроке: координаты, пинг, время сессии. */
public final class InfoModule extends Module {
    public final Setting.Bool coords;
    public final Setting.Bool session;

    public InfoModule() {
        super("Info", "coords, ping", Category.HUD, true);
        coords = add(new Setting.Bool("coords", "Coords", "position + ping", true));
        session = add(new Setting.Bool("session", "Session", "play time", true));
    }

    /** Пустая панель не рисуется: рамка без строк выглядит как артефакт. */
    public boolean hasContent() {
        return coords.value() || session.value();
    }
}
