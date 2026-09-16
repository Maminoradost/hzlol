package com.valkyrie.client.gui.widgets;

import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.render.ValkyrieFonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

public final class RoundedButton extends AbstractButton {
    @FunctionalInterface
    public interface OnPress {
        void onPress(RoundedButton button);
    }

    private final OnPress onPress;
    private float hoverAnim;
    private boolean hoveredLastFrame;

    public RoundedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        com.valkyrie.client.gui.ValkyrieUiFeedback.click();
        onPress.onPress(this);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered() && this.active;
        if (hovered && !hoveredLastFrame) {
            com.valkyrie.client.gui.ValkyrieUiFeedback.hover();
        }
        hoveredLastFrame = hovered;
        hoverAnim = ThunderRender.fast(hoverAnim, hovered ? 1.0f : 0.0f, Theme.SPEED_INSTANT);

        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        int accent = Theme.accent();

        int fill = Theme.mix(Theme.SURFACE_RAISED(), Theme.alpha(accent, 0.09f), hoverAnim);

        int lift = Math.round(hoverAnim * 2.0f);
        int visualY = y - lift;
        int radius = Math.min(Theme.RADIUS_CARD, Math.max(8, height / 2 + 2));

        ThunderRender.drawElevation(guiGraphics, x, visualY, width, height, radius, 4 + Math.round(hoverAnim * 3), 1.0f);
        ThunderRender.drawRoundRect(guiGraphics, x, visualY, width, height, radius, fill);
        ThunderRender.drawRoundRect(
            guiGraphics,
            x + radius,
            visualY,
            width - radius * 2,
            1,
            0,
            ThunderRender.withAlpha(0xFFFFFFFF, 0.35f + hoverAnim * 0.20f)
        );
        ThunderRender.drawRoundOutline(guiGraphics, x, visualY, width, height, radius, Theme.alpha(Theme.BORDER(), 0.5f));

        if (hoverAnim > 0.02f) {
            int lineWidth = Math.round(width * (0.42f + hoverAnim * 0.26f));
            int lineX = x + (width - lineWidth) / 2;
            ThunderRender.drawAccentBar(guiGraphics, lineX, visualY + height - 2, lineWidth, 2, accent, hoverAnim);
        }

        Font font = Minecraft.getInstance().font;
        String label = getMessage().getString();
        int textWidth = ValkyrieFonts.width(font, label);
        int textX = x + (width - textWidth) / 2;
        int textY = visualY + (height - ValkyrieFonts.lineHeight(font)) / 2;
        int textColor = Theme.mix(Theme.TEXT_SECONDARY(), Theme.TEXT_PRIMARY(), hoverAnim);
        ValkyrieFonts.draw(guiGraphics, font, label, textX, textY, textColor);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
