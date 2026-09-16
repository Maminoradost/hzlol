package com.valkyrie.client.gui;

import com.valkyrie.client.config.ValkyrieOptions;
import com.valkyrie.client.config.ValkyrieOptionsManager;
import com.valkyrie.client.ValkyrieKeyMappings;
import com.valkyrie.client.gui.notify.Notifications;
import com.valkyrie.client.gui.theme.Animated;
import com.valkyrie.client.gui.theme.Easing;
import com.valkyrie.client.gui.theme.GuiAsset;
import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.module.Category;
import com.valkyrie.client.module.Module;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.Setting;
import com.valkyrie.client.module.impl.AppearanceModule;
import com.valkyrie.client.render.ValkyrieFonts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

/**
 * Основной ClickGUI: акриловые панели поверх размытого кадра.
 * <p>
 * Колонка соответствует {@link Category}, строка — модулю из
 * {@link ModuleRegistry}. Левый клик переключает модуль, правый раскрывает его
 * настройки прямо в колонке: отдельное окно на каждую функцию не влезло бы на
 * экран уже при десятке модулей.
 */
public final class ValkyrieGuiScreen extends Screen {
    private static final int PANEL_WIDTH = 142;
    private static final int HEADER_HEIGHT = 30;
    private static final int ROW_HEIGHT = 22;
    private static final int SETTING_HEIGHT = 18;
    private static final int SLIDER_HEIGHT = 26;
    private static final int MAX_BODY_HEIGHT = 214;
    private static final int PANEL_ELEVATION = 10;
    /** Отступ строки настройки от левого края — визуальная вложенность. */
    private static final int SETTING_INDENT = 14;
    private static final int SPINE_WIDTH = 25;
    private static final int SPINE_HEIGHT = 58;
    private static final int SPINE_GAP = 5;
    private static final float FOLD_DURATION = 0.72f;

    /** Высота маскота относительно высоты экрана. */
    private static final float MASCOT_HEIGHT_FACTOR = 0.62f;

    private final List<Panel> panels = new ArrayList<>();
    private final Animated doneHover = Animated.instant(0.0f);
    private final Animated mascotFade = Animated.slow(0.0f);

    private long openedAtNs;
    private long closingStartedAtNs;
    private float openProgress;
    private float closeFrom = 1.0f;
    private boolean built;
    private boolean closing;
    private boolean panelsCollapsed;
    private boolean foldInitialized;
    private long foldStartedAtNs;
    private float foldProgress;
    private float foldFrom;

    private Panel draggingPanel;
    private SettingRow activeSlider;
    private float dragOffsetX;
    private float dragOffsetY;
    private int previewMouseX;
    private int previewMouseY;
    private long previewMovedAtNs;

    private int doneX;
    private int doneY;
    private int doneW;
    private int doneH;
    private int foldX;
    private int foldY;
    private int foldW;
    private int foldH;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;

    public ValkyrieGuiScreen() {
        super(Component.literal("Valkyrie"));
    }

    @Override
    protected void init() {
        if (!built) {
            buildPanels();
            built = true;
        }
        if (openedAtNs == 0L) {
            openedAtNs = System.nanoTime();
            ValkyrieUiFeedback.open();
        }
        layoutPanels();
        if (!foldInitialized) {
            panelsCollapsed = ValkyrieOptionsManager.OPTIONS.clickGuiCollapsed;
            foldProgress = panelsCollapsed ? 1.0f : 0.0f;
            foldFrom = foldProgress;
            foldInitialized = true;
        }
    }

    /** Колонки строятся обходом реестра — новый модуль появляется здесь сам. */
    private void buildPanels() {
        panels.clear();
        for (Category category : Category.values()) {
            List<Module> modules = ModuleRegistry.byCategory(category);
            if (modules.isEmpty()) {
                continue;
            }
            Panel panel = new Panel(category, panels.size());
            for (Module module : modules) {
                panel.add(new ModuleRow(module));
            }
            panels.add(panel);
        }
    }

    /**
     * Раскладка по умолчанию: колонки в ряд, а если ряд не влезает в ширину окна —
     * переносом в сетку. Без переноса на малом разрешении все колонки упирались бы
     * в {@link #clampPanel} и слипались в одну стопку у правого края.
     */
    private void layoutPanels() {
        int step = PANEL_WIDTH + Theme.GAP_PANEL;
        int usable = Math.max(PANEL_WIDTH, this.width - 24);
        int columns = Mth.clamp((usable + Theme.GAP_PANEL) / step, 1, panels.size());
        int rows = (panels.size() + columns - 1) / columns;

        int rowStep = MAX_BODY_HEIGHT / 2 + HEADER_HEIGHT + Theme.GAP_PANEL;
        float top = rows > 1
            ? Math.max(46.0f, (this.height - rows * rowStep) / 2.0f)
            : Math.max(46.0f, this.height * 0.20f);

        for (int i = 0; i < panels.size(); i++) {
            Panel panel = panels.get(i);
            if (!panel.positioned) {
                int savedX = savedX(panel.category);
                int savedY = savedY(panel.category);
                int row = i / columns;
                int column = i % columns;
                // Последний ряд может быть неполным — центрируем его отдельно.
                int inRow = Math.min(columns, panels.size() - row * columns);
                float rowStartX = (this.width - (inRow * PANEL_WIDTH + (inRow - 1) * Theme.GAP_PANEL)) / 2.0f;

                panel.x = savedX != ValkyrieOptions.UNSET ? savedX : rowStartX + column * step;
                panel.y = savedY != ValkyrieOptions.UNSET ? savedY : top + row * rowStep;
                panel.positioned = true;
            }
            clampPanel(panel);
        }
    }

    // ─── Отрисовка ───────────────────────────────────────────────────────────

    /**
     * Вызывается движком из {@code Screen.renderWithTooltipAndSubtitles} в собственном
     * страту, до {@link #render}. Повторно вызывать вручную нельзя: {@code GuiRenderState}
     * допускает лишь одно размытие за кадр и падает с «Can only blur once per frame».
     */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateOpenProgress();
        float backgroundAlpha = closing ? Easing.smoothStep(openProgress) : Easing.outCubic(openProgress);
        // Размытие применяется ко всему, что нарисовано раньше, — это и есть акрил.
        guiGraphics.blurBeforeThisStratum();
        // Лёгкая светлая вуаль поверх размытия повышает контраст текста.
        guiGraphics.fillGradient(0, 0, this.width, this.height, Theme.fade(Theme.SCRIM_TOP(), backgroundAlpha), Theme.fade(Theme.SCRIM_BOTTOM(), backgroundAlpha));
        int accent = Theme.accent();
        guiGraphics.fillGradient(0, 0, this.width, this.height, Theme.alpha(accent, 0.05f * backgroundAlpha), 0x00000000);
        // Лепестки сакуры поверх вуали, но до панелей: лёгкий ветер за акрилом.
        com.valkyrie.client.gui.particle.SakuraParticles.render(guiGraphics, partialTick, 0.85f * backgroundAlpha);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        ThunderRender.beginFrame();
        updateOpenProgress();
        if (closing && openProgress <= 0.0f) {
            finishClose();
            return;
        }

        float alpha = closing ? Easing.smoothStep(openProgress) : Easing.outCubic(openProgress);
        float motion = closing ? Easing.inOutCubic(openProgress) : Easing.outQuint(openProgress);
        float rise = (1.0f - motion) * (closing ? 18.0f : 20.0f);

        renderMascot(guiGraphics, alpha);
        renderHeaderBadge(guiGraphics, alpha);
        renderPlayerPreview(guiGraphics, mouseX, mouseY, alpha);

        Matrix3x2fStack pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate(0.0f, rise);

        float fold = updateFoldProgress();
        float expandedAlpha = 1.0f - Easing.smoothStep(Mth.clamp(fold / 0.82f, 0.0f, 1.0f));
        for (Panel panel : panels) {
            if (expandedAlpha > 0.01f) {
                panel.render(guiGraphics, this.font, mouseX, mouseY, alpha * expandedAlpha);
            }
            if (fold > 0.01f && fold < 0.99f) {
                panel.renderFoldShell(guiGraphics, fold, alpha);
            }
        }

        pose.popMatrix();
        if (fold > 0.48f) {
            float spineAlpha = Easing.smoothStep((fold - 0.48f) / 0.52f);
            renderSpines(guiGraphics, mouseX, mouseY, alpha * spineAlpha);
        }

        renderFooter(guiGraphics, mouseX, mouseY, alpha);
        com.valkyrie.client.gui.notify.ValkyrieQuotes.render(guiGraphics, this.font, ThunderRender.frameSeconds());
        Notifications.render(guiGraphics, this.font);
    }

    private void updateOpenProgress() {
        boolean reducedMotion = ModuleRegistry.get(AppearanceModule.class).reducedMotion.value();
        if (closing) {
            float elapsed = (System.nanoTime() - closingStartedAtNs) / 1_000_000_000.0f;
            float duration = reducedMotion ? 0.18f : Theme.CLOSE_DURATION;
            openProgress = closeFrom * (1.0f - Mth.clamp(elapsed / duration, 0.0f, 1.0f));
            return;
        }
        float elapsed = (System.nanoTime() - openedAtNs) / 1_000_000_000.0f;
        float duration = reducedMotion ? 0.10f : Theme.OPEN_DURATION;
        openProgress = Mth.clamp(elapsed / duration, 0.0f, 1.0f);
    }

    private float updateFoldProgress() {
        float target = panelsCollapsed ? 1.0f : 0.0f;
        if (foldStartedAtNs == 0L) return foldProgress;
        float elapsed = (System.nanoTime() - foldStartedAtNs) / 1_000_000_000.0f;
        boolean reduced = ModuleRegistry.get(AppearanceModule.class).reducedMotion.value();
        float distance = Math.max(0.15f, Math.abs(target - foldFrom));
        float duration = (reduced ? 0.24f : FOLD_DURATION) * distance;
        float t = Mth.clamp(elapsed / duration, 0.0f, 1.0f);
        foldProgress = Mth.lerp(Easing.smoothStep(t), foldFrom, target);
        if (t >= 1.0f) {
            foldProgress = target;
            foldStartedAtNs = 0L;
        }
        return foldProgress;
    }

    private boolean foldActive() {
        return foldStartedAtNs != 0L;
    }

    /** Ванильная модель игрока из инвентаря: тело и голова следят за курсором. */
    private void renderPlayerPreview(GuiGraphics guiGraphics, int mouseX, int mouseY, float alpha) {
        previewW = 0;
        previewH = 0;
        AppearanceModule appearance = ModuleRegistry.get(AppearanceModule.class);
        if (!appearance.playerPreview.value() || this.minecraft == null || this.minecraft.player == null) {
            return;
        }

        int panelW = 86;
        int panelH = 116;
        int panelX = 18;
        int panelY = this.height - panelH - 24;
        previewX = panelX;
        previewY = panelY;
        previewW = panelW;
        previewH = panelH;
        if (panelY < 54) {
            return;
        }

        if (previewMovedAtNs == 0L) {
            previewMouseX = mouseX;
            previewMouseY = mouseY;
            previewMovedAtNs = System.nanoTime();
        } else if (Math.abs(mouseX - previewMouseX) + Math.abs(mouseY - previewMouseY) > 1) {
            previewMouseX = mouseX;
            previewMouseY = mouseY;
            previewMovedAtNs = System.nanoTime();
        }

        float lookX = mouseX;
        float lookY = mouseY;
        if (appearance.companion.is("Context") && !panelsCollapsed && !foldActive()) {
            for (Panel panel : panels) {
                if (panel.hovered(mouseX, mouseY)) {
                    lookX = panel.x + PANEL_WIDTH * 0.5f;
                    lookY = panel.y + HEADER_HEIGHT * 0.5f;
                    if (panel.category == Category.COMBAT) {
                        lookY -= 18.0f;
                    } else if (panel.category == Category.VISUALS) {
                        lookY += 8.0f;
                    }
                    break;
                }
            }
        }

        boolean idle = System.nanoTime() - previewMovedAtNs > 3_500_000_000L;
        if (idle && appearance.idleReactions.value()) {
            float time = System.nanoTime() / 1_000_000_000.0f;
            lookX = panelX + panelW * 0.5f + Mth.sin(time * 0.48f) * 34.0f;
            lookY = panelY + 28.0f + Mth.sin(time * 0.31f) * 10.0f;
        }
        float reactionMotion = appearance.reducedMotion.value() ? 0.30f : 1.0f;
        lookX += CompanionReactions.lookX() * reactionMotion;
        lookY += CompanionReactions.lookY() * reactionMotion;

        ThunderRender.drawFloatingPanel(
            guiGraphics,
            panelX,
            panelY,
            panelW,
            panelH,
            Theme.RADIUS_PANEL,
            Theme.SURFACE(),
            5,
            alpha * 0.92f
        );
        ThunderRender.drawAccentBar(guiGraphics, panelX + 22, panelY + panelH - 1, panelW - 44, 1, Theme.accent(), alpha * 0.34f);

        ValkyrieFonts.drawCentered(
            guiGraphics,
            this.font,
            this.minecraft.player.getName().getString(),
            panelX + panelW / 2,
            panelY + 8,
            Theme.alpha(Theme.TEXT_SECONDARY(), alpha * 0.80f)
        );

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(0.0f, CompanionReactions.bob() * reactionMotion);
        InventoryScreen.renderEntityInInventoryFollowsMouse(
            guiGraphics,
            panelX + 7,
            panelY + 20,
            panelX + panelW - 7,
            panelY + panelH - 8,
            38,
            0.0625f,
            lookX,
            lookY,
            this.minecraft.player
        );
        guiGraphics.pose().popMatrix();
    }

    /** Опциональный маскот в левом нижнем углу — рисуется, только если PNG добавлен. */
    private void renderMascot(GuiGraphics guiGraphics, float alpha) {
        if (!GuiAsset.MASCOT.exists()) {
            return;
        }
        float fade = mascotFade.update(alpha);
        float height = this.height * MASCOT_HEIGHT_FACTOR;
        float slide = (1.0f - Easing.outQuint(openProgress)) * 26.0f;
        GuiAsset.MASCOT.drawByHeight(guiGraphics, -12.0f - slide, this.height + 1.0f, height, fade * 0.96f);
    }

    private void renderHeaderBadge(GuiGraphics guiGraphics, float alpha) {
        int accent = Theme.accent();
        String title = "Valkyrie";
        String hint = panelsCollapsed ? "Click a spine to restore" : "Right click a module for settings";

        int titleWidth = ValkyrieFonts.titleWidth(this.font, title);
        int hintWidth = ValkyrieFonts.width(this.font, hint);
        int width = Math.max(titleWidth, hintWidth) + 72;
        int height = 40;
        int x = (this.width - width) / 2;
        int y = 14;

        ThunderRender.drawFloatingPanel(guiGraphics, x, y, width, height, Theme.RADIUS_CARD, Theme.SURFACE(), 8, alpha);

        int dotX = x + 14;
        int dotY = y + height / 2 - 4;
        ThunderRender.drawRoundRect(guiGraphics, dotX, dotY, 8, 8, 4, Theme.alpha(accent, alpha));
        ThunderRender.drawRoundRect(guiGraphics, dotX + 2, dotY + 2, 4, 4, 2, Theme.alpha(0xFFFFFFFF, 0.75f * alpha));

        ValkyrieFonts.drawTitle(guiGraphics, this.font, title, dotX + 16, y + 9, Theme.alpha(Theme.TEXT_PRIMARY(), alpha));
        ValkyrieFonts.draw(guiGraphics, this.font, hint, dotX + 16, y + 22, Theme.alpha(Theme.TEXT_TERTIARY(), 0.9f * alpha));
        foldW = 24;
        foldH = 24;
        foldX = x + width - foldW - 8;
        foldY = y + 8;
        ThunderRender.drawRoundRect(guiGraphics, foldX, foldY, foldW, foldH, 7, Theme.alpha(Theme.SURFACE_HOVER(), alpha));
        int arrow = Theme.alpha(accent, alpha);
        int cx = foldX + foldW / 2;
        guiGraphics.fill(cx - 4, foldY + 7, cx - 3, foldY + 17, arrow);
        guiGraphics.fill(cx + 3, foldY + 7, cx + 4, foldY + 17, arrow);
        guiGraphics.fill(cx - 1, foldY + 10, cx + 1, foldY + 14, arrow);
    }

    private void renderSpines(GuiGraphics guiGraphics, int mouseX, int mouseY, float alpha) {
        int x = this.width - SPINE_WIDTH - 6;
        int top = spineTop();
        int height = spineHeight();
        for (Panel panel : panels) {
            int y = top + panel.dockIndex * (height + SPINE_GAP);
            boolean hovered = ThunderRender.isHovered(mouseX, mouseY, x, y, SPINE_WIDTH, height);
            int fill = Theme.mix(Theme.SURFACE(), Theme.SURFACE_HOVER(), hovered ? 1.0f : 0.0f);
            ThunderRender.drawFloatingPanel(guiGraphics, x, y, SPINE_WIDTH, height, 8, fill, 6, alpha);
            ThunderRender.drawAccentBar(guiGraphics, x, y + 8, 2, Math.max(10, height - 16), Theme.accent(), alpha * 0.70f);
            ValkyrieFonts.drawCentered(guiGraphics, this.font, panel.category.title().substring(0, 1), x + SPINE_WIDTH / 2, y + 8, Theme.alpha(Theme.TEXT_PRIMARY(), alpha));
            ValkyrieFonts.drawCentered(guiGraphics, this.font, String.valueOf(panel.rows.size()), x + SPINE_WIDTH / 2, y + height - 17, Theme.alpha(Theme.TEXT_TERTIARY(), alpha * 0.80f));
        }
    }

    private int spineTop() {
        int total = panels.size() * spineHeight() + Math.max(0, panels.size() - 1) * SPINE_GAP;
        return Math.max(62, (this.height - total) / 2);
    }

    private int spineHeight() {
        int available = Math.max(34, this.height - 124 - Math.max(0, panels.size() - 1) * SPINE_GAP);
        return Mth.clamp(available / Math.max(1, panels.size()), 34, SPINE_HEIGHT);
    }

    private void renderFooter(GuiGraphics guiGraphics, int mouseX, int mouseY, float alpha) {
        doneW = 78;
        doneH = 26;
        doneX = this.width - doneW - 22;
        doneY = this.height - doneH - 20;

        boolean hovered = ThunderRender.isHovered(mouseX, mouseY, doneX, doneY, doneW, doneH);
        float hover = doneHover.update(hovered);
        int accent = Theme.accent();

        ThunderRender.drawElevation(guiGraphics, doneX, doneY, doneW, doneH, Theme.RADIUS_PILL, 8 + Math.round(hover * 4), alpha);
        int fill = Theme.mix(accent, ThunderRender.lighten(accent, 0.18f), hover);
        ThunderRender.drawRoundRect(guiGraphics, doneX, doneY, doneW, doneH, doneH / 2, Theme.alpha(fill, alpha));
        ThunderRender.drawRoundRect(
            guiGraphics,
            doneX + doneH / 2,
            doneY,
            doneW - doneH,
            1,
            0,
            Theme.alpha(0xFFFFFFFF, (0.30f + hover * 0.25f) * alpha)
        );
        ValkyrieFonts.drawCentered(
            guiGraphics,
            this.font,
            "Done",
            doneX + doneW / 2,
            doneY + (doneH - ValkyrieFonts.lineHeight(this.font)) / 2,
            Theme.alpha(Theme.TEXT_ON_ACCENT(), alpha)
        );
    }

    // ─── Ввод ────────────────────────────────────────────────────────────────

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() || ValkyrieKeyMappings.OPEN_GUI.matches(event)
            || (this.minecraft != null && this.minecraft.options.keyInventory.matches(event))) {
            requestClose();
            return true;
        }
        if (closing) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) {
            return true;
        }
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int button = event.button();

        if (button == 0 && ThunderRender.isHovered(mouseX, mouseY, doneX, doneY, doneW, doneH)) {
            onClose();
            return true;
        }
        if (button == 0 && ThunderRender.isHovered(mouseX, mouseY, foldX, foldY, foldW, foldH)) {
            setPanelsCollapsed(!panelsCollapsed);
            return true;
        }
        if (button == 0 && previewW > 0 && ThunderRender.isHovered(mouseX, mouseY, previewX, previewY, previewW, previewH)) {
            CompanionReactions.greeting();
            com.valkyrie.client.gui.notify.ValkyrieQuotes.onCompanion();
            ValkyrieUiFeedback.click();
            return true;
        }
        if (foldActive()) {
            return true;
        }
        if (panelsCollapsed) {
            int spineX = this.width - SPINE_WIDTH - 6;
            int top = spineTop();
            int height = spineHeight();
            for (Panel panel : panels) {
                int spineY = top + panel.dockIndex * (height + SPINE_GAP);
                if (ThunderRender.isHovered(mouseX, mouseY, spineX, spineY, SPINE_WIDTH, height)) {
                    setPanelsCollapsed(false);
                    return true;
                }
            }
            return true;
        }

        // Новый клик отменяет захват прошлого слайдера: иначе перетаскивание
        // где-нибудь в другой колонке продолжало бы менять уже отпущенное значение.
        activeSlider = null;

        // Обратный обход: панели сверху списка перекрывают нижние.
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (button == 0 && panel.headerHovered(mouseX, mouseY)) {
                draggingPanel = panel;
                dragOffsetX = mouseX - panel.x;
                dragOffsetY = mouseY - panel.y;
                bringToFront(i);
                ValkyrieUiFeedback.click();
                return true;
            }
            if (panel.click(mouseX, mouseY, button)) {
                ValkyrieOptionsManager.save();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void bringToFront(int index) {
        Panel panel = panels.remove(index);
        panels.add(panel);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (closing || panelsCollapsed || foldActive()) {
            return true;
        }
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();

        if (draggingPanel != null) {
            draggingPanel.x = mouseX - dragOffsetX;
            draggingPanel.y = mouseY - dragOffsetY;
            clampPanel(draggingPanel);
            return true;
        }
        if (activeSlider != null) {
            activeSlider.updateSlider(mouseX);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingPanel != null || activeSlider != null) {
            if (activeSlider != null) {
                CompanionReactions.settingChanged();
            }
            draggingPanel = null;
            activeSlider = null;
            savePositions();
            ValkyrieOptionsManager.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (closing || panelsCollapsed || foldActive()) {
            return true;
        }
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (panel.hovered((int) mouseX, (int) mouseY)) {
                panel.scrollTo(panel.scrollTarget + (float) scrollY * 20.0f);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        requestClose();
    }

    public void requestClose() {
        if (closing) {
            return;
        }
        closeFrom = Math.max(0.05f, openProgress);
        closing = true;
        closingStartedAtNs = System.nanoTime();
        draggingPanel = null;
        activeSlider = null;
        ValkyrieUiFeedback.close();
    }

    private void setPanelsCollapsed(boolean collapsed) {
        panelsCollapsed = collapsed;
        foldFrom = foldProgress;
        foldStartedAtNs = System.nanoTime();
        draggingPanel = null;
        activeSlider = null;
        ValkyrieOptionsManager.OPTIONS.clickGuiCollapsed = collapsed;
        ValkyrieOptionsManager.save();
        CompanionReactions.settingChanged();
        ValkyrieUiFeedback.click();
    }

    private void finishClose() {
        savePositions();
        ValkyrieOptionsManager.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ─── Сохранение позиций ──────────────────────────────────────────────────

    private void savePositions() {
        ValkyrieOptions options = ValkyrieOptionsManager.OPTIONS;
        for (Panel panel : panels) {
            int x = Math.round(panel.x);
            int y = Math.round(panel.y);
            switch (panel.category) {
                case HUD -> {
                    options.panelHudX = x;
                    options.panelHudY = y;
                }
                case VISUALS -> {
                    options.panelVisualsX = x;
                    options.panelVisualsY = y;
                }
                case COMBAT -> {
                    options.panelCombatX = x;
                    options.panelCombatY = y;
                }
                case CLIENT -> {
                    options.panelClientX = x;
                    options.panelClientY = y;
                }
            }
        }
    }

    private int savedX(Category category) {
        ValkyrieOptions options = ValkyrieOptionsManager.OPTIONS;
        return switch (category) {
            case HUD -> options.panelHudX;
            case VISUALS -> options.panelVisualsX;
            case COMBAT -> options.panelCombatX;
            case CLIENT -> options.panelClientX;
        };
    }

    private int savedY(Category category) {
        ValkyrieOptions options = ValkyrieOptionsManager.OPTIONS;
        return switch (category) {
            case HUD -> options.panelHudY;
            case VISUALS -> options.panelVisualsY;
            case COMBAT -> options.panelCombatY;
            case CLIENT -> options.panelClientY;
        };
    }

    private void clampPanel(Panel panel) {
        panel.x = Mth.clamp(panel.x, 6.0f, Math.max(6.0f, this.width - PANEL_WIDTH - 6.0f));
        panel.y = Mth.clamp(panel.y, 8.0f, Math.max(8.0f, this.height - HEADER_HEIGHT - 60.0f));
    }

    // ─── Колонка ─────────────────────────────────────────────────────────────

    private final class Panel {
        private final Category category;
        private final int dockIndex;
        private final List<ModuleRow> rows = new ArrayList<>();
        private final Animated scroll = Animated.normal(0.0f);

        private float x;
        private float y;
        private float scrollTarget;
        private boolean positioned;

        private Panel(Category category, int dockIndex) {
            this.category = category;
            this.dockIndex = dockIndex;
        }

        private void add(ModuleRow row) {
            rows.add(row);
        }

        private void scrollTo(float target) {
            scrollTarget = Mth.clamp(target, -maxScroll(), 0.0f);
        }

        private void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, float alpha) {
            float offset = scroll.update(scrollTarget);
            int ix = Math.round(x);
            int iy = Math.round(y);
            int bodyHeight = bodyHeight();
            int totalHeight = HEADER_HEIGHT + bodyHeight;

            ThunderRender.drawFloatingPanel(
                guiGraphics,
                ix,
                iy,
                PANEL_WIDTH,
                totalHeight,
                Theme.RADIUS_PANEL,
                Theme.SURFACE(),
                PANEL_ELEVATION,
                alpha
            );

            int accent = Theme.accent();
            ThunderRender.drawRoundRect(guiGraphics, ix + Theme.PAD_PANEL, iy + 11, 3, 12, 1, Theme.alpha(accent, alpha));
            ValkyrieFonts.drawTitle(
                guiGraphics,
                font,
                category.title(),
                ix + Theme.PAD_PANEL + 9,
                iy + 8,
                Theme.alpha(Theme.TEXT_PRIMARY(), alpha)
            );
            ValkyrieFonts.draw(
                guiGraphics,
                font,
                category.subtitle(),
                ix + Theme.PAD_PANEL + 9,
                iy + 19,
                Theme.alpha(Theme.TEXT_TERTIARY(), 0.92f * alpha)
            );
            ThunderRender.drawRoundRect(
                guiGraphics,
                ix + Theme.PAD_PANEL,
                iy + HEADER_HEIGHT - 1,
                PANEL_WIDTH - Theme.PAD_PANEL * 2,
                1,
                0,
                Theme.alpha(Theme.BORDER(), 0.36f * alpha)
            );

            int clipTop = iy + HEADER_HEIGHT + 3;
            int clipHeight = bodyHeight - 7;
            guiGraphics.enableScissor(ix + 3, clipTop, ix + PANEL_WIDTH - 3, clipTop + clipHeight);

            float rowY = clipTop + offset;
            int index = 0;
            for (ModuleRow row : rows) {
                // Строки проявляются каскадом сверху вниз.
                float delay = index * 0.05f;
                float rowAlpha = Mth.clamp((openProgress - delay) / 0.35f, 0.0f, 1.0f) * alpha;
                rowY = row.layoutAndRender(
                    guiGraphics, font, ix + Theme.PAD_PANEL - 4, rowY,
                    PANEL_WIDTH - (Theme.PAD_PANEL - 4) * 2,
                    clipTop, clipHeight, mouseX, mouseY, rowAlpha
                );
                rowY += Theme.GAP_ROW;
                index++;
            }
            guiGraphics.disableScissor();

            renderScrollbar(guiGraphics, ix, clipTop, clipHeight, offset, alpha);
        }

        private void renderScrollbar(GuiGraphics guiGraphics, int ix, int clipTop, int clipHeight, float offset, float alpha) {
            float max = maxScroll();
            if (max <= 0.5f) {
                return;
            }
            int trackX = ix + PANEL_WIDTH - 5;
            float visibleRatio = clipHeight / (float) contentHeight();
            int thumbHeight = Math.max(18, Math.round(clipHeight * visibleRatio));
            float progress = -offset / max;
            int thumbY = clipTop + Math.round((clipHeight - thumbHeight) * progress);
            ThunderRender.drawRoundRect(guiGraphics, trackX, clipTop, 2, clipHeight, 1, Theme.alpha(Theme.BORDER(), 0.24f * alpha));
            ThunderRender.drawRoundRect(guiGraphics, trackX, thumbY, 2, thumbHeight, 1, Theme.alpha(Theme.accent(), 0.7f * alpha));
        }

        private void renderFoldShell(GuiGraphics guiGraphics, float fold, float alpha) {
            float stagger = dockIndex * 0.055f;
            float local = panelsCollapsed
                ? Mth.clamp((fold - stagger) / (1.0f - stagger), 0.0f, 1.0f)
                : Mth.clamp(fold / Math.max(0.25f, 1.0f - stagger), 0.0f, 1.0f);
            local = Easing.smoothStep(local);
            int targetX = ValkyrieGuiScreen.this.width - SPINE_WIDTH - 6;
            int targetY = spineTop() + dockIndex * (spineHeight() + SPINE_GAP);
            int sourceHeight = HEADER_HEIGHT + bodyHeight();
            int ix = Math.round(Mth.lerp(local, x, targetX));
            int iy = Math.round(Mth.lerp(local, y, targetY));
            int width = Math.max(SPINE_WIDTH, Math.round(Mth.lerp(local, PANEL_WIDTH, SPINE_WIDTH)));
            int height = Math.max(spineHeight(), Math.round(Mth.lerp(local, sourceHeight, spineHeight())));
            float shellAlpha = alpha * (0.90f + 0.10f * (float) Math.sin(local * Math.PI));
            ThunderRender.drawFloatingPanel(guiGraphics, ix, iy, width, height, Math.min(Theme.RADIUS_PANEL, Math.max(7, width / 4)), Theme.SURFACE(), 7, shellAlpha);
            int accentWidth = Math.max(2, Math.round(Mth.lerp(local, 3.0f, 2.0f)));
            ThunderRender.drawAccentBar(guiGraphics, ix, iy + Math.min(11, height / 4), accentWidth, Math.max(8, height - Math.min(22, height / 2)), Theme.accent(), shellAlpha * 0.72f);
            if (width > 62) {
                ValkyrieFonts.drawTitle(guiGraphics, ValkyrieGuiScreen.this.font, category.title(), ix + 14, iy + 9, Theme.alpha(Theme.TEXT_PRIMARY(), shellAlpha * (1.0f - local)));
            }
        }

        private int bodyHeight() {
            return Math.min(MAX_BODY_HEIGHT, contentHeight() + 8);
        }

        private boolean headerHovered(int mouseX, int mouseY) {
            return ThunderRender.isHovered(mouseX, mouseY, x, y, PANEL_WIDTH, HEADER_HEIGHT);
        }

        private boolean hovered(int mouseX, int mouseY) {
            return ThunderRender.isHovered(mouseX, mouseY, x, y, PANEL_WIDTH, HEADER_HEIGHT + bodyHeight());
        }

        private boolean click(int mouseX, int mouseY, int button) {
            // Клики за пределами тела панели игнорируются: строки обрезаны скиссором.
            int clipTop = Math.round(y) + HEADER_HEIGHT;
            int clipBottom = clipTop + bodyHeight();
            if (mouseY < clipTop || mouseY > clipBottom) {
                return false;
            }
            // Строки за пределами тела обрезаны скиссором, но их координаты живые:
            // без этой проверки кликом можно попасть по невидимой строке.
            if (mouseY < clipTop + 3 || mouseY > clipTop + bodyHeight() - 4) {
                return false;
            }
            for (ModuleRow row : rows) {
                if (row.click(mouseX, mouseY, button)) {
                    // Раскрытие меняет высоту колонки: прокрутка могла стать невалидной.
                    scrollTo(scrollTarget);
                    return true;
                }
            }
            return false;
        }

        private int contentHeight() {
            int height = 0;
            for (int i = 0; i < rows.size(); i++) {
                height += rows.get(i).totalHeight();
                if (i + 1 < rows.size()) {
                    height += Theme.GAP_ROW;
                }
            }
            return height;
        }

        private float maxScroll() {
            return Math.max(0.0f, contentHeight() - (bodyHeight() - 7));
        }
    }

    // ─── Строка модуля ───────────────────────────────────────────────────────

    private final class ModuleRow {
        private final Module module;
        private final List<SettingRow> settingRows = new ArrayList<>();

        private final Animated hover = Animated.instant(0.0f);
        private final Animated enabled = Animated.normal(0.0f);
        /** Плавное раскрытие: список настроек выезжает, а не появляется рывком. */
        private final Animated expand = Animated.normal(0.0f);

        private boolean hoveredLastFrame;
        private int x;
        private int y;
        private int width;

        private ModuleRow(Module module) {
            this.module = module;
            for (Setting<?> setting : module.settings()) {
                settingRows.add(new SettingRow(module, setting));
            }
            if (module.isEnabled()) {
                enabled.snap(1.0f);
            }
            if (module.isExpanded()) {
                expand.snap(1.0f);
            }
        }

        /** Высота одной шапки модуля без раскрытых настроек. */
        private int headerHeight() {
            return ROW_HEIGHT;
        }

        /** Полная высота с учётом текущей фазы раскрытия. */
        private int totalHeight() {
            return headerHeight() + Math.round(settingsHeight() * expand.value());
        }

        private int settingsHeight() {
            int height = 0;
            for (SettingRow row : settingRows) {
                if (row.visible()) {
                    height += row.height() + 2;
                }
            }
            return height;
        }

        /**
         * Размещает и рисует строку вместе с раскрытыми настройками.
         *
         * @return координата Y сразу под строкой
         */
        private float layoutAndRender(
            GuiGraphics guiGraphics,
            Font font,
            int rowX,
            float rowY,
            int rowWidth,
            int clipTop,
            int clipHeight,
            int mouseX,
            int mouseY,
            float alpha
        ) {
            this.x = rowX;
            this.y = Math.round(rowY);
            this.width = rowWidth;

            float amount = expand.update(module.isExpanded());
            int header = headerHeight();

            if (rowY + header >= clipTop && rowY <= clipTop + clipHeight) {
                renderHeader(guiGraphics, font, mouseX, mouseY, alpha, amount);
            }

            float cursorY = rowY + header;
            if (amount <= 0.01f) {
                // Свёрнуто: настройки не занимают места и не ловят клики.
                for (SettingRow row : settingRows) {
                    row.setBounds(rowX, -1000, rowWidth);
                }
                return cursorY;
            }

            // Настройки рисуются в полную высоту, а обрезает их анимация раскрытия
            // через scissor — иначе строки «сплющивались» бы по вертикали.
            int settingsTop = Math.round(cursorY);
            int visibleHeight = Math.round(settingsHeight() * amount);
            // Пересечение области настроек с телом панели. Если блок целиком уехал
            // за пределы прокрутки, границы схлопываются — скиссор с инвертированным
            // прямоугольником рисовал бы мусор на весь экран.
            int scissorTop = Math.max(clipTop, settingsTop);
            int scissorBottom = Math.min(clipTop + clipHeight, settingsTop + visibleHeight);
            if (scissorBottom > scissorTop) {
                guiGraphics.enableScissor(rowX - 4, scissorTop, rowX + rowWidth + 4, scissorBottom);
                float settingY = cursorY;
                for (SettingRow row : settingRows) {
                    if (!row.visible()) {
                        row.setBounds(rowX, -1000, rowWidth);
                        continue;
                    }
                    row.setBounds(rowX + SETTING_INDENT, Math.round(settingY), rowWidth - SETTING_INDENT);
                    row.render(guiGraphics, font, mouseX, mouseY, alpha * amount);
                    settingY += row.height() + 2;
                }
                guiGraphics.disableScissor();
            }

            return cursorY + visibleHeight;
        }

        private void renderHeader(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, float alpha, float expandAmount) {
            boolean isHovered = ThunderRender.isHovered(mouseX, mouseY, x, y, width, headerHeight());
            if (isHovered && !hoveredLastFrame) {
                ValkyrieUiFeedback.hover();
            }
            hoveredLastFrame = isHovered;

            float hoverAmount = hover.update(isHovered);
            float activeAmount = enabled.update(module.isEnabled());

            int accent = Theme.accent();
            int background = Theme.mix(Theme.SURFACE_MUTED(), Theme.SURFACE_HOVER(), hoverAmount);
            background = Theme.mix(background, Theme.alpha(accent, 0.10f), activeAmount);
            ThunderRender.drawRoundRect(guiGraphics, x, y, width, headerHeight(), Theme.RADIUS_ROW, Theme.fade(background, alpha));

            float markerAmount = Math.max(hoverAmount * 0.55f, activeAmount);
            if (markerAmount > 0.02f) {
                int markerHeight = Math.max(4, Math.round((headerHeight() - 10) * markerAmount));
                int markerY = y + (headerHeight() - markerHeight) / 2;
                ThunderRender.drawRoundRect(
                    guiGraphics, x + 3, markerY, 2, markerHeight, 1,
                    Theme.alpha(accent, (0.25f + activeAmount * 0.45f) * alpha)
                );
            }

            int textX = x + 11;
            int titleY = y + 4;
            int titleColor = Theme.mix(Theme.TEXT_SECONDARY(), Theme.TEXT_PRIMARY(), Math.max(hoverAmount, activeAmount));
            ValkyrieFonts.draw(guiGraphics, font, module.name(), textX, titleY, Theme.alpha(titleColor, alpha));
            ValkyrieFonts.draw(
                guiGraphics, font, module.description(), textX, titleY + 10,
                Theme.alpha(Theme.TEXT_TERTIARY(), 0.9f * alpha)
            );

            int controlRight = x + width - 9;
            if (module.hasToggle()) {
                controlRight = renderToggle(guiGraphics, controlRight, activeAmount, alpha);
            } else if (module.isAction()) {
                controlRight = renderActionPill(guiGraphics, font, controlRight, hoverAmount, alpha);
            }

            // Шеврон показывает, что у строки есть скрытые настройки.
            if (!settingRows.isEmpty()) {
                renderChevron(guiGraphics, controlRight - 8, y + headerHeight() / 2, expandAmount, alpha);
            }
        }

        private int renderToggle(GuiGraphics guiGraphics, int right, float amount, float alpha) {
            int trackW = 24;
            int trackH = 13;
            int trackX = right - trackW;
            int trackY = y + (headerHeight() - trackH) / 2;

            int track = Theme.mix(Theme.TRACK_OFF(), Theme.accent(), amount);
            ThunderRender.drawRoundRect(guiGraphics, trackX, trackY, trackW, trackH, trackH / 2, Theme.alpha(track, alpha));

            int knob = 9;
            int travel = trackW - knob - 4;
            int knobX = trackX + 2 + Math.round(travel * Easing.outCubic(amount));
            int knobY = trackY + 2;
            ThunderRender.drawElevation(guiGraphics, knobX, knobY, knob, knob, knob / 2, 3, alpha * 0.8f);
            ThunderRender.drawRoundRect(guiGraphics, knobX, knobY, knob, knob, knob / 2, Theme.alpha(Theme.KNOB(), alpha));
            return trackX - 6;
        }

        private int renderActionPill(GuiGraphics guiGraphics, Font font, int right, float hoverAmount, float alpha) {
            String label = module.actionLabel();
            int labelWidth = ValkyrieFonts.width(font, label);
            int pillW = labelWidth + 14;
            int pillH = 14;
            int pillX = right - pillW;
            int pillY = y + (headerHeight() - pillH) / 2;
            int accent = Theme.accent();
            int fill = Theme.mix(Theme.alpha(accent, 0.14f), Theme.alpha(accent, 0.30f), hoverAmount);
            ThunderRender.drawRoundRect(guiGraphics, pillX, pillY, pillW, pillH, pillH / 2, Theme.fade(fill, alpha));
            ValkyrieFonts.drawCentered(
                guiGraphics, font, label, pillX + pillW / 2,
                pillY + (pillH - ValkyrieFonts.lineHeight(font)) / 2,
                Theme.alpha(Theme.mix(Theme.TEXT_SECONDARY(), accent, 0.85f), alpha)
            );
            return pillX - 6;
        }

        /** Треугольник из сужающихся полосок: поворачивается вниз при раскрытии. */
        private void renderChevron(GuiGraphics guiGraphics, int centerX, int centerY, float amount, float alpha) {
            int color = Theme.alpha(Theme.TEXT_TERTIARY(), (0.55f + amount * 0.35f) * alpha);
            // Свёрнутый — стрелка вправо, раскрытый — вниз. Промежуток не нужен:
            // переход быстрый, а честный поворот потребовал бы матрицы на 6 пикселей.
            if (amount < 0.5f) {
                for (int i = 0; i < 3; i++) {
                    guiGraphics.fill(centerX + i - 1, centerY - 2 + i, centerX + i, centerY + 3 - i, color);
                }
            } else {
                for (int i = 0; i < 3; i++) {
                    guiGraphics.fill(centerX - 2 + i, centerY + i - 1, centerX + 3 - i, centerY + i, color);
                }
            }
        }

        private boolean click(int mouseX, int mouseY, int button) {
            if (ThunderRender.isHovered(mouseX, mouseY, x, y, width, headerHeight())) {
                return clickHeader(button);
            }
            if (!module.isExpanded()) {
                return false;
            }
            for (SettingRow row : settingRows) {
                if (row.visible() && row.isHovered(mouseX, mouseY)) {
                    return row.click(mouseX, button);
                }
            }
            return false;
        }

        private boolean clickHeader(int button) {
            if (button == 1) {
                // ПКМ — только раскрытие: у модулей без настроек реакции быть не должно.
                if (settingRows.isEmpty()) {
                    return false;
                }
                module.setExpanded(!module.isExpanded());
                ValkyrieUiFeedback.click();
                return true;
            }
            if (button != 0) {
                return false;
            }
            if (module.isAction()) {
                ValkyrieUiFeedback.click();
                module.runAction();
                CompanionReactions.settingChanged();
                return true;
            }
            if (module.hasToggle()) {
                module.toggle();
                if (module.isEnabled()) {
                    ValkyrieUiFeedback.toggleOn();
                } else {
                    ValkyrieUiFeedback.toggleOff();
                }
                return true;
            }
            // Строка без тумблера и без действия — раскрываем по левому клику,
            // иначе до настроек не добраться очевидным способом.
            if (!settingRows.isEmpty()) {
                module.setExpanded(!module.isExpanded());
                ValkyrieUiFeedback.click();
                return true;
            }
            return false;
        }
    }

    // ─── Строка настройки ────────────────────────────────────────────────────

    private final class SettingRow {
        private final Module module;
        private final Setting<?> setting;
        private final Animated hover = Animated.instant(0.0f);

        private int x;
        private int y;
        private int width;

        private SettingRow(Module module, Setting<?> setting) {
            this.module = module;
            this.setting = setting;
        }

        private boolean visible() {
            return setting.visible();
        }

        private int height() {
            return setting instanceof Setting.Slider ? SLIDER_HEIGHT : SETTING_HEIGHT;
        }

        private void setBounds(int x, int y, int width) {
            this.x = x;
            this.y = y;
            this.width = width;
        }

        private void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY, float alpha) {
            boolean isHovered = isHovered(mouseX, mouseY);
            float hoverAmount = hover.update(isHovered);

            int background = Theme.alpha(Theme.SURFACE_RAISED(), 0.18f + hoverAmount * 0.18f);
            ThunderRender.drawRoundRect(guiGraphics, x, y, width, height(), Theme.RADIUS_ROW - 3, Theme.fade(background, alpha));

            int textColor = Theme.mix(Theme.TEXT_TERTIARY(), Theme.TEXT_SECONDARY(), hoverAmount);
            int labelY = setting instanceof Setting.Slider
                ? y + 3
                : y + (height() - ValkyrieFonts.lineHeight(font)) / 2;
            ValkyrieFonts.draw(guiGraphics, font, setting.name(), x + 8, labelY, Theme.alpha(textColor, alpha));

            if (setting instanceof Setting.Bool bool) {
                renderCheck(guiGraphics, bool, alpha);
            } else if (setting instanceof Setting.Slider slider) {
                renderSlider(guiGraphics, font, slider, hoverAmount, alpha);
            } else if (setting instanceof Setting.Color color) {
                renderColor(guiGraphics, color, hoverAmount, alpha);
            } else if (setting instanceof Setting.Mode mode) {
                renderMode(guiGraphics, font, mode, alpha);
            }
        }

        /** Компактная галочка вместо полноразмерного тумблера: строка всего 18px. */
        private void renderCheck(GuiGraphics guiGraphics, Setting.Bool bool, float alpha) {
            int size = 10;
            int boxX = x + width - size - 7;
            int boxY = y + (height() - size) / 2;
            int accent = Theme.accent();

            if (bool.value()) {
                ThunderRender.drawRoundRect(guiGraphics, boxX, boxY, size, size, 3, Theme.alpha(accent, alpha));
                // Галочка: две короткие диагонали из точек.
                int tick = Theme.alpha(Theme.TEXT_ON_ACCENT(), alpha);
                guiGraphics.fill(boxX + 2, boxY + 5, boxX + 3, boxY + 7, tick);
                guiGraphics.fill(boxX + 3, boxY + 6, boxX + 4, boxY + 8, tick);
                guiGraphics.fill(boxX + 4, boxY + 4, boxX + 5, boxY + 7, tick);
                guiGraphics.fill(boxX + 5, boxY + 3, boxX + 6, boxY + 6, tick);
                guiGraphics.fill(boxX + 6, boxY + 2, boxX + 8, boxY + 5, tick);
            } else {
                ThunderRender.drawRoundOutline(guiGraphics, boxX, boxY, size, size, 3, Theme.alpha(Theme.BORDER(), 0.9f * alpha));
            }
        }

        private void renderSlider(GuiGraphics guiGraphics, Font font, Setting.Slider slider, float hoverAmount, float alpha) {
            String label = slider.label();
            int labelWidth = ValkyrieFonts.width(font, label);
            ValkyrieFonts.draw(
                guiGraphics, font, label, x + width - labelWidth - 8, y + 3,
                Theme.alpha(Theme.TEXT_SECONDARY(), alpha)
            );

            int trackX = x + 8;
            int trackW = width - 16;
            int trackY = y + height() - 9;
            ThunderRender.drawRoundRect(guiGraphics, trackX, trackY, trackW, 3, 1, Theme.alpha(Theme.TRACK_OFF(), alpha));

            float percent = Mth.clamp((slider.value() - slider.min()) / (slider.max() - slider.min()), 0.0f, 1.0f);
            int filled = Math.max(2, Math.round(trackW * percent));
            ThunderRender.drawAccentBar(guiGraphics, trackX, trackY, filled, 3, Theme.accent(), alpha);

            int knobSize = 8 + Math.round(hoverAmount * 2);
            int knobX = trackX + filled - knobSize / 2;
            int knobY = trackY + 1 - knobSize / 2;
            ThunderRender.drawElevation(guiGraphics, knobX, knobY, knobSize, knobSize, knobSize / 2, 3, alpha * 0.9f);
            ThunderRender.drawRoundRect(guiGraphics, knobX, knobY, knobSize, knobSize, knobSize / 2, Theme.alpha(Theme.KNOB(), alpha));
        }

        private void renderColor(GuiGraphics guiGraphics, Setting.Color color, float hoverAmount, float alpha) {
            int size = 12 + Math.round(hoverAmount * 2);
            int swatchX = x + width - size - 7;
            int swatchY = y + (height() - size) / 2;
            ThunderRender.drawRoundRect(guiGraphics, swatchX, swatchY, size, size, 4, Theme.alpha(color.value(), alpha));
            ThunderRender.drawRoundOutline(guiGraphics, swatchX, swatchY, size, size, 4, Theme.alpha(Theme.KNOB(), 0.55f * alpha));
        }

        private void renderMode(GuiGraphics guiGraphics, Font font, Setting.Mode mode, float alpha) {
            String label = mode.value();
            int labelWidth = ValkyrieFonts.width(font, label);
            ValkyrieFonts.draw(
                guiGraphics, font, label, x + width - labelWidth - 8,
                y + (height() - ValkyrieFonts.lineHeight(font)) / 2,
                Theme.alpha(Theme.mix(Theme.TEXT_SECONDARY(), Theme.accent(), 0.7f), alpha)
            );
        }

        private boolean click(int mouseX, int button) {
            if (button != 0) {
                return false;
            }
            if (setting instanceof Setting.Bool bool) {
                boolean next = !bool.value();
                bool.set(next);
                if (next) {
                    ValkyrieUiFeedback.toggleOn();
                } else {
                    ValkyrieUiFeedback.toggleOff();
                }
                CompanionReactions.settingChanged();
                return true;
            }
            if (setting instanceof Setting.Slider) {
                ValkyrieUiFeedback.tick();
                updateSlider(mouseX);
                activeSlider = this;
                return true;
            }
            if (setting instanceof Setting.Color color) {
                ValkyrieUiFeedback.click();
                color.set(Theme.nextAccent(color.value()));
                Notifications.info(module.name(), setting.name().toLowerCase(java.util.Locale.ROOT) + " changed");
                CompanionReactions.settingChanged();
                return true;
            }
            if (setting instanceof Setting.Mode mode) {
                ValkyrieUiFeedback.click();
                mode.cycle();
                CompanionReactions.settingChanged();
                return true;
            }
            return false;
        }

        private void updateSlider(int mouseX) {
            if (!(setting instanceof Setting.Slider slider)) {
                return;
            }
            int trackX = x + 8;
            int trackW = width - 16;
            float percent = Mth.clamp((mouseX - trackX) / (float) trackW, 0.0f, 1.0f);
            slider.set(slider.min() + (slider.max() - slider.min()) * percent);
        }

        private boolean isHovered(int mouseX, int mouseY) {
            return ThunderRender.isHovered(mouseX, mouseY, x, y, width, height());
        }
    }
}
