package com.valkyrie.launcher;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.BufferedInputStream;

final class LauncherSounds implements AutoCloseable {
    private final LauncherConfig config;
    private final Clip press;
    private final Clip open;
    private final Clip close;
    private final Clip[] typing = new Clip[3];
    private int typingIndex;
    private long lastTypeAt;

    LauncherSounds(LauncherConfig config) {
        this.config = config;
        press = load("/sounds/ui-click.wav");
        open = load("/sounds/ui-open.wav");
        close = load("/sounds/ui-close.wav");
        for (int i = 0; i < typing.length; i++) typing[i] = load("/sounds/ui-type.wav");
    }

    void press() {
        if (!config.uiSounds) return;
        restart(press);
    }

    void type() {
        long now = System.nanoTime();
        if (!config.uiSounds || now - lastTypeAt < 48_000_000L) return;
        lastTypeAt = now;
        Clip clip = typing[typingIndex++ % typing.length];
        restart(clip);
    }

    void open() {
        if (config.uiSounds) restart(open);
    }

    void closePanel() {
        if (config.uiSounds) restart(close);
    }

    boolean available() {
        return press != null && press.isOpen();
    }

    @Override
    public void close() {
        if (press != null) press.close();
        if (open != null) open.close();
        if (close != null) close.close();
        for (Clip clip : typing) if (clip != null) clip.close();
    }

    private static void restart(Clip clip) {
        if (clip == null) return;
        synchronized (clip) {
            clip.stop();
            clip.setFramePosition(0);
            clip.start();
        }
    }

    private static Clip load(String path) {
        var resource = LauncherSounds.class.getResourceAsStream(path);
        if (resource == null) return null;
        try (var input = new BufferedInputStream(resource); var stream = AudioSystem.getAudioInputStream(input)) {
            Clip clip = AudioSystem.getClip();
            clip.open(stream);
            return clip;
        } catch (Exception ignored) {
            return null;
        }
    }
}
