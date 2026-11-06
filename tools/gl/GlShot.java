import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.gl.GlRenderer;
import com.harlequinxd.drivehard.core.map.MapData;
import com.harlequinxd.drivehard.core.render.Renderer;
import com.harlequinxd.drivehard.core.render.SoftRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Renders the same scenes with the CPU renderer and the GPU renderer (headless OpenGL ES through
 * {@link FfmGl}) and writes them side by side, CPU left, GPU right, with timings.
 *
 *   javac --release 21 --enable-preview -d out ...core sources... tools/gl/*.java
 *   java --enable-preview -cp out GlShot outdir
 */
public final class GlShot {
    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : "build/glshot");
        out.mkdirs();
        int W = 432, H = 240;
        Game g = new Game(Game.DEFAULT_SEED, null, true);
        SoftRenderer soft = new SoftRenderer(W, H);
        soft.setThreads(4);
        GlRenderer gpu = new GlRenderer(new FfmGl(), W, H);
        gpu.psxSort = !Boolean.getBoolean("zbuf");      // -Dzbuf=true: the old depth-buffered objects
        soft.texMap = gpu.texMap = Integer.getInteger("texmap", Renderer.TEX_GBA);  // 0 perspective, 1 GBA, 2 PS1
        for (Renderer r : new Renderer[]{soft, gpu}) {
            r.textures = g.textures;
            r.ground = g.city.groundSource();
            r.env = g.env;
        }
        g.renderer = soft;
        for (int i = 0; i < 30; i++) g.update(1 / 30f, 0, 0.6f, 0, 0, false);
        g.cache.bakeAllNow();
        String[] names = {"downtown", "town", "coast", "village", "night", "sea0", "sea1", "sea2", "sea3"};
        int scene = 0;
        for (String name : names) {
            if (scene == 1 || scene == 2 || scene == 3) {
                MapData.Label pick = null;
                int seen = 0;
                for (MapData.Label l : g.map.labels) if (l.rank <= 2 && seen++ == scene * 3) { pick = l; break; }
                if (pick != null) g.fastTravel(pick, (s, f) -> { });
            }
            if (name.equals("coast")) coast(g);
            if (name.equals("night")) g.env.hours = 22.5;
            if (name.startsWith("sea")) {
                g.env.hours = 10.0;
                if (name.equals("sea0")) coast(g);
                g.cam.yaw = (name.charAt(3) - '0') * (float) (Math.PI / 2);
            }
            for (int i = 0; i < 20; i++) g.update(1 / 30f, 0, 0, name.startsWith("sea") ? 0f : 0.03f, 0, false);
            g.cache.bakeAllNow();
            g.update(1 / 30f, 0, 0, 0, 0, false);
            int[] a = new int[W * H], b = new int[W * H];
            g.renderer = soft;
            long t0 = System.nanoTime();
            g.render();
            long t1 = System.nanoTime();
            System.arraycopy(soft.fb, 0, a, 0, a.length);
            g.renderer = gpu;
            for (int i = 0; i < 8; i++) g.render();          // stream everything in
            long t2 = System.nanoTime();
            g.render();
            gpu.readFrame(b);
            long t3 = System.nanoTime();
            BufferedImage im = new BufferedImage(W * 2 + 4, H, BufferedImage.TYPE_INT_RGB);
            im.setRGB(0, 0, W, H, a, 0, W);
            im.setRGB(W + 4, 0, W, H, b, 0, W);
            ImageIO.write(im, "png", new File(out, scene + "-" + name + ".png"));
            System.out.printf("%-9s cpu %5.1f ms  gpu(llvmpipe) %5.1f ms  %s  diff %.1f%%%n", name, (t1 - t0) / 1e6, (t3 - t2) / 1e6,
                    gpu.memoryReport(), diff(a, b) * 100);
            scene++;
        }
        System.exit(0);
    }

    /** Puts the player on the coast, looking out to sea. */
    static void coast(Game g) {
        var t = g.city.terrain;
        float best = Float.MAX_VALUE, bx = g.player.x, bz = g.player.z;
        for (int k = 0; k < 4000; k++) {
            float x = g.city.spawnX + (float) Math.cos(k * 0.37) * k * 6f, z = g.city.spawnZ + (float) Math.sin(k * 0.37) * k * 6f;
            float h = t.height(x, z);
            if (h > 0.2f && h < 1.2f && (t.height(x + 25f, z) < -0.8f || t.height(x - 25f, z) < -0.8f || t.height(x, z + 25f) < -0.8f || t.height(x, z - 25f) < -0.8f)) { bx = x; bz = z; break; }
        }
        g.player.x = bx; g.player.z = bz;
        g.city.chunks.loadAllNow(bx, bz);
        g.player.y = g.city.surface(bx, bz);
        g.player.facing = (float) (Math.PI / 2);
        g.cam.yaw = (float) (Math.PI / 2);
    }

    static double diff(int[] a, int[] b) {
        long d = 0;
        for (int i = 0; i < a.length; i++) {
            int x = a[i], y = b[i];
            d += Math.abs(((x >> 16) & 255) - ((y >> 16) & 255)) + Math.abs(((x >> 8) & 255) - ((y >> 8) & 255)) + Math.abs((x & 255) - (y & 255));
        }
        return d / (a.length * 3.0 * 255.0);
    }
}
