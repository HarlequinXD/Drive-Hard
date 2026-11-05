import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.city.City;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Downtown pavements with the pedestrian puppets walking about: four views around the player. */
public final class PedShots {
    public static void main(String[] args) throws Exception {
        Game g = new Game(Game.DEFAULT_SEED, null, true);
        g.setResolution(432, 240);
        g.env.hours = 10;
        City c = g.city;
        g.pedestrians.spawnMin = 4f;
        g.pedestrians.spawnMax = 30f;
        g.pedestrians.target = 45;
        g.player.x = (c.rx0[c.iCentre] + c.rx1[c.iCentre]) * 0.5f;
        g.player.z = (c.rz1[c.jCentre - 1] + c.rz0[c.jCentre]) * 0.5f;
        c.chunks.loadAllNow(g.player.x, g.player.z);
        g.player.y = c.surface(g.player.x, g.player.z);
        g.cache.bakeAllNow();
        long t0 = System.nanoTime();
        for (int i = 0; i < 300; i++) g.update(1 / 30f, 0, 0, 0, 0, false);
        System.out.printf("10 s of game in %.0f ms, %d pedestrians%n", (System.nanoTime() - t0) / 1e6, g.pedestrians.peds.size());
        g.cache.bakeAllNow();
        g.cam.pitch = (float) Math.toRadians(14);
        g.cam.distance = 5f;
        BufferedImage out = new BufferedImage(432 * 2, 240 * 2, BufferedImage.TYPE_INT_RGB);
        for (int shot = 0; shot < 4; shot++) {
            for (int i = 0; i < 15; i++) g.update(1 / 30f, 0, 0, 0, 0, false);
            g.render();
            out.setRGB((shot % 2) * 432, (shot / 2) * 240, 432, 240, g.renderer.fb, 0, 432);
            System.out.println("shot " + shot + ": peds drawn " + g.pedsDrawn);
            g.cam.yaw += (float) (Math.PI / 2);
        }
        ImageIO.write(out, "png", new File(args[0]));
        System.exit(0);
    }
}
