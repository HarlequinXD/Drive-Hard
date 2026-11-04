import com.harlequinxd.drivehard.core.model.Flora;
import com.harlequinxd.drivehard.core.sprite.PropSprites;
import com.harlequinxd.drivehard.core.sprite.SpriteSheet;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Bakes every plant species and writes a contact sheet (direction 1 of each variant, to scale). */
public class FloraPreview {
    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "build/preview");
        out.mkdirs();
        long total = 0, px = 0;
        int W = 1800, H = 520, x = 4, y = 4, rowH = 0;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int yy = 0; yy < H; yy++) for (int xx = 0; xx < W; xx++) img.setRGB(xx, yy, ((xx / 8 + yy / 8) & 1) == 0 ? 0x6A8A7A : 0x5E7C6E);
        for (Flora f : Flora.values()) {
            long t0 = System.nanoTime();
            SpriteSheet s = PropSprites.flora(f);
            long ms = (System.nanoTime() - t0) / 1000000;
            total += ms; px += (long) s.width * s.height;
            System.out.printf("%-17s %dx%d atlas %dx%d  %d ms%n", f, s.frameW, s.frameH, s.width, s.height, ms);
            // draw at a common scale of 12 px/m so sizes compare
            for (int v = 0; v < f.variants; v++) {
                float k = 12f / f.ppm;
                int fw = Math.round(s.frameW * k), fh = Math.round(s.frameH * k);
                if (x + fw > W) { x = 4; y += rowH + 4; rowH = 0; }
                int fx = s.frameX(0, v), fy = s.frameY(0, v);
                for (int j = 0; j < fh; j++) for (int i = 0; i < fw; i++) {
                    int c = s.pixels[(fy + (int) (j / k)) * s.width + fx + (int) (i / k)];
                    if ((c >>> 24) != 0 && y + j < H) img.setRGB(x + i, y + j, c & 0xFFFFFF);
                }
                x += fw + 4; rowH = Math.max(rowH, fh);
            }
        }
        System.out.printf("total %d ms, %.1f M atlas pixels%n", total, px / 1e6);
        ImageIO.write(img, "png", new File(out, "flora.png"));
    }
}
