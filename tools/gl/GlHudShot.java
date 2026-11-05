import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.gl.Gl;
import com.harlequinxd.drivehard.core.gl.GlRenderer;
import com.harlequinxd.drivehard.core.ui.MapView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** The phone's GPU path end to end: scene, HUD on a clear layer, menu, presented to a 2x "screen". */
public final class GlHudShot {
    public static void main(String[] args) throws Exception {
        int W = 432, H = 240, SW = 960, SH = 540;
        Game g = new Game(Game.DEFAULT_SEED, null, true);
        FfmGl gl = new FfmGl();
        GlRenderer gpu = new GlRenderer(gl, W, H);
        g.useRenderer(gpu);
        for (int i = 0; i < 30; i++) g.update(1 / 30f, 0, 0.6f, 0.02f, 0, false);
        g.cache.bakeAllNow();
        for (int i = 0; i < 6; i++) { g.update(1 / 30f, 0, 0, 0, 0, false); g.render(); }
        // an off-screen "phone screen"
        int tex = gl.genTexture();
        gl.bindTexture(Gl.TEXTURE_2D, tex);
        gl.texImage2D(Gl.TEXTURE_2D, 0, Gl.RGBA8, SW, SH, Gl.RGBA, Gl.UNSIGNED_BYTE, null);
        int fbo = gl.genFramebuffer();
        gl.bindFramebuffer(Gl.FRAMEBUFFER, fbo);
        gl.framebufferTexture2D(Gl.FRAMEBUFFER, Gl.COLOR_ATTACHMENT0, Gl.TEXTURE_2D, tex, 0);
        gpu.screenFramebuffer = fbo;
        MapView mv = new MapView();
        int[] fb = new int[W * H];
        String[] names = {"hud", "menu", "loading"};
        for (String name : names) {
            g.render();
            java.util.Arrays.fill(fb, 0);
            float scale = Math.min(SW / (float) W, SH / (float) H);
            int vw = Math.round(W * scale), vh = Math.round(H * scale), vx = (SW - vw) / 2, vy = (SH - vh) / 2;
            if (name.equals("hud")) {
                int r = Math.max(34, H / 7);
                mv.drawMinimap(fb, W, H, W - r - 8, r + 8, r, g);
                mv.drawHud(fb, W, H, g, 34, 6);
                mv.drawHints(fb, W, H, "LEFT STICK WALK  -  DRAG RIGHT SIDE TO LOOK", 1f);
                gpu.present(SW, SH, vx, vy, vw, vh, fb);
            } else if (name.equals("menu")) {
                mv.drawMenu(fb, W, H, "PAUSED", new String[]{"RESUME", "MAP AND FAST TRAVEL", "QUIT"}, -1, new String[]{"#ON FOOT", "LEFT STICK     WALK"});
                gpu.present(SW, SH, vx, vy, vw, vh, fb);
            } else {
                var ls = new com.harlequinxd.drivehard.core.ui.LoadScreen(W, H);
                ls.reset("Streaming the streets around you");
                ls.set("Streaming the streets around you", 0.6f);
                gpu.presentImage(SW, SH, vx, vy, vw, vh, ls.render(), W, H);
            }
            ByteBuffer b = ByteBuffer.allocateDirect(SW * SH * 4).order(ByteOrder.nativeOrder());
            gl.bindFramebuffer(Gl.FRAMEBUFFER, fbo);
            gl.readPixels(0, 0, SW, SH, Gl.RGBA, Gl.UNSIGNED_BYTE, b);
            BufferedImage im = new BufferedImage(SW, SH, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < SH; y++)
                for (int x = 0; x < SW; x++) {
                    int o = ((SH - 1 - y) * SW + x) * 4;
                    im.setRGB(x, y, ((b.get(o) & 255) << 16) | ((b.get(o + 1) & 255) << 8) | (b.get(o + 2) & 255));
                }
            ImageIO.write(im, "png", new File(args[0], "screen-" + name + ".png"));
            System.out.println(name + " GL error 0x" + Integer.toHexString(gl.getError()));
        }
        System.exit(0);
    }
}
