package com.valkyrie.client.module;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.util.Mth;

/**
 * Настройка модуля: значение, его сериализация и данные для отрисовки строки.
 * <p>
 * Значение хранится прямо здесь, а не в {@code ValkyrieOptions}: иначе каждый
 * новый модуль требовал бы правки класса конфига, ClickGUI и миграции — ровно та
 * связанность, ради устранения которой заведён реестр.
 */
public abstract class Setting<T> {
    private final String key;
    private final String name;
    private final String description;

    /**
     * Условие видимости строки. Позволяет прятать зависимые настройки
     * (например «Wall check» без включённого таргетинга) вместо их блокировки.
     */
    private BooleanSupplier visibleWhen = () -> true;

    protected Setting(String key, String name, String description) {
        this.key = key;
        this.name = name;
        this.description = description;
    }

    public String key() {
        return key;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public boolean visible() {
        return visibleWhen.getAsBoolean();
    }

    /** Показывать строку только при выполнении условия. Возвращает себя для цепочки. */
    public Setting<T> visibleWhen(BooleanSupplier condition) {
        this.visibleWhen = condition;
        return this;
    }

    public abstract T get();

    public abstract void set(T value);

    /** Записывает значение в объект настроек модуля. */
    public abstract void save(JsonObject target);

    /** Читает значение, молча игнорируя мусор: битый конфиг не должен ронять клиент. */
    public abstract void load(JsonElement source);

    // ─── Реализации ──────────────────────────────────────────────────────────

    /** Тумблер. */
    public static final class Bool extends Setting<Boolean> {
        private boolean value;

        public Bool(String key, String name, String description, boolean initial) {
            super(key, name, description);
            this.value = initial;
        }

        @Override
        public Boolean get() {
            return value;
        }

        public boolean value() {
            return value;
        }

        @Override
        public void set(Boolean next) {
            this.value = next;
        }

        @Override
        public void save(JsonObject target) {
            target.addProperty(key(), value);
        }

        @Override
        public void load(JsonElement source) {
            if (source != null && source.isJsonPrimitive()) {
                try {
                    value = source.getAsBoolean();
                } catch (RuntimeException ignored) {
                    // Оставляем значение по умолчанию.
                }
            }
        }
    }

    /** Число в диапазоне с форматированием подписи. */
    public static final class Slider extends Setting<Float> {
        private final float min;
        private final float max;
        private final Formatter formatter;
        private float value;

        public Slider(String key, String name, float initial, float min, float max, Formatter formatter) {
            super(key, name, "");
            this.min = min;
            this.max = max;
            this.formatter = formatter;
            this.value = Mth.clamp(initial, min, max);
        }

        public float min() {
            return min;
        }

        public float max() {
            return max;
        }

        public float value() {
            return value;
        }

        public String label() {
            return formatter.format(value);
        }

        @Override
        public Float get() {
            return value;
        }

        @Override
        public void set(Float next) {
            this.value = Mth.clamp(next, min, max);
        }

        @Override
        public void save(JsonObject target) {
            target.addProperty(key(), value);
        }

        @Override
        public void load(JsonElement source) {
            if (source != null && source.isJsonPrimitive()) {
                try {
                    value = Mth.clamp(source.getAsFloat(), min, max);
                } catch (RuntimeException ignored) {
                    // Оставляем значение по умолчанию.
                }
            }
        }

        @FunctionalInterface
        public interface Formatter {
            String format(float value);
        }

        public static String percent(float value) {
            return Math.round(value * 100.0f) + "%";
        }

        public static String meters(float value) {
            return Math.round(value) + "m";
        }

        public static String plain(float value) {
            return String.valueOf(Math.round(value));
        }

        public static String decimal(float value) {
            return String.format("%.1f", value);
        }
    }

    /** Цвет ARGB, переключается по палитре темы. */
    public static final class Color extends Setting<Integer> {
        private int value;

        public Color(String key, String name, int initial) {
            super(key, name, "palette");
            this.value = initial;
        }

        public int value() {
            return value;
        }

        @Override
        public Integer get() {
            return value;
        }

        @Override
        public void set(Integer next) {
            this.value = next;
        }

        @Override
        public void save(JsonObject target) {
            target.addProperty(key(), value);
        }

        @Override
        public void load(JsonElement source) {
            if (source != null && source.isJsonPrimitive()) {
                try {
                    value = source.getAsInt();
                } catch (RuntimeException ignored) {
                    // Оставляем значение по умолчанию.
                }
            }
        }
    }

    /** Выбор одного варианта из списка — циклический перебор кликом. */
    public static final class Mode extends Setting<String> {
        private final List<String> options;
        private int index;

        public Mode(String key, String name, List<String> options, String initial) {
            super(key, name, "");
            this.options = List.copyOf(options);
            this.index = Math.max(0, this.options.indexOf(initial));
        }

        public List<String> options() {
            return options;
        }

        public String value() {
            return options.get(index);
        }

        public boolean is(String candidate) {
            return value().equals(candidate);
        }

        public void cycle() {
            index = (index + 1) % options.size();
        }

        @Override
        public String get() {
            return value();
        }

        @Override
        public void set(String next) {
            int found = options.indexOf(next);
            if (found >= 0) {
                index = found;
            }
        }

        @Override
        public void save(JsonObject target) {
            target.add(key(), new JsonPrimitive(value()));
        }

        @Override
        public void load(JsonElement source) {
            if (source != null && source.isJsonPrimitive()) {
                try {
                    set(source.getAsString());
                } catch (RuntimeException ignored) {
                    // Оставляем значение по умолчанию.
                }
            }
        }
    }
}
