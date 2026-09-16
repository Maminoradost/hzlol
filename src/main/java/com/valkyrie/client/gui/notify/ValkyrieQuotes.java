package com.valkyrie.client.gui.notify;

import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.gui.thunder.ThunderRender;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.AppearanceModule;
import com.valkyrie.client.module.impl.FocusModeModule;
import com.valkyrie.client.render.ValkyrieFonts;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * «Фразочки» — короткие атмосферные реплики клиента.
 * <p>
 * Три канала:
 * <ul>
 *   <li>Сменяющийся субтитр под логотипом главного меню — атмосфера без текста поверх боя.</li>
 *   <li>Фраза при входе в экран функций — одна реплика, исчезает через пару секунд.</li>
 *   <li>Реакции на события мира — цель в зоне ауры, попадание, убийство. отдельная очередь, снизу-центр.</li>
 * </ul>
 * <p>
 * Все тексты на русском и подобраны под «ветер/сакура»: спокойные, не агрессивные.
 * Вывод — синим акцентом на полу-прозрачной плашке снизу-центра экрана.
 */
public final class ValkyrieQuotes {
    private ValkyrieQuotes() {
    }

    /** Субтитры главного меню — сменяются медленно, по одной реплике. */
    private static final List<String> MENU_SUB = List.of(
        "ветер меняется",
        "лепестки помнят дорогу",
        "тишина перед ударом",
        "ближайшая цель — ближайшая",
        "аккуратность важнее скорости",
        "один удар — один шаг"
    );

    /** Фразы при входе в ClickGUI — одна за открытие, случайная. */
    private static final List<String> ON_OPEN_GUI = List.of(
        "настрой оружие по ветру",
        "спокойствие — твоя сила",
        "острые лепестки не шумят"
    );

    /** Реакции: цель в зоне ауры. */
    private static final List<String> QUOTE_TARGET = List.of(
        "цель в зоне",
        "вижу тебя",
        "лепестки ложатся рядом"
    );

    /** Реакции: попадание прошло. */
    private static final List<String> QUOTE_HIT = List.of(
        "попал",
        "точно в ветку",
        "удар засчитан"
    );

    /** Реакции: цель пала. */
    private static final List<String> QUOTE_KILL = List.of(
        "одним меньше",
        "лепесток упал",
        "отпустил его",
        "следующий"
    );
    private static final List<String> QUOTE_COMPANION = List.of(
        "я рядом",
        "ветер сегодня спокойный",
        "всё настроено чисто"
    );
    private static final List<String> QUOTE_HURT = List.of(
        "держи дистанцию",
        "не спеши",
        "ветер предупреждает"
    );
    private static final List<String> QUOTE_LOW_HEALTH = List.of(
        "пора отступить",
        "сохрани себя",
        "лепестки редеют"
    );

    /** Мировая фраза — нижний-центр, исчезает через 2.4с. */
    private static final float WORLD_LIFE = 2.4f;

    /** Текущая мировая фраза и её таймер живости (в секундах). */
    private static String worldQuote = "";
    private static float worldRemain = 0.0f;

    /** Субтитр меню — индекс вращается по таймеру. */
    private static int subIndex = 0;
    private static float subTimer = 0.0f;
    private static long lastTargetNs;
    private static long lastHitNs;
    private static long lastCompanionNs;
    private static long lastHurtNs;
    private static long lastLowHealthNs;

    /** Случайная фраза из списка — консистентный рандом без состояния. */
    private static String pick(List<String> list) {
        int i = (int) (Math.random() * list.size());
        return list.get(i);
    }

    /** Меню: вызвать раз в кадр с временем кадра. Обновляет субтитр. */
    public static void tickMenu(float frameSeconds) {
        if (!menuEnabled()) {
            return;
        }
        subTimer += frameSeconds;
        if (subTimer >= 6.5f) {
            subTimer = 0.0f;
            subIndex = (subIndex + 1) % MENU_SUB.size();
        }
    }

    /** Текущая строка субтитра — для отрисовки меню. */
    public static String menuSubtitle() {
        return menuEnabled() ? MENU_SUB.get(subIndex) : "";
    }

    /** Вызывается при открытии ClickGUI: показывает одну реплику-приветствие. */
    public static void onOpenGui() {
        if (menuEnabled()) {
            showWorld(pick(ON_OPEN_GUI));
        }
    }

    /** KillAura нашла свежую цель. */
    public static void onTarget() {
        long now = System.nanoTime();
        if (worldEnabled() && now - lastTargetNs >= 6_000_000_000L) {
            lastTargetNs = now;
            showWorld(pick(QUOTE_TARGET));
        }
    }

    /** KillAura нанесла удар. */
    public static void onHit() {
        long now = System.nanoTime();
        if (worldEnabled() && now - lastHitNs >= 9_000_000_000L) {
            lastHitNs = now;
            showWorld(pick(QUOTE_HIT));
        }
    }

    /** Цель пала. */
    public static void onKill() {
        if (worldEnabled()) {
            showWorld(pick(QUOTE_KILL));
        }
    }

    public static void onCompanion() {
        long now = System.nanoTime();
        if (menuEnabled() && now - lastCompanionNs >= 3_000_000_000L) {
            lastCompanionNs = now;
            showWorld(pick(QUOTE_COMPANION));
        }
    }

    public static void onHurt() {
        long now = System.nanoTime();
        if (worldEnabled() && now - lastHurtNs >= 10_000_000_000L) {
            lastHurtNs = now;
            showWorld(pick(QUOTE_HURT));
        }
    }

    public static void onLowHealth() {
        long now = System.nanoTime();
        if (worldEnabled() && now - lastLowHealthNs >= 25_000_000_000L) {
            lastLowHealthNs = now;
            showWorld(pick(QUOTE_LOW_HEALTH));
        }
    }

    private static void showWorld(String text) {
        // Без спама: новая реплика перебивает старую, но не стекуется.
        worldQuote = text;
        worldRemain = WORLD_LIFE;
    }

    /** Вызывать раз в кадр из {@code render} — обновляет таймер и рисует плашку. */
    public static void render(GuiGraphics gg, Font font, float frameSeconds) {
        if (Minecraft.getInstance().screen == null && ModuleRegistry.isEnabled(FocusModeModule.class)) {
            worldRemain = 0.0f;
            return;
        }
        if (!menuEnabled() && !worldEnabled()) {
            worldRemain = 0.0f;
            return;
        }
        if (worldRemain <= 0.0f) {
            return;
        }
        worldRemain -= frameSeconds;
        if (worldRemain < 0.0f) {
            worldRemain = 0.0f;
            return;
        }

        // Появление/исчезновение: 0..1 по первой половине, 1..0 по второй.
        float t = worldRemain / WORLD_LIFE;
        float alpha = t > 0.5f ? Mth.clamp((1.0f - t) * 4.0f, 0.0f, 1.0f) : Mth.clamp(t * 2.0f, 0.0f, 1.0f);

        int w = ValkyrieFonts.width(font, worldQuote);
        int lineHeight = ValkyrieFonts.lineHeight(font);
        int x = (gg.guiWidth() - w) / 2;
        int y = gg.guiHeight() - 34;

        // Плашка тем же акрилом, что панели и тосты: единый визуальный язык.
        int padX = 10;
        int padY = 5;
        ThunderRender.drawFloatingPanel(
            gg,
            x - padX,
            y - padY,
            w + padX * 2,
            lineHeight + padY * 2,
            Theme.RADIUS_PILL,
            Theme.HUD_SURFACE(),
            6,
            alpha * 0.9f
        );

        ValkyrieFonts.draw(gg, font, worldQuote, x, y, Theme.alpha(Theme.TEXT_SECONDARY(), alpha * 0.95f));
    }

    /** Таймер мировой фразы жив? — для решения о очистке. */
    public static boolean hasWorld() {
        return worldRemain > 0.0f;
    }

    /** Сброс при выходе из мира. */
    public static void reset() {
        worldQuote = "";
        worldRemain = 0.0f;
    }

    private static boolean menuEnabled() {
        return !ModuleRegistry.get(AppearanceModule.class).quotes.is("Off");
    }

    private static boolean worldEnabled() {
        return !ModuleRegistry.isEnabled(FocusModeModule.class)
            && ModuleRegistry.get(AppearanceModule.class).quotes.is("All");
    }
}
