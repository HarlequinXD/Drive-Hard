package com.harlequinxd.drivehard.core.road;

/**
 * A named settlement. Hamlets are a junction with houses scattered around it; villages and towns
 * get a grid of streets ({@link RoadClass#TOWN}) around their centre, with house lots along every
 * street and, in towns, a centre with the town office, post office, shops, supermarket, school,
 * shrine, clinic and a petrol station. Roads from elsewhere arrive at the edge of the grid.
 */
public final class Town implements java.io.Serializable {
    public enum Size { HAMLET, VILLAGE, TOWN }

    public final int id;
    public final String name;
    public final Region region;
    public final Size size;
    /** Centre node (a grid crossroads in villages and towns). */
    public final RoadNode centre;
    /** Street grid: nodes[i][j] (i along x, j along z), null for hamlets. */
    public RoadNode[][] grid;
    /** Which grid streets were built: hStreet[i][j] joins (i,j)-(i+1,j), vStreet[i][j] joins (i,j)-(i,j+1). */
    public boolean[][] hStreet, vStreet;

    /** True if the street from grid node (i, j) east (alongX) or south was built. */
    public boolean street(int i, int j, boolean alongX) {
        boolean[][] a = alongX ? hStreet : vStreet;
        return a != null && i >= 0 && j >= 0 && i < a.length && j < a[0].length && a[i][j];
    }

    /** Grid line positions (street centre lines). */
    public float[] gx, gz;
    /** Built-up area. */
    public float x0, z0, x1, z1;
    /** True for towns built at a freeway exit. */
    public boolean exitTown;
    /** On the coast (fishing towns have a harbour). */
    public boolean coastal;
    /** Coastal, but off the coast road (reached by its own road). */
    public boolean spur;

    Town(int id, String name, Region region, Size size, RoadNode centre) {
        this.id = id;
        this.name = name;
        this.region = region;
        this.size = size;
        this.centre = centre;
        float r = size == Size.HAMLET ? 170f : 0f;
        x0 = centre.x - r; x1 = centre.x + r; z0 = centre.z - r; z1 = centre.z + r;
    }

    public boolean contains(float x, float z, float margin) {
        return x >= x0 - margin && x <= x1 + margin && z >= z0 - margin && z <= z1 + margin;
    }

    /** A named district of the island; towns belong to the nearest region centre. */
    public static final class Region implements java.io.Serializable {
        public final String name;
        public final float x, z;

        Region(String name, float x, float z) {
            this.name = name;
            this.x = x;
            this.z = z;
        }
    }
}
