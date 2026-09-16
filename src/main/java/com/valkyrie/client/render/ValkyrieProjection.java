package com.valkyrie.client.render;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * Перевод мировых координат в координаты GUI.
 * <p>
 * Матрица собирается ровно так же, как в {@code GameRenderer.renderLevel}:
 * {@code proj * bob * view}, где {@code view} — только поворот камеры, а сдвиг
 * на позицию камеры делается вычитанием до умножения (иначе теряется точность
 * на больших координатах мира).
 * <p>
 * FOV берётся из {@link ViewportEvent.ComputeFov}: одноимённый метод
 * {@code GameRenderer#getFov} приватный, а событие отдаёт уже итоговое значение
 * со всеми модификаторами (скорость, зелья, лава).
 */
public final class ValkyrieProjection {
    /** Точка за камерой не имеет корректной проекции. */
    private static final float MIN_W = 0.0001f;

    /** Последний FOV мирового прохода. Обновляется раз в кадр событием. */
    private static float worldFov = 70.0f;
    private static long frameSequence;
    private static long cachedFrame = -1;
    private static Matrix4f cachedMatrix;

    private ValkyrieProjection() {
    }

    /** Регистрируется один раз при старте клиента. */
    public static void register() {
        ViewportEvent.ComputeFov.BUS.addListener(ValkyrieProjection::onComputeFov);
    }

    private static void onComputeFov(ViewportEvent.ComputeFov event) {
        // Тот же вызов повторяется для руки с usedConfiguredFov=false — он нам не нужен.
        if (event.usedConfiguredFov()) {
            worldFov = event.getFOV();
        }
    }

    public static float worldFov() {
        return worldFov;
    }

    public static void beginFrame() {
        frameSequence++;
    }

    /**
     * Доля тика, с которой отрисован текущий кадр мира.
     * <p>
     * Берётся у камеры, а не у {@code DeltaTracker}: при заморозке тикрейта
     * {@code GameRenderer} подставляет камере 1.0, и сущности, интерполированные
     * по «живой» дельте, разъехались бы с моделями.
     */
    public static float renderPartialTick(float fallback) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        return camera.isInitialized() ? camera.getPartialTickTime() : fallback;
    }

    /**
     * Матрица {@code proj * bob * view} для текущего кадра.
     *
     * @return {@code null}, если камера ещё не инициализирована
     */
    public static Matrix4f viewProjection(float partialTick) {
        if (cachedFrame == frameSequence) return cachedMatrix;
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (!camera.isInitialized() || minecraft.getWindow().getHeight() == 0) {
            cachedFrame = frameSequence;
            cachedMatrix = null;
            return null;
        }
        // bobView в GameRenderer считается именно по камерному partialTick.
        partialTick = camera.getPartialTickTime();

        float aspect = (float) minecraft.getWindow().getWidth() / minecraft.getWindow().getHeight();
        Matrix4f matrix = new Matrix4f().perspective(
            worldFov * (float) (Math.PI / 180.0),
            aspect,
            0.05f,
            minecraft.gameRenderer.getDepthFar()
        );

        applyBobView(matrix, partialTick);

        // Поворот камеры — сопряжённый кватернион: мир крутится навстречу взгляду.
        Quaternionf inverse = camera.rotation().conjugate(new Quaternionf());
        cachedMatrix = matrix.mul(new Matrix4f().rotation(inverse));
        cachedFrame = frameSequence;
        return cachedMatrix;
    }

    /**
     * Покачивание при ходьбе. Без него ESP «плавает» относительно моделей,
     * потому что мир рендерится уже с этим сдвигом.
     * <p>
     * Наклон от урона ({@code GameRenderer#bobHurt}) намеренно не воспроизводится:
     * он длится доли секунды и требует состояния {@code hurtDir}/{@code hurtDuration},
     * а рассинхрон на это время визуально незаметен.
     */
    private static void applyBobView(Matrix4f matrix, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.options.bobView().get()) {
            return;
        }
        if (!(minecraft.getCameraEntity() instanceof AbstractClientPlayer player)) {
            return;
        }

        float walk = player.avatarState().getBackwardsInterpolatedWalkDistance(partialTick);
        float bob = player.avatarState().getInterpolatedBob(partialTick);
        if (bob == 0.0f) {
            return;
        }

        matrix.translate(Mth.sin(walk * (float) Math.PI) * bob * 0.5f, -Math.abs(Mth.cos(walk * (float) Math.PI) * bob), 0.0f);
        matrix.rotateZ(Mth.sin(walk * (float) Math.PI) * bob * 3.0f * (float) (Math.PI / 180.0));
        matrix.rotateX(Math.abs(Mth.cos(walk * (float) Math.PI - 0.2f) * bob) * 5.0f * (float) (Math.PI / 180.0));
    }

    /**
     * Проецирует мировую точку в координаты GUI.
     *
     * @param matrix результат {@link #viewProjection(float)}
     * @return {@code null}, если точка позади камеры
     */
    public static ScreenPos project(Matrix4f matrix, Vec3 world, int guiWidth, int guiHeight) {
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return project(matrix, cameraPos, world, guiWidth, guiHeight);
    }

    public static ScreenPos project(Matrix4f matrix, Vec3 cameraPos, Vec3 world, int guiWidth, int guiHeight) {
        if (matrix == null) {
            return null;
        }

        Vector4f point = new Vector4f(
            (float) (world.x - cameraPos.x),
            (float) (world.y - cameraPos.y),
            (float) (world.z - cameraPos.z),
            1.0f
        );
        matrix.transform(point);
        if (point.w <= MIN_W) {
            return null;
        }

        float ndcX = point.x / point.w;
        float ndcY = point.y / point.w;
        return new ScreenPos(
            (ndcX * 0.5f + 0.5f) * guiWidth,
            (0.5f - ndcY * 0.5f) * guiHeight,
            point.w
        );
    }

    /**
     * Проекция с прижатием к рамке экрана: точка позади камеры отражается,
     * чтобы указатель ушёл в правильную сторону, а не залип в центре.
     *
     * @param padding отступ от края экрана в пикселях GUI
     */
    public static ScreenPos projectClamped(Matrix4f matrix, Vec3 world, int guiWidth, int guiHeight, int padding) {
        if (matrix == null) {
            return null;
        }

        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vector4f point = new Vector4f(
            (float) (world.x - cameraPos.x),
            (float) (world.y - cameraPos.y),
            (float) (world.z - cameraPos.z),
            1.0f
        );
        matrix.transform(point);

        float w = Math.abs(point.w) < MIN_W ? MIN_W : Math.abs(point.w);
        float ndcX = point.x / w;
        float ndcY = point.y / w;
        boolean behind = point.w <= MIN_W;
        if (behind) {
            // Разворачиваем и уводим за край: направление сохраняется, позиция — нет.
            ndcX = -ndcX;
            ndcY = -ndcY;
            float length = Math.max(Math.abs(ndcX), Math.abs(ndcY));
            if (length < MIN_W) {
                ndcX = 0.0f;
                ndcY = -1.0f;
            } else {
                ndcX /= length;
                ndcY /= length;
            }
        }

        float x = (ndcX * 0.5f + 0.5f) * guiWidth;
        float y = (0.5f - ndcY * 0.5f) * guiHeight;
        boolean onScreen = !behind
            && x >= padding && x <= guiWidth - padding
            && y >= padding && y <= guiHeight - padding;

        return new ScreenPos(
            Mth.clamp(x, padding, Math.max(padding, guiWidth - padding)),
            Mth.clamp(y, padding, Math.max(padding, guiHeight - padding)),
            onScreen ? point.w : -Math.abs(point.w)
        );
    }

    /**
     * Точка на экране.
     *
     * @param depth значение {@code w} перспективного деления; отрицательное — точка
     *              не видна и координаты прижаты к краю
     */
    public record ScreenPos(float x, float y, float depth) {
        public boolean visible() {
            return depth > 0.0f;
        }
    }
}
