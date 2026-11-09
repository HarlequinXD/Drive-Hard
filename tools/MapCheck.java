import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.city.MapAudit;
import com.harlequinxd.drivehard.core.city.Terrain;
import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.road.RoadEdge;
import com.harlequinxd.drivehard.core.road.RoadNetwork;
import com.harlequinxd.drivehard.core.road.Town;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.HashMap;

/**
 * Builds the island the phone builds and checks it (see {@link MapAudit}). Writes a marked map to
 * {@code <out>/map-check.png} and the full list to {@code <out>/map-check.txt}; exits 1 if there
 * are errors. Usage: java MapCheck [out-dir] [seed]
 */
public class MapCheck {
    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "build/map-check");
        long seed = Game.DEFAULT_SEED;
        if (a.length > 1) {
            String v = a[1].toUpperCase();
            boolean named = false;
            for (com.harlequinxd.drivehard.core.WorldSpec.Size sz : com.harlequinxd.drivehard.core.WorldSpec.Size.values())
                if (sz.name().equals(v)) { seed = com.harlequinxd.drivehard.core.WorldSpec.sized(Game.DEFAULT_SEED, sz); named = true; }
            if (!named) seed = Long.parseLong(a[1]);
        }
        out.mkdirs();
        long t0 = System.nanoTime();
        Game g = new Game(seed, null, true);
        long t1 = System.nanoTime();
        MapAudit audit = new MapAudit(g.city).run();
        long t2 = System.nanoTime();
        System.out.printf("%s island (%d km, %d cities, %d towns) built in %.1f s, checked in %.1f s%n", g.city.spec.size,
                g.city.spec.size.km(), g.city.metros.size(), g.city.roads.towns.size(), (t1 - t0) / 1e9, (t2 - t1) / 1e9);
        System.out.print(audit.summary());
        StringBuilder all = new StringBuilder(audit.summary()).append('\n');
        HashMap<String, Integer> shown = new HashMap<>();
        for (MapAudit.Problem p : audit.problems) {
            String line = String.format("%s %-18s at %8.0f %8.0f  %s%n", p.error ? "ERROR" : "warn ", p.kind, p.x, p.z, p.what);
            all.append(line);
            int n = shown.merge(p.kind, 1, Integer::sum);
            if (n <= 6) System.out.print("  " + line);
        }
        java.nio.file.Files.writeString(new File(out, "map-check.txt").toPath(), all);
        ImageIO.write(image(audit, 32f), "png", new File(out, "map-check.png"));
        System.exit(audit.errors() > 0 ? 1 : 0);
    }

    /** Island map (1 px per {@code mpp} metres) with roads, towns and every problem ringed: red errors, amber warnings. */
    static java.awt.image.BufferedImage image(MapAudit audit, float mpp) {
        Terrain terrain = audit.city().terrain; RoadNetwork roads = audit.city().roads;
        int w = (int) ((terrain.islandX1 - terrain.islandX0) / mpp), h = (int) ((terrain.islandZ1 - terrain.islandZ0) / mpp);
        java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < h; py++)
            for (int px = 0; px < w; px++) {
                float x = terrain.islandX0 + (px + 0.5f) * mpp, z = terrain.islandZ0 + (py + 0.5f) * mpp;
                float over = terrain.coastOver(x, z);
                int c;
                if (over > 0f) c = 0x1c3a5c;
                else {
                    float n = terrain.natural(x, z);
                    int g = (int) Math.max(0, Math.min(230, 60 + (float) Math.sqrt(Math.max(0f, n)) * 9f));
                    c = (g / 3) << 16 | g << 8 | (g / 3);
                }
                im.setRGB(px, py, c);
            }
        java.awt.Graphics2D g2 = im.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        for (RoadEdge e : roads.edges) {
            g2.setColor(e.cls.motorway() ? new java.awt.Color(0xe0e0e0) : e.cls == RoadClass.LANE ? new java.awt.Color(0x8a7a5a) : new java.awt.Color(0xb0b0b0));
            for (int i = 0; i < e.count() - 1; i++)
                g2.drawLine(px(terrain, e.x[i], mpp), pz(terrain, e.z[i], mpp), px(terrain, e.x[i + 1], mpp), pz(terrain, e.z[i + 1], mpp));
        }
        g2.setColor(new java.awt.Color(0xffffff));
        for (Town t : roads.towns) g2.drawString(t.name, px(terrain, t.centre.x, mpp) + 4, pz(terrain, t.centre.z, mpp) - 4);
        g2.setStroke(new java.awt.BasicStroke(1.5f));
        for (MapAudit.Problem p : audit.problems) {
            g2.setColor(p.error ? new java.awt.Color(0xff3030) : new java.awt.Color(0xffb020));
            int r = p.error ? 5 : 3;
            g2.drawOval(px(terrain, p.x, mpp) - r, pz(terrain, p.z, mpp) - r, r * 2, r * 2);
        }
        g2.dispose();
        return im;
    }

    /** Close-up of (cx, cz): ground heights shaded, roads at their width, plots outlined, problems ringed. */
    static java.awt.image.BufferedImage zoom(MapAudit audit, float cx, float cz, float radius, float ppm) {
        Terrain terrain = audit.city().terrain; RoadNetwork roads = audit.city().roads;
        int n = (int) (radius * 2 * ppm);
        java.awt.image.BufferedImage im = new java.awt.image.BufferedImage(n, n, java.awt.image.BufferedImage.TYPE_INT_RGB);
        float x0 = cx - radius, z0 = cz - radius;
        for (int py = 0; py < n; py++)
            for (int px = 0; px < n; px++) {
                float x = x0 + (px + 0.5f) / ppm, z = z0 + (py + 0.5f) / ppm;
                float h = terrain.height(x, z);
                int band = ((int) Math.floor(h)) & 1;
                int g = (int) Math.max(0, Math.min(200, 60 + (h % 50 + 50) % 50 * 2.5f)) + band * 12;
                im.setRGB(px, py, terrain.coastOver(x, z) > 0 ? 0x1c3a5c : ((g / 3) << 16 | g << 8 | (g / 3)));
            }
        java.awt.Graphics2D g2 = im.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        java.util.function.BiFunction<Float, Float, java.awt.Point> P = (x, z) -> new java.awt.Point((int) ((x - x0) * ppm), (int) ((z - z0) * ppm));
        roads.forEachPiece(x0, z0, x0 + radius * 2, z0 + radius * 2, (e, i) -> {
            g2.setStroke(new java.awt.BasicStroke(e.cls.halfWidth() * 2 * ppm, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.setColor(e.bridge[i] ? new java.awt.Color(0xc0a0ff) : e.cls == RoadClass.LANE ? new java.awt.Color(0x9a8a6a) : new java.awt.Color(0x707070));
            java.awt.Point a = P.apply(e.x[i], e.z[i]), b = P.apply(e.x[i + 1], e.z[i + 1]);
            g2.drawLine(a.x, a.y, b.x, b.y);
        });
        g2.setStroke(new java.awt.BasicStroke(1f));
        audit.forEachPlot((sx0, sz0, sx1, sz1, kind, fx, fz, ground) -> {
            if (sx1 < x0 || sz1 < z0 || sx0 > x0 + radius * 2 || sz0 > z0 + radius * 2) return;
            java.awt.Point a = P.apply(sx0, sz0), b = P.apply(sx1, sz1);
            g2.setColor(new java.awt.Color(java.awt.Color.HSBtoRGB(kind / 16f, 0.7f, 1f)));
            g2.drawRect(a.x, a.y, b.x - a.x, b.y - a.y);
            java.awt.Point q = P.apply(fx, fz);
            g2.fillOval(q.x - 2, q.y - 2, 4, 4);
            if (ground != null) {
                g2.setColor(new java.awt.Color(0x60ffffff, true));
                for (int i = 0; i < ground.length; i += 6) {
                    java.awt.Point c = P.apply(ground[i], ground[i + 1]), d = P.apply(ground[i + 2], ground[i + 3]);
                    g2.fillRect(c.x, c.y, d.x - c.x, d.y - c.y);
                }
            }
        });
        g2.setStroke(new java.awt.BasicStroke(2f));
        for (MapAudit.Problem p : audit.problems) {
            if (Math.abs(p.x - cx) > radius || Math.abs(p.z - cz) > radius) continue;
            java.awt.Point q = P.apply(p.x, p.z);
            g2.setColor(p.error ? java.awt.Color.RED : java.awt.Color.ORANGE);
            g2.drawOval(q.x - 6, q.y - 6, 12, 12);
        }
        g2.dispose();
        return im;
    }

    private static int px(Terrain terrain, float x, float mpp) { return (int) ((x - terrain.islandX0) / mpp); }

    private static int pz(Terrain terrain, float z, float mpp) { return (int) ((z - terrain.islandZ0) / mpp); }
}
