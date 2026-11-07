import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.game.Player;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;

/** Close-ups of the on-foot locomotion: turn lean, skid, a run into a wall, bracing, landing. */
public final class LocoShots {
    static Game g;
    static final int W = 480, H = 320, C = 150;
    static BufferedImage out = new BufferedImage(C * 2 * 4, C * 2 * 3 + 3 * 14, BufferedImage.TYPE_INT_RGB);
    static int shot;
    static float sx0, sz0;

    public static void main(String[] a) throws Exception {
        g = new Game(Game.DEFAULT_SEED, null, true);
        g.setResolution(W, H);
        g.env.hours = 13;
        Player p = g.player;
        // an open street in the city, and a building wall near it
        float[] spot = new float[3];
        java.lang.reflect.Method ss = Game.class.getDeclaredMethod("streetSpot", float.class, float.class, boolean.class, float[].class);
        ss.setAccessible(true);
        ss.invoke(g, g.city.spawnX - 240f, g.city.spawnZ - 60f, true, spot);
        sx0 = spot[0]; sz0 = spot[1];
        g.city.chunks.loadAllNow(sx0, sz0, null);
        Box wall = null;
        for (float ox = -40; ox <= 40; ox += 10) for (float oz = -40; oz <= 40; oz += 10)
            for (Box b : g.city.near(sx0 + ox, sz0 + oz))
                if (b.maxY - b.minY > 5f && (wall == null || Math.hypot((b.minX + b.maxX) / 2 - sx0, (b.minZ + b.maxZ) / 2 - sz0) < Math.hypot((wall.minX + wall.maxX) / 2 - sx0, (wall.minZ + wall.maxZ) / 2 - sz0))) wall = b;
        System.out.println("street " + sx0 + "," + sz0 + " wall " + (wall == null ? "none" : wall.minX + "," + wall.minZ));

        // 1. running, then a hard turn
        reset(0f);
        run(1.6f, 0f, 1f);
        run(0.25f, 1f, 0.3f); take("RUN TURN LEAN", 0f);
        run(0.25f, 1f, 0.3f); take("RUN TURN LEAN 2", 0f);
        // 2. running, then reversing: the skid
        reset(0f);
        run(1.6f, 0f, 1f);
        run(0.12f, 0f, -1f); take("SKID", 1.57f);
        run(0.15f, 0f, -1f); take("SKID 2", 1.57f);
        // 3. into the wall at a run
        if (wall != null) {
            // approach the face looking at the street
            float cx = (wall.minX + wall.maxX) / 2, cz = (wall.minZ + wall.maxZ) / 2;
            float wx, wz, f;
            float[] d = {Math.abs(sx0 - wall.minX), Math.abs(sx0 - wall.maxX), Math.abs(sz0 - wall.minZ), Math.abs(sz0 - wall.maxZ)};
            int k = 0; for (int i = 1; i < 4; i++) if (d[i] < d[k]) k = i;
            if (k == 0) { wx = wall.minX - 7f; wz = cz; f = (float) Math.PI * 0.5f; }
            else if (k == 1) { wx = wall.maxX + 7f; wz = cz; f = -(float) Math.PI * 0.5f; }
            else if (k == 2) { wx = cx; wz = wall.minZ - 7f; f = 0f; }
            else { wx = cx; wz = wall.maxZ + 7f; f = (float) Math.PI; }
            reset(f);
            p.x = wx; p.z = wz; p.y = g.city.surface(wx, wz); p.facing = f;
            g.cam.yaw = f + (float) Math.PI;
            for (int i = 0; i < 5; i++) step(0f, 0f);
            for (int i = 0; i < 90 && p.bumpImpact == 0f; i++) step(0f, 1f);
            System.out.println("bump impact " + p.bumpImpact);
            step(0f, 0f); take("WALL HIT", 2.4f);
            run(0.12f, 0f, 0f); take("STAGGER", 2.4f);
            run(0.6f, 0f, 0f);
            run(1.0f, 0f, 0.5f); take("BRACE ON WALL", 2.2f);
        }
        // 4. a drop: knees take it
        reset(0f);
        p.y += 5f; p.onGround = false; p.vy = 0f;
        for (int i = 0; i < 120 && !p.onGround; i++) step(0f, 0f);
        step(0f, 0f); take("HARD LANDING", 0.6f);
        reset(0f);
        run(1.5f, 0f, 1f);
        step(0f, 1f, true);
        for (int i = 0; i < 6; i++) step(0f, 1f); take("RUNNING JUMP", 1.57f);
        for (int i = 0; i < 60 && !g.player.onGround; i++) step(0f, 1f);
        step(0f, 1f); take("RUNNING LANDING", 1.57f);
        ImageIO.write(out, "png", new File(a[0]));
        System.exit(0);
    }

    static void reset(float facing) {
        Player p = g.player;
        p.x = sx0; p.z = sz0; p.y = g.city.surface(p.x, p.z);
        p.vx = p.vz = p.vy = 0f; p.onGround = true; p.facing = facing;
        g.cam.yaw = facing + (float) Math.PI;
        for (int i = 0; i < 40; i++) step(0f, 0f);
    }

    static void run(float t, float sx, float sy) { for (int i = 0; i < Math.round(t * 30); i++) step(sx, sy); }

    static void step(float sx, float sy) { step(sx, sy, false); }

    static void step(float sx, float sy, boolean jump) { g.update(1 / 30f, sx, sy, 0f, 0f, jump); }

    static void take(String label, float view) {
        com.harlequinxd.drivehard.core.game.OrbitCamera c = g.cam;
        float y0c = c.yaw, p0 = c.pitch, d0 = c.distance, h0 = c.targetHeight;
        c.yaw = g.player.facing + view; c.pitch = 0.18f; c.distance = 3.6f; c.targetHeight = 0.9f;
        c.update(g.player.x, g.player.y, g.player.z, 10f);
        g.render();
        c.yaw = y0c; c.pitch = p0; c.distance = d0; c.targetHeight = h0;
        Player p = g.player;
        float[] q = new float[3];
        g.renderer.project(p.x, p.y + 0.9f, p.z, q);
        BufferedImage f = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        f.setRGB(0, 0, W, H, g.renderer.fb, 0, W);
        int x0 = Math.max(0, Math.min(W - C, Math.round(q[0]) - C / 2)), y0 = Math.max(0, Math.min(H - C, Math.round(q[1]) - C / 2));
        Graphics2D gr = out.createGraphics();
        int ox = (shot % 4) * C * 2, oy = (shot / 4) * (C * 2 + 14);
        gr.drawImage(f.getSubimage(x0, y0, C, C), ox, oy + 14, C * 2, C * 2, null);
        gr.setColor(Color.WHITE);
        gr.drawString(label + String.format("  v=%.1f", Math.hypot(p.vx, p.vz)), ox + 4, oy + 11);
        gr.dispose();
        shot++;
    }
}
