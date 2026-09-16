package com.valkyrie.client.combat;

import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.CriticalsModule;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** Двухфазный natural jump: запрос прыжка, затем удар в настоящий falling tick. */
public final class CriticalsController {
    private static boolean pending;
    private static int pendingTicks;

    private CriticalsController() {
    }

    public static boolean allowAttack(LocalPlayer player, LivingEntity target, ClientInput input) {
        if (!ModuleRegistry.isEnabled(CriticalsModule.class)) {
            reset();
            return true;
        }
        if (isCritical(player)) {
            reset();
            return true;
        }
        if (pending) {
            pendingTicks++;
            if (pendingTicks > 16 || impossible(player)) {
                reset();
                return true;
            }
            // В подъёме ждём; после неудачного приземления разрешаем обычный удар.
            if (player.onGround() && pendingTicks > 2) {
                reset();
                return true;
            }
            return false;
        }
        if (!canStart(player) || player.getAttackStrengthScale(0.5f) <= 0.9f) {
            return true;
        }
        input.makeJump();
        pending = true;
        pendingTicks = 0;
        return false;
    }

    private static boolean isCritical(LocalPlayer player) {
        return player.getAttackStrengthScale(0.5f) > 0.9f
            && player.fallDistance > 0.0f
            && !player.onGround()
            && !player.onClimbable()
            && !player.isInWater()
            && !player.isMobilityRestricted()
            && !player.isPassenger()
            && !player.isSprinting();
    }

    private static boolean canStart(LocalPlayer player) {
        return player.onGround() && !impossible(player) && !player.isSprinting();
    }

    private static boolean impossible(LocalPlayer player) {
        return !player.isAlive() || player.isSpectator() || player.isPassenger() || player.onClimbable()
            || player.isInWater() || player.isSwimming() || player.isFallFlying()
            || player.getAbilities().flying || player.isMobilityRestricted() || player.isUsingItem()
            || player.hasEffect(MobEffects.SLOW_FALLING) || player.hasEffect(MobEffects.LEVITATION);
    }

    public static void reset() {
        pending = false;
        pendingTicks = 0;
    }
}
