package com.valkyrie.client.gui;

import com.valkyrie.client.gui.notify.Notifications;
import com.valkyrie.client.gui.theme.Palette;
import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.gui.widgets.RoundedButton;
import com.valkyrie.client.render.ValkyrieFonts;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

public final class ValkyrieMainMenu extends Screen {
    /** Тёмная графика логотипа — для светлого фона. */
    private static final ResourceLocation LOGO_ON_LIGHT = ResourceLocation.fromNamespaceAndPath(
        "valkyrieclient",
        "textures/gui/logo_dark.png"
    );
    /** Светлая графика логотипа — для тёмного фона. */
    private static final ResourceLocation LOGO_ON_DARK = ResourceLocation.fromNamespaceAndPath(
        "valkyrieclient",
        "textures/gui/logo.png"
    );
    private static final int LOGO_SOURCE_WIDTH = 772;
    private static final int LOGO_SOURCE_HEIGHT = 288;
    private static final int BUTTON_WIDTH = 164;
    private static final int BUTTON_HEIGHT = 22;
    private static final int BUTTON_GAP = 10;
    private static final int BUTTON_COUNT = 5;
    private static final int BUTTON_BLOCK_HEIGHT = BUTTON_COUNT * BUTTON_HEIGHT + (BUTTON_COUNT - 1) * BUTTON_GAP;

    private final long startNs = System.nanoTime();
    private float enterAnimation;
    private int buttonsTop;

    public ValkyrieMainMenu() {
        super(Component.translatable("menu.title"));
    }

    @Override
    protected void init() {
        enterAnimation = 0.0f;
        int centerX = this.width / 2;
        // Полный блок кнопок вместе с акриловой панелью всегда помещается по высоте.
        int preferredTop = this.height / 2 + 20;
        int maxTop = Math.max(72, this.height - BUTTON_BLOCK_HEIGHT - 42);
        buttonsTop = Mth.clamp(preferredTop, 72, maxTop);

        addButton(centerX, buttonsTop, BUTTON_WIDTH, BUTTON_HEIGHT, "menu.singleplayer",
            () -> this.minecraft.setScreen(new SelectWorldScreen(this)));
        addButton(centerX, buttonsTop + (BUTTON_HEIGHT + BUTTON_GAP), BUTTON_WIDTH, BUTTON_HEIGHT, "menu.multiplayer",
            () -> this.minecraft.setScreen(new JoinMultiplayerScreen(this)));
        addButton(centerX, buttonsTop + (BUTTON_HEIGHT + BUTTON_GAP) * 2, BUTTON_WIDTH, BUTTON_HEIGHT, "menu.options",
            () -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options)));
        addButton(centerX, buttonsTop + (BUTTON_HEIGHT + BUTTON_GAP) * 3, BUTTON_WIDTH, BUTTON_HEIGHT,
            Component.literal("Мой ТГК"), () -> Util.getPlatform().openUri("https://t.me/+5N4FXvQT9C8wYjNi"));
        addButton(centerX, buttonsTop + (BUTTON_HEIGHT + BUTTON_GAP) * 4, BUTTON_WIDTH, BUTTON_HEIGHT, "menu.quit",
            () -> this.minecraft.stop());
    }

    private void addButton(int centerX, int y, int width, int height, String translationKey, Runnable action) {
        addButton(centerX, y, width, height, Component.translatable(translationKey), action);
    }

    private void addButton(int centerX, int y, int width, int height, Component label, Runnable action) {
        this.addRenderableWidget(new RoundedButton(
            centerX - width / 2,
            y,
            width,
            height,
            label,
            button -> action.run()
        ));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        ThunderRender.beginFrame();
        boolean reducedMotion = com.valkyrie.client.module.ModuleRegistry
            .get(com.valkyrie.client.module.impl.AppearanceModule.class)
            .reducedMotion.value();
        enterAnimation = ThunderRender.fast(enterAnimation, 1.0f, reducedMotion ? 40.0f : 14.0f);
        float eased = easeOutBack(enterAnimation);
        float alpha = Mth.clamp(enterAnimation * 1.25f, 0.0f, 1.0f);

        renderLogo(guiGraphics, eased, alpha);
        renderButtonPanel(guiGraphics, alpha);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderSubtitle(guiGraphics, alpha);
        renderFooter(guiGraphics, alpha);
        com.valkyrie.client.gui.notify.ValkyrieQuotes.tickMenu(ThunderRender.frameSeconds());
        com.valkyrie.client.gui.notify.ValkyrieQuotes.render(guiGraphics, this.font, ThunderRender.frameSeconds());
        Notifications.render(guiGraphics, this.font);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int accent = Theme.accent();
        float seconds = (System.nanoTime() - startNs) / 1_000_000_000.0f;
        float wave = (Mth.sin(seconds * 0.22f) + 1.0f) * 0.5f;

        Palette palette = Theme.palette();
        int top = lerpColor(palette.menuTopA, palette.menuTopB, wave);
        int bottom = lerpColor(palette.menuBottomA, palette.menuBottomB, wave);
        guiGraphics.fillGradient(0, 0, this.width, this.height, top, bottom);
        guiGraphics.fillGradient(0, 0, this.width, this.height, ThunderRender.withAlpha(accent, 0.035f), 0x00000000);
        guiGraphics.fillGradient(0, this.height / 3, this.width, this.height, 0x00000000, palette.menuVignette);
        com.valkyrie.client.gui.particle.SakuraParticles.render(guiGraphics, partialTick, 1.0f);
    }

    private void renderParticles(GuiGraphics guiGraphics, float seconds) {
        // Эффект сакуры вынесен в SakuraParticles; вызов из renderBackground.
    }

    private void renderLogo(GuiGraphics guiGraphics, float eased, float alpha) {
        // Новый wordmark горизонтальный: ширину ограничиваем и экраном, и
        // свободной высотой над кнопками, сохраняя исходную пропорцию 772:288.
        int byScreen = Math.round(Mth.clamp(this.width * 0.34f, 210.0f, 390.0f));
        int freeHeight = Math.max(56, buttonsTop - 38);
        int byHeight = Math.round(freeHeight * (LOGO_SOURCE_WIDTH / (float) LOGO_SOURCE_HEIGHT));
        int logoWidth = Math.min(byScreen, byHeight);
        int logoHeight = Math.round(logoWidth * (LOGO_SOURCE_HEIGHT / (float) LOGO_SOURCE_WIDTH));
        int logoX = (this.width - logoWidth) / 2;
        int logoY = Math.max(18, buttonsTop - logoHeight - 18);
        float scale = 0.92f + 0.08f * eased;
        float centerX = logoX + logoWidth / 2.0f;
        float centerY = logoY + logoHeight / 2.0f;
        float logoScale = logoWidth / (float) LOGO_SOURCE_WIDTH;

        Matrix3x2fStack pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate(centerX, centerY);
        pose.scale(scale * logoScale, scale * logoScale);
        pose.translate(-LOGO_SOURCE_WIDTH / 2.0f, -LOGO_SOURCE_HEIGHT / 2.0f);

        guiGraphics.blit(
            RenderPipelines.GUI_TEXTURED,
            Theme.isDark() ? LOGO_ON_DARK : LOGO_ON_LIGHT,
            0,
            0,
            0.0f,
            0.0f,
            LOGO_SOURCE_WIDTH,
            LOGO_SOURCE_HEIGHT,
            LOGO_SOURCE_WIDTH,
            LOGO_SOURCE_HEIGHT,
            ThunderRender.withAlpha(0xFFFFFFFF, 0.92f * alpha)
        );

        pose.popMatrix();
    }

    private void renderButtonPanel(GuiGraphics guiGraphics, float alpha) {
        int panelWidth = 196;
        int panelHeight = BUTTON_BLOCK_HEIGHT + 28;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = buttonsTop - 14;
        int accent = Theme.accent();

        ThunderRender.drawFloatingPanel(
            guiGraphics,
            panelX,
            panelY,
            panelWidth,
            panelHeight,
            Theme.RADIUS_PANEL,
            Theme.SURFACE(),
            12,
            alpha
        );
        ThunderRender.drawAccentBar(guiGraphics, panelX + 56, panelY + panelHeight - 2, panelWidth - 112, 1, accent, alpha * 0.40f);
    }

    private void renderSubtitle(GuiGraphics guiGraphics, float alpha) {
        String text = com.valkyrie.client.gui.notify.ValkyrieQuotes.menuSubtitle();
        int color = Theme.alpha(Theme.TEXT_TERTIARY(), 0.6f * alpha);
        ValkyrieFonts.drawCentered(guiGraphics, this.font, text, this.width / 2, this.height - 36, color);
    }

    private void renderFooter(GuiGraphics guiGraphics, float alpha) {
        String left = "Valkyrie Client";
        String right = "0.9 Beta · Forge 1.21.10";
        ValkyrieFonts.draw(guiGraphics, this.font, left, 12, this.height - 18, Theme.alpha(Theme.TEXT_SECONDARY(), 0.70f * alpha));
        int rightWidth = ValkyrieFonts.width(this.font, right);
        ValkyrieFonts.draw(guiGraphics, this.font, right, this.width - rightWidth - 12, this.height - 18, Theme.alpha(Theme.TEXT_TERTIARY(), 0.62f * alpha));
    }

    private static float easeOutBack(float value) {
        float t = Mth.clamp(value, 0.0f, 1.0f);
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1.0f, 3.0) + c1 * (float) Math.pow(t - 1.0f, 2.0);
    }

    private static int lerpColor(int from, int to, float amount) {
        amount = Mth.clamp(amount, 0.0f, 1.0f);
        int a = (int) Mth.lerp(amount, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
        int r = (int) Mth.lerp(amount, (from >>> 16) & 0xFF, (to >>> 16) & 0xFF);
        int g = (int) Mth.lerp(amount, (from >>> 8) & 0xFF, (to >>> 8) & 0xFF);
        int b = (int) Mth.lerp(amount, from & 0xFF, to & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
