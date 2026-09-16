package com.valkyrie.client.combat;

import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.AutoWeaponModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Единая точка автоматической атаки и подготовки оружия. */
public final class CombatActions {
    private CombatActions() {
    }

    public static void attack(Minecraft mc, LocalPlayer player, LivingEntity target) {
        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND);
        com.valkyrie.client.render.CombatVisuals.hit(target);
        com.valkyrie.client.gui.notify.ValkyrieQuotes.onHit();
    }

    /** false означает, что слот только что изменён и атаку нужно отложить. */
    public static boolean prepareWeapon(LocalPlayer player) {
        if (!ModuleRegistry.isEnabled(AutoWeaponModule.class)) return true;
        Inventory inventory = player.getInventory();
        int current = inventory.getSelectedSlot();
        int best = current;
        double bestScore = score(player, inventory.getItem(current));
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(ItemTags.SWORDS) && !stack.is(ItemTags.AXES)) continue;
            double score = score(player, stack);
            if (score > bestScore + 1.0E-4) {
                bestScore = score;
                best = slot;
            }
        }
        if (best == current) return true;
        inventory.setSelectedSlot(best);
        return false;
    }

    private static double score(LocalPlayer player, ItemStack stack) {
        if (!stack.is(ItemTags.SWORDS) && !stack.is(ItemTags.AXES)) return Double.NEGATIVE_INFINITY;
        AttributeValue damage = new AttributeValue(player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE));
        AttributeValue speed = new AttributeValue(player.getAttributeBaseValue(Attributes.ATTACK_SPEED));
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE)) damage.add(modifier);
            if (attribute.equals(Attributes.ATTACK_SPEED)) speed.add(modifier);
        });
        // Урон главный; скорость лишь разрешает равенство без лишнего переключения.
        return damage.value() * 100.0 + speed.value();
    }

    private static final class AttributeValue {
        private final double base;
        private double add;
        private double multipliedBase;
        private double multipliedTotal = 1.0;

        private AttributeValue(double base) {
            this.base = base;
        }

        private void add(AttributeModifier modifier) {
            switch (modifier.operation()) {
                case ADD_VALUE -> add += modifier.amount();
                case ADD_MULTIPLIED_BASE -> multipliedBase += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> multipliedTotal *= 1.0 + modifier.amount();
            }
        }

        private double value() {
            return (base + add + base * multipliedBase) * multipliedTotal;
        }
    }
}
