package com.harlequinxd.drivehard.core.game;

/** Axis-aligned solid box used for both rendering and collision. */
public final class Box {
    public final float minX, minY, minZ, maxX, maxY, maxZ;
    public final int sideTile, topTile;

    public Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int sideTile, int topTile) {
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        this.sideTile = sideTile; this.topTile = topTile;
    }

    /** Box from a footprint centre, size and base height. */
    public static Box at(float cx, float cz, float sx, float sz, float baseY, float height, int side, int top) {
        return new Box(cx - sx / 2, baseY, cz - sz / 2, cx + sx / 2, baseY + height, cz + sz / 2, side, top);
    }
}
