import com.harlequinxd.drivehard.core.sprite.PixelCars;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Review sheets of the pixel-art cars: every model in every angle, and a close-up of a few. */
public final class CarSheet {
    public static void main(String[] args) throws Exception {
        PixelCars kit = new PixelCars();
        int from = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        int n = PixelCars.MODELS.length - from, fw = PixelCars.FW, fh = PixelCars.FH;
        int bg = 0xFF686C74;
        // all 16 angles, one row per model
        BufferedImage all = new BufferedImage(fw * 16, fh * n, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < n; i++)
            for (int a = 0; a < 16; a++) {
                int[] px = kit.draw(PixelCars.MODELS[from + i], 0, a);
                for (int y = 0; y < fh; y++)
                    for (int x = 0; x < fw; x++) all.setRGB(a * fw + x, i * fh + y, px[y * fw + x] == 0 ? bg : px[y * fw + x]);
            }
        ImageIO.write(all, "png", new File(args[0], "cars-all.png"));
        // a close-up page per model: 8 of the angles at 2x, in two paints
        for (int i = 0; i < n; i++) {
            int s = 2;
            BufferedImage page = new BufferedImage(fw * 8 * s, fh * 2 * s, BufferedImage.TYPE_INT_RGB);
            for (int row = 0; row < 2; row++)
                for (int k = 0; k < 8; k++) {
                    int[] px = kit.draw(PixelCars.MODELS[from + i], row, k * 2 + (row == 1 ? 1 : 0));
                    for (int y = 0; y < fh * s; y++)
                        for (int x = 0; x < fw * s; x++) {
                            int v = px[(y / s) * fw + x / s];
                            page.setRGB(k * fw * s + x, row * fh * s + y, v == 0 ? bg : v);
                        }
                }
            ImageIO.write(page, "png", new File(args[0], "car-" + (from + i) + ".png"));
        }
        System.out.println("ok");
    }
}
