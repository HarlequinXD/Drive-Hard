package com.harlequinxd.drivehard.core.audio;

/**
 * Every sound effect in the game, synthesized at start-up (no sound files): oscillators, filtered
 * noise, envelopes and a touch of bit-crush, at 22 050 Hz, the way a PS1 / GBA sound designer
 * would squeeze a whole game's effects into a few hundred kilobytes. Variants of the common
 * sounds (footsteps, strokes, bumps) differ in seed and tuning so repeats don't sound copied.
 */
public final class SoundBank {
    static final int R = Mixer.RATE;

    // on foot
    public final short[][] stepHard = new short[4][], stepGrass = new short[4][], stepSand = new short[4][], stepWater = new short[3][];
    public final short[] jump, land, landWater;
    // swimming
    public final short[][] stroke = new short[3][];
    public final short[] splashBig, splashSmall;
    // the car
    public final short[] doorOpen, doorClose, starter, handbrake, gearClunk, horn, hornShort;
    public final short[][] bump = new short[3][], crash = new short[2][], pop = new short[3][];
    // loops
    public final short[] tyreSqueal, roadRoar, gravelRoll, windLoop, rainLoop, roofRain, surfLoop, birdsLoop, cricketsLoop, cityHum, lapLoop;
    // weather one-shots
    public final short[] thunderNear, thunderFar;
    // interface
    public final short[] uiMove, uiSelect, uiBack, uiPause, uiMap, uiStart, missionStart, missionPass, missionFail, checkpoint, tick;

    public SoundBank() {
        for (int i = 0; i < 4; i++) {
            stepHard[i] = stepHard(100 + i);
            stepGrass[i] = stepGrass(200 + i);
            stepSand[i] = stepSand(300 + i);
        }
        for (int i = 0; i < 3; i++) {
            stepWater[i] = splash(0.16f, 0.35f, 400 + i);
            stroke[i] = splash(0.32f, 0.55f, 500 + i);
            bump[i] = bump(600 + i, 0.9f + i * 0.15f);
            pop[i] = pop(700 + i);
        }
        jump = whoosh(0.18f, 900f, 2600f, 0.5f, 11);
        land = thud(0.16f, 90f, 0.8f, 12);
        landWater = splash(0.4f, 0.8f, 13);
        splashBig = splash(0.85f, 1f, 14);
        splashSmall = splash(0.25f, 0.45f, 15);
        doorOpen = doorOpen(16);
        doorClose = doorClose(17);
        starter = starter(18);
        handbrake = ratchet(19);
        gearClunk = clunk(20);
        horn = horn(0.55f);
        hornShort = horn(0.18f);
        crash[0] = crash(0.7f, 21);
        crash[1] = crash(1.2f, 22);
        tyreSqueal = squeal(23);
        roadRoar = loopNoise(1.5f, 240f, 0.55f, 24, 0f);
        gravelRoll = gravel(25);
        windLoop = wind(26);
        rainLoop = rain(27, 4200f);
        roofRain = rain(28, 1800f);
        surfLoop = surf(29);
        birdsLoop = birds(30);
        cricketsLoop = crickets(31);
        cityHum = loopNoise(3f, 140f, 0.5f, 32, 0.25f);
        lapLoop = lapping(33);
        thunderNear = thunder(3.2f, true, 34);
        thunderFar = thunder(4.2f, false, 35);
        uiMove = blips(new float[]{1318.5f}, 0.045f, 0.5f);
        uiSelect = blips(new float[]{987.8f, 1318.5f, 1975.5f}, 0.055f, 0.55f);
        uiBack = blips(new float[]{1318.5f, 880f}, 0.06f, 0.5f);
        uiPause = blips(new float[]{1567.98f, 1174.66f, 783.99f}, 0.07f, 0.5f);
        uiMap = sweep(400f, 1600f, 0.22f, 0.45f);
        uiStart = fanfare(new float[]{523.25f, 659.25f, 783.99f, 1046.5f}, 0.09f, 0.55f);
        missionStart = fanfare(new float[]{392f, 523.25f, 659.25f, 783.99f}, 0.11f, 0.55f);
        missionPass = fanfare(new float[]{523.25f, 659.25f, 783.99f, 1046.5f, 1318.5f, 1567.98f}, 0.10f, 0.6f);
        missionFail = fanfare(new float[]{392f, 369.99f, 349.23f, 293.66f}, 0.2f, 0.5f);
        checkpoint = blips(new float[]{1567.98f, 2093f}, 0.07f, 0.55f);
        tick = blips(new float[]{2093f}, 0.03f, 0.35f);
    }

    // ------------------------------------------------------------------ building blocks

    /** A tiny deterministic random source. */
    private static final class Rng {
        int s;
        Rng(int seed) { s = seed * 747796405 + 1013904223; }
        float next() { s = s * 1103515245 + 12345; return ((s >> 8) & 0xFFFF) / 32768f - 1f; }
        float unit() { return (next() + 1f) * 0.5f; }
    }

    private static int n(float seconds) { return Math.max(1, (int) (seconds * R)); }

    private static short[] pcm(float[] x, float gain, int crush) {
        float peak = 1e-6f;
        for (float v : x) peak = Math.max(peak, Math.abs(v));
        float g = gain / peak;
        short[] o = new short[x.length];
        for (int i = 0; i < x.length; i++) {
            float v = x[i] * g;
            if (crush > 0) v = Math.round(v * crush) / (float) crush;    // retro grit
            o[i] = (short) Math.max(-32767, Math.min(32767, v * 32767f));
        }
        return o;
    }

    /** Makes a buffer loop seamlessly: its end crossfades into its start. */
    private static float[] seamless(float[] x, float fadeSeconds) {
        int f = Math.min(x.length / 3, n(fadeSeconds));
        int len = x.length - f;
        float[] o = new float[len];
        System.arraycopy(x, 0, o, 0, len);
        for (int i = 0; i < f; i++) {
            float t = i / (float) f;
            o[i] = x[i] * t + x[len + i] * (1f - t);
        }
        return o;
    }

    /** One-pole low-pass in place. */
    private static void lowpass(float[] x, float hz) {
        float a = Math.min(1f, (float) (2 * Math.PI * hz / R)), y = 0f;
        for (int i = 0; i < x.length; i++) { y += (x[i] - y) * a; x[i] = y; }
    }

    /** One-pole high-pass in place. */
    private static void highpass(float[] x, float hz) {
        float a = Math.min(1f, (float) (2 * Math.PI * hz / R)), y = 0f;
        for (int i = 0; i < x.length; i++) { y += (x[i] - y) * a; x[i] -= y; }
    }

    /** A resonant band-pass (state-variable filter) over noise or anything. */
    private static void bandpass(float[] x, float hz, float q) {
        float f = (float) (2 * Math.sin(Math.PI * Math.min(hz, R * 0.22f) / R)), low = 0, band = 0;
        float damp = 1f / q;
        for (int i = 0; i < x.length; i++) {
            low += f * band;
            float high = x[i] - low - damp * band;
            band += f * high;
            x[i] = band;
        }
    }

    private static float[] noise(int len, int seed) {
        Rng r = new Rng(seed);
        float[] x = new float[len];
        for (int i = 0; i < len; i++) x[i] = r.next();
        return x;
    }

    private static void env(float[] x, float attack, float decayPower) {
        int a = Math.max(1, n(attack));
        for (int i = 0; i < x.length; i++) {
            float t = i / (float) x.length;
            float e = i < a ? i / (float) a : (float) Math.pow(1f - t, decayPower);
            x[i] *= e;
        }
    }

    // ------------------------------------------------------------------ footsteps

    private static short[] stepHard(int seed) {
        Rng r = new Rng(seed);
        int len = n(0.09f);
        float[] x = noise(len, seed);
        highpass(x, 1500f + r.unit() * 800f);
        env(x, 0.002f, 6f);
        // the heel's knock
        float f0 = 140f + r.unit() * 60f;
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            x[i] += (float) Math.sin(2 * Math.PI * f0 * t) * Math.exp(-t * 60) * 0.8f;
        }
        return pcm(x, 0.55f, 0);
    }

    private static short[] stepGrass(int seed) {
        Rng r = new Rng(seed);
        int len = n(0.16f);
        float[] x = noise(len, seed);
        // crushed blades: a burst of tiny crackles
        for (int i = 0; i < len; i++) if (r.unit() > 0.985f) x[i] *= 4f;
        bandpass(x, 2200f + r.unit() * 900f, 0.9f);
        env(x, 0.012f, 2.5f);
        return pcm(x, 0.4f, 0);
    }

    private static short[] stepSand(int seed) {
        Rng r = new Rng(seed);
        int len = n(0.18f);
        float[] x = noise(len, seed);
        for (int i = 0; i < len; i++) if (r.unit() > 0.94f) x[i] *= 2.5f;
        bandpass(x, 1100f + r.unit() * 500f, 0.7f);
        env(x, 0.02f, 1.8f);
        return pcm(x, 0.42f, 0);
    }

    // ------------------------------------------------------------------ water

    /** A splash: a noise burst that darkens as it falls, with a few bubble chirps. */
    private static short[] splash(float seconds, float size, int seed) {
        Rng r = new Rng(seed);
        int len = n(seconds);
        float[] x = noise(len, seed);
        float y = 0f;
        for (int i = 0; i < len; i++) {
            float t = i / (float) len;
            float a = (float) (2 * Math.PI * (4500f - 3800f * t) / R);
            y += (x[i] - y) * Math.min(1f, a);
            float e = t < 0.04f ? t / 0.04f : (float) Math.pow(1f - t, 1.6f);
            x[i] = y * e;
        }
        // bubbles: short rising sine chirps
        int bubbles = (int) (3 + size * 8);
        for (int b = 0; b < bubbles; b++) {
            int st = (int) (r.unit() * len * 0.8f);
            float f0 = 600f + r.unit() * 900f, dur = 0.03f + r.unit() * 0.05f;
            int bl = n(dur);
            double ph = 0;
            for (int i = 0; i < bl && st + i < len; i++) {
                float t = i / (float) bl;
                ph += 2 * Math.PI * f0 * (1f + t * 0.8f) / R;
                x[st + i] += (float) Math.sin(ph) * (1f - t) * 0.25f * size;
            }
        }
        return pcm(x, 0.5f + size * 0.3f, 0);
    }

    private static short[] lapping(int seed) {
        int len = n(4f);
        float[] x = noise(len, seed);
        lowpass(x, 900f);
        Rng r = new Rng(seed + 1);
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            float swell = (float) (0.5 + 0.5 * Math.sin(t * 2 * Math.PI * 0.75 + Math.sin(t * 1.3) * 0.8));
            x[i] *= swell * swell;
        }
        for (int b = 0; b < 10; b++) {
            int st = (int) (r.unit() * len * 0.9f);
            float f0 = 500f + r.unit() * 700f;
            int bl = n(0.05f);
            double ph = 0;
            for (int i = 0; i < bl; i++) {
                ph += 2 * Math.PI * f0 * (1f + i / (float) bl) / R;
                x[st + i] += (float) Math.sin(ph) * (1f - i / (float) bl) * 0.15f;
            }
        }
        return pcm(seamless(x, 0.4f), 0.45f, 0);
    }

    // ------------------------------------------------------------------ body

    private static short[] whoosh(float seconds, float f0, float f1, float gain, int seed) {
        int len = n(seconds);
        float[] x = noise(len, seed);
        float low = 0, band = 0;
        for (int i = 0; i < len; i++) {
            float t = i / (float) len;
            float f = (float) (2 * Math.sin(Math.PI * (f0 + (f1 - f0) * t) / R));
            low += f * band;
            float high = x[i] - low - 0.6f * band;
            band += f * high;
            x[i] = band * (float) Math.sin(Math.PI * t);
        }
        return pcm(x, gain, 0);
    }

    private static short[] thud(float seconds, float hz, float gain, int seed) {
        int len = n(seconds);
        float[] x = noise(len, seed);
        lowpass(x, 600f);
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            x[i] = x[i] * 0.4f * (float) Math.exp(-t * 30) + (float) Math.sin(2 * Math.PI * hz * t * (1.0 - t * 1.5)) * (float) Math.exp(-t * 22);
        }
        return pcm(x, gain, 0);
    }

    // ------------------------------------------------------------------ car

    private static short[] doorOpen(int seed) {
        int len = n(0.22f);
        float[] x = new float[len];
        float[] nz = noise(len, seed);
        highpass(nz, 2500f);
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            // the latch: two clicks; the hinge: a short creak
            x[i] = nz[i] * ((float) Math.exp(-t * 400) + (float) Math.exp(-Math.max(0, t - 0.025) * 300) * (t > 0.025 ? 1 : 0)) * 0.9f;
            if (t > 0.05) x[i] += (float) (Math.sin(2 * Math.PI * (520 + 80 * Math.sin(t * 90)) * t) * 0.12 * Math.exp(-(t - 0.05) * 18));
        }
        return pcm(x, 0.5f, 0);
    }

    private static short[] doorClose(int seed) {
        int len = n(0.32f);
        float[] x = noise(len, seed);
        lowpass(x, 1400f);
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            x[i] = x[i] * (float) Math.exp(-t * 35) * 0.9f
                    + (float) (Math.sin(2 * Math.PI * 85 * t) * Math.exp(-t * 18) * 0.9)
                    + (float) (Math.sin(2 * Math.PI * 240 * t) * Math.exp(-t * 40) * 0.3);
        }
        return pcm(x, 0.75f, 0);
    }

    /** The starter motor cranking, then the engine catching. */
    private static short[] starter(int seed) {
        int len = n(0.9f);
        float[] x = new float[len];
        float[] nz = noise(len, seed);
        double ph = 0;
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            float crank = 11f + t * 4f;                                   // compressions per second
            double cr = Math.sin(2 * Math.PI * crank * t);
            float whirr = (float) (Math.sin(2 * Math.PI * 340 * t) * 0.25 + nz[i] * 0.2);
            float v = t < 0.62f ? (float) (whirr * (0.6 + 0.4 * cr)) : 0f;
            if (t > 0.55f) {                                               // it catches and flares
                float k = Math.min(1f, (t - 0.55f) / 0.08f) * (float) Math.exp(-(t - 0.63f) * 3.5f * (t > 0.63f ? 1 : 0));
                ph += 2 * Math.PI * (55 + 40 * k) / R;
                v += (float) (Math.sin(ph) * 0.8 + Math.sin(ph * 2) * 0.35 + nz[i] * 0.3) * k;
            }
            x[i] = v;
        }
        lowpass(x, 3000f);
        return pcm(x, 0.6f, 128);
    }

    private static short[] ratchet(int seed) {
        int len = n(0.2f);
        float[] x = new float[len];
        float[] nz = noise(len, seed);
        highpass(nz, 2000f);
        for (int c = 0; c < 6; c++) {
            int st = n(0.012f + c * 0.028f);
            for (int i = 0; i < n(0.012f) && st + i < len; i++) x[st + i] += nz[st + i] * (1f - i / (float) n(0.012f));
        }
        return pcm(x, 0.4f, 0);
    }

    private static short[] clunk(int seed) {
        int len = n(0.08f);
        float[] x = noise(len, seed);
        bandpass(x, 900f, 2f);
        env(x, 0.001f, 4f);
        return pcm(x, 0.3f, 0);
    }

    /** A two-tone Japanese car horn (a fifth apart, square-ish, slightly detuned). */
    private static short[] horn(float seconds) {
        int len = n(seconds);
        float[] x = new float[len];
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            float a = (float) Math.signum(Math.sin(2 * Math.PI * 420 * t)) + (float) Math.signum(Math.sin(2 * Math.PI * 503 * t)) * 0.8f;
            float e = Math.min(1f, t / 0.015f) * Math.min(1f, (seconds - t) / 0.04f);
            x[i] = a * e;
        }
        lowpass(x, 2200f);
        return pcm(x, 0.5f, 64);
    }

    private static short[] bump(int seed, float pitch) {
        int len = n(0.25f);
        float[] x = noise(len, seed);
        lowpass(x, 900f * pitch);
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            x[i] = x[i] * (float) Math.exp(-t * 25) * 0.7f + (float) (Math.sin(2 * Math.PI * 70 * pitch * t) * Math.exp(-t * 20));
        }
        return pcm(x, 0.6f, 0);
    }

    /** A crash: a body thump, crumpling metal (ringing partials over noise) and falling glass. */
    private static short[] crash(float seconds, int seed) {
        Rng r = new Rng(seed);
        int len = n(seconds);
        float[] x = noise(len, seed);
        float[] crunch = noise(len, seed + 1);
        bandpass(crunch, 1400f, 0.8f);
        float[] parts = {287f, 431f, 617f, 922f, 1311f};
        float[] glass = new float[len];
        for (int g = 0; g < 26; g++) {
            int st = n(0.08f + r.unit() * (seconds - 0.3f));
            float f0 = 3000f + r.unit() * 4000f;
            int gl = n(0.02f + r.unit() * 0.06f);
            for (int i = 0; i < gl && st + i < len; i++) glass[st + i] += (float) Math.sin(2 * Math.PI * f0 * i / R) * (1f - i / (float) gl) * 0.35f;
        }
        lowpass(x, 500f);
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            float m = 0f;
            for (int k = 0; k < parts.length; k++) m += (float) Math.sin(2 * Math.PI * parts[k] * t + k) * (float) Math.exp(-t * (6 + k * 3));
            float crumple = crunch[i] * (float) Math.exp(-t * 7) * (0.6f + 0.4f * (float) Math.sin(t * 90));
            x[i] = x[i] * 1.6f * (float) Math.exp(-t * 14) + m * 0.25f + crumple * 1.4f + glass[i];
        }
        return pcm(x, 0.95f, 160);
    }

    /** An exhaust pop on the overrun. */
    private static short[] pop(int seed) {
        int len = n(0.09f);
        float[] x = noise(len, seed);
        lowpass(x, 1200f);
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            x[i] = x[i] * (float) Math.exp(-t * 60) + (float) (Math.sin(2 * Math.PI * 95 * t) * Math.exp(-t * 45));
        }
        return pcm(x, 0.5f, 64);
    }

    private static short[] squeal(int seed) {
        int len = n(1.2f);
        float[] x = noise(len, seed);
        bandpass(x, 1100f, 14f);
        double ph = 0;
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            float wob = (float) (Math.sin(t * 2 * Math.PI * 7) * 60 + Math.sin(t * 2 * Math.PI * 3.1) * 40);
            ph += 2 * Math.PI * (1250 + wob) / R;
            x[i] = x[i] * 0.8f + (float) Math.sin(ph) * 0.35f + (float) Math.sin(ph * 1.5) * 0.12f;
        }
        return pcm(seamless(x, 0.2f), 0.45f, 0);
    }

    private static short[] loopNoise(float seconds, float hz, float gain, int seed, float wobble) {
        int len = n(seconds);
        float[] x = noise(len, seed);
        lowpass(x, hz);
        lowpass(x, hz * 1.5f);
        if (wobble > 0f) for (int i = 0; i < len; i++) x[i] *= 1f + wobble * (float) Math.sin(i * 2 * Math.PI * 0.37 / R);
        return pcm(seamless(x, 0.3f), gain, 0);
    }

    private static short[] gravel(int seed) {
        Rng r = new Rng(seed);
        int len = n(1.6f);
        float[] x = noise(len, seed);
        lowpass(x, 500f);
        float[] grit = noise(len, seed + 1);
        highpass(grit, 1800f);
        for (int i = 0; i < len; i++) x[i] = x[i] * 1.2f + grit[i] * (r.unit() > 0.92f ? 0.9f : 0.12f);
        return pcm(seamless(x, 0.2f), 0.5f, 0);
    }

    // ------------------------------------------------------------------ ambience and weather

    private static short[] wind(int seed) {
        int len = n(6f);
        float[] x = noise(len, seed);
        float low = 0, band = 0;
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            float hz = 380f + 220f * (float) Math.sin(t * 0.9) + 140f * (float) Math.sin(t * 2.3 + 1);
            float f = (float) (2 * Math.sin(Math.PI * hz / R));
            low += f * band;
            float high = x[i] - low - 0.35f * band;
            band += f * high;
            float gust = 0.55f + 0.45f * (float) Math.sin(t * 0.7 + Math.sin(t * 1.9));
            x[i] = band * gust;
        }
        return pcm(seamless(x, 1f), 0.5f, 0);
    }

    private static short[] rain(int seed, float hz) {
        Rng r = new Rng(seed);
        int len = n(3f);
        float[] x = noise(len, seed);
        lowpass(x, hz);
        highpass(x, 400f);
        for (int i = 0; i < len; i++) if (r.unit() > 0.997f) {
            // the odd heavier drop
            for (int k = 0; k < 40 && i + k < len; k++) x[i + k] += (float) Math.sin(k * 0.9) * (1f - k / 40f) * 0.8f;
        }
        return pcm(seamless(x, 0.3f), 0.45f, 0);
    }

    /** Surf: waves rolling in and washing back, a swell every few seconds. */
    private static short[] surf(int seed) {
        int len = n(9f);
        float[] x = noise(len, seed);
        lowpass(x, 1600f);
        float[] hiss = noise(len, seed + 1);
        highpass(hiss, 2500f);
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            float ph = (t % 4.5f) / 4.5f;
            float wave = ph < 0.25f ? ph / 0.25f : (float) Math.pow(1f - (ph - 0.25f) / 0.75f, 1.6f);
            float wash = (float) Math.max(0, Math.sin(Math.PI * Math.min(1, Math.max(0, (ph - 0.3f) / 0.6f))));
            x[i] = x[i] * (0.25f + wave) + hiss[i] * wash * 0.35f;
        }
        return pcm(seamless(x, 0.6f), 0.55f, 0);
    }

    /** Birdsong: little chirps and two-note calls scattered over a quiet loop. */
    private static short[] birds(int seed) {
        Rng r = new Rng(seed);
        int len = n(10f);
        float[] x = new float[len];
        for (int b = 0; b < 26; b++) {
            int st = (int) (r.unit() * (len - n(0.6f)));
            int kind = (int) (r.unit() * 3);
            float f0 = 2400f + r.unit() * 2200f;
            int notes = kind == 0 ? 1 : kind == 1 ? 2 : 3 + (int) (r.unit() * 4);
            float gain = 0.15f + r.unit() * 0.25f;
            int pos = st;
            for (int k = 0; k < notes; k++) {
                int nl = n(0.04f + r.unit() * 0.08f);
                double ph = 0;
                float fa = f0 * (kind == 1 && k == 1 ? 0.8f : 1f), fb = fa * (kind == 2 ? 1.25f : 0.85f + r.unit() * 0.4f);
                for (int i = 0; i < nl && pos + i < len; i++) {
                    float t = i / (float) nl;
                    ph += 2 * Math.PI * (fa + (fb - fa) * t) / R;
                    x[pos + i] += (float) (Math.sin(ph) * Math.sin(Math.PI * t)) * gain;
                }
                pos += nl + n(0.02f + r.unit() * 0.05f);
            }
        }
        return pcm(seamless(x, 0.3f), 0.35f, 0);
    }

    private static short[] crickets(int seed) {
        Rng r = new Rng(seed);
        int len = n(6f);
        float[] x = new float[len];
        for (int c = 0; c < 3; c++) {
            float f0 = 4200f + c * 380f, rate = 3.5f + r.unit() * 2f, off = r.unit();
            for (int i = 0; i < len; i++) {
                float t = i / (float) R;
                float chirp = ((t * rate + off) % 1f) < 0.35f ? 1f : 0f;
                float trill = (float) (0.5 + 0.5 * Math.sin(2 * Math.PI * 34 * t));
                x[i] += (float) Math.sin(2 * Math.PI * f0 * t) * chirp * trill * (0.3f + 0.15f * c);
            }
        }
        lowpass(x, 6000f);
        return pcm(seamless(x, 0.2f), 0.25f, 0);
    }

    private static short[] thunder(float seconds, boolean near, int seed) {
        Rng r = new Rng(seed);
        int len = n(seconds);
        float[] x = noise(len, seed);
        lowpass(x, near ? 220f : 120f);
        lowpass(x, near ? 300f : 160f);
        float[] crack = noise(len, seed + 1);
        highpass(crack, 900f);
        for (int i = 0; i < len; i++) {
            float t = i / (float) len;
            // rolling: several swells as the sound arrives from along the bolt
            float roll = (float) (0.6 + 0.4 * Math.sin(t * 17 + Math.sin(t * 5) * 2));
            float e = t < 0.02f ? t / 0.02f : (float) Math.pow(1f - t, 1.3f);
            x[i] = x[i] * e * roll * 3f;
            if (near) x[i] += crack[i] * (float) Math.exp(-t * seconds * 9) * 0.9f;
        }
        return pcm(x, near ? 0.95f : 0.6f, 0);
    }

    // ------------------------------------------------------------------ interface

    /** Square-wave blips (pulse width 25%), the classic menu sound. */
    static short[] blips(float[] notes, float each, float gain) {
        int nl = n(each), len = nl * notes.length + n(0.03f);
        float[] x = new float[len];
        for (int k = 0; k < notes.length; k++)
            for (int i = 0; i < nl; i++) {
                float t = i / (float) R;
                float ph = (notes[k] * t) % 1f;
                float sq = ph < 0.25f ? 1f : -0.35f;
                float e = Math.min(1f, i / 30f) * (1f - i / (float) nl * 0.6f);
                x[k * nl + i] += sq * e;
            }
        lowpass(x, 7000f);
        return pcm(x, gain, 32);
    }

    static short[] fanfare(float[] notes, float each, float gain) {
        int nl = n(each), tail = n(0.45f), len = nl * notes.length + tail;
        float[] x = new float[len];
        for (int k = 0; k < notes.length; k++) {
            boolean last = k == notes.length - 1;
            int l = last ? nl + tail : nl;
            for (int i = 0; i < l; i++) {
                float t = i / (float) R;
                float ph = (notes[k] * t) % 1f;
                float sq = (ph < 0.5f ? 1f : -1f) * 0.5f + (float) Math.sin(2 * Math.PI * notes[k] * 0.5f * t) * 0.4f;
                float e = Math.min(1f, i / 40f) * (last ? (float) Math.pow(1f - i / (float) l, 1.5f) : 0.9f);
                x[k * nl + i] += sq * e;
            }
        }
        lowpass(x, 5000f);
        return pcm(x, gain, 48);
    }

    private static short[] sweep(float f0, float f1, float seconds, float gain) {
        int len = n(seconds);
        float[] x = new float[len];
        double ph = 0;
        for (int i = 0; i < len; i++) {
            float t = i / (float) len;
            ph += (f0 + (f1 - f0) * t) / R;
            x[i] = ((ph % 1.0) < 0.5 ? 1f : -1f) * (1f - t) * Math.min(1f, i / 40f);
        }
        lowpass(x, 5000f);
        return pcm(x, gain, 32);
    }
}
