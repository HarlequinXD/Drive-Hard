import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.road.RoadEdge;
import com.harlequinxd.drivehard.core.road.RoadNode;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-down plans of freeway interchanges at true road widths (1 px = 1.25 m): ramps red,
 * freeway orange, bridges outlined, junction nodes dotted. Also the climb of each ramp.
 * args: out.png [seed] [max plans]
 */
public final class InterchangePlan {
    public static void main(String[] a) throws Exception {
        long seed = a.length > 1 ? Long.parseLong(a[1]) : com.harlequinxd.drivehard.core.Game.DEFAULT_SEED;
        int max = a.length > 2 ? Integer.parseInt(a[2]) : 6;
        City c = new City(new com.harlequinxd.drivehard.core.tex.TextureLibrary(seed, true), seed);
        List<float[]> spots = new ArrayList<>();
        int ramps = 0;
        for (RoadEdge e : c.roads.edges) {
            if (e.cls != RoadClass.RAMP) continue;
            ramps++;
            float x = e.x[0], z = e.z[0];
            boolean found = false;
            for (float[] s : spots) if (Math.hypot(s[0] / s[2] - x, s[1] / s[2] - z) < 700f) { s[0] += x; s[1] += z; s[2]++; found = true; break; }
            if (!found) spots.add(new float[]{x, z, 1});
        }
        System.out.printf("%d ramps in %d interchanges (%d at existing bridges)%n", ramps, spots.size(), c.roads.bridgeIcCount);
        int S = 640, cols = 3;
        float mpp = 1.25f;
        int first = a.length > 3 ? Integer.parseInt(a[3]) : 0;
        if (first > 0) spots = new ArrayList<>(spots.subList(Math.min(first, spots.size()), spots.size()));
        int n = Math.min(max, spots.size());
        BufferedImage im = new BufferedImage(S * cols, S * ((n + cols - 1) / cols), BufferedImage.TYPE_INT_RGB);
        Graphics2D gr = im.createGraphics();
        gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (int k = 0; k < n; k++) {
            float[] s = spots.get(k);
            float cx = s[0] / s[2], cz = s[1] / s[2];
            int ox = (k % cols) * S, oz = (k / cols) * S;
            gr.setClip(ox, oz, S, S);
            gr.setColor(new Color(88, 120, 70));
            gr.fillRect(ox, oz, S, S);
            RoadClass[] order = {RoadClass.LANE, RoadClass.TOWN, RoadClass.COUNTRY, RoadClass.HIGHWAY, RoadClass.FREEWAY, RoadClass.RAMP};
            for (int pass = 0; pass < 2; pass++)
                for (RoadClass want : order)
                    for (RoadEdge e : c.roads.edges) {
                        if (e.cls != want) continue;
                        boolean bridgeEdge = false;
                        if (e.bridge != null) for (boolean b : e.bridge) bridgeEdge |= b;
                        if ((pass == 1) != bridgeEdge) continue;     // bridges drawn last, on top
                        Path2D.Float p = new Path2D.Float();
                        boolean any = false;
                        for (int i = 0; i < e.x.length; i++) {
                            float px = ox + S / 2f + (e.x[i] - cx) / mpp, pz = oz + S / 2f + (e.z[i] - cz) / mpp;
                            if (i == 0) p.moveTo(px, pz); else p.lineTo(px, pz);
                            if (Math.abs(e.x[i] - cx) < S * mpp && Math.abs(e.z[i] - cz) < S * mpp) any = true;
                        }
                        if (!any) continue;
                        float w = e.cls.halfWidth() * 2f / mpp;
                        if (bridgeEdge) { gr.setColor(Color.BLACK); gr.setStroke(new BasicStroke(w + 3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)); gr.draw(p); }
                        gr.setColor(want == RoadClass.RAMP ? new Color(200, 70, 60) : want == RoadClass.FREEWAY ? new Color(225, 135, 60)
                                : want == RoadClass.HIGHWAY ? new Color(225, 195, 80) : new Color(170, 168, 160));
                        gr.setStroke(new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                        gr.draw(p);
                    }
            gr.setColor(Color.WHITE);
            for (RoadNode nd : c.roads.nodes) {
                float px = ox + S / 2f + (nd.x - cx) / mpp, pz = oz + S / 2f + (nd.z - cz) / mpp;
                if (px > ox && px < ox + S && pz > oz && pz < oz + S) gr.fillOval((int) px - 2, (int) pz - 2, 4, 4);
            }
            gr.setClip(null);
            gr.setColor(Color.WHITE);
            gr.drawString(String.format("#%d  %.0f, %.0f   (%d ramps)   100 m = %d px", k, cx, cz, (int) s[2], (int) (100 / mpp)), ox + 8, oz + 16);
            for (RoadEdge e : c.roads.edges) {
                if (e.cls != RoadClass.RAMP || Math.hypot(e.x[0] - cx, e.z[0] - cz) > 700f || e.y == null) continue;
                float maxG = 0;
                for (int i = 1; i < e.x.length; i++) {
                    float d = (float) Math.hypot(e.x[i] - e.x[i - 1], e.z[i] - e.z[i - 1]);
                    if (d > 0.5f) maxG = Math.max(maxG, Math.abs(e.y[i] - e.y[i - 1]) / d);
                }
                System.out.printf("  #%d ramp %d: %.0f m, climbs %.1f m, steepest %.1f%%%n", k, e.id, e.len, e.y[e.y.length - 1] - e.y[0], maxG * 100);
            }
        }
        ImageIO.write(im, "png", new File(a[0]));
        System.exit(0);
    }
}
