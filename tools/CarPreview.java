import com.harlequinxd.drivehard.core.model.Cars;
import com.harlequinxd.drivehard.core.sprite.PropSprites;
import com.harlequinxd.drivehard.core.sprite.SpriteSheet;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Bakes all 100 car styles; writes a contact sheet (3/4 view) and one car's 16 angles. */
public class CarPreview {
    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "build/preview");
        out.mkdirs();
        int cols = 10, cw = 160, ch = 90;
        BufferedImage img = new BufferedImage(cols * cw, 10 * ch, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++)
            img.setRGB(x, y, ((x / 8 + y / 8) & 1) == 0 ? 0x6A6E70 : 0x5E6264);
        long t0 = System.nanoTime(), px = 0;
        SpriteSheet first = null;
        for (int s = 0; s < Cars.STYLES; s++) {
            SpriteSheet sh = PropSprites.car(s);
            if (s == 8) first = sh;
            px += (long) sh.width * sh.height;
            int fx = sh.frameX(3, 0), fy = sh.frameY(3, 0);
            int ox = (s % cols) * cw + (cw - sh.frameW) / 2, oy = (s / cols) * ch + (ch - sh.frameH) / 2;
            for (int j = 0; j < sh.frameH; j++) for (int i = 0; i < sh.frameW; i++) {
                int c = sh.pixels[(fy + j) * sh.width + fx + i];
                if ((c >>> 24) != 0 && ox + i >= 0 && oy + j >= 0 && ox + i < img.getWidth() && oy + j < img.getHeight())
                    img.setRGB(ox + i, oy + j, c & 0xFFFFFF);
            }
        }
        System.out.printf("100 cars baked in %.0f ms, avg %.0f KB each%n", (System.nanoTime() - t0) / 1e6, px * 4 / 100 / 1024.0);
        ImageIO.write(img, "png", new File(out, "cars.png"));
        BakePreview.write(first.pixels, first.width, first.height, 2, new File(out, "car_angles.png"));
    }
}
