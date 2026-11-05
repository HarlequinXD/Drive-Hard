import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.road.RoadEdge;
import com.harlequinxd.drivehard.core.city.Terrain;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * The island's road network on its relief, plus how curvy the freeways are: the tightest radius
 * (over a 150 m arc) and how often they turn back on themselves. args: out.png [seed]
 */
public final class RoadMap {
    public static void main(String[] a) throws Exception {
        long seed = a.length > 1 ? Long.parseLong(a[1]) : com.harlequinxd.drivehard.core.Game.DEFAULT_SEED;
        long t0 = System.nanoTime();
        City c = new City(new com.harlequinxd.drivehard.core.tex.TextureLibrary(seed, true), seed);
        System.out.printf("island in %.1f s%n", (System.nanoTime() - t0) / 1e9);
        Terrain t = c.terrain;
        int N = 1600;
        float x0 = t.islandX0, z0 = t.islandZ0, S = Terrain.ISLAND_SIZE, px = S / N;
        BufferedImage im = new BufferedImage(N, N, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                float x = x0 + (i + 0.5f) * px, z = z0 + (j + 0.5f) * px;
                float h = t.natural(x, z), hx = t.natural(x + px, z);
                boolean sea = t.coastOver(x, z) > 0;
                int shade = (int) Math.max(-40, Math.min(40, (hx - h) * 6));
                int r, g, b;
                if (sea) { r = 50; g = 90; b = 130; }
                else { float k = Math.min(1f, Math.max(0f, h / 400f)); r = (int) (90 + 70 * k); g = (int) (140 - 10 * k); b = (int) (80 + 20 * k); }
                im.setRGB(i, j, new Color(clamp(r + shade), clamp(g + shade), clamp(b + shade)).getRGB());
            }
        Graphics2D gr = im.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int fw = 0, tight = 0, hairpins = 0;
        float minR = 1e9f, fwLen = 0;
        for (RoadClass want : new RoadClass[]{RoadClass.COUNTRY, RoadClass.LANE, RoadClass.TOWN, RoadClass.HIGHWAY, RoadClass.RAMP, RoadClass.FREEWAY}) {
            gr.setColor(want == RoadClass.FREEWAY ? new Color(240, 130, 50) : want == RoadClass.HIGHWAY ? new Color(240, 200, 70) : new Color(230, 225, 210));
            gr.setStroke(new BasicStroke(want == RoadClass.FREEWAY ? 3f : want == RoadClass.HIGHWAY ? 2f : 1f));
            for (RoadEdge e : c.roads.edges) {
                if (e.cls != want) continue;
                for (int k = 1; k < e.x.length; k++)
                    gr.drawLine((int) ((e.x[k - 1] - x0) / px), (int) ((e.z[k - 1] - z0) / px), (int) ((e.x[k] - x0) / px), (int) ((e.z[k] - z0) / px));
                if (want != RoadClass.FREEWAY) continue;
                fw++;
                fwLen += e.len;
                // heading change over ~150 m arcs
                float acc = 0;
                int last = 0;
                for (int k = 1; k < e.x.length; k++) {
                    acc += (float) Math.hypot(e.x[k] - e.x[k - 1], e.z[k] - e.z[k - 1]);
                    if (acc < 150f || k < 2) continue;
                    float h0 = (float) Math.atan2(e.x[last + 1] - e.x[last], e.z[last + 1] - e.z[last]);
                    float h1 = (float) Math.atan2(e.x[k] - e.x[k - 1], e.z[k] - e.z[k - 1]);
                    float d = Math.abs(wrap(h1 - h0));
                    if (d > 1e-3f) {
                        float rad = acc / d;
                        minR = Math.min(minR, rad);
                        if (rad < 400f) { tight++; gr.setColor(Color.RED); gr.fillOval((int) ((e.x[k] - x0) / px) - 4, (int) ((e.z[k] - z0) / px) - 4, 8, 8); gr.setColor(new Color(240, 130, 50)); }
                    }
                    last = k; acc = 0;
                }
                // turning back: heading change > 100 deg within 800 m
                for (int k = 1; k < e.x.length; k++) {
                    float dist = 0;
                    float h0 = (float) Math.atan2(e.x[k] - e.x[k - 1], e.z[k] - e.z[k - 1]);
                    for (int m = k + 1; m < e.x.length && dist < 800f; m++) {
                        dist += (float) Math.hypot(e.x[m] - e.x[m - 1], e.z[m] - e.z[m - 1]);
                        float h1 = (float) Math.atan2(e.x[m] - e.x[m - 1], e.z[m] - e.z[m - 1]);
                        if (Math.abs(wrap(h1 - h0)) > Math.toRadians(100)) { hairpins++; System.out.printf("switchback at %.0f,%.0f (edge %d, %d pts, len %.0f m)%n", e.x[k], e.z[k], e.id, e.x.length, e.len); gr.setColor(Color.BLUE); gr.fillOval((int) ((e.x[k] - x0) / px) - 6, (int) ((e.z[k] - z0) / px) - 6, 12, 12); gr.setColor(new Color(240, 130, 50)); k = m; break; }
                    }
                }
            }
        }
        System.out.printf("freeways: %d edges, %.1f km, tightest radius %.0f m, %d arcs under 400 m radius, %d switchbacks%n",
                fw, fwLen / 1000f, minR, tight, hairpins);
        ImageIO.write(im, "png", new File(a[0]));
        System.exit(0);
    }

    static float wrap(float a) { while (a > Math.PI) a -= 2 * Math.PI; while (a < -Math.PI) a += 2 * Math.PI; return a; }
    static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}
