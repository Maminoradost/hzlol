package com.valkyrie.client.render;

import com.valkyrie.client.render.font.GfxFontRenderers;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/** Текст UI: TTF-рендерер Gfx, если он готов, иначе ванильный {@link Font}. */
public final class ValkyrieFonts {
    private ValkyrieFonts() {
    }

    public static Component titleText(String value) {
        return Component.literal(value).withStyle(Style.EMPTY.withBold(true));
    }

    public static void draw(GuiGraphics guiGraphics, Font font, String value, int x, int y, int color) {
        if (GfxFontRenderers.isReady()) {
            GfxFontRenderers.ui().drawString(guiGraphics, value, x, y, color);
            return;
        }
        guiGraphics.drawString(font, value, x, y, color, false);
    }

    public static void drawTitle(GuiGraphics guiGraphics, Font font, String value, int x, int y, int color) {
        if (GfxFontRenderers.isReady()) {
            GfxFontRenderers.uiTitle().drawString(guiGraphics, value, x, y, color);
            return;
        }
        guiGraphics.drawString(font, titleText(value), x, y, color, false);
    }

    public static void drawCentered(GuiGraphics guiGraphics, Font font, String value, int centerX, int y, int color) {
        if (GfxFontRenderers.isReady()) {
            GfxFontRenderers.ui().drawCenteredString(guiGraphics, value, centerX, y, color);
            return;
        }
        guiGraphics.drawString(font, value, centerX - font.width(value) / 2, y, color, false);
    }

    public static void drawCenteredTitle(GuiGraphics guiGraphics, Font font, String value, int centerX, int y, int color) {
        if (GfxFontRenderers.isReady()) {
            GfxFontRenderers.uiTitle().drawCenteredString(guiGraphics, value, centerX, y, color);
            return;
        }
        Component component = titleText(value);
        guiGraphics.drawString(font, component, centerX - font.width(component) / 2, y, color, false);
    }

    public static int width(Font font, String value) {
        if (GfxFontRenderers.isReady()) {
            return (int) Math.ceil(GfxFontRenderers.ui().getStringWidth(value));
        }
        return font.width(value);
    }

    public static int titleWidth(Font font, String value) {
        if (GfxFontRenderers.isReady()) {
            return (int) Math.ceil(GfxFontRenderers.uiTitle().getStringWidth(value));
        }
        return font.width(titleText(value));
    }

    public static int lineHeight(Font font) {
        if (GfxFontRenderers.isReady()) {
            return GfxFontRenderers.ui().lineHeight();
        }
        return font.lineHeight;
    }
}
