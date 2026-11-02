import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.city.TrafficLights;
import com.harlequinxd.drivehard.core.game.OrbitCamera;
import com.harlequinxd.drivehard.core.game.Player;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

/** Headless checks: controller, streaming collision, terrain, traffic signals. */
public class SimTest {
    static int fails = 0;

    public static void main(String[] a) {
        City c = new City(new TextureLibrary(1234), 1234);
        float dt = 1f / 60f;
        OrbitCamera cam = new OrbitCamera();
        float[] mv = new float[2];

        Player p = new Player(c.spawnX, c.spawnZ);
        c.chunks.ensureAround(p.x, p.z);
        p.y = c.surface(p.x, p.z);
        cam.toWorld(0, 1, mv);
        float z0 = p.z;
        for (int i = 0; i < 60; i++) { c.chunks.ensureAround(p.x, p.z); p.update(dt, mv[0], mv[1], false, c); }
        check("stick up moves away from camera", p.z < z0 - 3f);
        check("full stick runs", p.anim.name().equals("RUN"));
        check("on the ground", p.onGround && Math.abs(p.y - c.surface(p.x, p.z)) < 0.05f);

        float maxY = 0, base = p.y;
        p.update(dt, 0, 0, true, c);
        for (int i = 0; i < 120; i++) { p.update(dt, 0, 0, false, c); maxY = Math.max(maxY, p.y - base); }
        System.out.printf("jump apex %.2f m%n", maxY);
        check("jump lands", p.onGround && Math.abs(p.y - base) < 0.05f);

        // cross a street: curb down, road, curb up
        int j0 = 3;
        Player q = new Player((c.rx1[2] + c.rx0[3]) * 0.5f, c.rz1[j0] + 1.0f);
        c.chunks.ensureAround(q.x, q.z);
        q.y = c.surface(q.x, q.z);
        for (int i = 0; i < 700; i++) { c.chunks.ensureAround(q.x, q.z); q.update(dt, 0, -0.5f, false, c); }
        check("crossed the street onto the next sidewalk", q.z < c.rz0[j0] && c.isRaised(q.x, q.z)
                && Math.abs(q.y - c.surface(q.x, q.z)) < 0.05f);

        // walk east from the promenade toward the sea: must stop at the water line
        Player w = new Player(c.spawnX, c.spawnZ);
        c.chunks.ensureAround(w.x, w.z);
        w.y = c.surface(w.x, w.z);
        for (int i = 0; i < 60 * 30; i++) { c.chunks.ensureAround(w.x, w.z); w.update(dt, 1f, 0f, false, c); }
        System.out.printf("beach walk stopped at x=%.1f (city edge %.1f), ground %.2f%n", w.x, c.cityX1, c.terrain.height(w.x, w.z));
        check("stopped at the sea", c.terrain.height(w.x, w.z) > -0.6f && w.x > c.cityX1 + 30f);

        // hills exist and terrain is continuous
        float maxH = 0;
        for (float x = c.cityX0 - 400; x < c.cityX0; x += 10) maxH = Math.max(maxH, c.terrain.height(x, (c.cityZ0 + c.cityZ1) / 2));
        System.out.printf("highest hill west of city: %.1f m%n", maxH);
        check("hills west of the city", maxH > 15f);
        check("ocean east of the beach", c.terrain.height(c.cityX1 + 200, (c.cityZ0 + c.cityZ1) / 2) < -1f);

        // traffic signals: N-S and E-W never both green, cycle through all states
        boolean conflict = false; int seen = 0;
        for (float t = 0; t < TrafficLights.CYCLE * 2; t += 0.25f) {
            int ns = c.traffic.state(5, true, t), ew = c.traffic.state(5, false, t);
            if (ns != TrafficLights.RED && ew != TrafficLights.RED) conflict = true;
            seen |= 1 << ns;
        }
        check("signals never conflict", !conflict);
        check("signals cycle green/yellow/red", seen == 7);

        // streaming: chunks generated deterministically
        int polys1 = c.generate(10, 6).polyCount, polys2 = c.generate(10, 6).polyCount;
        check("chunk generation deterministic", polys1 == polys2 && polys1 > 0);
        System.out.println(fails == 0 ? "ALL OK" : fails + " FAILED");
        if (fails > 0) System.exit(1);
    }

    static void check(String n, boolean ok) {
        System.out.println((ok ? "ok   " : "FAIL ") + n);
        if (!ok) fails++;
    }
}
