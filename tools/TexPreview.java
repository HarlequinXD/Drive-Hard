import com.harlequinxd.drivehard.core.render.Texture;
import com.harlequinxd.drivehard.core.tex.JpTextures;
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
        System.out.printf("textures: %d in %.0f ms, %d shops%n", lib.list.size(), (System.nanoTime() - t0) / 1e6, TextureLibrary.SHOP_COUNT);
        int[] ids = new int[59];
        for (int i = 0; i < 59; i++) ids[i] = lib.shops[i];
        sheet(lib, ids, 6, new File(out, "tex_shops.png"));
        int[] fac = new int[48];
        int n = 0;
        for (JpTextures.Kind k : JpTextures.Kind.values()) for (int i = 0; i < Math.min(5, lib.facades(k).length) && n < 48; i++) fac[n++] = lib.facades(k)[i];
        sheet(lib, java.util.Arrays.copyOf(fac, n), 16, new File(out, "tex_facades.png"));
        int[] misc = new int[30];
        n = 0;
        for (int i = 0; i < 12; i++) misc[n++] = lib.signs[i];
        for (int v : lib.vending) misc[n++] = v;
        for (int v : lib.roofs) misc[n++] = v;
        misc[n++] = lib.blockWall;
        misc[n++] = lib.stationSigns[0]; misc[n++] = lib.koban[0]; misc[n++] = lib.fireDoors[0];
        sheet(lib, java.util.Arrays.copyOf(misc, n), 10, new File(out, "tex_misc.png"));
        for (int i = 0; i < 10; i++) System.out.println(lib.shopNames.get(i * 7));
    }
    static void sheet(TextureLibrary lib, int[] ids, int cols, File f) throws Exception {
        int cw = 0, ch = 0;
        for (int id : ids) { cw = Math.max(cw, lib.list.get(id).w); ch = Math.max(ch, lib.list.get(id).h); }
        int rows = (ids.length + cols - 1) / cols, s = 2;
        BufferedImage img = new BufferedImage(cols * (cw + 2) * s, rows * (ch + 2) * s, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < ids.length; i++) {
            Texture t = lib.list.get(ids[i]);
            int ox = (i % cols) * (cw + 2), oy = (i / cols) * (ch + 2);
            for (int y = 0; y < t.h * s; y++) for (int x = 0; x < t.w * s; x++) {
                int c = t.px[(y / s) * t.w + x / s];
                img.setRGB(ox * s + x, oy * s + y, (c >>> 24) < 128 ? 0xFF00FF : c);
            }
        }
        ImageIO.write(img, "png", f);
    }
}
