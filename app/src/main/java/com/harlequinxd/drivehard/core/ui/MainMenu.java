package com.harlequinxd.drivehard.core.ui;

import com.harlequinxd.drivehard.core.GameAudio;
import com.harlequinxd.drivehard.core.Missions;
import com.harlequinxd.drivehard.core.Settings;
import com.harlequinxd.drivehard.core.WorldSpec;
import com.harlequinxd.drivehard.core.tex.Noise;

/**
 * The title screen: the first thing the game shows, before any island is made. It draws its own
 * scene (a sunset coast road rushing toward the horizon, mountains and a skyline against the
 * sun, a car's tail lights) and runs without a world behind it.
 *
 * Pages: MAIN (cruise, story mode, options, quit); CRUISE, where a new island is set up before
 * it is made (map size, time of day, whether the clock runs, the weather, the traffic, or carry
 * on with the island already made); STORY (chapter one's missions); OPTIONS (sound, polygon
 * sorting and the map size for new islands). Keys, pad and touch all drive it: the platform calls
 * {@link #up()}, {@link #down()}, {@link #left()}, {@link #right()}, {@link #select()},
 * {@link #back()} or {@link #tap}, and reads {@link #poll()} for what to do.
 */
public final class MainMenu extends Paint {
    public static final int NONE = 0, CRUISE = 1, MISSION = 2, QUIT = 3;
    private static final int MAIN = 0, STORY = 1, OPTIONS = 2, SETUP = 3;
    private static final String[] MAIN_ITEMS = {"CRUISE", "STORY MODE", "OPTIONS", "QUIT"};
    private static final String[] MAIN_HELP = {
            "MAKE AN ISLAND AND FREE ROAM IT. NO RULES, NO CLOCK.",
            "CHAPTER 1: NEW IN TOWN. SIX JOBS FOR VINCE.",
            "SOUND, PICTURE, POLYGONS AND MAP SIZE.",
            "SEE YOU ON THE ROAD.",
    };
    // cruise set-up rows
    private static final int S_ISLAND = 0, S_SIZE = 1, S_TIME = 2, S_CLOCK = 3, S_WEATHER = 4, S_TRAFFIC = 5, S_START = 6, S_BACK = 7;
    private static final String[] SETUP_ITEMS = {"ISLAND", "MAP SIZE", "TIME OF DAY", "CLOCK", "WEATHER", "TRAFFIC", "START DRIVING", "BACK"};
    private static final String[] OPT_ITEMS = {"MUSIC", "EFFECTS", "POLYGONS", "MAP SIZE", "BACK"};
    private static final String[] OPT_HELP = {
            "VOLUME OF THE THEME.",
            "VOLUME OF CARS, FOOTSTEPS, WEATHER AND MENUS.",
            "DEPTH BUFFER: NOTHING SHOWS THROUGH. PS1 SORT: THE ORIGINAL ORDERING TABLE.",
            "THE SIZE OF NEW ISLANDS (STORY MODE TOO). BIGGER ISLANDS TAKE LONGER TO MAKE.",
            "",
    };

    private final GameAudio audio;
    private final Settings settings;
    private final boolean touch;
    private int page = MAIN, index, action = NONE;
    /** The mission picked on the story page (for {@link #MISSION}). */
    public int mission;
    /** With {@link #CRUISE}: make a new island (false: carry on with the current one). */
    public boolean newIsland = true;
    /** The island already made (shown on the cruise page), or null. */
    private String currentIsland;
    private boolean keepCurrent;
    private int currentSize = -1;
    private float t, pageT, hlY = -1f, quitT = -1f;
    private final int[] mainSaved = new int[1];
    private final java.util.List<int[]> boxes = new java.util.ArrayList<>();
    private final java.util.List<int[]> valueBoxes = new java.util.ArrayList<>();

    public MainMenu(GameAudio audio, Settings settings, boolean touch) {
        this.audio = audio;
        this.settings = settings;
        this.touch = touch;
    }

    /** The island already made (its name or size), so Cruise can carry on with it; null for none. */
    public void setIsland(String description, int sizeOrdinal) {
        currentIsland = description;
        currentSize = sizeOrdinal;
        keepCurrent = description != null;
    }

    /** Shows the menu (the theme starts). */
    public void open() {
        page = MAIN;
        index = 0;
        t = pageT = 0f;
        hlY = -1f;
        quitT = -1f;
        action = NONE;
        audio.music(true);
    }

    /** Shows the story page directly (after leaving a mission). */
    public void openStory(int current) {
        open();
        go(STORY);
        index = Math.max(0, Math.min(Missions.LIST.length - 1, current < 0 ? firstOpen() : current));
    }

    private int firstOpen() {
        for (int i = 0; i < Missions.LIST.length; i++) if ((settings.passed & (1 << i)) == 0) return Missions.unlocked(settings, i) ? i : 0;
        return 0;
    }

    /** What the player chose (once), or NONE. */
    public int poll() { int a = action; action = NONE; return a; }

    /** True on the first page (Back there leaves the game on Android). */
    public boolean onMainPage() { return page == MAIN && quitT < 0f; }

    public int highlighted() { return page * 16 + index; }

    private int count() {
        switch (page) {
            case MAIN: return MAIN_ITEMS.length;
            case STORY: return Missions.LIST.length + 1;
            case SETUP: return SETUP_ITEMS.length;
            default: return OPT_ITEMS.length;
        }
    }

    private void go(int p) {
        if (p == MAIN) index = mainSaved[0];
        else { if (page == MAIN) mainSaved[0] = index; index = 0; }
        page = p;
        pageT = 0f;
        hlY = -1f;
    }

    // ------------------------------------------------------------------ input

    public void up() { move(-1); }
    public void down() { move(1); }

    private void move(int d) {
        if (quitT >= 0f) return;
        int n = count();
        index = (index + d + n) % n;
        // the size can't change while carrying on with the island already made
        if (page == SETUP && index == S_SIZE && keepCurrent) index = (index + d + n) % n;
        if (page == SETUP && index == S_ISLAND && currentIsland == null) index = (index + d + n) % n;
        audio.uiMove();
    }

    public void left() { change(-1); }
    public void right() { change(1); }

    private static int wrap(int v, int n) { return ((v % n) + n) % n; }

    private void change(int d) {
        if (page == OPTIONS) {
            switch (index) {
                case 0: settings.music = Math.max(0, Math.min(10, settings.music + d)); break;
                case 1: settings.effects = Math.max(0, Math.min(10, settings.effects + d)); break;
                case 2: settings.psxSort = !settings.psxSort; break;
                case 3: settings.mapSize = wrap(settings.mapSize + d, 4); break;
                default: return;
            }
        } else if (page == SETUP) {
            switch (index) {
                case S_ISLAND: if (currentIsland == null) return; keepCurrent = !keepCurrent; break;
                case S_SIZE: if (keepCurrent) return; settings.mapSize = wrap(settings.mapSize + d, 4); break;
                case S_TIME: settings.timeOfDay = wrap(settings.timeOfDay + d, Settings.TIME_NAMES.length); break;
                case S_CLOCK: settings.clockRuns = !settings.clockRuns; break;
                case S_WEATHER: settings.weather = wrap(settings.weather + d, Settings.WEATHER_NAMES.length); break;
                case S_TRAFFIC: settings.traffic = wrap(settings.traffic + d, Settings.TRAFFIC_NAMES.length); break;
                default: return;
            }
        } else return;
        settings.apply(audio);
        settings.save();
        audio.uiMove();
    }

    public void select() {
        if (quitT >= 0f) return;
        if (page == MAIN) {
            switch (index) {
                case 0:
                    audio.uiSelect();
                    go(SETUP);
                    index = S_START;
                    break;
                case 1: audio.uiSelect(); go(STORY); index = firstOpen(); break;
                case 2: audio.uiSelect(); go(OPTIONS); break;
                default: audio.uiBack(); quitT = 0f; break;
            }
        } else if (page == STORY) {
            if (index == Missions.LIST.length) { back(); return; }
            if (!Missions.unlocked(settings, index)) { audio.uiBack(); return; }
            audio.uiStart();
            mission = index;
            // story mode plays on the island already made, or a new one of the chosen size
            newIsland = currentIsland == null;
            action = MISSION;
        } else if (page == SETUP) {
            if (index == S_BACK) { back(); return; }
            if (index == S_START) {
                audio.uiStart();
                newIsland = !keepCurrent || currentIsland == null;
                action = CRUISE;
                return;
            }
            change(1);
        } else {
            if (index == OPT_ITEMS.length - 1) { back(); return; }
            change(1);
        }
    }

    public void back() {
        if (quitT >= 0f) return;
        if (page != MAIN) { audio.uiBack(); go(MAIN); }
    }

    /** A tap or click in frame pixels. */
    public void tap(float x, float y) {
        for (int i = 0; i < valueBoxes.size(); i++) {
            int[] b = valueBoxes.get(i);
            if (in(b, x, y)) {
                index = b[4];
                change(x < (b[0] + b[2]) * 0.5f ? -1 : 1);
                return;
            }
        }
        for (int i = 0; i < boxes.size(); i++) {
            int[] b = boxes.get(i);
            if (in(b, x, y)) {
                if (b[4] == -1) { back(); return; }
                index = b[4];
                select();
                return;
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    private static final int RED = 0xFFE0263A, INK = 0xFF05080A, WHITE = 0xFFF4F1E8, GREY = 0xFF9AA6AC, GOLD = 0xFFFFC870;

    /** Draws the whole screen: the scene, then the menu over it. */
    public void draw(int[] fb, int w, int h, float dt) {
        begin(fb, w, h);
        t += dt;
        pageT += dt;
        if (quitT >= 0f) {
            quitT += dt;
            if (quitT > 0.6f) action = QUIT;
        }
        boxes.clear();
        valueBoxes.clear();
        scene(w, h);
        // the menu's side: a dark slanted panel on the left
        float fadeIn = Math.min(1f, t / 0.8f);
        int panelW = page == MAIN ? Math.max(190, w * 2 / 5) : page == SETUP ? Math.max(300, w * 17 / 20) : Math.max(260, w * 3 / 5);
        for (int y = 0; y < h; y++) {
            int x1 = panelW - (h - y) / 4;
            int a = (int) (200 * fadeIn);
            int row = y * w;
            for (int x = 0; x < Math.min(w, x1); x++) fb[row + x] = blend(fb[row + x], INK, x > x1 - 3 ? a / 2 : a);
            if (x1 >= 0 && x1 < w) fb[row + x1] = blend(fb[row + x1], RED, (int) (230 * fadeIn));
        }
        int bar = Math.max(10, h / 22);
        rect(0, h - bar - 3, w, h, 0xFF000000, 220);
        rect(0, h - bar - 4, w, h - bar - 3, RED, 200);
        switch (page) {
            case MAIN: logo(w, h); mainPage(w, h); break;
            case STORY: storyPage(w, h, bar); break;
            case SETUP: setupPage(w, h); break;
            default: optionsPage(w, h); break;
        }
        footer(w, h, bar);
        if (quitT >= 0f) rect(0, 0, w, h, 0xFF000000, (int) Math.min(256, quitT / 0.6f * 256));
        else if (t < 0.5f) rect(0, 0, w, h, 0xFF000000, (int) ((1f - t / 0.5f) * 256));
    }

    // ------------------------------------------------------------------ the scene

    /**
     * A coast road at sunset, drawn straight into the frame: a banded sky with stars coming out,
     * the striped sun on the horizon, two ridges of mountains and a city skyline with lights,
     * the sea to the right glinting, and the road rushing toward the sun with its dashes and
     * poles flying past. Pixel art: flat colour bands, ordered dither between them.
     */
    private void scene(int w, int h) {
        int hor = h * 11 / 20;                       // horizon row
        float speed = t * 1.6f;
        int sunX = w * 13 / 20, sunR = Math.max(26, h / 6);
        // sky: indigo to violet to orange, dithered bands
        int[] sky = {0xFF140C2E, 0xFF2A1450, 0xFF4E1C6A, 0xFF85285F, 0xFFC23A4C, 0xFFF06A3A, 0xFFFFA650};
        for (int y = 0; y < hor; y++) {
            float f = y / (float) hor * (sky.length - 1);
            int b = Math.min(sky.length - 2, (int) f);
            float fr = f - b;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float th = BAYER[(y & 3) * 4 + (x & 3)];
                fb[row + x] = fr > th ? sky[b + 1] : sky[b];
            }
        }
        // stars in the dark top, twinkling
        for (int k = 0; k < 70; k++) {
            int sx = (int) (Noise.rnd(k, 3, 911) * w), sy = (int) (Noise.rnd(k, 4, 911) * hor * 0.45f);
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (1.5f + Noise.rnd(k, 5, 911) * 3f) + k);
            plot(sx, sy, 0xFFFFF4E0, (int) (60 + 160 * tw));
        }
        // the sun: a disc of warm bands, the lower half cut by widening gaps
        for (int y = -sunR; y <= sunR; y++) {
            int yy = hor - sunR / 3 + y;
            if (yy < 0 || yy >= hor) continue;
            float v = (y + sunR) / (2f * sunR);
            int lower = y - sunR / 6;
            if (lower > 0) {
                int gap = 1 + lower / Math.max(2, sunR / 6);
                int period = 3 + lower / Math.max(2, sunR / 5);
                if (((lower + (int) (t * 6)) % period) < gap) continue;
            }
            int c = v < 0.3f ? 0xFFFFE07A : v < 0.6f ? 0xFFFFB04A : 0xFFFF6A4A;
            int half = (int) Math.sqrt(Math.max(0, sunR * sunR - y * y));
            for (int x = sunX - half; x <= sunX + half; x++) plot(x, yy, c, 256);
        }
        // far mountains (violet) and a nearer ridge (dark), scrolling slowly
        for (int x = 0; x < w; x++) {
            float u = (x + t * 4f) / w;
            int m1 = hor - (int) (h * (0.10f + 0.07f * ridge(u * 3.1f, 1) + 0.03f * ridge(u * 9f, 2)));
            for (int y = Math.max(0, m1); y < hor; y++) fb[y * w + x] = blend(fb[y * w + x], 0xFF3A1E5A, 230);
            float u2 = (x + t * 9f) / w;
            int m2 = hor - (int) (h * (0.04f + 0.05f * ridge(u2 * 4.3f, 3) + 0.02f * ridge(u2 * 13f, 4)));
            for (int y = Math.max(0, m2); y < hor; y++) fb[y * w + x] = 0xFF1C1030;
        }
        // the skyline on the left of the horizon, windows lighting up
        int cx0 = w / 12, cx1 = w * 9 / 20;
        for (int k = 0, x = cx0; x < cx1; k++) {
            int bw = 6 + (int) (Noise.rnd(k, 7, 913) * 12), bh = 8 + (int) (Noise.rnd(k, 8, 913) * h * 0.16f);
            for (int y = hor - bh; y < hor; y++)
                for (int xx = x; xx < Math.min(cx1, x + bw); xx++) {
                    boolean win = ((y - hor) % 3 == 0) && ((xx - x) % 3 == 1) && Noise.rnd(xx * 7 + y, k, 914) < 0.45f;
                    fb[y * w + xx] = win ? (Noise.rnd(xx, y, (int) (t * 0.5f) + 915) < 0.9f ? 0xFFFFD27A : 0xFF2A1840) : 0xFF120A22;
                }
            x += bw + 1;
        }
        // ground: the sea on the right with the sun's glint, the land on the left
        int roadL = w * 3 / 10, roadR = w * 7 / 10;
        for (int y = hor; y < h; y++) {
            float d = (y - hor + 1) / (float) (h - hor);          // 0 at the horizon, 1 at the bottom
            float z = 1f / d;                                         // depth
            int half = (int) ((roadR - roadL) * 0.5f * d);
            int mid = w / 2 + (int) ((sunX - w / 2) * (1f - d) * 0.6f);
            int left = mid - half, right = mid + half;
            int row = y * w;
            boolean stripe = ((int) (z * 2f + speed * 3f) & 1) == 0;
            for (int x = 0; x < w; x++) {
                int c;
                if (x >= left && x < right) {
                    // asphalt, edge lines, the dashed centre line
                    int e = Math.max(1, half / 18);
                    if (x < left + e || x >= right - e) c = 0xFFE8E2D0;
                    else if (Math.abs(x - mid) <= Math.max(0, half / 40) && stripe) c = 0xFFF2D25A;
                    else c = stripe ? 0xFF2E2A36 : 0xFF34303C;
                } else if (x >= right) {
                    // the sea: dark water, a band of glints under the sun
                    float glint = 1f - Math.min(1f, Math.abs(x - sunX) / (float) (sunR * (0.6f + d)));
                    float sp = Noise.rnd(x >> 1, (int) (y + t * 8f) >> 1, 917);
                    c = glint > 0f && sp < glint * 0.7f ? 0xFFFFB86A : (((x + y) & 1) == 0 ? 0xFF1A2448 : 0xFF202C54);
                } else {
                    // the verge and fields: banded, rushing past
                    boolean band = ((int) (z * 1.3f + speed * 2f) & 1) == 0;
                    c = band ? 0xFF1E3A2A : 0xFF24442E;
                }
                fb[row + x] = c;
            }
            // haze toward the horizon
            int haze = (int) (200 * (1f - Math.min(1f, d * 3f)));
            if (haze > 0) for (int x = 0; x < w; x++) fb[row + x] = blend(fb[row + x], 0xFFB04A58, haze);
        }
        // utility poles flying past on the left verge
        for (int k = 0; k < 6; k++) {
            float zz = ((k * 1.0f - speed * 0.9f) % 6f + 6f) % 6f + 0.35f;  // depth units
            float d = 1f / zz;
            if (d > 1.4f) continue;
            int y = hor + (int) ((h - hor) * Math.min(1f, d));
            int mid = w / 2 + (int) ((sunX - w / 2) * (1f - Math.min(1f, d)) * 0.6f);
            int x = mid - (int) ((roadR - roadL) * 0.5f * d) - (int) (w * 0.08f * d);
            int ph = (int) (h * 0.55f * d), pw = Math.max(1, (int) (3 * d));
            rect(x - pw, y - ph, x + pw, y, 0xFF140C10, 256);
            rect(x - pw * 5, y - ph + Math.max(1, (int) (4 * d)), x + pw * 5, y - ph + Math.max(2, (int) (6 * d)), 0xFF140C10, 256);
        }
        // the car: tail lights glowing at the bottom of the road
        int cw = w / 6, chh = h / 9, cxm = w / 2, cy = h - Math.max(10, h / 22) - 6;
        float bob = (float) Math.sin(t * 9f) * 0.8f;
        int top = cy - chh + Math.round(bob);
        rect(cxm - cw / 2, top + chh / 3, cxm + cw / 2, cy + Math.round(bob), 0xFF2A0A12, 256);       // body
        rect(cxm - cw * 3 / 8, top, cxm + cw * 3 / 8, top + chh / 3, 0xFF1A0610, 256);               // cabin
        rect(cxm - cw * 3 / 10, top + 2, cxm + cw * 3 / 10, top + chh / 3 - 1, 0xFF3A2A4A, 256);      // rear window
        int tl = Math.max(4, cw / 6);
        for (int s = -1; s <= 1; s += 2) {
            int lx = cxm + s * (cw / 2 - tl / 2 - 2);
            rect(lx - tl / 2, top + chh / 2, lx + tl / 2, top + chh / 2 + 3, 0xFFFF3040, 256);
            // glow
            for (int r = 1; r < 6; r++) frame(lx - tl / 2 - r, top + chh / 2 - r, lx + tl / 2 + r, top + chh / 2 + 3 + r, 0xFFFF3040, 70 - r * 12);
        }
        rect(cxm - cw / 2 - 2, cy + Math.round(bob), cxm - cw / 2 + cw / 6, cy + 3, 0xFF050305, 256);    // tyres
        rect(cxm + cw / 2 - cw / 6, cy + Math.round(bob), cxm + cw / 2 + 2, cy + 3, 0xFF050305, 256);
    }

    private static final float[] BAYER = {0.03f, 0.53f, 0.15f, 0.65f, 0.78f, 0.28f, 0.9f, 0.4f, 0.21f, 0.71f, 0.09f, 0.59f, 0.96f, 0.46f, 0.84f, 0.34f};

    /** Smooth ridge noise 0..1. */
    private static float ridge(float x, int s) {
        int i = (int) Math.floor(x);
        float f = x - i;
        f = f * f * (3 - 2 * f);
        float a = Noise.rnd(i, s, 919), b = Noise.rnd(i + 1, s, 919);
        return a + (b - a) * f;
    }

    // ------------------------------------------------------------------ pages

    private int logoScale(int h) { return h >= 300 ? 5 : 4; }

    private void logo(int w, int h) {
        int sc = logoScale(h);
        int x = 18, y = 14;
        float slide = Math.min(1f, t / 0.6f);
        x -= Math.round((1f - slide) * (1f - slide) * 140f);
        String a = "DRIVE", b = "HARD";
        // speed streaks behind
        for (int i = 0; i < 5; i++) rect(x - 40 + i * 7, y + sc * 3 + i * sc, x - 6 + i * 7, y + sc * 3 + i * sc + Math.max(1, sc / 2), RED, 140 - i * 25);
        text(a, x, y, sc, WHITE, 256, true, 0xFF000000);
        text(b, x + sc * 5, y + sc * 8, sc, RED, 256, true, 0xFF000000);
        float gl = (t % 4f) / 0.6f;
        if (gl < 1f) {
            int gx = x + Math.round(gl * textW(a, sc));
            for (int yy = y; yy < y + sc * 15; yy++) for (int k = 0; k < sc; k++) plot(gx + k + (y + sc * 15 - yy) / 3, yy, 0xFFFFFFFF, 80);
        }
        text("ISLAND STREET RACING", x + 2, y + sc * 16 + 2, 1, GOLD, (int) (256 * slide), false, 0xFF000000);
    }

    private void highlight(int x0, int y, int x1, int hgt) {
        if (hlY < 0f) hlY = y;
        hlY += (y - hlY) * 0.35f;
        int yy = Math.round(hlY);
        slant(x0 - 4, yy, x1, yy + hgt, RED, 235);
        rect(x0 - 10, yy, x0 - 7, yy + hgt, WHITE, 220);
    }

    private void mainPage(int w, int h) {
        int sc = logoScale(h);
        int x0 = 26, y0 = 14 + sc * 16 + 22, step = h >= 300 ? 30 : 24;
        highlight(x0, y0 + index * step - 4, x0 + 150, 2 * 7 + 8);
        for (int i = 0; i < MAIN_ITEMS.length; i++) {
            float in = Math.max(0f, Math.min(1f, (t - 0.25f - i * 0.07f) / 0.25f));
            int off = Math.round((1f - in) * 60f);
            boolean on = i == index;
            String label = i == 3 && touch ? "EXIT" : MAIN_ITEMS[i];
            text(label, x0 - off + (on ? 4 : 0), y0 + i * step, 2, on ? 0xFFFFFFFF : 0xFFC9D1D5, (int) (256 * in), true, 0xFF000000);
            boxes.add(new int[]{0, y0 + i * step - 6, x0 + 170, y0 + i * step + 2 * 7 + 6, i});
        }
        int hy = y0 + MAIN_ITEMS.length * step + 4;
        for (String line : wrap(MAIN_HELP[index], Math.max(150, w * 2 / 5 - x0 - 30), 1)) {
            rect(x0, hy, x0 + 2, hy + 9, RED, 255);
            text(line, x0 + 8, hy + 1, 1, GREY, 256, false, 0);
            hy += 11;
        }
        int done = Integer.bitCount(settings.passed & ((1 << Missions.LIST.length) - 1));
        String p = "STORY " + done + " / " + Missions.LIST.length;
        text(p, w - textW(p, 1) - 10, 8, 1, done == Missions.LIST.length ? GOLD : WHITE, 256, false, 0xFF000000);
        if (currentIsland != null) {
            String s = "ISLAND: " + currentIsland;
            text(s, w - textW(s, 1) - 10, 20, 1, GREY, 256, false, 0xFF000000);
        }
    }

    private String sizeText(int ord) {
        WorldSpec.Size s = WorldSpec.Size.values()[ord];
        int c = s.minCities();
        return s.label + "  " + s.km() + " KM  " + (s == WorldSpec.Size.MEGA ? c + "+ CITIES" : c + (c == 1 ? " CITY" : " CITIES"));
    }

    private String setupValue(int i) {
        switch (i) {
            case S_ISLAND: return currentIsland == null ? "NEW ISLAND" : keepCurrent ? "KEEP THIS ONE" : "MAKE A NEW ONE";
            case S_SIZE: return sizeText(keepCurrent && currentSize >= 0 ? currentSize : settings.mapSize);
            case S_TIME: {
                double hh = Settings.TIME_HOURS[settings.timeOfDay];
                int H = (int) hh, M = (int) Math.round((hh - H) * 60);
                return Settings.TIME_NAMES[settings.timeOfDay] + "  " + (H < 10 ? "0" : "") + H + ":" + (M < 10 ? "0" : "") + M;
            }
            case S_CLOCK: return settings.clockRuns ? "DAY AND NIGHT GO BY" : "STOPPED";
            case S_WEATHER: return Settings.WEATHER_NAMES[settings.weather];
            case S_TRAFFIC: return Settings.TRAFFIC_NAMES[settings.traffic];
            default: return "";
        }
    }

    private static final String[] SETUP_HELP = {
            "CARRY ON WITH THE ISLAND YOU HAVE, OR MAKE A FRESH ONE FROM A NEW SEED.",
            "SMALL AND MEDIUM: ONE CITY. LARGE: TWO. MEGA: THREE OR MORE, TAKES A WHILE TO MAKE.",
            "WHERE THE SUN IS WHEN YOU START.",
            "LET THE HOURS PASS, OR KEEP THE LIGHT YOU PICKED.",
            "CHANGING: SHOWERS, FOG AND STORMS COME AND GO. OR PICK ONE TO STAY.",
            "HOW BUSY THE ROADS ARE.",
            "MAKE THE ISLAND AND GO.",
            "",
    };

    private void setupPage(int w, int h) {
        int x0 = 22, y0 = 12;
        text("CRUISE", x0, y0, 3, WHITE, 256, true, 0xFF000000);
        text("SET UP YOUR ISLAND", x0 + 2, y0 + 25, 1, RED, 256, false, 0xFF000000);
        int listY = y0 + 42, step = h >= 300 ? 22 : 17, rowW = Math.min(w - 2 * x0, 360);
        int vx = x0 + 104, vw = rowW - (vx - x0) - 14;
        highlight(x0, listY + index * step - 4, x0 + rowW, 15);
        for (int i = 0; i < SETUP_ITEMS.length; i++) {
            float in = Math.max(0f, Math.min(1f, (pageT - i * 0.04f) / 0.2f));
            int off = Math.round((1f - in) * 40f);
            int y = listY + i * step;
            boolean on = i == index;
            int a = (int) (256 * in);
            boolean dim = (i == S_SIZE && keepCurrent) || (i == S_ISLAND && currentIsland == null);
            if (i >= S_START) {
                String label = SETUP_ITEMS[i];
                text(label, x0 + 6 - off, y, i == S_START ? 2 : 1, on ? 0xFFFFFFFF : i == S_START ? GOLD : GREY, a, i == S_START, 0xFF000000);
                boxes.add(new int[]{0, y - 5, x0 + rowW, y + 14, i});
                continue;
            }
            text(SETUP_ITEMS[i], x0 + 6 - off, y, 1, on ? 0xFFFFFFFF : dim ? 0xFF5C666C : 0xFFC9D1D5, a, false, on ? 0xFF000000 : 0);
            boxes.add(new int[]{0, y - 5, vx - 4, y + 12, i});
            valueBoxes.add(new int[]{vx - 4, y - 5, vx + vw + 8, y + 12, i});
            int vc = dim ? 0xFF5C666C : on ? 0xFFFFFFFF : GOLD;
            if (!dim) {
                text("<", vx - off, y, 1, on ? 0xFFFFFFFF : GREY, a, false, 0);
                text(">", vx + vw - off, y, 1, on ? 0xFFFFFFFF : GREY, a, false, 0);
            }
            String v = setupValue(i);
            text(v, vx + 12 + (vw - 22 - textW(v, 1)) / 2 - off, y, 1, vc, a, false, on ? 0xFF000000 : 0);
        }
        // the sizes side by side, the chosen one lit, its cities as dots
        int sizeOrd = keepCurrent && currentSize >= 0 ? currentSize : settings.mapSize;
        int gy = listY + SETUP_ITEMS.length * step + 6, gx = x0 + 6;
        int[] side = {8, 14, 22, 34};
        for (int k = 0; k < 4; k++) {
            int s = side[k];
            int yy = gy + 34 - s;
            boolean lit = k == sizeOrd;
            rect(gx, yy, gx + s, yy + s, lit ? 0xFF2E7A4A : 0xFF1A2A24, lit ? 240 : 160);
            frame(gx, yy, gx + s, yy + s, lit ? WHITE : 0xFF4A5A54, 256);
            int cities = WorldSpec.Size.values()[k].minCities();
            for (int c = 0; c < cities; c++) {
                int cx = gx + 2 + (int) (Noise.rnd(k, c, 921) * (s - 5)), cy = yy + 2 + (int) (Noise.rnd(c, k, 922) * (s - 5));
                rect(cx, cy, cx + 2, cy + 2, lit ? GOLD : 0xFF6A6A5A, 256);
            }
            gx += s + 10;
        }
        String help = SETUP_HELP[index];
        if (!help.isEmpty()) {
            int hy = gy + 40;
            for (String line : wrap(help, Math.min(w - x0 * 2 - 10, rowW), 1)) {
                rect(x0, hy, x0 + 2, hy + 9, RED, 255);
                text(line, x0 + 8, hy + 1, 1, GREY, 256, false, 0);
                hy += 11;
            }
        }
    }

    private void storyPage(int w, int h, int bar) {
        int x0 = 22, y0 = 12;
        text("STORY MODE", x0, y0, 2, WHITE, 256, true, 0xFF000000);
        text(Missions.CHAPTER, x0, y0 + 18, 1, RED, 256, false, 0);
        int listY = y0 + 32, step = h >= 300 ? 19 : 15, listW = Math.min(170, w * 2 / 5);
        int n = Missions.LIST.length;
        highlight(x0, listY + index * step - 3, x0 + listW, 13);
        for (int i = 0; i <= n; i++) {
            float in = Math.max(0f, Math.min(1f, (pageT - i * 0.05f) / 0.2f));
            int off = Math.round((1f - in) * 40f);
            int y = listY + i * step;
            boolean on = i == index;
            if (i == n) {
                text("BACK", x0 - off + 14, y, 1, on ? 0xFFFFFFFF : GREY, (int) (256 * in), false, on ? 0xFF000000 : 0);
                boxes.add(new int[]{0, y - 4, x0 + listW, y + 11, i});
                continue;
            }
            boolean open = Missions.unlocked(settings, i), passed = (settings.passed & (1 << i)) != 0;
            text((i + 1) + ".", x0 - off, y, 1, on ? 0xFFFFFFFF : RED, (int) (256 * in), false, 0);
            String name = open ? Missions.LIST[i].name : "LOCKED";
            text(name, x0 - off + 14, y, 1, on ? 0xFFFFFFFF : open ? 0xFFD8DEE1 : 0xFF5C666C, (int) (256 * in), false, on ? 0xFF000000 : 0);
            if (passed) tick(x0 + listW - 12 - off, y, on ? 0xFFFFFFFF : 0xFF60D080);
            else if (!open) lock(x0 + listW - 12 - off, y, on ? 0xFFFFFFFF : 0xFF5C666C);
            boxes.add(new int[]{0, y - 4, x0 + listW, y + 11, i});
        }
        String isl = currentIsland != null ? "ON YOUR ISLAND: " + currentIsland : "ON A NEW " + sizeText(settings.mapSize) + " ISLAND";
        text(isl, x0, listY + (n + 1) * step + 4, 1, GREY, 256, false, 0xFF000000);
        // the briefing panel on the right
        int px = x0 + listW + 18, pw = w - px - 14, py = listY - 4, ph = h - bar - 14 - py;
        if (pw < 120) return;
        float in = Math.min(1f, pageT / 0.3f);
        rect(px, py, px + pw, py + ph, INK, (int) (210 * in));
        rect(px, py, px + 2, py + ph, RED, 255);
        if (index >= n) {
            text("RETURN TO THE MAIN MENU", px + 10, py + 10, 1, GREY, 256, false, 0);
            return;
        }
        Missions.Def d = Missions.LIST[index];
        boolean open = Missions.unlocked(settings, index);
        text("MISSION " + (index + 1), px + 10, py + 8, 1, RED, 256, false, 0);
        text(open ? d.name : "? ? ?", px + 10, py + 19, 2, WHITE, 256, true, 0xFF000000);
        int y = py + 40;
        if (!open) {
            for (String line : wrap("PASS MISSION " + index + " TO UNLOCK THIS JOB.", pw - 20, 1)) { text(line, px + 10, y, 1, GREY, 256, false, 0); y += 10; }
            return;
        }
        for (String line : wrap(d.brief, pw - 20, 1)) {
            if (y > py + ph - 34) break;
            text(line, px + 10, y, 1, 0xFFD0D8DC, 256, false, 0);
            y += 10;
        }
        int by = py + ph - 26;
        rect(px + 10, by - 4, px + pw - 10, by - 3, 0xFF2A3238, 255);
        text(d.goal, px + 10, by + 1, 1, 0xFFFFD070, 256, false, 0);
        float best = settings.best[index];
        String st = (settings.passed & (1 << index)) != 0 ? "PASSED" + (best > 0 ? "   BEST " + clock(best) : "") : "NOT YET PASSED";
        text(st, px + 10, by + 12, 1, (settings.passed & (1 << index)) != 0 ? 0xFF80E0A0 : GREY, 256, false, 0);
    }

    private static String clock(float t) {
        int c = Math.round(t * 100f), s = c / 100;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60) + "." + (c % 100 < 10 ? "0" : "") + (c % 100);
    }

    private void tick(int x, int y, int c) {
        int[][] p = {{0, 3}, {1, 4}, {2, 5}, {3, 4}, {4, 3}, {5, 2}, {6, 1}, {7, 0}};
        for (int[] q : p) { plot(x + q[0], y + q[1] + 1, c, 256); plot(x + q[0], y + q[1] + 2, c, 256); }
    }

    private void lock(int x, int y, int c) {
        frame(x + 1, y, x + 6, y + 4, c, 256);
        rect(x, y + 3, x + 7, y + 8, c, 256);
    }

    private void optionsPage(int w, int h) {
        int x0 = 22, y0 = 12;
        text("OPTIONS", x0, y0, 3, WHITE, 256, true, 0xFF000000);
        int listY = y0 + 36, step = h >= 300 ? 24 : 20, rowW = Math.min(w - 2 * x0, 340);
        highlight(x0, listY + index * step - 4, x0 + rowW, 15);
        int vx = x0 + 100;
        for (int i = 0; i < OPT_ITEMS.length; i++) {
            float in = Math.max(0f, Math.min(1f, (pageT - i * 0.05f) / 0.2f));
            int off = Math.round((1f - in) * 40f);
            int y = listY + i * step;
            boolean on = i == index;
            int a = (int) (256 * in);
            text(OPT_ITEMS[i], x0 + 6 - off, y, 1, on ? 0xFFFFFFFF : 0xFFC9D1D5, a, false, on ? 0xFF000000 : 0);
            boxes.add(new int[]{0, y - 5, vx - 4, y + 12, i});
            if (i >= OPT_ITEMS.length - 1) continue;
            int vw = rowW - (vx - x0) - 14;
            valueBoxes.add(new int[]{vx - 4, y - 5, vx + vw + 8, y + 12, i});
            text("<", vx - off, y, 1, on ? 0xFFFFFFFF : GREY, a, false, 0);
            text(">", vx + vw - off, y, 1, on ? 0xFFFFFFFF : GREY, a, false, 0);
            int bx = vx + 12 - off, bw = vw - 22;
            if (i < 2) {
                int v = i == 0 ? settings.music : settings.effects;
                for (int k = 0; k < 10; k++) {
                    int sx = bx + k * bw / 10, ex = bx + (k + 1) * bw / 10 - 2;
                    int segH = 3 + k / 2;
                    rect(sx, y + 7 - segH, ex, y + 7, k < v ? (on ? 0xFFFFFFFF : GOLD) : 0xFF2A3238, a);
                }
            } else {
                String v = i == 2 ? (settings.psxSort ? "PS1 SORT" : "DEPTH BUFFER") : sizeText(settings.mapSize);
                text(v, bx + (bw - textW(v, 1)) / 2, y, 1, on ? 0xFFFFFFFF : GOLD, a, false, on ? 0xFF000000 : 0);
            }
        }
        String help = OPT_HELP[index];
        if (!help.isEmpty()) {
            int hy = listY + OPT_ITEMS.length * step + 4;
            for (String line : wrap(help, Math.min(w - x0 * 2 - 10, rowW), 1)) {
                rect(x0, hy, x0 + 2, hy + 9, RED, 255);
                text(line, x0 + 8, hy + 1, 1, GREY, 256, false, 0);
                hy += 11;
            }
        }
    }

    private void footer(int w, int h, int bar) {
        String s;
        boolean values = page == OPTIONS || page == SETUP;
        if (touch) s = page == MAIN ? "TAP TO CHOOSE" : values ? "TAP < > TO CHANGE   BACK TO RETURN" : "TAP A MISSION TO PLAY   BACK TO RETURN";
        else s = values ? "ARROWS CHANGE   ENTER / A SELECT   ESC / B BACK" : "ARROWS MOVE   ENTER / A SELECT   ESC / B BACK";
        int y = h - bar + (bar - 7) / 2 - 1;
        text(s, 12, y, 1, GREY, 256, false, 0);
        String v = "V1.1";
        text(v, w - textW(v, 1) - 10, y, 1, 0xFF5C666C, 256, false, 0);
        if (touch && page != MAIN) {
            int bx = w - 70, by = 8;
            rect(bx, by, bx + 58, by + 16, INK, 200);
            frame(bx, by, bx + 58, by + 16, 0xFF56626A, 220);
            text("< BACK", bx + 10, by + 5, 1, WHITE, 256, false, 0);
            boxes.add(new int[]{bx, by, bx + 58, by + 16, -1});
        }
    }
}
