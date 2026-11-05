import com.harlequinxd.drivehard.core.Game;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Downtown with traffic flowing: a few frames a second apart, to see the pixel cars on the road. */
public final class TrafficShots {
    public static void main(String[] args) throws Exception {
        Game g = new Game(Game.DEFAULT_SEED, null, true);
        g.setResolution(432, 240);
        for (int i = 0; i < 24; i++) g.requestCar();
        g.cache.bakeAllNow();
        g.env.hours = 10;
        BufferedImage out = new BufferedImage(432 * 2, 240 * 2, BufferedImage.TYPE_INT_RGB);
        g.traffic.target = 0;
        g.traffic.cars.clear();
        for (int shot = 0; shot < 4; shot++) {
            g.parked.clear();
            // a row of cars across the view, 7..16 m ahead, at different headings
            float fx = (float) Math.sin(g.player.facing), fz = (float) Math.cos(g.player.facing);
            float rx = fz, rz = -fx;
            for (int k = 0; k < 5; k++) {
                int style = ((shot * 5 + k) * 7 % 50) * 4 + (k % 4);
                float d = 9f + (k % 2) * 5f, lat = (k - 2) * 4.2f;
                float x = g.player.x + fx * d + rx * lat, z = g.player.z + fz * d + rz * lat;
                float yaw = (float) (shot * 0.6 + k * 1.3);
                com.harlequinxd.drivehard.core.game.Vehicle v = new com.harlequinxd.drivehard.core.game.Vehicle(style, x, z, yaw, 0f);
                v.y = g.city.surface(x, z);
                g.parked.add(v);
                g.requestCarStyle(style);
            }
            g.cache.bakeAllNow();
            for (int i = 0; i < 40; i++) g.update(1 / 30f, 0, 0, 0, 0, false);
            g.cache.bakeAllNow();
            g.render();
            out.setRGB((shot % 2) * 432, (shot / 2) * 240, 432, 240, g.renderer.fb, 0, 432);
            System.out.println("shot " + shot + ": cars drawn " + g.carsDrawn);
            g.cam.yaw += 0.35f;
        }
        ImageIO.write(out, "png", new File(args[0]));
        System.exit(0);
    }
}
