import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.render.SoftRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Renders game frames on the desktop with the exact phone renderer and reports timings. */
public class RenderShot {
    static File out;

    public static void main(String[] a) throws Exception {
        out = new File(a.length > 0 ? a[0] : "build/preview");
        out.mkdirs();
        long t0 = System.nanoTime();
        Game g = new Game(1234, s -> System.out.println("  " + s));
        System.out.printf("load %.0f ms, %d textures, %d chunks loaded%n",
                (System.nanoTime() - t0) / 1e6, g.lib.list.size(), g.city.chunks.loadedCount());
        g.setResolution(520, 240);
        City c = g.city;

        // spawn on the promenade, walk a little
        for (int i = 0; i < 30; i++) g.update(1 / 60f, 0, 0.5f, 0, 0, false);
        shot(g, "shot_spawn.png");

        float ix = (c.rx0[4] + c.rx1[4]) * 0.5f, iz = (c.rz0[5] + c.rz1[5]) * 0.5f;
        tour(g, "shot_signals.png", c.rx1[4] + 3f, c.rz1[5] + 9f, 0.5f, 14, 9f);
        tour(g, "shot_downtown.png", (c.rx0[3] + c.rx1[3]) * 0.5f, (c.rz1[4] + c.rz0[5]) * 0.5f, 0.6f, 22, 6.5f);
        tour(g, "shot_beach.png", c.cityX1 + 40f, (c.rz1[3] + c.rz0[4]) * 0.5f, -1.2f, 18, 8f);
        tour(g, "shot_hills.png", c.cityX0 - 150f, (c.cityZ0 + c.cityZ1) * 0.5f, -1.57f, 12, 7f);
        tour(g, "shot_hill_view.png", c.cityX0 - 110f, c.cityZ0 - 60f, (float) Math.toRadians(-135), 25, 12f);
        tour(g, "shot_aerial.png", ix, iz, 0.8f, 40, 45f);
        System.out.printf("chunks loaded %d, generated %d, unloaded %d%n", c.chunks.loadedCount(),
                c.chunks.generatedTotal, c.chunks.unloadedTotal);

        // timing at downtown
        g.player.x = (c.rx0[3] + c.rx1[3]) * 0.5f; g.player.z = (c.rz1[4] + c.rz0[5]) * 0.5f;
        g.cam.pitch = (float) Math.toRadians(20); g.cam.distance = 6f;
        c.chunks.loadAllNow(g.player.x, g.player.z);
        int frames = 120;
        long t1 = System.nanoTime();
        for (int i = 0; i < frames; i++) { g.update(1 / 60f, 0.3f, 0.6f, 0.01f, 0, false); g.render(); }
        double ms = (System.nanoTime() - t1) / 1e6 / frames;
        SoftRenderer r = g.renderer;
        System.out.printf("avg frame %.2f ms, polys %d, tris %d, batches %d (culled %d, occluded %d), sprites %d%n",
                ms, r.statPolys, r.statPrims, g.batchesDrawn, g.batchesCulled, g.batchesOccluded, g.spritesDrawn);
        g.dispose();
        System.exit(0);
    }

    static void tour(Game g, String name, float x, float z, float yaw, float pitchDeg, float dist) throws Exception {
        g.player.x = x; g.player.z = z;
        g.city.chunks.loadAllNow(x, z);
        g.player.y = g.city.surface(x, z);
        g.player.vy = 0;
        g.cam.yaw = yaw; g.cam.pitch = (float) Math.toRadians(pitchDeg); g.cam.distance = dist;
        for (int k = 0; k < 90; k++) g.update(1 / 60f, 0, 0, 0, 0, false);
        shot(g, name);
    }

    static void shot(Game g, String name) throws Exception {
        g.render();
        SoftRenderer r = g.renderer;
        System.out.printf("%-20s polys %5d tris %5d batches %3d culled %4d occl %3d sprites %3d  player %.0f,%.1f,%.0f%n", name,
                r.statPolys, r.statPrims, g.batchesDrawn, g.batchesCulled, g.batchesOccluded, g.spritesDrawn,
                g.player.x, g.player.y, g.player.z);
        int s = 3;
        BufferedImage img = new BufferedImage(r.W * s, r.H * s, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < r.H * s; y++)
            for (int x = 0; x < r.W * s; x++) img.setRGB(x, y, r.fb[(y / s) * r.W + x / s]);
        ImageIO.write(img, "png", new File(out, name));
    }
}
