package com.harlequinxd.drivehard.gl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.nio.FloatBuffer;

/** Screen-space overlay: joystick, jump button and a small text line. */
public final class Hud {
    private int program, uScreen, uColor, uTex, aPos, aUV;
    private int ringTex, knobTex, jumpTex, textTex;
    private final FloatBuffer quad = GlUtil.floats(6 * 4);
    private String lastText = null;
    private static final int TEXT_W = 512, TEXT_H = 32;
    private Bitmap textBmp;
    private Canvas textCanvas;
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public void init() {
        program = GlUtil.program(Shaders.HUD_VS, Shaders.HUD_FS);
        uScreen = GLES20.glGetUniformLocation(program, "uScreen");
        uColor = GLES20.glGetUniformLocation(program, "uColor");
        uTex = GLES20.glGetUniformLocation(program, "uTex");
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aUV = GLES20.glGetAttribLocation(program, "aUV");

        ringTex = make(128, (c, p) -> {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6);
            p.setColor(Color.WHITE);
            c.drawCircle(64, 64, 58, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(60, 255, 255, 255));
            c.drawCircle(64, 64, 55, p);
        });
        knobTex = make(64, (c, p) -> {
            p.setColor(Color.WHITE);
            c.drawCircle(32, 32, 30, p);
        });
        jumpTex = make(128, (c, p) -> {
            p.setColor(Color.argb(90, 255, 255, 255));
            c.drawCircle(64, 64, 60, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(5);
            p.setColor(Color.WHITE);
            c.drawCircle(64, 64, 58, p);
            p.setStyle(Paint.Style.FILL);
            Path arrow = new Path();
            arrow.moveTo(64, 26); arrow.lineTo(90, 56); arrow.lineTo(74, 56);
            arrow.lineTo(74, 80); arrow.lineTo(54, 80); arrow.lineTo(54, 56); arrow.lineTo(38, 56);
            arrow.close();
            c.drawPath(arrow, p);
            p.setTextSize(18);
            p.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText("JUMP", 64, 104, p);
        });

        textBmp = Bitmap.createBitmap(TEXT_W, TEXT_H, Bitmap.Config.ARGB_8888);
        textCanvas = new Canvas(textBmp);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(22);
        textPaint.setTypeface(Typeface.MONOSPACE);
        textPaint.setShadowLayer(2, 1, 1, Color.BLACK);
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        textTex = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTex);
        GlUtil.params(false, true);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textBmp, 0);
        lastText = null;
    }

    private interface Painter { void paint(Canvas c, Paint p); }

    private static int make(int size, Painter painter) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        painter.paint(c, p);
        int id = GlUtil.texture(b, false);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
        GlUtil.params(false, true);
        b.recycle();
        return id;
    }

    public void setText(String s) {
        if (s.equals(lastText)) return;
        lastText = s;
        textBmp.eraseColor(Color.TRANSPARENT);
        textCanvas.drawText(s, 6, 24, textPaint);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTex);
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, textBmp);
    }

    public void begin(int w, int h) {
        GLES20.glUseProgram(program);
        GLES20.glUniform2f(uScreen, w, h);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA); // premultiplied bitmaps
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public void end() {
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void ring(float cx, float cy, float r, float alpha) { draw(ringTex, cx - r, cy - r, r * 2, r * 2, alpha); }
    public void knob(float cx, float cy, float r, float alpha) { draw(knobTex, cx - r, cy - r, r * 2, r * 2, alpha); }
    public void jump(float cx, float cy, float r, float alpha) { draw(jumpTex, cx - r, cy - r, r * 2, r * 2, alpha); }
    public void text(float x, float y, float scale) { draw(textTex, x, y, TEXT_W * scale, TEXT_H * scale, 1f); }

    private void draw(int tex, float x, float y, float w, float h, float alpha) {
        float[] v = {
                x, y, 0, 0, x + w, y, 1, 0, x + w, y + h, 1, 1,
                x, y, 0, 0, x + w, y + h, 1, 1, x, y + h, 0, 1};
        quad.position(0);
        quad.put(v);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glUniform4f(uColor, alpha, alpha, alpha, alpha);
        quad.position(0);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad);
        GLES20.glEnableVertexAttribArray(aPos);
        quad.position(2);
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 16, quad);
        GLES20.glEnableVertexAttribArray(aUV);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);
    }
}
