import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.city.MapAudit;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.HashMap;

/**
 * Builds the island the phone builds and checks it (see {@link MapAudit}). Writes a marked map to
 * {@code <out>/map-check.png} and the full list to {@code <out>/map-check.txt}; exits 1 if there
 * are errors. Usage: java MapCheck [out-dir] [seed]
 */
public class MapCheck {
    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "build/map-check");
        long seed = a.length > 1 ? Long.parseLong(a[1]) : Game.DEFAULT_SEED;
        out.mkdirs();
        long t0 = System.nanoTime();
        Game g = new Game(seed, null, true);
        long t1 = System.nanoTime();
        MapAudit audit = new MapAudit(g.city).run();
        long t2 = System.nanoTime();
        System.out.printf("island built in %.1f s, checked in %.1f s%n", (t1 - t0) / 1e9, (t2 - t1) / 1e9);
        System.out.print(audit.summary());
        StringBuilder all = new StringBuilder(audit.summary()).append('\n');
        HashMap<String, Integer> shown = new HashMap<>();
        for (MapAudit.Problem p : audit.problems) {
            String line = String.format("%s %-18s at %8.0f %8.0f  %s%n", p.error ? "ERROR" : "warn ", p.kind, p.x, p.z, p.what);
            all.append(line);
            int n = shown.merge(p.kind, 1, Integer::sum);
            if (n <= 6) System.out.print("  " + line);
        }
        java.nio.file.Files.writeString(new File(out, "map-check.txt").toPath(), all);
        ImageIO.write(audit.image(32f), "png", new File(out, "map-check.png"));
        System.exit(audit.errors() > 0 ? 1 : 0);
    }
}
