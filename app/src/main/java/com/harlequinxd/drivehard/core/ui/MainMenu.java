package com.harlequinxd.drivehard.core.ui;

import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.Missions;
import com.harlequinxd.drivehard.core.Settings;

/**
 * The title screen and main menu, drawn over a live view of the island (the camera circling
 * high above the player while traffic and the weather go on) with the theme playing.
 *
 * Pages: MAIN (cruise, story mode, options, quit), STORY (chapter one's missions, locked until
 * the one before is passed, with best times and the briefing) and OPTIONS (music and effects
 * volume, texture mapping, polygon sorting). Keys, pad and touch all drive it: the platform
 * calls {@link #up()}, {@link #down()}, {@link #left()}, {@link #right()}, {@link #select()},
 * {@link #back()} or {@link #tap}, and reads {@link #poll()} for what to do.
 */
public final class MainMenu extends Paint {
    public static final int NONE = 0, CRUISE = 1, MISSION = 2, QUIT = 3;
    private static final int MAIN = 0, STORY = 1, OPTIONS = 2;
    private static final String[] MAIN_ITEMS = {"CRUISE", "STORY MODE", "OPTIONS", "QUIT"};
    private static final String[] MAIN_HELP = {
            "FREE ROAM THE ISLAND. NO RULES, NO CLOCK.",
            "CHAPTER 1: NEW IN TOWN. SIX JOBS FOR VINCE.",
            "SOUND, PICTURE AND POLYGONS.",
            "SEE YOU ON THE ROAD.",
    };
    private static final String[] OPT_ITEMS = {"MUSIC", "EFFECTS", "POLYGONS", "BACK"};
    private static final String[] OPT_HELP = {
            "VOLUME OF THE THEME.",
            "VOLUME OF CARS, FOOTSTEPS, WEATHER AND MENUS.",
            "DEPTH BUFFER: NOTHING SHOWS THROUGH. PS1 SORT: THE ORIGINAL ORDERING TABLE.",
            "",
    };

    private final Game g;
    private final Settings settings;
    private final boolean touch;
    private int page = MAIN, index, action = NONE;
    /** The mission picked on the story page (for {@link #MISSION}). */
    public int mission;
    private float t, pageT, hlY = -1f, quitT = -1f;
    private final int[] mainSaved = new int[1];
    private final java.util.List<int[]> boxes = new java.util.ArrayList<>();
    private final java.util.List<int[]> valueBoxes = new java.util.ArrayList<>();

    public MainMenu(Game g, Settings settings, boolean touch) {
        this.g = g;
        this.settings = settings;
        this.touch = touch;
    }

    /** Shows the menu (the theme starts, the world behind goes quiet). */
    public void open() {
        page = MAIN;
        index = 0;
        t = pageT = 0f;
        hlY = -1f;
        quitT = -1f;
        action = NONE;
        g.audio.music(true);
    }

    /** Shows the story page directly (after leaving a mission). */
    public void openStory() {
        open();
        go(STORY);
        index = Math.max(0, Math.min(Missions.LIST.length - 1, g.missions.current < 0 ? firstOpen() : g.missions.current));
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

    private int count() { return page == MAIN ? MAIN_ITEMS.length : page == STORY ? Missions.LIST.length + 1 : OPT_ITEMS.length; }

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
        index = (index + d + count()) % count();
        g.audio.uiMove();
    }

    public void left() { change(-1); }
    public void right() { change(1); }

    private void change(int d) {
        if (page != OPTIONS) return;
        switch (index) {
            case 0: settings.music = Math.max(0, Math.min(10, settings.music + d)); break;
            case 1: settings.effects = Math.max(0, Math.min(10, settings.effects + d)); break;
            case 2: settings.psxSort = !settings.psxSort; break;
            default: return;
        }
        settings.apply(g);
        settings.save();
        g.audio.uiMove();
    }

    public void select() {
        if (quitT >= 0f) return;
        if (page == MAIN) {
            switch (index) {
                case 0: g.audio.uiStart(); action = CRUISE; break;
                case 1: g.audio.uiSelect(); go(STORY); index = firstOpen(); break;
                case 2: g.audio.uiSelect(); go(OPTIONS); break;
                default: g.audio.uiBack(); quitT = 0f; break;
            }
        } else if (page == STORY) {
            if (index == Missions.LIST.length) { back(); return; }
            if (!Missions.unlocked(settings, index)) { g.audio.uiBack(); return; }
            g.audio.uiStart();
            mission = index;
            action = MISSION;
        } else {
            if (index == OPT_ITEMS.length - 1) { back(); return; }
            change(1);
        }
    }

    public void back() {
        if (quitT >= 0f) return;
        if (page != MAIN) { g.audio.uiBack(); go(MAIN); }
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

    /** Draws the menu over the frame (the game's view of the island is already in it). */
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
        // cinematic bars, a dark wash on the left for the menu, a soft vignette at the top
        float fadeIn = Math.min(1f, t / 0.8f);
        hfade(0, 0, w * 3 / 5, h, INK, (int) (215 * fadeIn), 0);
        vfade(0, 0, w, h / 4, INK, (int) (120 * fadeIn), 0);
        int bar = Math.max(10, h / 20);
        rect(0, 0, w, bar, 0xFF000000, 235);
        rect(0, h - bar - 4, w, h, 0xFF000000, 235);
        rect(0, h - bar - 5, w, h - bar - 4, RED, 200);
        if (page == MAIN) logo(w, h, bar);
        switch (page) {
            case MAIN: mainPage(w, h, bar); break;
            case STORY: storyPage(w, h, bar); break;
            default: optionsPage(w, h, bar); break;
        }
        footer(w, h, bar);
        if (quitT >= 0f) rect(0, 0, w, h, 0xFF000000, (int) Math.min(256, quitT / 0.6f * 256));
        else if (t < 0.5f) rect(0, 0, w, h, 0xFF000000, (int) ((1f - t / 0.5f) * 256));
    }

    private int logoScale(int h) { return h >= 300 ? 4 : 3; }

    private void logo(int w, int h, int bar) {
        int sc = logoScale(h);
        int x = 18, y = bar + 8;
        float slide = Math.min(1f, t / 0.6f);
        x -= Math.round((1f - slide) * (1f - slide) * 120f);
        // DRIVE in white, HARD in red, leaning forward, with a speed streak behind
        String a = "DRIVE ", b = "HARD";
        int aw = textW(a, sc);
        for (int i = 0; i < 4; i++) rect(x - 30 + i * 6, y + sc * 2 + i * sc, x - 6 + i * 6, y + sc * 2 + i * sc + Math.max(1, sc / 2), RED, 120 - i * 25);
        text(a, x, y, sc, WHITE, 256, true, 0xFF000000);
        text(b, x + aw, y, sc, RED, 256, true, 0xFF000000);
        // a glint sweeping across every few seconds
        float gl = (t % 4f) / 0.6f;
        if (gl < 1f) {
            int gx = x + Math.round(gl * (aw + textW(b, sc)));
            for (int yy = y; yy < y + sc * 7; yy++) for (int k = 0; k < sc; k++) plot(gx + k + (y + sc * 7 - yy) / 3, yy, 0xFFFFFFFF, 90);
        }
        text("PS1 / GBA ISLAND DRIVING", x + 2, y + sc * 7 + 5, 1, GREY, (int) (256 * slide), false, 0);
    }

    private int itemsTop(int h, int bar) { return bar + 8 + logoScale(h) * 7 + 24; }

    private void highlight(int x0, int y, int x1, int hgt) {
        if (hlY < 0f) hlY = y;
        hlY += (y - hlY) * 0.35f;
        int yy = Math.round(hlY);
        slant(x0 - 4, yy, x1, yy + hgt, RED, 235);
        rect(x0 - 10, yy, x0 - 7, yy + hgt, WHITE, 220);
    }

    private void mainPage(int w, int h, int bar) {
        int x0 = 26, y0 = itemsTop(h, bar), step = h >= 300 ? 30 : 24;
        int sc = 2;
        highlight(x0, y0 + index * step - 4, x0 + 150, sc * 7 + 8);
        for (int i = 0; i < MAIN_ITEMS.length; i++) {
            float in = Math.max(0f, Math.min(1f, (t - 0.25f - i * 0.07f) / 0.25f));
            int off = Math.round((1f - in) * 60f);
            boolean on = i == index;
            String label = i == 3 && touch ? "EXIT" : MAIN_ITEMS[i];
            text(label, x0 - off + (on ? 4 : 0), y0 + i * step, sc, on ? 0xFFFFFFFF : 0xFFC9D1D5, (int) (256 * in), true, 0xFF000000);
            boxes.add(new int[]{0, y0 + i * step - 6, x0 + 170, y0 + i * step + sc * 7 + 6, i});
        }
        String help = MAIN_HELP[index];
        int hy = y0 + MAIN_ITEMS.length * step + 4;
        rect(x0, hy, x0 + 2, hy + 9, RED, 255);
        text(help, x0 + 8, hy + 1, 1, GREY, 256, false, 0);
        // progress, top right
        int done = Integer.bitCount(settings.passed & ((1 << Missions.LIST.length) - 1));
        String p = "STORY " + done + " / " + Missions.LIST.length;
        text(p, w - textW(p, 1) - 10, bar + 6, 1, done == Missions.LIST.length ? GOLD : GREY, 256, false, 0);
    }

    private void storyPage(int w, int h, int bar) {
        int x0 = 22, y0 = bar + 8;
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
            String num = (i + 1) + ".";
            text(num, x0 - off, y, 1, on ? 0xFFFFFFFF : RED, (int) (256 * in), false, 0);
            String name = open ? Missions.LIST[i].name : "LOCKED";
            text(name, x0 - off + 14, y, 1, on ? 0xFFFFFFFF : open ? 0xFFD8DEE1 : 0xFF5C666C, (int) (256 * in), false, on ? 0xFF000000 : 0);
            if (passed) tick(x0 + listW - 12 - off, y, on ? 0xFFFFFFFF : 0xFF60D080);
            else if (!open) lock(x0 + listW - 12 - off, y, on ? 0xFFFFFFFF : 0xFF5C666C);
            boxes.add(new int[]{0, y - 4, x0 + listW, y + 11, i});
        }
        // the briefing panel on the right
        int px = x0 + listW + 18, pw = w - px - 14, py = listY - 4, ph = h - bar - 14 - py;
        if (pw < 120) return;
        float in = Math.min(1f, pageT / 0.3f);
        rect(px, py, px + pw, py + ph, INK, (int) (190 * in));
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

    private void optionsPage(int w, int h, int bar) {
        int x0 = 22, y0 = bar + 8;
        text("OPTIONS", x0, y0, 2, WHITE, 256, true, 0xFF000000);
        int listY = y0 + 30, step = h >= 300 ? 24 : 20, rowW = Math.min(w - 2 * x0, 330);
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
            if (i >= 3) continue;
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
                String v = settings.psxSort ? "PS1 SORT" : "DEPTH BUFFER";
                text(v, bx + (bw - textW(v, 1)) / 2, y, 1, on ? 0xFFFFFFFF : GOLD, a, false, on ? 0xFF000000 : 0);
            }
        }
        String help = OPT_HELP[index];
        if (!help.isEmpty()) {
            int hy = listY + OPT_ITEMS.length * step + 4;
            for (String line : wrap(help, w - x0 * 2 - 10, 1)) {
                rect(x0, hy, x0 + 2, hy + 9, RED, 255);
                text(line, x0 + 8, hy + 1, 1, GREY, 256, false, 0);
                hy += 11;
            }
        }
    }

    private void footer(int w, int h, int bar) {
        String s;
        if (touch) s = page == MAIN ? "TAP TO CHOOSE" : page == OPTIONS ? "TAP < > TO CHANGE   BACK TO RETURN" : "TAP A MISSION TO PLAY   BACK TO RETURN";
        else s = page == OPTIONS ? "ARROWS CHANGE   ENTER / A SELECT   ESC / B BACK" : "ARROWS MOVE   ENTER / A SELECT   ESC / B BACK";
        int y = h - bar + (bar - 7) / 2 - 2;
        text(s, 12, y, 1, GREY, 256, false, 0);
        String v = "V1.0";
        text(v, w - textW(v, 1) - 10, y, 1, 0xFF5C666C, 256, false, 0);
        if (touch && page != MAIN) {
            // an on-screen back button for touch
            int bx = w - 70, by = bar + 6;
            rect(bx, by, bx + 58, by + 16, INK, 200);
            frame(bx, by, bx + 58, by + 16, 0xFF56626A, 220);
            text("< BACK", bx + 10, by + 5, 1, WHITE, 256, false, 0);
            boxes.add(new int[]{bx, by, bx + 58, by + 16, -1});
        }
    }
}
