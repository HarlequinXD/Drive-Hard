import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.render.SoftRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Renders game frames on the desktop with the exact phone renderer and reports timings. */
public class RenderShot {
    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "build/preview");
        out.mkdirs();
        long t0 = System.nanoTime();
        Game g = new Game(1234, s -> System.out.println("  " + s));
        System.out.printf("load %.0f ms, %d textures, %d polys, %d buildings, %d boxes%n",
                (System.nanoTime() - t0) / 1e6, g.lib.list.size(), g.city.gb.polyCount, g.city.buildingCount, g.city.boxes.size());
        int W = 520, H = 240;
        g.setResolution(W, H);

        // shot 1: spawn, default camera; walk a bit so animation is mid-stride
        for (int i = 0; i < 30; i++) g.update(1 / 60f, 0, 0.5f, 0, 0, false);
        shot(g, new File(out, "shot_spawn.png"));
        // shot 2: turn camera to look into the city (west)
        for (int i = 0; i < 30; i++) g.update(1 / 60f, 0, 0, 0.05f, 0, false);
        shot(g, new File(out, "shot_city.png"));
        // shot 3: run west into downtown for a while
        g.cam.yaw = (float) Math.PI / 2;  // camera east of player, looking west
        for (int i = 0; i < 60 * 14; i++) g.update(1 / 60f, 0, 1f, 0, 0, false);
        shot(g, new File(out, "shot_run.png"));
        g.cam.pitch = (float) Math.toRadians(45);
        g.cam.distance = 9f;
        for (int i = 0; i < 20; i++) g.update(1 / 60f, 0, 0, 0, 0, false);
        shot(g, new File(out, "shot_high.png"));

        // tour shots: teleport to downtown / avenue / aerial
        var c = g.city;
        float[][] spots = {
                {(c.rx0[3] + c.rx1[3]) * 0.5f, (c.rz1[4] + c.rz0[5]) * 0.5f, 0.6f, 22, 6.5f},   // downtown avenue
                {(c.rx0[2] + c.rx1[2]) * 0.5f + 4, (c.rz1[2] + c.rz0[3]) * 0.5f, 2.2f, 18, 6f},  // side street
                {(c.rx0[4] + c.rx1[4]) * 0.5f, c.rz1[4] + 2, 0.8f, 38, 40f},                    // aerial
                {(c.rx0[8] + c.rx1[8]) * 0.5f, (c.rz1[6] + c.rz0[7]) * 0.5f, -1.2f, 16, 7f},    // deco district
        };
        String[] names = {"shot_downtown.png", "shot_street.png", "shot_aerial.png", "shot_deco.png"};
        for (int i = 0; i < spots.length; i++) {
            g.player.x = spots[i][0]; g.player.z = spots[i][1]; g.player.y = 0;
            g.cam.yaw = spots[i][2]; g.cam.pitch = (float) Math.toRadians(spots[i][3]); g.cam.distance = spots[i][4];
            for (int k = 0; k < 90; k++) g.update(1 / 60f, 0, 0, 0, 0, false);
            shot(g, new File(out, names[i]));
        }

        // timing
        int frames = 120;
        long t1 = System.nanoTime();
        for (int i = 0; i < frames; i++) { g.update(1 / 60f, 0.3f, 1f, 0.01f, 0, false); g.render(); }
        double ms = (System.nanoTime() - t1) / 1e6 / frames;
        SoftRenderer r = g.renderer;
        System.out.printf("avg frame %.2f ms, polys %d, prims %d, chunks %d%n", ms, r.statPolys, r.statPrims, g.chunksDrawn);
    }

    static void shot(Game g, File f) throws Exception {
        g.render();
        SoftRenderer r = g.renderer;
        System.out.printf("%s: polys %d prims %d chunks %d  player %.1f,%.2f,%.1f%n", f.getName(), r.statPolys, r.statPrims,
                g.chunksDrawn, g.player.x, g.player.y, g.player.z);
        int s = 3;
        BufferedImage img = new BufferedImage(r.W * s, r.H * s, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < r.H * s; y++)
            for (int x = 0; x < r.W * s; x++) img.setRGB(x, y, r.fb[(y / s) * r.W + x / s]);
        ImageIO.write(img, "png", f);
    }
}
