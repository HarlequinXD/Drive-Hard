package com.harlequinxd.drivehard.core.game;

/** Axis-aligned solid box used for collision. */
public final class Box {
    public final float minX, minY, minZ, maxX, maxY, maxZ;

    public Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }
}
