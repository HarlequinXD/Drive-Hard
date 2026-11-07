package com.harlequinxd.drivehard.core.audio;

/**
 * The theme: "Drive Hard", a 70s-cop-show funk groove in A minor at 104 BPM, the way the Driver
 * games opened, played live by a little sequencer and synth (no recordings).
 *
 * <p>Instruments: drums (synthesized once: kick, snare, hats, clap), a filtered synth bass, an
 * electric piano comping the chords (Am7 - D7 - Fmaj7 - E7), a string pad, brass stabs and a
 * pulse-wave lead with delayed vibrato. Sections: intro (pad and hats, then the band comes in),
 * groove, the hook, a breakdown with a brass riser, the hook again, then back to the groove.</p>
 */
public final class Music {
    private static final int R = Mixer.RATE;
    private static final float BPM = 104f;
    private static final double STEP = R * 60.0 / BPM / 4.0;          // samples per 16th note

    /** Target volume 0..1 (glides). */
    public volatile float volume = 0f;
    float cvol;

    // the arrangement: bars of each section
    private static final int INTRO = 0, GROOVE = 1, HOOK = 2, BREAK = 3;
    private static final int[] SONG = {
            INTRO, INTRO, INTRO, INTRO,
            GROOVE, GROOVE, GROOVE, GROOVE, GROOVE, GROOVE, GROOVE, GROOVE,
            HOOK, HOOK, HOOK, HOOK, HOOK, HOOK, HOOK, HOOK,
            BREAK, BREAK, BREAK, BREAK,
            HOOK, HOOK, HOOK, HOOK, HOOK, HOOK, HOOK, HOOK};
    private static final int LOOP_TO = 4;                               // after the end, back to the groove

    // chords, one a bar: root (MIDI) and voicing (semitones over the root, played an octave up)
    private static final int[] ROOT = {45, 50, 41, 40};                 // A2, D3, F2, E2
    private static final int[][] VOICING = {{12, 15, 19, 22}, {12, 16, 19, 22}, {12, 16, 19, 23}, {12, 16, 20, 23}};

    // bass: per 16th, semitones over the root (-99 rest), and accents
    private static final int[] BASS = {0, -99, -99, 0, -99, -99, 12, 0, -99, 0, -99, -99, 7, -99, 10, 12};
    private static final int[] BASS_BREAK = {0, -99, -99, -99, -99, -99, -99, -99, 0, -99, -99, 0, -99, -99, -99, -99};
    // drums per 16th: k kick, s snare, g ghost, h hat, o open hat, c clap
    private static final String DRUMS = "k-h-shkgh-k-sghoh";
    private static final String DRUMS_A = "k.h.s.hgkhh.s.og";
    private static final String DRUMS_INTRO = "--h---h---h---ho";
    private static final String DRUMS_BREAK = "k-------k--k----";
    // electric piano comping: steps with a stab
    private static final int[] COMP = {2, 6, 10, 13};

    // the hook: 8 bars of (step in bar, MIDI note, length in steps)
    private static final int[][][] LEAD = {
            {{0, 76, 2}, {2, 79, 2}, {4, 81, 4}, {8, 79, 2}, {10, 76, 2}, {12, 74, 4}},
            {{0, 78, 3}, {3, 76, 3}, {6, 74, 2}, {8, 69, 6}},
            {{0, 72, 2}, {2, 76, 2}, {4, 77, 2}, {6, 81, 4}, {10, 79, 2}, {12, 77, 2}, {14, 76, 2}},
            {{0, 80, 4}, {4, 83, 4}, {8, 86, 2}, {10, 83, 2}, {12, 80, 4}},
            {{0, 81, 2}, {2, 84, 2}, {4, 81, 2}, {6, 79, 2}, {8, 76, 4}, {12, 74, 2}, {14, 76, 2}},
            {{0, 78, 4}, {4, 81, 4}, {8, 78, 2}, {10, 76, 2}, {12, 74, 4}},
            {{0, 76, 2}, {2, 77, 2}, {4, 81, 2}, {6, 84, 6}, {12, 83, 2}, {14, 81, 2}},
            {{0, 80, 6}, {6, 83, 2}, {8, 88, 8}}};

    // drums, synthesized once
    private final short[] kick, snare, ghost, hat, openHat, clap;

    // voices
    private static final int SAMPLE = 0, BASSV = 1, KEYS = 2, LEADV = 3, PAD = 4, BRASS = 5;

    private static final class V {
        int kind;
        short[] s;
        double pos, ph, ph2, ph3;
        float freq, vel, age, len, pan;
        float lp, band, low;
        boolean on;
    }

    private final V[] vs = new V[48];
    private double clock;
    private int stepIndex = -1, barIndex;
    private int noise = 777;

    public Music() {
        for (int i = 0; i < vs.length; i++) vs[i] = new V();
        kick = kick();
        snare = snare(1.0f, 11);
        ghost = snare(0.35f, 12);
        hat = hat(0.045f, 13);
        openHat = hat(0.22f, 14);
        clap = clap();
    }

    /** Back to the top of the song (the menu opening). */
    public void restart() {
        clock = 0;
        stepIndex = -1;
        barIndex = 0;
        for (V v : vs) v.on = false;
    }

    private static float midi(int m) { return (float) (440.0 * Math.pow(2.0, (m - 69) / 12.0)); }

    private V free() {
        V best = vs[0];
        for (V v : vs) { if (!v.on) return v; if (v.age > best.age) best = v; }
        return best;
    }

    private void note(int kind, float freq, float vel, float lenSec, float pan) {
        V v = free();
        v.kind = kind; v.freq = freq; v.vel = vel; v.len = lenSec; v.pan = pan;
        v.age = 0f; v.ph = v.ph2 = v.ph3 = 0; v.lp = v.band = v.low = 0f; v.on = true;
        v.s = null;
    }

    private void hit(short[] s, float vel, float pan) {
        V v = free();
        v.kind = SAMPLE; v.s = s; v.pos = 0; v.vel = vel; v.pan = pan; v.age = 0f; v.on = true;
    }

    private void step(int bar, int st) {
        int section = SONG[bar];
        int chord = bar % 4;
        int root = ROOT[chord];
        float stepSec = (float) (STEP / R);
        // drums
        String d = section == INTRO ? (bar < 2 ? DRUMS_INTRO : DRUMS_A) : section == BREAK ? DRUMS_BREAK : (bar % 2 == 0 ? DRUMS_A : DRUMS);
        char c = st < d.length() ? d.charAt(st) : '-';
        switch (c) {
            case 'k': hit(kick, 1f, 0f); break;
            case 's': hit(snare, 0.85f, 0.05f); break;
            case 'g': hit(ghost, 0.45f, 0.05f); break;
            case 'h': hit(hat, 0.38f + (st % 4 == 2 ? 0.1f : 0f), -0.3f); break;
            case 'o': hit(openHat, 0.4f, -0.3f); break;
            case 'c': hit(clap, 0.7f, 0.1f); break;
            default: break;
        }
        if (section != INTRO && st == 4 && bar % 4 == 3) hit(clap, 0.55f, 0.15f);
        if (section == BREAK && bar == 23 && st >= 8 && st % 2 == 0) hit(snare, 0.4f + (st - 8) * 0.07f, 0f);   // the roll back in
        // bass
        if (section != INTRO || bar >= 2) {
            int[] pat = section == BREAK ? BASS_BREAK : BASS;
            if (pat[st] > -99) note(BASSV, midi(root + pat[st] - 12 + 12), st == 0 ? 1f : 0.8f, stepSec * (st == 0 ? 2.2f : 1.4f), 0f);
        }
        // pad on the intro and the breakdown, a bar long
        if ((section == INTRO || section == BREAK) && st == 0)
            for (int k = 0; k < 4; k++) note(PAD, midi(root + VOICING[chord][k]), 0.28f, stepSec * 16f, k % 2 == 0 ? -0.4f : 0.4f);
        // keys comping in the groove and the hook
        if (section == GROOVE || section == HOOK) {
            for (int cs : COMP) if (cs == st)
                for (int k = 0; k < 4; k++) note(KEYS, midi(root + VOICING[chord][k]), st == 2 ? 0.55f : 0.42f, stepSec * 1.6f, 0.35f);
        }
        // brass stabs: hits on the groove's last bar, a riser at the end of the breakdown
        if (section == GROOVE && bar % 4 == 3 && (st == 12 || st == 14))
            for (int k = 0; k < 3; k++) note(BRASS, midi(root + VOICING[chord][k + 1]), 0.5f, stepSec * 1.3f, -0.2f);
        if (section == BREAK && bar == 23 && st == 0)
            for (int k = 0; k < 3; k++) note(BRASS, midi(root + VOICING[chord][k + 1]), 0.55f, stepSec * 16f, -0.2f);
        // the lead
        if (section == HOOK) {
            int hb = (bar - (bar >= 24 ? 24 : 12)) % 8;
            for (int[] n : LEAD[hb]) if (n[0] == st) note(LEADV, midi(n[1]), 0.6f, stepSec * n[2] * 0.95f, 0.1f);
        }
    }

    /** Adds the music into the mix (and its reverb send). */
    void render(float[] ml, float[] mr, float[] sl, float[] sr, int frames, float gainIn) {
        float target = Math.max(0f, Math.min(1f, volume));
        if (target < 1e-4f && cvol < 1e-4f) { cvol = 0f; return; }
        for (int f = 0; f < frames; f++) {
            cvol += (target - cvol) * 0.0004f;
            int stepNow = (int) (clock / STEP);
            if (stepNow != stepIndex) {
                stepIndex = stepNow;
                int st = stepNow % 16;
                if (st == 0 && stepNow > 0) {
                    barIndex++;
                    if (barIndex >= SONG.length) barIndex = LOOP_TO;
                }
                step(barIndex, st);
            }
            clock += 1.0;
            float l = 0f, r = 0f, send = 0f;
            for (V v : vs) {
                if (!v.on) continue;
                float x = voice(v);
                if (!v.on) continue;
                float pl = (float) Math.sqrt(0.5f * (1f - v.pan)), pr = (float) Math.sqrt(0.5f * (1f + v.pan));
                l += x * pl; r += x * pr;
                if (v.kind == LEADV || v.kind == KEYS || v.kind == PAD || v.kind == BRASS) send += x * 0.6f;
                else if (v.kind == SAMPLE) send += x * 0.15f;
            }
            float g = cvol * gainIn * 0.55f;
            ml[f] += l * g; mr[f] += r * g;
            sl[f] += send * g; sr[f] += send * g;
        }
    }

    private float rnd() { noise = noise * 1103515245 + 12345; return ((noise >> 9) & 0x7FFF) / 16384f - 1f; }

    /** One sample of a voice (switches it off at its end). */
    private float voice(V v) {
        float t = v.age;
        v.age += 1f / R;
        switch (v.kind) {
            case SAMPLE: {
                int ip = (int) v.pos;
                if (ip >= v.s.length) { v.on = false; return 0f; }
                v.pos += 1.0;
                return v.s[ip] * (1f / 32768f) * v.vel;
            }
            case BASSV: {
                if (t > v.len + 0.05f) { v.on = false; return 0f; }
                v.ph += v.freq / R; if (v.ph >= 1) v.ph -= 1;
                v.ph2 += v.freq * 0.5f / R; if (v.ph2 >= 1) v.ph2 -= 1;
                float saw = (float) (v.ph * 2 - 1);
                float sub = (float) Math.sin(v.ph2 * Math.PI * 2);
                // a plucky filter: opens on the attack, closes fast
                float cut = 180f + 1700f * (float) Math.exp(-t * 22) * v.vel;
                float fq = (float) (2 * Math.sin(Math.PI * cut / R));
                v.low += fq * v.band;
                float hi = saw - v.low - 0.45f * v.band;
                v.band += fq * hi;
                float env = Math.min(1f, t * 400f) * (t > v.len ? Math.max(0f, 1f - (t - v.len) * 20f) : 1f);
                return (v.low * 0.75f + sub * 0.45f) * env * v.vel * 0.9f;
            }
            case KEYS: {
                if (t > v.len + 0.25f) { v.on = false; return 0f; }
                // an electric piano: a sine with a bell-like FM tine that fades fast
                v.ph += v.freq / R; if (v.ph >= 1) v.ph -= 1;
                double ph = v.ph * Math.PI * 2;
                float tine = (float) Math.exp(-t * 14);
                float x = (float) Math.sin(ph + Math.sin(ph * 2) * 1.1 * tine) * 0.7f + (float) Math.sin(ph * 2) * 0.12f;
                float env = Math.min(1f, t * 600f) * (float) Math.exp(-t * 3.5f) * (t > v.len ? Math.max(0f, 1f - (t - v.len) * 6f) : 1f);
                // tremolo
                env *= 0.85f + 0.15f * (float) Math.sin(t * 2 * Math.PI * 5.5);
                return x * env * v.vel * 0.32f;
            }
            case LEADV: {
                if (t > v.len + 0.12f) { v.on = false; return 0f; }
                float vib = t > 0.18f ? (float) Math.sin(t * 2 * Math.PI * 5.8) * 0.012f * Math.min(1f, (t - 0.18f) * 4f) : 0f;
                v.ph += v.freq * (1f + vib) / R; if (v.ph >= 1) v.ph -= 1;
                v.ph2 += v.freq * 1.004f * (1f + vib) / R; if (v.ph2 >= 1) v.ph2 -= 1;
                float pulse = (v.ph < 0.3 ? 1f : -1f) * 0.6f + (v.ph2 < 0.5 ? 1f : -1f) * 0.4f;
                float cut = 1800f + 2200f * (float) Math.exp(-t * 6);
                v.lp += (pulse - v.lp) * Math.min(1f, (float) (2 * Math.PI * cut / R));
                float env = Math.min(1f, t * 200f) * (t > v.len ? Math.max(0f, 1f - (t - v.len) * 9f) : 1f) * (0.8f + 0.2f * (float) Math.exp(-t * 3));
                return v.lp * env * v.vel * 0.42f;
            }
            case PAD: {
                if (t > v.len + 0.6f) { v.on = false; return 0f; }
                v.ph += v.freq * 0.997f / R; if (v.ph >= 1) v.ph -= 1;
                v.ph2 += v.freq * 1.003f / R; if (v.ph2 >= 1) v.ph2 -= 1;
                float saw = (float) ((v.ph * 2 - 1) + (v.ph2 * 2 - 1)) * 0.5f;
                v.lp += (saw - v.lp) * 0.09f;
                float env = Math.min(1f, t * 1.6f) * (t > v.len ? Math.max(0f, 1f - (t - v.len) * 1.7f) : 1f);
                return v.lp * env * v.vel * 0.5f;
            }
            case BRASS: {
                if (t > v.len + 0.08f) { v.on = false; return 0f; }
                v.ph += v.freq / R; if (v.ph >= 1) v.ph -= 1;
                float saw = (float) (v.ph * 2 - 1);
                float cut = 600f + 2600f * Math.min(1f, t * 14f) * (float) Math.exp(-t * 2.5f) + (v.len > 1f ? t * 900f : 0f);
                v.lp += (saw - v.lp) * Math.min(1f, (float) (2 * Math.PI * cut / R));
                float env = Math.min(1f, t * 60f) * (t > v.len ? Math.max(0f, 1f - (t - v.len) * 14f) : 1f);
                return v.lp * env * v.vel * 0.4f;
            }
            default: v.on = false; return 0f;
        }
    }

    // ------------------------------------------------------------------ drums

    private static short[] toPcm(float[] x, float gain) {
        float peak = 1e-6f;
        for (float v : x) peak = Math.max(peak, Math.abs(v));
        short[] o = new short[x.length];
        for (int i = 0; i < x.length; i++) o[i] = (short) (Math.round(x[i] / peak * gain * 512f) / 512f * 32767f);    // a hint of 8-bit-era grit
        return o;
    }

    private short[] kick() {
        int len = (int) (0.35f * R);
        float[] x = new float[len];
        double ph = 0;
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            ph += 2 * Math.PI * (48 + 110 * Math.exp(-t * 32)) / R;
            x[i] = (float) Math.sin(ph) * (float) Math.exp(-t * 9) + (i < 60 ? rnd() * 0.4f * (1f - i / 60f) : 0f);
        }
        return toPcm(x, 1f);
    }

    private short[] snare(float gain, int seed) {
        int len = (int) (0.22f * R);
        float[] x = new float[len];
        float lp = 0f;
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            float n = rnd();
            lp += (n - lp) * 0.45f;
            x[i] = (n - lp) * (float) Math.exp(-t * 18) * 0.9f + (float) Math.sin(2 * Math.PI * 185 * t) * (float) Math.exp(-t * 30) * 0.6f;
        }
        return toPcm(x, gain);
    }

    private short[] hat(float seconds, int seed) {
        int len = (int) (seconds * R);
        float[] x = new float[len];
        float lp = 0f;
        for (int i = 0; i < len; i++) {
            float t = i / (float) len;
            float n = rnd();
            lp += (n - lp) * 0.7f;
            x[i] = (n - lp) * (float) Math.pow(1f - t, 2.5f);
        }
        return toPcm(x, 0.6f);
    }

    private short[] clap() {
        int len = (int) (0.25f * R);
        float[] x = new float[len];
        float low = 0, band = 0, fq = (float) (2 * Math.sin(Math.PI * 1300f / R));
        for (int i = 0; i < len; i++) {
            float t = i / (float) R;
            float n = rnd();
            low += fq * band;
            float hi = n - low - 0.7f * band;
            band += fq * hi;
            float e = (float) Math.exp(-t * 20) + (t < 0.03f ? (float) Math.exp(-((t * 1000) % 10) * 0.6f) : 0f);
            x[i] = band * e;
        }
        return toPcm(x, 0.8f);
    }
}
