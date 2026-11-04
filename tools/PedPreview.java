import com.harlequinxd.drivehard.core.sprite.PropSprites;
import com.harlequinxd.drivehard.core.sprite.SpriteSheet;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Bakes all 100 pedestrian styles; contact sheet of a walking 3/4 frame each. */
public class PedPreview {
    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "build/preview");
        out.mkdirs();
        int cols = 20, cw = 48, ch = 64, sc = 2;
        BufferedImage img = new BufferedImage(cols * cw * sc, 5 * ch * sc, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++)
            img.setRGB(x, y, ((x / 16 + y / 16) & 1) == 0 ? 0x6A8A7A : 0x5E7C6E);
        long t0 = System.nanoTime();
        for (int s = 0; s < 100; s++) {
            SpriteSheet sh = PropSprites.pedestrian(s);
            int dir = (s * 3) % 8, frame = 4 + 2;
            int fx = sh.frameX(dir, frame), fy = sh.frameY(dir, frame);
            int ox = (s % cols) * cw * sc, oy = (s / cols) * ch * sc;
            for (int j = 0; j < ch * sc; j++) for (int i = 0; i < cw * sc; i++) {
                int c = sh.pixels[(fy + j / sc) * sh.width + fx + i / sc];
                if ((c >>> 24) != 0) img.setRGB(ox + i, oy + j, c & 0xFFFFFF);
            }
        }
        System.out.printf("100 pedestrians baked in %.0f ms%n", (System.nanoTime() - t0) / 1e6);
        ImageIO.write(img, "png", new File(out, "pedestrians.png"));
    }
}
