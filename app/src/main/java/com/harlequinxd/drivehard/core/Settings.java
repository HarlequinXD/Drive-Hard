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
    /** New islands: the size (WorldSpec.Size ordinal); the seed of the last island made (0 none). */
    public int mapSize = 2;
    public long lastSeed;
    /** Cruise: time of day (index into TIME_HOURS), clock running, weather (0 changing), traffic. */
    public int timeOfDay = 1, weather = 0, traffic = 2;
    public boolean clockRuns = true;

    public static final String[] TIME_NAMES = {"DAWN", "MORNING", "NOON", "AFTERNOON", "SUNSET", "NIGHT", "MIDNIGHT"};
    public static final double[] TIME_HOURS = {5.4, 8.0, 12.0, 15.5, 18.1, 21.5, 0.5};
    public static final String[] WEATHER_NAMES = {"CHANGING", "CLEAR", "FAIR", "HAZY", "FOG", "OVERCAST", "RAIN", "STORM"};
    public static final Environment.Weather[] WEATHERS = {null, Environment.Weather.CLEAR, Environment.Weather.FAIR,
            Environment.Weather.HAZE, Environment.Weather.FOG, Environment.Weather.OVERCAST, Environment.Weather.RAIN, Environment.Weather.STORM};
    public static final String[] TRAFFIC_NAMES = {"NONE", "LIGHT", "NORMAL", "HEAVY"};
    public static final int[] TRAFFIC = {0, 14, 30, 48};

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
            s.mapSize = clamp(Integer.parseInt(p.getProperty("mapSize", "2")), 0, 3);
            s.lastSeed = Long.parseLong(p.getProperty("lastSeed", "0"));
            s.timeOfDay = clamp(Integer.parseInt(p.getProperty("timeOfDay", "1")), 0, TIME_HOURS.length - 1);
            s.weather = clamp(Integer.parseInt(p.getProperty("weather", "0")), 0, WEATHERS.length - 1);
            s.traffic = clamp(Integer.parseInt(p.getProperty("traffic", "2")), 0, TRAFFIC.length - 1);
            s.clockRuns = Boolean.parseBoolean(p.getProperty("clockRuns", "true"));
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
        p.setProperty("mapSize", Integer.toString(mapSize));
        p.setProperty("lastSeed", Long.toString(lastSeed));
        p.setProperty("timeOfDay", Integer.toString(timeOfDay));
        p.setProperty("weather", Integer.toString(weather));
        p.setProperty("traffic", Integer.toString(traffic));
        p.setProperty("clockRuns", Boolean.toString(clockRuns));
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
        apply(g.audio);
        Renderer r = g.renderer;
        if (r != null) {
            r.texMap = Renderer.TEX_PERSPECTIVE;                 // textures sit exactly on every polygon
            if (r instanceof GlRenderer) ((GlRenderer) r).psxSort = psxSort;
            if (r instanceof com.harlequinxd.drivehard.core.render.SoftRenderer)
                ((com.harlequinxd.drivehard.core.render.SoftRenderer) r).depthBuffer = !psxSort;
        }
    }

    /** Volumes into effect (before there is an island). */
    public void apply(GameAudio a) {
        a.mixer.musicLevel = music / 10f;
        a.mixer.sfx = effects / 10f;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
