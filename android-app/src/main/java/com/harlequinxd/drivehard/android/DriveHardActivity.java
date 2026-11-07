package com.harlequinxd.drivehard.android;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.gl.GlRenderer;
import com.harlequinxd.drivehard.core.render.SoftRenderer;
import com.harlequinxd.drivehard.core.map.MapData;
import com.harlequinxd.drivehard.core.ui.LoadScreen;
import com.harlequinxd.drivehard.core.ui.MapView;
import com.harlequinxd.drivehard.core.ui.MainMenu;
import com.harlequinxd.drivehard.core.GameAudio;
import com.harlequinxd.drivehard.core.Settings;

/**
 * Android front end. The game renders at a low resolution matched to the screen's shape (so
 * nothing is letterboxed), and the core draws the minimap, HUD, map, pause menu and loading
 * screens into that frame exactly like the desktop build.
 *
 * Phones with OpenGL ES 3.0 draw the game on the GPU ({@link GlRenderer}, in a GLSurfaceView
 * under the controls), 30 frames a second: far less work for the CPU, so the phone stays cooler.
 * Without it (or if the GPU set-up fails) the game falls back to the CPU renderer. The touch controls are drawn on top at
 * full screen resolution: a floating stick on the left, and on the right either JUMP / CAR with
 * camera drag, or GAS / BRAKE / EXIT pedals when driving. Tap the minimap for the map, the pause
 * button (or Back) for the menu.
 */
public final class DriveHardActivity extends Activity {
    private GameView gameView;
    private GLSurfaceView glView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // the game data folder (the island and the chunks already visited); a new install or
        // update of the app starts a fresh one
        com.harlequinxd.drivehard.core.data.WorldCache.root = new java.io.File(getFilesDir(), "GameData");
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            com.harlequinxd.drivehard.core.data.WorldCache.build = info.lastUpdateTime + "-" + info.versionName;
        } catch (Exception e) {
            com.harlequinxd.drivehard.core.data.WorldCache.build = "android";
        }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        boolean gles3 = am != null && am.getDeviceConfigurationInfo().reqGlEsVersion >= 0x30000;
        gameView = new GameView(gles3);
        if (gles3) {
            glView = new GLSurfaceView(this);
            glView.setEGLContextClientVersion(3);
            glView.setPreserveEGLContextOnPause(true);
            glView.setRenderer(gameView);
            glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            gameView.glView = glView;
            FrameLayout root = new FrameLayout(this);
            root.addView(glView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            root.addView(gameView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            setContentView(root);
        } else {
            setContentView(gameView);
        }
        hideSystemUi();
    }

    @Override protected void onResume() {
        super.onResume();
        if (glView != null) glView.onResume();
        if (gameView != null) gameView.resume();
        hideSystemUi();
    }

    @Override protected void onPause() {
        if (gameView != null) gameView.pause();
        if (glView != null) glView.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (gameView != null) gameView.close();
        super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override public void onBackPressed() {
        if (gameView != null && gameView.back()) return;
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

    private enum Screen { MENU, GAME, MAP, PAUSE }

    private final class GameView extends View implements Runnable, GLSurfaceView.Renderer {
        private static final int RES_H = 240, MAX_W = 520;
        private static final int ACCENT = 0xFFE0263A;
        private volatile int W, H;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint pixels = new Paint();
        private volatile Bitmap frame, loadBitmap;
        private int[] fb;
        private volatile LoadScreen loadScreen;
        private volatile boolean openMapRequest;
        private final MapView mapView = new MapView();
        private volatile boolean running, paused, travelling;
        private volatile Game game;
        private volatile String error = "";
        private Thread loop;
        private volatile Screen screen = Screen.MENU;
        private volatile MapData.Label pendingTravel;
        private volatile MainMenu mainMenu;
        private Settings settings;
        private volatile AndroidAudio audio;
        private volatile boolean quit;
        private float density = 1f;

        // touch state (UI thread writes, game thread reads under the lock)
        private final Object input = new Object();
        private float stickX, stickY, camYaw, camPitch;
        private boolean gas, brake, handbrake, jumpHeld, carHeld, jumpLatch, carLatch;
        private int stickPointer = -1, camPointer = -1, gasPointer = -1, brakePointer = -1, handPointer = -1, jumpPointer = -1, carPointer = -1;
        private float stickOriginX, stickOriginY, stickKnobX, stickKnobY, camLastX, camLastY;
        // map gestures (in frame pixels)
        private int mapPointer = -1;
        private float mapLastX, mapLastY, pinch;
        private boolean mapMoved;
        private final java.util.concurrent.ConcurrentLinkedQueue<float[]> mapOps = new java.util.concurrent.ConcurrentLinkedQueue<>();
        private float hintTime = 8f;
        // GPU drawing (GL thread)
        private volatile boolean gpu;
        GLSurfaceView glView;
        private GlRenderer glRenderer;
        private volatile int screenW, screenH;
        private long lastGlFrame;
        private Thread pacer;

        GameView(boolean gpu) {
            super(DriveHardActivity.this);
            this.gpu = gpu;
            setFocusable(true);
            pixels.setFilterBitmap(false);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            density = getResources().getDisplayMetrics().density;
        }

        // ------------------------------------------------------------------ lifecycle

        void resume() {
            paused = false;
            AndroidAudio a = audio;
            if (a != null) a.resume();
            if (loop == null || !loop.isAlive()) {
                running = true;
                loop = new Thread(this, "drive-hard-android");
                loop.start();
            }
            if (gpu && (pacer == null || !pacer.isAlive())) {
                // the GPU draws 30 frames a second (half the screen's rate: cooler, and plenty)
                pacer = new Thread(() -> {
                    while (running && gpu) {
                        if (!paused && glView != null) glView.requestRender();
                        postInvalidateOnAnimation();
                        try { Thread.sleep(33); } catch (InterruptedException e) { return; }
                    }
                }, "drive-hard-frames");
                pacer.start();
            }
        }

        void pause() {
            paused = true;
            AndroidAudio a = audio;
            if (a != null) a.pause();
            clearInput();
            if (game != null && screen == Screen.GAME) screen = Screen.PAUSE;
        }

        void close() {
            running = false;
            AndroidAudio a = audio;
            if (a != null) a.stop();
            if (pacer != null) pacer.interrupt();
            if (loop != null) {
                loop.interrupt();
                try { loop.join(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }

        /** Back: close the travel dialog, then the map or the menu; in game open the menu. */
        boolean back() {
            if (game == null || travelling) return true;
            if (screen == Screen.MENU) {
                MainMenu m = mainMenu;
                if (m != null && m.onMainPage()) return false;       // leave the game
                mapOps.add(new float[]{4});
                return true;
            }
            if (screen == Screen.GAME && game.missions.modal()) { mapOps.add(new float[]{4}); return true; }
            if (screen == Screen.MAP) {
                mapOps.add(new float[]{3});
                return true;
            }
            if (screen == Screen.PAUSE) { screen = Screen.GAME; return true; }
            clearInput();
            screen = Screen.PAUSE;
            return true;
        }

        private void clearInput() {
            synchronized (input) {
                stickPointer = camPointer = gasPointer = brakePointer = handPointer = jumpPointer = carPointer = -1;
                stickX = stickY = camYaw = camPitch = 0f;
                gas = brake = handbrake = jumpHeld = carHeld = jumpLatch = carLatch = false;
            }
        }

        // ------------------------------------------------------------------ game thread

        @Override public void run() {
            Game g = null;
            try {
                while (running && (getWidth() == 0 || getHeight() == 0)) Thread.sleep(20);
                H = RES_H;
                W = Math.min(MAX_W, Math.max(320, Math.round(RES_H * getWidth() / (float) getHeight()) & ~1));
                fb = new int[W * H];
                frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
                loadBitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
                loadScreen = new LoadScreen(W, H);
                loadScreen.reset("Starting up");
                postInvalidate();
                g = new Game(Game.DEFAULT_SEED, loadScreen::set, true);
                if (!gpu) g.setResolution(W, H);
                settings = Settings.load(new java.io.File(getFilesDir(), "settings.txt"));
                g.settings = settings;
                settings.apply(g);
                AndroidAudio a = new AndroidAudio(g.audio.mixer);
                a.start();
                if (paused) a.pause();
                audio = a;
                mainMenu = new MainMenu(g, settings, true);
                mainMenu.open();
                screen = Screen.MENU;
                game = g;
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
                        pendingTravel = null;
                        travel(g, destination);
                        previous = System.nanoTime();
                        continue;
                    }
                    if (gpu) {
                        // the GL thread draws the frames; this thread only loads and travels
                        if (quit) { runOnUiThread(DriveHardActivity.this::finish); return; }
                        Thread.sleep(16);
                        continue;
                    }
                    // the CPU renderer (no OpenGL ES 3, or the GPU set-up failed)
                    if (!(g.renderer instanceof SoftRenderer)) { g.setResolution(W, H); settings.apply(g); }
                    frame(g, dt);
                    if (quit) { runOnUiThread(DriveHardActivity.this::finish); return; }
                    postInvalidateOnAnimation();
                    long wait = 33_333_333L - (System.nanoTime() - now);
                    if (wait > 0) Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable e) {
                android.util.Log.e("DriveHard", "game error", e);
                error = "Game error: " + e.getMessage();
                travelling = true;
                postInvalidate();
            } finally {
                if (g != null) g.dispose();
                game = null;
            }
        }

        /** One frame of whichever screen is up, and the sounds that go with it. */
        private void frame(Game g, float dt) {
            switch (screen) {
                case MENU: menuFrame(g, dt); break;
                case MAP: mapFrame(g); break;
                case PAUSE: pauseFrame(g); break;
                default: gameFrame(g, dt); break;
            }
            Screen now = screen;
            g.audio.screen(now == Screen.MENU ? GameAudio.SCREEN_MENU : now == Screen.MAP ? GameAudio.SCREEN_MAP
                    : now == Screen.PAUSE ? GameAudio.SCREEN_PAUSE : g.missions.modal() ? GameAudio.SCREEN_MENU : GameAudio.SCREEN_GAME,
                    now == Screen.GAME && g.missions.modal() ? g.missions.choice : -1);
        }

        private void menuFrame(Game g, float dt) {
            MainMenu m = mainMenu;
            float[] op;
            while ((op = mapOps.poll()) != null) {
                if ((int) op[0] == 2) m.tap(op[1], op[2]);
                else if ((int) op[0] == 4) m.back();
            }
            switch (m.poll()) {
                case MainMenu.CRUISE:
                    g.missions.abandon();
                    leaveMenu(g);
                    return;
                case MainMenu.MISSION:
                    g.missions.abandon();
                    leaveMenu(g);
                    g.missions.brief(m.mission);
                    return;
                case MainMenu.QUIT:
                    quit = true;
                    return;
                default: break;
            }
            g.attract(dt);
            g.render();
            sceneToFrame(g);
            m.draw(fb, W, H, dt);
            publish();
        }

        private void leaveMenu(Game g) {
            g.endAttract();
            g.audio.music(false);
            clearInput();
            hintTime = 8f;
            screen = Screen.GAME;
        }

        private void openMenu(Game g, boolean story) {
            g.missions.abandon();
            mapOps.clear();
            if (story) mainMenu.openStory(); else mainMenu.open();
            screen = Screen.MENU;
        }

        /** A mission briefing or result is up: the world waits, taps work its buttons. */
        private void missionScreen(Game g) {
            float[] op;
            while ((op = mapOps.poll()) != null) {
                if ((int) op[0] == 2) g.missions.tap(op[1], op[2]);
                else if ((int) op[0] == 4) g.missions.back();
            }
            if (g.missions.request) { g.missions.request = false; openMenu(g, true); return; }
            g.render();
            sceneToFrame(g);
            int r = minimapRadius();
            mapView.drawMinimap(fb, W, H, W - r - 8, r + 8, r, g);
            mapView.drawHud(fb, W, H, g, 34, 6);
            g.missions.draw(fb, W, H);
            publish();
        }

        private void gameFrame(Game g, float dt) {
            if (g.missions.modal()) { missionScreen(g); return; }
            if (openMapRequest) {
                openMapRequest = false;
                mapOps.clear();
                mapView.open(g);
                screen = Screen.MAP;
                return;
            }
            float x, y, yaw, pitch;
            boolean doJump, doCar, doGas, doBrake, doHand;
            synchronized (input) {
                x = stickX;
                y = stickY;
                yaw = camYaw;
                pitch = camPitch;
                camYaw = camPitch = 0f;
                doJump = jumpLatch;
                doCar = carLatch;
                jumpLatch = carLatch = false;
                doGas = gas;
                doBrake = brake;
                doHand = handbrake;
            }
            g.update(dt, x, y, yaw, pitch, doJump, x, doGas ? 1f : 0f, doBrake ? 1f : 0f, doCar, doHand);
            g.render();
            hintTime -= dt;
            sceneToFrame(g);
            int r = minimapRadius();
            mapView.drawMinimap(fb, W, H, W - r - 8, r + 8, r, g);
            mapView.drawHud(fb, W, H, g, 34, 6);
            g.missions.draw(fb, W, H);
            mapView.drawHints(fb, W, H, g.driving() ? "STEER WITH THE STICK  -  GAS, BRAKE AND HANDBRAKE ON THE RIGHT"
                    : "LEFT STICK WALK  -  DRAG RIGHT SIDE TO LOOK  -  TAP THE MAP TO TRAVEL", Math.min(1f, hintTime));
            publish();
        }

        private int minimapRadius() { return Math.max(34, H / 7); }

        private void mapFrame(Game g) {
            mapView.hints = "DRAG PAN  PINCH ZOOM  TAP A PLACE  < > STEP THROUGH PLACES";
            float[] op;
            while ((op = mapOps.poll()) != null) {
                switch ((int) op[0]) {
                    case 0: mapView.pan(g, op[1], op[2], H); break;
                    case 1: mapView.zoomBy(op[1]); break;
                    case 2: {
                        String hit = mapView.tap(g, op[1], op[2], W, H);
                        if (hit.equals("travel") && mapView.selected != null) {
                            travelling = true;          // (the GL thread stops drawing the game first)
                            pendingTravel = mapView.selected;
                            return;
                        }
                        if (hit.equals("close")) { screen = Screen.GAME; return; }
                        break;
                    }
                    case 3:
                        if (mapView.dialog) mapView.dialog = false;
                        else { screen = Screen.GAME; return; }
                        break;
                    default: break;
                }
            }
            mapView.drawFull(fb, W, H, g);
            publish();
        }

        private static final String[] MENU = {"RESUME", "MAP AND FAST TRAVEL", "MAIN MENU", "QUIT"};

        private void pauseFrame(Game g) {
            float[] op;
            while ((op = mapOps.poll()) != null) {
                if ((int) op[0] != 2) continue;
                for (int i = 0; i < mapView.menuBoxes.size(); i++) {
                    int[] b = mapView.menuBoxes.get(i);
                    if (op[1] >= b[0] && op[1] < b[2] && op[2] >= b[1] && op[2] < b[3]) {
                        if (i == 0) screen = Screen.GAME;
                        if (i == 1) { mapOps.clear(); mapView.open(g); screen = Screen.MAP; }
                        if (i == 2) openMenu(g, false);
                        if (i == 3) quit = true;
                        return;
                    }
                }
            }
            g.render();
            sceneToFrame(g);
            mapView.drawMenu(fb, W, H, "PAUSED", MENU, -1, new String[]{
                    "#ON FOOT",
                    "LEFT STICK     WALK",
                    "DRAG RIGHT     LOOK AROUND",
                    "JUMP           JUMP",
                    "CAR            GET IN A CAR",
                    "",
                    "#DRIVING",
                    "LEFT STICK     STEER",
                    "GAS / BRAKE    PEDALS (HOLD BRAKE",
                    "               TO REVERSE)",
                    "EXIT           LEAVE THE CAR",
                    "",
                    "#MAP: TAP THE MINIMAP",
            });
            publish();
        }

        /** The HUD draws over the scene: the CPU frame itself, or a clear layer over the GPU's. */
        private void sceneToFrame(Game g) {
            if (g.renderer.fb != null) System.arraycopy(g.renderer.fb, 0, fb, 0, fb.length);
            else java.util.Arrays.fill(fb, 0);
        }

        private void publish() {
            if (glRenderer != null && game != null && game.renderer == glRenderer) {
                glRenderer.present(screenW, screenH, Math.round(viewLeft), Math.round(viewTop),
                        Math.round(W * viewScale), Math.round(H * viewScale), fb);
                return;
            }
            synchronized (frame) { frame.setPixels(fb, 0, W, 0, 0, W, H); }
        }

        private void layout(int sw, int sh) {
            viewScale = Math.min(sw / (float) W, sh / (float) H);
            viewLeft = (sw - W * viewScale) * 0.5f;
            viewTop = (sh - H * viewScale) * 0.5f;
        }

        // ------------------------------------------------------------------ GL thread

        @Override public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            // a new context: everything the GPU held is gone and is uploaded again
            if (glRenderer != null) glRenderer.contextLost();
        }

        @Override public void onSurfaceChanged(GL10 unused, int width, int height) {
            screenW = width;
            screenH = height;
        }

        @Override public void onDrawFrame(GL10 unused) {
            if (!gpu || W == 0 || loadScreen == null) {
                GLES30.glClearColor(0f, 0f, 0f, 1f);
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
                return;
            }
            try {
                if (glRenderer == null) glRenderer = new GlRenderer(new AndroidGl(), W, H);
                layout(screenW, screenH);
                Game g = game;
                if (g == null || travelling) {
                    glRenderer.presentImage(screenW, screenH, Math.round(viewLeft), Math.round(viewTop),
                            Math.round(W * viewScale), Math.round(H * viewScale), loadScreen.render(), W, H);
                    lastGlFrame = 0;
                    return;
                }
                if (g.renderer != glRenderer) { g.useRenderer(glRenderer); settings.apply(g); }
                long now = System.nanoTime();
                float dt = lastGlFrame == 0 ? 1 / 30f : Math.min(0.05f, (now - lastGlFrame) / 1_000_000_000f);
                lastGlFrame = now;
                if (paused) return;
                frame(g, dt);
            } catch (Throwable e) {
                // no usable GPU path on this phone: carry on with the CPU renderer
                android.util.Log.e("DriveHard", "GPU renderer failed, using the CPU renderer", e);
                gpu = false;
                if (glRenderer != null) glRenderer.contextLost();
                runOnUiThread(() -> { if (glView != null) glView.setVisibility(View.GONE); });
            }
        }

        private void travel(Game g, MapData.Label destination) {
            float km = (float) Math.hypot(destination.x - g.mapX(), destination.z - g.mapZ()) / 1000f;
            loadScreen.reset("Finding a street");
            loadScreen.heading("TRAVELLING TO " + destination.name.toUpperCase(), String.format(java.util.Locale.US, "%.1f KM", km));
            travelling = true;
            postInvalidate();
            try {
                g.fastTravel(destination, loadScreen::set);
                Thread.sleep(400);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                clearInput();
                mapView.dialog = false;
                screen = Screen.GAME;
                hintTime = 4f;
                travelling = false;
            }
        }

        // ------------------------------------------------------------------ drawing (UI thread)

        private float viewScale = 1f, viewLeft, viewTop;

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Bitmap frame = this.frame;
            LoadScreen loadScreen = this.loadScreen;
            if (gpu) {
                // the GPU draws the game underneath: only the controls (and any error) go here
                if (W > 0) layout(getWidth(), getHeight());
                if (!error.isEmpty()) {
                    paint.setColor(0xFFFF8A80);
                    paint.setTextSize(16 * density);
                    paint.setTypeface(Typeface.DEFAULT_BOLD);
                    canvas.drawText(error, 24 * density, 40 * density, paint);
                }
                if (game != null && !travelling && screen == Screen.GAME && !game.missions.modal()) drawControls(canvas);
                return;
            }
            canvas.drawColor(Color.BLACK);
            if (frame == null || loadScreen == null) { postInvalidateDelayed(33); return; }
            layout(getWidth(), getHeight());
            RectF dst = new RectF(viewLeft, viewTop, viewLeft + W * viewScale, viewTop + H * viewScale);
            if (game == null || travelling) {
                loadBitmap.setPixels(loadScreen.render(), 0, W, 0, 0, W, H);
                canvas.drawBitmap(loadBitmap, null, dst, pixels);
                if (!error.isEmpty()) {
                    paint.setColor(0xFFFF8A80);
                    paint.setTextSize(16 * density);
                    paint.setTypeface(Typeface.DEFAULT_BOLD);
                    canvas.drawText(error, 24 * density, 40 * density, paint);
                }
                postInvalidateDelayed(33);
                return;
            }
            synchronized (frame) { canvas.drawBitmap(frame, null, dst, pixels); }
            Game gm = game;
            if (screen == Screen.GAME && gm != null && !gm.missions.modal()) drawControls(canvas);
        }

        // control layout in screen pixels
        private float unit() { return Math.min(getWidth(), getHeight()) / 100f; }

        private float stickHomeX() { return getWidth() * 0.15f; }
        private float stickHomeY() { return getHeight() * 0.74f; }
        private float stickRadius() { return unit() * 13f; }

        private float[] gasButton() { return new float[]{getWidth() - unit() * 15f, getHeight() - unit() * 21f, unit() * 12f}; }
        private float[] handButton() { return new float[]{getWidth() - unit() * 40f, getHeight() - unit() * 37f, unit() * 8.5f}; }
        private float[] brakeButton() { return new float[]{getWidth() - unit() * 40f, getHeight() - unit() * 13f, unit() * 9.5f}; }
        private float[] jumpButton() { return new float[]{getWidth() - unit() * 15f, getHeight() - unit() * 21f, unit() * 11f}; }
        private float[] carButton() { return new float[]{getWidth() - unit() * 36f, getHeight() - unit() * 11f, unit() * 8f}; }
        private float[] exitButton() { return new float[]{getWidth() - unit() * 13f, getHeight() - unit() * 48f, unit() * 7.5f}; }
        private float[] pauseButton() { return new float[]{viewLeft + 16 * viewScale, viewTop + 16 * viewScale, 11 * viewScale}; }
        private float[] minimapArea() {
            int r = minimapRadius();
            return new float[]{viewLeft + (W - r - 8) * viewScale, viewTop + (r + 8) * viewScale, (r + 4) * viewScale};
        }

        private void drawControls(Canvas c) {
            Game g = game;
            boolean driving = g != null && g.driving();
            // floating stick: shown at home when idle, under the thumb when held
            float sx, sy, kx, ky;
            synchronized (input) {
                boolean held = stickPointer >= 0;
                sx = held ? stickOriginX : stickHomeX();
                sy = held ? stickOriginY : stickHomeY();
                kx = held ? stickKnobX : sx;
                ky = held ? stickKnobY : sy;
            }
            float r = stickRadius();
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(null);
            paint.setColor(0x40000000);
            c.drawCircle(sx, sy, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * density);
            paint.setColor(0x80FFFFFF);
            c.drawCircle(sx, sy, r, paint);
            paint.setStrokeWidth(1.2f * density);
            paint.setColor(0x40FFFFFF);
            c.drawCircle(sx, sy, r * 0.55f, paint);
            // direction ticks
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2;
                float ox = (float) Math.cos(a), oy = (float) Math.sin(a);
                c.drawLine(sx + ox * r * 0.82f, sy + oy * r * 0.82f, sx + ox * r * 0.95f, sy + oy * r * 0.95f, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new LinearGradient(kx, ky - r * 0.4f, kx, ky + r * 0.4f, 0xF0F4F1E8, 0xF0A9A49A, Shader.TileMode.CLAMP));
            c.drawCircle(kx, ky, r * 0.42f, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f * density);
            paint.setColor(0xA0000000);
            c.drawCircle(kx, ky, r * 0.42f, paint);
            paint.setStyle(Paint.Style.FILL);

            if (driving) {
                pedal(c, gasButton(), "GAS", gas, ACCENT, true);
                pedal(c, brakeButton(), "BRAKE", brake, 0xFF3A6EA5, true);
                pedal(c, handButton(), "HAND", handbrake, 0xFFB0452E, true);
                roundButton(c, exitButton(), "EXIT", carHeld, 0xFF2A3640);
            } else {
                roundButton(c, jumpButton(), "JUMP", jumpHeld, ACCENT);
                roundButton(c, carButton(), "CAR", carHeld, 0xFF2A3640);
            }
            // pause button
            float[] p = pauseButton();
            paint.setColor(0xB0000000);
            c.drawCircle(p[0], p[1], p[2], paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(0xC0FFFFFF);
            paint.setStrokeWidth(1.2f * density);
            c.drawCircle(p[0], p[1], p[2], paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            float bw = p[2] * 0.22f, bh = p[2] * 0.9f;
            c.drawRect(p[0] - bw * 2f, p[1] - bh / 2, p[0] - bw, p[1] + bh / 2, paint);
            c.drawRect(p[0] + bw, p[1] - bh / 2, p[0] + bw * 2f, p[1] + bh / 2, paint);
            // "MAP" tag under the minimap
            float[] m = minimapArea();
            label(c, "MAP", m[0], m[1] + m[2] + 12 * density, 11 * density, 0xE0FFFFFF);
        }

        private void roundButton(Canvas c, float[] b, String text, boolean down, int colour) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(down ? colour : 0x70000000);
            c.drawCircle(b[0], b[1], b[2], paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f * density);
            paint.setColor(down ? 0xFFFFFFFF : (colour | 0xFF000000));
            c.drawCircle(b[0], b[1], b[2], paint);
            paint.setStrokeWidth(1f * density);
            paint.setColor(0x60FFFFFF);
            c.drawCircle(b[0], b[1], b[2] - 4 * density, paint);
            paint.setStyle(Paint.Style.FILL);
            label(c, text, b[0], b[1] + b[2] * 0.14f, b[2] * 0.42f, Color.WHITE);
        }

        /** A pedal: rounded rectangle with grip lines. */
        private void pedal(Canvas c, float[] b, String text, boolean down, int colour, boolean grip) {
            float w = b[2] * 1.25f, h = b[2] * 1.6f;
            RectF r = new RectF(b[0] - w / 2, b[1] - h / 2 + (down ? 2 * density : 0), b[0] + w / 2, b[1] + h / 2 + (down ? 2 * density : 0));
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(down ? colour : 0x80000000);
            c.drawRoundRect(r, b[2] * 0.25f, b[2] * 0.25f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f * density);
            paint.setColor(down ? 0xFFFFFFFF : (colour | 0xFF000000));
            c.drawRoundRect(r, b[2] * 0.25f, b[2] * 0.25f, paint);
            if (grip) {
                paint.setStrokeWidth(1.5f * density);
                paint.setColor(0x50FFFFFF);
                for (int i = 1; i <= 3; i++) {
                    float y = r.top + h * (0.18f + i * 0.13f);
                    c.drawLine(r.left + w * 0.22f, y, r.right - w * 0.22f, y, paint);
                }
            }
            paint.setStyle(Paint.Style.FILL);
            label(c, text, b[0], r.bottom - h * 0.12f, b[2] * 0.3f, Color.WHITE);
        }

        private void label(Canvas c, String text, float cx, float baseline, float size, int colour) {
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(size);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(0xA0000000);
            c.drawText(text, cx + density, baseline + density, paint);
            paint.setColor(colour);
            c.drawText(text, cx, baseline, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        // ------------------------------------------------------------------ touch

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (game == null || travelling) return true;
            Game gm = game;
            if (screen != Screen.GAME || (gm != null && gm.missions.modal())) return menuTouch(e);
            int action = e.getActionMasked();
            int index = e.getActionIndex();
            int id = e.getPointerId(index);
            float x = e.getX(index), y = e.getY(index);
            boolean driving = game != null && game.driving();
            synchronized (input) {
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_POINTER_DOWN: {
                        float[] p = pauseButton(), m = minimapArea();
                        if (hit(p, x, y, 1.6f)) { buzz(); clearLocked(); screen = Screen.PAUSE; break; }
                        if (hit(m, x, y, 1.1f)) {
                            buzz();
                            clearLocked();
                            openMapRequest = true;     // the game thread owns the map view
                            break;
                        }
                        if (driving && hit(gasButton(), x, y, 1.35f)) { gas = true; gasPointer = id; buzz(); }
                        else if (driving && hit(brakeButton(), x, y, 1.35f)) { brake = true; brakePointer = id; buzz(); }
                        else if (driving && hit(handButton(), x, y, 1.3f)) { handbrake = true; handPointer = id; buzz(); }
                        else if (driving && hit(exitButton(), x, y, 1.3f)) { carHeld = true; carLatch = true; carPointer = id; buzz(); }
                        else if (!driving && hit(jumpButton(), x, y, 1.3f)) { jumpHeld = true; jumpLatch = true; jumpPointer = id; buzz(); }
                        else if (!driving && hit(carButton(), x, y, 1.3f)) { carHeld = true; carLatch = true; carPointer = id; buzz(); }
                        else if (x < getWidth() * 0.42f && stickPointer < 0) {
                            stickPointer = id;
                            // the stick appears under the thumb (kept clear of the screen edge)
                            float r = stickRadius();
                            stickOriginX = Math.max(r + 8 * density, x);
                            stickOriginY = Math.min(getHeight() - r - 8 * density, Math.max(r + 8 * density, y));
                            moveStick(x, y);
                        } else if (camPointer < 0) {
                            camPointer = id;
                            camLastX = x;
                            camLastY = y;
                        }
                        break;
                    }
                    case MotionEvent.ACTION_MOVE:
                        for (int i = 0; i < e.getPointerCount(); i++) {
                            int pid = e.getPointerId(i);
                            float px = e.getX(i), py = e.getY(i);
                            if (pid == stickPointer) moveStick(px, py);
                            else if (pid == camPointer) {
                                float k = 1f / unit();
                                camYaw -= (px - camLastX) * k * 0.055f;
                                camPitch -= (py - camLastY) * k * 0.035f;
                                camLastX = px;
                                camLastY = py;
                            }
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) { clearLocked(); break; }
                        if (id == stickPointer) { stickPointer = -1; stickX = stickY = 0f; }
                        if (id == camPointer) camPointer = -1;
                        if (id == gasPointer) { gas = false; gasPointer = -1; }
                        if (id == brakePointer) { brake = false; brakePointer = -1; }
                        if (id == handPointer) { handbrake = false; handPointer = -1; }
                        if (id == jumpPointer) { jumpHeld = false; jumpPointer = -1; }
                        if (id == carPointer) { carHeld = false; carPointer = -1; }
                        break;
                    default: break;
                }
            }
            if (action == MotionEvent.ACTION_UP) performClick();
            invalidate();
            return true;
        }

        private void clearLocked() {
            stickPointer = camPointer = gasPointer = brakePointer = handPointer = jumpPointer = carPointer = -1;
            stickX = stickY = 0f;
            gas = brake = handbrake = jumpHeld = carHeld = false;
        }

        private void moveStick(float x, float y) {
            float r = stickRadius();
            float dx = x - stickOriginX, dy = y - stickOriginY;
            float len = (float) Math.hypot(dx, dy);
            // dragging past the rim pulls the stick along, so it never "runs out"
            if (len > r * 1.4f) {
                stickOriginX += dx / len * (len - r * 1.4f);
                stickOriginY += dy / len * (len - r * 1.4f);
                dx = x - stickOriginX;
                dy = y - stickOriginY;
                len = r * 1.4f;
            }
            float cl = Math.min(len, r);
            float nx = len > 0 ? dx / len : 0, ny = len > 0 ? dy / len : 0;
            stickKnobX = stickOriginX + nx * cl;
            stickKnobY = stickOriginY + ny * cl;
            float mag = Math.min(1f, len / r);
            // small dead zone, then a gentle curve for fine steering
            mag = mag < 0.08f ? 0f : (mag - 0.08f) / 0.92f;
            mag = mag * (0.6f + 0.4f * mag);
            stickX = nx * mag;
            stickY = -ny * mag;
        }

        private boolean menuTouch(MotionEvent e) {
            int action = e.getActionMasked();
            float[] p = toFrame(e.getX(e.getActionIndex()), e.getY(e.getActionIndex()));
            if (screen == Screen.PAUSE || screen == Screen.MENU || screen == Screen.GAME) {
                if (action == MotionEvent.ACTION_UP) {
                    performClick();
                    mapOps.add(new float[]{2, p[0], p[1]});
                    buzz();
                }
                return true;
            }
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mapPointer = e.getPointerId(0);
                    mapLastX = p[0];
                    mapLastY = p[1];
                    mapMoved = false;
                    pinch = 0f;
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (e.getPointerCount() >= 2) { pinch = spread(e); mapMoved = true; }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (e.getPointerCount() >= 2) {
                        float d = spread(e);
                        if (pinch > 0f && d > 0f) mapOps.add(new float[]{1, d / pinch});
                        pinch = d;
                    } else {
                        int i = e.findPointerIndex(mapPointer);
                        if (i < 0) break;
                        float[] q = toFrame(e.getX(i), e.getY(i));
                        float dx = q[0] - mapLastX, dy = q[1] - mapLastY;
                        if (Math.abs(dx) + Math.abs(dy) > 2.5f || mapMoved) {
                            mapMoved = true;
                            mapOps.add(new float[]{0, dx, dy});
                            mapLastX = q[0];
                            mapLastY = q[1];
                        }
                    }
                    break;
                case MotionEvent.ACTION_POINTER_UP: {
                    // keep panning with the finger that stays down
                    int left = e.getActionIndex() == 0 ? 1 : 0;
                    mapPointer = e.getPointerId(left);
                    float[] q = toFrame(e.getX(left), e.getY(left));
                    mapLastX = q[0];
                    mapLastY = q[1];
                    pinch = 0f;
                    break;
                }
                case MotionEvent.ACTION_UP:
                    performClick();
                    if (!mapMoved) { mapOps.add(new float[]{2, p[0], p[1]}); buzz(); }
                    mapPointer = -1;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    mapPointer = -1;
                    break;
                default: break;
            }
            return true;
        }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }

        private float spread(MotionEvent e) {
            return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
        }

        private float[] toFrame(float sx, float sy) {
            return new float[]{(sx - viewLeft) / viewScale, (sy - viewTop) / viewScale};
        }

        private boolean hit(float[] b, float x, float y, float slack) {
            float dx = x - b[0], dy = y - b[1], r = b[2] * slack;
            return dx * dx + dy * dy <= r * r;
        }

        private void buzz() {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }
}
