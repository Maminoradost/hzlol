package com.valkyrie.client;

import com.valkyrie.client.config.ValkyrieOptionsManager;
import com.valkyrie.client.gui.ValkyrieGuiScreen;
import com.valkyrie.client.gui.ValkyrieMainMenu;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.MainMenuModule;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public final class ClientHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean loggedMainMenuReplacement = false;
    private static boolean hadLevel;
    private static boolean lowHealth;
    private static int previousHurtTime;

    public static void init() {
        // Реестр строится до чтения конфига: загружать состояние некуда,
        // пока модули не созданы.
        ModuleRegistry.register();
        ValkyrieOptionsManager.load();
        com.valkyrie.client.render.font.GfxFontRenderers.init();
        // Проекция слушает FOV мирового прохода — без этого ESP и трейсеры
        // считали бы по 70°, и метки уезжали бы при любом изменении обзора.
        com.valkyrie.client.render.ValkyrieProjection.register();
        com.valkyrie.client.render.SakuraBlockHighlight.register();
        com.valkyrie.client.render.FirstPersonVisuals.register();
        com.valkyrie.client.render.CameraEffectsController.register();
        com.valkyrie.client.render.WorldAtmosphere.register();
        MinecraftForge.EVENT_BUS.register(new ClientHandler());
        LOGGER.info("[Valkyrie] Client handler initialized");
    }

    /**
     * Бой считается внутри тика игрока.
     * <p>
     * Событие обновления ввода — единственная точка, где прицел игрока уже
     * посчитан ({@code gameRenderer.pick}), а пакет движения ещё не отправлен.
     * В {@code ClientTickEvent.Pre} угол пришлось бы менять до {@code pick()},
     * и аура уводила бы за собой добычу блоков.
     */
    @SubscribeEvent
    public void onMovementInput(MovementInputUpdateEvent event) {
        if (event.getEntity() == Minecraft.getInstance().player) {
            com.valkyrie.client.combat.CombatEngine.tick(event.getInput());
        }
    }

    @SubscribeEvent
    public void onRenderTickPre(TickEvent.RenderTickEvent.Pre event) {
        com.valkyrie.client.render.FirstPersonVisuals.beginFrame();
        com.valkyrie.client.render.ValkyrieProjection.beginFrame();
        com.valkyrie.client.render.VisualTime.beforeRender();
    }

    @SubscribeEvent
    public void onRenderTickPost(TickEvent.RenderTickEvent.Post event) {
        com.valkyrie.client.render.VisualTime.afterRender();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            com.valkyrie.client.render.VisualTime.reset();
            if (hadLevel) {
                com.valkyrie.client.gui.notify.ValkyrieQuotes.reset();
                com.valkyrie.client.gui.CompanionReactions.reset();
                // Освобождаем то, что держит ссылки на покинутый мир и GPU:
                // кэш траекторий держит ClientLevel, шрифты — атласы чужих
                // ников, набранные за сессию.
                com.valkyrie.client.render.ProjectileTrajectory.reset();
                com.valkyrie.client.render.font.GfxFontRenderers.releaseDynamicPages();
            }
            hadLevel = false;
            lowHealth = false;
            previousHurtTime = 0;
        } else {
            hadLevel = true;
            if (minecraft.player != null) {
                int hurtTime = minecraft.player.hurtTime;
                if (hurtTime > previousHurtTime) {
                    com.valkyrie.client.gui.notify.ValkyrieQuotes.onHurt();
                    com.valkyrie.client.gui.CompanionReactions.warning();
                }
                previousHurtTime = hurtTime;
                float healthRatio = minecraft.player.getHealth() / Math.max(1.0f, minecraft.player.getMaxHealth());
                if (!lowHealth && healthRatio <= 0.28f) {
                    lowHealth = true;
                    com.valkyrie.client.gui.notify.ValkyrieQuotes.onLowHealth();
                } else if (lowHealth && healthRatio >= 0.42f) {
                    lowHealth = false;
                }
            }
        }

        // Возврат взгляда игроку: в тихом режиме камера не должна дёргаться.
        com.valkyrie.client.combat.CombatEngine.restore();
        com.valkyrie.client.render.MemoryEchoTracker.tick();
        com.valkyrie.client.render.ProjectileTrailTracker.tick();
        com.valkyrie.client.render.CameraEffectsController.tick();
        com.valkyrie.client.render.StorageIndex.tick();

        if (ValkyrieKeyMappings.OPEN_GUI.consumeClick()) {
            if (minecraft.screen instanceof ValkyrieGuiScreen screen) {
                screen.requestClose();
            } else if (minecraft.screen == null) {
                minecraft.setScreen(new ValkyrieGuiScreen());
                com.valkyrie.client.gui.notify.ValkyrieQuotes.onOpenGui();
            }
        }
    }

    @SubscribeEvent
    public void onScreenOpen(ScreenEvent.Opening event) {
        if (!ModuleRegistry.isEnabled(MainMenuModule.class)) {
            return;
        }

        if (event.getScreen() instanceof TitleScreen) {
            if (!loggedMainMenuReplacement) {
                loggedMainMenuReplacement = true;
                LOGGER.info("[Valkyrie] Replacing vanilla title screen with Valkyrie main menu");
            }
            event.setNewScreen(new ValkyrieMainMenu());
        }
    }
}
