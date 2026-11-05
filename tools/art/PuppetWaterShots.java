import com.harlequinxd.drivehard.core.Game;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Walks the player from the beach into the sea, capturing her walking, wading and swimming. */
public final class PuppetWaterShots {
    public static void main(String[] args) throws Exception {
        Game g = new Game(Game.DEFAULT_SEED, null, true);
        g.setResolution(432, 240);
        var t = g.city.terrain;
        float bx = g.player.x, bz = g.player.z, dir = 0f;
        outer:
        for (int k = 0; k < 6000; k++) {
            float x = g.city.spawnX + (float) Math.cos(k * 0.37) * k * 6f, z = g.city.spawnZ + (float) Math.sin(k * 0.37) * k * 6f;
            float h = t.height(x, z);
            if (h < 0.3f || h > 1.2f) continue;
            for (int a = 0; a < 8; a++) {
                float ang = (float) (a * Math.PI / 4);
                if (t.height(x + (float) Math.sin(ang) * 30f, z + (float) Math.cos(ang) * 30f) < -1.5f) { bx = x; bz = z; dir = ang; break outer; }
            }
        }
        g.player.x = bx; g.player.z = bz;
        g.city.chunks.loadAllNow(bx, bz);
        g.player.y = g.city.surface(bx, bz);
        g.player.facing = dir;
        g.cam.yaw = dir + (float) Math.PI;
        g.env.hours = 11;
        String[] want = {"walk", "wade", "swim"};
        BufferedImage out = new BufferedImage(432, 240 * 3, BufferedImage.TYPE_INT_RGB);
        int got = 0;
        for (int i = 0; i < 2000 && got < 3; i++) {
            // walk toward the sea, the camera turning a little to see her from the side
            g.update(1 / 30f, 0, 1f, i < 60 ? 0.02f : 0f, 0, false);
            if (i % 150 == 0) System.out.printf("  %d: %.1f %.1f h %.2f depth %.2f%n", i, g.player.x, g.player.z, t.height(g.player.x, g.player.z), g.player.waterDepth);
            boolean shoot = (got == 0 && i == 40) || (got == 1 && g.player.waterDepth > 0.6f && !g.player.swimming)
                    || (got == 2 && g.player.swimming && i % 7 == 0 && g.player.anim.name().startsWith("SWIM_B"));
            if (!shoot) continue;
            g.cache.bakeAllNow();
            g.render();
            out.setRGB(0, got * 240, 432, 240, g.renderer.fb, 0, 432);
            System.out.println(want[got] + " at frame " + i + ": depth " + g.player.waterDepth + " swimming " + g.player.swimming + " " + g.player.anim);
            got++;
        }
        ImageIO.write(out, "png", new File(args[0]));
        System.exit(0);
    }
}
