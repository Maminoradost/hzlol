package com.valkyrie.client.render.font;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.awt.*;

/** Shared TTF renderers (Inter-like UI via bundled JetBrains Mono until Inter assets are added). */
public final class GfxFontRenderers {
    private static final Logger LOGGER = LogUtils.getLogger();

    static final String DEFAULT_PREBAKE = buildPrebake();

    private static GfxFontRenderer ui;
    private static GfxFontRenderer uiTitle;
    private static GfxFontRenderer uiSmall;
    private static boolean ready;

    private GfxFontRenderers() {
    }

    public static void init() {
        if (ready) {
            return;
        }
        try {
            Font regular = loadUiFont(Font.PLAIN);
            Font semibold = loadUiFont(Font.BOLD);
            ui = new GfxFontRenderer(semibold, 9.35f, 256, 2, DEFAULT_PREBAKE);
            uiSmall = new GfxFontRenderer(regular, 8.0f, 256, 2, DEFAULT_PREBAKE);
            uiTitle = new GfxFontRenderer(semibold, 11.8f, 256, 2, DEFAULT_PREBAKE);

            ui.ensureInitialized();
            uiTitle.ensureInitialized();
            uiSmall.ensureInitialized();
            ready = true;
            LOGGER.info("[Valkyrie] Gfx TTF font renderer initialized");
        } catch (Exception exception) {
            LOGGER.error("[Valkyrie] Gfx font init failed, vanilla fallback will be used", exception);
            ready = false;
        }
    }

    public static boolean isReady() {
        return ready;
    }

    /**
     * Освобождает динамические атласы всех рендереров (глифы вне пребейка,
     * набранные за сессию: чужие ники, незнакомые алфавиты). Пребейк-страницы
     * остаются, так что UI продолжает работать без перегенерации.
     */
    public static void releaseDynamicPages() {
        if (!ready) {
            return;
        }
        ui.releaseDynamicPages();
        uiTitle.releaseDynamicPages();
        uiSmall.releaseDynamicPages();
    }

    public static GfxFontRenderer ui() {
        return ui;
    }

    public static GfxFontRenderer uiTitle() {
        return uiTitle;
    }

    public static GfxFontRenderer uiSmall() {
        return uiSmall;
    }

    private static Font loadUiFont(int style) {
        if (hasFontAsset("nunito.ttf")) {
            return GfxFontRenderer.loadTrueType("nunito.ttf", style);
        }
        if (hasFontAsset("inter.ttf")) {
            return GfxFontRenderer.loadTrueType("inter.ttf", style);
        }
        return GfxFontRenderer.loadTrueType("jetbrains-mono.ttf", style);
    }

    private static boolean hasFontAsset(String fileName) {
        String path = "/assets/valkyrieclient/font/" + fileName;
        return GfxFontRenderers.class.getResource(path) != null;
    }

    private static String buildPrebake() {
        StringBuilder builder = new StringBuilder();
        for (int code = 32; code < 127; code++) {
            builder.append((char) code);
        }
        builder.append("АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ");
        builder.append("абвгдеёжзийклмнопрстуфхцчшщъыьэюя");
        builder.append("ОдиночнаяСетеваяНастройкиВыйтиигрыVisualClientValkyrie");
        builder.append("LMBRMBShift—togglemodulesettingscloseHUDKeystrokesMainMenuTracersTheme");
        return builder.toString();
    }
}
