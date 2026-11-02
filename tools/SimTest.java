import com.harlequinxd.drivehard.core.game.OrbitCamera;
import com.harlequinxd.drivehard.core.game.Player;
import com.harlequinxd.drivehard.core.game.TestWorld;
import com.harlequinxd.drivehard.core.gfx.ProcTextures;

/** Headless sanity checks of the player controller against the test world. */
public class SimTest {
    static int fails = 0;

    public static void main(String[] a) {
        TestWorld w = new TestWorld();
        new ProcTextures();
        System.out.println("world verts: " + w.mesh.vertexCount() + ", boxes: " + w.boxes.size());
        float dt = 1f / 60f;
        OrbitCamera cam = new OrbitCamera();
        float[] mv = new float[2];

        // Camera at yaw 0 sits at +Z; pushing stick up must move toward -Z.
        Player p = new Player(0, 20);
        cam.toWorld(0, 1, mv);
        for (int i = 0; i < 60; i++) p.update(dt, mv[0], mv[1], false, w);
        check("stick up moves away from camera", p.z < 19f && Math.abs(p.x) < 0.01f);
        check("full stick runs", p.anim.name().equals("RUN"));
        // Stick right with camera yaw 0 -> +X
        cam.toWorld(1, 0, mv);
        check("stick right is +X", mv[0] > 0.99f);

        // Jump height
        p = new Player(-25, 25);
        float maxY = 0;
        p.update(dt, 0, 0, true, w);
        for (int i = 0; i < 120; i++) { p.update(dt, 0, 0, false, w); maxY = Math.max(maxY, p.y); }
        System.out.printf("jump apex %.2f m%n", maxY);
        check("jump lands", p.onGround && p.y == 0f);

        // Climb stairs: stairs at x=20, z from 10 to 13, platform to 16 at 1.5 m
        p = new Player(20, 8.5f);
        for (int i = 0; i < 240; i++) p.update(dt, 0, 0.5f, false, w);
        System.out.printf("after stairs y=%.2f z=%.2f%n", p.y, p.z);
        check("walked up stairs", p.y > 1.4f);

        // Wall blocks: concrete wall at z 26.8..27.2 x 11..17
        p = new Player(14, 24);
        for (int i = 0; i < 180; i++) p.update(dt, 0, 1f, false, w);
        check("wall blocks", p.z < 26.8f - Player.RADIUS + 0.02f);
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }

    static void check(String n, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + n);
        if (!ok) fails++;
    }
}
