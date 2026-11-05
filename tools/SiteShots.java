import com.harlequinxd.drivehard.core.Game;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Ground-level views around given points (a bridge, a junction): args out.png x,z [x,z ...]. Four views each. */
public final class SiteShots {
    public static void main(String[] a) throws Exception {
        Game g = new Game(Game.DEFAULT_SEED, null, true);
        g.setResolution(432, 240);
        g.env.hours = 11;
        g.traffic.target = 0;
        g.pedestrians.target = 0;
        int n = a.length - 1;
        BufferedImage out = new BufferedImage(432 * 4, 240 * n, BufferedImage.TYPE_INT_RGB);
        for (int k = 0; k < n; k++) {
            String[] xz = a[k + 1].split(",");
            float cx = Float.parseFloat(xz[0]), cz = Float.parseFloat(xz[1]);
            g.city.chunks.loadAllNow(cx, cz);
            for (int v = 0; v < 4; v++) {
                // stand 60 m out from the site, looking back at it
                float ang = (float) (v * Math.PI / 2 + 0.6);
                float back = Float.parseFloat(System.getProperty("back", "60")); float px = cx + (float) Math.sin(ang) * back, pz = cz + (float) Math.cos(ang) * back;
                g.player.x = px; g.player.z = pz;
                g.player.y = g.city.surface(px, pz, 200f);
                for (int i = 0; i < 3; i++) g.update(1 / 30f, 0, 0, 0, 0, false);
                g.player.x = px; g.player.z = pz;
                g.cam.yaw = ang;
                g.cam.pitch = (float) Math.toRadians(10);
                g.cam.distance = 8f;
                g.cam.update(px, g.player.y, pz, 1f);
                g.render();
                out.setRGB(v * 432, k * 240, 432, 240, g.renderer.fb, 0, 432);
            }
        }
        ImageIO.write(out, "png", new File(a[0]));
        System.exit(0);
    }
}
