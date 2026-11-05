import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.game.Vehicle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Gets in a coupe, builds speed down the street, then throws it into a hard turn: four frames of the slide. */
public final class DriftShots {
    public static void main(String[] args) throws Exception {
        Game g = new Game(Game.DEFAULT_SEED, null, true);
        g.setResolution(432, 240);
        g.env.hours = 10;
        g.traffic.target = 0;
        g.traffic.cars.clear();
        int style = Integer.parseInt(args.length > 1 ? args[1] : "180");   // Drift coupe
        float fx = (float) Math.sin(g.player.facing), fz = (float) Math.cos(g.player.facing);
        Vehicle v = new Vehicle(style, g.player.x + fz * 2.2f, g.player.z - fx * 2.2f, g.player.facing, 0f);
        v.y = g.city.surface(v.x, v.z);
        g.parked.add(v);
        g.requestCarStyle(style);
        g.cache.bakeAllNow();
        float dt = 1 / 30f;
        g.update(dt, 0, 0, 0, 0, false, 0, 0, 0, true);
        for (int i = 0; i < 20; i++) g.update(dt, 0, 0, 0, 0, false);
        System.out.println("mode " + g.mode);
        for (int i = 0; i < 75; i++) g.update(dt, 0, 0, 0, 0, false, 0, 1, 0, false);
        System.out.printf("speed %.1f m/s%n", g.vehicle.speed);
        BufferedImage out = new BufferedImage(432 * 2, 240 * 2, BufferedImage.TYPE_INT_RGB);
        for (int shot = 0; shot < 4; shot++) {
            for (int i = 0; i < 9; i++) g.update(dt, 0, 0, 0, 0, false, shot < 3 ? -1f : -0.3f, 1f, 0, false);
            g.cache.bakeAllNow();
            g.render();
            out.setRGB((shot % 2) * 432, (shot / 2) * 240, 432, 240, g.renderer.fb, 0, 432);
            Vehicle c = g.vehicle;
            float sp = (float) Math.hypot(c.vx, c.vz);
            float beta = (float) Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, (c.vx * c.fx() + c.vz * c.fz()) / Math.max(sp, 0.01f)))));
            System.out.printf("shot %d: %.1f m/s, slide %.0f deg, rear slip %.2f%n", shot, sp, beta, c.slipRear);
        }
        ImageIO.write(out, "png", new File(args[0]));
        System.exit(0);
    }
}
