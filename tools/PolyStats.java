import com.harlequinxd.drivehard.core.city.Chunk;
import com.harlequinxd.drivehard.core.city.City;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.road.Town;

/**
 * Polygons in a few typical places (downtown, the biggest town, a village): all geometry and
 * the building batches, per 64 m chunk, and chunk generation time. args: [seed]
 */
public final class PolyStats {
    public static void main(String[] a) {
        long seed = a.length > 0 ? Long.parseLong(a[0]) : com.harlequinxd.drivehard.core.Game.DEFAULT_SEED;
        City c = new City(new com.harlequinxd.drivehard.core.tex.TextureLibrary(seed, true), seed);
        Town big = null, village = null;
        for (Town t : c.roads.towns) {
            if (t.grid == null) continue;
            if (t.size == Town.Size.TOWN && (big == null)) big = t;
            if (t.size == Town.Size.VILLAGE && village == null) village = t;
        }
        float dx = (c.rx0[c.iCentre] + c.rx1[c.iCentre]) * 0.5f, dz = (c.rz1[c.jCentre - 1] + c.rz0[c.jCentre]) * 0.5f;
        report(c, "downtown", dx, dz);
        float sx = (c.rx1[0] + c.rx0[1]) * 0.5f, sz = c.rz1[2] + 1.5f;
        report(c, "suburb", sx, sz);
        if (big != null) report(c, "town", big.centre.x, big.centre.z);
        if (village != null) report(c, "village", village.centre.x, village.centre.z);
        System.exit(0);
    }

    static void report(City c, String name, float x, float z) {
        int cx0 = (int) Math.floor((x - 160) / 64f), cx1 = (int) Math.floor((x + 160) / 64f);
        int cz0 = (int) Math.floor((z - 160) / 64f), cz1 = (int) Math.floor((z + 160) / 64f);
        long t0 = System.nanoTime();
        int chunks = 0;
        long all = 0, obj = 0;
        for (int j = cz0; j <= cz1; j++)
            for (int i = cx0; i <= cx1; i++) {
                Chunk ch = c.chunks.ensure(i, j);
                chunks++;
                for (Geo g : ch.batches) all += g.count;
                for (Geo g : ch.objectBatches) obj += g.count;
            }
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("%-9s %3d chunks: %6.0f polys/chunk (%5.0f in building batches), %.0f ms/chunk%n", name, chunks,
                all / (double) chunks, obj / (double) chunks, ms / chunks);
    }
}
