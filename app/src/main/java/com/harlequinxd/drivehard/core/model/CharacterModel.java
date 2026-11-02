package com.harlequinxd.drivehard.core.model;

import com.harlequinxd.drivehard.core.math.Mat4;
import com.harlequinxd.drivehard.core.model.CharacterSpec.Top;

import java.util.ArrayList;
import java.util.List;

/**
 * A procedurally modelled, skinned-by-rigid-parts human. Facing +Z, Y up, feet at y = 0.
 * The character's left side is +X.
 */
public final class CharacterModel {
    public static final int PELVIS = 0, CHEST = 1, HEAD = 2,
            UARM_L = 3, FARM_L = 4, UARM_R = 5, FARM_R = 6,
            THIGH_L = 7, SHIN_L = 8, FOOT_L = 9, THIGH_R = 10, SHIN_R = 11, FOOT_R = 12,
            PONY = 13, BONES = 14;

    public static final int[] PARENT = {-1, PELVIS, CHEST, CHEST, UARM_L, CHEST, UARM_R,
            PELVIS, THIGH_L, SHIN_L, PELVIS, THIGH_R, SHIN_R, HEAD};

    public final float[] offsets = new float[BONES * 3];
    public final List<Part> parts = new ArrayList<>();
    public final CharacterSpec spec;

    public CharacterModel(CharacterSpec spec) {
        this.spec = spec;
        build();
    }

    private void off(int b, float x, float y, float z) {
        offsets[b * 3] = x; offsets[b * 3 + 1] = y; offsets[b * 3 + 2] = z;
    }

    /** Computes per-bone world matrices for a pose. */
    public Mat4[] solve(Pose pose) {
        Mat4[] w = new Mat4[BONES];
        for (int b = 0; b < BONES; b++) {
            float ox = offsets[b * 3], oy = offsets[b * 3 + 1], oz = offsets[b * 3 + 2];
            if (b == PELVIS) { ox += pose.rootX; oy += pose.rootY; oz += pose.rootZ; }
            Mat4 local = Mat4.mul(Mat4.translation(ox, oy, oz),
                    Mat4.mul(Mat4.rotY(pose.ry[b]), Mat4.mul(Mat4.rotX(pose.rx[b]), Mat4.rotZ(pose.rz[b]))));
            w[b] = PARENT[b] < 0 ? local : Mat4.mul(w[PARENT[b]], local);
        }
        return w;
    }

    // ------------------------------------------------------------------ modelling

    private void build() {
        final CharacterSpec s = spec;
        final boolean f = s.female;
        final float k = s.height / (f ? 1.68f : 1.78f) * (f ? 1f : 1.06f);
        final float bw = 1f + s.build * 0.35f;              // girth multiplier
        final float hip = f ? 1.06f : 0.92f;
        final float sh = f ? 1.0f : 1.17f;
        final float bust = f ? 1f : 0f;

        off(PELVIS, 0, 0.95f * k, 0);
        off(CHEST, 0, 0.08f * k, 0);
        off(HEAD, 0, 0.40f * k, 0);
        off(UARM_L, 0.165f * k * sh, 0.335f * k, -0.01f * k);
        off(UARM_R, -0.165f * k * sh, 0.335f * k, -0.01f * k);
        off(FARM_L, 0, -0.27f * k, 0);
        off(FARM_R, 0, -0.27f * k, 0);
        off(THIGH_L, 0.085f * k * hip, -0.06f * k, 0);
        off(THIGH_R, -0.085f * k * hip, -0.06f * k, 0);
        off(SHIN_L, 0, -0.42f * k, 0);
        off(SHIN_R, 0, -0.42f * k, 0);
        off(FOOT_L, 0, -0.405f * k, 0);
        off(FOOT_R, 0, -0.405f * k, 0);
        off(PONY, 0, 0.17f * k, -0.085f * k);

        Mat4 K = Mat4.scale(k, k, k);
        final int skin = s.skin;

        // Pelvis / hips (shorts or trousers)
        parts.add(new Lathe().sides(10)
                .ring(0.09f, 0.125f * bw, 0.085f * bw)
                .ring(0.04f, 0.145f * hip * bw, 0.095f * bw)
                .ring(-0.03f, 0.16f * hip * bw, 0.10f * bw)
                .ring(-0.08f, 0.14f * hip * bw, 0.092f * bw)
                .ring(-0.11f, 0.07f, 0.06f)
                .color(s.bottomColor)
                .transform(K).build(PELVIS));

        // Torso
        final Top top = s.top;
        Lathe torso = new Lathe().sides(10)
                .ring(-0.03f, 0.12f * bw, 0.082f * bw)
                .ring(0.05f, (f ? 0.112f : 0.13f) * bw, 0.08f * bw)
                .ring(0.14f, (f ? 0.125f : 0.145f) * bw, 0.088f * bw)
                .ring(0.21f, 0.135f * bw * sh, 0.095f * bw + 0.008f * bust, 0, 0.016f * bust)
                .ring(0.26f, 0.14f * bw * sh, 0.095f * bw + 0.006f * bust, 0, 0.012f * bust)
                .ring(0.31f, 0.15f * sh * bw, 0.085f * bw)
                .ring(0.35f, 0.155f * sh, 0.07f)
                .ring(0.385f, 0.10f * sh, 0.06f)
                .ring(0.41f, 0.045f, 0.04f);
        torso.color((x, y, z) -> {
            float ax = Math.abs(x);
            if (top == Top.TANK) {
                if (y > 0.29f) return (ax > 0.045f && ax < 0.09f) ? s.topColor : skin;
                if (y > 0.235f && z > 0.03f && ax < 0.075f) return skin;
                return s.topColor;
            } else {
                if (y > 0.385f) return skin;
                if (y > 0.33f && z > 0.02f && ax < 0.055f) return skin;
                return s.topColor;
            }
        });
        parts.add(torso.transform(K).build(CHEST));

        // Neck
        parts.add(new Lathe().sides(8)
                .ring(-0.05f, 0.04f, 0.04f).ring(0.0f, 0.042f, 0.042f).ring(0.07f, 0.038f, 0.04f)
                .color(skin).transform(K).build(HEAD));

        // Head
        parts.add(new Lathe().sides(12)
                .ring(0.032f, 0.022f, 0.02f, 0, 0.035f)
                .ring(0.05f, 0.05f, 0.055f, 0, 0.02f)
                .ring(0.09f, 0.068f, 0.08f, 0, 0.01f)
                .ring(0.13f, 0.075f, 0.09f, 0, 0f)
                .ring(0.17f, 0.077f, 0.092f, 0, -0.005f)
                .ring(0.21f, 0.07f, 0.085f, 0, -0.01f)
                .ring(0.24f, 0.045f, 0.055f, 0, -0.01f)
                .ring(0.255f, 0.01f, 0.01f, 0, -0.01f)
                .color(skin).shine(0.15f).transform(K).build(HEAD));

        // Face details: eyes, brows, nose, lips
        for (int side = -1; side <= 1; side += 2) {
            parts.add(Lathe.ellipsoid(0.013f, 0.009f, 0.01f, 4, 6).color(s.eyeColor)
                    .transform(Mat4.mul(K, Mat4.translation(side * 0.03f, 0.137f, 0.08f))).build(HEAD));
            parts.add(Lathe.ellipsoid(0.017f, 0.004f, 0.008f, 3, 6).color(darken(s.hairColor, 0.55f))
                    .transform(Mat4.mul(K, Mat4.translation(side * 0.031f, 0.158f, 0.083f))).build(HEAD));
        }
        parts.add(Lathe.ellipsoid(0.011f, 0.022f, 0.013f, 4, 6).color(skin)
                .transform(Mat4.mul(K, Mat4.translation(0, 0.108f, 0.09f))).build(HEAD));
        parts.add(Lathe.ellipsoid(0.021f, 0.007f, 0.01f, 3, 6).color(s.lipColor)
                .transform(Mat4.mul(K, Mat4.translation(0, 0.073f, 0.079f))).build(HEAD));

        buildHair(K);

        // Arms
        final boolean sleeve = top != Top.TANK;
        final boolean longSleeve = top == Top.LONGSLEEVE;
        for (int side = 0; side < 2; side++) {
            int ua = side == 0 ? UARM_L : UARM_R, fa = side == 0 ? FARM_L : FARM_R;
            float am = (f ? 1f : 1.15f) * bw;
            parts.add(new Lathe().sides(8)
                    .ring(0.045f, 0.015f, 0.015f)
                    .ring(0.025f, 0.044f * am, 0.047f * am)
                    .ring(-0.03f, 0.047f * am, 0.05f * am)
                    .ring(-0.15f, 0.039f * am, 0.041f * am)
                    .ring(-0.26f, 0.032f * am, 0.034f * am)
                    .ring(-0.29f, 0.028f, 0.028f)
                    .color((x, y, z) -> (longSleeve || (sleeve && y > -0.12f)) ? s.topColor : skin)
                    .transform(K).build(ua));
            parts.add(new Lathe().sides(8)
                    .ring(0.02f, 0.028f, 0.028f)
                    .ring(-0.04f, 0.035f * am, 0.035f * am)
                    .ring(-0.18f, 0.027f * am, 0.03f * am)
                    .ring(-0.245f, 0.021f, 0.026f)
                    .ring(-0.265f, 0.016f, 0.038f)
                    .ring(-0.30f, 0.017f, 0.04f)
                    .ring(-0.34f, 0.013f, 0.029f)
                    .ring(-0.355f, 0.004f, 0.01f)
                    .color((x, y, z) -> (longSleeve && y > -0.23f) ? s.topColor : skin)
                    .transform(K).build(fa));
        }

        // Legs
        final boolean pants = s.bottom == CharacterSpec.Bottom.PANTS;
        for (int side = 0; side < 2; side++) {
            int th = side == 0 ? THIGH_L : THIGH_R, sn = side == 0 ? SHIN_L : SHIN_R, ft = side == 0 ? FOOT_L : FOOT_R;
            float lm = bw * (f ? 1f : 1.05f);
            parts.add(new Lathe().sides(8)
                    .ring(0.04f, 0.06f, 0.06f)
                    .ring(0.0f, 0.084f * lm, 0.084f * lm)
                    .ring(-0.12f, 0.077f * lm, 0.079f * lm)
                    .ring(-0.30f, 0.058f * lm, 0.06f * lm)
                    .ring(-0.41f, 0.047f, 0.05f)
                    .ring(-0.44f, 0.038f, 0.038f)
                    .color((x, y, z) -> (pants || y > -0.13f) ? s.bottomColor : skin)
                    .transform(K).build(th));
            parts.add(new Lathe().sides(8)
                    .ring(0.02f, 0.044f, 0.044f)
                    .ring(-0.03f, 0.049f * lm, 0.051f * lm)
                    .ring(-0.12f, 0.05f * lm, 0.056f * lm, 0, -0.008f)
                    .ring(-0.28f, 0.034f, 0.037f)
                    .ring(-0.37f, 0.03f, 0.031f)
                    .ring(-0.41f, 0.029f, 0.029f)
                    .color((x, y, z) -> pants ? s.bottomColor : (y < -0.355f ? s.shoeColor : skin))
                    .transform(K).build(sn));
            // Shoe: built along local Y, rotated so it points forward (+Z); local +Z becomes down.
            Mat4 shoeT = Mat4.mul(K, Mat4.mul(Mat4.translation(0, -0.032f, 0.0f), Mat4.rotX((float) (Math.PI / 2))));
            parts.add(new Lathe().sides(8)
                    .ring(-0.06f, 0.02f, 0.02f)
                    .ring(-0.048f, 0.036f, 0.034f)
                    .ring(0.0f, 0.041f, 0.037f)
                    .ring(0.06f, 0.044f, 0.031f, 0, 0.006f)
                    .ring(0.12f, 0.041f, 0.025f, 0, 0.01f)
                    .ring(0.158f, 0.029f, 0.018f, 0, 0.012f)
                    .ring(0.172f, 0.008f, 0.008f, 0, 0.013f)
                    .color((x, y, z) -> z > 0.02f ? s.soleColor : s.shoeColor)
                    .transform(shoeT).foot().build(ft));
        }
    }

    private void buildHair(Mat4 K) {
        final CharacterSpec s = spec;
        if (s.hair == CharacterSpec.Hair.BALD) return;
        final int hc = s.hairColor;
        Lathe.ColorFn hairFn = (x, y, z) -> {
            // strand-like light/dark variation
            double n = Math.sin(x * 140.0 + z * 60.0) * 0.5 + Math.sin(y * 90.0 + x * 40.0) * 0.5;
            return n > 0.35 ? lighten(hc, 0.14f) : (n < -0.45 ? darken(hc, 0.85f) : hc);
        };
        final boolean longHair = s.hair == CharacterSpec.Hair.LONG;
        final boolean shortHair = s.hair == CharacterSpec.Hair.SHORT;
        Lathe cap = new Lathe().sides(12)
                .ring(0.274f, 0.01f, 0.01f, 0, -0.012f)
                .ring(0.258f, 0.052f, 0.062f, 0, -0.012f)
                .ring(0.226f, 0.08f, 0.095f, 0, -0.012f)
                .ring(0.18f, 0.086f, 0.102f, 0, -0.01f)
                .ring(0.13f, 0.085f, 0.101f, 0, -0.012f);
        if (shortHair) {
            cap.ring(0.08f, 0.078f, 0.092f, 0, -0.02f);
        } else if (longHair) {
            cap.ring(0.08f, 0.088f, 0.1f, 0, -0.02f)
               .ring(0.0f, 0.10f, 0.09f, 0, -0.03f)
               .ring(-0.08f, 0.115f, 0.07f, 0, -0.035f)
               .ring(-0.12f, 0.10f, 0.05f, 0, -0.035f);
        } else {
            cap.ring(0.09f, 0.075f, 0.09f, 0, -0.02f)
               .ring(0.06f, 0.06f, 0.072f, 0, -0.03f);
        }
        cap.openEnds();
        final float sideCut = longHair ? -0.06f : -0.035f;
        cap.keep((x, y, z) -> {
            if (z > 0.012f && y < 0.198f) return false;              // face opening
            if (!longHair && z > sideCut && y < 0.15f) return false;  // expose ears/jaw
            if (longHair && z > 0.0f && y < 0.198f) return false;
            return true;
        });
        parts.add(cap.color(hairFn).shine(0.35f).transform(K).build(HEAD));

        if (s.hair == CharacterSpec.Hair.PONYTAIL) {
            parts.add(Lathe.ellipsoid(0.025f, 0.02f, 0.025f, 4, 6).color(darken(hc, 0.6f))
                    .transform(Mat4.mul(K, Mat4.translation(0, 0.172f, -0.085f))).build(HEAD));
            parts.add(new Lathe().sides(8)
                    .ring(0.02f, 0.012f, 0.012f)
                    .ring(0.0f, 0.03f, 0.03f)
                    .ring(-0.06f, 0.037f, 0.035f)
                    .ring(-0.14f, 0.03f, 0.028f)
                    .ring(-0.22f, 0.016f, 0.016f)
                    .ring(-0.25f, 0.004f, 0.004f)
                    .color(hairFn).shine(0.35f).transform(K).build(PONY));
        }
    }

    static int darken(int c, float f) {
        int r = (int) (((c >> 16) & 255) * f), g = (int) (((c >> 8) & 255) * f), b = (int) ((c & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    static int lighten(int c, float t) {
        return CharacterSpec.mix(c, 0xFFFFFF, t);
    }
}
