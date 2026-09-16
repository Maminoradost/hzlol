package com.valkyrie.client.combat;

import com.valkyrie.client.gui.notify.Notifications;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.AutoTotemModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Items;

/** Один синхронизированный vanilla SWAP; никогда не работает поверх открытого меню. */
public final class AutoTotemController {
    private AutoTotemController() {
    }

    public static boolean tick(Minecraft mc, LocalPlayer player) {
        AutoTotemModule module = ModuleRegistry.get(AutoTotemModule.class);
        if (!module.isEnabled() || mc.gameMode == null || mc.screen != null || player.isSpectator()
            || player.containerMenu != player.inventoryMenu || !player.containerMenu.getCarried().isEmpty()
            || player.isUsingItem() || player.getAbilities().instabuild
            || player.getHealth() + player.getAbsorptionAmount() > module.health.value()
            || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return false;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (!inventory.getItem(slot).is(Items.TOTEM_OF_UNDYING)) continue;
            int menuSlot = slot < Inventory.getSelectionSize()
                ? InventoryMenu.USE_ROW_SLOT_START + slot
                : slot;
            mc.gameMode.handleInventoryMouseClick(
                player.inventoryMenu.containerId,
                menuSlot,
                Inventory.SLOT_OFFHAND,
                ClickType.SWAP,
                player
            );
            Notifications.warning("AutoTotem", "totem equipped");
            return true;
        }
        return false;
    }
}
