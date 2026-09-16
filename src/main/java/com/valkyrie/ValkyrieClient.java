package com.valkyrie;

import com.valkyrie.client.hud.ValkyrieEspOverlay;
import com.valkyrie.client.hud.ValkyrieHudOverlay;
import com.valkyrie.client.hud.ValkyrieTracersOverlay;
import com.valkyrie.client.hud.ValkyrieCrosshairOverlay;
import com.valkyrie.client.hud.ValkyrieTargetGlowOverlay;
import com.valkyrie.client.hud.ValkyrieCombatBloomOverlay;
import com.valkyrie.client.hud.ValkyrieThreatConstellationOverlay;
import com.valkyrie.client.hud.ValkyrieIntentEchoOverlay;
import com.valkyrie.client.hud.ValkyrieProjectileOverlay;
import com.valkyrie.client.hud.ValkyrieStorageEspOverlay;
import com.valkyrie.client.hud.ValkyrieWorldColorOverlay;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.CrosshairModule;
import com.valkyrie.client.ValkyrieSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(ValkyrieClient.MODID)
public final class ValkyrieClient {
    public static final String MODID = "valkyrieclient";

    private static final ResourceLocation HUD_LAYER_ID = ResourceLocation.fromNamespaceAndPath(MODID, "hud");
    private static final ResourceLocation TRACERS_LAYER_ID = ResourceLocation.fromNamespaceAndPath(MODID, "tracers");
    private static final ResourceLocation ESP_LAYER_ID = ResourceLocation.fromNamespaceAndPath(MODID, "esp");
    private static final ResourceLocation CROSSHAIR_LAYER_ID = ResourceLocation.fromNamespaceAndPath(MODID, "crosshair");
    private static final ResourceLocation TARGET_GLOW_LAYER_ID = ResourceLocation.fromNamespaceAndPath(MODID, "target_glow");
    private static final ResourceLocation COMBAT_BLOOM_LAYER_ID = ResourceLocation.fromNamespaceAndPath(MODID, "combat_bloom");
    private static final ResourceLocation THREAT_CONSTELLATION_LAYER_ID = ResourceLocation.fromNamespaceAndPath(MODID, "threat_constellation");
    private static final ResourceLocation INTENT_ECHO_LAYER_ID = ResourceLocation.fromNamespaceAndPath(MODID, "intent_echo");
    private static final ResourceLocation PROJECTILE_LAYER_ID = ResourceLocation.fromNamespaceAndPath(MODID, "projectiles");
    private static final ResourceLocation STORAGE_LAYER_ID = ResourceLocation.fromNamespaceAndPath(MODID, "storage_esp");
    private static final ResourceLocation WORLD_COLOR_LAYER_ID = ResourceLocation.fromNamespaceAndPath(MODID, "world_color");

    public ValkyrieClient() {
        var modBusGroup = FMLJavaModLoadingContext.get().getModBusGroup();
        ValkyrieSounds.SOUND_EVENTS.register(modBusGroup);
        FMLClientSetupEvent.getBus(modBusGroup).addListener(ValkyrieClient::clientSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            RegisterKeyMappingsEvent.BUS.addListener(ModEvents::registerKeyMappings);
            AddGuiOverlayLayersEvent.BUS.addListener(ModEvents::addGuiOverlayLayers);
        }
    }

    private static void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(com.valkyrie.client.ClientHandler::init);
    }

    private static final class ModEvents {
        private ModEvents() {
        }

        public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(com.valkyrie.client.ValkyrieKeyMappings.OPEN_GUI);
        }

        public static void addGuiOverlayLayers(AddGuiOverlayLayersEvent event) {
            // Порядок = порядок отрисовки: рамки ESP под линиями трейсеров,
            // HUD поверх всего, чтобы панели не перекрывались метками мира.
            event.getLayeredDraw()
                .addConditionTo(
                    ForgeLayeredDraw.PRE_SLEEP_STACK,
                    ForgeLayeredDraw.CROSSHAIR,
                    () -> !ModuleRegistry.isEnabled(CrosshairModule.class)
                )
                .addAbove(
                    ForgeLayeredDraw.PRE_SLEEP_STACK,
                    CROSSHAIR_LAYER_ID,
                    ForgeLayeredDraw.CROSSHAIR,
                    new ValkyrieCrosshairOverlay()
                )
                .add(WORLD_COLOR_LAYER_ID, new ValkyrieWorldColorOverlay())
                .add(STORAGE_LAYER_ID, new ValkyrieStorageEspOverlay())
                .add(PROJECTILE_LAYER_ID, new ValkyrieProjectileOverlay())
                .add(INTENT_ECHO_LAYER_ID, new ValkyrieIntentEchoOverlay())
                .add(THREAT_CONSTELLATION_LAYER_ID, new ValkyrieThreatConstellationOverlay())
                .add(COMBAT_BLOOM_LAYER_ID, new ValkyrieCombatBloomOverlay())
                .add(TARGET_GLOW_LAYER_ID, new ValkyrieTargetGlowOverlay())
                .add(ESP_LAYER_ID, new ValkyrieEspOverlay())
                .add(TRACERS_LAYER_ID, new ValkyrieTracersOverlay())
                .add(HUD_LAYER_ID, new ValkyrieHudOverlay());
        }
    }
}
