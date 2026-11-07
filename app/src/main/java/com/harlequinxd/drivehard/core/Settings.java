package com.harlequinxd.drivehard.core;

import com.harlequinxd.drivehard.core.gl.GlRenderer;
import com.harlequinxd.drivehard.core.render.Renderer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/** The player's options and story progress, kept in a small text file in the game's data folder. */
public final class Settings {
    /** Volumes 0..10. */
    public int music = 7, effects = 8;
    /**
     * Polygon sorting: false (the default) a depth buffer on top of the ordering table, so fences,
     * walls and signs never show through what is in front of them; true pure PS1 ordering table.
     */
    public boolean psxSort = false;
    /** Story: missions passed (bit per mission) and best times in seconds (0 = none). */
    public int passed;
    public final float[] best = new float[16];

    private transient File file;

    public static Settings load(File file) {
        Settings s = new Settings();
        s.file = file;
        if (file == null || !file.isFile()) return s;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
            s.music = clamp(Integer.parseInt(p.getProperty("music", "7")), 0, 10);
            s.effects = clamp(Integer.parseInt(p.getProperty("effects", "8")), 0, 10);
            s.psxSort = !Boolean.parseBoolean(p.getProperty("depthBuffer", "true"));
            s.passed = Integer.parseInt(p.getProperty("passed", "0"));
            for (int i = 0; i < s.best.length; i++) s.best[i] = Float.parseFloat(p.getProperty("best" + i, "0"));
        } catch (Exception ignored) {
            // a damaged file: keep the defaults
        }
        return s;
    }

    public void save() {
        if (file == null) return;
        Properties p = new Properties();
        p.setProperty("music", Integer.toString(music));
        p.setProperty("effects", Integer.toString(effects));
        p.setProperty("depthBuffer", Boolean.toString(!psxSort));
        p.setProperty("passed", Integer.toString(passed));
        for (int i = 0; i < best.length; i++) if (best[i] > 0f) p.setProperty("best" + i, Float.toString(best[i]));
        try {
            File dir = file.getParentFile();
            if (dir != null) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(file)) { p.store(out, "Drive Hard"); }
        } catch (Exception ignored) {
        }
    }

    /** Puts the options into effect. */
    public void apply(Game g) {
        g.audio.mixer.musicLevel = music / 10f;
        g.audio.mixer.sfx = effects / 10f;
        Renderer r = g.renderer;
        if (r != null) {
            r.texMap = Renderer.TEX_PERSPECTIVE;                 // textures sit exactly on every polygon
            if (r instanceof GlRenderer) ((GlRenderer) r).psxSort = psxSort;
            if (r instanceof com.harlequinxd.drivehard.core.render.SoftRenderer)
                ((com.harlequinxd.drivehard.core.render.SoftRenderer) r).depthBuffer = !psxSort;
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
