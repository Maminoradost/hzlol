package com.valkyrie.client.module;

/**
 * Категория модуля — одна колонка ClickGUI.
 * <p>
 * Порядок констант задаёт порядок колонок слева направо.
 */
public enum Category {
    HUD("HUD", "overlay"),
    VISUALS("Visuals", "world"),
    COMBAT("Combat", "fight"),
    CLIENT("Client", "settings");

    private final String title;
    private final String subtitle;

    Category(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }
}
