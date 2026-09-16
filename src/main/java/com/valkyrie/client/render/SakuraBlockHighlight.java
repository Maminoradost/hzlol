package com.valkyrie.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.valkyrie.client.gui.theme.Theme;
import com.valkyrie.client.module.ModuleRegistry;
import com.valkyrie.client.module.impl.BlockHighlightModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RenderHighlightEvent;

/** Замена ванильного block outline через предназначенный для этого Forge hook. */
public final class SakuraBlockHighlight {
    private static boolean registered;

    private SakuraBlockHighlight() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        RenderHighlightEvent.Block.BUS.addListener(SakuraBlockHighlight::onHighlight);
    }

    private static void onHighlight(RenderHighlightEvent.Block event) {
        BlockHighlightModule module = ModuleRegistry.get(BlockHighlightModule.class);
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || mc.level == null) {
            return;
        }

        BlockHitResult hit = event.getTarget();
        BlockPos pos = hit.getBlockPos().immutable();
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        CollisionContext context = event.getCamera().getEntity() != null
            ? CollisionContext.of(event.getCamera().getEntity())
            : CollisionContext.empty();
        VoxelShape shape = state.getShape(mc.level, pos, context);
        if (shape.isEmpty()) {
            return;
        }

        int blockX = pos.getX();
        int blockY = pos.getY();
        int blockZ = pos.getZ();
        String style = module.style.value();
        float configuredOpacity = module.opacity.value();
        boolean pulse = module.pulse.value();
        int accent = Theme.accent();

        event.setCustomRenderer((source, poseStack, translucent, renderState) -> {
            // Forge вызывает custom callback в opaque и translucent местах.
            if (translucent) {
                return;
            }
            Vec3 camera = renderState.cameraRenderState.pos;
            double offsetX = blockX - camera.x;
            double offsetY = blockY - camera.y;
            double offsetZ = blockZ - camera.z;
            float breath = pulse
                ? 0.78f + 0.22f * ((Mth.sin(System.nanoTime() / 1_000_000_000.0f * 1.8f) + 1.0f) * 0.5f)
                : 1.0f;
            float alpha = configuredOpacity * breath;
            float red = ARGB.red(accent) / 255.0f;
            float green = ARGB.green(accent) / 255.0f;
            float blue = ARGB.blue(accent) / 255.0f;

            if (!style.equals("Outline")) {
                RenderType fillType = RenderType.debugFilledBox();
                VertexConsumer fill = source.getBuffer(fillType);
                shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                    ShapeRenderer.addChainedFilledBoxVertices(
                        poseStack,
                        fill,
                        minX + offsetX,
                        minY + offsetY,
                        minZ + offsetZ,
                        maxX + offsetX,
                        maxY + offsetY,
                        maxZ + offsetZ,
                        red,
                        green,
                        blue,
                        alpha * 0.16f
                    )
                );
                source.endBatch(fillType);
            }

            if (!style.equals("Fill")) {
                RenderType outlineType = RenderType.lines();
                ShapeRenderer.renderShape(
                    poseStack,
                    source.getBuffer(outlineType),
                    shape,
                    offsetX,
                    offsetY,
                    offsetZ,
                    Theme.alpha(accent, alpha)
                );
                source.endBatch(outlineType);
            }
        });
    }
}
