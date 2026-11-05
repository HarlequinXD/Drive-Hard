import com.harlequinxd.drivehard.core.render.Texture;
import com.harlequinxd.drivehard.core.tex.JpTextures;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Contact sheet of the facade textures of every kind (first 6 of each), 2x, plus house windows and doors. args: out.png */
public final class TexSheet {
    public static void main(String[] a) throws Exception {
        TextureLibrary lib = new TextureLibrary(20261103L, false);
        java.util.List<int[]> rows = new java.util.ArrayList<>();
        for (JpTextures.Kind k : JpTextures.Kind.values()) rows.add(lib.facades(k));
        rows.add(lib.houseWin);
        rows.add(lib.houseDoor);
        rows.add(lib.houseMat);
        int S = 2, cellW = 136, cellH = 136, cols = 10;
        BufferedImage im = new BufferedImage(cellW * cols * S, cellH * rows.size() * S, BufferedImage.TYPE_INT_RGB);
        for (int r = 0; r < rows.size(); r++) {
            int[] ids = rows.get(r);
            for (int c = 0; c < Math.min(cols, ids.length); c++) {
                Texture t = lib.list.get(ids[c]);
                float sc = Math.min(128f / t.w, 128f / t.h);
                int w = (int) (t.w * sc), h = (int) (t.h * sc);
                for (int y = 0; y < h; y++)
                    for (int x = 0; x < w; x++) {
                        int p = t.px[(int) (y / sc) * t.w + (int) (x / sc)];
                        for (int s = 0; s < S * S; s++) im.setRGB((c * cellW + x) * S + s % S, (r * cellH + y) * S + s / S, p);
                    }
            }
        }
        ImageIO.write(im, "png", new File(a[0]));
        System.out.println(rows.size() + " rows: " + java.util.Arrays.toString(JpTextures.Kind.values()) + " + houseWin, houseDoor, houseMat");
    }
}
