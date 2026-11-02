package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.render.Geo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** One streamed 64x64 m piece of the island: render batches, collision boxes and sprite props. */
public final class Chunk {
    public static final int SPRITE_PALM = 0, SPRITE_LAMP = 1, SPRITE_SIGNAL = 2, SPRITE_UMBRELLA = 3;
    /** Sprite record: kind, variant (or signal ref), x, y, z, yaw. */
    public static final int SPRITE_STRIDE = 6;

    public final int cx, cz;
    public final List<Geo> batches = new ArrayList<>();
    /** Batches that are tall objects (buildings): candidates for terrain occlusion culling. */
    public final List<Geo> objectBatches = new ArrayList<>();
    public final List<Box> boxes = new ArrayList<>();
    public float[] sprites = new float[SPRITE_STRIDE * 16];
    public int spriteCount;
    public float minX = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
    public int polyCount;

    public Chunk(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    public void sprite(int kind, int variant, float x, float y, float z, float yaw) {
        if ((spriteCount + 1) * SPRITE_STRIDE > sprites.length) sprites = Arrays.copyOf(sprites, sprites.length * 2);
        int o = spriteCount++ * SPRITE_STRIDE;
        sprites[o] = kind; sprites[o + 1] = variant; sprites[o + 2] = x; sprites[o + 3] = y; sprites[o + 4] = z; sprites[o + 5] = yaw;
    }

    /** Called once generation is complete. */
    void finish() {
        for (Geo g : batches) {
            if (g.minX < minX) minX = g.minX;
            if (g.minZ < minZ) minZ = g.minZ;
            if (g.maxX > maxX) maxX = g.maxX;
            if (g.maxZ > maxZ) maxZ = g.maxZ;
            polyCount += g.count;
        }
        for (Box b : boxes) {
            if (b.minX < minX) minX = b.minX;
            if (b.minZ < minZ) minZ = b.minZ;
            if (b.maxX > maxX) maxX = b.maxX;
            if (b.maxZ > maxZ) maxZ = b.maxZ;
        }
        float x0 = cx * ChunkManager.SIZE, z0 = cz * ChunkManager.SIZE;
        minX = Math.min(minX, x0); minZ = Math.min(minZ, z0);
        maxX = Math.max(maxX, x0 + ChunkManager.SIZE); maxZ = Math.max(maxZ, z0 + ChunkManager.SIZE);
    }
}
