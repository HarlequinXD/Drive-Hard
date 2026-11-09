package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.model.Flora;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.tex.JpTextures.Kind;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import java.util.Random;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;

/**
 * The buildings found in most country towns: the town office, post office, clinic, supermarket
 * with its car park, the school with a sports ground, a shrine with its torii, and a petrol
 * station. Each is built on its site (world rectangle) facing the street on {@code face}
 * (0 = north, 1 = south). Name boards stand on the roof or on posts, never on a wall.
 */
final class TownGen {
    private final City city;
    private final TextureLibrary lib;
    private final GeoBuilder gb;
    private final Random r;
    private final Chunk ch;

    TownGen(City city, TextureLibrary lib, GeoBuilder gb, Random r, Chunk ch) {
        this.city = city;
        this.lib = lib;
        this.gb = gb;
        this.r = r;
        this.ch = ch;
    }

    void build(int kind, float x0, float z0, float x1, float z1, int face) {
        boolean[] street = new boolean[4];
        street[face] = true;
        switch (kind) {
            case Countryside.TOWN_HALL: {
                // set back behind a forecourt, three storeys of offices
                float[] b = cap(inset(x0, z0, x1, z1, face, 2f, 7f, 2f), face, 34f, 18f);
                new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.TOWN_HALL, b[0], b[1], b[2], b[3], street, new boolean[4], city.shopsSuburb);
                roofSign(b, face, base(b) + 4.6f + 2 * 3.6f + 0.8f, lib.signTownOffice, 10f);
                flagpole(face == 0 ? b[0] + 2f : b[2] - 2f, face == 0 ? b[1] - 4f : b[3] + 4f);
                break;
            }
            case Countryside.POST: {
                float[] b = cap(inset(x0, z0, x1, z1, face, 1f, 4f, 1f), face, 16f, 12f);
                new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.POST_OFFICE, b[0], b[1], b[2], b[3], street, street, new int[]{lib.lobbies[0]});
                roofSign(b, face, base(b) + 4.6f + 3.2f + 0.8f, lib.signPost, Math.min(8f, b[2] - b[0] - 1f));
                postBox(face == 0 ? b[0] + 1.5f : b[2] - 1.5f, face == 0 ? b[1] - 2f : b[3] + 2f);
                break;
            }
            case Countryside.CLINIC: {
                float[] b = cap(inset(x0, z0, x1, z1, face, 1f, 4f, 1f), face, 16f, 12f);
                new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.CLINIC, b[0], b[1], b[2], b[3], street, new boolean[4], city.shopsSuburb);
                roofSign(b, face, base(b) + 2 * 3.2f + 0.8f, lib.signClinic, Math.min(7f, b[2] - b[0] - 1f));
                break;
            }
            case Countryside.SUPERMARKET: {
                // the store across the back of the block, car park in front
                float[] b = face == 0 ? new float[]{x0 + 2f, z1 - 22f, x1 - 2f, z1 - 1f} : new float[]{x0 + 2f, z0 + 1f, x1 - 2f, z0 + 22f};
                b = cap(b, 1 - face, 44f, 22f);
                new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.SUPERMARKET, b[0], b[1], b[2], b[3], street, street, new int[]{lib.superWall});
                roofSign(b, face, base(b) + 4.6f + 0.8f, lib.signSuper, 14f);
                for (int k = 0; k < 4; k++) {   // car park lamps
                    float lx = x0 + 6f + k * (x1 - x0 - 12f) / 3f, lz = face == 0 ? z0 + 9f : z1 - 9f;
                    city.lampAt(ch, lx, lz, 0f);
                }
                break;
            }
            case Countryside.SCHOOL: {
                // a long three-storey block along the street, the sports ground behind it
                float[] b = face == 0 ? new float[]{x0 + 3f, z0 + 2f, x1 - 3f, z0 + 14f} : new float[]{x0 + 3f, z1 - 14f, x1 - 3f, z1 - 2f};
                b = cap(b, face, 56f, 12f);
                new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.SCHOOL, b[0], b[1], b[2], b[3], street, new boolean[4], city.shopsSuburb);
                roofSign(b, face, base(b) + 3 * 3.6f + 0.8f, lib.signSchool, 9f);
                // fence round the ground, a row of cherry trees
                float fz0 = face == 0 ? z0 + 15f : z0 + 0.5f, fz1 = face == 0 ? z1 - 0.5f : z1 - 15f;
                fence(x0 + 0.5f, fz0, x1 - 0.5f, fz1);
                for (float x = x0 + 4f; x < x1 - 3f; x += 9f) city.plantAtTerrain(ch, Flora.SAKURA, x, face == 0 ? z1 - 2f : z0 + 2f);
                break;
            }
            case Countryside.SHRINE:
                shrine(x0, z0, x1, z1, face);
                break;
            case Countryside.GAS:
                gasStation(x0, z0, x1, z1, face);
                break;
            default:
                break;
        }
    }

    /**
     * Keeps a civic building to a real size: at most maxW along the street (centred) and maxD deep
     * (from the street side, face 0 = north, 1 = south).
     */
    private static float[] cap(float[] b, int face, float maxW, float maxD) {
        float w = b[2] - b[0];
        if (w > maxW) { float c = (b[0] + b[2]) * 0.5f; b[0] = c - maxW * 0.5f; b[2] = c + maxW * 0.5f; }
        if (b[3] - b[1] > maxD) { if (face == 0) b[3] = b[1] + maxD; else b[1] = b[3] - maxD; }
        return b;
    }

    /** Building footprint inside a site: side margins, front setback, back margin. */
    private static float[] inset(float x0, float z0, float x1, float z1, int face, float side, float front, float back) {
        return face == 0 ? new float[]{x0 + side, z0 + front, x1 - side, z1 - back}
                : new float[]{x0 + side, z0 + back, x1 - side, z1 - front};
    }

    private float base(float[] b) {
        Terrain t = city.terrain;
        return Math.max(Math.max(t.height(b[0], b[1]), t.height(b[2], b[1])), Math.max(t.height(b[0], b[3]), t.height(b[2], b[3])));
    }

    /** The hot-spring inn's red sign on its roof. */
    void onsenSign(float[] b, int face) {
        roofSign(b, face, base(b) + 4.6f + 3 * 3.2f + 0.8f, lib.signOnsen, Math.min(9f, b[2] - b[0] - 1f));
    }

    /** A name board standing on the roof at the street edge, lettered both sides. */
    private void roofSign(float[] b, int face, float y, int tex, float w) {
        float cx = (b[0] + b[2]) * 0.5f, z = face == 0 ? b[1] + 0.6f : b[3] - 0.6f;
        float h = w / 4f;
        gb.begin(false);
        if (face == 0) {
            gb.wall(cx + w * 0.5f, z, cx - w * 0.5f, z, y, y + h, tex, 0f, 1f, 0f, 1f, L_OBJECT, -0.2f, 1f);
            gb.wall(cx - w * 0.5f, z + 0.1f, cx + w * 0.5f, z + 0.1f, y, y + h, tex, 1f, 0f, 0f, 1f, L_OBJECT, -0.2f, 1f);
        } else {
            gb.wall(cx - w * 0.5f, z, cx + w * 0.5f, z, y, y + h, tex, 0f, 1f, 0f, 1f, L_OBJECT, -0.2f, 1f);
            gb.wall(cx + w * 0.5f, z - 0.1f, cx - w * 0.5f, z - 0.1f, y, y + h, tex, 1f, 0f, 0f, 1f, L_OBJECT, -0.2f, 1f);
        }
        for (int k = -1; k <= 1; k += 2)
            gb.box(cx + k * w * 0.35f - 0.05f, y - 0.8f, z - 0.05f, cx + k * w * 0.35f + 0.05f, y, z + 0.15f, lib.metal, lib.metal, 1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
        gb.end();
    }

    private void flagpole(float x, float z) {
        float y = city.terrain.height(x, z);
        gb.begin(false);
        gb.box(x - 0.06f, y, z - 0.06f, x + 0.06f, y + 9f, z + 0.06f, lib.pole, lib.pole, 1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
        gb.wall(x + 0.06f, z, x + 1.9f, z, y + 7.6f, y + 8.8f, lib.paintWhite, 0f, 1f, 0f, 1f, L_OBJECT, 0f, 1f);
        gb.wall(x + 1.9f, z, x + 0.06f, z, y + 7.6f, y + 8.8f, lib.paintWhite, 0f, 1f, 0f, 1f, L_OBJECT, 0f, 1f);
        gb.end();
        ch.boxes.add(new Box(x - 0.1f, y - 1f, z - 0.1f, x + 0.1f, y + 9f, z + 0.1f));
    }

    /** The red post box outside the post office. */
    private void postBox(float x, float z) {
        float y = city.terrain.height(x, z);
        gb.begin(false);
        gb.box(x - 0.3f, y, z - 0.25f, x + 0.3f, y + 1.3f, z + 0.25f, lib.paintRed, lib.paintRed, 1f, 1f, L_OBJECT, 0f, 0);
        gb.end();
        ch.boxes.add(new Box(x - 0.3f, y - 1f, z - 0.25f, x + 0.3f, y + 1.3f, z + 0.25f));
    }

    /** Low mesh fence: thin posts and a see-through top rail. */
    private void fence(float x0, float z0, float x1, float z1) {
        gb.begin(false);
        float[][] e = {{x0, z0, x1, z0}, {x1, z0, x1, z1}, {x1, z1, x0, z1}, {x0, z1, x0, z0}};
        for (float[] q : e) {
            float len = (float) Math.hypot(q[2] - q[0], q[3] - q[1]);
            for (float s = 0; s <= len; s += 4f) {
                float t = s / len, x = q[0] + (q[2] - q[0]) * t, z = q[1] + (q[3] - q[1]) * t, y = city.terrain.height(x, z);
                gb.box(x - 0.04f, y - 0.2f, z - 0.04f, x + 0.04f, y + 1.8f, z + 0.04f, lib.pole, lib.pole, 1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
            }
            // the mesh between the posts is solid, in 4 m pieces that follow the ground
            for (float s = 0; s < len; s += 4f) {
                float e0 = Math.min(len, s + 4f);
                float ax = q[0] + (q[2] - q[0]) * s / len, az = q[1] + (q[3] - q[1]) * s / len;
                float bx = q[0] + (q[2] - q[0]) * e0 / len, bz = q[1] + (q[3] - q[1]) * e0 / len;
                float ya = city.terrain.height(ax, az), yb = city.terrain.height(bx, bz);
                ch.boxes.add(new Box(Math.min(ax, bx) - 0.06f, Math.min(ya, yb) - 1f, Math.min(az, bz) - 0.06f,
                        Math.max(ax, bx) + 0.06f, Math.max(ya, yb) + 1.8f, Math.max(az, bz) + 0.06f));
            }
        }
        gb.end();
    }

    // ------------------------------------------------------------------ shrine

    /** Gravel precinct, red torii on the street, a small hall with a tiled roof, cedars around. */
    private void shrine(float x0, float z0, float x1, float z1, int face) {
        float cx = (x0 + x1) * 0.5f;
        float tz = face == 0 ? z0 + 2f : z1 - 2f;
        torii(cx, tz, 4.2f, 4.6f);
        // the hall at the back of the precinct
        float hw = 4f, hd = 3.5f;
        float hz = face == 0 ? z1 - 4f - hd : z0 + 4f + hd;
        boolean[] street = new boolean[4];
        street[face] = true;
        new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.SHRINE_HALL, cx - hw, hz - hd, cx + hw, hz + hd, street, new boolean[4], city.shopsSuburb);
        // stone lanterns along the approach
        gb.begin(false);
        for (int k = 1; k <= 2; k++) {
            float z = tz + (hz - tz) * k / 3f;
            for (int sd = -1; sd <= 1; sd += 2) {
                float x = cx + sd * 2.2f, y = city.terrain.height(x, z);
                gb.box(x - 0.25f, y, z - 0.25f, x + 0.25f, y + 1.2f, z + 0.25f, lib.concrete, lib.concrete, 1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
                gb.box(x - 0.4f, y + 1.2f, z - 0.4f, x + 0.4f, y + 1.45f, z + 0.4f, lib.concrete, lib.concrete, 1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
            }
        }
        gb.end();
        // tall cedars around the precinct
        for (float x = x0 + 2f; x < x1; x += 7f) {
            city.plantAtTerrain(ch, Flora.CEDAR, x, face == 0 ? z1 - 1.5f : z0 + 1.5f);
            if (Math.abs(x - cx) > 5f) city.plantAtTerrain(ch, Flora.CEDAR, x, face == 0 ? z0 + 1.5f : z1 - 1.5f);
        }
        for (float z = z0 + 6f; z < z1 - 4f; z += 7f) {
            city.plantAtTerrain(ch, Flora.CEDAR, x0 + 1.5f, z);
            city.plantAtTerrain(ch, Flora.CEDAR, x1 - 1.5f, z);
        }
    }

    /** A torii across the approach at (x, z): two posts, the tie beam and the curved top beam. */
    private void torii(float x, float z, float span, float h) {
        float y = city.terrain.height(x, z);
        int red = lib.paintRed;
        gb.begin(false);
        for (int s = -1; s <= 1; s += 2) {
            float px = x + s * span * 0.5f;
            gb.box(px - 0.18f, y - 0.3f, z - 0.18f, px + 0.18f, y + h, z + 0.18f, red, red, 1f, 1f, L_OBJECT, 0f, 0);
            ch.boxes.add(new Box(px - 0.2f, y - 1f, z - 0.2f, px + 0.2f, y + h, z + 0.2f));
        }
        gb.box(x - span * 0.5f - 0.3f, y + h - 1.0f, z - 0.12f, x + span * 0.5f + 0.3f, y + h - 0.75f, z + 0.12f, red, red, 1f, 1f, L_OBJECT, 0f, 0);
        gb.box(x - span * 0.5f - 0.8f, y + h, z - 0.22f, x + span * 0.5f + 0.8f, y + h + 0.3f, z + 0.22f, red, red, 1f, 1f, L_OBJECT, 0f, 0);
        gb.box(x - span * 0.5f - 0.9f, y + h + 0.3f, z - 0.25f, x + span * 0.5f + 0.9f, y + h + 0.45f, z + 0.25f, lib.roofTar, lib.roofTar, 1f, 1f, L_OBJECT, 0f, 0);
        gb.end();
    }

    // ------------------------------------------------------------------ harbour

    /**
     * Fishing harbour: two concrete breakwaters reaching out to sea from either side of the quay,
     * fishing boats moored between them, and net sheds on the quay. (qx, qz) is the end of the
     * harbour road, (sx, sz) the seaward axis, (cx, cz) the water's edge.
     */
    void harbour(float qx, float qz, float sx, float sz, float cx, float cz) {
        float px = -sz, pz = sx;                      // along the shore
        gb.begin(true);
        for (int side = -1; side <= 1; side += 2) {
            float off = side * 28f, len = side < 0 ? 95f : 70f;
            float ax = cx + px * off - sx * 8f, az = cz + pz * off - sz * 8f;
            float bx = ax + sx * len + px * 3.5f, bz = az + sz * len + pz * 3.5f;
            box(ax - px * 3.5f, az - pz * 3.5f, bx, bz, -4f, 1.6f, lib.concrete, lib.concrete);
            // the harbour mouth: breakwater heads turn in across the entrance
            float hx = bx - px * side * 3.5f, hz = bz - pz * side * 3.5f;
            box(hx, hz, hx - px * side * 14f - sx * 5f, hz - pz * side * 14f - sz * 5f, -4f, 1.6f, lib.concrete, lib.concrete);
        }
        // fishing boats: white hulls with a wheelhouse, moored inside the harbour
        for (int k = 0; k < 3; k++) {
            float along = 22f + k * 14f, across = -14f + k * 12f;
            float bx = cx + sx * along + px * across, bz = cz + sz * along + pz * across;
            float hl = 4.5f, hw = 1.4f;
            float x0 = bx - Math.abs(sx) * hl - Math.abs(px) * hw, x1 = bx + Math.abs(sx) * hl + Math.abs(px) * hw;
            float z0 = bz - Math.abs(sz) * hl - Math.abs(pz) * hw, z1 = bz + Math.abs(sz) * hl + Math.abs(pz) * hw;
            gb.box(x0, -0.4f, z0, x1, 0.9f, z1, lib.paintWhite, lib.metal, 1f, 1f, L_OBJECT, 0f, 0);
            float cx0 = bx - Math.abs(sx) * 1.2f - Math.abs(px) * 0.9f, cx1 = bx + Math.abs(sx) * 0.6f + Math.abs(px) * 0.9f;
            float cz0 = bz - Math.abs(sz) * 1.2f - Math.abs(pz) * 0.9f, cz1 = bz + Math.abs(sz) * 0.6f + Math.abs(pz) * 0.9f;
            gb.box(cx0, 0.9f, cz0, cx1, 2.4f, cz1, lib.paintWhite, lib.paintRed, 1f, 1f, L_OBJECT, 0f, 0);
            ch.boxes.add(new Box(x0, -1f, z0, x1, 2.4f, z1));
        }
        gb.end();
        // net sheds on the quay
        boolean[] street = new boolean[4];
        int face = sx > 0 ? BuildingGen.E : (sx < 0 ? BuildingGen.W : (sz > 0 ? BuildingGen.S : BuildingGen.N));
        street[face] = true;
        for (int side = -1; side <= 1; side += 2) {
            float mx = qx - sx * 14f + px * side * 18f, mz = qz - sz * 14f + pz * side * 18f;
            if (city.terrain.coastOver(mx, mz) > -8f) continue;
            float hx = Math.abs(px) * 6f + Math.abs(sx) * 5f, hz = Math.abs(pz) * 6f + Math.abs(sz) * 5f;
            new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.SHED, mx - hx, mz - hz, mx + hx, mz + hz, street, new boolean[4], city.shopsSuburb);
        }
    }

    /** Axis-aligned box spanning two corners (any order). */
    private void box(float ax, float az, float bx, float bz, float y0, float y1, int side, int top) {
        float x0 = Math.min(ax, bx), x1 = Math.max(ax, bx), z0 = Math.min(az, bz), z1 = Math.max(az, bz);
        gb.box(x0, y0, z0, x1, y1, z1, side, top, 0.5f, 0.5f, L_OBJECT, 0f, 0);
        ch.boxes.add(new Box(x0, y0, z0, x1, y1, z1));
    }

    // ------------------------------------------------------------------ petrol station

    private void gasStation(float x0, float z0, float x1, float z1, int face) {
        float cx = (x0 + x1) * 0.5f;
        float fz0 = face == 0 ? z0 + 2f : z1 - 13f, fz1 = fz0 + 11f;
        float y = Math.max(city.terrain.height(cx - 7f, fz0), city.terrain.height(cx + 7f, fz1));
        gb.begin(true);
        // canopy on four posts
        for (int k = 0; k < 4; k++) {
            float px = cx + ((k & 1) == 0 ? -5f : 5f), pz = (k & 2) == 0 ? fz0 + 2f : fz1 - 2f;
            gb.box(px - 0.2f, y - 0.3f, pz - 0.2f, px + 0.2f, y + 5f, pz + 0.2f, lib.concrete, lib.concrete, 1f, 1f, L_OBJECT, 0f, 0);
            ch.boxes.add(new Box(px - 0.25f, y - 1f, pz - 0.25f, px + 0.25f, y + 5f, pz + 0.25f));
        }
        gb.box(cx - 7f, y + 5f, fz0, cx + 7f, y + 5.9f, fz1, lib.canopy, lib.metal, 1f / 14f, 1f / 0.9f, L_OBJECT, 0f, 0);
        // pump islands
        for (int s = -1; s <= 1; s += 2) {
            float px = cx + s * 2.6f, pz0 = fz0 + 3.5f, pz1 = fz1 - 3.5f;
            gb.box(px - 0.5f, y, pz0, px + 0.5f, y + 0.2f, pz1, lib.concrete, lib.concrete, 1f, 1f, L_OBJECT, 0f, 0);
            gb.box(px - 0.3f, y + 0.2f, (pz0 + pz1) * 0.5f - 0.4f, px + 0.3f, y + 1.7f, (pz0 + pz1) * 0.5f + 0.4f, lib.metal, lib.paintRed, 1f, 1f, L_OBJECT, 0f, 0);
            ch.boxes.add(new Box(px - 0.5f, y - 1f, pz0, px + 0.5f, y + 1.7f, pz1));
        }
        gb.end();
        // kiosk at the back
        float kz0 = face == 0 ? fz1 + 1f : fz0 - 7f, kz1 = kz0 + 6f;
        boolean[] street = new boolean[4];
        street[face] = true;
        if (kz1 <= Math.max(z0, z1) && kz0 >= Math.min(z0, z1))
            new BuildingGen(city, lib, gb, r, ch).build(BuildingGen.Type.KONBINI, cx - 5f, kz0, cx + 5f, kz1, street, street, new int[]{lib.lobbies[1]});
        // price sign on a pole at the street corner
        float sx = x0 + 1.5f, sz = face == 0 ? z0 + 0.8f : z1 - 0.8f, sy = city.terrain.height(sx, sz);
        gb.begin(false);
        gb.box(sx - 0.1f, sy, sz - 0.1f, sx + 0.1f, sy + 5f, sz + 0.1f, lib.pole, lib.pole, 1f, 1f, L_OBJECT, 0f, 0);
        gb.wall(sx + 1.2f, sz, sx - 1.2f, sz, sy + 5f, sy + 5.6f, lib.signGas, 0f, 1f, 0f, 1f, L_OBJECT, -0.2f, 1f);
        gb.wall(sx - 1.2f, sz + 0.05f, sx + 1.2f, sz + 0.05f, sy + 5f, sy + 5.6f, lib.signGas, 1f, 0f, 0f, 1f, L_OBJECT, -0.2f, 1f);
        gb.end();
        ch.boxes.add(new Box(sx - 0.15f, sy - 1f, sz - 0.15f, sx + 0.15f, sy + 5f, sz + 0.15f));
    }
}
