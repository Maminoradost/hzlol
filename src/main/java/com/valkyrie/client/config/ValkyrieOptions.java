package com.valkyrie.client.config;

/**
 * Состояние клиента, сериализуемое в {@code config/valkyrieclient.json}.
 * <p>
 * Здесь живёт только то, что не принадлежит конкретному модулю: раскладка
 * интерфейса и позиции блоков. Настройки функций хранит
 * {@link com.valkyrie.client.module.ModuleRegistry} — иначе добавление модуля
 * требовало бы правки этого класса, ClickGUI и миграции разом.
 * <p>
 * Координаты со значением {@code -1} означают «позиция не задана» — элемент
 * раскладывается автоматически относительно размеров экрана.
 */
public final class ValkyrieOptions {
    public static final int UNSET = -1;

    public int configVersion = 10;

    // ─── Позиции блоков HUD ──────────────────────────────────────────────────

    public int hudWatermarkX = 10;
    public int hudWatermarkY = 10;
    public int hudKeysX = 10;
    public int hudKeysY = UNSET;
    public int hudInfoX = 10;
    public int hudInfoY = 44;
    public int hudArmorX = UNSET;
    public int hudArmorY = UNSET;
    public int hudEffectsX = UNSET;
    public int hudEffectsY = 10;
    public int hudTargetX = UNSET;
    public int hudTargetY = UNSET;

    // ─── Позиции колонок ClickGUI ────────────────────────────────────────────

    public int panelHudX = UNSET;
    public int panelHudY = UNSET;
    public int panelVisualsX = UNSET;
    public int panelVisualsY = UNSET;
    public int panelCombatX = UNSET;
    public int panelCombatY = UNSET;
    public int panelClientX = UNSET;
    public int panelClientY = UNSET;
    public boolean clickGuiCollapsed = false;

    public ValkyrieOptions() {
    }
}
