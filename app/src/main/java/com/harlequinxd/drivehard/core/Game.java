package com.harlequinxd.drivehard.core;

import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.game.OrbitCamera;
import com.harlequinxd.drivehard.core.game.Player;
import com.harlequinxd.drivehard.core.model.Animations;
import com.harlequinxd.drivehard.core.model.CharacterModel;
import com.harlequinxd.drivehard.core.model.CharacterSpec;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.render.SoftRenderer;
import com.harlequinxd.drivehard.core.render.Texture;
import com.harlequinxd.drivehard.core.sprite.SpriteBaker;
import com.harlequinxd.drivehard.core.sprite.SpriteSheet;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

/** Platform-independent game: world, player, camera and the software renderer. */
public final class Game {
    public final TextureLibrary lib;
    public final City city;
    public final Player player;
    public final OrbitCamera cam = new OrbitCamera();
    public final SpriteSheet sheet;
    public final int playerTex;
    public SoftRenderer renderer;
    private final float[] move = new float[2];
    private float time;
    public int chunksDrawn;

    public interface Progress { void step(String what); }

    public Game(long seed, Progress progress) {
        if (progress != null) progress.step("Painting textures");
        lib = new TextureLibrary(seed);
        if (progress != null) progress.step("Building the city");
        city = new City(lib, seed);
        if (progress != null) progress.step("Baking character sprites");
        sheet = new SpriteBaker().bake(new CharacterModel(CharacterSpec.player()));
        playerTex = lib.add(Texture.padded(sheet.pixels, sheet.width, sheet.height));
        player = new Player(city.spawnX, city.spawnZ);
        player.y = City.CURB;
        player.facing = (float) Math.PI;          // face north up the promenade
        cam.yaw = 0f;
        cam.distance = 6f;
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

    /**
     * @param stickX,stickY  movement stick (-1..1, y up)
     * @param dYaw,dPitch    camera orbit deltas in radians
     */
    public void update(float dt, float stickX, float stickY, float dYaw, float dPitch, boolean jump) {
        time += dt;
        cam.orbit(dYaw, dPitch);
        cam.toWorld(stickX, stickY, move);
        player.update(dt, move[0], move[1], jump, city);
        cam.update(player.x, player.y, player.z, dt);
    }

    public void render() {
        SoftRenderer r = renderer;
        r.time = time;
        r.lookAt(cam.eyeX, cam.eyeY, cam.eyeZ, cam.tx, cam.ty, cam.tz);
        r.begin();
        r.drawSky();

        chunksDrawn = 0;
        float reach = r.far + 48f;
        for (Geo g : city.gb.chunks) {
            if (g.count == 0) continue;
            float cx = Math.max(g.minX, Math.min(r.ex, g.maxX)), cz = Math.max(g.minZ, Math.min(r.ez, g.maxZ));
            float dx = cx - r.ex, dz = cz - r.ez;
            if (dx * dx + dz * dz > reach * reach) continue;
            r.drawGeo(g);
            chunksDrawn++;
        }

        // blob shadow on whatever is under the player
        Player p = player;
        float gy = city.groundHeight(p.x, p.z, Player.RADIUS, p.y + 0.01f, 0.01f);
        float air = Math.min(1f, (p.y - gy) / 3f);
        r.groundQuad(lib.shadow, p.x, gy + 0.02f, p.z, 0.38f * (1f - air * 0.4f), SoftRenderer.MODE_SHADOW, SoftRenderer.L_SHADOW);

        // player sprite
        SpriteSheet s = sheet;
        int dir = s.directionFor(p.facing, cam.yaw);
        int col = Animations.firstFrame(p.anim) + p.animFrame;
        float ppm = s.pixelsPerMeter;
        r.billboard(playerTex, p.x, p.y, p.z,
                -s.anchorX / ppm, (s.frameW - s.anchorX) / ppm, -(s.frameH - s.anchorY) / ppm, s.anchorY / ppm,
                col * s.frameW, dir * s.frameH, (col + 1) * s.frameW, (dir + 1) * s.frameH, -0.8f);
        r.flush();
    }
}
