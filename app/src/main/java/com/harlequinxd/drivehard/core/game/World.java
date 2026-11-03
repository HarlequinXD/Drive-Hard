package com.harlequinxd.drivehard.core.game;

import java.util.List;

/**
 * Collision world: a walkable surface height function plus solid boxes near any point.
 */
public abstract class World {
    public float minX = -32, minZ = -32, maxX = 32, maxZ = 32;

    /** Solid boxes that may touch the point (within ~1.5 m). */
    public abstract List<Box> near(float x, float z);

    /** Walkable ground height without boxes (terrain, sidewalks). */
    public float surface(float x, float z) { return 0f; }

    /**
     * Ground height under something whose feet are at {@code y}: where surfaces are stacked
     * (a bridge over a road) the one at or just below {@code y}, so you can pass under bridges.
     */
    public float surface(float x, float z, float y) { return surface(x, z); }

    /** True where the player may not go (open sea). */
    public boolean blocked(float x, float z) { return false; }

    /** Highest walkable surface under a circle whose feet are at {@code y} (with step tolerance). */
    public float groundHeight(float x, float z, float radius, float y, float stepUp) {
        float h = surface(x, z, y + stepUp);
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
