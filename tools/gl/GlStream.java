import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.gl.GlRenderer;

/** Moves across the island rendering with the GPU: video memory must stay bounded, no GL errors. */
public final class GlStream {
    public static void main(String[] args) {
        Game g = new Game(Game.DEFAULT_SEED, null, true);
        FfmGl gl = new FfmGl();
        GlRenderer gpu = new GlRenderer(gl, 432, 240);
        g.useRenderer(gpu);
        float x0 = g.player.x, z0 = g.player.z;
        int errors = 0;
        long worst = 0;
        double peak = 0;
        for (int i = 0; i <= 1500; i++) {
            // 4 m per frame (about 430 km/h at 30 fps): far faster than any car, the hardest case
            g.player.x = x0 + i * 4f;
            g.player.z = z0 + (float) Math.sin(i * 0.01) * 300f;
            g.player.y = g.city.surface(g.player.x, g.player.z);
            g.update(1 / 30f, 0, 0, 0.01f, 0, false);
            long t = System.nanoTime();
            g.render();
            worst = Math.max(worst, System.nanoTime() - t);
            int e = gl.getError();
            if (e != 0) { errors++; if (errors < 5) System.out.println("GL error 0x" + Integer.toHexString(e) + " at frame " + i); }
            peak = Math.max(peak, gpu.gpuBytes / 1048576.0);
            if (i % 300 == 0) System.out.printf("frame %4d  %s  chunks %d%n", i, gpu.memoryReport(), g.city.chunks.loadedCount());
        }
        System.out.printf("GL errors %d, peak video memory %.1f MB, slowest frame %.0f ms (llvmpipe)%n", errors, peak, worst / 1e6);
        System.exit(errors == 0 ? 0 : 1);
    }
}
