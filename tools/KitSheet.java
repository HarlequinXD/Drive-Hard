import com.harlequinxd.drivehard.core.render.Texture;
import com.harlequinxd.drivehard.core.tex.HouseKits;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** The painted textures of the first house kits (front, back / left, right quarters). args: out.png [count] */
public final class KitSheet {
    public static void main(String[] a) throws Exception {
        TextureLibrary lib = new TextureLibrary(20261103L, false);
        int n = a.length > 1 ? Integer.parseInt(a[1]) : 12, cols = 6;
        java.util.List<HouseKits.Kit> all = new java.util.ArrayList<>(java.util.Arrays.asList(lib.suburbKits));
        all.addAll(java.util.Arrays.asList(lib.farmKits));
        BufferedImage im = new BufferedImage(cols * 264, ((n + cols - 1) / cols) * 264, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < n; i++) {
            HouseKits.Kit k = all.get(i * all.size() / n);
            Texture t = lib.list.get(k.tex);
            for (int y = 0; y < 256; y++) for (int x = 0; x < 256; x++) im.setRGB((i % cols) * 264 + x, (i / cols) * 264 + y, t.px[y * 256 + x]);
            System.out.printf("kit %d: %.1f x %.1f m, %d floors, roof %d, rise %.1f%s%n", i, k.W, k.D, k.floors, k.roof, k.rise, k.farm ? " farm" : "");
        }
        ImageIO.write(im, "png", new File(a[0]));
    }
}
