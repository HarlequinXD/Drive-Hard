package com.harlequinxd.drivehard.core.game;

import com.harlequinxd.drivehard.core.gfx.MeshData;

import java.util.ArrayList;
import java.util.List;

/** Static level: render mesh plus collision boxes over a flat ground plane at y = 0. */
public class World {
    public final List<Box> boxes = new ArrayList<>();
    public final MeshData mesh = new MeshData();
    public float halfSize = 32f;

    /** Highest walkable surface under a circle whose feet are at {@code y} (with step tolerance). */
    public float groundHeight(float x, float z, float radius, float y, float stepUp) {
        float h = 0f;
        for (Box b : boxes) {
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
