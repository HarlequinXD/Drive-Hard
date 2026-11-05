package com.harlequinxd.drivehard.core.model;

import static com.harlequinxd.drivehard.core.model.CharacterModel.*;

/**
 * Procedural keyframes for every baked animation.
 *
 * Conventions: thigh/upper-arm rx < 0 swings forward; shin rx > 0 bends the knee;
 * forearm rx < 0 bends the elbow; chest rx > 0 leans forward; left arm rz > 0 lifts it out
 * to the side (right arm uses the negative).
 */
public final class Animations {
    public enum Anim {
        IDLE(4, true), WALK(8, true), RUN(8, true), JUMP(4, false),
        /** In deep water (baked into their own wider sheet, anchored at the waterline). */
        SWIM_TREAD(8, true), SWIM_BREAST(8, true), SWIM_FREE(8, true);
        public final int frames;
        public final boolean loop;
        Anim(int frames, boolean loop) { this.frames = frames; this.loop = loop; }
    }

    /** Jump sub-frames. */
    public static final int JUMP_RISE = 0, JUMP_APEX = 1, JUMP_FALL = 2, JUMP_LAND = 3;

    /** The on-land animations (the main character sheet) and the swimming ones (the swim sheet). */
    public static final Anim[] LAND = {Anim.IDLE, Anim.WALK, Anim.RUN, Anim.JUMP};
    public static final Anim[] SWIM = {Anim.SWIM_TREAD, Anim.SWIM_BREAST, Anim.SWIM_FREE};

    /** First frame of an animation within the given sheet's animation list. */
    public static int firstFrame(Anim anim, Anim[] sheet) {
        int n = 0;
        for (Anim a : sheet) {
            if (a == anim) return n;
            n += a.frames;
        }
        return 0;
    }

    public static int totalFrames() {
        int n = 0;
        for (Anim a : Anim.values()) n += a.frames;
        return n;
    }

    public static int firstFrame(Anim anim) {
        int n = 0;
        for (Anim a : Anim.values()) {
            if (a == anim) return n;
            n += a.frames;
        }
        return 0;
    }

    public static Pose pose(Anim anim, int frame) {
        float p = frame / (float) anim.frames;
        switch (anim) {
            case IDLE: return idle(p);
            case WALK: return walk(p);
            case RUN: return run(p);
            case SWIM_TREAD: return tread(p);
            case SWIM_BREAST: return breaststroke(p);
            case SWIM_FREE: return freestyle(p);
            default: return jump(frame);
        }
    }

    private static float pos(float v) { return v > 0 ? v : 0; }

    private static void arms(Pose o, float rxL, float rxR, float out, float elbowL, float elbowR) {
        o.rx[UARM_L] = rxL; o.rx[UARM_R] = rxR;
        o.rz[UARM_L] = out; o.rz[UARM_R] = -out;
        o.rx[FARM_L] = elbowL; o.rx[FARM_R] = elbowR;
    }

    private static void legs(Pose o, float thL, float knL, float thR, float knR, float flat) {
        o.rx[THIGH_L] = thL; o.rx[SHIN_L] = knL; o.rx[FOOT_L] = -(thL + knL) * flat;
        o.rx[THIGH_R] = thR; o.rx[SHIN_R] = knR; o.rx[FOOT_R] = -(thR + knR) * flat;
    }

    static Pose idle(float p) {
        Pose o = new Pose();
        float w = (float) (Math.PI * 2 * p), s = (float) Math.sin(w);
        o.rx[CHEST] = 0.02f + 0.015f * s;
        o.rx[HEAD] = -0.02f - 0.012f * s;
        arms(o, 0.03f, 0.03f, 0.09f + 0.012f * s, -0.14f, -0.14f);
        legs(o, 0.0f, 0.05f, 0.0f, 0.05f, 1f);
        o.rz[THIGH_L] = 0.035f; o.rz[THIGH_R] = -0.035f;
        o.rz[FOOT_L] = -0.035f; o.rz[FOOT_R] = 0.035f;
        o.rz[PELVIS] = 0.012f * s;
        o.rx[PONY] = 0.08f;
        return o;
    }

    static Pose walk(float p) {
        Pose o = new Pose();
        float w = (float) (Math.PI * 2 * p), s = (float) Math.sin(w), c = (float) Math.cos(w);
        float thL = -0.42f * s, thR = 0.42f * s;
        float knL = 0.08f + 0.78f * (float) Math.pow(pos(c), 1.3);
        float knR = 0.08f + 0.78f * (float) Math.pow(pos(-c), 1.3);
        legs(o, thL, knL, thR, knR, 0.9f);
        arms(o, 0.32f * s, -0.32f * s, 0.07f, -0.2f - 0.25f * pos(-s), -0.2f - 0.25f * pos(s));
        o.ry[PELVIS] = -0.1f * s;
        o.rz[PELVIS] = 0.03f * c;
        o.ry[CHEST] = 0.18f * s;
        o.rx[CHEST] = 0.04f;
        o.ry[HEAD] = -0.08f * s;
        o.rx[HEAD] = -0.03f;
        o.rx[PONY] = 0.15f + 0.06f * (float) Math.cos(2 * w);
        o.rz[PONY] = 0.06f * s;
        return o;
    }

    static Pose run(float p) {
        Pose o = new Pose();
        float w = (float) (Math.PI * 2 * p), s = (float) Math.sin(w);
        float thL = -0.25f - 0.7f * s, thR = -0.25f + 0.7f * s;
        float knL = 0.35f + 1.35f * (float) Math.pow(pos((float) Math.cos(w - 0.5f)), 1.2);
        float knR = 0.35f + 1.35f * (float) Math.pow(pos((float) Math.cos(w + Math.PI - 0.5f)), 1.2);
        legs(o, thL, knL, thR, knR, 0.6f);
        o.rx[FOOT_L] += 0.15f; o.rx[FOOT_R] += 0.15f;
        arms(o, 0.65f * s - 0.15f, -0.65f * s - 0.15f, 0.15f, -1.3f, -1.3f);
        o.rx[PELVIS] = 0.06f;
        o.ry[PELVIS] = -0.15f * s;
        o.rx[CHEST] = 0.2f;
        o.ry[CHEST] = 0.28f * s;
        o.rx[HEAD] = -0.2f;
        o.ry[HEAD] = -0.13f * s;
        o.rx[PONY] = 0.5f + 0.15f * (float) Math.cos(2 * w);
        o.rz[PONY] = 0.12f * s;
        return o;
    }

    static Pose jump(int f) {
        Pose o = new Pose();
        o.grounded = false;
        switch (f) {
            case JUMP_RISE:
                legs(o, -0.55f, 0.9f, 0.25f, 0.35f, 0.4f);
                o.rx[FOOT_R] = 0.35f;
                arms(o, -1.9f, -1.6f, 0.25f, -0.4f, -0.4f);
                o.rx[CHEST] = 0.05f; o.rx[HEAD] = -0.1f;
                o.rx[PONY] = 0.0f;
                break;
            case JUMP_APEX:
                legs(o, -1.0f, 1.5f, -0.6f, 1.3f, 0.6f);
                arms(o, -0.6f, -0.5f, 1.0f, -0.5f, -0.5f);
                o.rx[CHEST] = 0.15f; o.rx[HEAD] = -0.1f;
                o.rx[PONY] = 0.6f;
                break;
            case JUMP_FALL:
                legs(o, -0.4f, 0.5f, -0.1f, 0.25f, 0.6f);
                arms(o, -0.3f, -0.25f, 1.4f, -0.3f, -0.3f);
                o.rx[CHEST] = -0.05f;
                o.rx[PONY] = 0.75f;
                break;
            default: // landing crouch
                o.grounded = true;
                legs(o, -0.95f, 1.55f, -0.95f, 1.55f, 1f);
                o.rz[THIGH_L] = 0.06f; o.rz[THIGH_R] = -0.06f;
                arms(o, -0.5f, -0.5f, 0.25f, -0.6f, -0.6f);
                o.rx[CHEST] = 0.4f; o.rx[HEAD] = -0.3f;
                o.rx[PONY] = 0.3f;
                break;
        }
        return o;
    }

    // ------------------------------------------------------------------ swimming
    // Swim poses are not grounded: the pelvis is placed relative to the sheet's anchor, which the
    // game puts on the water surface, so the water hides whatever is below it.

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    /** Treading water: upright, head and shoulders out, hands sculling, legs egg-beater kicking. */
    static Pose tread(float p) {
        Pose o = new Pose();
        o.grounded = false;
        float w = (float) (Math.PI * 2 * p), s = (float) Math.sin(w), c = (float) Math.cos(w);
        o.rootY = -0.95f - 0.4f + 0.04f * s;              // head rides just above the surface, bobbing
        o.rx[PELVIS] = 0.18f;
        o.rx[CHEST] = 0.05f;
        o.rx[HEAD] = -0.2f;
        // hands sweep in and out just under the surface
        o.rx[UARM_L] = -0.55f; o.rx[UARM_R] = -0.55f;
        o.rz[UARM_L] = 0.8f + 0.25f * s; o.rz[UARM_R] = -(0.8f + 0.25f * s);
        o.ry[UARM_L] = 0.3f * c; o.ry[UARM_R] = -0.3f * c;
        o.rx[FARM_L] = -0.5f; o.rx[FARM_R] = -0.5f;
        // egg-beater: sitting thighs, knees circling in opposition
        o.rx[THIGH_L] = -0.9f + 0.15f * s; o.rx[THIGH_R] = -0.9f - 0.15f * s;
        o.rz[THIGH_L] = 0.35f; o.rz[THIGH_R] = -0.35f;
        o.rx[SHIN_L] = 1.3f + 0.4f * c; o.rx[SHIN_R] = 1.3f - 0.4f * c;
        o.rx[FOOT_L] = 0.5f; o.rx[FOOT_R] = 0.5f;
        o.rx[PONY] = 0.25f;
        return o;
    }

    /**
     * Breaststroke: glide with arms out in front, sweep out and pull in under the chest (the head
     * lifts to breathe), hands shoot forward again while the legs draw up and frog-kick back.
     */
    static Pose breaststroke(float p) {
        Pose o = new Pose();
        o.grounded = false;
        float pull = smooth(0.0f, 0.3f, p) - smooth(0.3f, 0.5f, p);       // 0 glide .. 1 hands at chest
        float sweep = smooth(0.0f, 0.2f, p) * (1f - smooth(0.2f, 0.38f, p)); // arms wide
        float draw = smooth(0.3f, 0.55f, p) * (1f - smooth(0.6f, 0.75f, p)); // knees drawn up
        float kick = smooth(0.58f, 0.68f, p) * (1f - smooth(0.7f, 0.85f, p)); // legs snap round and back
        float lift = pull;
        o.rootY = -0.95f - 0.32f + 0.08f * lift;
        o.rootZ = -0.3f;
        o.rx[PELVIS] = 1.36f - 0.12f * lift;               // inclined, a little more upright when breathing
        o.rx[CHEST] = -0.08f - 0.12f * lift;
        o.rx[HEAD] = -0.95f - 0.25f * lift;                 // looking forward over the water
        // arms: forward (overhead along the body) -> wide -> elbows in under the chest -> forward
        float ext = -2.95f + 1.35f * pull;
        o.rx[UARM_L] = ext; o.rx[UARM_R] = ext;
        o.rz[UARM_L] = 0.12f + 0.9f * sweep + 0.3f * pull; o.rz[UARM_R] = -(0.12f + 0.9f * sweep + 0.3f * pull);
        o.rx[FARM_L] = -0.1f - 1.6f * pull; o.rx[FARM_R] = -0.1f - 1.6f * pull;
        // legs: frog kick
        o.rx[THIGH_L] = -0.15f - 0.75f * draw; o.rx[THIGH_R] = -0.15f - 0.75f * draw;
        o.rz[THIGH_L] = 0.08f + 0.35f * draw + 0.45f * kick; o.rz[THIGH_R] = -(0.08f + 0.35f * draw + 0.45f * kick);
        o.rx[SHIN_L] = 0.15f + 1.75f * draw + 0.4f * kick; o.rx[SHIN_R] = 0.15f + 1.75f * draw + 0.4f * kick;
        o.rx[FOOT_L] = 0.9f - 0.9f * draw; o.rx[FOOT_R] = 0.9f - 0.9f * draw;
        o.rz[FOOT_L] = -0.4f * draw; o.rz[FOOT_R] = 0.4f * draw;
        o.rx[PONY] = 1.2f;
        return o;
    }

    /**
     * Front crawl: almost flat, the body rolling side to side, arms windmilling (pull under the
     * body, recover over the back with a high elbow), a fast flutter kick, the head turning to
     * breathe every stroke cycle.
     */
    static Pose freestyle(float p) {
        Pose o = new Pose();
        o.grounded = false;
        float w = (float) (Math.PI * 2 * p);
        o.rootY = -0.95f - 0.02f;
        o.rootZ = -0.3f;
        o.rx[PELVIS] = 1.48f;
        float roll = 0.45f * (float) Math.sin(w);
        o.ry[PELVIS] = roll * 0.5f;
        o.ry[CHEST] = roll * 0.6f;
        o.rx[HEAD] = -0.25f;
        o.ry[HEAD] = 0.9f * (float) Math.pow(pos((float) Math.sin(w)), 3);   // breathe to one side
        // arm angle: -pi overhead, pulled through under the body to the hip (0), back over the top
        float aL = (float) (-Math.PI + Math.PI * 2 * p), aR = aL + (float) Math.PI;
        o.rx[UARM_L] = aL; o.rx[UARM_R] = aR;
        o.rz[UARM_L] = 0.15f; o.rz[UARM_R] = -0.15f;
        // high elbow on the recovery (angles 0..pi, over the back)
        float recL = (float) Math.sin(((aL % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI));
        float recR = (float) Math.sin(((aR % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI));
        o.rx[FARM_L] = -0.25f - 1.4f * pos(recL) - 0.4f * pos(-recL);
        o.rx[FARM_R] = -0.25f - 1.4f * pos(recR) - 0.4f * pos(-recR);
        o.rz[UARM_L] += 0.5f * pos(recL); o.rz[UARM_R] -= 0.5f * pos(recR);
        // flutter kick: three beats per arm cycle
        float k = (float) Math.sin(w * 3f);
        o.rx[THIGH_L] = 0.05f + 0.16f * k; o.rx[THIGH_R] = 0.05f - 0.16f * k;
        o.rx[SHIN_L] = 0.08f + 0.22f * pos(-k); o.rx[SHIN_R] = 0.08f + 0.22f * pos(k);
        o.rx[FOOT_L] = 1.0f; o.rx[FOOT_R] = 1.0f;
        o.rx[PONY] = 1.4f;
        return o;
    }
}
