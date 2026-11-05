import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.city.Chunk;
import com.harlequinxd.drivehard.core.game.Box;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

/**
 * Frames buildings from across the street: around each given point, the tallest few building
 * boxes, each looked at from 24 m out in front of its most open face. args: out.png x,z [x,z ...]
 */
public final class BuildingShots {
    static final float MAXH = Float.parseFloat(System.getProperty("maxH", "1000")), MAXW = Float.parseFloat(System.getProperty("maxW", "1000"));

    public static void main(String[] a) throws Exception {
        Game g = new Game(Game.DEFAULT_SEED, null, true);
        g.setResolution(432, 240);
        g.env.hours = 10.5f;
        g.traffic.target = 0;
        g.pedestrians.target = 0;
        int per = Integer.getInteger("per", 4);
        float back = Float.parseFloat(System.getProperty("back", "24"));
        List<int[]> frames = new ArrayList<>();
        BufferedImage out = new BufferedImage(432 * per, 240 * (a.length - 1), BufferedImage.TYPE_INT_RGB);
        for (int k = 1; k < a.length; k++) {
            String[] xz = a[k].split(",");
            float cx = Float.parseFloat(xz[0]), cz = Float.parseFloat(xz[1]);
            g.city.chunks.loadAllNow(cx, cz);
            List<Box> boxes = new ArrayList<>();
            for (Chunk ch : g.city.chunks.chunks())
                for (Box b : ch.boxes)
                    if (b.maxY - b.minY > 5f && b.maxY - b.minY < MAXH && b.maxX - b.minX < MAXW && b.maxZ - b.minZ < MAXW && b.maxX - b.minX > 6f && b.maxZ - b.minZ > 6f
                            && Math.hypot((b.minX + b.maxX) / 2 - cx, (b.minZ + b.maxZ) / 2 - cz) < 140f) boxes.add(b);
            boxes.sort((p, q) -> Float.compare((q.maxY - q.minY) + (q.maxX - q.minX) * 0.1f, (p.maxY - p.minY) + (p.maxX - p.minX) * 0.1f));
            List<Box> picked = new ArrayList<>();
            for (Box b : boxes) {
                boolean dup = false;
                for (Box p : picked) if (Math.hypot(p.minX - b.minX, p.minZ - b.minZ) < 25f) dup = true;
                if (!dup) picked.add(b);
                if (picked.size() == per) break;
            }
            for (int v = 0; v < picked.size(); v++) {
                Box b = picked.get(v);
                float mx = (b.minX + b.maxX) / 2, mz = (b.minZ + b.maxZ) / 2;
                // the most open face: test 26 m out from each face centre for other buildings
                float[][] faces = {{mx, b.minZ, 0, -1}, {mx, b.maxZ, 0, 1}, {b.minX, mz, -1, 0}, {b.maxX, mz, 1, 0}};
                float[] best = faces[0];
                int bestN = Integer.MAX_VALUE;
                for (float[] f : faces) {
                    float px = f[0] + f[2] * 26f, pz = f[1] + f[3] * 26f;
                    int n = 0;
                    for (Box o : boxes) if (px > o.minX - 3 && px < o.maxX + 3 && pz > o.minZ - 3 && pz < o.maxZ + 3) n++;
                    if (n < bestN) { bestN = n; best = f; }
                }
                float px = best[0] + best[2] * back, pz = best[1] + best[3] * back;
                g.player.x = px; g.player.z = pz;
                g.player.y = g.city.surface(px, pz, 200f);
                for (int i = 0; i < 3; i++) g.update(1 / 30f, 0, 0, 0, 0, false);
                g.player.x = px; g.player.z = pz;
                // camera behind the player, looking back at the face (and up a little)
                g.cam.yaw = (float) Math.atan2(best[2], best[3]);
                g.cam.pitch = (float) Math.toRadians(Float.parseFloat(System.getProperty("pitch", "-4")));
                g.cam.yaw += Float.parseFloat(System.getProperty("yawOff", "0"));
                g.cam.distance = 5f;
                g.cam.update(px, g.player.y + 1.2f, pz, 1f);
                g.render();
                out.setRGB(v * 432, (k - 1) * 240, 432, 240, g.renderer.fb, 0, 432);
            }
        }
        ImageIO.write(out, "png", new File(a[0]));
        System.exit(0);
    }
}
