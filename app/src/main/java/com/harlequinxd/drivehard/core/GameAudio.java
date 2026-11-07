package com.harlequinxd.drivehard.core;

import com.harlequinxd.drivehard.core.audio.Mixer;
import com.harlequinxd.drivehard.core.audio.SoundBank;
import com.harlequinxd.drivehard.core.game.Player;
import com.harlequinxd.drivehard.core.game.Vehicle;
import com.harlequinxd.drivehard.core.model.Animations;
import com.harlequinxd.drivehard.core.sim.Traffic;

import java.util.Random;

/**
 * The game's sound director: every frame it reads the game (the car, the player, the weather,
 * the traffic, where the camera is) and plays or steers the sounds, positioned in stereo around
 * the camera.
 *
 * <ul>
 * <li>The player's car: a live engine with a simulated gearbox (it revs through the gears, drops
 *   on each shift, crackles on the overrun), tyre squeal from the slip, road roar or gravel by
 *   the surface, wind at speed, bumps and crashes by impact speed, doors, the starter, the
 *   handbrake ratchet.</li>
 * <li>On foot: footsteps by surface (pavement, grass, sand, shallow water), jumps and landings,
 *   swim strokes, splashes, treading water.</li>
 * <li>Around: up to three nearby traffic engines, horns when you crash near them, surf near the
 *   sea, wind, rain (drumming on the roof in the car), thunder after lightning (later the farther
 *   the bolt), birds by day and crickets at night in the countryside, a city hum in town.</li>
 * <li>The interface: menu moves, selects, back, pause, the map, mission jingles; and the theme.</li>
 * </ul>
 */
public final class GameAudio {
    public final Mixer mixer = new Mixer();
    public final SoundBank bank = new SoundBank();
    private final Game game;
    private final Random rnd = new Random(77);

    // loops and engines
    private final Mixer.Engine engine, traffic0, traffic1, traffic2;
    private final Mixer.Engine[] trafficEngines;
    private final Mixer.Loop squeal, roar, gravel, rush, wind, rain, roof, surf, birds, crickets, city, lap;

    // state
    private int gear = 1;
    private float rpm = 900f, shiftCut, startDelay, engineOn, popTimer, lastGas;
    private int lastImpacts;
    private Game.Mode lastMode = Game.Mode.ON_FOOT;
    private boolean lastHand, lastGround = true, lastSwim, wasWet;
    private float lastPhase, lastVy, stepCool;
    private float lastFlash;
    private float thunderAt = -1f, thunderVol;
    private boolean thunderNear;
    private float coastTimer, coast, urban, honkCool;
    private float lx, lz, rx = 1f, rz;            // listener and its right-hand direction

    // the interface
    private int lastScreen = -1, lastIndex = -1;
    public static final int SCREEN_GAME = 0, SCREEN_PAUSE = 1, SCREEN_MAP = 2, SCREEN_MENU = 3;

    GameAudio(Game game) {
        this.game = game;
        engine = mixer.engine();
        traffic0 = mixer.engine();
        traffic1 = mixer.engine();
        traffic2 = mixer.engine();
        trafficEngines = new Mixer.Engine[]{traffic0, traffic1, traffic2};
        for (Mixer.Engine e : trafficEngines) { e.reverb = 0.35f; e.cylinders = 4; }
        SoundBank b = bank;
        squeal = mixer.loop(b.tyreSqueal);
        roar = mixer.loop(b.roadRoar);
        gravel = mixer.loop(b.gravelRoll);
        rush = mixer.loop(b.windLoop);
        wind = mixer.loop(b.windLoop);
        rain = mixer.loop(b.rainLoop);
        roof = mixer.loop(b.roofRain);
        surf = mixer.loop(b.surfLoop);
        birds = mixer.loop(b.birdsLoop);
        crickets = mixer.loop(b.cricketsLoop);
        city = mixer.loop(b.cityHum);
        lap = mixer.loop(b.lapLoop);
        surf.reverb = 0.15f; wind.reverb = 0.05f; rush.reverb = 0.05f; birds.reverb = 0.45f;
    }

    // ------------------------------------------------------------------ positional helpers

    private float gainAt(float x, float z, float ref) {
        float dx = x - lx, dz = z - lz, d2 = dx * dx + dz * dz;
        return ref * ref / (ref * ref + d2);
    }

    private float panAt(float x, float z) {
        float dx = x - lx, dz = z - lz, d = (float) Math.sqrt(dx * dx + dz * dz);
        if (d < 0.5f) return 0f;
        return Math.max(-1f, Math.min(1f, (dx * rx + dz * rz) / d)) * 0.75f;
    }

    private void at(short[] s, float x, float z, float vol, float ref, float pitch) {
        float g = gainAt(x, z, ref) * vol;
        if (g > 0.01f) mixer.play(s, g, panAt(x, z), pitch, 0.25f + urban * 0.2f);
    }

    private short[] pick(short[][] set) { return set[rnd.nextInt(set.length)]; }

    private float vary(float spread) { return 1f + (rnd.nextFloat() - 0.5f) * spread; }

    // ------------------------------------------------------------------ the interface

    /** The platform reports which screen is up (and the highlighted menu item) every frame. */
    public void screen(int screen, int index) {
        if (screen != lastScreen && lastScreen >= 0) {
            if (screen == SCREEN_PAUSE) mixer.play(bank.uiPause, 0.6f);
            else if (screen == SCREEN_MAP) mixer.play(bank.uiMap, 0.55f);
            else if (screen == SCREEN_GAME && lastScreen != SCREEN_MENU) mixer.play(bank.uiBack, 0.5f);
        } else if (index != lastIndex && lastIndex >= 0 && screen == lastScreen && screen != SCREEN_GAME) {
            mixer.play(bank.uiMove, 0.5f);
        }
        lastScreen = screen;
        lastIndex = index;
        mixer.worldMuted = screen != SCREEN_GAME;
    }

    public void uiMove() { mixer.play(bank.uiMove, 0.5f); }
    public void uiSelect() { mixer.play(bank.uiSelect, 0.6f); }
    public void uiBack() { mixer.play(bank.uiBack, 0.5f); }
    public void uiStart() { mixer.play(bank.uiStart, 0.65f); }

    /** The theme on (main menu) or off (in game); it glides. */
    public void music(boolean on) {
        if (on && mixer.music.volume == 0f) mixer.music.restart();
        mixer.music.volume = on ? 1f : 0f;
    }

    // ------------------------------------------------------------------ every frame

    /** Called at the end of each game update. */
    void update(float dt) {
        Game g = game;
        if (g.renderer != null) {
            lx = g.renderer.ex; lz = g.renderer.ez;
            rx = g.renderer.rightX(); rz = g.renderer.rightZ();
        } else {
            lx = g.cam.eyeX; lz = g.cam.eyeZ;
            rx = (float) Math.cos(g.cam.yaw); rz = -(float) Math.sin(g.cam.yaw);
        }
        car(dt);
        onFoot(dt);
        ambience(dt);
        trafficSounds(dt);
        lastMode = g.mode;
    }

    // ------------------------------------------------------------------ the car

    private static final float[] GEAR_TOP = {0f, 10f, 17f, 25f, 34f, 45f, 62f};       // m/s at the redline in each gear

    private void car(float dt) {
        Game g = game;
        Vehicle v = g.vehicle;
        Game.Mode m = g.mode;
        boolean inCar = m == Game.Mode.DRIVING || m == Game.Mode.ENTERING;
        // getting in and out
        if (m == Game.Mode.ENTERING && lastMode == Game.Mode.ON_FOOT && v != null) {
            at(bank.doorOpen, v.x, v.z, 0.8f, 8f, vary(0.1f));
        }
        if (m == Game.Mode.DRIVING && lastMode == Game.Mode.ENTERING && v != null) {
            at(bank.doorClose, v.x, v.z, 0.9f, 8f, vary(0.08f));
            at(bank.starter, v.x, v.z, 0.75f, 10f, vary(0.06f));
            startDelay = 0.6f;
            engineOn = 0f;
            gear = 1;
            rpm = 900f;
            lastImpacts = v.impacts;
        }
        if (m == Game.Mode.EXITING && lastMode == Game.Mode.DRIVING && v != null) {
            at(bank.doorOpen, v.x, v.z, 0.8f, 8f, 1f);
            at(bank.doorClose, v.x, v.z, 0.7f, 8f, 1.05f);
        }
        if (!inCar || v == null) {
            engine.volume = 0f;
            squeal.volume = roar.volume = gravel.volume = rush.volume = roof.volume = 0f;
            engineOn = 0f;
            return;
        }
        if (startDelay > 0f) { startDelay -= dt; engine.volume = 0f; return; }
        engineOn = Math.min(1f, engineOn + dt * 3f);
        float speed = Math.abs(v.speed);
        float gas = m == Game.Mode.DRIVING ? g.inGas : 0f, brake = g.inBrake;
        boolean reversing = v.speed < -0.5f || (speed < 0.5f && brake > 0.5f && gas < 0.1f);
        float slip = Math.max(v.slipFront, v.slipRear);

        // the gearbox: up at the top of the revs under power, down when they sag
        float wantRpm;
        if (reversing) {
            gear = 1;
            wantRpm = 900f + speed / 8f * 4200f;
        } else {
            float top = GEAR_TOP[gear];
            float inGear = speed / top;
            if (inGear > 0.93f && gear < 6 && gas > 0.2f) { gear++; shiftCut = 0.16f; play(bank.gearClunk, v, 0.35f); }
            else if (gear > 1 && speed / GEAR_TOP[gear - 1] < 0.62f) gear--;
            float low = gear == 1 ? 0f : GEAR_TOP[gear - 1] * 0.5f;
            inGear = Math.max(0f, (speed - low) / (GEAR_TOP[gear] - low));
            wantRpm = 900f + inGear * 5600f;
            if (speed < 1.5f) wantRpm = 900f + gas * 3800f;                     // revving at a standstill
            if (v.slipRear > 0.55f && gas > 0.5f) wantRpm += 1400f * v.slipRear; // wheelspin flares the revs
        }
        if (shiftCut > 0f) { shiftCut -= dt; wantRpm *= 0.78f; }
        rpm += (Math.min(7200f, wantRpm) - rpm) * Math.min(1f, dt * (wantRpm > rpm ? 7f : 4f));
        engine.rpm = rpm;
        engine.load = shiftCut > 0f ? 0.1f : Math.max(gas, reversing ? 0.4f : 0f);
        engine.volume = (0.32f + 0.25f * engine.load + rpm / 7200f * 0.18f) * engineOn;
        engine.pan = panAt(v.x, v.z);
        engine.reverb = 0.12f + urban * 0.25f;
        // overrun crackle: lifting off from high revs
        if (lastGas > 0.6f && gas < 0.1f && rpm > 4200f) popTimer = 0.5f;
        lastGas = gas;
        if (popTimer > 0f) {
            popTimer -= dt;
            if (rnd.nextFloat() < dt * 9f) play(pick(bank.pop), v, 0.35f + rnd.nextFloat() * 0.2f);
        }

        // the road: tyres, surface, wind
        int foot = g.city.footing(v.x, v.z);
        float roll = Math.min(1f, speed / 28f);
        boolean rough = foot != 0;
        roar.volume = rough ? 0f : roll * 0.35f;
        roar.pitch = 0.7f + roll * 0.7f;
        gravel.volume = rough ? Math.min(1f, speed / 14f) * 0.5f : 0f;
        gravel.pitch = 0.75f + roll * 0.6f;
        float sq = smooth(0.28f, 0.75f, slip) * Math.min(1f, speed / 5f);
        squeal.volume = rough ? 0f : sq * 0.55f;
        squeal.pitch = 0.88f + slip * 0.22f + roll * 0.08f;
        squeal.pan = roar.pan = gravel.pan = engine.pan;
        rush.volume = smooth(14f, 45f, speed) * 0.4f;
        rush.pitch = 0.8f + roll * 0.6f;
        // the handbrake
        if (v.handbrake && !lastHand) play(bank.handbrake, v, 0.55f);
        lastHand = v.handbrake;
        // impacts
        if (v.impacts != lastImpacts) {
            lastImpacts = v.impacts;
            float k = v.impact;
            if (k > 9f) play(bank.crash[1], v, Math.min(1f, 0.6f + k / 40f));
            else if (k > 5f) play(bank.crash[0], v, 0.5f + k / 30f);
            else play(pick(bank.bump), v, 0.25f + k / 12f);
            if (k > 6f) honkNear(v.x, v.z);
        }
    }

    private void play(short[] s, Vehicle v, float vol) { at(s, v.x, v.z, vol, 9f, vary(0.08f)); }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    // ------------------------------------------------------------------ on foot

    private void onFoot(float dt) {
        Game g = game;
        Player p = g.player;
        boolean foot = g.mode == Game.Mode.ON_FOOT || g.mode == Game.Mode.EXITING;
        lap.volume = foot && p.swimming && p.anim == Animations.Anim.SWIM_TREAD ? 0.3f : 0f;
        if (!foot) { lastGround = true; lastSwim = false; lastPhase = p.cyclePhase(); return; }
        float ph = p.cyclePhase();
        boolean wrap = ph < lastPhase - 0.2f, half = lastPhase < 0.5f && ph >= 0.5f;
        lastPhase = ph;
        stepCool -= dt;
        boolean wet = !p.swimming && p.waterDepth > 0.08f;
        // footsteps on the two footfalls of each stride
        if ((wrap || half) && p.onGround && !p.swimming && stepCool <= 0f
                && (p.anim == Animations.Anim.WALK || p.anim == Animations.Anim.RUN || p.anim == Animations.Anim.WADE)) {
            boolean run = p.anim == Animations.Anim.RUN;
            float vol = run ? 0.75f : 0.5f;
            short[] s;
            if (wet) s = pick(bank.stepWater);
            else {
                int f = g.city.footing(p.x, p.z);
                s = f == 0 ? pick(bank.stepHard) : f == 1 ? pick(bank.stepGrass) : pick(bank.stepSand);
            }
            at(s, p.x, p.z, vol, 6f, vary(0.14f));
            stepCool = 0.12f;
        }
        // swimming strokes
        if (p.swimming) {
            if (p.anim == Animations.Anim.SWIM_FREE && (wrap || half)) at(pick(bank.stroke), p.x, p.z, 0.55f, 6f, vary(0.15f));
            if (p.anim == Animations.Anim.SWIM_BREAST && wrap) at(pick(bank.stroke), p.x, p.z, 0.5f, 6f, vary(0.1f) * 0.85f);
        }
        // into the water
        if (p.splashImpact < -1f) at(p.splashImpact < -5f ? bank.splashBig : bank.landWater, p.x, p.z, 0.9f, 8f, vary(0.1f));
        else if (p.swimming && !lastSwim && !wasWet) at(bank.splashSmall, p.x, p.z, 0.6f, 6f, vary(0.1f));
        // running into things, and skidding to turn
        if (p.bumpImpact > 0f) at(bank.bodyHit[p.bumpImpact > 4.5f ? 1 : 0], p.x, p.z, Math.min(1f, 0.35f + p.bumpImpact * 0.1f), 6f, vary(0.12f));
        if (p.skidStart && !p.swimming) {
            int f = g.city.footing(p.x, p.z);
            at(f == 0 ? pick(bank.stepHard) : f == 1 ? pick(bank.stepGrass) : pick(bank.stepSand), p.x, p.z, 0.8f, 6f, 0.7f);
        }
        // jumping and landing
        if (lastGround && !p.onGround && p.vy > 3f && !p.swimming) at(bank.jump, p.x, p.z, 0.45f, 6f, vary(0.1f));
        if (!lastGround && p.onGround && lastVy < -4.5f) at(wet ? pick(bank.stepWater) : bank.land, p.x, p.z, Math.min(1f, -lastVy / 12f), 6f, vary(0.1f));
        lastGround = p.onGround;
        lastSwim = p.swimming;
        wasWet = wet;
        lastVy = p.vy;
    }

    // ------------------------------------------------------------------ the world around

    private void ambience(float dt) {
        Game g = game;
        Environment env = g.env;
        // every half second: how much sea and city is around the camera
        coastTimer -= dt;
        if (coastTimer <= 0f) {
            coastTimer = 0.5f;
            int sea = 0, town = 0, n = 0;
            for (int ring = 1; ring <= 3; ring++)
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4 + ring * 0.3;
                    float x = lx + (float) Math.cos(a) * ring * 18f, z = lz + (float) Math.sin(a) * ring * 18f;
                    if (g.city.terrain.natural(x, z) < 0.1f) sea++;
                    if (g.city.urban(x, z)) town++;
                    n++;
                }
            coast = sea / (float) n;
            urban = town / (float) n;
        }
        float rainK = env.rain;
        boolean inCar = g.mode == Game.Mode.DRIVING || g.mode == Game.Mode.ENTERING;
        surf.volume = smooth(0.02f, 0.5f, coast) * 0.55f;
        float alt = Math.max(0f, g.cam.eyeY - 25f) / 60f;
        float wm = (float) Math.sqrt(env.windX * env.windX + env.windZ * env.windZ);
        wind.volume = Math.min(0.5f, 0.06f + wm * 0.04f + alt * 0.25f + rainK * 0.15f) * (inCar ? 0.5f : 1f);
        rain.volume = rainK * (inCar ? 0.25f : 0.6f);
        roof.volume = inCar ? rainK * 0.55f : 0f;
        float day = 1f - env.night;
        float quiet = (1f - urban) * (1f - rainK) * (inCar ? 0.35f : 1f);
        birds.volume = smooth(0.4f, 0.9f, day) * quiet * 0.3f;
        crickets.volume = smooth(0.5f, 0.9f, env.night) * quiet * 0.28f;
        city.volume = urban * 0.3f * (inCar ? 0.6f : 1f);
        // thunder: the flash, then the rumble (later and softer the farther the bolt)
        if (env.flash > 0.9f && lastFlash < 0.5f) {
            float dist = 0.2f + rnd.nextFloat() * 2.5f;
            thunderAt = dist;
            thunderNear = dist < 0.9f;
            thunderVol = 1f - dist / 3.2f;
        }
        lastFlash = env.flash;
        if (thunderAt > 0f) {
            thunderAt -= dt;
            if (thunderAt <= 0f) mixer.play(thunderNear ? bank.thunderNear : bank.thunderFar, thunderVol, (rnd.nextFloat() - 0.5f) * 0.8f, vary(0.1f), 0.5f);
        }
    }

    // ------------------------------------------------------------------ traffic

    private final Traffic.Car[] near = new Traffic.Car[3];

    private void trafficSounds(float dt) {
        Game g = game;
        honkCool -= dt;
        near[0] = near[1] = near[2] = null;
        float[] d = {1e9f, 1e9f, 1e9f};
        for (Traffic.Car c : g.traffic.cars) {
            float dx = c.x - lx, dz = c.z - lz, d2 = dx * dx + dz * dz;
            if (d2 > 50f * 50f) continue;
            for (int k = 0; k < 3; k++) if (d2 < d[k]) {
                for (int j = 2; j > k; j--) { d[j] = d[j - 1]; near[j] = near[j - 1]; }
                d[k] = d2; near[k] = c;
                break;
            }
        }
        for (int k = 0; k < 3; k++) {
            Mixer.Engine e = trafficEngines[k];
            Traffic.Car c = near[k];
            if (c == null) { e.volume = 0f; continue; }
            float sp = Math.abs(c.speed);
            e.rpm = 900f + Math.min(1f, sp / 16f) * 2600f + (sp > 16f ? (sp - 16f) * 60f : 0f);
            e.load = c.brake > 0.3f ? 0.05f : Math.min(1f, 0.25f + sp / 30f);
            e.volume = gainAt(c.x, c.z, 7f) * 0.45f;
            e.pan = panAt(c.x, c.z);
            e.cylinders = (c.style % 5 == 0) ? 3 : (c.style % 7 == 0) ? 6 : 4;
        }
    }

    /** A nearby driver leans on the horn (after a crash next to them). */
    private void honkNear(float x, float z) {
        if (honkCool > 0f) return;
        for (Traffic.Car c : game.traffic.cars) {
            float dx = c.x - x, dz = c.z - z;
            if (dx * dx + dz * dz < 20f * 20f) {
                at(rnd.nextBoolean() ? bank.horn : bank.hornShort, c.x, c.z, 0.7f, 10f, vary(0.12f));
                honkCool = 2.5f;
                return;
            }
        }
    }

    /** Mission and HUD jingles. */
    public void jingle(short[] s) { mixer.play(s, 0.65f, 0f, 1f, 0.3f); }
}
