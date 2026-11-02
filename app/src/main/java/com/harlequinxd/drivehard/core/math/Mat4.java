package com.harlequinxd.drivehard.core.math;

/**
 * Minimal row-major 4x4 affine matrix used by the pure-Java parts of the engine
 * (sprite baking, procedural modelling). Android GL code uses android.opengl.Matrix.
 */
public final class Mat4 {
    public final float[] m = new float[16];

    public Mat4() { identity(); }

    public Mat4 identity() {
        for (int i = 0; i < 16; i++) m[i] = 0f;
        m[0] = m[5] = m[10] = m[15] = 1f;
        return this;
    }

    public Mat4 set(Mat4 o) {
        System.arraycopy(o.m, 0, m, 0, 16);
        return this;
    }

    public static Mat4 translation(float x, float y, float z) {
        Mat4 r = new Mat4();
        r.m[3] = x; r.m[7] = y; r.m[11] = z;
        return r;
    }

    public static Mat4 scale(float x, float y, float z) {
        Mat4 r = new Mat4();
        r.m[0] = x; r.m[5] = y; r.m[10] = z;
        return r;
    }

    /** Rotation about X. Positive angle tips +Y toward +Z. */
    public static Mat4 rotX(float a) {
        Mat4 r = new Mat4();
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        r.m[5] = c; r.m[6] = -s;
        r.m[9] = s; r.m[10] = c;
        return r;
    }

    /** Rotation about Y. Positive angle turns +Z toward +X. */
    public static Mat4 rotY(float a) {
        Mat4 r = new Mat4();
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        r.m[0] = c; r.m[2] = s;
        r.m[8] = -s; r.m[10] = c;
        return r;
    }

    /** Rotation about Z. Positive angle turns -Y toward +X. */
    public static Mat4 rotZ(float a) {
        Mat4 r = new Mat4();
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        r.m[0] = c; r.m[1] = -s;
        r.m[4] = s; r.m[5] = c;
        return r;
    }

    /** Returns a * b (b applied first). */
    public static Mat4 mul(Mat4 a, Mat4 b) {
        Mat4 r = new Mat4();
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                float s = 0f;
                for (int k = 0; k < 4; k++) s += a.m[i * 4 + k] * b.m[k * 4 + j];
                r.m[i * 4 + j] = s;
            }
        }
        return r;
    }

    public void transformPoint(float x, float y, float z, float[] out, int off) {
        out[off] = m[0] * x + m[1] * y + m[2] * z + m[3];
        out[off + 1] = m[4] * x + m[5] * y + m[6] * z + m[7];
        out[off + 2] = m[8] * x + m[9] * y + m[10] * z + m[11];
    }

    public void transformDir(float x, float y, float z, float[] out, int off) {
        out[off] = m[0] * x + m[1] * y + m[2] * z;
        out[off + 1] = m[4] * x + m[5] * y + m[6] * z;
        out[off + 2] = m[8] * x + m[9] * y + m[10] * z;
    }
}
