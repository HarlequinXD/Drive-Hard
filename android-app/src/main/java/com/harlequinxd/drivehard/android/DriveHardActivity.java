package com.harlequinxd.drivehard.android;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.map.MapData;

import java.util.ArrayList;
import java.util.List;

public final class DriveHardActivity extends Activity {
    private GameView gameView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        gameView = new GameView();
        setContentView(gameView);
        hideSystemUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (gameView != null) gameView.resume();
        hideSystemUi();
    }

    @Override protected void onPause() {
        if (gameView != null) gameView.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (gameView != null) gameView.close();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (gameView != null && gameView.closeMapOrTravel()) return;
        super.onBackPressed();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private final class GameView extends View implements Runnable {
        private static final int W = 480, H = 320;
        private static final float MIN_ZOOM = 0.5f, MAX_ZOOM = 12f;
        private final Paint paint = new Paint();
        private final Paint mapPaint = new Paint();
        private final Bitmap frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        private volatile boolean running, paused;
        private volatile Game game;
        private volatile String loading = "Starting...";
        private volatile Throwable failure;
        private Thread loop;
        private volatile Bitmap relief;
        private volatile boolean mapOpen, travelMenu, lastCar;
        private volatile float cameraYaw, cameraPitch, mapZoom = 2.2f;
        private volatile float mapCursorX, mapCursorZ, mapViewX, mapViewZ;
        private volatile MapData.Label selectedDestination, pendingTravel;
        private volatile float joystickX, joystickY;
        private volatile boolean gas, brake, jump, car;
        private boolean previousJump, ignoreMapTap;
        private int joystickPointer = -1, cameraPointer = -1, mapDragPointer = -1;
        private int gasPointer = -1, brakePointer = -1, jumpPointer = -1, carPointer = -1;
        private float cameraLastX, cameraLastY, dragLastX, dragLastY, pinchDistance;
        private boolean mapMoved;
        private float viewScale = 1f, viewLeft, viewTop;

        GameView() {
            super(DriveHardActivity.this);
            setFocusable(true);
            paint.setFilterBitmap(false);
            mapPaint.setFilterBitmap(false);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void resume() {
            paused = false;
            if (loop == null || !loop.isAlive()) {
                running = true;
                loop = new Thread(this, "drive-hard-android");
                loop.start();
            }
        }

        void pause() {
            paused = true;
            clearInput();
        }

        void close() {
            running = false;
            if (loop != null) {
                loop.interrupt();
                try {
                    loop.join(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        boolean closeMapOrTravel() {
            if (!mapOpen) return false;
            if (travelMenu) travelMenu = false;
            else mapOpen = false;
            postInvalidate();
            return true;
        }

        private synchronized void clearInput() {
            joystickPointer = cameraPointer = mapDragPointer = -1;
            gasPointer = brakePointer = jumpPointer = carPointer = -1;
            joystickX = joystickY = cameraYaw = cameraPitch = 0f;
            gas = brake = jump = car = false;
            previousJump = lastCar = false;
        }

        @Override public void run() {
            Game g = null;
            try {
                g = new Game(Game.DEFAULT_SEED, message -> {
                    loading = message;
                    postInvalidate();
                });
                g.setResolution(W, H);
                game = g;
                mapCursorX = mapViewX = g.mapX();
                mapCursorZ = mapViewZ = g.mapZ();
                relief = Bitmap.createBitmap(g.map.relief, MapData.SIZE, MapData.SIZE, Bitmap.Config.ARGB_8888);
                long previous = System.nanoTime();
                while (running) {
                    if (paused) {
                        Thread.sleep(100);
                        previous = System.nanoTime();
                        continue;
                    }
                    long now = System.nanoTime();
                    float dt = Math.min(0.05f, (now - previous) / 1_000_000_000f);
                    previous = now;
                    MapData.Label destination = pendingTravel;
                    if (destination != null) {
                        g.fastTravel(destination);
                        pendingTravel = null;
                    }
                    if (!mapOpen) {
                        float x, y, yaw, pitch;
                        boolean doJump, doCar, doGas, doBrake;
                        synchronized (this) {
                            x = joystickX;
                            y = joystickY;
                            yaw = cameraYaw;
                            pitch = cameraPitch;
                            cameraYaw = cameraPitch = 0f;
                            doJump = jump && !previousJump;
                            previousJump = jump;
                            doCar = car && !lastCar;
                            lastCar = car;
                            doGas = gas;
                            doBrake = brake;
                        }
                        g.update(dt, x, y, yaw, pitch, doJump, x,
                                doGas ? 1f : 0f, doBrake ? 1f : 0f, doCar);
                        g.render();
                        synchronized (frame) {
                            frame.setPixels(g.renderer.fb, 0, W, 0, 0, W, H);
                        }
                    }
                    postInvalidateOnAnimation();
                    long wait = 33_333_333L - (System.nanoTime() - now);
                    if (wait > 0) Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable e) {
                failure = e;
                loading = "Game error: " + e.getMessage();
                postInvalidate();
            } finally {
                if (g != null) g.dispose();
                game = null;
            }
        }

        @Override protected void onDraw(Canvas screen) {
            super.onDraw(screen);
            screen.drawColor(Color.BLACK);
            viewScale = Math.min(getWidth() / (float) W, getHeight() / (float) H);
            viewLeft = (getWidth() - W * viewScale) * 0.5f;
            viewTop = (getHeight() - H * viewScale) * 0.5f;
            screen.save();
            screen.translate(viewLeft, viewTop);
            screen.scale(viewScale, viewScale);
            Canvas canvas = screen;
            if (game == null) {
                canvas.drawColor(Color.rgb(10, 18, 24));
                text(canvas, loading, 18, H / 2f, 14, Color.WHITE);
                if (failure != null) text(canvas, "See Android log for details", 18, H / 2f + 22, 9, Color.LTGRAY);
            } else if (mapOpen) {
                drawFullMap(canvas);
                drawMapControls(canvas);
            } else {
                synchronized (frame) {
                    canvas.drawBitmap(frame, 0, 0, paint);
                }
                drawMinimap(canvas);
                drawGameControls(canvas);
            }
            screen.restore();
        }

        private void drawGameControls(Canvas c) {
            button(c, 68, 250, 43, "MOVE", true);
            button(c, 414, 250, 27, game != null && game.driving() ? "GAS" : "JUMP", true);
            button(c, 354, 250, 27, "BRAKE", true);
            button(c, 414, 188, 24, game != null && game.driving() ? "EXIT" : "CAR", false);
            button(c, 354, 188, 24, "MAP", false);
        }

        private void drawMinimap(Canvas c) {
            float x = 348, y = 8, size = 124;
            rounded(c, x, y, x + size, y + size, 6, 0xD9000A10);
            drawMap(c, x + 5, y + 5, size - 10, size - 10, false);
            stroke(c, x + 5, y + 5, x + size - 5, y + size - 5, 0xFFB5D1C8);
            text(c, "MAP", x + 10, y + 14, 8, Color.WHITE);
        }

        private void drawFullMap(Canvas c) {
            c.drawColor(0xFF07121A);
            drawMap(c, 0, 18, W, H - 48, true);
            paint.setColor(0xD9000A12);
            c.drawRect(0, 0, W, 19, paint);
            c.drawRect(0, H - 30, W, H, paint);
            text(c, "ISLAND MAP", 8, 14, 10, Color.WHITE);
            text(c, Math.round(mapZoom * 100) + "%", W - 82, 14, 9, 0xFFB5D1C8);
            button(c, W - 49, 10, 8, "+", false);
            button(c, W - 26, 10, 8, "-", false);
            text(c, "DRAG TO PAN   PINCH TO ZOOM", 7, H - 18, 8, 0xFFE7F0E7);
            text(c, travelMenu && selectedDestination != null
                    ? "TAP TRAVEL TO " + selectedDestination.name + "  /  CANCEL"
                    : "TAP A TOWN TO SELECT   /   CLOSE", 7, H - 5, 8, 0xFFE7F0E7);
            button(c, W - 98, H - 27, 40, "CLOSE", false);
            if (travelMenu) button(c, W - 52, H - 27, 46, "TRAVEL", false);
        }

        private void drawMap(Canvas c, float x, float y, float width, float height, boolean full) {
            Game g = game;
            if (g == null || relief == null) return;
            MapData m = g.map;
            float cx = full ? mapViewX : g.mapX();
            float cz = full ? mapViewZ : g.mapZ();
            float worldHeight = full ? m.extent / mapZoom : m.extent / 14f;
            float worldWidth = worldHeight * width / height;
            if (!full) worldHeight = worldWidth * height / width;
            float left = cx - worldWidth * 0.5f, top = cz - worldHeight * 0.5f;
            c.save();
            c.clipRect(x, y, x + width, y + height);
            c.drawRect(x, y, x + width, y + height, paintColor(0xFF0E171E));
            float ix = x + (m.x0 - left) * width / worldWidth;
            float iy = y + (m.z0 - top) * height / worldHeight;
            float iw = m.extent * width / worldWidth;
            float ih = m.extent * height / worldHeight;
            c.drawBitmap(relief, null, new RectF(ix, iy, ix + iw, iy + ih), mapPaint);

            List<Integer> roads = new ArrayList<>();
            boolean[] marks = new boolean[m.roads.size()];
            m.roadsIn(left, top, left + worldWidth, top + worldHeight, roads, marks);
            for (int index : roads) {
                MapData.Road road = m.roads.get(index);
                paint.setColor(road.cls.city ? 0xFFF4EBC3 : 0xFFD7C99A);
                paint.setStrokeWidth(road.cls.city ? 1.15f : 0.65f);
                paint.setStyle(Paint.Style.STROKE);
                Path path = new Path();
                for (int i = 0; i < road.x.length; i++) {
                    float px = x + (road.x[i] - left) * width / worldWidth;
                    float py = y + (road.z[i] - top) * height / worldHeight;
                    if (i == 0) path.moveTo(px, py);
                    else path.lineTo(px, py);
                }
                c.drawPath(path, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            if (full) {
                int maxRank = mapZoom < 1.6f ? 0 : mapZoom < 2.8f ? 1 : mapZoom < 5f ? 2 : 3;
                for (MapData.Label label : m.labels) {
                    if (label.rank > maxRank) continue;
                    float lx = x + (label.x - left) * width / worldWidth;
                    float ly = y + (label.z - top) * height / worldHeight;
                    if (lx < x || lx >= x + width || ly < y || ly >= y + height) continue;
                    paint.setColor(0xFFFFD955);
                    c.drawCircle(lx, ly, 2.2f, paint);
                    text(c, label.name, lx + 4, ly - 3, 7, Color.WHITE);
                }
                float mx = x + (mapCursorX - left) * width / worldWidth;
                float my = y + (mapCursorZ - top) * height / worldHeight;
                paint.setColor(Color.WHITE);
                paint.setStrokeWidth(1f);
                c.drawLine(mx - 5, my, mx + 5, my, paint);
                c.drawLine(mx, my - 5, mx, my + 5, paint);
            }
            float px = x + (g.mapX() - left) * width / worldWidth;
            float py = y + (g.mapZ() - top) * height / worldHeight;
            drawPlayer(c, px, py, g.mapHeading(), full ? 6 : 4);
            c.restore();
        }

        private void drawPlayer(Canvas c, float x, float y, float heading, float size) {
            float dx = (float) Math.sin(heading), dy = (float) Math.cos(heading);
            Path marker = new Path();
            marker.moveTo(x + dx * size, y + dy * size);
            marker.lineTo(x - dx * size * 0.65f - dy * size * 0.6f, y - dy * size * 0.65f + dx * size * 0.6f);
            marker.lineTo(x - dx * size * 0.65f + dy * size * 0.6f, y - dy * size * 0.65f - dx * size * 0.6f);
            marker.close();
            paint.setColor(0xFF53E7F3);
            c.drawPath(marker, paint);
        }

        private void drawMapControls(Canvas c) {
            if (!travelMenu || selectedDestination == null) return;
            rounded(c, 100, 126, 380, 188, 5, 0xF007111D);
            stroke(c, 100, 126, 380, 188, Color.WHITE);
            text(c, "FAST TRAVEL: " + selectedDestination.name, 112, 150, 10, Color.WHITE);
            text(c, "TAP TRAVEL TO CONFIRM  /  CANCEL", 112, 170, 8, Color.WHITE);
        }

        private void button(Canvas c, float cx, float cy, float radius, String label, boolean active) {
            paint.setColor(active ? 0x66364B53 : 0xB314252D);
            c.drawCircle(cx, cy, radius, paint);
            paint.setColor(active ? 0xB9D7E8DE : 0xFFD7E8DE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.3f);
            c.drawCircle(cx, cy, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            text(c, label, cx - label.length() * 2.3f, cy + 3, 7, Color.WHITE);
        }

        private void rounded(Canvas c, float l, float t, float r, float b, float radius, int color) {
            paint.setColor(color);
            c.drawRoundRect(l, t, r, b, radius, radius, paint);
        }

        private void stroke(Canvas c, float l, float t, float r, float b, int color) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            c.drawRect(l, t, r, b, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void text(Canvas c, String value, float x, float y, float size, int color) {
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTypeface(android.graphics.Typeface.create("monospace", android.graphics.Typeface.BOLD));
            c.drawText(value, x, y, paint);
        }

        private Paint paintColor(int color) {
            paint.setColor(color);
            return paint;
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            float[] p = point(event.getX(event.getActionIndex()), event.getY(event.getActionIndex()));
            int action = event.getActionMasked();
            if (mapOpen) return handleMapTouch(event, p[0], p[1]);
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                int id = event.getPointerId(event.getActionIndex());
                float x = p[0], y = p[1];
                if (in(x, y, 348, 8, 472, 132)) {
                    openMap();
                } else if (x < 165 && y > 172 && joystickPointer < 0) {
                    joystickPointer = id;
                    updateJoystick(x, y);
                } else if (inCircle(x, y, 414, 250, 36)) {
                    if (game != null && game.driving()) {
                        gas = true;
                        gasPointer = id;
                    } else {
                        jump = true;
                        jumpPointer = id;
                    }
                } else if (inCircle(x, y, 354, 250, 36)) {
                    brake = true;
                    brakePointer = id;
                } else if (inCircle(x, y, 414, 188, 34)) {
                    car = true;
                    carPointer = id;
                } else if (inCircle(x, y, 354, 188, 34)) {
                    openMap();
                } else if (x > 205 && cameraPointer < 0) {
                    cameraPointer = id;
                    cameraLastX = x;
                    cameraLastY = y;
                }
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int id = event.getPointerId(i);
                    float[] q = point(event.getX(i), event.getY(i));
                    if (id == joystickPointer) updateJoystick(q[0], q[1]);
                    if (id == cameraPointer) {
                        synchronized (this) {
                            cameraYaw -= (q[0] - cameraLastX) * 0.012f;
                            cameraPitch -= (q[1] - cameraLastY) * 0.008f;
                        }
                        cameraLastX = q[0];
                        cameraLastY = q[1];
                    }
                }
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
                if (action == MotionEvent.ACTION_UP) performClick();
                int id = action == MotionEvent.ACTION_CANCEL ? -1 : event.getPointerId(event.getActionIndex());
                if (id == -1 || id == joystickPointer) {
                    joystickPointer = -1;
                    joystickX = joystickY = 0f;
                }
                if (id == -1 || id == cameraPointer) cameraPointer = -1;
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    gas = brake = jump = car = false;
                    gasPointer = brakePointer = jumpPointer = carPointer = -1;
                    previousJump = lastCar = false;
                } else if (id >= 0) {
                    if (id == gasPointer) { gas = false; gasPointer = -1; }
                    if (id == brakePointer) { brake = false; brakePointer = -1; }
                    if (id == jumpPointer) { jump = false; jumpPointer = -1; }
                    if (id == carPointer) { car = false; carPointer = -1; }
                }
                invalidate();
                return true;
            }
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }

        private boolean handleMapTouch(MotionEvent event, float x, float y) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                int id = event.getPointerId(event.getActionIndex());
                if (event.getPointerCount() >= 2) {
                    pinchDistance = pointerDistance(event);
                    mapDragPointer = -1;
                    mapMoved = true;
                } else {
                    mapDragPointer = id;
                    dragLastX = x;
                    dragLastY = y;
                    mapMoved = false;
                }
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                if (event.getPointerCount() >= 2) {
                    float distance = pointerDistance(event);
                    if (pinchDistance > 0 && distance > 0) {
                        mapZoom = clamp(mapZoom * distance / pinchDistance, MIN_ZOOM, MAX_ZOOM);
                        pinchDistance = distance;
                        mapMoved = true;
                    }
                } else if (mapDragPointer >= 0 && !travelMenu) {
                    float dx = x - dragLastX, dy = y - dragLastY;
                    if (Math.abs(dx) + Math.abs(dy) > 1) mapMoved = true;
                    panMap(dx, dy);
                    dragLastX = x;
                    dragLastY = y;
                }
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_UP) {
                if (action == MotionEvent.ACTION_UP && ignoreMapTap) {
                    ignoreMapTap = false;
                    mapDragPointer = -1;
                    pinchDistance = 0;
                    return true;
                }
                if (!mapMoved && action == MotionEvent.ACTION_UP) mapTap(x, y);
                mapDragPointer = -1;
                pinchDistance = 0;
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL) {
                mapDragPointer = -1;
                pinchDistance = 0;
                return true;
            }
            return true;
        }

        private void mapTap(float x, float y) {
            if (y < 20 && x > W - 55) {
                mapZoom = clamp(mapZoom * (x > W - 31 ? 0.8f : 1.25f), MIN_ZOOM, MAX_ZOOM);
            } else if (y > H - 32 && x > W - 104) {
                if (x > W - 57 && travelMenu && selectedDestination != null) {
                    pendingTravel = selectedDestination;
                    mapOpen = false;
                } else if (x > W - 104 && x < W - 56) mapOpen = false;
                else if (travelMenu) travelMenu = false;
            } else if (travelMenu) {
                if (x > 275) {
                    pendingTravel = selectedDestination;
                    mapOpen = false;
                } else travelMenu = false;
            } else if (y >= 19 && y < H - 30) {
                MapData m = game.map;
                float height = m.extent / mapZoom;
                float width = height * W / (float) (H - 48);
                float worldX = mapViewX - width * 0.5f + x * width / W;
                float worldZ = mapViewZ - height * 0.5f + (y - 18) * height / (H - 48);
                mapCursorX = worldX;
                mapCursorZ = worldZ;
                float radius = 12f * m.extent / (mapZoom * H);
                MapData.Label nearest = null;
                float best = radius;
                for (MapData.Label label : m.labels) {
                    if (label.rank == 4) continue;
                    float distance = (float) Math.hypot(label.x - worldX, label.z - worldZ);
                    if (distance < best) {
                        best = distance;
                        nearest = label;
                    }
                }
                if (nearest != null) {
                    selectedDestination = nearest;
                    travelMenu = true;
                }
            }
        }

        private void openMap() {
            Game g = game;
            if (g == null) return;
            mapOpen = true;
            travelMenu = false;
            selectedDestination = null;
            mapCursorX = mapViewX = g.mapX();
            mapCursorZ = mapViewZ = g.mapZ();
            mapZoom = 2.2f;
            ignoreMapTap = true;
            clearInput();
        }

        private void panMap(float dx, float dy) {
            Game g = game;
            if (g == null) return;
            float units = g.map.extent / (mapZoom * (H - 48));
            mapViewX -= dx * units;
            mapViewZ -= dy * units;
            mapCursorX = mapViewX;
            mapCursorZ = mapViewZ;
            mapViewX = clamp(mapViewX, g.map.x0, g.map.x0 + g.map.extent);
            mapViewZ = clamp(mapViewZ, g.map.z0, g.map.z0 + g.map.extent);
        }

        private void updateJoystick(float x, float y) {
            float dx = (x - 68) / 43f, dy = (250 - y) / 43f;
            float length = (float) Math.hypot(dx, dy);
            if (length > 1) {
                dx /= length;
                dy /= length;
            }
            joystickX = dx;
            joystickY = dy;
        }

        private float[] point(float sx, float sy) {
            return new float[]{(sx - viewLeft) / viewScale, (sy - viewTop) / viewScale};
        }

        private float pointerDistance(MotionEvent event) {
            return (float) Math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1)) / viewScale;
        }

        private static boolean in(float x, float y, float l, float t, float r, float b) {
            return x >= l && x <= r && y >= t && y <= b;
        }

        private static boolean inCircle(float x, float y, float cx, float cy, float radius) {
            float dx = x - cx, dy = y - cy;
            return dx * dx + dy * dy <= radius * radius;
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
