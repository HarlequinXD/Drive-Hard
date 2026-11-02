import com.harlequinxd.drivehard.core.model.CharacterModel;
import com.harlequinxd.drivehard.core.model.CharacterSpec;
import com.harlequinxd.drivehard.core.sprite.SpriteBaker;
import com.harlequinxd.drivehard.core.sprite.SpriteSheet;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Desktop preview of the sprite baker (no Android needed).
 * Build: see tools/preview.sh. Writes PNGs into the given output directory.
 */
public class BakePreview {
    public static void main(String[] args) throws Exception {
        File outDir = new File(args.length > 0 ? args[0] : "preview");
        outDir.mkdirs();
        int scale = 4;
        com.harlequinxd.drivehard.core.gfx.ProcTextures tex = new com.harlequinxd.drivehard.core.gfx.ProcTextures();
        write(tex.pixels, 256, 256, 3, new File(outDir, "textures.png"));

        long t0 = System.nanoTime();
        SpriteSheet player = new SpriteBaker().bake(new CharacterModel(CharacterSpec.player()));
        long t1 = System.nanoTime();
        System.out.printf("player bake: %.1f ms, atlas %dx%d%n", (t1 - t0) / 1e6, player.width, player.height);
        write(player.pixels, player.width, player.height, 2, new File(outDir, "player_atlas.png"));

        // Pedestrian variety: first idle-front frame of a few random specs.
        int count = 12;
        int fw = player.frameW, fh = player.frameH;
        int[] strip = new int[fw * count * fh];
        for (int i = 0; i < count; i++) {
            SpriteSheet s = new SpriteBaker().bake(new CharacterModel(CharacterSpec.randomPedestrian(i)));
            int dir = i % 8, col = 4 + (i % 8); // walk frames
            for (int y = 0; y < fh; y++)
                for (int x = 0; x < fw; x++)
                    strip[y * fw * count + i * fw + x] = s.pixels[(dir * fh + y) * s.width + col * fw + x];
        }
        write(strip, fw * count, fh, scale, new File(outDir, "pedestrians.png"));
    }

    static void write(int[] px, int w, int h, int scale, File f) throws Exception {
        BufferedImage img = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h * scale; y++) {
            for (int x = 0; x < w * scale; x++) {
                int c = px[(y / scale) * w + (x / scale)];
                int bg = (((x / scale) / 8 + (y / scale) / 8) & 1) == 0 ? 0x6A8A7A : 0x5E7C6E;
                img.setRGB(x, y, (c >>> 24) != 0 ? c & 0xFFFFFF : bg);
            }
        }
        ImageIO.write(img, "png", f);
    }
}
