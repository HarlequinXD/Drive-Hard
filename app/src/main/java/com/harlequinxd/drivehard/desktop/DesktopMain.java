package com.harlequinxd.drivehard.desktop;

import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.map.MapData;
import com.harlequinxd.drivehard.core.ui.LoadScreen;
import com.harlequinxd.drivehard.core.ui.MapView;
import com.harlequinxd.drivehard.core.ui.MainMenu;
import com.harlequinxd.drivehard.core.GameAudio;
import com.harlequinxd.drivehard.core.Settings;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;

/**
 * Windows desktop front end: keyboard + mouse and Xbox controller (XInput).
 *
 * Game: WASD / arrows or left stick to walk and steer, W / RT gas, S / LT brake, Space / A / X
 * handbrake (held, while driving), mouse (click
 * the window to capture it) or right stick for the camera, Space / A jump, E / F / Y get in or
 * out, M / Tab / View for the map, Esc / Start for the pause menu, F11 fullscreen.
 * Main menu: arrows / WASD / D-pad to move, left and right to change options, Enter / A to
 * pick, Esc / B to go back; it opens first, and from the pause menu.
 * Map: drag / WASD / stick to pan, wheel / + - / triggers to zoom, Q E / LB RB to step through
 * places, click / Enter / A to pick a place and travel, Esc / B to go back.
 * The loading and fast-travel screens, the minimap, the HUD and the map are all drawn by the
 * shared core UI, so the desktop and Android builds look the same.
 */
public final class DesktopMain extends JPanel implements KeyListener, Runnable {
    private static final int W = 480, H = 320;
    private enum Screen { MENU, GAME, MAP, PAUSE }

    private final Set<Integer> keys = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Integer> taps = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<float[]> clicks = new ConcurrentLinkedQueue<>();
    private final AtomicInteger wheel = new AtomicInteger(), lookX = new AtomicInteger(), lookY = new AtomicInteger();
    private final AtomicInteger dragX = new AtomicInteger(), dragY = new AtomicInteger();
    private final XboxInput pad = new XboxInput();
    private final BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    private final int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    private final LoadScreen loadScreen = new LoadScreen(W, H);
    private final BufferedImage loadImage = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    private final MapView mapView = new MapView();
    private volatile String error = "";
    private volatile Game game;
    private volatile boolean running = true, travelling, imageReady;
    /** The player asked for a fresh island: the current game ends and a new seed is generated. */
    private volatile boolean newIsland;
    private volatile Screen screen = Screen.GAME;
    private int menuIndex;
    private boolean fullscreen;
    private JFrame window;
    private Rectangle windowedBounds;
    private int padPrev;
    private float hintTime = 9f;
    private float fps;

    // mouse capture for camera look
    private volatile boolean captured, dragging;
    private Robot robot;
    private int lastMouseX, lastMouseY;
    private final Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
            new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "none");

    private static final String[] MENU = {"RESUME", "MAP", "FULLSCREEN", "NEW ISLAND", "MAIN MENU", "QUIT TO DESKTOP"};
    private MainMenu mainMenu;
    private Settings settings;
    private DesktopAudio audio;

    private DesktopMain() {
        setBackground(Color.BLACK);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);       // Tab opens the map
        addKeyListener(this);
        try { robot = new Robot(); } catch (Throwable ignored) { robot = null; }
        addMouseWheelListener(e -> wheel.addAndGet(e.getWheelRotation()));
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (screen == Screen.GAME && game != null && game.missions.modal()) return;
                requestFocusInWindow();
                lastMouseX = e.getX(); lastMouseY = e.getY();
                if (screen == Screen.GAME && game != null && !travelling) { capture(true); return; }
                if (SwingUtilities.isLeftMouseButton(e)) dragging = true;
            }
            @Override public void mouseReleased(MouseEvent e) { dragging = false; }
            @Override public void mouseClicked(MouseEvent e) {
                if ((screen != Screen.GAME || (game != null && game.missions.modal())) && SwingUtilities.isLeftMouseButton(e) && e.getClickCount() >= 1)
                    clicks.add(new float[]{toInternalX(e.getX()), toInternalY(e.getY())});
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { look(e); }
            @Override public void mouseDragged(MouseEvent e) {
                if (captured) { look(e); return; }
                if (dragging) {
                    dragX.addAndGet(e.getX() - lastMouseX);
                    dragY.addAndGet(e.getY() - lastMouseY);
                }
                lastMouseX = e.getX(); lastMouseY = e.getY();
            }
        });
        setPreferredSize(new Dimension(1200, 800));
        // the load / travel screen animates on its own while the game thread is busy generating
        new javax.swing.Timer(33, e -> { if (game == null || travelling) repaint(); }).start();
    }

    private void look(MouseEvent e) {
        if (!captured) { lastMouseX = e.getX(); lastMouseY = e.getY(); return; }
        Point c = centre();
        int dx = e.getXOnScreen() - c.x, dy = e.getYOnScreen() - c.y;
        if (dx == 0 && dy == 0) return;
        lookX.addAndGet(dx);
        lookY.addAndGet(dy);
        if (robot != null) robot.mouseMove(c.x, c.y);
    }

    private Point centre() {
        Point p = new Point(getWidth() / 2, getHeight() / 2);
        try { SwingUtilities.convertPointToScreen(p, this); } catch (Throwable ignored) { }
        return p;
    }

    private void capture(boolean on) {
        if (robot == null) on = false;
        if (captured == on) return;
        captured = on;
        SwingUtilities.invokeLater(() -> {
            setCursor(captured ? blankCursor : Cursor.getDefaultCursor());
            if (captured) { Point c = centre(); robot.mouseMove(c.x, c.y); }
        });
        lookX.set(0);
        lookY.set(0);
    }

    public static void main(String[] args) {
        // the game data folder beside the game: the island and the chunks already visited
        com.harlequinxd.drivehard.core.data.WorldCache.root = com.harlequinxd.drivehard.core.data.WorldCache.desktopRoot();
        com.harlequinxd.drivehard.core.data.WorldCache.build = com.harlequinxd.drivehard.core.data.WorldCache.desktopBuild();
        for (String a : args) if (a.equals("--new-island")) saveSeed(Game.randomSeed());
        SwingUtilities.invokeLater(() -> {
            DesktopMain view = new DesktopMain();
            JFrame frame = new JFrame("Drive Hard");
            view.window = frame;
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent e) { view.running = false; }
                @Override public void windowDeactivated(WindowEvent e) { view.focusLost(); }
            });
            frame.setContentPane(view);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            view.requestFocusInWindow();
            new Thread(view, "drive-hard-loop").start();
        });
    }

    /** The island played last time (kept in the game data folder), or a new one on the first run. */
    private static long savedSeed() {
        java.io.File root = com.harlequinxd.drivehard.core.data.WorldCache.root;
        java.io.File f = root == null ? null : new java.io.File(root, "island-seed.txt");
        if (f != null && f.isFile()) {
            try {
                return Long.parseLong(new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8").trim());
            } catch (Exception ignored) {
            }
        }
        long seed = Game.randomSeed();
        saveSeed(seed);
        return seed;
    }

    private static void saveSeed(long seed) {
        java.io.File root = com.harlequinxd.drivehard.core.data.WorldCache.root;
        if (root == null) return;
        try {
            root.mkdirs();
            java.nio.file.Files.write(new java.io.File(root, "island-seed.txt").toPath(), Long.toString(seed).getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    private void focusLost() {
        keys.clear();
        capture(false);
        if (game != null && screen == Screen.GAME) { screen = Screen.PAUSE; menuIndex = 0; }
    }

    // ------------------------------------------------------------------ main loop

    @Override public void run() {
        try {
            long seed = savedSeed();
            while (running) {
                loadScreen.reset("Starting up");
                newIsland = false;
                Game g = new Game(seed, loadScreen::set);
                g.setResolution(W, H);
                if (settings == null) settings = Settings.load(new java.io.File(com.harlequinxd.drivehard.core.data.WorldCache.root, "settings.txt"));
                g.settings = settings;
                settings.apply(g);
                audio = new DesktopAudio(g.audio.mixer);
                audio.start();
                mainMenu = new MainMenu(g, settings, false);
                mainMenu.open();
                game = g;
                screen = Screen.MENU;
                play(g);
                audio.stop();
                if (newIsland) {
                    game = null;
                    g.dispose();
                    seed = Game.randomSeed();
                    saveSeed(seed);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
            error = "Error: " + e;
            travelling = true;
            repaint();
        } finally {
            if (game != null) game.dispose();
        }
    }

    /** Runs one island until the game is closed or a new island is asked for. */
    private void play(Game g) {
        long previous = System.nanoTime(), stat = previous;
        int frames = 0;
        while (running) {
            long start = System.nanoTime();
            float dt = Math.min(.05f, (start - previous) / 1_000_000_000f);
            previous = start;
            frameTaps.clear();
            Integer t;
            while ((t = taps.poll()) != null) frameTaps.add(t);
            boolean connected = pad.poll();
            int buttons = connected ? pad.buttons() : 0;
            int pressed = buttons & ~padPrev;
            padPrev = buttons;
            if (tapped(KeyEvent.VK_F11)) SwingUtilities.invokeLater(this::toggleFullscreen);
            switch (screen) {
                case MENU: menuFrame(g, dt, connected, pressed); break;
                case MAP: mapFrame(g, dt, connected, pressed); break;
                case PAUSE: pauseFrame(g, connected, pressed); break;
                default: gameFrame(g, dt, connected, pressed); break;
            }
            Screen now = screen;
            g.audio.screen(now == Screen.MENU ? GameAudio.SCREEN_MENU : now == Screen.MAP ? GameAudio.SCREEN_MAP
                    : now == Screen.PAUSE ? GameAudio.SCREEN_PAUSE : g.missions.modal() ? GameAudio.SCREEN_MENU : GameAudio.SCREEN_GAME,
                    now == Screen.PAUSE ? menuIndex : now == Screen.GAME && g.missions.modal() ? g.missions.choice : -1);
            repaint();
            frames++;
            if (start - stat > 1_000_000_000L) { fps = frames * 1_000_000_000f / (start - stat); frames = 0; stat = start; }
            long wait = 16_666_667L - (System.nanoTime() - start);
            if (wait > 0) java.util.concurrent.locks.LockSupport.parkNanos(wait);
            if (newIsland) break;
        }
    }

    // ------------------------------------------------------------------ the main menu

    private float stickRepeat;
    private boolean stickHeld;

    private void menuFrame(Game g, float dt, boolean connected, int pressed) {
        capture(false);
        MainMenu m = mainMenu;
        // the stick steps through the items like the D-pad, with a repeat when held
        float sy = connected ? pad.ly() : 0f, sx = connected ? pad.lx() : 0f;
        int stick = Math.abs(sy) > 0.6f ? (sy > 0 ? -1 : 1) * 2 : Math.abs(sx) > 0.6f ? (sx > 0 ? 1 : -1) : 0;
        if (stick == 0) { stickRepeat = 0f; stickHeld = false; }
        else {
            stickRepeat -= dt;
            if (stickRepeat <= 0f) {
                stickRepeat = stickHeld ? 0.12f : 0.35f;
                stickHeld = true;
                if (stick == -2) m.up(); else if (stick == 2) m.down(); else if (stick == 1) m.right(); else m.left();
            }
        }
        if (tapped(KeyEvent.VK_W) || tapped(KeyEvent.VK_UP) || (pressed & XboxInput.DPAD_UP) != 0) m.up();
        if (tapped(KeyEvent.VK_S) || tapped(KeyEvent.VK_DOWN) || (pressed & XboxInput.DPAD_DOWN) != 0) m.down();
        if (tapped(KeyEvent.VK_A) || tapped(KeyEvent.VK_LEFT) || (pressed & XboxInput.DPAD_LEFT) != 0) m.left();
        if (tapped(KeyEvent.VK_D) || tapped(KeyEvent.VK_RIGHT) || (pressed & XboxInput.DPAD_RIGHT) != 0) m.right();
        if (tapped(KeyEvent.VK_ENTER) || tapped(KeyEvent.VK_SPACE) || (pressed & (XboxInput.A | XboxInput.START)) != 0) m.select();
        if (tapped(KeyEvent.VK_ESCAPE) || tapped(KeyEvent.VK_BACK_SPACE) || (pressed & XboxInput.B) != 0) m.back();
        float[] c;
        while ((c = clicks.poll()) != null) m.tap(c[0], c[1]);
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
                running = false;
                SwingUtilities.invokeLater(() -> window.dispose());
                System.exit(0);
                return;
            default: break;
        }
        g.attract(dt);
        g.render();
        synchronized (image) {
            System.arraycopy(g.renderer.fb, 0, pixels, 0, pixels.length);
            m.draw(pixels, W, H, dt);
            imageReady = true;
        }
    }

    private void leaveMenu(Game g) {
        g.endAttract();
        g.audio.music(false);
        screen = Screen.GAME;
        keys.clear();
        hintTime = 9f;
    }

    private void openMenu(Game g, boolean story) {
        capture(false);
        g.missions.abandon();
        if (story) mainMenu.openStory(); else mainMenu.open();
        screen = Screen.MENU;
        clicks.clear();
    }

    /** A briefing or result screen is up: the game waits, the keys work its buttons. */
    private void missionScreen(Game g, boolean connected, int pressed) {
        if (tapped(KeyEvent.VK_A) || tapped(KeyEvent.VK_LEFT) || (pressed & XboxInput.DPAD_LEFT) != 0) g.missions.nav(-1);
        if (tapped(KeyEvent.VK_D) || tapped(KeyEvent.VK_RIGHT) || (pressed & XboxInput.DPAD_RIGHT) != 0) g.missions.nav(1);
        if (tapped(KeyEvent.VK_ENTER) || tapped(KeyEvent.VK_SPACE) || (pressed & XboxInput.A) != 0) g.missions.select();
        else if (tapped(KeyEvent.VK_ESCAPE) || (pressed & XboxInput.B) != 0) g.missions.back();
        float[] c;
        while ((c = clicks.poll()) != null) g.missions.tap(c[0], c[1]);
        if (g.missions.request) { g.missions.request = false; openMenu(g, true); return; }
        g.render();
        synchronized (image) {
            System.arraycopy(g.renderer.fb, 0, pixels, 0, pixels.length);
            mapView.drawMinimap(pixels, W, H, W - 50, 50, 42, g);
            mapView.drawHud(pixels, W, H, g, 6, 6);
            g.missions.draw(pixels, W, H);
            imageReady = true;
        }
    }

    private void gameFrame(Game g, float dt, boolean connected, int pressed) {
        if (g.missions.modal()) { capture(false); missionScreen(g, connected, pressed); return; }
        if (tapped(KeyEvent.VK_ESCAPE) || (pressed & XboxInput.START) != 0) {
            capture(false);
            screen = Screen.PAUSE;
            menuIndex = 0;
            return;
        }
        if (tapped(KeyEvent.VK_M) || tapped(KeyEvent.VK_TAB) || (pressed & XboxInput.BACK) != 0) {
            openMap(g);
            return;
        }
        float lx = connected ? pad.lx() : 0f, ly = connected ? pad.ly() : 0f;
        float rx = connected ? pad.rx() : 0f, ry = connected ? pad.ry() : 0f;
        float x = clamp(lx + (key(KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1 : 0) - (key(KeyEvent.VK_A, KeyEvent.VK_LEFT) ? 1 : 0));
        float y = clamp(ly + (key(KeyEvent.VK_W, KeyEvent.VK_UP) ? 1 : 0) - (key(KeyEvent.VK_S, KeyEvent.VK_DOWN) ? 1 : 0));
        boolean jump = tapped(KeyEvent.VK_SPACE) || (pressed & XboxInput.A) != 0;
        boolean car = tapped(KeyEvent.VK_E) || tapped(KeyEvent.VK_F) || tapped(KeyEvent.VK_ENTER) || (pressed & XboxInput.Y) != 0;
        float gas = connected ? pad.rt() : 0f, brake = connected ? pad.lt() : 0f;
        if (key(KeyEvent.VK_W, KeyEvent.VK_UP)) gas = 1f;
        if (key(KeyEvent.VK_S, KeyEvent.VK_DOWN)) brake = 1f;
        float yaw = -rx * 2.6f * dt - lookX.getAndSet(0) * 0.0045f;
        float pitch = -ry * 1.7f * dt - lookY.getAndSet(0) * 0.003f;
        boolean handbrake = key(KeyEvent.VK_SPACE, KeyEvent.VK_SPACE)
                || (connected && (pad.buttons() & (XboxInput.A | XboxInput.X)) != 0);
        g.update(dt, x, y, yaw, pitch, jump, x, gas, brake, car, handbrake);
        g.render();
        hintTime -= dt;
        synchronized (image) {
            System.arraycopy(g.renderer.fb, 0, pixels, 0, pixels.length);
            mapView.drawMinimap(pixels, W, H, W - 50, 50, 42, g);
            mapView.drawHud(pixels, W, H, g, 6, 6);
            g.missions.draw(pixels, W, H);
            mapView.drawHints(pixels, W, H, connected
                    ? "LS MOVE  RS CAMERA  RT GAS  LT BRAKE  A JUMP  Y CAR  VIEW MAP  START MENU"
                    : (captured ? "WASD MOVE  MOUSE CAMERA  SPACE JUMP  E CAR  M MAP  ESC MENU"
                    : "CLICK TO CAPTURE THE MOUSE  -  WASD MOVE  E CAR  M MAP  ESC MENU"), Math.min(1f, hintTime));
            imageReady = true;
        }
    }

    private void openMap(Game g) {
        capture(false);
        mapView.open(g);
        screen = Screen.MAP;
        clicks.clear();
        dragX.set(0);
        dragY.set(0);
        wheel.set(0);
    }

    private void mapFrame(Game g, float dt, boolean connected, int pressed) {
        mapView.hints = connected
                ? "LS PAN  LT RT ZOOM  LB RB PLACES  A SELECT  Y ME  B BACK"
                : "DRAG / WASD PAN  WHEEL ZOOM  Q E PLACES  CLICK / ENTER SELECT  ESC BACK";
        boolean back = tapped(KeyEvent.VK_ESCAPE) || (pressed & XboxInput.B) != 0;
        boolean toggle = tapped(KeyEvent.VK_M) || tapped(KeyEvent.VK_TAB) || (pressed & XboxInput.BACK) != 0;
        boolean confirm = tapped(KeyEvent.VK_ENTER) || tapped(KeyEvent.VK_SPACE) || (pressed & XboxInput.A) != 0;
        if (toggle) { screen = Screen.GAME; return; }
        if (mapView.dialog) {
            if (back) { mapView.dialog = false; }
            else if (confirm) { travel(g, mapView.selected); return; }
        } else {
            if (back) { screen = Screen.GAME; return; }
            if (confirm) mapView.pickAtCursor(g, H);
            if (tapped(KeyEvent.VK_Q) || (pressed & XboxInput.LEFT_SHOULDER) != 0) mapView.cycle(-1);
            if (tapped(KeyEvent.VK_E) || (pressed & XboxInput.RIGHT_SHOULDER) != 0) mapView.cycle(1);
            if (tapped(KeyEvent.VK_C) || (pressed & XboxInput.Y) != 0) { mapView.viewX = mapView.cursorX = g.mapX(); mapView.viewZ = mapView.cursorZ = g.mapZ(); }
            float ax = (connected ? pad.lx() + pad.rx() : 0f) + (key(KeyEvent.VK_D, KeyEvent.VK_RIGHT) ? 1 : 0) - (key(KeyEvent.VK_A, KeyEvent.VK_LEFT) ? 1 : 0);
            float az = (connected ? -pad.ly() - pad.ry() : 0f) + (key(KeyEvent.VK_S, KeyEvent.VK_DOWN) ? 1 : 0) - (key(KeyEvent.VK_W, KeyEvent.VK_UP) ? 1 : 0);
            if (connected) {
                if (pad.button(XboxInput.DPAD_LEFT)) ax -= 1f;
                if (pad.button(XboxInput.DPAD_RIGHT)) ax += 1f;
                if (pad.button(XboxInput.DPAD_UP)) az -= 1f;
                if (pad.button(XboxInput.DPAD_DOWN)) az += 1f;
            }
            if (ax != 0f || az != 0f) mapView.steer(g, clamp(ax), clamp(az), dt, H);
            float zoomIn = (connected ? pad.rt() : 0f) + (key(KeyEvent.VK_EQUALS, KeyEvent.VK_ADD) || key(KeyEvent.VK_PAGE_UP) ? 1f : 0f);
            float zoomOut = (connected ? pad.lt() : 0f) + (key(KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT) || key(KeyEvent.VK_PAGE_DOWN) ? 1f : 0f);
            mapView.zoomBy((float) Math.exp((zoomIn - zoomOut) * 2.2f * dt));
            int w = wheel.getAndSet(0);
            if (w != 0) mapView.zoomBy((float) Math.pow(1.25f, -w));
            float sx = scaleX(), sy = scaleY();
            int ddx = dragX.getAndSet(0), ddy = dragY.getAndSet(0);
            if (ddx != 0 || ddy != 0) mapView.pan(g, ddx * sx, ddy * sy, H);
        }
        float[] c;
        while ((c = clicks.poll()) != null) {
            String hit = mapView.tap(g, c[0], c[1], W, H);
            if (hit.equals("travel")) { travel(g, mapView.selected); return; }
            if (hit.equals("close")) { screen = Screen.GAME; return; }
        }
        synchronized (image) {
            mapView.drawFull(pixels, W, H, g);
            imageReady = true;
        }
    }

    private void pauseFrame(Game g, boolean connected, int pressed) {
        if (tapped(KeyEvent.VK_ESCAPE) || (pressed & (XboxInput.START | XboxInput.B)) != 0) { screen = Screen.GAME; return; }
        if (tapped(KeyEvent.VK_W) || tapped(KeyEvent.VK_UP) || (pressed & XboxInput.DPAD_UP) != 0) menuIndex = (menuIndex + MENU.length - 1) % MENU.length;
        if (tapped(KeyEvent.VK_S) || tapped(KeyEvent.VK_DOWN) || (pressed & XboxInput.DPAD_DOWN) != 0) menuIndex = (menuIndex + 1) % MENU.length;
        int choose = -1;
        if (tapped(KeyEvent.VK_ENTER) || tapped(KeyEvent.VK_SPACE) || (pressed & XboxInput.A) != 0) choose = menuIndex;
        float[] c;
        while ((c = clicks.poll()) != null)
            for (int i = 0; i < mapView.menuBoxes.size(); i++) {
                int[] b = mapView.menuBoxes.get(i);
                if (c[0] >= b[0] && c[0] < b[2] && c[1] >= b[1] && c[1] < b[3]) choose = menuIndex = i;
            }
        if (choose == 0) { screen = Screen.GAME; return; }
        if (choose == 1) { openMap(g); return; }
        if (choose == 2) SwingUtilities.invokeLater(this::toggleFullscreen);
        if (choose == 3) { newIsland = true; return; }
        if (choose == 4) { openMenu(g, false); return; }
        if (choose == 5) { running = false; SwingUtilities.invokeLater(() -> window.dispose()); System.exit(0); }
        g.render();
        synchronized (image) {
            System.arraycopy(g.renderer.fb, 0, pixels, 0, pixels.length);
            mapView.drawMenu(pixels, W, H, "PAUSED", MENU, menuIndex, new String[]{
                    "#KEYBOARD AND MOUSE",
                    "WASD / ARROWS   WALK, STEER",
                    "W / S           GAS / BRAKE",
                    "MOUSE           CAMERA",
                    "SPACE           JUMP",
                    "E / F           GET IN / OUT",
                    "M / TAB         MAP",
                    "F11             FULLSCREEN",
                    "",
                    "#XBOX CONTROLLER" + (connected ? "  (CONNECTED)" : ""),
                    "LS              WALK, STEER",
                    "RT / LT         GAS / BRAKE",
                    "RS              CAMERA",
                    "A  JUMP     Y  GET IN / OUT",
                    "VIEW  MAP   START  MENU",
            });
            imageReady = true;
        }
    }

    /** Fast travel behind the travel screen (the world around the destination is built first). */
    private void travel(Game g, MapData.Label destination) {
        if (destination == null) return;
        float km = (float) Math.hypot(destination.x - g.mapX(), destination.z - g.mapZ()) / 1000f;
        loadScreen.reset("Finding a street");
        loadScreen.heading("TRAVELLING TO " + destination.name.toUpperCase(), String.format("%.1f KM", km));
        travelling = true;
        repaint();
        try {
            g.fastTravel(destination, loadScreen::set);
        } finally {
            // hold the full bar for a moment so the screen never just flickers
            long until = System.nanoTime() + 400_000_000L;
            while (System.nanoTime() < until) java.util.concurrent.locks.LockSupport.parkNanos(20_000_000L);
            screen = Screen.GAME;
            mapView.dialog = false;
            keys.clear();
            hintTime = 4f;
            travelling = false;
        }
    }

    // ------------------------------------------------------------------ helpers

    private final java.util.HashSet<Integer> frameTaps = new java.util.HashSet<>();

    private boolean tapped(int code) { return frameTaps.contains(code); }

    private boolean key(int a) { return keys.contains(a); }

    private boolean key(int a, int b) { return keys.contains(a) || keys.contains(b); }

    private static float clamp(float x) { return Math.max(-1f, Math.min(1f, x)); }

    private int drawWidth() { return Math.min(getWidth(), (int) ((long) getHeight() * W / H)); }

    private float scaleX() { int dw = drawWidth(); return dw <= 0 ? 1f : W / (float) dw; }

    private float scaleY() { int dw = drawWidth(), dh = (int) ((long) dw * H / W); return dh <= 0 ? 1f : H / (float) dh; }

    private float toInternalX(int x) {
        int dw = drawWidth();
        return dw <= 0 ? -1f : (x - (getWidth() - dw) / 2f) * W / dw;
    }

    private float toInternalY(int y) {
        int dw = drawWidth(), dh = (int) ((long) dw * H / W);
        return dh <= 0 ? -1f : (y - (getHeight() - dh) / 2f) * H / dh;
    }

    private void toggleFullscreen() {
        Rectangle previousBounds = window.getBounds();
        Rectangle screenBounds = window.getGraphicsConfiguration().getBounds();
        window.dispose();
        fullscreen = !fullscreen;
        window.setUndecorated(fullscreen);
        window.setResizable(!fullscreen);
        if (fullscreen) {
            windowedBounds = previousBounds;
            window.setBounds(screenBounds);
        } else {
            window.setBounds(windowedBounds != null ? windowedBounds : new Rectangle(0, 0, 1200, 800));
            if (windowedBounds == null) window.setLocationRelativeTo(null);
            windowedBounds = null;
        }
        window.setVisible(true);
        window.toFront();
        requestFocusInWindow();
    }

    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int width = getWidth(), height = getHeight();
        int dw = drawWidth(), dh = (int) ((long) dw * H / W);
        int x = (width - dw) / 2, y = (height - dh) / 2;
        if (game == null || travelling) {
            int[] dst = ((DataBufferInt) loadImage.getRaster().getDataBuffer()).getData();
            System.arraycopy(loadScreen.render(), 0, dst, 0, dst.length);
            g.drawImage(loadImage, x, y, dw, dh, null);
            if (!error.isEmpty()) {
                g.setColor(new Color(255, 120, 110));
                g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
                g.drawString(error, x + 20, y + 30);
            }
            return;
        }
        if (imageReady) synchronized (image) { g.drawImage(image, x, y, dw, dh, null); }
    }

    @Override public void keyPressed(KeyEvent e) {
        if (keys.add(e.getKeyCode())) taps.add(e.getKeyCode());
    }

    @Override public void keyReleased(KeyEvent e) { keys.remove(e.getKeyCode()); }

    @Override public void keyTyped(KeyEvent e) { }
}
