package com.harlequinxd.drivehard.core;

/**
 * How big an island to make and how many cities it has, all read from the seed: a new island's
 * seed carries its size in bits 60-61 (with bit 62 marking a sized seed), so the same seed always
 * makes the same island. Seeds without the mark (the original island) are LARGE.
 *
 * <ul>
 * <li>SMALL, 32 km: one city.</li>
 * <li>MEDIUM, 64 km: one city.</li>
 * <li>LARGE, 128 km: two cities.</li>
 * <li>MEGA, 256 km: at least three cities.</li>
 * </ul>
 * Every size has towns, villages, hamlets and suburbs round its cities.
 */
public final class WorldSpec implements java.io.Serializable {
    public enum Size {
        SMALL("SMALL", 32000f, 1, 56),
        MEDIUM("MEDIUM", 64000f, 1, 80),
        LARGE("LARGE", 128000f, 2, 100),
        MEGA("MEGA", 256000f, 4, 100);

        public final String label;
        /** Side of the square the island is made in (metres). */
        public final float islandSize;
        /** Cities wanted (MEGA: up to this many, never fewer than three). */
        public final int cities;
        /** Blocks across the main city's street grid. */
        public final int mainBlocks;

        Size(String label, float islandSize, int cities, int mainBlocks) {
            this.label = label;
            this.islandSize = islandSize;
            this.cities = cities;
            this.mainBlocks = mainBlocks;
        }

        public int km() { return Math.round(islandSize / 1000f); }

        /** Fewest cities this size must have. */
        public int minCities() { return this == MEGA ? 3 : cities; }

        /** Blocks across the street grid of the other cities. */
        public int otherBlocks() { return this == MEGA ? 72 : 64; }
    }

    private static final long SIZED = 1L << 62;

    public final long seed;
    public final Size size;

    private WorldSpec(long seed, Size size) {
        this.seed = seed;
        this.size = size;
    }

    public static WorldSpec of(long seed) {
        if ((seed & SIZED) == 0 || seed < 0) return new WorldSpec(seed, Size.LARGE);
        return new WorldSpec(seed, Size.values()[(int) ((seed >>> 60) & 3)]);
    }

    /** A fresh random seed for an island of this size. */
    public static long randomSeed(Size size) {
        long r = java.util.concurrent.ThreadLocalRandom.current().nextLong() & ((1L << 60) - 1);
        return r | SIZED | ((long) size.ordinal() << 60);
    }

    /** The seed for an island of this size made from the given base number. */
    public static long sized(long base, Size size) {
        return (base & ((1L << 60) - 1)) | SIZED | ((long) size.ordinal() << 60);
    }

    /** The seed with its size bits stripped (what the generators mix into their randomness). */
    public static long base(long seed) {
        return (seed & SIZED) != 0 && seed > 0 ? seed & ((1L << 60) - 1) : seed;
    }
}
