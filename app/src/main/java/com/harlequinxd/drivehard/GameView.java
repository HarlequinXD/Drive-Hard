package com.harlequinxd.drivehard;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.input.TouchControls;

import java.util.Locale;

/**
 * Presents the software renderer: the game draws into an int[] framebuffer at ~240p, which is
 * copied to a Bitmap and scaled up to the screen with nearest-neighbour sampling. The touch HUD
 * is drawn on top at native resolution. No OpenGL is used by the game itself.
 */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private static final String TAG = "DriveHard";
    /** The game is rendered at 360x240: the Game Boy Advance's 3:2 shape at 240 lines. */
    private static final int LOW_H = 240, LOW_W = 360;

    private final TouchControls controls;
    private final MapOverlay mapView;
    private final TouchControls.State input = new TouchControls.State();
    private volatile Game game;
    private volatile String loading = "Starting";
    private Thread loop;
    /**
     * Frame pacing. The game runs at 30 fps (like the PS2 Driver games): a software renderer at
     * 60 fps keeps every core busy all the time and the phone heats up. When the phone reports
     * it is getting hot, the cap drops to 24 and then 20 fps and fewer render threads are used.
     */
    private final Thermal thermal;
    private int fpsCap = 30;
    private int renderThreads = -1;
    private volatile boolean running;
    private int surfW, surfH;
    private volatile boolean sizeChanged;

    private Bitmap frame;
    private final Rect dst = new Rect();
    private final Paint blit = new Paint();
    private final Paint hud = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private float fps, frameMs;
    /** Fade of the get-in/get-out button, 0..1. */
    private float carAlpha;
    private boolean carVisible;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);
        controls = new TouchControls(context.getResources().getDisplayMetrics().density);
        mapView = new MapOverlay(context.getResources().getDisplayMetrics().density);
        thermal = new Thermal(context);
        blit.setFilterBitmap(false);
        blit.setAntiAlias(false);
        blit.setDither(false);
        text.setColor(Color.WHITE);
        text.setTypeface(Typeface.MONOSPACE);
        text.setShadowLayer(3f, 1f, 1f, Color.BLACK);

        Thread loader = new Thread(() -> {
            try {
                long t0 = System.currentTimeMillis();
                Game g = new Game(Game.DEFAULT_SEED, s -> loading = s);
                Log.i(TAG, "loaded in " + (System.currentTimeMillis() - t0) + " ms");
                game = g;
            } catch (Throwable t) {
                Log.e(TAG, "load failed", t);
                loading = "Load failed: " + t;
            }
        }, "loader");
        loader.start();
    }

    // ------------------------------------------------------------------ surface lifecycle

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        loop = new Thread(this, "game-loop");
        loop.setPriority(Thread.MAX_PRIORITY);
        loop.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfW = width;
        surfH = height;
        controls.setSize(width, height);
        sizeChanged = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        if (loop != null) {
            try { loop.join(1000); } catch (InterruptedException ignored) { }
            loop = null;
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Game g = game;
        if (mapView.open) {
            mapView.onTouch(event, g, surfW, surfH);
            return true;
        }
        if (g != null && event.getActionMasked() == MotionEvent.ACTION_DOWN && mapView.hitMini(event.getX(), event.getY(), surfW, surfH)) {
            // tapping the minimap opens the island map; let go of the controls first
            MotionEvent cancel = MotionEvent.obtain(event);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            controls.onTouch(cancel);
            cancel.recycle();
            mapView.openAt(g, surfW, surfH);
            return true;
        }
        controls.onTouch(event);
        return true;
    }

    // ------------------------------------------------------------------ loop

    @Override
    public void run() {
        long last = System.nanoTime(), fpsStart = last;
        int fpsFrames = 0;
        float msAcc = 0;
        while (running) {
            Game g = game;
            if (g == null) {
                drawLoading();
                sleep(30);
                continue;
            }
            if (sizeChanged || frame == null) {
                if (surfW <= 0 || surfH <= 0) { sleep(16); continue; }
                sizeChanged = false;
                int lw = LOW_W;
                g.setResolution(lw, LOW_H);
                renderThreads = -1;
                frame = Bitmap.createBitmap(lw, LOW_H, Bitmap.Config.ARGB_8888);
                // scale the 3:2 frame up to fill the height, centred (black bars at the sides)
                int dw = Math.min(surfW, Math.round(surfH * 1.5f)), dh = Math.round(dw / 1.5f);
                int dx = (surfW - dw) / 2, dy = (surfH - dh) / 2;
                dst.set(dx, dy, dx + dw, dy + dh);
            }
            long now = System.nanoTime();
            pace(g);
            long frameNs = 1_000_000_000L / fpsCap;
            float dt = Math.min(0.05f, (now - last) / 1e9f);
            last = now;

            if (mapView.open) {
                // the world waits while you read the map
                Canvas c = lockCanvas();
                if (c != null) {
                    try {
                        c.drawColor(Color.BLACK);
                c.drawBitmap(frame, null, dst, blit);
                        mapView.drawFull(c, g, surfW, surfH);
                    } finally {
                        getHolder().unlockCanvasAndPost(c);
                    }
                }
                last = System.nanoTime();
                long target = now + 1_000_000_000L / 20;      // the map needs no more than 20 fps
                for (long wait; (wait = target - System.nanoTime()) > 0; ) java.util.concurrent.locks.LockSupport.parkNanos(wait);
                continue;
            }
            boolean driving = g.driving();
            carVisible = driving || g.canEnter();
            carAlpha += ((carVisible ? 1f : 0f) - carAlpha) * Math.min(1f, dt * 7f);
            controls.setDriving(driving);
            controls.setCarButtonEnabled(carVisible && carAlpha > 0.35f);
            controls.read(input);
            float k = 0.0055f / Math.max(1f, controls.density / 2.5f);
            g.update(dt, input.stickX, input.stickY, -input.camDX * k, input.camDY * k, input.jump,
                    input.steer, input.gas, input.reverse, input.car);
            long r0 = System.nanoTime();
            g.render();
            msAcc += (System.nanoTime() - r0) / 1e6f;
            frame.setPixels(g.renderer.fb, 0, g.renderer.W, 0, 0, g.renderer.W, g.renderer.H);

            Canvas c = lockCanvas();
            if (c == null) continue;
            try {
                c.drawColor(Color.BLACK);
                c.drawBitmap(frame, null, dst, blit);
                drawHud(c, g);
            } finally {
                getHolder().unlockCanvasAndPost(c);
            }

            // frame cap: sleep out the rest of the frame (saves battery and heat)
            long target = now + frameNs;
            for (long wait; (wait = target - System.nanoTime()) > 0; ) java.util.concurrent.locks.LockSupport.parkNanos(wait);

            fpsFrames++;
            if (now - fpsStart > 500_000_000L) {
                fps = fpsFrames * 1e9f / (now - fpsStart);
                frameMs = msAcc / fpsFrames;
                fpsFrames = 0;
                msAcc = 0;
                fpsStart = now;
            }
        }
    }

    /** Picks the frame cap and render thread count for how hot the phone is. */
    private void pace(Game g) {
        int level = thermal.level();
        int cores = Runtime.getRuntime().availableProcessors();
        int cap, threads;
        switch (level) {
            case 0: cap = 30; threads = Math.min(3, Math.max(1, cores - 1)); break;
            case 1: cap = 30; threads = Math.min(2, Math.max(1, cores - 1)); break;
            case 2: cap = 24; threads = Math.min(2, Math.max(1, cores - 1)); break;
            default: cap = 20; threads = 1; break;
        }
        fpsCap = cap;
        if (threads != renderThreads) {
            renderThreads = threads;
            g.renderer.setThreads(threads);
        }
    }

    private Canvas lockCanvas() {
        SurfaceHolder h = getHolder();
        try {
            if (Build.VERSION.SDK_INT >= 26) return h.lockHardwareCanvas();
        } catch (Throwable ignored) { }
        return h.lockCanvas();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    private void drawLoading() {
        Canvas c = lockCanvas();
        if (c == null) return;
        try {
            c.drawColor(0xFF10141C);
            float d = controls.density;
            text.setTextSize(28 * d);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(0xFFFF6FA8);
            c.drawText("DRIVE HARD", c.getWidth() / 2f, c.getHeight() / 2f - 10 * d, text);
            text.setTextSize(14 * d);
            text.setColor(Color.WHITE);
            c.drawText(loading + "...", c.getWidth() / 2f, c.getHeight() / 2f + 24 * d, text);
            text.setTextAlign(Paint.Align.LEFT);
        } finally {
            getHolder().unlockCanvasAndPost(c);
        }
    }

    private void drawHud(Canvas c, Game g) {
        float d = controls.density;
        if (input.driving) drawDriveHud(c, g, d);
        else drawFootHud(c, d);
        drawCarButton(c, g, d);
        drawPlace(c, g, d);
        mapView.drawMini(c, g, surfW, surfH);
        // stats
        text.setTextSize(12 * d);
        text.setTextAlign(Paint.Align.LEFT);
        c.drawText(String.format(Locale.US, "DRIVE HARD  %.0f/%d fps%s  %.1f ms  %dx%d  %d tris  %s",
                fps, fpsCap, thermal.level() > 0 ? " (hot " + thermal.level() + ")" : "", frameMs, g.renderer.W, g.renderer.H, g.renderer.statPrims, g.mode.name()),
                10 * d, 18 * d, text);
        // clock, day, season and weather
        com.harlequinxd.drivehard.core.Environment e = g.env;
        text.setTextSize(15 * d);
        c.drawText(String.format(Locale.US, "%s  Day %d  %s  %s", e.clock(), e.day(), e.season().label, e.shown().label),
                10 * d, 38 * d, text);
    }

    // ------------------------------------------------------------------ place names

    private String shownPlace = "", shownRegion = "";
    private long placeSince;

    /** Town and region name banner when you arrive somewhere new; fades out after a few seconds. */
    private void drawPlace(Canvas c, Game g, float d) {
        String place = g.placeName, region = g.regionName;
        long now = System.nanoTime();
        boolean newPlace = !place.isEmpty() && !place.equals(shownPlace);
        boolean newRegion = !region.equals(shownRegion);
        if (newPlace || newRegion) {
            if (newPlace || shownRegion.isEmpty() || place.isEmpty()) placeSince = now;
            if (!place.isEmpty()) shownPlace = place;
            shownRegion = region;
        }
        if (place.isEmpty() && !newRegion) shownPlace = "";
        float t = (now - placeSince) / 1e9f;
        if (t > 5f) return;
        float a = t < 0.5f ? t / 0.5f : (t > 4f ? 5f - t : 1f);
        int alpha = Math.max(0, Math.min(255, (int) (a * 255)));
        float cx = getWidth() * 0.5f, y = 64 * d;
        text.setTextAlign(Paint.Align.CENTER);
        String big = shownPlace.isEmpty() ? shownRegion : shownPlace.toUpperCase(Locale.US);
        String small = shownPlace.isEmpty() ? "" : shownRegion;
        text.setTextSize(26 * d);
        text.setColor(alpha << 24);
        c.drawText(big, cx + 2 * d, y + 2 * d, text);
        text.setColor(alpha << 24 | 0xFFFFFF);
        c.drawText(big, cx, y, text);
        if (!small.isEmpty()) {
            text.setTextSize(14 * d);
            text.setColor(alpha << 24);
            c.drawText(small, cx + 1.5f * d, y + 24 * d + 1.5f * d, text);
            text.setColor(alpha << 24 | 0xFFFFFF);
            c.drawText(small, cx, y + 24 * d, text);
        }
        text.setColor(0xFFFFFFFF);
    }

    private void button(Canvas c, float x, float y, float r, boolean held, int alpha) {
        hud.setStyle(Paint.Style.FILL);
        hud.setColor(((held ? 0x80 : 0x40) * alpha / 255) << 24 | 0xFFFFFF);
        c.drawCircle(x, y, r, hud);
        hud.setStyle(Paint.Style.STROKE);
        hud.setStrokeWidth(3 * controls.density);
        hud.setColor((0xD0 * alpha / 255) << 24 | 0xFFFFFF);
        c.drawCircle(x, y, r, hud);
        hud.setStyle(Paint.Style.FILL);
    }

    private void drawDriveHud(Canvas c, Game g, float d) {
        // D-pad: four arrow pads around a centre
        float px = controls.padX(), py = controls.padY(), pr = controls.padRadius();
        hud.setStyle(Paint.Style.FILL);
        hud.setColor(0x26FFFFFF);
        c.drawCircle(px, py, pr * 1.05f, hud);
        float a = pr * 0.34f, off = pr * 0.62f;
        drawPad(c, px + off, py, 0, a, input.padRight);
        drawPad(c, px, py - off, 1, a, input.padUp);
        drawPad(c, px - off, py, 2, a, input.padLeft);
        drawPad(c, px, py + off, 3, a, input.padDown);

        // gas and reverse
        button(c, controls.gasX(), controls.gasY(), controls.gasRadius(), input.gasHeld, 255);
        button(c, controls.revX(), controls.revY(), controls.revRadius(), input.revHeld, 255);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(15 * d);
        c.drawText("GAS", controls.gasX(), controls.gasY() + 5 * d, text);
        text.setTextSize(13 * d);
        c.drawText("REV", controls.revX(), controls.revY() + 5 * d, text);

        // speedometer
        if (g.vehicle != null) {
            text.setTextSize(26 * d);
            text.setTextAlign(Paint.Align.RIGHT);
            float kmh = Math.abs(g.vehicle.speed) * 3.6f;
            c.drawText(String.format(Locale.US, "%3.0f", kmh), c.getWidth() - 70 * d, 52 * d, text);
            text.setTextSize(12 * d);
            text.setTextAlign(Paint.Align.LEFT);
            c.drawText("km/h", c.getWidth() - 66 * d, 52 * d, text);
        }
        text.setTextAlign(Paint.Align.LEFT);
    }

    /** One triangular D-pad arrow; dir 0 right, 1 up, 2 left, 3 down. */
    private void drawPad(Canvas c, float x, float y, int dir, float a, boolean held) {
        hud.setStyle(Paint.Style.FILL);
        hud.setColor(held ? 0xE6FFFFFF : 0x80FFFFFF);
        arrow.reset();
        float[] tip = {a, 0}, b1 = {-a * 0.7f, -a * 0.85f}, b2 = {-a * 0.7f, a * 0.85f};
        float[][] pts = {tip, b1, b2};
        for (int i = 0; i < 3; i++) {
            float ux = pts[i][0], uy = pts[i][1], rx, ry;
            switch (dir) {
                case 1: rx = uy; ry = -ux; break;
                case 2: rx = -ux; ry = -uy; break;
                case 3: rx = -uy; ry = ux; break;
                default: rx = ux; ry = uy; break;
            }
            if (i == 0) arrow.moveTo(x + rx, y + ry); else arrow.lineTo(x + rx, y + ry);
        }
        arrow.close();
        c.drawPath(arrow, hud);
    }

    /** Get in / get out button: fades in next to a car, always shown while driving. */
    private void drawCarButton(Canvas c, Game g, float d) {
        int alpha = Math.round(255 * Math.max(0f, Math.min(1f, carAlpha)));
        if (alpha < 4) return;
        float x = controls.carX(), y = controls.carY(), r = controls.carRadius();
        button(c, x, y, r, input.carHeld, alpha);
        // little car icon
        hud.setColor((0xF0 * alpha / 255) << 24 | 0xFFFFFF);
        float w = r * 0.62f, h = r * 0.2f;
        arrow.reset();
        arrow.moveTo(x - w, y + h);
        arrow.lineTo(x - w, y - h * 0.2f);
        arrow.lineTo(x - w * 0.55f, y - h * 0.4f);
        arrow.lineTo(x - w * 0.3f, y - h * 1.6f);
        arrow.lineTo(x + w * 0.35f, y - h * 1.6f);
        arrow.lineTo(x + w * 0.62f, y - h * 0.4f);
        arrow.lineTo(x + w, y - h * 0.1f);
        arrow.lineTo(x + w, y + h);
        arrow.close();
        c.drawPath(arrow, hud);
        hud.setColor((0xA0 * alpha / 255) << 24);
        c.drawCircle(x - w * 0.5f, y + h, h * 0.75f, hud);
        c.drawCircle(x + w * 0.5f, y + h, h * 0.75f, hud);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(10 * d);
        int old = text.getColor();
        text.setColor((alpha << 24) | 0xFFFFFF);
        c.drawText(g.driving() ? "GET OUT" : "GET IN", x, y + r * 0.72f, text);
        text.setColor(old);
        text.setTextAlign(Paint.Align.LEFT);
    }

    private void drawFootHud(Canvas c, float d) {
        // joystick
        float jr = controls.joyRadius();
        hud.setStyle(Paint.Style.FILL);
        hud.setColor(input.joyActive ? 0x40FFFFFF : 0x22FFFFFF);
        c.drawCircle(input.joyCX, input.joyCY, jr, hud);
        hud.setStyle(Paint.Style.STROKE);
        hud.setStrokeWidth(3 * d);
        hud.setColor(input.joyActive ? 0xD0FFFFFF : 0x70FFFFFF);
        c.drawCircle(input.joyCX, input.joyCY, jr, hud);
        hud.setStyle(Paint.Style.FILL);
        hud.setColor(input.joyActive ? 0xE6FFFFFF : 0x80FFFFFF);
        c.drawCircle(input.knobX, input.knobY, jr * 0.42f, hud);

        // jump button
        float bx = controls.jumpX(), by = controls.jumpY(), br = controls.jumpRadius();
        hud.setColor(input.jumpHeld ? 0x80FFFFFF : 0x40FFFFFF);
        c.drawCircle(bx, by, br, hud);
        hud.setStyle(Paint.Style.STROKE);
        hud.setColor(0xD0FFFFFF);
        c.drawCircle(bx, by, br, hud);
        hud.setStyle(Paint.Style.FILL);
        arrow.reset();
        arrow.moveTo(bx, by - br * 0.55f);
        arrow.lineTo(bx + br * 0.38f, by - br * 0.08f);
        arrow.lineTo(bx + br * 0.14f, by - br * 0.08f);
        arrow.lineTo(bx + br * 0.14f, by + br * 0.25f);
        arrow.lineTo(bx - br * 0.14f, by + br * 0.25f);
        arrow.lineTo(bx - br * 0.14f, by - br * 0.08f);
        arrow.lineTo(bx - br * 0.38f, by - br * 0.08f);
        arrow.close();
        c.drawPath(arrow, hud);
        text.setTextSize(11 * d);
        text.setTextAlign(Paint.Align.CENTER);
        c.drawText("JUMP", bx, by + br * 0.62f, text);
        text.setTextAlign(Paint.Align.LEFT);
    }
}
