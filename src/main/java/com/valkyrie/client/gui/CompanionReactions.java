package com.valkyrie.client.gui;

/** Короткие, ненавязчивые реакции модели-компаньона на действия пользователя. */
public final class CompanionReactions {
    private static long actionAtNs;
    private static Kind kind = Kind.NONE;

    private CompanionReactions() {
    }

    public static void moduleToggled(boolean enabled) {
        react(enabled ? Kind.ENABLED : Kind.DISABLED);
    }

    public static void settingChanged() {
        react(Kind.SETTING);
    }

    public static void greeting() {
        react(Kind.GREETING);
    }

    public static void warning() {
        react(Kind.WARNING);
    }

    private static void react(Kind next) {
        kind = next;
        actionAtNs = System.nanoTime();
    }

    public static float lookX() {
        float wave = wave();
        return switch (kind) {
            case DISABLED -> -10.0f * wave;
            case SETTING -> 6.0f * wave;
            case WARNING -> (float) Math.sin(age() * 34.0f) * 8.0f * wave;
            case GREETING -> 4.0f * wave;
            default -> 0.0f;
        };
    }

    public static float lookY() {
        float wave = wave();
        return switch (kind) {
            case ENABLED -> 18.0f * wave;
            case DISABLED -> -6.0f * wave;
            case SETTING -> 7.0f * wave;
            case GREETING -> 12.0f * wave;
            case WARNING -> -5.0f * wave;
            default -> 0.0f;
        };
    }

    public static float bob() {
        return kind == Kind.ENABLED || kind == Kind.GREETING ? -2.0f * wave() : 0.0f;
    }

    private static float wave() {
        float age = age();
        float duration = kind == Kind.WARNING || kind == Kind.GREETING ? 0.70f : 0.45f;
        if (age < 0.0f || age >= duration) return 0.0f;
        return (float) Math.sin(age / duration * Math.PI);
    }

    private static float age() {
        return (System.nanoTime() - actionAtNs) / 1_000_000_000.0f;
    }

    public static void reset() {
        kind = Kind.NONE;
        actionAtNs = 0L;
    }

    private enum Kind {
        NONE, ENABLED, DISABLED, SETTING, GREETING, WARNING
    }
}
