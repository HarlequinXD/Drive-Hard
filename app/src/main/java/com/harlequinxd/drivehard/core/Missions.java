package com.harlequinxd.drivehard.core;

import com.harlequinxd.drivehard.core.game.Vehicle;
import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.road.RoadEdge;
import com.harlequinxd.drivehard.core.sim.Traffic;
import com.harlequinxd.drivehard.core.ui.Paint;

import java.util.Random;

/**
 * Story mode, chapter one: six driving jobs in the style of Driver's undercover missions. Each
 * has a briefing, a countdown, an objective with a timer and a marker (a light beam in the
 * world, a blip on the minimap) and a result screen. Places are picked on the island's road
 * network around wherever the player starts, so the missions work on any island seed.
 *
 * While a briefing or result is up, {@link #modal()} is true: the platform freezes the game,
 * sends the menu keys and taps here, and leaves when {@link #request} asks for the main menu.
 */
public final class Missions extends Paint {
    public static final int NONE = 0, BRIEF = 1, RUN = 2, PASSED = 3, FAILED = 4;
    /** Mission types. */
    static final int REACH = 0, RUSH = 1, CIRCUIT = 2, FRAGILE = 3, TAIL = 4, GETAWAY = 5;
    public static final String CHAPTER = "CHAPTER 1  NEW IN TOWN";

    public static final class Def {
        public final String name, brief, goal;
        final int kind;
        Def(String name, int kind, String goal, String brief) { this.name = name; this.kind = kind; this.goal = goal; this.brief = brief; }
    }

    public static final Def[] LIST = {
            new Def("WHEELS", REACH, "FIND A CAR AND MEET YOUR CONTACT",
                    "You just got off the boat with nothing but the clothes on your back. Vince runs the garage across town and he needs a driver. Find yourself some wheels, any car will do, and meet him before he gives up on you."),
            new Def("RUSH JOB", RUSH, "DELIVER THE PACKAGE BEFORE TIME RUNS OUT",
                    "Vince has a package that has to be across the island in a hurry. No questions, no stopping for lights. Get it to the drop and pull up in the marker before the clock runs out."),
            new Def("THE CIRCUIT", CIRCUIT, "HIT EVERY CHECKPOINT",
                    "Word is there is a street race tonight and the boys want to see what you can do. Hit every checkpoint in order. Each one buys you a little more time, so keep your foot down."),
            new Def("FRAGILE", FRAGILE, "DELIVER THE CARGO WITHOUT BREAKING IT",
                    "The cargo in the back is worth more than the car and it does not like bumps. Get it to the buyer in one piece. Every knock cracks something, and if you leave the car you leave the cargo."),
            new Def("SHADOW", TAIL, "FOLLOW THE CAR WITHOUT BEING SEEN",
                    "Somebody has been skimming from Vince. We think it is the driver of the car up ahead. Follow them and see where they go. Stay close enough not to lose them, but not so close they spot you."),
            new Def("HEAT", GETAWAY, "GET TO THE SAFEHOUSE IN ONE PIECE",
                    "The job went sideways and every eye on the island is looking for your car. Get to the safehouse on the other side of town. Fast, but clean: crash too many times and the car will draw a crowd."),
    };

    private final Game g;
    private final Random rnd = new Random();
    public int phase = NONE;
    public int current = -1;
    /** The platform should go back to the main menu (the story page). */
    public boolean request;
    /** The highlighted button on a briefing or result screen. */
    public int choice;

    // the running mission
    private float startX, startZ, startYaw;
    private boolean startInCar;
    private float[] tx = new float[8], tz = new float[8];
    private int targets, next;
    private float timeLeft, elapsed, countdown, limit, damage, flash, closeTime, lostTime;
    private String flashText = "", failReason = "", note = "";
    private int crashes, lastImpacts, lastTick = -1;
    private Vehicle tracked;
    private Traffic.Car tail;
    private float tailHeld;
    private boolean newBest;
    public boolean hasTarget;
    public float targetX, targetZ;
    private float anim;

    static final int MAX_CRASHES = 4;
    static final float TAIL_TIME = 60f, TAIL_FAR = 110f, TAIL_NEAR = 9f;

    Missions(Game g) { this.g = g; }

    // ------------------------------------------------------------------ state

    public boolean active() { return phase != NONE; }

    public boolean modal() { return phase == BRIEF || phase == PASSED || phase == FAILED; }

    /** The countdown before a mission: the player cannot move yet. */
    boolean frozen() { return phase == RUN && countdown > 0f; }

    public static boolean unlocked(Settings s, int i) { return i == 0 || s == null || (s.passed & (1 << (i - 1))) != 0; }

    /** Opens a mission's briefing (the world waits behind it). */
    public void brief(int index) {
        current = index;
        phase = BRIEF;
        choice = 0;
        request = false;
        anim = 0f;
        plan();
        g.audio.jingle(g.audio.bank.uiSelect);
    }

    /** Leaves story mode (the main menu's "cruise", or quitting to the menu). */
    public void abandon() {
        phase = NONE;
        current = -1;
        hasTarget = false;
        g.traffic.keep = null;
        tail = null;
    }

    /** Picks the start and the places for the current mission around where the player is. */
    private void plan() {
        Def d = LIST[current];
        float[] spot = new float[3];
        startInCar = d.kind != REACH;
        float px = g.mapX(), pz = g.mapZ();
        if (!g.streetSpot(px, pz, startInCar, spot)) { spot[0] = px; spot[1] = pz; spot[2] = g.mapHeading(); }
        startX = spot[0]; startZ = spot[1]; startYaw = spot[2];
        targets = 0;
        float ox = startX, oz = startZ;
        switch (d.kind) {
            case REACH: add(ox, oz, 380f, 560f); break;
            case RUSH: add(ox, oz, 750f, 1050f); break;
            case CIRCUIT:
                for (int i = 0; i < 6; i++) { add(ox, oz, 230f, 330f); ox = tx[targets - 1]; oz = tz[targets - 1]; }
                break;
            case FRAGILE: add(ox, oz, 550f, 800f); break;
            case TAIL: break;
            default: add(ox, oz, 1150f, 1600f); break;
        }
        // time limits from the straight-line distance (roads wind: about 1.4 times longer)
        float path = 0f;
        ox = startX; oz = startZ;
        for (int i = 0; i < targets; i++) { path += (float) Math.hypot(tx[i] - ox, tz[i] - oz) * 1.4f; ox = tx[i]; oz = tz[i]; }
        switch (d.kind) {
            case REACH: limit = path / 9f + 45f; break;
            case RUSH: limit = path / 15f + 12f; break;
            case CIRCUIT: limit = (float) Math.hypot(tx[0] - startX, tz[0] - startZ) * 1.4f / 13f + 10f; break;
            case FRAGILE: limit = path / 9f + 30f; break;
            case TAIL: limit = 0f; break;
            default: limit = path / 14f + 25f; break;
        }
        limit = (float) Math.ceil(limit);
    }

    /** Adds a target on an ordinary road between lo and hi metres from (ox, oz). */
    private void add(float ox, float oz, float lo, float hi) {
        float[] q = new float[5];
        java.util.List<RoadEdge> edges = g.city.roads.edges;
        float bestX = ox, bestZ = oz, bestErr = Float.MAX_VALUE;
        for (int tries = 0; tries < 1500; tries++) {
            RoadEdge e = edges.get(rnd.nextInt(edges.size()));
            if (e.cls == RoadClass.FREEWAY || e.cls == RoadClass.RAMP || e.cls == RoadClass.LANE || e.len < 40f) continue;
            e.point(e.len * (0.3f + rnd.nextFloat() * 0.4f), q);
            if (q[2] < 0.5f) continue;                                   // a bridge or the waterline
            float d = (float) Math.hypot(q[0] - ox, q[1] - oz);
            // don't double back toward the start of a circuit
            if (targets > 0 && Math.hypot(q[0] - startX, q[1] - startZ) < d * 0.5f) continue;
            float err = d < lo ? lo - d : d > hi ? d - hi : 0f;
            if (err < bestErr) { bestErr = err; bestX = q[0]; bestZ = q[1]; if (err == 0f) break; }
        }
        tx[targets] = bestX;
        tz[targets] = bestZ;
        targets++;
    }

    /** Puts the player at the start and runs the countdown. */
    private void go() {
        g.placeForMission(startX, startZ, startYaw, startInCar);
        phase = RUN;
        countdown = 3.2f;
        lastTick = 4;
        next = 0;
        elapsed = 0f;
        timeLeft = limit;
        damage = 0f;
        crashes = 0;
        tracked = null;
        tail = null;
        tailHeld = 0f;
        closeTime = lostTime = 0f;
        flash = 0f;
        note = "";
        g.traffic.keep = null;
        hasTarget = targets > 0;
        if (hasTarget) { targetX = tx[0]; targetZ = tz[0]; }
    }

    private void pass() {
        phase = PASSED;
        choice = 0;
        anim = 0f;
        hasTarget = false;
        g.traffic.keep = null;
        Settings s = g.settings;
        newBest = false;
        if (s != null) {
            s.passed |= 1 << current;
            if (s.best[current] <= 0f || elapsed < s.best[current]) { newBest = s.best[current] > 0f; s.best[current] = elapsed; }
            s.save();
        }
        g.audio.jingle(g.audio.bank.missionPass);
    }

    private void fail(String why) {
        phase = FAILED;
        failReason = why;
        choice = 0;
        anim = 0f;
        hasTarget = false;
        g.traffic.keep = null;
        g.audio.jingle(g.audio.bank.missionFail);
    }

    // ------------------------------------------------------------------ every frame

    void update(float dt) {
        if (phase != RUN) return;
        Def d = LIST[current];
        flash = Math.max(0f, flash - dt);
        if (countdown > 0f) {
            countdown -= dt;
            int n = (int) Math.ceil(countdown);
            if (n != lastTick) {
                lastTick = n;
                if (n > 0) g.audio.mixer.play(g.audio.bank.tick, 0.8f);
                else g.audio.jingle(g.audio.bank.missionStart);
            }
            return;
        }
        elapsed += dt;
        float px = g.mapX(), pz = g.mapZ();
        boolean inCar = g.mode == Game.Mode.DRIVING && g.vehicle != null;
        float speed = inCar ? Math.abs(g.vehicle.speed) : 0f;

        // impacts (a different car starts its own count)
        if (inCar) {
            if (g.vehicle != tracked) { tracked = g.vehicle; lastImpacts = tracked.impacts; }
            if (tracked.impacts != lastImpacts) {
                lastImpacts = tracked.impacts;
                float hit = tracked.impact;
                if (d.kind == FRAGILE) {
                    damage += Math.min(60f, hit * hit * 1.6f + hit * 2f);
                    flash("CRACK!  " + Math.min(100, Math.round(damage)) + "% DAMAGE", 1.2f);
                    if (damage >= 100f) { fail("THE CARGO IS RUINED"); return; }
                }
                if (d.kind == GETAWAY && hit > 3f) {
                    crashes++;
                    flash(crashes >= MAX_CRASHES ? "TOO MUCH ATTENTION" : "CRASH  " + crashes + " OF " + MAX_CRASHES, 1.2f);
                    if (crashes >= MAX_CRASHES) { fail("YOU DREW TOO MUCH ATTENTION"); return; }
                }
            }
        }
        if (d.kind == FRAGILE && !inCar && g.mode == Game.Mode.ON_FOOT) { fail("YOU ABANDONED THE CARGO"); return; }

        if (d.kind == TAIL) { updateTail(dt, px, pz); return; }

        // the clock
        int before = (int) Math.ceil(timeLeft);
        timeLeft -= dt;
        int after = (int) Math.ceil(timeLeft);
        if (after != before && after <= 10 && after > 0) g.audio.mixer.play(g.audio.bank.tick, 0.7f);
        if (timeLeft <= 0f) { timeLeft = 0f; fail("OUT OF TIME"); return; }

        float dist = (float) Math.hypot(targetX - px, targetZ - pz);
        if (d.kind == CIRCUIT) {
            if (dist < 14f) {
                next++;
                if (next >= targets) { pass(); return; }
                float leg = (float) Math.hypot(tx[next] - targetX, tz[next] - targetZ) * 1.4f;
                float bonus = (float) Math.ceil(leg / 14f + 6f);
                timeLeft += bonus;
                targetX = tx[next];
                targetZ = tz[next];
                g.audio.mixer.play(g.audio.bank.checkpoint, 0.8f);
                flash("CHECKPOINT " + next + " OF " + targets + "   +" + (int) bonus + " SEC", 1.6f);
            }
            return;
        }
        // stop in the marker (in a car: the first mission needs one too)
        note = dist < 40f ? (!inCar ? "YOU NEED A CAR" : speed > 6f ? "STOP IN THE MARKER" : "") : "";
        if (dist < 10f && inCar && speed < 6f) pass();
    }

    private void updateTail(float dt, float px, float pz) {
        if (tail == null) {
            // the target: a car on the road ahead of us
            float best = Float.MAX_VALUE;
            float fx = g.vehicle != null ? g.vehicle.fx() : 0f, fz = g.vehicle != null ? g.vehicle.fz() : 1f;
            for (Traffic.Car c : g.traffic.cars) {
                float dx = c.x - px, dz = c.z - pz, d = (float) Math.hypot(dx, dz);
                if (d < 20f || d > 120f) continue;
                float ahead = (dx * fx + dz * fz) / d;
                float score = d - ahead * 50f;
                if (score < best) { best = score; tail = c; }
            }
            if (tail == null) { note = "WAITING FOR THE TARGET"; return; }
            g.traffic.keep = tail;
            note = "";
            flash("THAT'S THEM. STAY ON THEIR TAIL", 2f);
        }
        if (!g.traffic.cars.contains(tail)) { fail("YOU LOST THEM"); return; }
        targetX = tail.x;
        targetZ = tail.z;
        hasTarget = true;
        float d = (float) Math.hypot(tail.x - px, tail.z - pz);
        if (d > TAIL_FAR) { fail("YOU LOST THEM"); return; }
        lostTime = d > TAIL_FAR * 0.7f ? lostTime + dt : 0f;
        closeTime = d < TAIL_NEAR ? closeTime + dt : Math.max(0f, closeTime - dt * 0.5f);
        if (closeTime > 2.5f) { fail("THEY SPOTTED YOU"); return; }
        note = d < TAIL_NEAR ? "TOO CLOSE! BACK OFF" : d > TAIL_FAR * 0.7f ? "DON'T LOSE THEM" : "";
        tailHeld += dt;
        if (tailHeld >= TAIL_TIME) pass();
    }

    private void flash(String s, float t) { flashText = s; flash = t; }

    // ------------------------------------------------------------------ input on the modal screens

    private int buttons() { return 2; }

    public void nav(int dx) {
        if (!modal()) return;
        int c = Math.max(0, Math.min(buttons() - 1, choice + dx));
        if (c != choice) { choice = c; g.audio.uiMove(); }
    }

    public void select() {
        if (!modal() || anim < 0.35f) return;
        if (phase == BRIEF) {
            if (choice == 0) { g.audio.uiStart(); go(); }
            else { g.audio.uiBack(); abandon(); request = true; }
        } else if (phase == PASSED) {
            if (choice == 0) {
                g.audio.uiSelect();
                if (current + 1 < LIST.length) brief(current + 1);
                else abandon();                               // the chapter is done: free roam
            } else { g.audio.uiBack(); abandon(); request = true; }
        } else if (phase == FAILED) {
            if (choice == 0) { g.audio.uiStart(); go(); }
            else { g.audio.uiBack(); abandon(); request = true; }
        }
    }

    public void back() {
        if (!modal()) return;
        choice = 1;
        select();
    }

    private final int[][] boxes = new int[2][4];

    /** A tap or click in frame pixels on a briefing or result screen. */
    public void tap(float x, float y) {
        if (!modal()) return;
        for (int i = 0; i < 2; i++)
            if (in(boxes[i], x, y)) { choice = i; select(); return; }
    }

    // ------------------------------------------------------------------ drawing

    private final float[] proj = new float[3];

    /** The mission layer over the game frame: marker, HUD and any briefing or result screen. */
    private long lastDraw;

    public void draw(int[] fb, int w, int h) {
        long now = System.nanoTime();
        anim += lastDraw == 0 ? 0f : Math.min(0.1f, (now - lastDraw) / 1e9f);
        lastDraw = now;
        if (phase == NONE) return;
        begin(fb, w, h);
        if (phase == RUN) { beacon(); hud(); }
        else if (phase == BRIEF) briefing();
        else results();
    }

    private static String clock(float t) {
        int s = (int) Math.ceil(Math.max(0f, t));
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    private static String clockExact(float t) {
        int c = Math.round(t * 100f);
        int s = c / 100;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60) + "." + (c % 100 < 10 ? "0" : "") + (c % 100);
    }

    /** The light beam over the target, or an arrow at the screen edge pointing to it. */
    private void beacon() {
        if (!hasTarget || g.renderer == null) return;
        boolean car = LIST[current].kind == TAIL;
        float ty = car && tail != null ? tail.y + 2.4f : g.city.surface(targetX, targetZ);
        boolean front = g.renderer.project(targetX, ty, targetZ, proj);
        float dist = (float) Math.hypot(targetX - g.mapX(), targetZ - g.mapZ());
        int col = car ? 0xFFFF4040 : LIST[current].kind == CIRCUIT ? 0xFF40D0FF : 0xFFFFC040;
        int sx = Math.round(proj[0]), sy = Math.round(proj[1]);
        if (front && sx > -20 && sx < w + 20 && sy > -40 && sy < h + 60) {
            float pulse = 0.75f + 0.25f * (float) Math.sin(anim * 5f);
            if (car) {
                // a bobbing diamond over the car
                int bob = sy - 6 - (int) (Math.sin(anim * 4f) * 2f);
                diamond(sx, bob, 5, col, (int) (230 * pulse));
                return;
            }
            int half = Math.max(1, Math.min(9, Math.round(g.renderer.focal() * 1.6f / Math.max(1f, proj[2]))));
            int top = Math.max(0, sy - Math.max(70, h));
            for (int y = top; y <= Math.min(h - 1, sy); y++) {
                float t = (sy - y) / (float) Math.max(1, sy - top);                // 0 at the ground, 1 at the top
                int a = (int) ((1f - t * 0.85f) * 150f * pulse);
                for (int x = sx - half; x <= sx + half; x++) {
                    float edge = 1f - Math.abs(x - sx) / (half + 1f);
                    plot(x, y, col, (int) (a * (0.35f + 0.65f * edge)));
                }
                plot(sx, y, 0xFFFFFFFF, a / 2);
            }
            // a ring of light on the ground, and the distance above it
            int rr = half * 3 + 2;
            for (int k = 0; k < 40; k++) {
                double an = k * Math.PI * 2 / 40;
                plot(sx + (int) Math.round(Math.cos(an) * rr), sy + (int) Math.round(Math.sin(an) * rr * 0.35f), col, (int) (220 * pulse));
            }
            String m = Math.round(dist) + " M";
            int ly = Math.max(4, Math.min(h - 50, sy - 40));
            text(m, sx - textW(m, 1) / 2, ly, 1, 0xFFFFFFFF, 230, false, 0xFF000000);
        } else {
            // off screen: an arrow on the frame edge toward it
            float dx = proj[0] - w * 0.5f, dy = proj[1] - h * 0.5f;
            if (!front) dy = Math.abs(dy) + h * 0.3f;
            float l = (float) Math.hypot(dx, dy);
            if (l < 1f) return;
            dx /= l; dy /= l;
            float k = Math.min((w * 0.5f - 18f) / Math.max(1e-3f, Math.abs(dx)), (h * 0.5f - 18f) / Math.max(1e-3f, Math.abs(dy)));
            int ax = Math.round(w * 0.5f + dx * k), ay = Math.round(h * 0.5f + dy * k);
            arrow(ax, ay, dx, dy, col);
            String m = Math.round(dist) + " M";
            text(m, Math.max(2, Math.min(w - textW(m, 1) - 2, ax - textW(m, 1) / 2)), ay + (dy > 0 ? -18 : 11), 1, 0xFFFFFFFF, 230, false, 0xFF000000);
        }
    }

    private void diamond(int cx, int cy, int r, int c, int a) {
        for (int y = -r * 2; y <= 0; y++) {
            int span = r - Math.abs(y + r);
            for (int x = -span; x <= span; x++) plot(cx + x, cy + y, c, a);
        }
    }

    private void arrow(int cx, int cy, float dx, float dy, int c) {
        float nx = -dy, ny = dx;
        for (int s = -2; s <= 10; s++)
            for (int t = -7; t <= 7; t++) {
                float along = 9 - s;                         // the tip leads
                if (Math.abs(t) > along * 0.7f) continue;
                int x = Math.round(cx + dx * (s - 4) + nx * t), y = Math.round(cy + dy * (s - 4) + ny * t);
                plot(x, y, 0xFF000000, 120);
            }
        for (int s = -1; s <= 9; s++)
            for (int t = -6; t <= 6; t++) {
                float along = 9 - s;
                if (Math.abs(t) > along * 0.6f) continue;
                plot(Math.round(cx + dx * (s - 4) + nx * t), Math.round(cy + dy * (s - 4) + ny * t), c, 240);
            }
    }

    private void hud() {
        Def d = LIST[current];
        int cx = w / 2;
        if (countdown > 0f) {
            int n = (int) Math.ceil(countdown);
            String s = n > 3 ? "" : n > 0 ? Integer.toString(n) : "GO!";
            float f = countdown - (float) Math.floor(countdown);              // 1 -> 0 each second
            int scale = 4 + Math.round(f * 3f);
            if (!s.isEmpty()) text(s, cx - textW(s, scale) / 2, h / 2 - 50 - scale * 3, scale, n > 0 ? 0xFFFFFFFF : 0xFF60FF80, (int) (256 * Math.min(1f, f * 3f)), true, 0xFF000000);
            centredBanner(d.name, d.goal, h / 2 - 8);
            return;
        }
        if (elapsed < 0.6f) text("GO!", cx - textW("GO!", 6) / 2, h / 2 - 70, 6, 0xFF60FF80, (int) (256 * (1f - elapsed / 0.6f)), true, 0xFF000000);
        // the panel at the top: mission, objective and the clock
        String obj = objective();
        int pw = Math.max(150, Math.max(textW(obj, 1), textW(d.name, 1)) + 20);
        int x0 = cx - pw / 2, y0 = 4;
        rect(x0, y0, x0 + pw, y0 + 42, 0xFF05080A, 165);
        rect(x0, y0, x0 + pw, y0 + 2, 0xFFE0263A, 255);
        text(d.name, cx - textW(d.name, 1) / 2, y0 + 5, 1, 0xFFE0263A, 256, false, 0);
        String big;
        int bigCol = 0xFFFFFFFF;
        if (d.kind == TAIL) {
            big = clock(TAIL_TIME - tailHeld);
        } else {
            big = clock(timeLeft);
            if (timeLeft < 10f && ((int) (timeLeft * 4f) & 1) == 0) bigCol = 0xFFFF5050;
            else if (timeLeft < 10f) bigCol = 0xFFFFB0A0;
        }
        text(big, cx - textW(big, 2) / 2, y0 + 15, 2, bigCol, 256, false, 0xFF000000);
        text(obj, cx - textW(obj, 1) / 2, y0 + 32, 1, 0xFFDDE4E8, 256, false, 0);
        int y = y0 + 46;
        // meters
        if (d.kind == FRAGILE) y = meter(cx, y, "CARGO", 1f - damage / 100f, damage > 70f ? 0xFFFF5050 : 0xFF60D080);
        if (d.kind == GETAWAY) {
            String s = "CRASHES " + crashes + " / " + MAX_CRASHES;
            rect(cx - textW(s, 1) / 2 - 6, y, cx + textW(s, 1) / 2 + 6, y + 12, 0xFF05080A, 150);
            text(s, cx - textW(s, 1) / 2, y + 3, 1, crashes >= MAX_CRASHES - 1 ? 0xFFFF6060 : 0xFFFFD080, 256, false, 0);
            y += 15;
        }
        if (d.kind == TAIL && tail != null) {
            float dd = (float) Math.hypot(tail.x - g.mapX(), tail.z - g.mapZ());
            float closeness = 1f - Math.min(1f, Math.max(0f, (dd - TAIL_NEAR) / (TAIL_FAR - TAIL_NEAR)));
            y = meter(cx, y, "DISTANCE", closeness, dd < TAIL_NEAR || dd > TAIL_FAR * 0.7f ? 0xFFFF5050 : 0xFF60D080);
        }
        if (!note.isEmpty() && ((int) (anim * 3f) & 1) == 0)
            text(note, cx - textW(note, 1) / 2, y + 2, 1, 0xFFFFE070, 256, false, 0xFF000000);
        if (flash > 0f) {
            int a = (int) (256 * Math.min(1f, flash * 2f));
            text(flashText, cx - textW(flashText, 1) / 2, h / 2 - 40, 1, 0xFFFFFFFF, a, false, 0xFF000000);
        }
    }

    private String objective() {
        Def d = LIST[current];
        boolean inCar = g.mode == Game.Mode.DRIVING;
        switch (d.kind) {
            case REACH: return inCar ? "MEET VINCE AT THE MARKER" : "GET A CAR";
            case RUSH: return "DELIVER THE PACKAGE";
            case CIRCUIT: return "CHECKPOINT " + (next + 1) + " OF " + targets;
            case FRAGILE: return "DELIVER THE CARGO";
            case TAIL: return tail == null ? "FIND THE TARGET" : "FOLLOW THE CAR";
            default: return "REACH THE SAFEHOUSE";
        }
    }

    private int meter(int cx, int y, String label, float v, int col) {
        int bw = 110, x0 = cx - bw / 2;
        rect(x0 - 4, y, x0 + bw + 4, y + 14, 0xFF05080A, 150);
        text(label, x0, y + 4, 1, 0xFF9AA6AC, 256, false, 0);
        int lx = x0 + textW(label, 1) + 6, lw = x0 + bw - lx;
        rect(lx, y + 4, lx + lw, y + 10, 0xFF20262A, 230);
        rect(lx, y + 4, lx + Math.round(lw * Math.max(0f, Math.min(1f, v))), y + 10, col, 245);
        return y + 17;
    }

    private void centredBanner(String title, String sub, int y) {
        rect(0, y, w, y + 30, 0xFF05080A, 160);
        rect(0, y, w, y + 1, 0xFFE0263A, 255);
        rect(0, y + 29, w, y + 30, 0xFFE0263A, 255);
        text(title, w / 2 - textW(title, 2) / 2, y + 4, 2, 0xFFFFFFFF, 256, true, 0xFF000000);
        text(sub, w / 2 - textW(sub, 1) / 2, y + 20, 1, 0xFFB8C4CA, 256, false, 0);
    }

    // ------------------------------------------------------------------ briefing and results

    private void dim(int a) { rect(0, 0, w, h, 0xFF04060A, a); }

    private void briefing() {
        Def d = LIST[current];
        float t = Math.min(1f, anim / 0.35f);
        dim((int) (190 * t));
        int pw = Math.min(w - 24, 360), x0 = (w - pw) / 2;
        int slide = Math.round((1f - t) * (1f - t) * 40f);
        int y = Math.max(8, h / 2 - 105) + slide;
        String head = CHAPTER + "   MISSION " + (current + 1) + " OF " + LIST.length;
        text(head, x0, y, 1, 0xFFE0263A, 256, false, 0);
        rect(x0, y + 10, x0 + pw, y + 11, 0xFFE0263A, 200);
        text(d.name, x0, y + 16, 3, 0xFFFFFFFF, 256, true, 0xFF000000);
        y += 44;
        for (String line : wrap(d.brief, pw, 1)) { text(line, x0, y, 1, 0xFFD6DDE0, 256, false, 0xFF000000); y += 10; }
        y += 6;
        rect(x0, y, x0 + pw, y + (limit > 0 ? 28 : 16), 0xFF0E1418, 200);
        rect(x0, y, x0 + 2, y + (limit > 0 ? 28 : 16), 0xFFFFC040, 255);
        text("OBJECTIVE  " + d.goal, x0 + 8, y + 4, 1, 0xFFFFD070, 256, false, 0);
        if (d.kind == TAIL) { }
        else if (limit > 0) text("TIME LIMIT  " + clock(limit) + (d.kind == CIRCUIT ? "  PLUS BONUS PER CHECKPOINT" : ""), x0 + 8, y + 16, 1, 0xFFB8C4CA, 256, false, 0);
        y += (limit > 0 ? 28 : 16) + 6;
        if (d.kind == TAIL) { text("FOLLOW FOR " + (int) TAIL_TIME + " SECONDS. DON'T GET TOO CLOSE.", x0, y - 4, 1, 0xFFB8C4CA, 256, false, 0); y += 8; }
        Settings s = g.settings;
        if (s != null && s.best[current] > 0f) { text("BEST  " + clockExact(s.best[current]), x0, y, 1, 0xFF80E0A0, 256, false, 0); y += 12; }
        buttons(x0, Math.min(h - 26, y + 4), "START", "BACK");
    }

    private void results() {
        boolean ok = phase == PASSED;
        float t = Math.min(1f, anim / 0.3f);
        dim((int) (150 * t));
        // a band across the screen that stretches open
        int bandH = Math.round(54 * t), cy = h / 2 - 30;
        int col = ok ? 0xFFFFC040 : 0xFFE0263A;
        rect(0, cy - bandH / 2, w, cy + bandH / 2, 0xFF05080A, 210);
        rect(0, cy - bandH / 2, w, cy - bandH / 2 + 2, col, 255);
        rect(0, cy + bandH / 2 - 2, w, cy + bandH / 2, col, 255);
        if (t < 1f) return;
        String title = ok ? "MISSION PASSED" : "MISSION FAILED";
        int sc = w >= 400 ? 4 : 3;
        float pop = Math.min(1f, (anim - 0.3f) / 0.15f);
        text(title, w / 2 - textW(title, sc) / 2, cy - sc * 4 - 2, sc, ok ? 0xFFFFE080 : 0xFFFF6060, (int) (256 * pop), true, 0xFF000000);
        String sub = ok ? LIST[current].name : failReason;
        text(sub, w / 2 - textW(sub, 1) / 2, cy + 14, 1, 0xFFE6ECEE, 256, false, 0xFF000000);
        int y = cy + bandH / 2 + 10;
        if (ok) {
            String tm = "TIME  " + clockExact(elapsed);
            text(tm, w / 2 - textW(tm, 2) / 2, y, 2, 0xFFFFFFFF, 256, false, 0xFF000000);
            y += 20;
            Settings s = g.settings;
            if (newBest && ((int) (anim * 3) & 1) == 0) { String nb = "NEW BEST TIME"; text(nb, w / 2 - textW(nb, 1) / 2, y, 1, 0xFF80FFA0, 256, false, 0xFF000000); }
            else if (s != null && s.best[current] > 0f) { String b = "BEST  " + clockExact(s.best[current]); text(b, w / 2 - textW(b, 1) / 2, y, 1, 0xFF9AA6AC, 256, false, 0); }
            y += 14;
            if (current + 1 == LIST.length) { String c = "CHAPTER COMPLETE"; text(c, w / 2 - textW(c, 1) / 2, y, 1, 0xFFFFD070, 256, false, 0); y += 12; }
        }
        int bw = 2 * 104 + 10;
        buttons((w - bw) / 2, Math.min(h - 26, y + 6), ok ? (current + 1 < LIST.length ? "NEXT MISSION" : "FREE ROAM") : "RETRY", "MAIN MENU");
    }

    private void buttons(int x0, int y, String a, String b) {
        String[] labels = {a, b};
        for (int i = 0; i < 2; i++) {
            int bx = x0 + i * 114, bw = 104, bh = 20;
            boolean on = choice == i;
            if (on) {
                slant(bx, y, bx + bw + 10, y + bh, 0xFFE0263A, 240);
            } else {
                rect(bx, y, bx + bw, y + bh, 0xFF0E1418, 210);
                frame(bx, y, bx + bw, y + bh, 0xFF56626A, 200);
            }
            String l = labels[i];
            text(l, bx + (bw - textW(l, 1)) / 2, y + 7, 1, on ? 0xFFFFFFFF : 0xFFB8C4CA, 256, false, on ? 0xFF000000 : 0);
            boxes[i][0] = bx; boxes[i][1] = y; boxes[i][2] = bx + bw; boxes[i][3] = y + bh;
        }
    }
}
