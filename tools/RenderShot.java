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
        // desktop: bake a good set of car/pedestrian styles and all plants up front
        for (int i = 0; i < 24; i++) { g.requestCar(); g.requestPed(); }
        long tb = System.nanoTime();
        g.cache.bakeAllNow();
        System.out.printf("baked %d sprite sheets in %.0f ms%n", g.cache.bakedTotal, (System.nanoTime() - tb) / 1e6);
        g.traffic.spawnMin = 12f;
        g.pedestrians.spawnMin = 6f;

        // spawn on the promenade, walk a little
        for (int i = 0; i < 30; i++) g.update(1 / 60f, 0, 0.5f, 0, 0, false);
        shot(g, "shot_spawn.png");

        float ix = (c.rx0[4] + c.rx1[4]) * 0.5f, iz = (c.rz0[5] + c.rz1[5]) * 0.5f;
        int jr = c.jRail, ic = c.iCentre;
        float stX = c.railway.stationX[1];
        tour(g, "shot_station.png", stX + 6f, c.rz0[jr + 1] - 1.5f, 0.25f, 10, 9f);
        tour(g, "shot_viaduct.png", (c.rx1[2] + c.rx0[3]) * 0.5f, c.rz0[jr + 1] + 3f, -0.6f, 8, 8f);
        // let the trains run for a while, then follow one from street level
        for (int k = 0; k < 60 * 40; k++) c.railway.update(1 / 60f);
        com.harlequinxd.drivehard.core.city.Railway.Train tr = c.railway.trains.get(0);
        tour(g, "shot_train.png", tr.x + 30f, c.rz0[jr + 1] - 2f, -0.9f, 6, 7f);
        tour(g, "shot_platform.png", stX, c.railway.zc + 16f, 0.2f, 32, 26f);
        tour(g, "shot_signals.png", c.rx1[3] + 3f, c.rz1[3] + 9f, 0.5f, 14, 9f);
        tour(g, "shot_traffic.png", c.rx1[3] + 2f, c.rz1[2] + 2f, (float) Math.toRadians(-140), 24, 14f);
        tour(g, "shot_entertainment.png", (c.rx1[ic + 2] + c.rx0[ic + 3]) * 0.5f, c.rz1[jr + 2] + 1.5f, 3.14f, 12, 7f);
        tour(g, "shot_downtown.png", (c.rx0[ic] + c.rx1[ic]) * 0.5f, (c.rz1[jr - 1] + c.rz0[jr]) * 0.5f, 0.6f, 22, 6.5f);
        tour(g, "shot_shops.png", (c.rx1[2] + c.rx0[3]) * 0.5f, c.rz1[3] + 1.5f, 2.6f, 10, 6f);
        tour(g, "shot_suburb.png", (c.rx1[0] + c.rx0[1]) * 0.5f, c.rz1[2] + 1.5f, 2.6f, 14, 7f);
        tour(g, "shot_beach.png", c.cityX1 + 40f, (c.rz1[3] + c.rz0[4]) * 0.5f, -1.2f, 18, 8f);
        tour(g, "shot_hills.png", c.cityX0 - 150f, (c.cityZ0 + c.cityZ1) * 0.5f, -1.57f, 12, 7f);
        tour(g, "shot_hill_view.png", c.cityX0 - 110f, c.cityZ0 - 60f, (float) Math.toRadians(-135), 25, 12f);
        tour(g, "shot_aerial.png", ix, iz, 0.8f, 40, 45f);
        System.out.printf("chunks loaded %d, generated %d, unloaded %d%n", c.chunks.loadedCount(),
                c.chunks.generatedTotal, c.chunks.unloadedTotal);

        // timing at downtown
        g.player.x = (c.rx0[c.iCentre] + c.rx1[c.iCentre]) * 0.5f; g.player.z = (c.rz1[c.jRail - 1] + c.rz0[c.jRail]) * 0.5f;
        g.cam.pitch = (float) Math.toRadians(20); g.cam.distance = 6f;
        c.chunks.loadAllNow(g.player.x, g.player.z);
        int frames = 120;
        long t1 = System.nanoTime();
        for (int i = 0; i < frames; i++) { g.update(1 / 60f, 0.3f, 0.6f, 0.01f, 0, false); g.render(); }
        double ms = (System.nanoTime() - t1) / 1e6 / frames;
        SoftRenderer r = g.renderer;
        System.out.printf("avg frame %.2f ms, polys %d, tris %d, batches %d (culled %d, occluded %d), sprites %d, cars %d, peds %d%n",
                ms, r.statPolys, r.statPrims, g.batchesDrawn, g.batchesCulled, g.batchesOccluded, g.spritesDrawn, g.carsDrawn, g.pedsDrawn);
        System.gc(); System.gc();
        Runtime rt = Runtime.getRuntime();
        System.out.printf("heap used %.0f MB%n", (rt.totalMemory() - rt.freeMemory()) / 1048576.0);
        g.dispose();
        System.exit(0);
    }

    static void tour(Game g, String name, float x, float z, float yaw, float pitchDeg, float dist) throws Exception {
        g.player.x = x; g.player.z = z;
        g.city.chunks.loadAllNow(x, z);
        g.player.y = g.city.surface(x, z);
        g.player.vy = 0;
        g.cam.yaw = yaw; g.cam.pitch = (float) Math.toRadians(pitchDeg); g.cam.distance = dist;
        for (int k = 0; k < 240; k++) {
            g.update(1 / 60f, 0, 0, 0, 0, false);
            if (k % 60 == 0) g.cache.bakeAllNow();
        }
        shot(g, name);
    }

    static void shot(Game g, String name) throws Exception {
        g.render();
        SoftRenderer r = g.renderer;
        System.out.printf("%-20s polys %5d tris %5d batches %3d culled %4d occl %3d sprites %3d cars %2d peds %2d%n", name,
                r.statPolys, r.statPrims, g.batchesDrawn, g.batchesCulled, g.batchesOccluded, g.spritesDrawn, g.carsDrawn, g.pedsDrawn);
        int s = 3;
        BufferedImage img = new BufferedImage(r.W * s, r.H * s, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < r.H * s; y++)
            for (int x = 0; x < r.W * s; x++) img.setRGB(x, y, r.fb[(y / s) * r.W + x / s]);
        ImageIO.write(img, "png", new File(out, name));
    }
}
