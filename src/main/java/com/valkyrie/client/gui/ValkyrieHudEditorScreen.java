package com.valkyrie.client.gui;

import com.valkyrie.client.config.ValkyrieOptions;
import com.valkyrie.client.config.ValkyrieOptionsManager;
import com.valkyrie.client.gui.notify.Notifications;
import com.valkyrie.client.gui.theme.Animated;
import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.hud.ValkyrieHudOverlay;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.ArmorModule;
import com.valkyrie.client.module.impl.EffectsModule;
import com.valkyrie.client.module.impl.InfoModule;
import com.valkyrie.client.module.impl.KeystrokesModule;
import com.valkyrie.client.module.impl.TargetHudModule;
import com.valkyrie.client.module.impl.WatermarkModule;
import com.valkyrie.client.render.ValkyrieFonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;

public final class ValkyrieHudEditorScreen extends Screen {
    private static final int DONE_WIDTH = 78;
    private static final int DONE_HEIGHT = 26;

    private final Map<HudBlock, Animated> lifts = new EnumMap<>(HudBlock.class);
    private HudBlock dragging;
    private int dragOffsetX;
    private int dragOffsetY;
    private long openedAtNs;
    private float doneHover;

    public ValkyrieHudEditorScreen() {
        super(Component.literal("Valkyrie HUD Editor"));
    }

    @Override
    protected void init() {
        openedAtNs = System.nanoTime();
    }

    /**
     * Движок сам зовёт этот метод перед {@link #render} в отдельном страту.
     * Ручной повторный вызов даёт второе размытие за кадр и краш GuiRenderState.
     */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.blurBeforeThisStratum();
        guiGraphics.fillGradient(0, 0, this.width, this.height, Theme.SCRIM_TOP(), Theme.SCRIM_BOTTOM());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        ThunderRender.beginFrame();
        float elapsed = (System.nanoTime() - openedAtNs) / 1_000_000_000.0f;
        float alpha = Mth.clamp(elapsed / 0.22f, 0.0f, 1.0f);
        renderGrid(guiGraphics, alpha);
        renderBlocks(guiGraphics, mouseX, mouseY, alpha);
        renderTopHint(guiGraphics, alpha);
        renderDone(guiGraphics, mouseX, mouseY, alpha);
        Notifications.render(guiGraphics, this.font);
    }

    private void renderGrid(GuiGraphics guiGraphics, float alpha) {
        int color = Theme.alpha(Theme.BORDER(), 0.12f * alpha);
        for (int x = 0; x < width; x += 24) {
            guiGraphics.fill(x, 0, x + 1, height, color);
        }
        for (int y = 0; y < height; y += 24) {
            guiGraphics.fill(0, y, width, y + 1, color);
        }
    }

    /** Анимация подъёма живёт по блоку, поэтому хранится в карте, а не в поле. */
    private Animated liftAnimation(HudBlock block) {
        return lifts.computeIfAbsent(block, key -> Animated.instant(0.0f));
    }

    private void renderBlocks(GuiGraphics guiGraphics, int mouseX, int mouseY, float alpha) {
        for (HudBlock block : HudBlock.values()) {
            if (!block.active()) {
                continue;
            }
            Rect rect = block.rect(this.font, Minecraft.getInstance(), width, height);
            boolean hovered = ThunderRender.isHovered(mouseX, mouseY, rect.x, rect.y, rect.w, rect.h);
            boolean lifted = hovered || dragging == block;
            // Подъём и подсветка догоняют состояние плавно, иначе блок «щёлкает».
            float lift = liftAnimation(block).update(lifted);

            int fill = Theme.mix(Theme.HUD_SURFACE(), Theme.SURFACE_RAISED(), lift * 0.45f);
            ThunderRender.drawFloatingPanel(
                guiGraphics,
                rect.x,
                rect.y,
                rect.w,
                rect.h,
                Theme.RADIUS_CARD,
                fill,
                3 + Math.round(lift * 3),
                alpha
            );
            ThunderRender.drawAccentBar(guiGraphics, rect.x + 7, rect.y + rect.h - 1, rect.w - 14, 1, accent(), alpha * 0.38f);
            ValkyrieFonts.draw(guiGraphics, font, block.title, rect.x + 8, rect.y + 5, Theme.alpha(Theme.TEXT_PRIMARY(), alpha));
            ValkyrieFonts.draw(guiGraphics, font, block.subtitle, rect.x + 8, rect.y + 16, Theme.alpha(Theme.TEXT_SECONDARY(), 0.76f * alpha));
            if (lift > 0.01f) {
                int glow = Math.round(lift * 3);
                ThunderRender.drawRoundRect(
                    guiGraphics,
                    rect.x - glow,
                    rect.y - glow,
                    rect.w + glow * 2,
                    rect.h + glow * 2,
                    12,
                    ThunderRender.withAlpha(accent(), 0.07f * lift * alpha)
                );
            }
        }
    }

    private void renderTopHint(GuiGraphics guiGraphics, float alpha) {
        String hint = "Drag blocks to move · Right click to reset";
        int panelW = ValkyrieFonts.width(font, hint) + 28;
        int panelH = 26;
        int x = (width - panelW) / 2;
        int y = 14;
        ThunderRender.drawFloatingPanel(guiGraphics, x, y, panelW, panelH, Theme.RADIUS_PILL, Theme.SURFACE(), 8, alpha);
        ValkyrieFonts.drawCentered(
            guiGraphics,
            font,
            hint,
            width / 2,
            y + (panelH - ValkyrieFonts.lineHeight(font)) / 2,
            Theme.alpha(Theme.TEXT_SECONDARY(), alpha)
        );
    }

    private void renderDone(GuiGraphics guiGraphics, int mouseX, int mouseY, float alpha) {
        int w = DONE_WIDTH;
        int h = DONE_HEIGHT;
        int x = doneX();
        int y = doneY();
        boolean hovered = ThunderRender.isHovered(mouseX, mouseY, x, y, w, h);
        doneHover = ThunderRender.fast(doneHover, hovered ? 1.0f : 0.0f, Theme.SPEED_INSTANT);

        int accent = accent();
        int fill = Theme.mix(accent, ThunderRender.lighten(accent, 0.18f), doneHover);
        ThunderRender.drawElevation(guiGraphics, x, y, w, h, h / 2, 8 + Math.round(doneHover * 4), alpha);
        ThunderRender.drawRoundRect(guiGraphics, x, y, w, h, h / 2, Theme.alpha(fill, alpha));
        ValkyrieFonts.drawCentered(
            guiGraphics,
            font,
            "Done",
            x + w / 2,
            y + (h - ValkyrieFonts.lineHeight(font)) / 2,
            Theme.alpha(Theme.TEXT_ON_ACCENT(), alpha)
        );
    }

    private int doneX() {
        return width - DONE_WIDTH - 20;
    }

    private int doneY() {
        return height - DONE_HEIGHT - 18;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int button = event.button();
        if (button == 0 && ThunderRender.isHovered(mouseX, mouseY, doneX(), doneY(), DONE_WIDTH, DONE_HEIGHT)) {
            ValkyrieUiFeedback.click();
            onClose();
            return true;
        }
        for (HudBlock block : HudBlock.values()) {
            if (!block.active()) {
                continue;
            }
            Rect rect = block.rect(this.font, Minecraft.getInstance(), width, height);
            if (ThunderRender.isHovered(mouseX, mouseY, rect.x, rect.y, rect.w, rect.h)) {
                if (button == 1) {
                    block.reset(width, height);
                    ValkyrieUiFeedback.click();
                    ValkyrieOptionsManager.save();
                    return true;
                }
                if (button == 0) {
                    dragging = block;
                    dragOffsetX = mouseX - rect.x;
                    dragOffsetY = mouseY - rect.y;
                    ValkyrieUiFeedback.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging == null) {
            return super.mouseDragged(event, dragX, dragY);
        }
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        Rect rect = dragging.rect(this.font, Minecraft.getInstance(), width, height);
        int x = Mth.clamp(mouseX - dragOffsetX, 2, Math.max(2, width - rect.w - 2));
        int y = Mth.clamp(mouseY - dragOffsetY, 2, Math.max(2, height - rect.h - 2));
        dragging.moveTo(x, y);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            ValkyrieOptionsManager.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        ValkyrieOptionsManager.save();
        Minecraft.getInstance().setScreen(new ValkyrieGuiScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int accent() {
        return Theme.accent();
    }

    private enum HudBlock {
        WATERMARK("Watermark", "client + fps"),
        KEYS("Keystrokes", "WASD + mouse"),
        INFO("Info", "coords / ping / session"),
        ARMOR("Armor", "durability"),
        EFFECTS("Effects", "active potions"),
        TARGET("Target HUD", "aura target");

        private final String title;
        private final String subtitle;

        HudBlock(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }

        /**
         * Выключённые блоки в редакторе не показываются: двигать то, чего не видно
         * на экране, бессмысленно и путает — позиция будто бы не применяется.
         */
        private boolean active() {
            return switch (this) {
                case WATERMARK -> ModuleRegistry.isEnabled(WatermarkModule.class);
                case KEYS -> ModuleRegistry.isEnabled(KeystrokesModule.class);
                case INFO -> ModuleRegistry.isEnabled(InfoModule.class);
                case ARMOR -> ModuleRegistry.isEnabled(ArmorModule.class);
                case EFFECTS -> ModuleRegistry.isEnabled(EffectsModule.class);
                case TARGET -> ModuleRegistry.isEnabled(TargetHudModule.class);
            };
        }

        private Rect rect(Font font, Minecraft minecraft, int screenWidth, int screenHeight) {
            ValkyrieOptions options = ValkyrieOptionsManager.OPTIONS;
            return switch (this) {
                case WATERMARK -> new Rect(options.hudWatermarkX, options.hudWatermarkY, ValkyrieHudOverlay.resolveWatermarkWidth(font, minecraft), ValkyrieHudOverlay.watermarkHeight());
                case KEYS -> new Rect(options.hudKeysX, options.hudKeysY >= 0 ? options.hudKeysY : screenHeight - 10 - ValkyrieHudOverlay.keystrokesHeight(), ValkyrieHudOverlay.keystrokesWidth(), ValkyrieHudOverlay.keystrokesHeight());
                case INFO -> new Rect(options.hudInfoX, options.hudInfoY, ValkyrieHudOverlay.infoWidth(), ValkyrieHudOverlay.infoHeight());
                case ARMOR -> {
                    int armorWidth = ValkyrieHudOverlay.armorWidth(font, minecraft);
                    yield new Rect(
                        options.hudArmorX >= 0 ? options.hudArmorX : (screenWidth - armorWidth) / 2,
                        options.hudArmorY >= 0 ? options.hudArmorY : screenHeight - 50,
                        armorWidth,
                        ValkyrieHudOverlay.armorHeight()
                    );
                }
                case EFFECTS -> new Rect(
                    options.hudEffectsX >= 0 ? options.hudEffectsX : screenWidth - ValkyrieHudOverlay.effectsWidth() - 10,
                    options.hudEffectsY,
                    ValkyrieHudOverlay.effectsWidth(),
                    ValkyrieHudOverlay.effectsHeight()
                );
                case TARGET -> new Rect(
                    options.hudTargetX >= 0 ? options.hudTargetX : (screenWidth - ValkyrieHudOverlay.targetWidth()) / 2,
                    options.hudTargetY >= 0 ? options.hudTargetY : screenHeight - 112,
                    ValkyrieHudOverlay.targetWidth(),
                    ValkyrieHudOverlay.targetHeight()
                );
            };
        }

        private void moveTo(int x, int y) {
            ValkyrieOptions options = ValkyrieOptionsManager.OPTIONS;
            switch (this) {
                case WATERMARK -> {
                    options.hudWatermarkX = x;
                    options.hudWatermarkY = y;
                }
                case KEYS -> {
                    options.hudKeysX = x;
                    options.hudKeysY = y;
                }
                case INFO -> {
                    options.hudInfoX = x;
                    options.hudInfoY = y;
                }
                case ARMOR -> {
                    options.hudArmorX = x;
                    options.hudArmorY = y;
                }
                case EFFECTS -> {
                    options.hudEffectsX = x;
                    options.hudEffectsY = y;
                }
                case TARGET -> {
                    options.hudTargetX = x;
                    options.hudTargetY = y;
                }
            }
        }

        private void reset(int screenWidth, int screenHeight) {
            ValkyrieOptions options = ValkyrieOptionsManager.OPTIONS;
            switch (this) {
                case WATERMARK -> {
                    options.hudWatermarkX = 10;
                    options.hudWatermarkY = 10;
                }
                case KEYS -> {
                    options.hudKeysX = 10;
                    options.hudKeysY = screenHeight - 67;
                }
                case INFO -> {
                    options.hudInfoX = 10;
                    options.hudInfoY = 44;
                }
                case ARMOR -> {
                    // Ширина брони плавает вместе с экипировкой, поэтому сбрасываем
                    // в автопозицию: центровка пересчитается на каждом кадре.
                    options.hudArmorX = -1;
                    options.hudArmorY = -1;
                }
                case EFFECTS -> {
                    options.hudEffectsX = screenWidth - ValkyrieHudOverlay.effectsWidth() - 10;
                    options.hudEffectsY = 10;
                }
                case TARGET -> {
                    options.hudTargetX = (screenWidth - ValkyrieHudOverlay.targetWidth()) / 2;
                    options.hudTargetY = screenHeight - 112;
                }
            }
        }
    }

    private record Rect(int x, int y, int w, int h) {
    }
}
