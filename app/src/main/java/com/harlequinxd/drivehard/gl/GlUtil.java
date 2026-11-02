package com.harlequinxd.drivehard.gl;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public final class GlUtil {
    private static final String TAG = "DriveHard";

    private GlUtil() {}

    public static int program(String vs, String fs) {
        int v = shader(GLES20.GL_VERTEX_SHADER, vs);
        int f = shader(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(p);
            Log.e(TAG, "link failed: " + log);
            throw new RuntimeException("Program link failed: " + log);
        }
        return p;
    }

    private static int shader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(s);
            Log.e(TAG, "shader compile failed: " + log);
            throw new RuntimeException("Shader compile failed: " + log);
        }
        return s;
    }

    /** Uploads ARGB pixels as an RGBA texture with nearest filtering. */
    public static int texture(int[] argb, int w, int h, boolean repeat) {
        Bitmap bmp = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888);
        int id = texture(bmp, repeat);
        bmp.recycle();
        return id;
    }

    public static int texture(Bitmap bmp, boolean repeat) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        params(repeat, false);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        return ids[0];
    }

    public static void params(boolean repeat, boolean linear) {
        int filter = linear ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
        int wrap = repeat ? GLES20.GL_REPEAT : GLES20.GL_CLAMP_TO_EDGE;
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap);
    }

    /** 4x4 ordered-dither (Bayer) matrix as a repeating luminance texture. */
    public static int bayerTexture() {
        int[] m = {0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5};
        ByteBuffer b = ByteBuffer.allocateDirect(16);
        for (int v : m) b.put((byte) (v * 16 + 8));
        b.position(0);
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0]);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, 4, 4, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, b);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4);
        params(true, false);
        return ids[0];
    }

    public static FloatBuffer floats(int count) {
        return ByteBuffer.allocateDirect(count * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    public static FloatBuffer floats(float[] data, int count) {
        FloatBuffer fb = floats(count);
        fb.put(data, 0, count);
        fb.position(0);
        return fb;
    }
}
