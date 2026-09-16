package com.valkyrie.client.gui.theme;

import com.mojang.logging.LogUtils;
import com.valkyrie.ValkyrieClient;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;
import org.slf4j.Logger;

/**
 * Необязательный декоративный PNG (маскот, баннер, фон).
 * <p>
 * Ассет ищется в {@code assets/valkyrieclient/textures/gui/}. Если файла нет,
 * все методы отрисовки молча ничего не делают — интерфейс остаётся рабочим,
 * а картинку можно добавить позже, не трогая код.
 * <p>
 * Чтобы подключить свой файл, положите PNG в указанную папку и создайте
 * константу через {@link #of(String, int, int)}.
 */
public final class GuiAsset {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, Boolean> PRESENCE_CACHE = new HashMap<>();

    /** Маскот в ClickGUI: положите {@code textures/gui/mascot.png}, чтобы включить. */
    public static final GuiAsset MASCOT = of("mascot.png", 512, 768);

    private final ResourceLocation location;
    private final int sourceWidth;
    private final int sourceHeight;

    private GuiAsset(ResourceLocation location, int sourceWidth, int sourceHeight) {
        this.location = location;
        this.sourceWidth = sourceWidth;
        this.sourceHeight = sourceHeight;
    }

    /**
     * @param fileName     имя файла в {@code textures/gui/}
     * @param sourceWidth  ширина исходника в пикселях
     * @param sourceHeight высота исходника в пикселях
     */
    public static GuiAsset of(String fileName, int sourceWidth, int sourceHeight) {
        return new GuiAsset(
            ResourceLocation.fromNamespaceAndPath(ValkyrieClient.MODID, "textures/gui/" + fileName),
            sourceWidth,
            sourceHeight
        );
    }

    public float aspectRatio() {
        return sourceWidth / (float) sourceHeight;
    }

    /**
     * Есть ли текстура в ресурсах. Результат кэшируется: обращение к менеджеру
     * ресурсов каждый кадр обходится слишком дорого.
     */
    public boolean exists() {
        return PRESENCE_CACHE.computeIfAbsent(location, id -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getResourceManager() == null) {
                return false;
            }
            boolean found = minecraft.getResourceManager().getResource(id).isPresent();
            if (!found) {
                LOGGER.info("[Valkyrie] Optional GUI asset not found, skipping: {}", id);
            }
            return found;
        });
    }

    /** Сбрасывает кэш наличия — вызывается при перезагрузке ресурспаков. */
    public static void invalidateCache() {
        PRESENCE_CACHE.clear();
    }

    /**
     * Рисует ассет вписанным в заданную высоту с сохранением пропорций.
     *
     * @param anchorX левый край области
     * @param bottomY нижняя граница, к которой прижимается картинка
     * @param height  желаемая высота в пикселях
     * @param opacity прозрачность 0..1
     */
    public void drawByHeight(GuiGraphics guiGraphics, float anchorX, float bottomY, float height, float opacity) {
        if (!exists() || opacity <= 0.01f || height <= 1.0f) {
            return;
        }

        float scale = height / sourceHeight;

        Matrix3x2fStack pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate(anchorX, bottomY - height);
        pose.scale(scale, scale);

        guiGraphics.blit(
            RenderPipelines.GUI_TEXTURED,
            location,
            0,
            0,
            0.0f,
            0.0f,
            sourceWidth,
            sourceHeight,
            sourceWidth,
            sourceHeight,
            Theme.alpha(0xFFFFFFFF, Mth.clamp(opacity, 0.0f, 1.0f))
        );

        pose.popMatrix();
    }

    /** Ширина, которую займёт ассет при заданной высоте. */
    public float widthForHeight(float height) {
        return height * aspectRatio();
    }
}
