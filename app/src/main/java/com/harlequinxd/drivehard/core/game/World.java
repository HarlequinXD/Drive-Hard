package com.harlequinxd.drivehard.core.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collision world: axis-aligned boxes over a ground plane at y = 0, indexed in a uniform grid
 * so lookups stay cheap across a whole city.
 */
public class World {
    private static final float CELL = 16f;
    public final List<Box> boxes = new ArrayList<>();
    public float minX = -32, minZ = -32, maxX = 32, maxZ = 32;

    private List<Box>[] grid;
    private int gw, gh;
    private float gx0, gz0;

    /** Call after all boxes are added. */
    @SuppressWarnings("unchecked")
    public void buildIndex() {
        gx0 = minX - CELL;
        gz0 = minZ - CELL;
        gw = (int) Math.ceil((maxX - minX) / CELL) + 3;
        gh = (int) Math.ceil((maxZ - minZ) / CELL) + 3;
        grid = new List[gw * gh];
        for (Box b : boxes) {
            int x0 = cx(b.minX - 1), x1 = cx(b.maxX + 1), z0 = cz(b.minZ - 1), z1 = cz(b.maxZ + 1);
            for (int z = z0; z <= z1; z++)
                for (int x = x0; x <= x1; x++) {
                    int i = z * gw + x;
                    if (grid[i] == null) grid[i] = new ArrayList<>(4);
                    grid[i].add(b);
                }
        }
    }

    private int cx(float x) { return Math.max(0, Math.min(gw - 1, (int) ((x - gx0) / CELL))); }
    private int cz(float z) { return Math.max(0, Math.min(gh - 1, (int) ((z - gz0) / CELL))); }

    /** Boxes that may touch the point (within ~1 m). */
    public List<Box> near(float x, float z) {
        if (grid == null) return boxes;
        List<Box> l = grid[cz(z) * gw + cx(x)];
        return l == null ? Collections.<Box>emptyList() : l;
    }

    /** Highest walkable surface under a circle whose feet are at {@code y} (with step tolerance). */
    public float groundHeight(float x, float z, float radius, float y, float stepUp) {
        float h = 0f;
        for (Box b : near(x, z)) {
            if (b.maxY > y + stepUp) continue;
            if (b.maxY <= h) continue;
            if (circleOverlaps(b, x, z, radius * 0.7f)) h = b.maxY;
        }
        return h;
    }

    static boolean circleOverlaps(Box b, float x, float z, float r) {
        float cx = Math.max(b.minX, Math.min(x, b.maxX));
        float cz = Math.max(b.minZ, Math.min(z, b.maxZ));
        float dx = x - cx, dz = z - cz;
        return dx * dx + dz * dz < r * r;
    }
}
