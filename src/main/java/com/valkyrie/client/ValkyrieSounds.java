package com.valkyrie.client;

import com.valkyrie.ValkyrieClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ValkyrieSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
        DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ValkyrieClient.MODID);

    public static final RegistryObject<SoundEvent> CREAM_CLICK = SOUND_EVENTS.register(
        "cream_click",
        () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ValkyrieClient.MODID, "cream_click"))
    );

    private ValkyrieSounds() {
    }
}
