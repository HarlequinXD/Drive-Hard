import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.game.OrbitCamera;
import com.harlequinxd.drivehard.core.game.Player;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

/** Headless sanity checks of the player controller in the generated city. */
public class SimTest {
    static int fails = 0;

    public static void main(String[] a) {
        City c = new City(new TextureLibrary(1234), 1234);
        float dt = 1f / 60f;
        OrbitCamera cam = new OrbitCamera();
        float[] mv = new float[2];

        // Camera at yaw 0 sits at +Z; pushing stick up must move toward -Z (along the promenade).
        Player p = new Player(c.spawnX, c.spawnZ);
        cam.toWorld(0, 1, mv);
        float z0 = p.z;
        for (int i = 0; i < 60; i++) p.update(dt, mv[0], mv[1], false, c);
        check("stick up moves away from camera", p.z < z0 - 3f);
        check("full stick runs", p.anim.name().equals("RUN"));
        check("standing on raised park (curb height)", Math.abs(p.y - City.CURB) < 1e-3f);
        cam.toWorld(1, 0, mv);
        check("stick right is +X", mv[0] > 0.99f);

        // Jump
        float maxY = 0, base = p.y;
        p.update(dt, 0, 0, true, c);
        for (int i = 0; i < 120; i++) { p.update(dt, 0, 0, false, c); maxY = Math.max(maxY, p.y - base); }
        System.out.printf("jump apex %.2f m%n", maxY);
        check("jump lands", p.onGround && Math.abs(p.y - base) < 1e-3f);

        // Step down off a curb onto the road and back up: walk from a sidewalk across a street.
        int i0 = 2, j0 = 3;
        Player q = new Player((c.rx1[i0] + c.rx0[i0 + 1]) * 0.5f, c.rz1[j0] + 1.0f);
        q.y = City.CURB;
        for (int i = 0; i < 700; i++) q.update(dt, 0, -0.5f, false, c);   // walk north across the E-W road
        System.out.printf("crossed road: z %.1f (road %.1f..%.1f) y %.2f%n", q.z, c.rz0[j0], c.rz1[j0], q.y);
        check("crossed the street onto the next sidewalk", q.z < c.rz0[j0] && Math.abs(q.y - City.CURB) < 1e-3f);

        // Buildings block: run into the block interior from the sidewalk
        Player b = new Player((c.rx1[4] + c.rx0[5]) * 0.5f, c.rz1[5] + 2f);
        b.y = City.CURB;
        for (int i = 0; i < 600; i++) b.update(dt, 0, 1f, false, c);
        System.out.printf("into block: z %.1f (block %.1f..%.1f)%n", b.z, c.rz1[5], c.rz0[6]);
        check("blocked by buildings or stays in block", b.z < c.rz0[6]);
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }

    static void check(String n, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + n);
        if (!ok) fails++;
    }
}
