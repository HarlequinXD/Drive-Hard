package com.harlequinxd.drivehard.core.audio;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The sound mixer: 22 050 Hz stereo, the rate of the PS1 / GBA era. The game thread sets
 * parameters and queues one-shots; the platform's audio thread calls {@link #render} to pull
 * finished blocks of 16-bit samples.
 *
 * <ul>
 * <li>One-shots: a sample played once, with volume, pan and pitch (linear-interpolated).</li>
 * <li>Loops: persistent voices (tyres, wind, rain, surf...) whose volume, pan and pitch the
 *   game changes every frame; they glide to their targets so nothing clicks.</li>
 * <li>Engines: live synthesizers (firing pulses, exhaust noise, a throttle-opened filter and a
 *   little drive), so the revs follow the car exactly instead of a looped recording.</li>
 * <li>A small reverb (four combs and two all-passes per side, like the PS1's SPU reverb) gives
 *   space; its send is set per place (more between buildings, less in the open).</li>
 * <li>A soft limiter on the output: crashes over a full engine never clip harshly.</li>
 * </ul>
 */
public final class Mixer {
    public static final int RATE = 22050;

    /** A playing sample. */
    private static final class Voice {
        short[] s;
        double pos, step;
        float l, r, rev;
        boolean dead;
    }

    /** A looping voice the game steers. */
    public static final class Loop {
        final short[] s;
        double pos;
        /** Targets, set by the game. */
        public volatile float volume, pan, pitch = 1f, reverb = 0.3f;
        float cl, cr, cp = 1f;

        Loop(short[] s) { this.s = s; }
    }

    /** A live engine sound. */
    public static final class Engine {
        /** Targets, set by the game: revs per minute, throttle 0..1, volume, pan, character. */
        public volatile float rpm = 900f, load, volume, pan, reverb = 0.25f;
        /** 4 for an in-line four, 6 for a six (deeper and smoother), 3 for a kei car (rattly). */
        public volatile int cylinders = 4;
        float crpm = 900f, cload, cvol, cpan;
        double phase, sub;
        float lp, lp2, rumble;
        int seed = 12345;
    }

    private final ConcurrentLinkedQueue<Voice> incoming = new ConcurrentLinkedQueue<>();
    private final Voice[] voices = new Voice[40];
    private final Loop[] loops = new Loop[24];
    private int loopCount;
    private final Engine[] engines = new Engine[6];
    private int engineCount;
    /** Master, effects and music volumes 0..1 (the options menu). */
    public volatile float master = 0.9f, sfx = 1f, musicLevel = 0.8f;
    /** The theme (its own volume glides; see {@link Music#volume}). */
    public final Music music = new Music();
    /** While true, loops and engines fade out (one-shots such as menu blips still play). */
    public volatile boolean worldMuted;
    private float worldGain = 1f;

    // reverb (per side: four combs in parallel, then two all-passes)
    private static final int[] COMB = {1116, 1188, 1277, 1356}, ALLP = {556, 441};
    private final float[][] combL = new float[4][], combR = new float[4][], apL = new float[2][], apR = new float[2][];
    private final int[] combIdx = new int[4], apIdx = new int[2];
    private final float[] combLpL = new float[4], combLpR = new float[4];

    public Mixer() {
        for (int i = 0; i < 4; i++) {
            // scaled from 44.1 kHz to our rate; the right side a little longer (stereo width)
            combL[i] = new float[COMB[i] / 2];
            combR[i] = new float[COMB[i] / 2 + 12];
        }
        for (int i = 0; i < 2; i++) {
            apL[i] = new float[ALLP[i] / 2];
            apR[i] = new float[ALLP[i] / 2 + 7];
        }
    }

    /** A new looping voice (create them all before the audio thread starts). */
    public Loop loop(short[] s) {
        Loop l = new Loop(s);
        loops[loopCount++] = l;
        return l;
    }

    /** A new engine voice (create them all before the audio thread starts). */
    public Engine engine() {
        Engine e = new Engine();
        e.seed += engineCount * 7919;
        engines[engineCount++] = e;
        return e;
    }

    /** Plays a sample once: volume 0..1, pan -1 (left) .. 1 (right), pitch 1 = as recorded, reverb send 0..1. */
    public void play(short[] s, float volume, float pan, float pitch, float reverb) {
        if (s == null || volume <= 0.001f) return;
        Voice v = new Voice();
        v.s = s;
        v.step = Math.max(0.05, pitch);
        float p = Math.max(-1f, Math.min(1f, pan));
        v.l = volume * (float) Math.sqrt(0.5f * (1f - p));
        v.r = volume * (float) Math.sqrt(0.5f * (1f + p));
        v.rev = reverb;
        incoming.add(v);
    }

    public void play(short[] s, float volume) { play(s, volume, 0f, 1f, 0.2f); }

    private float[] mixL = new float[0], mixR = new float[0], sendL = new float[0], sendR = new float[0];

    /** Fills {@code out} with {@code frames} stereo frames (interleaved, 16-bit). Audio thread only. */
    public void render(short[] out, int frames) {
        if (mixL.length < frames) {
            mixL = new float[frames]; mixR = new float[frames];
            sendL = new float[frames]; sendR = new float[frames];
        }
        float[] ml = mixL, mr = mixR, sl = sendL, sr = sendR;
        java.util.Arrays.fill(ml, 0, frames, 0f);
        java.util.Arrays.fill(mr, 0, frames, 0f);
        java.util.Arrays.fill(sl, 0, frames, 0f);
        java.util.Arrays.fill(sr, 0, frames, 0f);
        for (Voice v; (v = incoming.poll()) != null; ) {
            for (int i = 0; i < voices.length; i++) if (voices[i] == null) { voices[i] = v; v = null; break; }
            if (v != null) {
                // full: replace the quietest
                int q = 0;
                for (int i = 1; i < voices.length; i++) if (voices[i].l + voices[i].r < voices[q].l + voices[q].r) q = i;
                voices[q] = v;
            }
        }
        // the world fades with the pause menu
        float wantWorld = worldMuted ? 0f : 1f;
        float wStep = 1f / (RATE * 0.25f);

        for (int i = 0; i < voices.length; i++) {
            Voice v = voices[i];
            if (v == null) continue;
            short[] s = v.s;
            int n = s.length;
            for (int f = 0; f < frames; f++) {
                int ip = (int) v.pos;
                if (ip >= n - 1) { v.dead = true; break; }
                float t = (float) (v.pos - ip);
                float x = (s[ip] + (s[ip + 1] - s[ip]) * t) * (1f / 32768f);
                ml[f] += x * v.l; mr[f] += x * v.r;
                sl[f] += x * v.l * v.rev; sr[f] += x * v.r * v.rev;
                v.pos += v.step;
            }
            if (v.dead) voices[i] = null;
        }

        for (int i = 0; i < loopCount; i++) {
            Loop lp = loops[i];
            short[] s = lp.s;
            int n = s.length;
            float tv = Math.max(0f, lp.volume), tp = Math.max(-1f, Math.min(1f, lp.pan));
            float tl = tv * (float) Math.sqrt(0.5f * (1f - tp)), tr = tv * (float) Math.sqrt(0.5f * (1f + tp));
            float tpi = Math.max(0.1f, lp.pitch), rv = lp.reverb;
            if (tl + tr + lp.cl + lp.cr < 1e-4f) { lp.cl = tl; lp.cr = tr; continue; }
            float g = worldGain;
            for (int f = 0; f < frames; f++) {
                lp.cl += (tl - lp.cl) * 0.002f;
                lp.cr += (tr - lp.cr) * 0.002f;
                lp.cp += (tpi - lp.cp) * 0.003f;
                int ip = (int) lp.pos;
                int ip2 = ip + 1 >= n ? 0 : ip + 1;
                float t = (float) (lp.pos - ip);
                float x = (s[ip] + (s[ip2] - s[ip]) * t) * (1f / 32768f) * g;
                ml[f] += x * lp.cl; mr[f] += x * lp.cr;
                sl[f] += x * lp.cl * rv; sr[f] += x * lp.cr * rv;
                lp.pos += lp.cp;
                if (lp.pos >= n) lp.pos -= n;
                g += (wantWorld - g) * wStep * 4f;
            }
        }

        for (int i = 0; i < engineCount; i++) engine(engines[i], frames, ml, mr, sl, sr);
        float fx = sfx;
        if (fx != 1f) for (int f = 0; f < frames; f++) { ml[f] *= fx; mr[f] *= fx; sl[f] *= fx; sr[f] *= fx; }
        music.render(ml, mr, sl, sr, frames, musicLevel);

        // the world gain follows the pause menu over the block
        worldGain += (wantWorld - worldGain) * Math.min(1f, frames * wStep);

        // reverb
        for (int f = 0; f < frames; f++) {
            float inL = sl[f] * 0.3f, inR = sr[f] * 0.3f;
            float accL = 0f, accR = 0f;
            for (int c = 0; c < 4; c++) {
                float[] bl = combL[c], br = combR[c];
                int il = combIdx[c] % bl.length, ir = combIdx[c] % br.length;
                float yl = bl[il], yr = br[ir];
                combLpL[c] = yl * 0.62f + combLpL[c] * 0.38f;          // damping: darker tails
                combLpR[c] = yr * 0.62f + combLpR[c] * 0.38f;
                bl[il] = inL + combLpL[c] * 0.80f;
                br[ir] = inR + combLpR[c] * 0.80f;
                accL += yl; accR += yr;
                combIdx[c]++;
            }
            for (int a = 0; a < 2; a++) {
                float[] bl = apL[a], br = apR[a];
                int il = apIdx[a] % bl.length, ir = apIdx[a] % br.length;
                float bL = bl[il], bR = br[ir];
                bl[il] = accL + bL * 0.5f; br[ir] = accR + bR * 0.5f;
                accL = bL - accL; accR = bR - accR;
                apIdx[a]++;
            }
            ml[f] += accL * 0.22f;
            mr[f] += accR * 0.22f;
        }
        for (int c = 0; c < 4; c++) if (combIdx[c] > 1 << 30) combIdx[c] = 0;
        for (int a = 0; a < 2; a++) if (apIdx[a] > 1 << 30) apIdx[a] = 0;

        // master and a soft limiter
        float m = master;
        for (int f = 0; f < frames; f++) {
            out[f * 2] = (short) (soft(ml[f] * m) * 32000f);
            out[f * 2 + 1] = (short) (soft(mr[f] * m) * 32000f);
        }
    }

    private static float soft(float x) {
        if (x > -0.6f && x < 0.6f) return x;
        float a = Math.abs(x);
        float y = 0.6f + (a - 0.6f) / (1f + (a - 0.6f) * 2.5f);
        return Math.copySign(Math.min(0.98f, y), x);
    }

    /** One engine: firing pulses at the cylinders' rate, exhaust noise, a throttle-opened low-pass and drive. */
    private void engine(Engine e, int frames, float[] ml, float[] mr, float[] sl, float[] sr) {
        float tv = Math.max(0f, e.volume) * worldGain;
        if (tv < 1e-4f && e.cvol < 1e-4f) { e.cvol = 0f; return; }
        float trpm = Math.max(400f, Math.min(9000f, e.rpm)), tload = Math.max(0f, Math.min(1f, e.load));
        float tpan = Math.max(-1f, Math.min(1f, e.pan)), rv = e.reverb;
        int cyl = e.cylinders;
        for (int f = 0; f < frames; f++) {
            e.crpm += (trpm - e.crpm) * 0.0016f;
            e.cload += (tload - e.cload) * 0.0012f;
            e.cvol += (tv - e.cvol) * 0.0015f;
            e.cpan += (tpan - e.cpan) * 0.001f;
            float load = e.cload;
            // firing frequency: a four-stroke fires each cylinder once every two turns
            double fire = e.crpm / 60.0 * cyl * 0.5;
            e.phase += fire / RATE;
            if (e.phase >= 1.0) e.phase -= 1.0;
            e.sub += fire / RATE / cyl;               // one turn of the whole cycle: uneven cylinders
            if (e.sub >= 1.0) e.sub -= 1.0;
            double ph = e.phase * Math.PI * 2;
            // a pulse per firing: sharp at the top, with harmonics that grow with load
            float pulse = (float) Math.pow(0.5 + 0.5 * Math.cos(ph), 3.0 + load * 5.0);
            float body = (float) (Math.sin(ph) * 0.45 + Math.sin(ph * 2) * (0.22 + load * 0.15) + Math.sin(ph * 3) * 0.10 * load);
            float lope = (float) Math.sin(e.sub * Math.PI * 2) * 0.18f;           // the cylinders aren't identical
            e.seed = e.seed * 1103515245 + 12345;
            float noise = ((e.seed >> 9) & 0x7FFF) / 16384f - 1f;
            float x = body * (0.7f + lope) + (pulse - 0.25f) * (0.6f + load * 0.6f) + noise * pulse * (0.25f + load * 0.55f);
            // intake / exhaust filter opens with the throttle and revs
            float cut = Math.min(0.9f, (350f + load * 2600f + e.crpm * 0.35f) / RATE * 6.28f);
            e.lp += (x - e.lp) * cut;
            e.lp2 += (e.lp - e.lp2) * cut;
            e.rumble += (noise - e.rumble) * 0.02f;
            float y = e.lp2 + e.rumble * 0.25f;
            // a little drive: growl under load
            float d = 1.2f + load * 2.2f;
            y = (float) Math.tanh(y * d) * (0.75f / (float) Math.tanh(d));
            // 8-bit-era grit: a gentle quantize
            y = Math.round(y * 96f) / 96f;
            float v = e.cvol;
            float l = v * (float) Math.sqrt(0.5f * (1f - e.cpan)), r = v * (float) Math.sqrt(0.5f * (1f + e.cpan));
            ml[f] += y * l; mr[f] += y * r;
            sl[f] += y * l * rv; sr[f] += y * r * rv;
        }
    }
}
