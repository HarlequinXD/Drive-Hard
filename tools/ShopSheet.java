import com.harlequinxd.drivehard.core.render.Texture;
import com.harlequinxd.drivehard.core.tex.JpTextures;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** One shopfront per category (2x), as the game paints them. args: out.png [seed] */
public final class ShopSheet {
    public static void main(String[] a) throws Exception {
        int seed = a.length > 1 ? Integer.parseInt(a[1]) : 5000;
        int n = JpTextures.CATEGORIES.length, cols = 5, S = 1;
        Texture t0 = com.harlequinxd.drivehard.core.tex.ShopLayers.shop(0, seed, new String[1]);
        int cw = t0.w * S + 8, chh = t0.h * S + 8;
        BufferedImage im = new BufferedImage(cols * cw, ((n + cols - 1) / cols) * chh, BufferedImage.TYPE_INT_RGB);
        long t = System.nanoTime();
        for (int i = 0; i < n; i++) {
            Texture tx = com.harlequinxd.drivehard.core.tex.ShopLayers.shop(i, seed + i, new String[1]);
            for (int y = 0; y < tx.h * S; y++) for (int x = 0; x < tx.w * S; x++)
                im.setRGB((i % cols) * cw + x, (i / cols) * chh + y, tx.px[(y / S) * tx.w + x / S]);
        }
        System.out.printf("%d shops, %dx%d, %.1f ms each%n", n, t0.w, t0.h, (System.nanoTime() - t) / 1e6 / n);
        ImageIO.write(im, "png", new File(a[0]));
    }
}
