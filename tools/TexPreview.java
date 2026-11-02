import com.harlequinxd.drivehard.core.render.Texture;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Writes contact sheets of the generated texture library. */
public class TexPreview {
    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "build/preview");
        out.mkdirs();
        long t0 = System.nanoTime();
        TextureLibrary lib = new TextureLibrary(1234);
        System.out.printf("textures: %d in %.0f ms%n", lib.list.size(), (System.nanoTime() - t0) / 1e6);
        sheet(lib, 0, 24, 8, new File(out, "tex_base.png"));
        sheet(lib, 24, 24 + 64, 16, new File(out, "tex_facades1.png"));
        sheet(lib, 24 + 120, 24 + 184, 16, new File(out, "tex_facades2.png"));
        sheet(lib, lib.shops[0], lib.shops[0] + 24, 8, new File(out, "tex_shops.png"));
        for (int i = 0; i < 12; i++) System.out.println(lib.shopNames.get(i));
    }
    static void sheet(TextureLibrary lib, int from, int to, int cols, File f) throws Exception {
        int cw = 0, ch = 0;
        for (int i = from; i < to; i++) { cw = Math.max(cw, lib.list.get(i).w); ch = Math.max(ch, lib.list.get(i).h); }
        int n = to - from, rows = (n + cols - 1) / cols, s = 2;
        BufferedImage img = new BufferedImage(cols * (cw + 2) * s, rows * (ch + 2) * s, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < n; i++) {
            Texture t = lib.list.get(from + i);
            int ox = (i % cols) * (cw + 2), oy = (i / cols) * (ch + 2);
            for (int y = 0; y < t.h * s; y++) for (int x = 0; x < t.w * s; x++) {
                int c = t.px[(y / s) * t.w + x / s];
                img.setRGB(ox * s + x, oy * s + y, (c >>> 24) < 128 ? 0xFF00FF : c);
            }
        }
        ImageIO.write(img, "png", f);
    }
}
