package com.harlequinxd.drivehard.core.city;

import com.harlequinxd.drivehard.core.game.Box;
import com.harlequinxd.drivehard.core.render.Geo;
import com.harlequinxd.drivehard.core.tex.TextureLibrary;

import java.util.ArrayList;
import java.util.List;

import static com.harlequinxd.drivehard.core.render.SoftRenderer.L_OBJECT;

/**
 * Elevated commuter line running east-west through the middle block row of the city: a concrete
 * viaduct on pillars with two tracks, three stations with side platforms, canopies and name
 * boards, ground-level station buildings, and a small train simulation.
 *
 * Trains keep to the left like Japanese roads: eastbound on the north track, westbound on the south.
 */
public final class Railway {
    public static final int CARS = 4;
    public static final float CAR_LEN = 13f, CAR_W = 2.9f;
    public static final float TRAIN_LEN = CARS * CAR_LEN;

    private final City city;
    private final TextureLibrary lib;
    public final float zc, deckY, xs, xe;
    public final int[] stationBlock;
    public final float[] stationX;
    public final List<Train> trains = new ArrayList<>();

    private static final float DECK_HALF = 5f, STATION_HALF = 11.5f, DECK_THICK = 1.3f;
    private static final float PLATFORM_H = 1.0f;

    Railway(City city) {
        this.city = city;
        this.lib = city.lib;
        int j = city.jRail;
        zc = (city.rz1[j] + city.rz0[j + 1]) * 0.5f;
        xs = city.rx1[0] + 1.5f;
        xe = city.rx0[city.nbx] - 1.5f;
        float max = -1e9f;
        for (float x = xs - 10f; x <= xe + 10f; x += 4f)
            for (float dz = -STATION_HALF; dz <= STATION_HALF; dz += 5.75f) max = Math.max(max, city.terrain.height(x, zc + dz));
        deckY = max + 7.5f;
        stationBlock = new int[]{1, city.nbx / 2, city.nbx - 2};
        stationX = new float[stationBlock.length];
        for (int k = 0; k < stationBlock.length; k++)
            stationX[k] = (city.rx1[stationBlock[k]] + city.rx0[stationBlock[k] + 1]) * 0.5f;
        Train a = new Train(trainGeo(lib.trainSideA, lib.trainFrontA), 0, +1);
        Train b = new Train(trainGeo(lib.trainSideB, lib.trainFrontB), stationX.length - 1, -1);
        b.dwell = 7f;
        trains.add(a);
        trains.add(b);
    }

    /** Station index whose platforms sit over block column i, or -1. */
    public int stationForBlock(int i) {
        for (int k = 0; k < stationBlock.length; k++) if (stationBlock[k] == i) return k;
        return -1;
    }

    private int stationAt(float x) {
        for (int k = 0; k < stationBlock.length; k++) {
            int i = stationBlock[k];
            if (x >= city.rx1[i] && x <= city.rx0[i + 1]) return k;
        }
        return -1;
    }

    private boolean overRoad(float x) {
        for (int i = 0; i <= city.nbx; i++) if (x > city.rx0[i] - 1.5f && x < city.rx1[i] + 1.5f) return true;
        return false;
    }

    // ------------------------------------------------------------------ static geometry

    void generate(Chunk ch, GeoBuilder gb, float x0, float z0, float x1, float z1) {
        if (zc + STATION_HALF < z0 || zc - STATION_HALF >= z1) return;
        float a = Math.max(x0, xs), b = Math.min(x1, xe);
        if (b <= a) return;
        gb.begin(false);
        // deck in 8 m pieces aligned to world x so neighbouring chunks meet exactly
        for (float x = (float) Math.floor(a / 8f) * 8f; x < b; x += 8f) {
            float pa = Math.max(x, a), pb = Math.min(x + 8f, b);
            if (pb - pa < 0.01f) continue;
            int st = stationAt((pa + pb) * 0.5f);
            float hw = st >= 0 ? STATION_HALF : DECK_HALF;
            deckPiece(gb, pa, pb, hw, st >= 0);
        }
        // pillars every 16 m (not on roads, not inside station buildings)
        for (float x = (float) Math.ceil(a / 16f) * 16f; x < b; x += 16f) {
            if (overRoad(x) || stationAt(x) >= 0) continue;
            float g = city.terrain.height(x, zc);
            float top = deckY - DECK_THICK;
            gb.box(x - 1f, g - 0.3f, zc - 1.6f, x + 1f, top, zc + 1.6f, lib.concrete, -1, 0.5f, 0.5f, L_OBJECT, 0f, 0);
            gb.box(x - 1.2f, top - 0.8f, zc - DECK_HALF + 0.6f, x + 1.2f, top, zc + DECK_HALF - 0.6f, lib.concrete, -1, 0.5f, 0.5f, L_OBJECT, 0f, 0);
            ch.boxes.add(new Box(x - 1f, g - 1f, zc - 1.6f, x + 1f, top, zc + 1.6f));
        }
        // buffer stops at both ends
        if (xs >= a && xs < b) bufferStop(gb, xs + 0.5f);
        if (xe >= a && xe < b) bufferStop(gb, xe - 1.5f);
        gb.end();
    }

    private void deckPiece(GeoBuilder gb, float pa, float pb, float hw, boolean station) {
        float top = deckY, bot = deckY - DECK_THICK;
        float zn = zc - hw, zs = zc + hw;
        // deck surface between the platforms (whole deck outside stations)
        float inner = station ? 3.7f : hw;
        gb.floor(pa, zc - inner, pb, zc + inner, top, lib.concrete, 2f, 2f, L_OBJECT, GeoBuilder.sunLight(0, 1, 0), 0.2f);
        for (int t = -1; t <= 1; t += 2) {
            float tz = zc + 2f * t;
            float[] p = {pa, top + 0.02f, tz + 1.3f, pb, top + 0.02f, tz + 1.3f, pb, top + 0.02f, tz - 1.3f, pa, top + 0.02f, tz - 1.3f};
            // track texture runs its rails along v, so u spans the sleeper width
            float[] uv = {0f, pa / 2.6f, 0f, pb / 2.6f, 1f, pb / 2.6f, 1f, pa / 2.6f};
            float l = GeoBuilder.sunLight(0, 1, 0);
            gb.quad(p, uv, new float[]{l, l, l, l}, lib.track, L_OBJECT, Geo.FLAG_NO_SUBDIV, -0.15f, false);
        }
        // underside
        float dl = 0.55f;
        gb.quad(new float[]{pa, bot, zn, pb, bot, zn, pb, bot, zs, pa, bot, zs},
                new float[]{pa / 2f, zn / 2f, pb / 2f, zn / 2f, pb / 2f, zs / 2f, pa / 2f, zs / 2f},
                new float[]{dl, dl, dl, dl}, lib.concrete, L_OBJECT, 0, 0f, false);
        // outer faces + parapets
        float para = station ? top + PLATFORM_H + 1.1f : top + 1.1f;
        gb.wall(pb, zn, pa, zn, bot, para, lib.concrete, pb / 2f, pa / 2f, 0f, (para - bot) / 2f, L_OBJECT, 0f, 1f);
        gb.wall(pa, zs, pb, zs, bot, para, lib.concrete, pa / 2f, pb / 2f, 0f, (para - bot) / 2f, L_OBJECT, 0f, 1f);
        if (!station) {
            gb.wall(pa, zn + 0.25f, pb, zn + 0.25f, top, para, lib.concrete, pa / 2f, pb / 2f, 0f, 0.5f, L_OBJECT, 0f, 0.9f);
            gb.wall(pb, zs - 0.25f, pa, zs - 0.25f, top, para, lib.concrete, pb / 2f, pa / 2f, 0f, 0.5f, L_OBJECT, 0f, 0.9f);
            gb.floor(pa, zn, pb, zn + 0.25f, para, lib.concrete, 2f, 2f, L_OBJECT, 1f, 0f);
            gb.floor(pa, zs - 0.25f, pb, zs, para, lib.concrete, 2f, 2f, L_OBJECT, 1f, 0f);
            return;
        }
        // side platforms with an edge face, canopy, columns and name boards
        float py = top + PLATFORM_H;
        float pl = GeoBuilder.sunLight(0, 1, 0);
        gb.floor(pa, zn, pb, zc - 3.7f, py, lib.platform, 2f, 2f, L_OBJECT, pl, 0f);
        gb.floor(pa, zc + 3.7f, pb, zs, py, lib.platform, 2f, 2f, L_OBJECT, pl, 0f);
        gb.wall(pa, zc - 3.7f, pb, zc - 3.7f, top, py, lib.concrete, pa / 2f, pb / 2f, 0f, 0.5f, L_OBJECT, 0f, 0.8f);
        gb.wall(pb, zc + 3.7f, pa, zc + 3.7f, top, py, lib.concrete, pb / 2f, pa / 2f, 0f, 0.5f, L_OBJECT, 0f, 0.8f);
        gb.wall(pa, zn + 0.2f, pb, zn + 0.2f, py, para, lib.concrete, pa / 2f, pb / 2f, 0f, 0.5f, L_OBJECT, 0f, 0.9f);
        gb.wall(pb, zs - 0.2f, pa, zs - 0.2f, py, para, lib.concrete, pb / 2f, pa / 2f, 0f, 0.5f, L_OBJECT, 0f, 0.9f);
        float cy = py + 3.6f;
        float l = 0.75f;
        for (int side = -1; side <= 1; side += 2) {
            float za = side < 0 ? zn : zc + 3.4f, zb = side < 0 ? zc - 3.4f : zs;
            // canopy: top face and dimmer underside
            gb.quad(new float[]{pa, cy, zb, pb, cy, zb, pb, cy, za, pa, cy, za},
                    new float[]{pa / 2f, zb / 2f, pb / 2f, zb / 2f, pb / 2f, za / 2f, pa / 2f, za / 2f},
                    new float[]{1f, 1f, 1f, 1f}, lib.metal, L_OBJECT, 0, 0f, false);
            gb.quad(new float[]{pa, cy - 0.05f, za, pb, cy - 0.05f, za, pb, cy - 0.05f, zb, pa, cy - 0.05f, zb},
                    new float[]{pa / 2f, za / 2f, pb / 2f, za / 2f, pb / 2f, zb / 2f, pa / 2f, zb / 2f},
                    new float[]{l, l, l, l}, lib.roofMembrane, L_OBJECT, 0, 0f, false);
            float colZ = side < 0 ? zc - 7.6f : zc + 7.6f;
            float cx = (float) Math.floor(pa / 8f) * 8f + 4f;
            if (cx >= pa && cx < pb)
                gb.box(cx - 0.15f, py, colZ - 0.15f, cx + 0.15f, cy, colZ + 0.15f, lib.pole, -1, 1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
        }
        // name boards on posts, facing the tracks
        float mid = (float) Math.floor(pa / 16f) * 16f + 8f;
        if (mid - 2f >= pa && mid + 2f <= pb) {
            int st = stationAt(mid);
            int sign = lib.stationSigns[Math.max(0, st)];
            float y0 = py + 1.9f, y1 = py + 2.9f;
            float zN = zc - 6.2f, zS = zc + 6.2f;
            gb.wall(mid - 2f, zN, mid + 2f, zN, y0, y1, sign, 0f, 1f, 0f, 1f, L_OBJECT, -0.1f, 1f);
            gb.wall(mid + 2f, zN - 0.06f, mid - 2f, zN - 0.06f, y0, y1, lib.metal, 0f, 1f, 0f, 1f, L_OBJECT, 0f, 1f);
            gb.wall(mid + 2f, zS, mid - 2f, zS, y0, y1, sign, 0f, 1f, 0f, 1f, L_OBJECT, -0.1f, 1f);
            gb.wall(mid - 2f, zS + 0.06f, mid + 2f, zS + 0.06f, y0, y1, lib.metal, 0f, 1f, 0f, 1f, L_OBJECT, 0f, 1f);
            for (int s = -1; s <= 1; s += 2) {
                float px = mid + s * 1.8f;
                gb.box(px - 0.05f, py, zN - 0.08f, px + 0.05f, y0, zN - 0.02f, lib.pole, -1, 1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
                gb.box(px - 0.05f, py, zS + 0.02f, px + 0.05f, y0, zS + 0.08f, lib.pole, -1, 1f, 1f, L_OBJECT, 0f, Geo.FLAG_DETAIL);
            }
        }
    }

    private void bufferStop(GeoBuilder gb, float x) {
        for (int t = -1; t <= 1; t += 2) {
            float tz = zc + 2f * t;
            gb.box(x, deckY, tz - 1.2f, x + 1f, deckY + 1.1f, tz + 1.2f, lib.paintYellow, lib.paintYellow, 1f, 1f, L_OBJECT, 0f, 0);
        }
    }

    /** Ground-level station hall under the platforms, with glass entrances and name boards on both street sides. */
    void stationBuilding(Chunk ch, GeoBuilder gb, int station, float x0, float z0, float x1, float z1, int[] shopSet) {
        gb.begin(true);
        float g = Math.max(Math.max(city.terrain.height(x0, z0), city.terrain.height(x1, z0)),
                Math.max(city.terrain.height(x0, z1), city.terrain.height(x1, z1))) + City.CURB;
        float top = deckY - DECK_THICK;
        float glass = g + 4.2f;
        int lobby = lib.lobbies[station % lib.lobbies.length];
        int sign = lib.stationSigns[station];
        float w = x1 - x0;
        // street faces: glass hall on the ground floor, concrete band above with the station name
        gb.wallGrid(x1, z0, x0, z0, g - 0.3f, glass, lobby, 1f / 6f, 1f / 4.5f, 6f, 4.5f, L_OBJECT, 0f, false);
        gb.wallGrid(x0, z1, x1, z1, g - 0.3f, glass, lobby, 1f / 6f, 1f / 4.5f, 6f, 4.5f, L_OBJECT, 0f, false);
        gb.wall(x1, z0, x0, z0, glass, top, lib.concrete, w / 2f, 0f, 0f, (top - glass) / 2f, L_OBJECT, 0f, 1f);
        gb.wall(x0, z1, x1, z1, glass, top, lib.concrete, 0f, w / 2f, 0f, (top - glass) / 2f, L_OBJECT, 0f, 1f);
        float cx = (x0 + x1) * 0.5f, sy0 = glass + 0.2f, sy1 = Math.min(top - 0.1f, glass + 1.5f);
        gb.wall(cx + 2.6f, z0 - 0.05f, cx - 2.6f, z0 - 0.05f, sy0, sy1, sign, 0f, 1f, 0f, 1f, L_OBJECT, -0.2f, 1f);
        gb.wall(cx - 2.6f, z1 + 0.05f, cx + 2.6f, z1 + 0.05f, sy0, sy1, sign, 0f, 1f, 0f, 1f, L_OBJECT, -0.2f, 1f);
        // ends: shops on the west/east faces
        int[] set = shopSet != null && shopSet.length > 0 ? shopSet : lib.shops;
        float d = z1 - z0;
        int n = Math.max(1, (int) (d / 6f));
        for (int k = 0; k < n; k++) {
            float za = z0 + d * k / n, zb = z0 + d * (k + 1) / n;
            int s1 = set[(station * 7 + k) % set.length], s2 = set[(station * 11 + k * 3 + 1) % set.length];
            gb.wall(x0, za, x0, zb, g - 0.3f, g + 4.2f, s1, 0f, 1f, 0f, 1f, L_OBJECT, 0f, 1f);
            gb.wall(x1, zb, x1, za, g - 0.3f, g + 4.2f, s2, 0f, 1f, 0f, 1f, L_OBJECT, 0f, 1f);
        }
        gb.wall(x0, z0, x0, z1, g + 4.2f, top, lib.concrete, 0f, d / 2f, 0f, (top - g - 4.2f) / 2f, L_OBJECT, 0f, 1f);
        gb.wall(x1, z1, x1, z0, g + 4.2f, top, lib.concrete, 0f, d / 2f, 0f, (top - g - 4.2f) / 2f, L_OBJECT, 0f, 1f);
        gb.end();
        ch.boxes.add(new Box(x0, g - 1f, z0, x1, top, z1));
    }

    // ------------------------------------------------------------------ trains

    private Geo trainGeo(int side, int front) {
        Geo g = new Geo();
        float y0 = 0.55f, y1 = 3.75f, hz = CAR_W * 0.5f;
        float sl = GeoBuilder.sunLight(0, 0, 1), nl = GeoBuilder.sunLight(0, 0, -1);
        float el = GeoBuilder.sunLight(1, 0, 0), wl = GeoBuilder.sunLight(-1, 0, 0), tl = GeoBuilder.sunLight(0, 1, 0);
        float x0 = -TRAIN_LEN * 0.5f;
        for (int c = 0; c < CARS; c++) {
            float ca = x0 + c * CAR_LEN + 0.25f, cb = ca + CAR_LEN - 0.5f;
            int pieces = 3;
            for (int k = 0; k < pieces; k++) {
                float a = ca + (cb - ca) * k / pieces, b = ca + (cb - ca) * (k + 1) / pieces;
                // south side (+Z): A=(a) -> B=(b)
                g.add(new float[]{a, y0, hz, b, y0, hz, b, y1, hz, a, y1, hz},
                        new float[]{0, 1, 1, 1, 1, 0, 0, 0}, new float[]{sl, sl, sl, sl}, side, L_OBJECT, 0, 0f, false);
                // north side (-Z): A=(b) -> B=(a)
                g.add(new float[]{b, y0, -hz, a, y0, -hz, a, y1, -hz, b, y1, -hz},
                        new float[]{0, 1, 1, 1, 1, 0, 0, 0}, new float[]{nl, nl, nl, nl}, side, L_OBJECT, 0, 0f, false);
                // roof
                g.add(new float[]{a, y1, hz, b, y1, hz, b, y1, -hz, a, y1, -hz},
                        new float[]{0, 0, 1, 0, 1, 1, 0, 1}, new float[]{tl, tl, tl, tl}, lib.metal, L_OBJECT, 0, 0f, false);
                // dark underframe
                g.add(new float[]{a, 0.1f, hz - 0.2f, b, 0.1f, hz - 0.2f, b, y0, hz - 0.2f, a, y0, hz - 0.2f},
                        new float[]{0, 1, 1, 1, 1, 0, 0, 0}, new float[]{0.5f, 0.5f, 0.5f, 0.5f}, lib.pole, L_OBJECT, 0, 0.05f, false);
                g.add(new float[]{b, 0.1f, -hz + 0.2f, a, 0.1f, -hz + 0.2f, a, y0, -hz + 0.2f, b, y0, -hz + 0.2f},
                        new float[]{0, 1, 1, 1, 1, 0, 0, 0}, new float[]{0.5f, 0.5f, 0.5f, 0.5f}, lib.pole, L_OBJECT, 0, 0.05f, false);
            }
            int eastTex = c == CARS - 1 ? front : lib.metal, westTex = c == 0 ? front : lib.metal;
            // east end (+X)
            g.add(new float[]{cb, y0, hz, cb, y0, -hz, cb, y1, -hz, cb, y1, hz},
                    new float[]{0, 1, 1, 1, 1, 0, 0, 0}, new float[]{el, el, el, el}, eastTex, L_OBJECT, 0, 0f, false);
            // west end (-X)
            g.add(new float[]{ca, y0, -hz, ca, y0, hz, ca, y1, hz, ca, y1, -hz},
                    new float[]{0, 1, 1, 1, 1, 0, 0, 0}, new float[]{wl, wl, wl, wl}, westTex, L_OBJECT, 0, 0f, false);
        }
        return g;
    }

    public final class Train {
        public final Geo geo;
        public float x, z, speed, dwell;
        public int dir, next;

        Train(Geo geo, int station, int dir) {
            this.geo = geo;
            this.x = stationX[station];
            this.dir = dir;
            this.next = station;
            this.dwell = 12f;
            this.z = track(dir);
        }

        public float y() { return deckY + 0.05f; }

        /** Bounds for culling / collision. */
        public float halfLen() { return TRAIN_LEN * 0.5f; }
    }

    private float track(int dir) { return dir > 0 ? zc - 2f : zc + 2f; }

    public void update(float dt) {
        final float acc = 1.0f, brake = 1.1f, vmax = 18f;
        for (Train t : trains) {
            if (t.dwell > 0f) {
                t.dwell -= dt;
                if (t.dwell <= 0f) {
                    int k = t.next;
                    if (k == 0) t.dir = 1;
                    else if (k == stationX.length - 1) t.dir = -1;
                    t.next = k + t.dir;
                    t.z = track(t.dir);
                }
                continue;
            }
            float target = stationX[t.next];
            float dist = (target - t.x) * t.dir;
            if (dist <= 0.05f) {
                t.x = target; t.speed = 0f; t.dwell = 15f;
                continue;
            }
            float stopDist = t.speed * t.speed / (2f * brake);
            if (stopDist >= dist - 0.2f) t.speed -= Math.min(t.speed, t.speed * t.speed / (2f * Math.max(dist, 0.05f)) * dt);
            else t.speed = Math.min(vmax, t.speed + acc * dt);
            if (t.speed < 0.6f && dist > 0.05f) t.speed = Math.min(0.6f, dist / Math.max(dt, 1e-3f));
            t.x += Math.min(dist, t.speed * dt) * t.dir;
        }
    }
}
