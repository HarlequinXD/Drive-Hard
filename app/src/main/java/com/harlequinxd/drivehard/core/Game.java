package com.harlequinxd.drivehard.core;

import com.harlequinxd.drivehard.core.city.Chunk;
import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.city.Terrain;
import com.harlequinxd.drivehard.core.city.TrafficLights;
import com.harlequinxd.drivehard.core.game.OrbitCamera;
import com.harlequinxd.drivehard.core.game.Player;
import com.harlequinxd.drivehard.core.model.Animations;
import com.harlequinxd.drivehard.core.model.CharacterModel;
import com.harlequinxd.drivehard.core.model.CharacterSpec;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.render.SoftRenderer;
import com.harlequinxd.drivehard.core.render.Texture;
import com.harlequinxd.drivehard.core.sprite.PropSprites;
import com.harlequinxd.drivehard.core.sprite.SpriteBaker;
import com.harlequinxd.drivehard.core.sprite.SpriteSheet;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

/** Platform-independent game: streamed island, player, camera, props and the software renderer. */
public final class Game {
    public final TextureLibrary lib;
    public final City city;
    public final Player player;
    public final OrbitCamera cam = new OrbitCamera();
    public final SpriteSheet sheet, palms, lamps, signals, umbrellas;
    private final int playerTex, palmTex, lampTex, signalTex, umbrellaTex;
    public SoftRenderer renderer;
    private final float[] move = new float[2];
    public float time;

    /** PS1-style draw distances (metres). */
    public float spriteDist = 105f, smallSpriteDist = 60f;

    // stats
    public int batchesDrawn, batchesCulled, batchesOccluded, spritesDrawn;

    public interface Progress { void step(String what); }

    public Game(long seed, Progress progress) {
        if (progress != null) progress.step("Painting textures");
        lib = new TextureLibrary(seed);
        if (progress != null) progress.step("Baking sprites");
        sheet = new SpriteBaker().bake(new CharacterModel(CharacterSpec.player()));
        palms = PropSprites.palms();
        lamps = PropSprites.lamp();
        signals = PropSprites.signals();
        umbrellas = PropSprites.umbrellas();
        playerTex = lib.add(Texture.padded(sheet.pixels, sheet.width, sheet.height));
        palmTex = lib.add(Texture.padded(palms.pixels, palms.width, palms.height));
        lampTex = lib.add(Texture.padded(lamps.pixels, lamps.width, lamps.height));
        signalTex = lib.add(Texture.padded(signals.pixels, signals.width, signals.height));
        umbrellaTex = lib.add(Texture.padded(umbrellas.pixels, umbrellas.width, umbrellas.height));
        if (progress != null) progress.step("Raising the island");
        city = new City(lib, seed);
        player = new Player(city.spawnX, city.spawnZ);
        city.chunks.ensureAround(player.x, player.z);
        player.y = city.surface(player.x, player.z);
        player.facing = (float) Math.PI;
        cam.yaw = 0f;
        cam.distance = 6f;
        if (progress != null) progress.step("Streaming the city");
        city.chunks.loadRadius = 150f;          // first load: just what is around
        city.chunks.loadAllNow(player.x, player.z);
        city.chunks.loadRadius = 200f;
        city.chunks.start();
    }

    public void dispose() {
        city.chunks.stop();
        if (renderer != null) renderer.dispose();
    }

    /** (Re)creates the renderer for a given internal resolution. */
    public void setResolution(int w, int h) {
        if (renderer != null) renderer.dispose();
        renderer = new SoftRenderer(w, h);
        renderer.setThreads(Math.min(4, Math.max(1, Runtime.getRuntime().availableProcessors() - 1)));
        renderer.textures = lib.array();
        renderer.setFogColor(0xBCD8EA);
        renderer.setZenithColor(0x3E86D0);
    }

    public void update(float dt, float stickX, float stickY, float dYaw, float dPitch, boolean jump) {
        time += dt;
        city.chunks.update(player.x, player.z);
        city.chunks.ensureAround(player.x, player.z);   // never walk on missing collision
        cam.orbit(dYaw, dPitch);
        cam.toWorld(stickX, stickY, move);
        player.update(dt, move[0], move[1], jump, city);
        cam.update(player.x, player.y, player.z, dt);
        cameraCollision();
    }

    private float camPull = 1f;

    /** Pulls the camera in front of walls between it and the player and keeps it above the terrain. */
    private void cameraCollision() {
        float dx = cam.eyeX - cam.tx, dy = cam.eyeY - cam.ty, dz = cam.eyeZ - cam.tz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float hit = 1f;
        for (float t = 0.4f; t <= len; t += 0.35f) {
            float px = cam.tx + dx / len * t, py = cam.ty + dy / len * t, pz = cam.tz + dz / len * t;
            if (insideSolid(px, py, pz)) { hit = Math.max(0.12f, (t - 0.45f) / len); break; }
        }
        // pull in instantly, ease back out
        camPull = hit < camPull ? hit : camPull + (hit - camPull) * 0.08f;
        cam.eyeX = cam.tx + dx * camPull;
        cam.eyeY = cam.ty + dy * camPull;
        cam.eyeZ = cam.tz + dz * camPull;
        float gh = city.terrain.height(cam.eyeX, cam.eyeZ) + 0.5f;
        if (cam.eyeY < gh) cam.eyeY = gh;
    }

    private boolean insideSolid(float x, float y, float z) {
        for (com.harlequinxd.drivehard.core.game.Box b : city.near(x, z))
            if (x > b.minX - 0.2f && x < b.maxX + 0.2f && z > b.minZ - 0.2f && z < b.maxZ + 0.2f && y > b.minY && y < b.maxY + 0.2f
                    && (b.maxX - b.minX) > 1f) return true;
        return false;
    }

    public void render() {
        SoftRenderer r = renderer;
        r.time = time;
        r.lookAt(cam.eyeX, cam.eyeY, cam.eyeZ, cam.tx, cam.ty, cam.tz);
        r.begin();
        r.drawSky();
        Terrain terrain = city.terrain;

        batchesDrawn = batchesCulled = batchesOccluded = spritesDrawn = 0;
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
                // buildings fully hidden behind hills are skipped (cheap heightfield ray test)
                if (g.object && terrain.occluded(r.ex, r.ey, r.ez,
                        (g.minX + g.maxX) * 0.5f, g.maxY, (g.minZ + g.maxZ) * 0.5f)) {
                    batchesOccluded++;
                    continue;
                }
                r.drawGeo(g);
                batchesDrawn++;
            }
            drawSprites(c, r, terrain);
        }

        // blob shadow + player
        Player p = player;
        float gy = city.groundHeight(p.x, p.z, Player.RADIUS, p.y + 0.01f, 0.01f);
        float air = Math.min(1f, (p.y - gy) / 3f);
        r.groundQuad(lib.shadow, p.x, gy + 0.02f, p.z, 0.38f * (1f - air * 0.4f), SoftRenderer.MODE_SHADOW, SoftRenderer.L_SHADOW);
        SpriteSheet s = sheet;
        int dir = s.directionFor(p.facing, cam.yaw);
        int frame = Animations.firstFrame(p.anim) + p.animFrame;
        sprite(r, playerTex, s, dir, frame, p.x, p.y, p.z, -0.8f);
        r.flush();
    }

    private void drawSprites(Chunk c, SoftRenderer r, Terrain terrain) {
        float[] sp = c.sprites;
        float sd2 = spriteDist * spriteDist, small2 = smallSpriteDist * smallSpriteDist;
        for (int i = 0; i < c.spriteCount; i++) {
            int o = i * Chunk.SPRITE_STRIDE;
            int kind = (int) sp[o];
            float x = sp[o + 2], y = sp[o + 3], z = sp[o + 4], yaw = sp[o + 5];
            float dx = x - r.ex, dz = z - r.ez, d2 = dx * dx + dz * dz;
            boolean small = kind == Chunk.SPRITE_UMBRELLA;
            if (d2 > (small ? small2 : sd2)) continue;
            SpriteSheet s;
            int tex, frame;
            switch (kind) {
                case Chunk.SPRITE_PALM: s = palms; tex = palmTex; frame = (int) sp[o + 1]; break;
                case Chunk.SPRITE_LAMP: s = lamps; tex = lampTex; frame = 0; break;
                case Chunk.SPRITE_UMBRELLA: s = umbrellas; tex = umbrellaTex; frame = (int) sp[o + 1]; break;
                default: {
                    int ref = (int) sp[o + 1];
                    int st = city.traffic.state(ref >> 1, (ref & 1) == 1, time);
                    s = signals; tex = signalTex;
                    frame = st == TrafficLights.GREEN ? 0 : (st == TrafficLights.YELLOW ? 1 : 2);
                    break;
                }
            }
            float top = s.top();
            if (!r.boxVisible(x + s.left(), y, z + s.left(), x + s.right(), y + top, z + s.right())) continue;
            if (d2 > 900f && terrain.occluded(r.ex, r.ey, r.ez, x, y + top, z)) continue;
            float viewYaw = (float) Math.atan2(r.ex - x, r.ez - z);
            sprite(r, tex, s, s.directionFor(yaw, viewYaw), frame, x, y, z, -0.3f);
            spritesDrawn++;
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
