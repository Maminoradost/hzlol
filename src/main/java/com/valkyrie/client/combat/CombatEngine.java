package com.valkyrie.client.combat;

import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.AimAssistModule;
import com.valkyrie.client.module.impl.KillAuraModule;
import com.valkyrie.client.module.impl.TriggerBotModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Один владелец ротации и автоматической атаки на тик. */
public final class CombatEngine {
    private CombatEngine() {
    }

    public static void tick(ClientInput input) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.screen != null || !player.isAlive() || player.isSpectator()) {
            KillAuraEngine.reset();
            CriticalsController.reset();
            return;
        }
        if (AutoTotemController.tick(mc, player)) {
            CriticalsController.reset();
            KillAuraEngine.reset();
            return;
        }

        // KillAura полностью владеет боевым pipeline, пока включена.
        if (ModuleRegistry.isEnabled(KillAuraModule.class)) {
            KillAuraEngine.onPreTick(input);
            return;
        }
        KillAuraEngine.reset();

        AimAssistModule assist = ModuleRegistry.get(AimAssistModule.class);
        if (assist.isEnabled() && mc.options.keyAttack.isDown() && !player.isUsingItem()) {
            LivingEntity target = CombatTargeting.closestToCrosshair(
                level, player, assist.targets, assist.range.value(), assist.fov.value(), true
            );
            if (target != null) {
                Vec3 eyes = player.getEyePosition();
                Vec3 aim = AimPoint.center(target, 1.0f);
                ValkyrieRotation.request(
                    CombatTargeting.yawTo(eyes, aim),
                    CombatTargeting.pitchTo(eyes, aim),
                    assist.speed.value(),
                    false
                );
                ValkyrieRotation.apply(player);
            }
        }

        TriggerBotModule trigger = ModuleRegistry.get(TriggerBotModule.class);
        if (!trigger.isEnabled() || player.isUsingItem()
            || !(mc.hitResult instanceof EntityHitResult hit)
            || !(hit.getEntity() instanceof LivingEntity target)
            || !CombatTargeting.valid(player, target, trigger.targets, player.entityInteractionRange(), true)) {
            return;
        }
        if (!CombatActions.prepareWeapon(player)) return;
        if (player.getAttackStrengthScale(0.0f) < trigger.cooldown.value()) return;
        if (!CriticalsController.allowAttack(player, target, input)) return;
        CombatActions.attack(mc, player, target);
    }

    public static void restore() {
        KillAuraEngine.onPostTick();
    }
}
