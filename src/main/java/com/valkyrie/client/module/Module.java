package com.valkyrie.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.valkyrie.client.gui.notify.Notifications;
import java.util.ArrayList;
import java.util.List;

/**
 * Функция клиента: имя, категория, состояние и собственные настройки.
 * <p>
 * Наследники переопределяют {@link #onEnable()}/{@link #onDisable()} и рисуют
 * себя сами; реестр отвечает только за состояние, конфиг и ClickGUI.
 */
public abstract class Module {
    private final String name;
    private final String description;
    private final Category category;
    private final List<Setting<?>> settings = new ArrayList<>();

    private boolean enabled;
    /** Раскрыт ли список настроек в ClickGUI (по правому клику). */
    private boolean expanded;

    protected Module(String name, String description, Category category) {
        this(name, description, category, false);
    }

    protected Module(String name, String description, Category category, boolean enabledByDefault) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = enabledByDefault;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Category category() {
        return category;
    }

    /** Ключ в конфиге: имя без пробелов и регистра, чтобы правка подписи не сбрасывала настройки. */
    public String key() {
        return name.toLowerCase(java.util.Locale.ROOT).replace(" ", "");
    }

    public List<Setting<?>> settings() {
        return settings;
    }

    /** Настройки, прошедшие условие видимости, — именно они рисуются и кликаются. */
    public List<Setting<?>> visibleSettings() {
        List<Setting<?>> visible = new ArrayList<>(settings.size());
        for (Setting<?> setting : settings) {
            if (setting.visible()) {
                visible.add(setting);
            }
        }
        return visible;
    }

    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        return setting;
    }

    /**
     * Есть ли у модуля включатель.
     * <p>
     * Часть записей реестра — не функции, а группы глобальных настроек
     * (оформление, цвета). Тумблер у них означал бы несуществующее состояние,
     * поэтому строка рисуется без переключателя и раскрывается по любому клику.
     */
    public boolean hasToggle() {
        return true;
    }

    /** Строка-кнопка: клик выполняет {@link #runAction()} вместо переключения. */
    public boolean isAction() {
        return false;
    }

    public void runAction() {
    }

    /** Короткая подпись action-кнопки в ClickGUI. */
    public String actionLabel() {
        return "OPEN";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean next) {
        if (this.enabled == next) {
            return;
        }
        this.enabled = next;
        if (next) {
            onEnable();
        } else {
            onDisable();
        }
        Notifications.toggle(name, next);
        com.valkyrie.client.gui.CompanionReactions.moduleToggled(next);
    }

    /**
     * Восстановление состояния из конфига: без побочных эффектов и без тоста.
     * Загрузка не должна выглядеть как действие пользователя.
     */
    public void setEnabledSilently(boolean next) {
        this.enabled = next;
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    // ─── Конфиг ──────────────────────────────────────────────────────────────

    public JsonObject save() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        for (Setting<?> setting : settings) {
            setting.save(json);
        }
        return json;
    }

    public void load(JsonObject json) {
        if (json == null) {
            return;
        }
        JsonElement enabledElement = json.get("enabled");
        if (enabledElement != null && enabledElement.isJsonPrimitive()) {
            try {
                setEnabledSilently(enabledElement.getAsBoolean());
            } catch (RuntimeException ignored) {
                // Битое значение — остаётся состояние по умолчанию.
            }
        }
        for (Setting<?> setting : settings) {
            setting.load(json.get(setting.key()));
        }
    }
}
