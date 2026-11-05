import com.harlequinxd.drivehard.core.render.Texture;
import com.harlequinxd.drivehard.core.tex.FacadeLayers;
import com.harlequinxd.drivehard.core.tex.FacadeRecipes;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Building elevations from the facade layer stack: each column one building type, each row
 * another style of it. The bays repeat across and up exactly as they tile on the game's walls:
 * ground floor (ground bays with an entrance), storeys of the bay, a parapet of plain material.
 * args: out.png [seed]
 */
public final class FacadePreview {
    public static void main(String[] a) throws Exception {
        long seed = a.length > 1 ? Long.parseLong(a[1]) : 1;
        FacadeRecipes.Type[] types = FacadeRecipes.Type.values();
        int bays = 4, storeys = 4, bw = 32, sh = 64;               // half-size texels
        int ew = bays * bw, eh = (storeys + 1) * sh + 12;
        int rows = 3, gap = 10;
        BufferedImage im = new BufferedImage(types.length * (ew + gap) + gap, rows * (eh + gap) + gap, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = im.createGraphics();
        g.setColor(new java.awt.Color(0x8FA9C0));
        g.fillRect(0, 0, im.getWidth(), im.getHeight());
        long t0 = System.nanoTime();
        int n = 0;
        for (int ti = 0; ti < types.length; ti++)
            for (int row = 0; row < rows; row++) {
                FacadeLayers.Style s = FacadeLayers.style(FacadeRecipes.make(types[ti], seed * 100 + row));
                n++;
                int ox = gap + ti * (ew + gap), oy = gap + row * (eh + gap);
                // parapet
                for (int y = 0; y < 12; y++) for (int x = 0; x < ew; x++) im.setRGB(ox + x, oy + y, at(s.plain, x * 2, y * 2));
                for (int st = 0; st < storeys; st++)
                    for (int b = 0; b < bays; b++) {
                        // the 2x2 bay texture: this bay's quarter of it
                        Texture t = s.bay;
                        for (int y = 0; y < sh; y++) for (int x = 0; x < bw; x++)
                            im.setRGB(ox + b * bw + x, oy + 12 + st * sh + y, at(t, (b & 1) * 64 + x * 2, (st & 1) * 128 + y * 2));
                    }
                for (int b = 0; b < bays; b++) blit(im, b == 1 ? s.entrance : s.ground, ox + b * bw, oy + 12 + storeys * sh, bw, sh);
            }
        System.out.printf("%d styles in %.0f ms (%.1f ms each)%n", n, (System.nanoTime() - t0) / 1e6, (System.nanoTime() - t0) / 1e6 / n);
        ImageIO.write(im, "png", new File(a[0]));
    }

    static int at(Texture t, int x, int y) { return t.px[(y % t.h) * t.w + (x % t.w)]; }

    static void blit(BufferedImage im, Texture t, int ox, int oy, int w, int h) {
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) im.setRGB(ox + x, oy + y, at(t, x * t.w / w, y * t.h / h));
    }
}
