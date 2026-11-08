import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.road.RoadEdge;
import com.harlequinxd.drivehard.core.road.RoadNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Looks down on freeway interchanges in game (the phone renderer): four views of each of the
 * first few interchanges. args: out.png [which interchanges, e.g. 0,3] [seed]
 */
public final class InterchangeShots {
    public static void main(String[] a) throws Exception {
        long seed = a.length > 2 ? Long.parseLong(a[2]) : Game.DEFAULT_SEED;
        Game g = new Game(seed, null, true);
        g.setResolution(432, 240);
        g.renderer.texMap = Integer.getInteger("texmap", g.renderer.texMap);      // 0 exact, 1 GBA, 2 PS1
        g.env.hours = 11;
        g.traffic.target = 0;
        g.pedestrians.target = 0;
        // interchange centres: the middle of each group of four ramps
        List<float[]> spots = new ArrayList<>();
        for (RoadEdge e : g.city.roads.edges) {
            if (e.cls != RoadClass.RAMP) continue;
            RoadNode n = g.city.roads.nodes.get(e.a), m = g.city.roads.nodes.get(e.b);
            float x = (n.x + m.x) * 0.5f, z = (n.z + m.z) * 0.5f;
            boolean found = false;
            for (float[] s : spots) if (Math.hypot(s[0] / s[2] - x, s[1] / s[2] - z) < 500f) { s[0] += x; s[1] += z; s[2]++; found = true; break; }
            if (!found) spots.add(new float[]{x, z, 1});
        }
        System.out.println(spots.size() + " interchanges");
        String[] pick = (a.length > 1 ? a[1] : "0,1").split(",");
        BufferedImage out = new BufferedImage(432 * 2, 240 * 2 * pick.length, BufferedImage.TYPE_INT_RGB);
        for (int p = 0; p < pick.length; p++) {
            float[] s = spots.get(Integer.parseInt(pick[p]) % spots.size());
            float x = s[0] / s[2], z = s[1] / s[2];
            System.out.printf("interchange %s at %.0f, %.0f (%d ramps)%n", pick[p], x, z, (int) s[2]);
            g.player.x = x; g.player.z = z;
            g.city.chunks.loadAllNow(x, z);
            g.player.y = g.city.surface(x, z);
            // stand on the crossroad bridge (the road node nearest the centre) and look about
            RoadNode best = null;
            for (RoadNode nd : g.city.roads.nodes)
                if (nd.kind == RoadNode.Kind.JUNCTION && nd.edges.length >= 3 && (best == null || Math.hypot(nd.x - x, nd.z - z) < Math.hypot(best.x - x, best.z - z))) best = nd;
            // stand back from the junction, out on the verge between the slip roads
            float ax = best.x - x, az = best.z - z, al = (float) Math.hypot(ax, az);
            float vx = best.x + ax / al * 25f, vz = best.z + az / al * 25f;
            for (int v = 0; v < 4; v++) {
                float px = v == 0 ? x : v == 1 ? best.x : vx, pz = v == 0 ? z : v == 1 ? best.z : vz;
                g.player.x = px; g.player.z = pz;
                g.city.chunks.loadAllNow(px, pz);
                g.player.y = g.city.surface(px, pz, 200f);
                for (int i = 0; i < 4; i++) g.update(1 / 30f, 0, 0, 0, 0, false);
                g.player.x = px; g.player.z = pz;
                // look back toward the bridge from the far views
                g.cam.yaw = v < 2 ? (float) (v * Math.PI / 2 + 0.4) : (float) Math.atan2(ax, az) + (v == 2 ? 0.35f : -0.35f);
                g.cam.pitch = (float) Math.toRadians(v < 2 ? 30 : 12);
                g.cam.distance = v < 2 ? 14f : 9f;
                g.cam.update(px, g.player.y, pz, 1f);
                g.render();
                out.setRGB((v % 2) * 432, (p * 2 + v / 2) * 240, 432, 240, g.renderer.fb, 0, 432);
            }
        }
        ImageIO.write(out, "png", new File(a[0]));
        System.exit(0);
    }
}
