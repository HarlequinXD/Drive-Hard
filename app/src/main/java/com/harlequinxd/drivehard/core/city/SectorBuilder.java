package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.render.Sectors;
import com.harlequinxd.drivehard.core.render.Geo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;

/**
 * Builds procedural building masses (footprints, tiled facades and roof planes), clipping later
 * masses where taller ones stand. At chunk finalization, {@link #toMeshes} turns these temporary
 * descriptions into textured wall, roof and bridge polygons.
 *
 * <p>Pieces can be given in a local frame (u along the frontage, v away from the street) that
 * maps to the world by a quarter turn, so lots facing any street use the same code.</p>
 */
final class SectorBuilder {
    private final Sectors out;
    /** Footprints of the masses already built (world rectangles x0, z0, x1, z1). */
    private final List<float[]> taken = new ArrayList<>();
    // local frame: world = o + u U + v V
    private float ox, oz, Ux = 1f, Uz = 0f, Vx = 0f, Vz = 1f;

    /** A tiled wall face: world endpoints A to B (tiles counted from A) and its tiling. */
    static final class Face {
        final float ax, az, bx, bz;
        final float len;
        final Sectors.Tiles tiles;
        Face(float ax, float az, float bx, float bz, Sectors.Tiles tiles) {
            this.ax = ax; this.az = az; this.bx = bx; this.bz = bz; this.tiles = tiles;
            len = (float) Math.hypot(bx - ax, bz - az);
        }
    }

    SectorBuilder(Sectors out) { this.out = out; }

    /**
     * Converts procedural buildings and bridge solids into static polygon batches.
     */
    static void toMeshes(Sectors source, GeoBuilder gb) {
        for (Sectors.Sector sector : source.sectors) roof(gb, sector);
        for (Sectors.Wall wall : source.walls) {
            // what is outside this wall: another roof (draw only the step above it) or open ground
            float mx = (wall.x0 + wall.x1) * 0.5f + wall.nx * 0.05f, mz = (wall.z0 + wall.z1) * 0.5f + wall.nz * 0.05f;
            Sectors.Sector out = null;
            for (Sectors.Sector o : source.sectors) {
                if (o == wall.front || !o.contains(mx, mz)) continue;
                if (out == null || o.y(mx, mz) > out.y(mx, mz)) out = o;
            }
            float top0 = wall.front.y(wall.x0, wall.z0), top1 = wall.front.y(wall.x1, wall.z1);
            if (out != null) {
                float b0 = out.y(wall.x0, wall.z0), b1 = out.y(wall.x1, wall.z1);
                if (Math.max(top0 - b0, top1 - b1) > 0.02f) wall(gb, wall, b0, b1, top0, top1, false);
            } else {
                wall(gb, wall, 0f, 0f, top0, top1, true);
            }
        }
        slabs(gb, source.slabs);
        source.sectors.clear();
        source.walls.clear();
        source.slabs.clear();
    }

    /** Roof tiles: tile size along the roof axes, so the ordering table sorts small pieces. */
    private static final float ROOF_TILE = 2.5f;

    /**
     * A roof (or the top of a block) as square tiles: the sector polygon cut along its own tile
     * axes into pieces of {@value #ROOF_TILE} m, each a small polygon.
     */
    private static void roof(GeoBuilder gb, Sectors.Sector sector) {
        float[] poly = sector.poly;
        int n = poly.length / 2;
        if (n < 3) return;
        // work relative to the first point: world coordinates are large
        float ox = poly[0], oz = poly[1];
        float ux = sector.ux, uz = sector.uz, vx = sector.vx, vz = sector.vz;
        float[] loc = new float[n * 2];
        float u0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v0 = Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float x = poly[i * 2] - ox, z = poly[i * 2 + 1] - oz;
            float u = x * ux + z * uz, v = x * vx + z * vz;
            loc[i * 2] = u; loc[i * 2 + 1] = v;
            u0 = Math.min(u0, u); u1 = Math.max(u1, u); v0 = Math.min(v0, v); v1 = Math.max(v1, v);
        }
        double area = 0.0;
        for (int i = 0, j = n - 1; i < n; j = i++) area += (double) loc[j * 2] * loc[i * 2 + 1] - (double) loc[i * 2] * loc[j * 2 + 1];
        if (Math.abs(area) < 1e-3) return;
        // world winding of the polygon (relative maths): decides the front of every tile
        double warea = 0.0;
        for (int i = 0, j = n - 1; i < n; j = i++)
            warea += (double) (poly[j * 2] - ox) * (poly[i * 2 + 1] - oz) - (double) (poly[i * 2] - ox) * (poly[j * 2 + 1] - oz);
        // the tile grid is anchored in world space so neighbouring pieces line up
        float wu = ox * ux + oz * uz, wv = ox * vx + oz * vz;
        float baseU = (float) (Math.floor(wu / ROOF_TILE) * ROOF_TILE - wu);
        float baseV = (float) (Math.floor(wv / ROOF_TILE) * ROOF_TILE - wv);
        float cu0 = baseU + (float) Math.floor((u0 - baseU) / ROOF_TILE) * ROOF_TILE;
        float cv0 = baseV + (float) Math.floor((v0 - baseV) / ROOF_TILE) * ROOF_TILE;
        float[] l3 = {sector.light, sector.light, sector.light};
        for (float cu = cu0; cu < u1 - 1e-3f; cu += ROOF_TILE) {
            float[] strip = clip(loc, 1f, 0f, cu);
            if (strip == null) continue;
            strip = clip(strip, -1f, 0f, -(cu + ROOF_TILE));
            if (strip == null) continue;
            for (float cv = cv0; cv < v1 - 1e-3f; cv += ROOF_TILE) {
                float[] t = clip(strip, 0f, 1f, cv);
                if (t == null) continue;
                t = clip(t, 0f, -1f, -(cv + ROOF_TILE));
                if (t == null) continue;
                int m = t.length / 2;
                // back to world (U and V are orthonormal: x = u U + v V), keeping the order
                float[] w = new float[m * 3];
                float[] uv = new float[m * 2];
                for (int k = 0; k < m; k++) {
                    float u = t[k * 2], v = t[k * 2 + 1];
                    float x = ox + u * ux + v * vx, z = oz + u * uz + v * vz;
                    w[k * 3] = x; w[k * 3 + 1] = sector.y(x, z); w[k * 3 + 2] = z;
                    uv[k * 2] = (u + wu) / sector.us;
                    uv[k * 2 + 1] = (v + wv) / sector.vs;
                }
                // the (u,v) frame may be mirrored relative to (x,z): the tile's world winding follows the polygon's
                double ta = 0.0;
                for (int i = 0, j = m - 1; i < m; j = i++)
                    ta += (double) (w[j * 3] - ox) * (w[i * 3 + 2] - oz) - (double) (w[i * 3] - ox) * (w[j * 3 + 2] - oz);
                boolean pos = ta > 0;
                for (int k = 1; k < m - 1; k++) {
                    int bi = pos ? k + 1 : k, ci = pos ? k : k + 1;
                    float[] p = {w[0], w[1], w[2], w[bi * 3], w[bi * 3 + 1], w[bi * 3 + 2], w[ci * 3], w[ci * 3 + 1], w[ci * 3 + 2]};
                    float[] q = {uv[0], uv[1], uv[bi * 2], uv[bi * 2 + 1], uv[ci * 2], uv[ci * 2 + 1]};
                    gb.quad(p, q, l3, sector.tex, L_OBJECT, Geo.FLAG_NO_SUBDIV, 0f, false);
                }
            }
        }
    }

    /** Wall tiles are at most this wide (plain wall runs, foundations, gables). */
    private static final float WALL_TILE = 2.5f;

    /**
     * A wall from bottom to top (at its two ends), tile by tile: the foundation below the
     * floor, each storey's tiles (plain, window, door, shopfront), then the gable or parapet
     * above. With {@code onGround} the bottom follows the terrain under each tile, so buildings
     * on slopes never float.
     */
    private static void wall(GeoBuilder gb, Sectors.Wall wall, float bottom0, float bottom1, float top0, float top1, boolean onGround) {
        Sectors.Tiles tiles = wall.tiles;
        if (tiles == null || wall.len < 0.02f) return;
        float faceLen = Math.max(wall.faceLen > 0f ? wall.faceLen : wall.len, wall.uOff + wall.len);
        int maxFloors = tiles.floors;
        float groundH = Math.max(0.1f, tiles.groundH), storeyH = Math.max(0.1f, tiles.storeyH);
        float pos0 = wall.uOff, pos1 = wall.uOff + wall.len;
        float minPos = Math.max(0f, pos0), maxPos = pos1;
        if (maxPos <= minPos) return;
        float base = wall.front.base;
        float maxTop = Math.max(top0, top1);
        float rowBottom = -1e9f;
        // rows: the foundation (-1), the storeys, and what stands above them (gable, parapet)
        for (int row = -1; row <= maxFloors; row++) {
            float rowTop = row < 0 ? base : (row == 0 ? base + groundH : base + groundH + row * storeyH);
            if (row == maxFloors) rowTop = maxTop;
            if (row >= 0 && rowBottom >= maxTop) break;
            boolean storey = row >= 0 && row < maxFloors;
            int cols = storey && row < tiles.cols.length && tiles.cols[row] > 0 ? tiles.cols[row] : Math.max(1, (int) Math.ceil(faceLen / WALL_TILE));
            float tileW = faceLen / cols;
            int firstCol = Math.max(0, (int) Math.floor(minPos / tileW));
            int lastCol = Math.min(cols - 1, (int) Math.floor(Math.max(minPos, maxPos - 1e-4f) / tileW));
            for (int col = firstCol; col <= lastCol; col++) {
                float a = Math.max(minPos, col * tileW), b = Math.min(maxPos, (col + 1) * tileW);
                if (b - a < 0.01f) continue;
                float s0 = (a - pos0) / wall.len, s1 = (b - pos0) / wall.len;
                float ax = wall.x0 + (wall.x1 - wall.x0) * s0, az = wall.z0 + (wall.z1 - wall.z0) * s0;
                float bx = wall.x0 + (wall.x1 - wall.x0) * s1, bz = wall.z0 + (wall.z1 - wall.z0) * s1;
                // the wall's own bottom and top at this tile's two edges
                float wb0, wb1;
                if (onGround) {
                    wb0 = Math.min(base, gb.h(ax, az)) - 0.3f;
                    wb1 = Math.min(base, gb.h(bx, bz)) - 0.3f;
                } else {
                    wb0 = bottom0 + (bottom1 - bottom0) * s0;
                    wb1 = bottom0 + (bottom1 - bottom0) * s1;
                }
                float wt0 = top0 + (top1 - top0) * s0, wt1 = top0 + (top1 - top0) * s1;
                float lo0 = Math.max(wb0, rowBottom), lo1 = Math.max(wb1, rowBottom);
                float hi0 = Math.min(wt0, rowTop), hi1 = Math.min(wt1, rowTop);
                if (Math.max(hi0 - lo0, hi1 - lo1) < 0.01f) continue;
                if (hi0 < lo0) hi0 = lo0;
                if (hi1 < lo1) hi1 = lo1;
                if (row < 0) {
                    patch(gb, wall, ax, az, bx, bz, lo0, lo1, hi0, hi1, tiles.found,
                            a * 0.5f, b * 0.5f, -hi0 * 0.5f, -hi1 * 0.5f, -lo0 * 0.5f, -lo1 * 0.5f);
                    continue;
                }
                byte kind = storey && tiles.kind[row] != null && col < tiles.kind[row].length ? tiles.kind[row][col] : Sectors.PLAIN;
                int tex = storey && tiles.tex[row] != null && col < tiles.tex[row].length ? tiles.tex[row][col] : 0;
                if (kind == Sectors.FULL) {
                    float h = Math.max(0.1f, rowTop - rowBottom);
                    patch(gb, wall, ax, az, bx, bz, lo0, lo1, hi0, hi1, tex,
                            (a - col * tileW) / tileW, (b - col * tileW) / tileW,
                            1f - (hi0 - rowBottom) / h, 1f - (hi1 - rowBottom) / h,
                            1f - (lo0 - rowBottom) / h, 1f - (lo1 - rowBottom) / h);
                } else {
                    int mat = row == 0 ? tiles.mat : tiles.matUp;
                    if (storey && kind != Sectors.PLAIN) {
                        opening(gb, wall, tiles, kind, tex, mat, rowBottom, rowTop, col, tileW,
                                ax, az, bx, bz, a, b, lo0, lo1, hi0, hi1);
                    } else {
                        materialPatch(gb, wall, ax, az, bx, bz, a, b, lo0, lo1, hi0, hi1, mat, tiles.matScale);
                    }
                }
            }
            rowBottom = rowTop;
        }
    }

    private static void slabs(GeoBuilder gb, List<Sectors.Slab> slabs) {
        Map<SlabEdgeKey, List<SlabEdge>> edges = new HashMap<>();
        for (Sectors.Slab slab : slabs) {
            plane(gb, slab, true);
            plane(gb, slab, false);
            int n = slab.poly.length / 2;
            float cx = 0f, cz = 0f;
            for (int i = 0; i < n; i++) { cx += slab.poly[i * 2]; cz += slab.poly[i * 2 + 1]; }
            cx /= n; cz /= n;
            for (int i = 0; i < n; i++) {
                if (slab.omitEndCaps && (i == 0 || i == 2)) continue;
                int j = (i + 1) % n;
                float x0 = slab.poly[i * 2], z0 = slab.poly[i * 2 + 1];
                float x1 = slab.poly[j * 2], z1 = slab.poly[j * 2 + 1];
                float dx = x1 - x0, dz = z1 - z0, len = (float) Math.hypot(dx, dz);
                if (len < 0.02f) continue;
                float nx = dz / len, nz = -dx / len;
                if (nx * (x0 - cx) + nz * (z0 - cz) < 0f) { nx = -nx; nz = -nz; }
                SlabEdge edge = new SlabEdge(slab, x0, z0, x1, z1, nx, nz);
                edges.computeIfAbsent(new SlabEdgeKey(edge), k -> new ArrayList<>()).add(edge);
            }
        }
        for (List<SlabEdge> group : edges.values()) {
            group.sort((a, b) -> Float.compare(a.topAverage(), b.topAverage()));
            SlabEdge previous = group.get(0);
            if (group.size() == 1) side(gb, previous, previous.bottom0, previous.bottom1, previous.top0, previous.top1);
            for (int i = 1; i < group.size(); i++) {
                SlabEdge current = group.get(i);
                if (Math.max(current.top0 - previous.top0, current.top1 - previous.top1) > 0.02f)
                    side(gb, current, previous.top0, previous.top1, current.top0, current.top1);
                previous = current;
            }
        }
    }

    private static void plane(GeoBuilder gb, Sectors.Slab slab, boolean top) {
        int n = slab.poly.length / 2;
        if (n < 3) return;
        float signedArea = 0f;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            signedArea += slab.poly[i * 2] * slab.poly[j * 2 + 1]
                    - slab.poly[j * 2] * slab.poly[i * 2 + 1];
        }
        float a = top ? slab.ta : slab.ba, b = top ? slab.tb : slab.bb, c = top ? slab.tc : slab.bc;
        float us = top ? slab.us : 2f, vs = top ? slab.vs : 2f;
        int tex = top ? slab.topTex : slab.botTex;
        float light = top ? slab.lightTop : slab.lightBot;
        for (int i = 1; i < n - 1; i++) {
            boolean up = top;
            boolean reverse = (signedArea > 0f) == up;
            int bIndex = reverse ? i + 1 : i;
            int cIndex = reverse ? i : i + 1;
            float[] p = {
                    slab.poly[0], a + b * slab.poly[0] + c * slab.poly[1], slab.poly[1],
                    slab.poly[bIndex * 2], a + b * slab.poly[bIndex * 2] + c * slab.poly[bIndex * 2 + 1], slab.poly[bIndex * 2 + 1],
                    slab.poly[cIndex * 2], a + b * slab.poly[cIndex * 2] + c * slab.poly[cIndex * 2 + 1], slab.poly[cIndex * 2 + 1]
            };
            float[] uv = new float[6];
            int[] indices = {0, bIndex, cIndex};
            for (int k = 0; k < 3; k++) {
                float x = slab.poly[indices[k] * 2], z = slab.poly[indices[k] * 2 + 1];
                if (top) {
                    uv[k * 2] = ((x - slab.ox) * slab.ux + (z - slab.oz) * slab.uz) / us;
                    uv[k * 2 + 1] = ((x - slab.ox) * slab.vx + (z - slab.oz) * slab.vz) / vs + slab.vOff;
                } else {
                    uv[k * 2] = x / us;
                    uv[k * 2 + 1] = z / vs;
                }
            }
            gb.quad(p, uv, new float[]{light, light, light}, tex, L_OBJECT, Geo.FLAG_NO_SUBDIV, 0f, false);
        }
    }

    private static void side(GeoBuilder gb, SlabEdge edge, float bottom0, float bottom1, float top0, float top1) {
        if (edge.slab.sideTex < 0 || Math.max(top0 - bottom0, top1 - bottom1) < 0.02f) return;
        float ax = edge.x0, az = edge.z0, bx = edge.x1, bz = edge.z1;
        if (- (bz - az) * edge.nx + (bx - ax) * edge.nz < 0f) {
            float t = ax; ax = bx; bx = t; t = az; az = bz; bz = t;
            t = bottom0; bottom0 = bottom1; bottom1 = t;
            t = top0; top0 = top1; top1 = t;
        }
        float u0 = (ax + az) * 0.5f, u1 = (bx + bz) * 0.5f;
        float[] p = {ax, bottom0, az, bx, bottom1, bz, bx, top1, bz, ax, top0, az};
        float[] uv = {u0, -bottom0 * 0.5f, u1, -bottom1 * 0.5f,
                u1, -top1 * 0.5f, u0, -top0 * 0.5f};
        float[] l = {edge.slab.lightSide, edge.slab.lightSide, edge.slab.lightSide, edge.slab.lightSide};
        gb.quad(p, uv, l, edge.slab.sideTex, L_OBJECT, Geo.FLAG_NO_SUBDIV, 0f, false);
    }

    private static final class SlabEdge {
        final Sectors.Slab slab;
        final float x0, z0, x1, z1, nx, nz;
        final float bottom0, bottom1, top0, top1;

        SlabEdge(Sectors.Slab slab, float x0, float z0, float x1, float z1, float nx, float nz) {
            this.slab = slab;
            if (x0 > x1 || (x0 == x1 && z0 > z1)) {
                this.x0 = x1; this.z0 = z1; this.x1 = x0; this.z1 = z0;
                bottom0 = slab.bot(x1, z1); bottom1 = slab.bot(x0, z0);
                top0 = slab.top(x1, z1); top1 = slab.top(x0, z0);
            } else {
                this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1;
                bottom0 = slab.bot(x0, z0); bottom1 = slab.bot(x1, z1);
                top0 = slab.top(x0, z0); top1 = slab.top(x1, z1);
            }
            this.nx = nx; this.nz = nz;
        }

        float topAverage() { return (top0 + top1) * 0.5f; }
    }

    private static final class SlabEdgeKey {
        final int x0, z0, x1, z1, bottom0, bottom1;

        SlabEdgeKey(SlabEdge edge) {
            x0 = q(edge.x0); z0 = q(edge.z0); x1 = q(edge.x1); z1 = q(edge.z1);
            bottom0 = q(edge.bottom0); bottom1 = q(edge.bottom1);
        }

        private static int q(float v) { return Math.round(v * 1000f); }

        @Override public int hashCode() {
            int h = x0; h = 31 * h + z0; h = 31 * h + x1; h = 31 * h + z1;
            h = 31 * h + bottom0; return 31 * h + bottom1;
        }

        @Override public boolean equals(Object obj) {
            if (!(obj instanceof SlabEdgeKey)) return false;
            SlabEdgeKey o = (SlabEdgeKey) obj;
            return x0 == o.x0 && z0 == o.z0 && x1 == o.x1 && z1 == o.z1
                    && bottom0 == o.bottom0 && bottom1 == o.bottom1;
        }
    }

    private static void opening(GeoBuilder gb, Sectors.Wall wall, Sectors.Tiles tiles, byte kind, int tex, int mat,
                                float rowBottom, float rowTop, int col, float tileW, float ax, float az,
                                float bx, float bz, float a, float b, float lo0, float lo1, float hi0, float hi1) {
        float ow, oy0, oy1;
        if (kind == Sectors.WINDOW) { ow = Math.min(tiles.winW, tileW - 0.5f); oy0 = tiles.sill; oy1 = tiles.sill + tiles.winH; }
        else if (kind == Sectors.SMALL) { ow = Math.min(0.7f, tileW - 0.4f); oy0 = 1.3f; oy1 = 2.1f; }
        else if (kind == Sectors.DOOR) { ow = Math.min(tiles.doorW, tileW - 0.3f); oy0 = 0f; oy1 = tiles.doorH; }
        else { ow = Math.min(1.8f, tileW - 0.5f); oy0 = 0.1f; oy1 = 2.1f; }
        oy1 = Math.min(oy1, rowTop - rowBottom);
        if (ow <= 0.05f || oy1 <= oy0) {
            materialPatch(gb, wall, ax, az, bx, bz, a, b, lo0, lo1, hi0, hi1, mat, tiles.matScale);
            return;
        }
        float center = (col + 0.5f) * tileW;
        float open0 = center - ow * 0.5f, open1 = center + ow * 0.5f;
        float a0 = Math.max(a, open0), a1 = Math.min(b, open1);
        if (a1 - a0 < 0.02f) {
            materialPatch(gb, wall, ax, az, bx, bz, a, b, lo0, lo1, hi0, hi1, mat, tiles.matScale);
            return;
        }
        float t0 = (a0 - a) / (b - a), t1 = (a1 - a) / (b - a);
        float x0 = lerp(ax, bx, t0), z0 = lerp(az, bz, t0);
        float x1 = lerp(ax, bx, t1), z1 = lerp(az, bz, t1);
        float bot0 = lerp(lo0, lo1, t0), bot1 = lerp(lo0, lo1, t1);
        float top0 = lerp(hi0, hi1, t0), top1 = lerp(hi0, hi1, t1);
        float y0 = rowBottom + oy0, y1 = rowBottom + oy1;
        float openBot0 = Math.max(bot0, y0), openBot1 = Math.max(bot1, y0);
        float openTop0 = Math.min(top0, y1), openTop1 = Math.min(top1, y1);
        if (Math.max(openTop0 - openBot0, openTop1 - openBot1) < 0.02f) {
            materialPatch(gb, wall, ax, az, bx, bz, a, b, lo0, lo1, hi0, hi1, mat, tiles.matScale);
            return;
        }
        materialPatch(gb, wall, ax, az, lerp(ax, bx, t0), lerp(az, bz, t0),
                a, a0, lo0, bot0, hi0, top0, mat, tiles.matScale);
        materialPatch(gb, wall, lerp(ax, bx, t1), lerp(az, bz, t1), bx, bz,
                a1, b, bot1, lo1, top1, hi1, mat, tiles.matScale);
        materialPatch(gb, wall, x0, z0, x1, z1, a0, a1,
                bot0, bot1, openBot0, openBot1, mat, tiles.matScale);
        materialPatch(gb, wall, x0, z0, x1, z1, a0, a1,
                openTop0, openTop1, top0, top1, mat, tiles.matScale);
        patch(gb, wall, x0, z0, x1, z1, openBot0, openBot1, openTop0, openTop1, tex,
                (a0 - open0) / ow, (a1 - open0) / ow,
                1f - (openTop0 - y0) / (y1 - y0), 1f - (openTop1 - y0) / (y1 - y0),
                1f - (openBot0 - y0) / (y1 - y0), 1f - (openBot1 - y0) / (y1 - y0));
    }

    private static void materialPatch(GeoBuilder gb, Sectors.Wall wall, float ax, float az, float bx, float bz,
                                      float u0, float u1, float bottom0, float bottom1, float top0, float top1,
                                      int tex, float scale) {
        patch(gb, wall, ax, az, bx, bz, bottom0, bottom1, top0, top1, tex,
                u0 / scale, u1 / scale, -top0 / scale, -top1 / scale, -bottom0 / scale, -bottom1 / scale);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static void patch(GeoBuilder gb, Sectors.Wall wall, float ax, float az, float bx, float bz,
                              float bottom0, float bottom1, float top0, float top1, int tex,
                              float u0, float u1, float topV0, float topV1, float botV0, float botV1) {
        if (tex < 0 || Math.max(top0 - bottom0, top1 - bottom1) < 0.01f) return;
        float nx = wall.nx, nz = wall.nz;
        if ((- (bz - az)) * nx + (bx - ax) * nz < 0f) {
            float t = ax; ax = bx; bx = t; t = az; az = bz; bz = t;
            t = bottom0; bottom0 = bottom1; bottom1 = t;
            t = top0; top0 = top1; top1 = t;
            t = u0; u0 = u1; u1 = t;
            t = topV0; topV0 = topV1; topV1 = t;
            t = botV0; botV0 = botV1; botV1 = t;
        }
        float[] p = {ax, bottom0, az, bx, bottom1, bz, bx, top1, bz, ax, top0, az};
        float[] uv = {u0, botV0, u1, botV1, u1, topV1, u0, topV0};
        float[] light = {wall.light, wall.light, wall.light, wall.light};
        gb.quad(p, uv, light, tex, L_OBJECT, Geo.FLAG_NO_SUBDIV, 0f, false);
    }

    private static final class EdgeKey {
        final int ax, az, bx, bz, base;

        EdgeKey(Sectors.Wall wall) {
            int x0 = q(wall.x0), z0 = q(wall.z0), x1 = q(wall.x1), z1 = q(wall.z1);
            if (x0 > x1 || (x0 == x1 && z0 > z1)) { int t = x0; x0 = x1; x1 = t; t = z0; z0 = z1; z1 = t; }
            ax = x0; az = z0; bx = x1; bz = z1; base = q(wall.front.base);
        }

        private static int q(float v) { return Math.round(v * 1000f); }

        @Override public int hashCode() {
            int h = ax; h = 31 * h + az; h = 31 * h + bx; h = 31 * h + bz; return 31 * h + base;
        }

        @Override public boolean equals(Object obj) {
            if (!(obj instanceof EdgeKey)) return false;
            EdgeKey o = (EdgeKey) obj;
            return ax == o.ax && az == o.az && bx == o.bx && bz == o.bz && base == o.base;
        }
    }

    /** World coordinates = local (u, v); call {@link #frame} for rotated lots. */
    void world() { ox = 0f; oz = 0f; Ux = 1f; Uz = 0f; Vx = 0f; Vz = 1f; }

    /** Local frame: origin and the world directions of u and v (unit, perpendicular). */
    void frame(float ox, float oz, float ux, float uz, float vx, float vz) {
        this.ox = ox; this.oz = oz; Ux = ux; Uz = uz; Vx = vx; Vz = vz;
    }

    float wx(float u, float v) { return ox + u * Ux + v * Vx; }
    float wz(float u, float v) { return oz + u * Uz + v * Vz; }

    /** A wall face from local A to local B. */
    Face face(float au, float av, float bu, float bv, Sectors.Tiles t) {
        return new Face(wx(au, av), wz(au, av), wx(bu, bv), wz(bu, bv), t);
    }

    /**
     * Adds a roof piece: local convex polygon (u, v pairs), roof plane y = A + B u + C v, roof
     * texture with tiles of {@code tile} metres down the slope, faces of the mass it belongs to
     * (edges lying on them get their tiles) and the tiling of its other edges.
     */
    void piece(float[] local, float A, float B, float C, float base, int roofTex, float tile, float light,
               Face[] faces, Sectors.Tiles inner) {
        float[] poly = new float[local.length];
        for (int i = 0; i < local.length; i += 2) {
            poly[i] = wx(local[i], local[i + 1]);
            poly[i + 1] = wz(local[i], local[i + 1]);
        }
        float b = B * Ux + C * Vx, c = B * Uz + C * Vz;
        float a = A - b * ox - c * oz;
        List<float[]> parts = new ArrayList<>();
        parts.add(poly);
        for (float[] r : taken) {
            List<float[]> next = new ArrayList<>();
            for (float[] p : parts) subtract(p, r, next);
            parts = next;
        }
        for (float[] p : parts) {
            p = clean(p);
            if (p == null) continue;
            Sectors.Sector s = new Sectors.Sector(p);
            s.a = a; s.b = b; s.c = c;
            s.base = base;
            s.tex = roofTex;
            float g = (float) Math.sqrt(b * b + c * c);
            if (g < 1e-4f) { s.ux = 1f; s.uz = 0f; s.vx = 0f; s.vz = 1f; s.us = s.vs = tile; }
            else {
                s.vx = -b / g; s.vz = -c / g;          // down the slope
                s.ux = -s.vz; s.uz = s.vx;             // along the contour
                s.us = tile;
                s.vs = tile / (float) Math.sqrt(1f + g * g);
            }
            float nl = (float) Math.sqrt(b * b + 1f + c * c);
            s.light = light >= 0f ? light : GeoBuilder.sunLight(-b / nl, 1f / nl, -c / nl);
            out.sectors.add(s);
            walls(s, faces, inner);
        }
    }

    /** The mass with this local footprint is complete: later masses are cut away where it stands. */
    void taken(float u0, float v0, float u1, float v1) {
        float x0 = wx(u0, v0), z0 = wz(u0, v0), x1 = wx(u1, v1), z1 = wz(u1, v1);
        taken.add(new float[]{Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1)});
    }

    /** Four faces of a local rectangle, outside on the left going round: front (v0), u0 side, back, u1 side. */
    Face[] rectFaces(float u0, float v0, float u1, float v1, Sectors.Tiles front, Sectors.Tiles left, Sectors.Tiles back, Sectors.Tiles right) {
        return new Face[]{face(u1, v0, u0, v0, front), face(u0, v0, u0, v1, left), face(u0, v1, u1, v1, back), face(u1, v1, u1, v0, right)};
    }

    /** A flat-topped block (local rectangle): one roof plane at height top, the same tiles all round. */
    void block(float u0, float v0, float u1, float v1, float base, float top, int topTex, Sectors.Tiles sides) {
        Face[] f = rectFaces(u0, v0, u1, v1, sides, sides, sides, sides);
        piece(new float[]{u0, v0, u1, v0, u1, v1, u0, v1}, top, 0f, 0f, base, topTex, 2f, -1f, f, sides);
        taken(u0, v0, u1, v1);
    }

    /** A wall face of plain material only (no storeys): parapets, sheds, fences, roof boxes. */
    static Sectors.Tiles plain(int mat, int found) {
        Sectors.Tiles t = new Sectors.Tiles(0);
        t.mat = mat; t.matUp = mat; t.found = found;
        return t;
    }

    // ------------------------------------------------------------------ walls

    private void walls(Sectors.Sector s, Face[] faces, Sectors.Tiles inner) {
        float[] p = s.poly;
        int n = p.length / 2;
        float cx = 0f, cz = 0f;
        for (int i = 0; i < n; i++) { cx += p[i * 2]; cz += p[i * 2 + 1]; }
        cx /= n; cz /= n;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float x0 = p[i * 2], z0 = p[i * 2 + 1], x1 = p[j * 2], z1 = p[j * 2 + 1];
            float dx = x1 - x0, dz = z1 - z0, len = (float) Math.hypot(dx, dz);
            if (len < 0.02f) continue;
            float nx = dz / len, nz = -dx / len;
            if (nx * ((x0 + x1) * 0.5f - cx) + nz * ((z0 + z1) * 0.5f - cz) < 0f) { nx = -nx; nz = -nz; }
            Face f = onFace(faces, x0, z0, x1, z1);
            Sectors.Wall w;
            if (f != null) {
                // lay the wall along its face, so the tiles line up across pieces
                float fdx = f.bx - f.ax, fdz = f.bz - f.az;
                if (dx * fdx + dz * fdz < 0f) { float t = x0; x0 = x1; x1 = t; t = z0; z0 = z1; z1 = t; }
                w = new Sectors.Wall(x0, z0, x1, z1, nx, nz, s, null, f.tiles);
                w.uOff = (float) Math.hypot(x0 - f.ax, z0 - f.az);
                w.faceLen = f.len;
            } else {
                w = new Sectors.Wall(x0, z0, x1, z1, nx, nz, s, null, inner);
            }
            w.light = GeoBuilder.sunLight(nx, 0f, nz);
            out.walls.add(w);
        }
    }

    private static Face onFace(Face[] faces, float x0, float z0, float x1, float z1) {
        if (faces == null) return null;
        for (Face f : faces) {
            float dx = f.bx - f.ax, dz = f.bz - f.az, l = (float) Math.hypot(dx, dz);
            if (l < 1e-4f) continue;
            float d0 = Math.abs(dx * (z0 - f.az) - dz * (x0 - f.ax)) / l, d1 = Math.abs(dx * (z1 - f.az) - dz * (x1 - f.ax)) / l;
            if (d0 > 0.02f || d1 > 0.02f) continue;
            float t0 = (dx * (x0 - f.ax) + dz * (z0 - f.az)) / l, t1 = (dx * (x1 - f.ax) + dz * (z1 - f.az)) / l;
            if (Math.min(t0, t1) > -0.02f && Math.max(t0, t1) < l + 0.02f) return f;
        }
        return null;
    }

    // ------------------------------------------------------------------ polygon clipping

    /** Convex polygon minus an axis-aligned rectangle: up to four convex parts outside it. */
    private static void subtract(float[] p, float[] r, List<float[]> out) {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < p.length; i += 2) {
            minX = Math.min(minX, p[i]); maxX = Math.max(maxX, p[i]);
            minZ = Math.min(minZ, p[i + 1]); maxZ = Math.max(maxZ, p[i + 1]);
        }
        if (maxX <= r[0] + 1e-3f || minX >= r[2] - 1e-3f || maxZ <= r[1] + 1e-3f || minZ >= r[3] - 1e-3f) { out.add(p); return; }
        float[] rest = p;
        // x < r0 | x > r2 | z < r1 | z > r3, each cut off what is left
        float[][] cut = {{-1, 0, -r[0]}, {1, 0, r[2]}, {0, -1, -r[1]}, {0, 1, r[3]}};
        for (float[] c : cut) {
            if (rest == null) return;
            // outside part: c.x*x + c.z*z >= c.k ; inside remainder: <= c.k
            float[] o = clip(rest, c[0], c[1], c[2]);
            if (o != null) out.add(o);
            rest = clip(rest, -c[0], -c[1], -c[2]);
        }
    }

    /** Sutherland-Hodgman: keeps the part with nx*x + nz*z >= k (null if nothing left). */
    private static float[] clip(float[] p, float nx, float nz, float k) {
        int n = p.length / 2;
        float[] o = new float[(n + 2) * 2];
        int m = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            float ax = p[i * 2], az = p[i * 2 + 1], bx = p[j * 2], bz = p[j * 2 + 1];
            float da = nx * ax + nz * az - k, db = nx * bx + nz * bz - k;
            if (da >= 0f) { o = grow(o, m); o[m++] = ax; o[m++] = az; }
            if ((da >= 0f) != (db >= 0f)) {
                float t = da / (da - db);
                o = grow(o, m);
                o[m++] = ax + (bx - ax) * t; o[m++] = az + (bz - az) * t;
            }
        }
        if (m < 6) return null;
        float[] q = java.util.Arrays.copyOf(o, m);
        return area(q) < 0.01f ? null : q;
    }

    private static float[] grow(float[] o, int m) { return m + 2 > o.length ? java.util.Arrays.copyOf(o, o.length * 2) : o; }

    /** Polygon area, relative to its first point (world coordinates are tens of km: products would lose all precision). */
    private static float area(float[] p) {
        double s = 0.0, ox = p[0], oz = p[1];
        int n = p.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++)
            s += (p[j * 2] - ox) * (p[i * 2 + 1] - oz) - (p[i * 2] - ox) * (p[j * 2 + 1] - oz);
        return (float) Math.abs(s) * 0.5f;
    }

    /** Drops repeated points; null for slivers. */
    private static float[] clean(float[] p) {
        int n = p.length / 2;
        float[] o = new float[p.length];
        int m = 0;
        for (int i = 0; i < n; i++) {
            float x = p[i * 2], z = p[i * 2 + 1];
            if (m > 0 && Math.abs(o[m - 2] - x) < 1e-3f && Math.abs(o[m - 1] - z) < 1e-3f) continue;
            o[m++] = x; o[m++] = z;
        }
        if (m >= 4 && Math.abs(o[0] - o[m - 2]) < 1e-3f && Math.abs(o[1] - o[m - 1]) < 1e-3f) m -= 2;
        if (m < 6) return null;
        float[] q = java.util.Arrays.copyOf(o, m);
        return area(q) < 0.02f ? null : q;
    }
}
