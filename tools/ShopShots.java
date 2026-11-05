import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.city.Chunk;
import com.harlequinxd.drivehard.core.render.Geo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stands across the street from shopfront polygons near each point and looks at them. args: out.png x,z [x,z ...] */
public final class ShopShots {
    public static void main(String[] a) throws Exception {
        Game g = new Game(Game.DEFAULT_SEED, null, true);
        g.setResolution(432, 240);
        g.env.hours = 15f;
        g.traffic.target = 0;
        g.pedestrians.target = 0;
        Set<Integer> shopTex = new HashSet<>();
        for (int t : g.lib.shops) shopTex.add(t);
        int per = 4;
        BufferedImage out = new BufferedImage(432 * per, 240 * (a.length - 1), BufferedImage.TYPE_INT_RGB);
        for (int k = 1; k < a.length; k++) {
            String[] xz = a[k].split(",");
            float cx = Float.parseFloat(xz[0]), cz = Float.parseFloat(xz[1]);
            g.city.chunks.loadAllNow(cx, cz);
            List<float[]> fronts = new ArrayList<>();
            for (Chunk ch : g.city.chunks.chunks())
                for (Geo geo : ch.batches)
                    for (int i = 0; i < geo.count; i++) {
                        if (!shopTex.contains(geo.tex[i])) continue;
                        float mx = 0, my = 0, mz = 0;
                        for (int v = 0; v < 4; v++) { mx += geo.pos[i * 12 + v * 3]; my += geo.pos[i * 12 + v * 3 + 1]; mz += geo.pos[i * 12 + v * 3 + 2]; }
                        mx /= 4; my /= 4; mz /= 4;
                        if (Math.hypot(mx - cx, mz - cz) > 150) continue;
                        boolean dup = false;
                        for (float[] f : fronts) if (Math.hypot(f[0] - mx, f[2] - mz) < 30) dup = true;
                        if (!dup) fronts.add(new float[]{mx, my, mz, geo.nrm[i * 3], geo.nrm[i * 3 + 2]});
                    }
            for (int v = 0; v < Math.min(per, fronts.size()); v++) {
                float[] f = fronts.get(v);
                float nl = (float) Math.hypot(f[3], f[4]);
                float nx = f[3] / nl, nz = f[4] / nl;
                float px = f[0] + nx * 9f, pz = f[2] + nz * 9f;
                g.player.x = px; g.player.z = pz;
                g.player.y = g.city.surface(px, pz, 200f);
                for (int i = 0; i < 3; i++) g.update(1 / 30f, 0, 0, 0, 0, false);
                g.player.x = px; g.player.z = pz;
                g.cam.yaw = (float) Math.atan2(nx, nz) + 0.35f;
                g.cam.pitch = (float) Math.toRadians(0);
                g.cam.distance = 4f;
                g.cam.update(px, g.player.y + 1.4f, pz, 1f);
                g.render();
                out.setRGB(v * 432, (k - 1) * 240, 432, 240, g.renderer.fb, 0, 432);
            }
        }
        ImageIO.write(out, "png", new File(a[0]));
        System.exit(0);
    }
}
