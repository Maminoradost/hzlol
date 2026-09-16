package com.valkyrie.client.render;

import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

/** Screen-space line strip (single smooth bar, not pixel steps). */
public final class ValkyrieLines {
    private ValkyrieLines() {
    }

    public static void drawLine(GuiGraphics guiGraphics, float x0, float y0, float x1, float y1, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length < 0.5f) {
            return;
        }

        float angle = (float) Math.atan2(dy, dx);
        Matrix3x2fStack pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.rotate(angle);

        int left = 0;
        int top = 0;
        int right = (int) Math.ceil(length);
        int bottom = 1;
        guiGraphics.fill(left, top, right, bottom, color);

        pose.popMatrix();
    }

    public static void drawEndpoint(GuiGraphics guiGraphics, float x, float y, int color) {
        float radius = 2.0f;
        int left = (int) (x - radius);
        int top = (int) (y - radius);
        int size = (int) (radius * 2);
        guiGraphics.fill(left, top, left + size, top + size, color);
    }
}
