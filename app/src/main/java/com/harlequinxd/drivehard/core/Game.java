package com.harlequinxd.drivehard.core;

import com.harlequinxd.drivehard.core.city.Chunk;
import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.city.Terrain;
import com.harlequinxd.drivehard.core.city.TrafficLights;
import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.game.OrbitCamera;
import com.harlequinxd.drivehard.core.game.Player;
import com.harlequinxd.drivehard.core.model.Animations;
import com.harlequinxd.drivehard.core.model.CharacterModel;
import com.harlequinxd.drivehard.core.model.CharacterSpec;
import com.harlequinxd.drivehard.core.model.Cars;
import com.harlequinxd.drivehard.core.model.Flora;
import com.harlequinxd.drivehard.core.render.Geo;
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

/** Platform-independent game: streamed island, player, traffic, pedestrians, flora and the renderer. */
public final class Game {
    public static final int GROUP_FLORA = 0, GROUP_CARS = 1, GROUP_PEDS = 2;
    public static final int CAR_CACHE = 32, PED_CACHE = 20;

    public final TextureLibrary lib;
    public final City city;
    public final Player player;
    public final OrbitCamera cam = new OrbitCamera();
    public final SpriteSheet sheet, lamps, signals, umbrellas, poles;
    private final int playerTex, lampTex, signalTex, umbrellaTex, poleTex;
    public final Texture[] textures = new Texture[4096];
    public final SpriteCache cache;
    public final Traffic traffic;
    public final Pedestrians pedestrians;
    private final SpriteCache.Entry[] flora = new SpriteCache.Entry[Flora.values().length];
    private final SpriteCache.Entry[] carEntries = new SpriteCache.Entry[Cars.STYLES];
    private final SpriteCache.Entry[] pedEntries = new SpriteCache.Entry[100];
    private final Random rnd = new Random(7);
    public SoftRenderer renderer;
    private final float[] move = new float[2];
    public float time;
    private float rotateTimer;

    /** PS1-style draw distances (metres). */
    public float treeDist = 105f, shrubDist = 50f, smallSpriteDist = 60f, carDist = 115f, pedDist = 70f;

    // stats
    public int batchesDrawn, batchesCulled, batchesOccluded, spritesDrawn, carsDrawn, pedsDrawn;

    public interface Progress { void step(String what); }

    public Game(long seed, Progress progress) {
        if (progress != null) progress.step("Painting textures");
        lib = new TextureLibrary(seed);
        if (progress != null) progress.step("Baking sprites");
        sheet = new SpriteBaker().bake(new CharacterModel(CharacterSpec.player()));
        lamps = PropSprites.lamp();
        signals = PropSprites.signals();
        umbrellas = PropSprites.umbrellas();
        poles = PropSprites.utilityPole();
        playerTex = lib.add(Texture.padded(sheet.pixels, sheet.width, sheet.height));
        lampTex = lib.add(Texture.padded(lamps.pixels, lamps.width, lamps.height));
        signalTex = lib.add(Texture.padded(signals.pixels, signals.width, signals.height));
        umbrellaTex = lib.add(Texture.padded(umbrellas.pixels, umbrellas.width, umbrellas.height));
        poleTex = lib.add(Texture.padded(poles.pixels, poles.width, poles.height));
        for (int i = 0; i < lib.list.size(); i++) textures[i] = lib.list.get(i);
        cache = new SpriteCache(textures, lib.list.size(), new int[]{0, CAR_CACHE, PED_CACHE});

        // plants bake in the background, most common species first
        Flora[] order = {Flora.GINKGO, Flora.ZELKOVA, Flora.SAKURA, Flora.BLACK_PINE, Flora.MAPLE, Flora.CEDAR,
                Flora.CAMPHOR, Flora.BAMBOO, Flora.AZALEA, Flora.BOXWOOD, Flora.CAMELLIA, Flora.HYDRANGEA};
        for (int i = 0; i < order.length; i++) {
            final Flora f = order[i];
            flora[f.ordinal()] = cache.get("flora:" + f, GROUP_FLORA, i, () -> PropSprites.flora(f));
        }
        if (progress != null) progress.step("Raising the island");
        city = new City(lib, seed);
        traffic = new Traffic(city, r -> pickReady(carEntries, r));
        pedestrians = new Pedestrians(city, r -> pickReady(pedEntries, r));
        city.dynamic = traffic::boxesNear;
        player = new Player(city.spawnX, city.spawnZ);
        city.chunks.ensureAround(player.x, player.z);
        player.y = city.surface(player.x, player.z);
        player.facing = (float) Math.PI;
        cam.yaw = 0f;
        cam.distance = 6f;
        if (progress != null) progress.step("Streaming the city");
        city.chunks.loadRadius = 150f;
        city.chunks.loadAllNow(player.x, player.z);
        city.chunks.loadRadius = 200f;
        city.chunks.start();
        for (int i = 0; i < 6; i++) { requestCar(); requestPed(); }
        cache.start();
    }

    public void dispose() {
        city.chunks.stop();
        cache.stop();
        if (renderer != null) renderer.dispose();
    }

    public void setResolution(int w, int h) {
        if (renderer != null) renderer.dispose();
        renderer = new SoftRenderer(w, h);
        renderer.setThreads(Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1)));
        renderer.textures = textures;
        renderer.setFogColor(0xBCD8EA);
        renderer.setZenithColor(0x3E86D0);
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

    private SpriteCache.Entry ped(int style) {
        final int s = style;
        return pedEntries[s] = cache.get("ped:" + s, GROUP_PEDS, 40, () -> PropSprites.pedestrian(s));
    }

    public void requestCar() { car(rnd.nextInt(Cars.STYLES)); }
    public void requestPed() { ped(rnd.nextInt(100)); }

    // ------------------------------------------------------------------ update

    public void update(float dt, float stickX, float stickY, float dYaw, float dPitch, boolean jump) {
        time += dt;
        cache.update();
        // keep active styles alive and slowly rotate in new ones so all 100 appear over time
        for (Traffic.Car c : traffic.cars) if (carEntries[c.style] != null) cache.touch(carEntries[c.style]);
        for (Pedestrians.Ped p : pedestrians.peds) if (pedEntries[p.style] != null) cache.touch(pedEntries[p.style]);
        rotateTimer -= dt;
        if (rotateTimer <= 0f) {
            rotateTimer = 1.5f;
            if (cache.queuedCount() < 3) {
                if (cache.readyCount(GROUP_CARS) < CAR_CACHE || rnd.nextInt(8) == 0) requestCar();
                if (cache.readyCount(GROUP_PEDS) < PED_CACHE || rnd.nextInt(8) == 0) requestPed();
            }
        }
        // forget evicted styles (cars/peds using them are re-requested when seen again)
        for (int i = 0; i < carEntries.length; i++) if (carEntries[i] != null && !cache.alive(carEntries[i])) carEntries[i] = null;
        for (int i = 0; i < pedEntries.length; i++) if (pedEntries[i] != null && !cache.alive(pedEntries[i])) pedEntries[i] = null;

        city.chunks.update(player.x, player.z);
        city.chunks.ensureAround(player.x, player.z);
        traffic.update(dt, time, player.x, player.z);
        city.railway.update(dt);
        pedestrians.update(dt, time, player.x, player.z);
        cam.orbit(dYaw, dPitch);
        cam.toWorld(stickX, stickY, move);
        player.update(dt, move[0], move[1], jump, city);
        cam.update(player.x, player.y, player.z, dt);
        cameraCollision();
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
        SoftRenderer r = renderer;
        r.time = time;
        r.lookAt(cam.eyeX, cam.eyeY, cam.eyeZ, cam.tx, cam.ty, cam.tz);
        r.begin();
        r.drawSky();
        Terrain terrain = city.terrain;

        batchesDrawn = batchesCulled = batchesOccluded = spritesDrawn = carsDrawn = pedsDrawn = 0;
        float far2 = (r.far + 40f) * (r.far + 40f);
        for (Chunk c : city.chunks.chunks()) {
            if (dist2(c.minX, c.minZ, c.maxX, c.maxZ, r.ex, r.ez) > far2) continue;
            if (!r.boxVisible(c.minX, -15f, c.minZ, c.maxX, 220f, c.maxZ)) continue;
            for (Geo g : c.batches) {
                if (dist2(g.minX, g.minZ, g.maxX, g.maxZ, r.ex, r.ez) > far2
                        || !r.boxVisible(g.minX, g.minY, g.minZ, g.maxX, g.maxY, g.maxZ)) {
                    batchesCulled++;
                    continue;
                }
                if (g.object && terrain.occluded(r.ex, r.ey, r.ez, (g.minX + g.maxX) * 0.5f, g.maxY, (g.minZ + g.maxZ) * 0.5f)) {
                    batchesOccluded++;
                    continue;
                }
                r.drawGeo(g);
                batchesDrawn++;
            }
            drawSprites(c, r, terrain);
        }
        for (com.harlequinxd.drivehard.core.city.Railway.Train t : city.railway.trains) {
            if (Math.abs(t.x - r.ex) - t.halfLen() > r.far + 20f) continue;
            r.drawGeoOffset(t.geo, t.x, t.y(), t.z);
        }
        drawTraffic(r);
        drawPedestrians(r);

        Player p = player;
        float gy = city.groundHeight(p.x, p.z, Player.RADIUS, p.y + 0.01f, 0.01f);
        float air = Math.min(1f, (p.y - gy) / 3f);
        boolean onTerrain = p.x < city.cityX0 || p.x > city.cityX1 || p.z < city.cityZ0 || p.z > city.cityZ1;
        // outside the streets the ground is depth-sorted terrain, so the shadow sorts with it
        if (onTerrain) r.groundQuad(lib.shadow, p.x, gy + 0.04f, p.z, 0.38f * (1f - air * 0.4f), SoftRenderer.MODE_SHADOW, SoftRenderer.L_OBJECT, -1.5f);
        else r.groundQuad(lib.shadow, p.x, gy + 0.02f, p.z, 0.38f * (1f - air * 0.4f), SoftRenderer.MODE_SHADOW, SoftRenderer.L_SHADOW);
        SpriteSheet s = sheet;
        sprite(r, playerTex, s, s.directionFor(p.facing, cam.yaw), Animations.firstFrame(p.anim) + p.animFrame, p.x, p.y, p.z, -0.8f);
        r.flush();
    }

    private void drawSprites(Chunk c, SoftRenderer r, Terrain terrain) {
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
            if (d2 > 900f && terrain.occluded(r.ex, r.ey, r.ez, x, y + top, z)) continue;
            float viewYaw = (float) Math.atan2(r.ex - x, r.ez - z);
            sprite(r, tex, s, s.directionFor(yaw, viewYaw), frame, x, y, z, -0.3f);
            spritesDrawn++;
        }
    }

    private void drawTraffic(SoftRenderer r) {
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

    private void drawPedestrians(SoftRenderer r) {
        float lim = pedDist * pedDist;
        for (Pedestrians.Ped p : pedestrians.peds) {
            float dx = p.x - r.ex, dz = p.z - r.ez;
            if (dx * dx + dz * dz > lim) continue;
            SpriteCache.Entry e = pedEntries[p.style];
            if (e == null) e = ped(p.style);
            if (!e.ready()) continue;
            if (!r.boxVisible(p.x - 0.5f, p.y, p.z - 0.5f, p.x + 0.5f, p.y + 2f, p.z + 0.5f)) continue;
            SpriteSheet s = e.sheet;
            int frame = p.state == Pedestrians.WAIT ? ((int) (p.idleTime * 1.6f)) % 4 : 4 + ((int) (p.phase * 8)) % 8;
            r.groundQuad(lib.shadow, p.x, p.y + 0.02f, p.z, 0.32f, SoftRenderer.MODE_SHADOW, SoftRenderer.L_SHADOW);
            float viewYaw = (float) Math.atan2(r.ex - p.x, r.ez - p.z);
            sprite(r, e.tex, s, s.directionFor(p.yaw, viewYaw), frame, p.x, p.y, p.z, -0.7f);
            pedsDrawn++;
        }
    }

    private static void sprite(SoftRenderer r, int tex, SpriteSheet s, int dir, int frame, float x, float y, float z, float bias) {
        int u = s.frameX(dir, frame), v = s.frameY(dir, frame);
        r.billboard(tex, x, y, z, s.left(), s.right(), s.bottom(), s.top(), u, v, u + s.frameW, v + s.frameH, bias);
    }

    private static float dist2(float x0, float z0, float x1, float z1, float px, float pz) {
        float cx = Math.max(x0, Math.min(px, x1)), cz = Math.max(z0, Math.min(pz, z1));
        return (cx - px) * (cx - px) + (cz - pz) * (cz - pz);
    }
}
