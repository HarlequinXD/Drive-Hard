import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.Settings;
import com.harlequinxd.drivehard.core.ui.MainMenu;
import com.harlequinxd.drivehard.core.ui.MapView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** The main menu pages and the mission screens, as the player sees them: args out.png [w h]. */
public final class MenuShots {
    static Game g;
    static int W, H;
    static BufferedImage out;
    static int shot;

    public static void main(String[] a) throws Exception {
        W = a.length > 1 ? Integer.parseInt(a[1]) : 480;
        H = a.length > 2 ? Integer.parseInt(a[2]) : 320;
        g = new Game(Game.DEFAULT_SEED, null, true);
        g.setResolution(W, H);
        g.env.hours = 17.5;
        Settings s = Settings.load(new File("/tmp/menushots-settings.txt"));
        s.passed = 0b11; s.best[0] = 71.4f; s.best[1] = 52.03f;
        g.settings = s;
        s.apply(g);
        out = new BufferedImage(W * 3, H * 4, BufferedImage.TYPE_INT_RGB);
        MainMenu m = new MainMenu(g, s, a.length > 3);
        MapView mv = new MapView();
        int[] fb = new int[W * H];
        m.open();
        for (int i = 0; i < 90; i++) { g.attract(1 / 30f); frameMenu(m, fb, i == 89); }
        m.down(); for (int i = 0; i < 10; i++) { g.attract(1 / 30f); frameMenu(m, fb, i == 9); }
        m.select(); for (int i = 0; i < 20; i++) { g.attract(1 / 30f); frameMenu(m, fb, i == 19); }
        m.down(); m.down(); for (int i = 0; i < 10; i++) { g.attract(1 / 30f); frameMenu(m, fb, i == 9); }
        m.back(); m.down(); m.select(); for (int i = 0; i < 20; i++) { g.attract(1 / 30f); frameMenu(m, fb, i == 19); }
        m.down(); m.down(); for (int i = 0; i < 10; i++) { g.attract(1 / 30f); frameMenu(m, fb, i == 9); }
        // a mission: the briefing, the countdown, driving to the marker, the results
        g.endAttract();
        g.missions.brief(2);
        for (int i = 0; i < 20; i++) gameFrame(mv, fb, i == 19, 0f);
        g.missions.select();
        for (int i = 0; i < 40; i++) gameFrame(mv, fb, i == 39, 0f);
        for (int i = 0; i < 150; i++) gameFrame(mv, fb, i == 149, 1f);
        for (int i = 0; i < 60; i++) gameFrame(mv, fb, i == 59, 0.6f);
        g.missions.abandon();
        g.missions.brief(3);
        g.missions.select();
        for (int i = 0; i < 200; i++) gameFrame(mv, fb, i == 199, 1f);
        java.lang.reflect.Method pass = g.missions.getClass().getDeclaredMethod("pass");
        pass.setAccessible(true); pass.invoke(g.missions);
        for (int i = 0; i < 30; i++) gameFrame(mv, fb, i == 29, 0f);
        g.missions.abandon();
        g.missions.brief(4);
        g.missions.select();
        for (int i = 0; i < 200; i++) gameFrame(mv, fb, i == 199, 0.8f);
        java.lang.reflect.Method fail = g.missions.getClass().getDeclaredMethod("fail", String.class);
        fail.setAccessible(true); fail.invoke(g.missions, "THEY SPOTTED YOU");
        for (int i = 0; i < 30; i++) gameFrame(mv, fb, i == 29, 0f);
        ImageIO.write(out, "png", new File(a[0]));
        System.exit(0);
    }

    static void frameMenu(MainMenu m, int[] fb, boolean keep) {
        g.render();
        System.arraycopy(g.renderer.fb, 0, fb, 0, fb.length);
        m.draw(fb, W, H, 1 / 30f);
        if (keep) save(fb);
    }

    static void gameFrame(MapView mv, int[] fb, boolean keep, float gas) {
        if (g.missions.modal()) try { Thread.sleep(25); } catch (InterruptedException e) { }
        if (!g.missions.modal()) g.update(1 / 30f, 0, 0, 0, 0, false, 0f, gas, 0f, false, false);
        g.render();
        System.arraycopy(g.renderer.fb, 0, fb, 0, fb.length);
        mv.drawMinimap(fb, W, H, W - 50, 50, 42, g);
        mv.drawHud(fb, W, H, g, 6, 6);
        g.missions.draw(fb, W, H);
        if (keep) save(fb);
    }

    static void save(int[] fb) {
        int x = shot % 3, y = shot / 3;
        if (y < 4) out.setRGB(x * W, y * H, W, H, fb, 0, W);
        shot++;
    }
}
