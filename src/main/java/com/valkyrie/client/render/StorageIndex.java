package com.valkyrie.client.render;

import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.StorageEspModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/** Низкочастотный индекс block entities; сканирует карты BE, а не блоки. */
public final class StorageIndex {
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static int cooldown;
    private static ClientLevel indexedLevel;

    private StorageIndex() {
    }

    public static void tick() {
        StorageEspModule module = ModuleRegistry.get(StorageEspModule.class);
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || mc.level == null || mc.player == null) {
            clear();
            return;
        }
        if (indexedLevel != mc.level) {
            indexedLevel = mc.level;
            cooldown = 0;
        }
        if (cooldown-- > 0) {
            return;
        }
        cooldown = 20;
        rebuild(mc.level, mc.player.chunkPosition().x, mc.player.chunkPosition().z, module);
    }

    private static void rebuild(ClientLevel level, int centerX, int centerZ, StorageEspModule module) {
        ENTRIES.clear();
        int chunkRadius = Mth.ceil(module.range.value() / 16.0f) + 1;
        for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                LevelChunk chunk = level.getChunkSource().getChunk(centerX + dx, centerZ + dz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    Kind kind = classify(blockEntity.getType(), module);
                    if (kind == null || blockEntity.isRemoved()) continue;
                    BlockPos pos = blockEntity.getBlockPos();
                    BlockState state = blockEntity.getBlockState();
                    // Двойной сундук: показываем только LEFT, RIGHT дублировал маркер.
                    if ((kind == Kind.CHEST || kind == Kind.TRAPPED) && state.hasProperty(ChestBlock.TYPE)
                        && state.getValue(ChestBlock.TYPE) == ChestType.RIGHT) {
                        continue;
                    }
                    ENTRIES.add(new Entry(pos.immutable(), kind));
                }
            }
        }
    }

    private static Kind classify(BlockEntityType<?> type, StorageEspModule module) {
        if (type == BlockEntityType.CHEST && module.chests.value()) return Kind.CHEST;
        if (type == BlockEntityType.TRAPPED_CHEST && module.chests.value()) return Kind.TRAPPED;
        if (type == BlockEntityType.ENDER_CHEST && module.enderChests.value()) return Kind.ENDER;
        if (type == BlockEntityType.BARREL && module.barrels.value()) return Kind.BARREL;
        if (type == BlockEntityType.SHULKER_BOX && module.shulkers.value()) return Kind.SHULKER;
        if ((type == BlockEntityType.FURNACE || type == BlockEntityType.BLAST_FURNACE || type == BlockEntityType.SMOKER) && module.furnaces.value()) return Kind.FURNACE;
        if (type == BlockEntityType.HOPPER && module.hoppers.value()) return Kind.HOPPER;
        return null;
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static void clear() {
        ENTRIES.clear();
        indexedLevel = null;
        cooldown = 0;
    }

    public enum Kind {
        CHEST("Chest"), TRAPPED("Trapped"), ENDER("Ender chest"), BARREL("Barrel"),
        SHULKER("Shulker"), FURNACE("Furnace"), HOPPER("Hopper");

        public final String label;
        Kind(String label) { this.label = label; }
    }

    public record Entry(BlockPos pos, Kind kind) {
    }
}
