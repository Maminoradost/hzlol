package com.valkyrie.client.module.impl;

import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.Setting;

/** Плашка с именем клиента, FPS и графиком кадров. */
public final class WatermarkModule extends Module {
    public final Setting.Bool showFps;
    public final Setting.Bool showGraph;

    public WatermarkModule() {
        super("Watermark", "name + fps", Category.HUD, true);
        showFps = add(new Setting.Bool("fps", "FPS", "frame counter", true));
        showGraph = add(new Setting.Bool("graph", "Graph", "fps sparkline", true));
        // График без счётчика выглядит как безымянная кривая — прячем вместе.
        showGraph.visibleWhen(showFps::value);
    }
}
