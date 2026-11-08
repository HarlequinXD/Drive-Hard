package com.harlequinxd.drivehard.core;

import com.harlequinxd.drivehard.core.city.Chunk;
import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.city.Terrain;
import com.harlequinxd.drivehard.core.city.TrafficLights;
import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.game.OrbitCamera;
import com.harlequinxd.drivehard.core.game.Player;
import com.harlequinxd.drivehard.core.game.Vehicle;
import com.harlequinxd.drivehard.core.model.Animations;
import com.harlequinxd.drivehard.core.model.Pose;
import com.harlequinxd.drivehard.core.model.CharacterModel;
import com.harlequinxd.drivehard.core.model.CharacterSpec;
import com.harlequinxd.drivehard.core.model.Cars;
import com.harlequinxd.drivehard.core.model.Flora;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.render.Renderer;
import com.harlequinxd.drivehard.core.render.SoftRenderer;
import com.harlequinxd.drivehard.core.render.Texture;
import com.harlequinxd.drivehard.core.sim.Pedestrians;
import com.harlequinxd.drivehard.core.sim.Traffic;
import com.harlequinxd.drivehard.core.sprite.PropSprites;
import com.harlequinxd.drivehard.core.sprite.SpriteBaker;
import com.harlequinxd.drivehard.core.sprite.SpriteCache;
import com.harlequinxd.drivehard.core.sprite.SpriteSheet;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Platform-independent game: streamed island, player, traffic, pedestrians, flora and the renderer. */
public final class Game {
    public static final int GROUP_FLORA = 0, GROUP_CARS = 1, GROUP_PEDS = 2;
    public static final int CAR_CACHE = 24, PED_CACHE = 16, GROUP_PLAYER = 3;

    public final TextureLibrary lib;
    public final City city;
    public final Player player;
    public final OrbitCamera cam = new OrbitCamera();

    // ------------------------------------------------------------------ vehicles
    /** On foot, hopping into a car, driving, or popping back out. */
    public enum Mode { ON_FOOT, ENTERING, DRIVING, EXITING }
    public Mode mode = Mode.ON_FOOT;
    /** The car being driven (or entered). */
    public Vehicle vehicle;
    /** Cars the player has left behind; they stay where they stop and can be taken again. */
    public final List<Vehicle> parked = new ArrayList<>();
    public static final int MAX_PARKED = 6;
    /** How close (metres from the body) the player must be to get in. */
    public static final float ENTER_RANGE = 2.2f;
    private float popT, popX0, popY0, popZ0;
    private float camIdle;
    private final float[] door = new float[2];
    private float camSurge, camSway;

    // ------------------------------------------------------------------ skid marks
    private static final int SKIDS = 640;
    private final float[] skidX = new float[SKIDS], skidZ = new float[SKIDS], skidY = new float[SKIDS], skidYaw = new float[SKIDS];
    private int skidHead, skidCount;
    private float skidRun;

    /** Lays rubber behind sliding or spinning tyres. */
    private void dropSkids(float dt) {
        Vehicle v = vehicle;
        float sp = (float) Math.hypot(v.vx, v.vz);
        boolean rear = v.slipRear > 0.45f, front = v.slipFront > 0.55f;
        if ((!rear && !front) || sp < 2f) { skidRun = 0.18f; return; }
        skidRun += sp * dt;
        if (skidRun < 0.18f) return;
        skidRun = 0f;
        float fx = v.fx(), fz = v.fz(), rx = -fz, rz = fx;
        float travel = (float) Math.atan2(v.vx, v.vz);
        for (int axle = 0; axle < 2; axle++) {
            if (axle == 0 ? !rear : !front) continue;
            float along = (axle == 0 ? -0.31f : 0.31f) * v.len;
            for (int side = -1; side <= 1; side += 2) {
                int i = skidHead;
                skidHead = (skidHead + 1) % SKIDS;
                skidCount = Math.min(SKIDS, skidCount + 1);
                skidX[i] = v.x + fx * along + rx * side * v.wid * 0.38f;
                skidZ[i] = v.z + fz * along + rz * side * v.wid * 0.38f;
                skidY[i] = v.y;
                skidYaw[i] = travel;
            }
        }
    }

    private void drawSkids(Renderer r) {
        for (int k = 0; k < skidCount; k++) {
            if (!r.boxVisible(skidX[k] - 1, skidY[k], skidZ[k] - 1, skidX[k] + 1, skidY[k] + 0.2f, skidZ[k] + 1)) continue;
            r.groundQuadRotated(lib.shadow, skidX[k], skidY[k] + 0.02f, skidZ[k], 0.32f, 0.12f, skidYaw[k],
                    SoftRenderer.MODE_SHADOW, SoftRenderer.L_SHADOW);
        }
    }
    private final List<Vehicle> trafficObstacles = new ArrayList<>();
    public final boolean lowMemory;
    public final SpriteSheet lamps, signals, umbrellas, poles;
    /** The player: hand-pixelled parts on the animation skeleton. */
    public final com.harlequinxd.drivehard.core.model.Puppet puppet = new com.harlequinxd.drivehard.core.model.Puppet();
    /** The player swimming (wider frames, anchored at the waterline) and the splash burst. */
    public final SpriteSheet splashSheet;
    private final int splashTex;
    private final int[] rippleTex = new int[com.harlequinxd.drivehard.core.tex.PropTextures.RIPPLE_FRAMES];
    private final int lampTex, signalTex, umbrellaTex, poleTex;
    public final Texture[] textures = new Texture[4096];
    private int nextTex;
    /** The game data folder for this island (null when there is none). */
    public final com.harlequinxd.drivehard.core.data.WorldCache worldCache;
    /** The island was read from the game data folder instead of generated. */
    public final boolean loadedFromDisk;
    public final SpriteCache cache;
    public final Traffic traffic;
    public final Pedestrians pedestrians;
    private final SpriteCache.Entry[] flora = new SpriteCache.Entry[Flora.values().length];
    private final SpriteCache.Entry[] carEntries = new SpriteCache.Entry[Cars.STYLES];
    /** Pedestrian puppets, built the first time each of the 100 looks is needed; their atlases fill reserved texture slots. */
    public static final int PED_STYLES = 100;
    private final com.harlequinxd.drivehard.core.model.Puppet[] pedPuppets = new com.harlequinxd.drivehard.core.model.Puppet[PED_STYLES];
    private int pedTexBase;
    private final Random rnd = new Random(7);
    public Renderer renderer;
    private final float[] move = new float[2];
    public float time;
    /** This frame's pedals and steering (for the engine sound). */
    public float inGas, inBrake, inSteer;
    /** Sound: effects, ambience, engines and the theme. */
    public final GameAudio audio;
    /** Time of day, season and weather. */
    public final Environment env;
    private float rotateTimer;

    /** PS1-style draw distances (metres). */
    public float treeDist = 105f, shrubDist = 50f, smallSpriteDist = 60f, carDist = 115f, pedDist = 70f;

    // stats
    public int batchesDrawn, batchesCulled, batchesOccluded, spritesDrawn, carsDrawn, pedsDrawn;

    /** Load-screen feedback: what is happening now and how far along (0..1) the whole load is. */
    public interface Progress { void step(String what, float done); }

    /** The island everyone plays on. */
    public static final long DEFAULT_SEED = 20261103L;

    /** A fresh seed for a new randomly generated island. */
    public static long randomSeed() {
        return ThreadLocalRandom.current().nextLong();
    }

    public Game(long seed, Progress progress) { this(seed, progress, false); }

    /**
     * @param lowMemory phones: fewer texture variants, fewer chunks kept loaded, a shorter view distance
     */
    public Game(long seed, Progress progress, boolean lowMemory) {
        this.lowMemory = lowMemory;
        env = new Environment(seed);
        audio = new GameAudio(this);
        missions = new Missions(this);
        // the character and prop sprites bake on another core while the island is generated
        final SpriteSheet[] baked = new SpriteSheet[5];
        Thread baker = new Thread(() -> {
            baked[1] = PropSprites.lamp();
            baked[2] = PropSprites.signals();
            baked[3] = PropSprites.umbrellas();
            baked[4] = PropSprites.utilityPole();
        }, "sprite-baker");
        baker.start();
        // the game data folder: the island and the chunks already visited are read from disk
        worldCache = com.harlequinxd.drivehard.core.data.WorldCache.open(seed, lowMemory);
        com.harlequinxd.drivehard.core.data.WorldCache.Island saved = null;
        if (worldCache != null && worldCache.hasIsland()) {
            if (progress != null) progress.step("Loading the island from game data", 0.00f);
            saved = worldCache.loadIsland(progress == null ? null
                    : f -> progress.step("Loading the island from game data", f * 0.75f));
        }
        loadedFromDisk = saved != null;
        if (saved == null) {
            if (progress != null) progress.step("Painting building textures", 0.00f);
            lib = new TextureLibrary(seed, lowMemory);
        } else {
            lib = saved.lib;
        }
        if (progress != null) progress.step("Baking characters and props", saved != null ? 0.76f : 0.10f);
        if (saved != null) {
            city = saved.city;
        } else {
            if (progress != null) progress.step("Raising the island", 0.14f);
            city = new City(lib, seed, progress == null ? null : (what, done) -> progress.step(what, 0.14f + done * 0.66f));
            // saved in the background for next time (the library and island do not change from here on)
            if (worldCache != null) worldCache.saveIsland(lib, city);
        }
        city.chunks.cache = worldCache;
        try { baker.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        for (int i = 0; i < lib.list.size(); i++) textures[i] = lib.list.get(i);
        nextTex = lib.list.size();
        lamps = baked[1];
        signals = baked[2];
        umbrellas = baked[3];
        poles = baked[4];
        {
            int sf = 8, sw = 48, sh = 48, perRow = 512 / sw;
            int[] frames = com.harlequinxd.drivehard.core.tex.PropTextures.splash(sf, sw, sh, 4242);
            int[] atlas = new int[512 * sh];
            for (int f = 0; f < sf; f++)
                for (int y = 0; y < sh; y++) System.arraycopy(frames, f * sw * sh + y * sw, atlas, y * 512 + f * sw, sw);
            splashSheet = new SpriteSheet(atlas, 512, sh, sw, sh, sf, 1, perRow, sw / 2, sh - 4, 32f);
            splashTex = addTexture(new Texture(512, sh, atlas));
        }
        for (int i = 0; i < rippleTex.length; i++) rippleTex[i] = addTexture(com.harlequinxd.drivehard.core.tex.PropTextures.ripple(i));
        puppet.tex = addTexture(puppet.atlas);
        lampTex = addTexture(Texture.padded(lamps.pixels, lamps.width, lamps.height));
        signalTex = addTexture(Texture.padded(signals.pixels, signals.width, signals.height));
        umbrellaTex = addTexture(Texture.padded(umbrellas.pixels, umbrellas.width, umbrellas.height));
        poleTex = addTexture(Texture.padded(poles.pixels, poles.width, poles.height));
        pedTexBase = nextTex;
        nextTex += PED_STYLES;
        cache = new SpriteCache(textures, nextTex, new int[]{0, lowMemory ? 10 : CAR_CACHE, 0, 1});
        // plants bake in the background, most common species first
        Flora[] order = {Flora.GINKGO, Flora.ZELKOVA, Flora.SAKURA, Flora.BLACK_PINE, Flora.MAPLE, Flora.CEDAR,
                Flora.CAMPHOR, Flora.BAMBOO, Flora.AZALEA, Flora.BOXWOOD, Flora.CAMELLIA, Flora.HYDRANGEA};
        for (int i = 0; i < order.length; i++) {
            final Flora f = order[i];
            flora[f.ordinal()] = cache.get("flora:" + f, GROUP_FLORA, i, () -> PropSprites.flora(f));
        }
        traffic = new Traffic(city, r -> pickReady(carEntries, r));
        pedestrians = new Pedestrians(city, r -> {
            int st = r.nextInt(PED_STYLES);
            pedPuppet(st);
            return st;
        });
        traffic.obstacles = trafficObstacles;
        traffic.autoDensity = true;
        city.dynamic = (x, z, out) -> {
            traffic.boxesNear(x, z, out);
            for (Vehicle v : parked) {
                float dx = v.x - x, dz = v.z - z;
                if (dx * dx + dz * dz < 64f) v.boxes(out);
            }
        };
        player = new Player(city.spawnX, city.spawnZ);
        city.chunks.ensureAround(player.x, player.z);
        player.y = city.surface(player.x, player.z);
        player.facing = (float) Math.PI;
        cam.yaw = 0f;
        cam.distance = 6f;
        if (progress != null) progress.step("Drawing the map", 0.80f);
        map = new com.harlequinxd.drivehard.core.map.MapData(city);
        if (progress != null) progress.step("Streaming the streets around you", 0.84f);
        // only what the camera can reach is kept: the view ends at 120 m, so chunks stream in a
        // little beyond that and are dropped soon after they fall out of range
        city.chunks.loadRadius = lowMemory ? 110f : 140f;
        city.chunks.loadAllNow(player.x, player.z, progress == null ? null
                : (what, done) -> progress.step(what, 0.84f + done * 0.15f));
        city.chunks.loadRadius = lowMemory ? 140f : 165f;
        city.chunks.unloadRadius = lowMemory ? 170f : 210f;
        city.chunks.start();
        for (int i = 0; i < 6; i++) requestCar();
        cache.start();
        if (progress != null) progress.step("Ready", 1f);
    }

    /** Adds a texture of the game's own (sprites, effects) after the island's textures. */
    private int addTexture(Texture t) {
        textures[nextTex] = t;
        return nextTex++;
    }

    public void dispose() {
        city.chunks.stop();
        cache.stop();
        if (renderer != null) renderer.dispose();
    }

    public void setResolution(int w, int h) {
        SoftRenderer soft = new SoftRenderer(w, h);
        soft.setThreads(Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1)));
        useRenderer(soft);
    }

    /** Draws with the given renderer from now on (the CPU renderer, or the GPU one on phones). */
    public void useRenderer(Renderer r) {
        if (renderer != null && renderer != r) renderer.dispose();
        renderer = r;
        renderer.textures = textures;
        renderer.ground = city.groundSource();
        renderer.setFogColor(0xBCD8EA);
        renderer.setZenithColor(0x3E86D0);
        renderer.env = env;
    }

    // ------------------------------------------------------------------ styles

    private static int pickReady(SpriteCache.Entry[] entries, Random r) {
        int n = 0;
        for (SpriteCache.Entry e : entries) if (e != null && e.ready()) n++;
        if (n == 0) return -1;
        int k = r.nextInt(n);
        for (int i = 0; i < entries.length; i++)
            if (entries[i] != null && entries[i].ready() && k-- == 0) return i;
        return -1;
    }

    private SpriteCache.Entry car(int style) {
        final int s = style;
        return carEntries[s] = cache.get("car:" + s, GROUP_CARS, 50, () -> PropSprites.car(s));
    }

    /** The puppet for one of the pedestrian looks (built on first use: a few milliseconds). */
    private com.harlequinxd.drivehard.core.model.Puppet pedPuppet(int style) {
        com.harlequinxd.drivehard.core.model.Puppet p = pedPuppets[style];
        if (p == null) {
            p = new com.harlequinxd.drivehard.core.model.Puppet(com.harlequinxd.drivehard.core.model.CharacterSpec.randomPedestrian(style));
            p.tex = pedTexBase + style;
            textures[p.tex] = p.atlas;
            pedPuppets[style] = p;
        }
        return p;
    }

    public void requestCar() { car(rnd.nextInt(Cars.STYLES)); }

    /** Starts baking one car style's sprites (tools, previews). */
    public void requestCarStyle(int style) { car(Math.floorMod(style, Cars.STYLES)); }
    public void requestPed() { pedPuppet(rnd.nextInt(PED_STYLES)); }

    // ------------------------------------------------------------------ update

    public void update(float dt, float stickX, float stickY, float dYaw, float dPitch, boolean jump) {
        update(dt, stickX, stickY, dYaw, dPitch, jump, 0f, 0f, 0f, false);
    }

    /**
     * One simulation step.
     * @param steer      driving: -1 left .. +1 right (D-pad)
     * @param gas        driving: accelerator 0..1
     * @param reverse    driving: brake / reverse 0..1
     * @param carButton  pressed this frame: get in the nearest car, or get out
     */
    public void update(float dt, float stickX, float stickY, float dYaw, float dPitch, boolean jump,
                       float steer, float gas, float reverse, boolean carButton) {
        update(dt, stickX, stickY, dYaw, dPitch, jump, steer, gas, reverse, carButton, false);
    }

    /** As above, with the handbrake held (driving: locks the rear wheels). */
    public void update(float dt, float stickX, float stickY, float dYaw, float dPitch, boolean jump,
                       float steer, float gas, float reverse, boolean carButton, boolean handbrake) {
        if (missions.frozen()) {
            // the countdown: engine running, handbrake on, nobody moves yet
            stickX = stickY = steer = gas = reverse = 0f;
            jump = carButton = false;
            handbrake = true;
        }
        inGas = gas; inBrake = reverse; inSteer = steer;
        if (vehicle != null) vehicle.handbrake = handbrake && mode == Mode.DRIVING;
        worldTick(dt);
        cam.orbit(dYaw, dPitch);
        if (dYaw != 0f || dPitch != 0f) camIdle = 0f; else camIdle += dt;
        playerTick(dt, stickX, stickY, jump, steer, gas, reverse, carButton);
        missions.update(dt);
        audio.update(dt);
    }

    /** The world carries on: time, weather, streaming, traffic and pedestrians. */
    private void worldTick(float dt) {
        time += dt;
        env.update(dt);
        cache.update();
        updatePlace(dt);
        // keep active styles alive and slowly rotate in new ones so all 100 appear over time
        for (Traffic.Car c : traffic.cars) if (carEntries[c.style] != null) cache.touch(carEntries[c.style]);
        if (vehicle != null && carEntries[vehicle.style] != null) cache.touch(carEntries[vehicle.style]);
        for (Vehicle v : parked) if (carEntries[v.style] != null) cache.touch(carEntries[v.style]);
        rotateTimer -= dt;
        if (rotateTimer <= 0f) {
            rotateTimer = 1.5f;
            boolean full = cache.readyCount(GROUP_CARS) >= CAR_CACHE;
            if (full) rotateTimer = 8f;           // once the cache is full, new styles trickle in slowly (baking costs CPU)
            if (cache.queuedCount() < 3) {
                if (cache.readyCount(GROUP_CARS) < CAR_CACHE || rnd.nextInt(4) == 0) requestCar();
            }
        }
        // forget evicted styles (cars/peds using them are re-requested when seen again)
        for (int i = 0; i < carEntries.length; i++) if (carEntries[i] != null && !cache.alive(carEntries[i])) carEntries[i] = null;

        city.chunks.update(player.x, player.z);
        city.chunks.ensureAround(player.x, player.z);
        if (renderer != null) {
            Renderer vr = renderer;
            float half = (float) Math.atan(vr.W * 0.5f / vr.focal());
            traffic.view(vr.ex, vr.ez, vr.fwdX(), vr.fwdZ(), half);
        }
        traffic.update(dt, time, player.x, player.z);
        pedestrians.update(dt, time, player.x, player.z);
        for (Vehicle v : parked) v.update(dt, 0f, 0f, 0f, city, v);
        trafficObstacles.clear();
        trafficObstacles.addAll(parked);
        if (vehicle != null) trafficObstacles.add(vehicle);
    }

    private void playerTick(float dt, float stickX, float stickY, boolean jump, float steer, float gas, float reverse, boolean carButton) {
        if (carButton) {
            if (mode == Mode.ON_FOOT) tryEnter();
            else if (mode == Mode.DRIVING) exitVehicle();
        }
        switch (mode) {
            case ON_FOOT:
                cam.toWorld(stickX, stickY, move);
                player.update(dt, move[0], move[1], jump, city);
                break;
            case ENTERING: {
                // the car rolls to a stop while she hops in through the driver's door
                vehicle.update(dt, 0f, 0f, 1f, city, null);
                popT += dt / 0.3f;
                vehicle.doorPoint(1, 0.1f, door);
                float t = Math.min(1f, popT);
                player.x = popX0 + (door[0] - popX0) * t;
                player.z = popZ0 + (door[1] - popZ0) * t;
                player.y = popY0 + (vehicle.y - popY0) * t + (float) Math.sin(Math.PI * t) * 0.7f;
                player.facing = (float) Math.atan2(vehicle.x - player.x, vehicle.z - player.z);
                player.anim = Animations.Anim.JUMP;
                player.animFrame = 1;
                if (popT >= 1f) mode = Mode.DRIVING;
                break;
            }
            case DRIVING:
                vehicle.update(dt, steer, gas, reverse, city, null);
                player.x = vehicle.x; player.y = vehicle.y; player.z = vehicle.z;
                break;
            case EXITING:
                player.update(dt, 0f, 0f, false, city);
                popT += dt;
                if (popT > 0.15f && player.onGround) mode = Mode.ON_FOOT;
                break;
        }
        updateWaterFx(dt);
        driveCamera(dt);
        float fx = mode == Mode.DRIVING || mode == Mode.ENTERING ? vehicle.x : player.x;
        float fz = mode == Mode.DRIVING || mode == Mode.ENTERING ? vehicle.z : player.z;
        float fy = mode == Mode.DRIVING || mode == Mode.ENTERING ? vehicle.y : player.y;
        if (mode == Mode.DRIVING) {
            // the camera rides on the car's weight: it drops back as the car pulls away and
            // surges forward under braking, and swings out a little in corners
            float k = Math.min(1f, dt * 4f);
            float wantSurge = Math.max(-0.9f, Math.min(0.9f, vehicle.pitch * 0.09f));
            float wantSway = Math.max(-0.7f, Math.min(0.7f, vehicle.roll * 0.06f));
            camSurge += (wantSurge - camSurge) * k;
            camSway += (wantSway - camSway) * k;
            float vf = vehicle.fx(), vfz = vehicle.fz();
            fx += vf * camSurge + -vfz * camSway;
            fz += vfz * camSurge + vf * camSway;
            dropSkids(dt);
        } else { camSurge = camSway = 0f; }
        cam.update(fx, fy, fz, dt);
        cameraCollision();
    }

    // ------------------------------------------------------------------ front end

    /** Story missions (briefings, objectives, results). */
    public final Missions missions;
    /** The options and story progress (set by the platform; may be null in tools). */
    public Settings settings;
    private float attractYaw = Float.NaN;

    /**
     * The main menu's backdrop: the world goes on (traffic, weather, time of day) while the camera
     * circles slowly, high above wherever the player is.
     */
    public void attract(float dt) {
        inGas = inBrake = inSteer = 0f;
        if (vehicle != null) vehicle.handbrake = false;
        worldTick(dt);
        if (Float.isNaN(attractYaw)) attractYaw = cam.yaw;
        attractYaw += dt * 0.05f;
        if (mode == Mode.DRIVING && vehicle != null) vehicle.update(dt, 0f, 0f, 1f, city, null);
        float fx = mapX(), fz = mapZ(), fy = driving() && vehicle != null ? vehicle.y : player.y;
        cam.yaw = attractYaw;
        cam.pitch += ((float) Math.toRadians(24) - cam.pitch) * Math.min(1f, dt * 1.5f);
        cam.distance += (20f - cam.distance) * Math.min(1f, dt * 1.5f);
        cam.targetHeight += (2.6f - cam.targetHeight) * Math.min(1f, dt * 1.5f);
        cam.update(fx, fy, fz, dt);
        cameraCollision();
        audio.update(dt);
    }

    /** Leaves the menu backdrop: the chase camera takes over again from where it is. */
    public void endAttract() { attractYaw = Float.NaN; camIdle = 1f; }

    /** The nearest ordinary street to (x, z): out = {x, z, yaw} in a lane (car) or on the kerb. */
    boolean streetSpot(float x, float z, boolean lane, float[] out) {
        float[] hit = new float[3], q = new float[5];
        boolean onRoad = false;
        for (float reach = 60f; reach <= 960f && !onRoad; reach *= 2f) onRoad = city.roads.nearestDrivable(x, z, reach, hit);
        if (!onRoad) return false;
        com.harlequinxd.drivehard.core.road.RoadEdge e = city.roads.edges.get((int) hit[0]);
        float s = hit[1];
        float lo = Math.min(e.len * 0.5f, e.clearA + 10f), hi = Math.max(e.len * 0.5f, e.len - e.clearB - 10f);
        s = Math.max(lo, Math.min(hi, s));
        if (lane) {
            e.lanePoint(s, 1, e.laneOffset(e.oneWay ? 0 : e.cls.lanes - 1), q);
            out[0] = q[0]; out[1] = q[1];
        } else {
            e.point(s, q);
            float off = e.cls.halfWidth() + (e.cls.city ? City.SIDEWALK * 0.5f : 1.2f);
            out[0] = q[0] + q[4] * off; out[1] = q[1] - q[3] * off;
        }
        out[2] = (float) Math.atan2(q[3], q[4]);
        return true;
    }

    /**
     * Puts the player at a mission's start: in a car (the one they are driving, or a fresh one)
     * stopped in a lane, or on foot on the kerb. Far moves build the streets there first.
     */
    void placeForMission(float x, float z, float yaw, boolean inCar) {
        if (Math.hypot(x - mapX(), z - mapZ()) > 150f) {
            traffic.cars.clear();
            pedestrians.peds.clear();
            city.chunks.loadAllNow(x, z, null);
        }
        float y = city.surface(x, z) + 0.05f;
        if (inCar) {
            if (vehicle == null || !driving()) {
                if (vehicle != null) parked.add(vehicle);
                int style = pickReady(carEntries, rnd);
                if (style < 0) style = rnd.nextInt(Cars.STYLES);
                vehicle = new Vehicle(style, x, z, yaw, 0f);
            }
            vehicle.x = x; vehicle.z = z; vehicle.yaw = yaw; vehicle.y = y;
            vehicle.vx = vehicle.vz = 0f;
            vehicle.setSpeed(0f);
            vehicle.parkingBrake = false;
            mode = Mode.DRIVING;
            player.x = x; player.z = z; player.y = y;
        } else {
            if (vehicle != null && driving()) { vehicle.parkingBrake = true; parked.add(vehicle); }
            vehicle = null;
            mode = Mode.ON_FOOT;
            player.x = x; player.z = z; player.y = y;
            player.vx = player.vy = player.vz = 0f;
            player.facing = yaw;
            player.onGround = true;
        }
        while (parked.size() > MAX_PARKED) parked.remove(0);
        cam.yaw = yaw + (float) Math.PI;
        cam.pitch = (float) Math.toRadians(16);
        camIdle = 1f;
        placeTimer = 0f;
    }

    /** Chase camera while driving: pulls back, raises the target, and swings behind the car when not dragged. */
    private void driveCamera(float dt) {
        boolean driving = mode == Mode.DRIVING || mode == Mode.ENTERING;
        float wantDist = driving ? 7f + Math.min(2.5f, Math.abs(vehicle.speed) * 0.09f) : 6f;
        cam.distance += (wantDist - cam.distance) * Math.min(1f, dt * 3f);
        cam.targetHeight += ((driving ? 1.5f : player.swimming ? 0.55f : 1.1f) - cam.targetHeight) * Math.min(1f, dt * 3f);
        if (driving && camIdle > 0.8f && Math.abs(vehicle.speed) > 1f) {
            // look down the direction of travel: in a slide the camera follows where the car is
            // going, and you watch the body swing out sideways
            float heading = vehicle.yaw;
            float sp = (float) Math.hypot(vehicle.vx, vehicle.vz);
            if (sp > 4f && vehicle.speed > 0f) {
                float travel = (float) Math.atan2(vehicle.vx, vehicle.vz);
                float d = travel - heading;
                while (d > Math.PI) d -= (float) (2 * Math.PI);
                while (d < -Math.PI) d += (float) (2 * Math.PI);
                heading += d * 0.6f;
            }
            float behind = heading + (float) Math.PI;
            float diff = behind - cam.yaw;
            while (diff > Math.PI) diff -= (float) (2 * Math.PI);
            while (diff < -Math.PI) diff += (float) (2 * Math.PI);
            cam.yaw += diff * Math.min(1f, dt * 3.2f);
            float wantPitch = (float) Math.toRadians(16);
            cam.pitch += (wantPitch - cam.pitch) * Math.min(1f, dt * 1.5f);
        }
    }

    // ------------------------------------------------------------------ getting in and out

    private Traffic.Car nearCar;
    private Vehicle nearParked;

    /** True when on foot next to a car that can be taken (drives the fading "get in" button). */
    public boolean canEnter() {
        if (mode != Mode.ON_FOOT) return false;
        findNearestCar();
        return nearCar != null || nearParked != null;
    }

    /** True if this car style's sprites are baked and drawable. */
    public boolean carReady(int style) { SpriteCache.Entry e = carEntries[style]; return e != null && e.ready(); }

    public boolean driving() { return mode == Mode.DRIVING || mode == Mode.ENTERING; }

    /** The island map (relief, roads and names) for the minimap and the map screen. */
    public final com.harlequinxd.drivehard.core.map.MapData map;

    /** Where you are and which way you face, for the map (facing vector = (sin, cos) in x, z). */
    public float mapX() { return vehicle != null && driving() ? vehicle.x : player.x; }
    public float mapZ() { return vehicle != null && driving() ? vehicle.z : player.z; }
    public float mapHeading() { return vehicle != null && driving() ? vehicle.yaw : player.facing; }

    /** Fast travel to a named location on the existing island map. Call on the game thread. */
    public boolean fastTravel(com.harlequinxd.drivehard.core.map.MapData.Label destination) {
        return fastTravel(destination, null);
    }

    /**
     * Moves the player (and the car they are driving) to a place on the map. They arrive on the
     * nearest ordinary street there: in a lane and pointing along it when driving, on the kerb when
     * walking. The streets around the arrival point are built before play resumes (reported through
     * progress, for the travel screen), and traffic and pedestrians are repopulated around it.
     */
    public boolean fastTravel(com.harlequinxd.drivehard.core.map.MapData.Label destination, Progress progress) {
        if (destination == null || mode == Mode.ENTERING || mode == Mode.EXITING) return false;
        if (progress != null) progress.step("Finding a street in " + destination.name, 0.02f);
        float x = destination.x, z = destination.z, yaw = driving() && vehicle != null ? vehicle.yaw : player.facing;
        float[] hit = new float[3], q = new float[5];
        boolean onRoad = false;
        for (float reach = 120f; reach <= 960f && !onRoad; reach *= 2f) onRoad = city.roads.nearestDrivable(x, z, reach, hit);
        if (onRoad) {
            com.harlequinxd.drivehard.core.road.RoadEdge e = city.roads.edges.get((int) hit[0]);
            float s = hit[1];
            float lo = Math.min(e.len * 0.5f, e.clearA + 12f), hi = Math.max(e.len * 0.5f, e.len - e.clearB - 12f);
            s = Math.max(lo, Math.min(hi, s));        // clear of the junction boxes at either end
            if (driving() && vehicle != null) {
                e.lanePoint(s, 1, e.laneOffset(e.oneWay ? 0 : e.cls.lanes - 1), q);
                x = q[0]; z = q[1];
            } else {
                e.point(s, q);
                // step off the carriageway onto the left kerb / verge
                float off = e.cls.halfWidth() + (e.cls.city ? City.SIDEWALK * 0.5f : 1.2f);
                x = q[0] + q[4] * off; z = q[1] - q[3] * off;
            }
            yaw = (float) Math.atan2(q[3], q[4]);
        }
        // everybody who was around the old spot stays there
        traffic.cars.clear();
        pedestrians.peds.clear();
        if (progress != null) progress.step("Building the streets of " + destination.name, 0.08f);
        city.chunks.loadAllNow(x, z, progress == null ? null
                : (what, done) -> progress.step("Building the streets of " + destination.name, 0.08f + done * 0.9f));
        if (driving() && vehicle != null) {
            vehicle.x = x; vehicle.z = z; vehicle.yaw = yaw;
            vehicle.setSpeed(0f);
            vehicle.y = city.surface(x, z) + 0.05f;
            player.x = x; player.z = z; player.y = vehicle.y;
        } else {
            player.x = x; player.z = z;
            player.y = city.surface(x, z) + 0.05f;
            player.vx = player.vy = player.vz = 0f;
            player.facing = yaw;
            player.onGround = true;
        }
        placeTimer = 0f;
        cam.yaw = yaw + (float) Math.PI;
        cam.update(x, player.y, z, 0.016f);
        if (progress != null) progress.step("Arrived", 1f);
        return true;
    }

    // ------------------------------------------------------------------ where am I

    /** Name of the city, the town or village here ("" in open country), and the region. */
    public volatile String placeName = "", regionName = "";
    private float placeTimer;

    private void updatePlace(float dt) {
        placeTimer -= dt;
        if (placeTimer > 0f) return;
        placeTimer = 0.5f;
        float x = vehicle != null && driving() ? vehicle.x : player.x, z = vehicle != null && driving() ? vehicle.z : player.z;
        com.harlequinxd.drivehard.core.road.RoadNetwork n = city.roads;
        String place;
        com.harlequinxd.drivehard.core.road.Town.Region rg = n.regionAt(x, z);
        String region = rg == null ? "" : rg.name;
        if (city.nearUrban(x, z, 150f)) {
            City.Suburb sb = city.suburbAt(x, z);
            place = sb != null ? sb.name : City.NAME;
            if (sb != null) region = City.NAME;
        } else {
            com.harlequinxd.drivehard.core.road.Town t = n.townAt(x, z, 80f);
            place = t == null ? "" : t.name;
        }
        placeName = place;
        regionName = region;
    }

    private void findNearestCar() {
        nearCar = null;
        nearParked = null;
        float best = ENTER_RANGE;
        for (Traffic.Car c : traffic.cars) {
            SpriteCache.Entry e = carEntries[c.style];
            if (e == null || !e.ready()) continue;
            float d = bodyDistance(c.x, c.z, c.yaw, Cars.spec(c.style).L, Cars.spec(c.style).W);
            if (d < best) { best = d; nearCar = c; }
        }
        for (Vehicle v : parked) {
            float d = v.distanceTo(player.x, player.z);
            if (d < best) { best = d; nearParked = v; nearCar = null; }
        }
    }

    private float bodyDistance(float cx, float cz, float yaw, float len, float wid) {
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        float dx = player.x - cx, dz = player.z - cz;
        float along = dx * fx + dz * fz, side = -dx * fz + dz * fx;
        float ea = Math.max(0f, Math.abs(along) - len * 0.5f), es = Math.max(0f, Math.abs(side) - wid * 0.5f);
        return (float) Math.sqrt(ea * ea + es * es);
    }

    private void tryEnter() {
        findNearestCar();
        if (nearParked != null) {
            vehicle = nearParked;
            parked.remove(nearParked);
        } else if (nearCar != null) {
            Traffic.Car c = nearCar;
            traffic.cars.remove(c);
            // the driver hits the brakes when she yanks the door open
            vehicle = new Vehicle(c.style, c.x, c.z, c.yaw, Math.min(c.speed, 2f));
            vehicle.y = c.y;
        } else return;
        mode = Mode.ENTERING;
        popT = 0f;
        popX0 = player.x; popY0 = player.y; popZ0 = player.z;
        player.vx = player.vz = player.vy = 0f;
    }

    private void exitVehicle() {
        Vehicle v = vehicle;
        // driver's door is on the right (right-hand drive); fall back to the left, then front/back
        float[][] tries = new float[4][2];
        v.doorPoint(1, 0.55f, tries[0]);
        v.doorPoint(-1, 0.55f, tries[1]);
        tries[2][0] = v.x + v.fx() * (v.len * 0.5f + 0.7f); tries[2][1] = v.z + v.fz() * (v.len * 0.5f + 0.7f);
        tries[3][0] = v.x - v.fx() * (v.len * 0.5f + 0.7f); tries[3][1] = v.z - v.fz() * (v.len * 0.5f + 0.7f);
        float[] spot = null;
        for (float[] t : tries) if (spotFree(t[0], t[1], v)) { spot = t; break; }
        if (spot == null) return;   // boxed in: stay in the car
        v.parkingBrake = true;
        v.handbrake = false;
        parked.add(v);
        while (parked.size() > MAX_PARKED) {
            // forget the farthest parked car
            Vehicle far = parked.get(0);
            float fd = -1;
            for (Vehicle p : parked) {
                float d = (p.x - v.x) * (p.x - v.x) + (p.z - v.z) * (p.z - v.z);
                if (d > fd && p != v) { fd = d; far = p; }
            }
            parked.remove(far);
        }
        vehicle = null;
        player.x = spot[0]; player.z = spot[1];
        player.y = city.surface(spot[0], spot[1]) + 0.3f;
        player.vx = player.vz = 0f;
        player.vy = 4.5f;                      // pop out with a little hop
        player.onGround = false;
        player.facing = (float) Math.atan2(spot[0] - v.x, spot[1] - v.z);
        mode = Mode.EXITING;
        popT = 0f;
    }

    private boolean spotFree(float x, float z, Vehicle self) {
        if (city.blocked(x, z)) return false;
        float y = city.surface(x, z);
        for (Box b : city.near(x, z)) {
            if (b.owner == self) continue;
            if (b.maxY < y + 0.4f || b.minY > y + Player.HEIGHT) continue;
            if (x > b.minX - Player.RADIUS && x < b.maxX + Player.RADIUS && z > b.minZ - Player.RADIUS && z < b.maxZ + Player.RADIUS) return false;
        }
        return true;
    }

    private float camPull = 1f;

    private void cameraCollision() {
        float dx = cam.eyeX - cam.tx, dy = cam.eyeY - cam.ty, dz = cam.eyeZ - cam.tz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float hit = 1f;
        for (float t = 0.4f; t <= len; t += 0.35f) {
            float px = cam.tx + dx / len * t, py = cam.ty + dy / len * t, pz = cam.tz + dz / len * t;
            if (insideSolid(px, py, pz)) { hit = Math.max(0.12f, (t - 0.45f) / len); break; }
        }
        camPull = hit < camPull ? hit : camPull + (hit - camPull) * 0.08f;
        cam.eyeX = cam.tx + dx * camPull;
        cam.eyeY = cam.ty + dy * camPull;
        cam.eyeZ = cam.tz + dz * camPull;
        float gh = city.terrain.height(cam.eyeX, cam.eyeZ) + 0.5f;
        if (cam.eyeY < gh) cam.eyeY = gh;
    }

    private boolean insideSolid(float x, float y, float z) {
        for (Box b : city.near(x, z))
            if (x > b.minX - 0.2f && x < b.maxX + 0.2f && z > b.minZ - 0.2f && z < b.maxZ + 0.2f && y > b.minY && y < b.maxY + 0.2f
                    && (b.maxX - b.minX) > 2.5f && (b.maxZ - b.minZ) > 2.5f) return true;
        return false;
    }

    // ------------------------------------------------------------------ render

    public void render() {
        Renderer r = renderer;
        r.time = time;
        r.lookAt(cam.eyeX, cam.eyeY, cam.eyeZ, cam.tx, cam.ty, cam.tz);
        r.begin();
        r.drawSky();
        r.drawGround();
        Terrain terrain = city.terrain;

        batchesDrawn = batchesCulled = batchesOccluded = spritesDrawn = carsDrawn = pedsDrawn = 0;
        float far2 = (r.far + 40f) * (r.far + 40f);
        for (Chunk c : city.chunks.chunks()) {
            if (dist2(c.minX, c.minZ, c.maxX, c.maxZ, r.ex, r.ez) > far2) continue;
            if (!r.boxVisible(c.minX, c.minY - 2f, c.minZ, c.maxX, c.maxY + 2f, c.maxZ)) continue;
            for (Geo g : c.batches) {
                if (dist2(g.minX, g.minZ, g.maxX, g.maxZ, r.ex, r.ez) > far2
                        || !r.boxVisible(g.minX, g.minY, g.minZ, g.maxX, g.maxY, g.maxZ)) {
                    batchesCulled++;
                    continue;
                }
                if (occlusionCull && g.object && terrain.occludedBox(r.ex, r.ey, r.ez, g.minX, g.minZ, g.maxX, g.maxZ, g.minY, g.maxY)) {
                    batchesOccluded++;
                    continue;
                }
                r.drawGeo(g);
                batchesDrawn++;
            }
            drawSprites(c, r, terrain);
        }
        drawSkids(r);
        drawTraffic(r);
        drawPedestrians(r);
        for (Vehicle v : parked) drawVehicle(r, v);
        if (vehicle != null) drawVehicle(r, vehicle);
        if (mode != Mode.DRIVING) drawPlayer(r);
        r.flush();
        r.weatherOverlay();
    }

    /** Skip buildings and plants hidden behind hills (off for tests: nothing should change). */
    public boolean occlusionCull = true;

    private void drawVehicle(Renderer r, Vehicle v) {
        SpriteCache.Entry e = carEntries[v.style];
        if (e == null) e = car(v.style);
        if (!e.ready()) return;
        if (!r.boxVisible(v.x - 6, v.y, v.z - 6, v.x + 6, v.y + 4f, v.z + 6)) return;
        r.groundQuadRotated(lib.shadow, v.x, v.y + 0.03f, v.z, v.len * 0.62f, v.wid * 0.7f, v.yaw,
                SoftRenderer.MODE_SHADOW, SoftRenderer.L_SHADOW);
        float viewYaw = (float) Math.atan2(r.ex - v.x, r.ez - v.z);
        sprite(r, e.tex, e.sheet, e.sheet.directionFor(v.yaw, viewYaw), 0, v.x, v.y, v.z, -1.0f);
        carsDrawn++;
    }

    private void drawPlayer(Renderer r) {
        Player p = player;
        drawWaterFx(r);
        float gy = city.groundHeight(p.x, p.z, Player.RADIUS, p.y + 0.01f, 0.01f);
        float air = Math.min(1f, (p.y - gy) / 3f);
        if (p.waterDepth < 0.15f && !p.swimming)
            r.groundQuad(lib.shadow, p.x, gy + 0.02f, p.z, 0.38f * (1f - air * 0.4f), SoftRenderer.MODE_SHADOW, SoftRenderer.L_SHADOW);
        // the sprite puppet on her skeleton; in water only what is above the surface is drawn
        Pose pose = p.pose();
        float clip = p.swimming ? p.y : (p.waterDepth > 0.02f && p.y < 0f ? 0f : -1e9f);
        // while hopping in, draw her just in front of the car so she reads as climbing in
        puppet.draw(r, p.x, p.y, p.z, p.facing, pose, clip, mode == Mode.ENTERING ? -1.6f : -0.8f);
    }

    // ------------------------------------------------------------------ water effects

    /** A ripple ring on the water or a splash burst. */
    private static final class WaterFx {
        float x, y, z, age, life, size;
        boolean splash;
    }

    private final List<WaterFx> waterFx = new ArrayList<>();
    private float fxTimer, wakeTimer, lastPhase;

    private void ripple(float x, float z, float size, float life) {
        if (waterFx.size() > 60) return;
        WaterFx f = new WaterFx();
        f.x = x; f.z = z; f.size = size; f.life = life; f.y = 0.03f;
        waterFx.add(f);
    }

    private void splash(float x, float y, float z, float size) {
        if (waterFx.size() > 60) return;
        WaterFx f = new WaterFx();
        f.x = x; f.y = y; f.z = z; f.size = size; f.life = 0.55f; f.splash = true;
        waterFx.add(f);
    }

    /**
     * Splashes and ripples for the player in the sea: footsteps splash and ring the water when
     * wading; strokes throw spray from the hands and kicks (crawl) or push a ring out from the
     * head (breaststroke), a wake of rings trails behind, treading water sends out slow rings,
     * and jumping in throws up a big splash.
     */
    private void updateWaterFx(float dt) {
        for (int i = waterFx.size() - 1; i >= 0; i--) {
            WaterFx f = waterFx.get(i);
            f.age += dt;
            if (f.age >= f.life) waterFx.remove(i);
        }
        Player p = player;
        if (mode != Mode.ON_FOOT && mode != Mode.EXITING) return;
        float fx = (float) Math.sin(p.facing), fz = (float) Math.cos(p.facing), sx = fz, sz = -fx;
        if (p.splashImpact < -1f) {
            float k = Math.min(1.8f, 0.7f - p.splashImpact * 0.12f);
            splash(p.x, 0f, p.z, k);
            ripple(p.x, p.z, 1.6f * k, 1.4f);
            ripple(p.x, p.z, 2.4f * k, 2.0f);
        }
        boolean wet = p.swimming || p.waterDepth > 0.08f;
        if (!wet) { lastPhase = p.cyclePhase(); return; }
        float speed = (float) Math.sqrt(p.vx * p.vx + p.vz * p.vz);
        float ph = p.cyclePhase();
        boolean wrap = ph < lastPhase - 0.2f, half = lastPhase < 0.5f && ph >= 0.5f;
        lastPhase = ph;
        fxTimer -= dt;
        wakeTimer -= dt;
        if (p.swimming) {
            switch (p.anim) {
                case SWIM_FREE:
                    if (wrap || half) {
                        float side = wrap ? 1f : -1f;
                        splash(p.x + fx * 0.95f + sx * 0.3f * side, 0f, p.z + fz * 0.95f + sz * 0.3f * side, 0.45f);
                        ripple(p.x + fx * 0.9f + sx * 0.3f * side, p.z + fz * 0.9f + sz * 0.3f * side, 0.9f, 1.1f);
                        splash(p.x - fx * 1.05f, 0f, p.z - fz * 1.05f, 0.3f);
                    }
                    break;
                case SWIM_BREAST:
                    if (wrap) ripple(p.x + fx * 0.5f, p.z + fz * 0.5f, 1.4f, 1.6f);
                    if (half) splash(p.x - fx * 0.9f, 0f, p.z - fz * 0.9f, 0.3f);
                    break;
                default:
                    if (fxTimer <= 0f) { ripple(p.x, p.z, 1.2f, 1.8f); fxTimer = 0.9f; }
                    break;
            }
            if (speed > 0.4f && wakeTimer <= 0f) {
                ripple(p.x - fx * 0.7f, p.z - fz * 0.7f, 0.7f + speed * 0.25f, 1.3f);
                wakeTimer = 0.45f;
            }
        } else {
            // wading: a splash at each step, rings round the legs
            float depth = Math.min(1f, p.waterDepth);
            if (speed > 0.5f && p.onGround && (wrap || half)) {
                float side = wrap ? 1f : -1f;
                float k = Math.min(1.1f, 0.25f + speed * 0.09f) * (0.5f + depth);
                splash(p.x + fx * 0.25f + sx * 0.12f * side, 0f, p.z + fz * 0.25f + sz * 0.12f * side, k);
                ripple(p.x, p.z, 0.8f + speed * 0.12f, 1.2f);
            } else if (fxTimer <= 0f) {
                ripple(p.x, p.z, 0.7f + depth * 0.4f, 1.6f);
                fxTimer = 1.3f;
            }
        }
    }

    private void drawWaterFx(Renderer r) {
        for (WaterFx f : waterFx) {
            float t = f.age / f.life;
            if (f.splash) {
                int frame = Math.min(splashSheet.frames - 1, (int) (t * splashSheet.frames));
                int u = splashSheet.frameX(0, frame), v = splashSheet.frameY(0, frame);
                float k = f.size;
                r.billboard(splashTex, f.x, f.y, f.z, splashSheet.left() * k, splashSheet.right() * k,
                        splashSheet.bottom() * k, splashSheet.top() * k, u, v, u + splashSheet.frameW, v + splashSheet.frameH, -1.0f);
            } else {
                int frame = Math.min(rippleTex.length - 1, (int) (t * rippleTex.length));
                r.groundQuad(rippleTex[frame], f.x, f.y, f.z, f.size, SoftRenderer.MODE_ALPHA, SoftRenderer.L_DECAL, -0.2f);
            }
        }
    }

    private void drawSprites(Chunk c, Renderer r, Terrain terrain) {
        float[] sp = c.sprites;
        float tree2 = treeDist * treeDist, shrub2 = shrubDist * shrubDist, small2 = smallSpriteDist * smallSpriteDist;
        for (int i = 0; i < c.spriteCount; i++) {
            int o = i * Chunk.SPRITE_STRIDE;
            int kind = (int) sp[o];
            float x = sp[o + 2], y = sp[o + 3], z = sp[o + 4], yaw = sp[o + 5];
            float dx = x - r.ex, dz = z - r.ez, d2 = dx * dx + dz * dz;
            SpriteSheet s;
            int tex, frame;
            float lim;
            if (kind < Chunk.SPRITE_LAMP) {
                SpriteCache.Entry e = flora[kind];
                if (e == null || !e.ready()) continue;
                Flora f = Flora.values()[kind];
                lim = f.tree ? tree2 : shrub2;
                if (d2 > lim) continue;
                s = e.sheet; tex = e.tex; frame = (int) sp[o + 1];
            } else if (kind == Chunk.SPRITE_LAMP) {
                if (d2 > tree2) continue;
                s = lamps; tex = lampTex; frame = 0;
            } else if (kind == Chunk.SPRITE_UTILITY) {
                if (d2 > tree2) continue;
                s = poles; tex = poleTex; frame = 0;
            } else if (kind == Chunk.SPRITE_UMBRELLA) {
                if (d2 > small2) continue;
                s = umbrellas; tex = umbrellaTex; frame = (int) sp[o + 1];
            } else {
                if (d2 > tree2) continue;
                int ref = (int) sp[o + 1];
                int st = city.traffic.state(ref >> 1, (ref & 1) == 1, time);
                s = signals; tex = signalTex;
                frame = st == TrafficLights.GREEN ? 0 : (st == TrafficLights.YELLOW ? 1 : 2);
            }
            float top = s.top();
            if (!r.boxVisible(x + s.left(), y, z + s.left(), x + s.right(), y + top, z + s.right())) continue;
            if (occlusionCull && d2 > 900f) {
                // a tree or pole is hidden only if its top and both sides of its crown are
                float hw = (s.right() - s.left()) * 0.5f;
                float rxv = (float) Math.cos(Math.atan2(r.ex - x, r.ez - z)), rzv = -(float) Math.sin(Math.atan2(r.ex - x, r.ez - z));
                if (terrain.occluded(r.ex, r.ey, r.ez, x, y + top + 1f, z)
                        && terrain.occluded(r.ex, r.ey, r.ez, x + rxv * hw, y + top * 0.75f, z + rzv * hw)
                        && terrain.occluded(r.ex, r.ey, r.ez, x - rxv * hw, y + top * 0.75f, z - rzv * hw)) continue;
            }
            float viewYaw = (float) Math.atan2(r.ex - x, r.ez - z);
            sprite(r, tex, s, s.directionFor(yaw, viewYaw), frame, x, y, z, -0.3f);
            spritesDrawn++;
        }
    }

    private void drawTraffic(Renderer r) {
        float lim = carDist * carDist;
        for (Traffic.Car c : traffic.cars) {
            float dx = c.x - r.ex, dz = c.z - r.ez;
            if (dx * dx + dz * dz > lim) continue;
            SpriteCache.Entry e = carEntries[c.style];
            if (e == null) e = car(c.style);
            if (!e.ready()) continue;
            SpriteSheet s = e.sheet;
            if (!r.boxVisible(c.x - 4, c.y, c.z - 4, c.x + 4, c.y + 3.5f, c.z + 4)) continue;
            Cars.Spec sp = Cars.spec(c.style);
            r.groundQuadRotated(lib.shadow, c.x, c.y + 0.03f, c.z, sp.L * 0.62f, sp.W * 0.7f, c.yaw,
                    SoftRenderer.MODE_SHADOW, SoftRenderer.L_SHADOW);
            float viewYaw = (float) Math.atan2(r.ex - c.x, r.ez - c.z);
            sprite(r, e.tex, s, s.directionFor(c.yaw, viewYaw), 0, c.x, c.y, c.z, -1.0f);
            carsDrawn++;
        }
    }

    private void drawPedestrians(Renderer r) {
        float lim = pedDist * pedDist;
        for (Pedestrians.Ped p : pedestrians.peds) {
            float dx = p.x - r.ex, dz = p.z - r.ez;
            if (dx * dx + dz * dz > lim) continue;
            if (!r.boxVisible(p.x - 0.5f, p.y, p.z - 0.5f, p.x + 0.5f, p.y + 2f, p.z + 0.5f)) continue;
            com.harlequinxd.drivehard.core.model.Puppet pp = pedPuppet(p.style);
            r.groundQuad(lib.shadow, p.x, p.y + 0.02f, p.z, 0.32f, SoftRenderer.MODE_SHADOW, SoftRenderer.L_SHADOW);
            // the same skeleton and animations as the player: idling at a crossing, or mid-stride
            boolean waiting = p.state == Pedestrians.WAIT;
            float ph = waiting ? p.idleTime * 1.6f / Animations.Anim.IDLE.frames : p.phase;
            ph -= (float) Math.floor(ph);
            Pose pose = Animations.poseAt(waiting ? Animations.Anim.IDLE : Animations.Anim.WALK, ph, 0);
            pp.draw(r, p.x, p.y, p.z, p.yaw, pose, -1e9f, -0.7f);
            pedsDrawn++;
        }
    }

    private static void sprite(Renderer r, int tex, SpriteSheet s, int dir, int frame, float x, float y, float z, float bias) {
        int u = s.frameX(dir, frame), v = s.frameY(dir, frame);
        r.billboard(tex, x, y, z, s.left(), s.right(), s.bottom(), s.top(), u, v, u + s.frameW, v + s.frameH, bias);
    }

    private static float dist2(float x0, float z0, float x1, float z1, float px, float pz) {
        float cx = Math.max(x0, Math.min(px, x1)), cz = Math.max(z0, Math.min(pz, z1));
        return (cx - px) * (cx - px) + (cz - pz) * (cz - pz);
    }
}
