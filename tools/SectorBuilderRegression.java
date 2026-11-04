package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.render.Sectors;

/** Focused regression checks for bridge geometry emitted by SectorBuilder. */
public final class SectorBuilderRegression {
    private SectorBuilderRegression() {}

    public static boolean bridgeEndCapsOmitted(Terrain terrain) {
        Sectors.Slab deck = new Sectors.Slab(new float[]{0, 0, 2, 0, 2, 10, 0, 10});
        deck.ta = 1f;
        deck.ba = 0f;
        deck.sideTex = 1;
        deck.omitEndCaps = true;
        Chunk probe = new Chunk(0, 0);
        probe.sectors.slabs.add(deck);
        GeoBuilder builder = new GeoBuilder(terrain, probe);
        SectorBuilder.toMeshes(probe.sectors, builder);
        builder.end();

        boolean capFound = false, longitudinalSideFound = false;
        for (Geo mesh : probe.batches) {
            for (int i = 0; i < mesh.count; i++) {
                capFound |= edgeFace(mesh, i, 0f, 0f, 2f, 0f)
                        || edgeFace(mesh, i, 0f, 10f, 2f, 10f);
                longitudinalSideFound |= edgeFace(mesh, i, 0f, 0f, 0f, 10f)
                        || edgeFace(mesh, i, 2f, 0f, 2f, 10f);
            }
        }
        return !capFound && longitudinalSideFound;
    }

    private static boolean edgeFace(Geo mesh, int polygon, float ax, float az, float bx, float bz) {
        boolean hasA = false, hasB = false, allOnEdge = true;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int vertex = 0; vertex < mesh.vertexCount(polygon); vertex++) {
            int offset = polygon * 12 + vertex * 3;
            float x = mesh.pos[offset], y = mesh.pos[offset + 1], z = mesh.pos[offset + 2];
            boolean atA = Math.abs(x - ax) < 0.001f && Math.abs(z - az) < 0.001f;
            boolean atB = Math.abs(x - bx) < 0.001f && Math.abs(z - bz) < 0.001f;
            hasA |= atA;
            hasB |= atB;
            allOnEdge &= atA || atB;
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        return hasA && hasB && allOnEdge && maxY - minY > 0.5f;
    }
}
