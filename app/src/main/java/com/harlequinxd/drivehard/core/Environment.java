package com.harlequinxd.drivehard.core;

import java.util.Random;

/**
 * Time of day, seasons and weather.
 *
 * <p>The clock runs one game hour per real minute (a day is 24 minutes). The year has two
 * seasons of {@value #SEASON_DAYS} days each: the <b>wet season</b> (humid, frequent showers,
 * afternoon thunderstorms, morning fog, rainbows after rain) and the <b>dry season</b> (clear
 * skies, fair-weather cumulus, dusty haze, the odd overcast day, brown-gold grass).</p>
 *
 * <p>Weather is a small Markov chain: each spell lasts one to four game hours and the next is
 * picked with weights that depend on the season and the hour (fog at dawn, storms in the
 * afternoon). Spells cross-fade over a quarter of an hour. Everything the renderer needs is
 * worked out in {@link #update}: scene light (tint), sky and fog colours, fog distance, cloud
 * cover and colour, rain, lightning, sun and moon directions, stars, lit windows, rainbows.</p>
 */
public final class Environment {
    public static final float SECONDS_PER_HOUR = 60f;
    public static final int SEASON_DAYS = 4;

    public enum Season { DRY("Dry season"), WET("Wet season");
        public final String label; Season(String l) { label = l; } }

    public enum Weather {
        CLEAR("Clear"), FAIR("Fair"), HAZE("Hazy"), FOG("Fog"), OVERCAST("Overcast"),
        SHOWERS("Showers"), RAIN("Rain"), STORM("Thunderstorm");
        public final String label; Weather(String l) { label = l; }
    }

    // per weather: cloud cover, cloud darkness, light, saturation, rain, fog distance scale, haze
    private static final float[][] P = {
            /* CLEAR    */ {0.05f, 0.0f, 1.00f, 1.00f, 0.0f, 1.00f, 0.0f},
            /* FAIR     */ {0.38f, 0.05f, 1.00f, 1.00f, 0.0f, 1.00f, 0.0f},
            /* HAZE     */ {0.12f, 0.0f, 0.95f, 0.80f, 0.0f, 0.62f, 1.0f},
            /* FOG      */ {0.70f, 0.15f, 0.85f, 0.55f, 0.0f, 0.30f, 0.0f},
            /* OVERCAST */ {0.92f, 0.35f, 0.74f, 0.55f, 0.0f, 0.80f, 0.0f},
            /* SHOWERS  */ {0.75f, 0.45f, 0.78f, 0.70f, 0.45f, 0.75f, 0.0f},
            /* RAIN     */ {1.00f, 0.60f, 0.60f, 0.50f, 0.80f, 0.60f, 0.0f},
            /* STORM    */ {1.00f, 0.85f, 0.45f, 0.45f, 1.00f, 0.50f, 0.0f},
    };

    /** Game time in hours since the start (day 1, 07:00). */
    public double hours = 7.0;
    public Weather weather = Weather.FAIR, next = Weather.FAIR;
    private float blend = 1f, spellLeft = 1.5f;
    private final Random rnd;

    // ------------------------------------------------------------------ outputs (per frame)
    /** Scene light per channel (256 = full daylight). */
    public int tintR = 256, tintG = 256, tintB = 256;
    public int zenith = 0x3A78C8, horizon = 0xBCD8EA;
    public float fogScale = 1f;
    public float cloudCover, cloudDark;
    public int cloudLit = 0xFFFFFF, cloudShade = 0x9098A8;
    public float rain, flash;
    public float sunX, sunY, sunZ, moonX, moonY, moonZ;
    public float sunVis, moonVis, stars;
    /** 0 by day, 1 at night: windows light up. */
    public float night;
    /** Share of windows lit right now. */
    public float windowsLit;
    public float rainbow;
    /** Dry season grass browning, 0..1. */
    public float dryness;
    public float windX, windZ;
    /** Lightning bolt: screen-space seed and remaining time (0: none). */
    public int boltSeed;
    public float boltTime;
    private float sinceRain = 99f, rainbowChance;
    private float nextFlash = 4f;

    public Environment(long seed) {
        rnd = new Random(seed ^ 0x5EA5011L);
    }

    public int day() { return 1 + (int) (hours / 24.0); }
    public float hourOfDay() { return (float) (hours % 24.0); }
    public Season season() { return ((day() - 1) / SEASON_DAYS) % 2 == 0 ? Season.DRY : Season.WET; }
    /** 0..1 through the current season. */
    public float seasonProgress() { return (float) (((hours / 24.0) % SEASON_DAYS) / SEASON_DAYS); }

    /** The weather shown in the HUD: the spell being faded in once it is half way. */
    public Weather shown() { return blend < 0.5f ? weather : next; }

    public String clock() {
        float h = hourOfDay();
        int hh = (int) h, mm = (int) ((h - hh) * 60f);
        return String.format(java.util.Locale.US, "%02d:%02d", hh, mm);
    }

    /** Jumps to a time and weather (tools, debugging). */
    public void force(double atHours, Weather w) {
        hours = atHours;
        weather = next = w;
        blend = 1f;
        spellLeft = 3f;
        update(0f);
    }

    // ------------------------------------------------------------------ simulation

    public void update(float dt) {
        float dh = dt / SECONDS_PER_HOUR;
        hours += dh;
        // weather spells
        if (blend < 1f) {
            blend = Math.min(1f, blend + dh / 0.25f);
            if (blend >= 1f) weather = next;
        } else {
            spellLeft -= dh;
            if (spellLeft <= 0f) {
                next = pick();
                spellLeft = 1f + rnd.nextFloat() * 3f;
                if (next != weather) blend = 0f;
            }
        }
        float[] p = mix();
        cloudCover = p[0];
        cloudDark = p[1];
        rain = p[4];
        fogScale = p[5];
        // rainbows: low sun, rain just ended
        if (rain > 0.3f) { sinceRain = 0f; rainbowChance = rnd.nextFloat(); }
        else sinceRain += dh;
        // lightning
        if (boltTime > 0f) boltTime -= dt;
        flash = Math.max(0f, flash - dt * 4f);
        float storm = weightOf(Weather.STORM);
        if (storm > 0.5f) {
            nextFlash -= dt;
            if (nextFlash <= 0f) {
                flash = 1f;
                boltSeed = rnd.nextInt();
                boltTime = 0.18f;
                nextFlash = 2.5f + rnd.nextFloat() * 9f;
            }
        }
        float wa = (float) (hours * 0.05);
        windX = (float) Math.cos(wa) * (6f + 10f * cloudDark);
        windZ = (float) Math.sin(wa) * (6f + 10f * cloudDark) + 4f;
        light(p);
    }

    private float weightOf(Weather w) {
        float a = weather == w ? 1f - blend : 0f, b = next == w ? blend : 0f;
        return weather == next ? (weather == w ? 1f : 0f) : a + b;
    }

    private float[] mix() {
        float[] a = P[weather.ordinal()], b = P[next.ordinal()], o = new float[a.length];
        float t = blend * blend * (3 - 2 * blend);
        for (int i = 0; i < a.length; i++) o[i] = a[i] + (b[i] - a[i]) * t;
        return o;
    }

    /** Next weather spell: weights by season and hour, and what comes naturally after the current one. */
    private Weather pick() {
        boolean wet = season() == Season.WET;
        float h = (hourOfDay() + 1f) % 24f;
        float[] w = new float[Weather.values().length];
        if (wet) {
            w[Weather.CLEAR.ordinal()] = 1f;
            w[Weather.FAIR.ordinal()] = 3f;
            w[Weather.OVERCAST.ordinal()] = 2.5f;
            w[Weather.SHOWERS.ordinal()] = 3f;
            w[Weather.RAIN.ordinal()] = 2f;
            w[Weather.STORM.ordinal()] = h >= 13f && h <= 20f ? 3f : 0.6f;
            w[Weather.FOG.ordinal()] = h >= 4f && h <= 8f ? 3f : 0.1f;
        } else {
            w[Weather.CLEAR.ordinal()] = 4f;
            w[Weather.FAIR.ordinal()] = 3.5f;
            w[Weather.HAZE.ordinal()] = h >= 10f && h <= 18f ? 2.5f : 0.8f;
            w[Weather.OVERCAST.ordinal()] = 1f;
            w[Weather.SHOWERS.ordinal()] = 0.4f;
            w[Weather.FOG.ordinal()] = h >= 4f && h <= 8f ? 1.2f : 0f;
        }
        // weather moves step by step: storms ease through rain, clear skies cloud over first
        Weather cur = weather;
        if (cur == Weather.STORM) { w[Weather.CLEAR.ordinal()] = 0f; w[Weather.HAZE.ordinal()] = 0f; w[Weather.RAIN.ordinal()] += 3f; }
        if (cur == Weather.CLEAR || cur == Weather.HAZE) { w[Weather.STORM.ordinal()] *= 0.2f; w[Weather.RAIN.ordinal()] *= 0.3f; }
        if (cur == Weather.RAIN) w[Weather.STORM.ordinal()] += wet ? 1f : 0f;
        float sum = 0f;
        for (float v : w) sum += v;
        float x = rnd.nextFloat() * sum;
        for (int i = 0; i < w.length; i++) { x -= w[i]; if (x <= 0f) return Weather.values()[i]; }
        return Weather.FAIR;
    }

    // ------------------------------------------------------------------ light and colour

    // keyframes over the day: hour, scene tint, zenith, horizon
    private static final float[] KH = {0f, 4.5f, 5.6f, 6.3f, 7.5f, 12f, 16.5f, 17.7f, 18.4f, 19.3f, 20.5f, 24f};
    private static final int[] KT = {0x404A78, 0x4A5080, 0x9080A0, 0xF0B090, 0xFAF2E6, 0xFFFFFF, 0xFFF8EC, 0xFAC8A0, 0xE08C70, 0x8A70A0, 0x404A78, 0x404A78};
    private static final int[] KZ = {0x060A1C, 0x0A1030, 0x283A70, 0x4A6EB0, 0x3C7CCC, 0x3A78C8, 0x3C7ACA, 0x4A6AB0, 0x3A4A90, 0x1C2458, 0x060A1C, 0x060A1C};
    private static final int[] KS = {0x141A30, 0x1C2240, 0x8A6A80, 0xF0A880, 0xC4DCEA, 0xBCD8EA, 0xC8DCE6, 0xF0B888, 0xF08A5C, 0x6A4A70, 0x141A30, 0x141A30};

    private void light(float[] p) {
        float h = hourOfDay();
        int k = 0;
        while (k < KH.length - 2 && h >= KH[k + 1]) k++;
        float t = (h - KH[k]) / (KH[k + 1] - KH[k]);
        t = t * t * (3 - 2 * t);
        int tint = lerp(KT[k], KT[k + 1], t), zen = lerp(KZ[k], KZ[k + 1], t), hor = lerp(KS[k], KS[k + 1], t);

        // sun from the east (+X) at 06:00 over the north-ish sky to the west at 18:00; moon opposite
        double a = (h - 6.0) / 12.0 * Math.PI;
        sunX = (float) Math.cos(a); sunY = (float) Math.sin(a) * 0.92f; sunZ = -0.38f;
        float l = (float) Math.sqrt(sunX * sunX + sunY * sunY + sunZ * sunZ);
        sunX /= l; sunY /= l; sunZ /= l;
        moonX = -sunX; moonY = -sunY; moonZ = -sunZ * 0.5f + 0.2f;
        l = (float) Math.sqrt(moonX * moonX + moonY * moonY + moonZ * moonZ);
        moonX /= l; moonY /= l; moonZ /= l;
        night = clamp01((0.08f - sunY) / 0.25f);
        stars = clamp01((-0.05f - sunY) / 0.2f) * (1f - p[0] * 0.9f);
        // more windows lit in the evening than in the small hours
        windowsLit = h >= 17f || h < 1f ? 0.55f : (h < 5f ? 0.18f : 0.35f);

        // weather: dim, desaturate and grey the light and sky
        float light = p[2], sat = p[3], cover = p[0];
        tint = scale(desat(tint, sat), light);
        int grey = desat(zen, 0f);
        int stormSky = lerp(scale(0x8A929E, 0.25f + 0.75f * lum(tint) / 255f), 0x3A4048, p[1] * (1f - night));
        zen = lerp(desat(zen, sat), lerp(grey, stormSky, cover), cover * 0.85f);
        hor = lerp(desat(hor, sat), lerp(desat(hor, 0f), stormSky, cover * 0.6f), cover * 0.75f);
        if (p[6] > 0f) {      // dusty haze: warm, milky horizon
            hor = lerp(hor, scale(0xD8C8A0, lum(tint) / 255f), p[6] * 0.7f);
            zen = lerp(zen, hor, p[6] * 0.35f);
        }
        if (weightOf(Weather.FOG) > 0f) hor = lerp(hor, scale(0xD0D4D8, Math.max(0.15f, lum(tint) / 255f)), weightOf(Weather.FOG) * 0.8f);
        sunVis = clamp01(sunY * 8f + 0.3f) * (1f - cover * 0.95f);
        moonVis = clamp01(moonY * 8f) * (1f - cover * 0.95f);

        // clouds: lit by the sun (golden at the ends of the day), dark bases in storms
        int lit = lerp(scale(lerp(0xFFFFFF, tint, 0.6f), 1f), 0x30364A, night * 0.85f);
        if (sunY > -0.1f && sunY < 0.3f) lit = lerp(lit, 0xFFB080, (1f - Math.abs(sunY - 0.08f) / 0.22f) * 0.6f * (1f - night));
        cloudLit = lit;
        cloudShade = lerp(scale(lit, 0.75f), scale(0x404650, lum(tint) / 255f + 0.15f), p[1]);

        // rainbow: sun behind you and low, rain just passed
        float bowTime = sinceRain < 0.6f && rain < 0.15f ? (1f - sinceRain / 0.6f) : 0f;
        rainbow = rainbowChance > 0.35f ? bowTime * clamp01((0.75f - sunY) * 3f) * clamp01(sunY * 10f) * (1f - cover * 0.7f) : 0f;

        // the dry season browns the grass, most at its end
        float sp = seasonProgress();
        dryness = season() == Season.DRY ? clamp01(sp * 1.6f) : clamp01(1f - sp * 3f) * 0.6f;

        if (flash > 0f) {
            tint = lerp(tint, 0xE8F0FF, flash * 0.75f);
            zen = lerp(zen, 0xC0C8E0, flash * 0.8f);
            hor = lerp(hor, 0xD0D8F0, flash * 0.8f);
        }
        // allow a little overbright at noon: textures are authored for the old fixed light
        tintR = ((tint >> 16) & 255) + (((tint >> 16) & 255) >> 7);
        tintG = ((tint >> 8) & 255) + (((tint >> 8) & 255) >> 7);
        tintB = (tint & 255) + ((tint & 255) >> 7);
        zenith = zen;
        horizon = hor;
    }

    static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }

    static int lerp(int a, int b, float t) {
        t = clamp01(t);
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int lum(int c) { return (((c >> 16) & 255) * 3 + ((c >> 8) & 255) * 6 + (c & 255)) / 10; }

    static int desat(int c, float sat) {
        int l = lum(c);
        return lerp((l << 16) | (l << 8) | l, c, sat);
    }

    static int scale(int c, float k) {
        int r = Math.min(255, (int) (((c >> 16) & 255) * k)), g = Math.min(255, (int) (((c >> 8) & 255) * k)), b = Math.min(255, (int) ((c & 255) * k));
        return (r << 16) | (g << 8) | b;
    }
}
