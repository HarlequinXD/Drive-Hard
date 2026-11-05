package com.harlequinxd.drivehard.core.ui;

import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.map.MapData;
import com.harlequinxd.drivehard.core.road.RoadClass;
import com.harlequinxd.drivehard.core.tex.PixelFont;

import java.util.ArrayList;
import java.util.List;

/**
 * The map screens, drawn in software straight into the game's frame buffer so desktop and
 * Android look identical: a heading-up circular minimap for the HUD and the full island map with
 * pan, zoom, place names, a destination list and the fast-travel dialog. The front ends only feed
 * it input (pan in pixels, zoom steps, taps/clicks, next/previous destination, confirm, cancel).
 */
public final class MapView {
    // ------------------------------------------------------------------ full map state
    public float viewX, viewZ, zoom = 2.2f, cursorX, cursorZ;
    public MapData.Label selected;
    /** The fast-travel dialog is up for {@link #selected}. */
    public boolean dialog;
    /** Platform specific control hints for the footer ("A SELECT  B BACK ..."). */
    public String hints = "";
    public static final float MIN_ZOOM = 0.8f, MAX_ZOOM = 48f;
    public static final int HEADER = 20, FOOTER = 18;
    private final List<MapData.Label> places = new ArrayList<>();
    private int placeIndex = -1;

    // layout of the clickable parts, refreshed every draw: {x0, y0, x1, y1}
    public final int[] zoomInBox = new int[4], zoomOutBox = new int[4], travelBox = new int[4], cancelBox = new int[4];
    public final int[] prevBox = new int[4], nextBox = new int[4], closeBox = new int[4], centreBox = new int[4];

    private final List<Integer> roadIds = new ArrayList<>();
    private final List<int[]> taken = new ArrayList<>();

    private boolean overlaps(int x0, int y0, int x1, int y1) {
        for (int[] b : taken) if (x0 < b[2] && x1 > b[0] && y0 < b[3] && y1 > b[1]) return true;
        return false;
    }
    private boolean[] roadMarks;
    private int[] fb;
    private int w, h, clipX0, clipY0, clipX1, clipY1;
    private long frame;

    /** Opens the map centred on the player. */
    public void open(Game g) {
        viewX = cursorX = g.mapX();
        viewZ = cursorZ = g.mapZ();
        zoom = 6f;
        selected = null;
        dialog = false;
        placeIndex = -1;
        places.clear();
        for (MapData.Label l : g.map.labels) if (l.rank <= 3) places.add(l);
        final float px = g.mapX(), pz = g.mapZ();
        places.sort((a, b) -> Float.compare(dist2(a, px, pz), dist2(b, px, pz)));
    }

    private static float dist2(MapData.Label l, float x, float z) {
        float dx = l.x - x, dz = l.z - z;
        return dx * dx + dz * dz;
    }

    private float mapH(int screenH) { return screenH - HEADER - FOOTER; }

    /** Screen pixels per metre for a screen of height h. */
    public float scale(MapData m, int screenH) { return mapH(screenH) * zoom / m.extent; }

    public void pan(Game g, float dxPx, float dyPx, int screenH) {
        float s = scale(g.map, screenH);
        viewX -= dxPx / s;
        viewZ -= dyPx / s;
        clampView(g.map);
        cursorX = viewX;
        cursorZ = viewZ;
    }

    /** Moves the cursor (and the view with it) by a stick / key amount per second. */
    public void steer(Game g, float ax, float az, float dt, int screenH) {
        float speed = 170f / scale(g.map, screenH) * dt;
        viewX += ax * speed;
        viewZ += az * speed;
        clampView(g.map);
        cursorX = viewX;
        cursorZ = viewZ;
    }

    public void zoomBy(float factor) {
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
    }

    private void clampView(MapData m) {
        viewX = Math.max(m.x0, Math.min(m.x0 + m.extent, viewX));
        viewZ = Math.max(m.z0, Math.min(m.z0 + m.extent, viewZ));
    }

    /** Next (+1) or previous (-1) place, nearest first; centres the map on it and selects it. */
    public void cycle(int dir) {
        if (places.isEmpty()) return;
        placeIndex = Math.floorMod(placeIndex + dir, places.size());
        selected = places.get(placeIndex);
        viewX = cursorX = selected.x;
        viewZ = cursorZ = selected.z;
        if (zoom < 5f) zoom = 5f;
        dialog = false;
    }

    /** Selects the place under the cursor (for pad / keyboard): true if one was found. */
    public boolean pickAtCursor(Game g, int screenH) {
        MapData.Label l = nearest(g, cursorX, cursorZ, 16f / scale(g.map, screenH));
        if (l == null) return false;
        selected = l;
        dialog = true;
        return true;
    }

    /**
     * A tap or click at screen pixel (x, y): buttons first, then a place near the point.
     * Returns "travel", "cancel", "close", "zoomin", "zoomout", "prev", "next", "centre",
     * "select" or "" (nothing there).
     */
    public String tap(Game g, float x, float y, int screenW, int screenH) {
        if (dialog) {
            if (in(travelBox, x, y)) return "travel";
            if (in(cancelBox, x, y)) { dialog = false; return "cancel"; }
            return "";
        }
        if (in(closeBox, x, y)) return "close";
        if (in(zoomInBox, x, y)) { zoomBy(1.5f); return "zoomin"; }
        if (in(zoomOutBox, x, y)) { zoomBy(1f / 1.5f); return "zoomout"; }
        if (in(prevBox, x, y)) { cycle(-1); return "prev"; }
        if (in(nextBox, x, y)) {
            if (selected != null && placeIndex >= 0 && places.get(placeIndex) == selected) { dialog = true; return "select"; }
            cycle(1);
            return "next";
        }
        if (in(centreBox, x, y)) { viewX = cursorX = g.mapX(); viewZ = cursorZ = g.mapZ(); return "centre"; }
        if (y < HEADER || y > screenH - FOOTER) return "";
        float s = scale(g.map, screenH);
        float wx = viewX + (x - screenW * 0.5f) / s, wz = viewZ + (y - (HEADER + mapH(screenH) * 0.5f)) / s;
        MapData.Label l = nearest(g, wx, wz, 18f / s);
        if (l == null) return "";
        selected = l;
        placeIndex = places.indexOf(l);
        dialog = true;
        return "select";
    }

    private MapData.Label nearest(Game g, float x, float z, float radius) {
        MapData.Label best = null;
        float bd = radius * radius;
        int maxRank = maxRank();
        for (MapData.Label l : g.map.labels) {
            if (l.rank > Math.min(3, maxRank)) continue;
            float d = dist2(l, x, z);
            if (d < bd) { bd = d; best = l; }
        }
        return best;
    }

    private int maxRank() { return zoom < 1.6f ? 1 : zoom < 3.5f ? 2 : 3; }

    private static boolean in(int[] b, float x, float y) {
        return x >= b[0] && x < b[2] && y >= b[1] && y < b[3];
    }

    // ------------------------------------------------------------------ full map drawing

    /** Draws the full map over the whole frame buffer. */
    public void drawFull(int[] fb, int w, int h, Game g) {
        begin(fb, w, h);
        frame++;
        MapData m = g.map;
        float s = scale(m, h);
        int top = HEADER, bottom = h - FOOTER;
        float cx = w * 0.5f, cy = top + (bottom - top) * 0.5f;
        clip(0, top, w, bottom);
        // terrain relief, filtered, with the fine city mask on top when zoomed in
        float inv = 1f / s;
        float rpx = MapData.SIZE / m.extent;
        for (int y = top; y < bottom; y++) {
            float wz = viewZ + (y + 0.5f - cy) * inv;
            for (int x = 0; x < w; x++) {
                float wx = viewX + (x + 0.5f - cx) * inv;
                int c = relief(m, (wx - m.x0) * rpx - 0.5f, (wz - m.z0) * rpx - 0.5f);
                if (s > 0.008f) {
                    int u = m.urbanAt(wx, wz);
                    if (u > 0) c = mix(c, u == 1 ? 0xFF6E6A66 : 0xFF8C8475, 0.75f);
                }
                fb[y * w + x] = mix(c, 0xFF0B1418, 0.30f);
            }
        }
        // a faint grid every 5 km for scale
        float grid = 5000f;
        for (float gx = (float) Math.floor((viewX - cx * inv) / grid) * grid; gx < viewX + cx * inv; gx += grid) {
            int sx = Math.round(cx + (gx - viewX) * s);
            for (int y = top; y < bottom; y += 2) plot(sx, y, 0xFFFFFFFF, 18);
        }
        for (float gz = (float) Math.floor((viewZ - (cy - top) * inv) / grid) * grid; gz < viewZ + (cy - top) * inv; gz += grid) {
            int sy = Math.round(cy + (gz - viewZ) * s);
            for (int x = 0; x < w; x += 2) plot(x, sy, 0xFFFFFFFF, 18);
        }
        // roads: dark casing first, then the fill, minor roads under major ones
        float halfW = cx * inv, halfH = (cy - top) * inv;
        roadIds.clear();
        if (roadMarks == null || roadMarks.length < m.roads.size()) roadMarks = new boolean[m.roads.size()];
        m.roadsIn(viewX - halfW, viewZ - halfH, viewX + halfW, viewZ + halfH, roadIds, roadMarks);
        for (int pass = 0; pass < 2; pass++)
            for (int tier = 0; tier < 4; tier++)
                for (int id : roadIds) {
                    MapData.Road r = m.roads.get(id);
                    if (tier(r.cls) != tier) continue;
                    int width = roadWidth(r.cls, s);
                    if (width <= 0) continue;
                    int col = pass == 0 ? 0xFF101216 : roadColour(r.cls);
                    int wd = pass == 0 ? width + 2 : width;
                    for (int i = 1; i < r.x.length; i++)
                        line(cx + (r.x[i - 1] - viewX) * s, cy + (r.z[i - 1] - viewZ) * s,
                                cx + (r.x[i] - viewX) * s, cy + (r.z[i] - viewZ) * s, col, wd, pass == 0 ? 150 : 255);
                }
        // place names
        int maxRank = maxRank();
        taken.clear();
        for (int pass = 0; pass <= 3; pass++)
            for (MapData.Label l : m.labels) {
                if (l.rank != pass || l.rank > maxRank) continue;
                int sx = Math.round(cx + (l.x - viewX) * s), sy = Math.round(cy + (l.z - viewZ) * s);
                if (sx < -60 || sx > w + 60 || sy < top - 10 || sy > bottom + 10) continue;
                boolean sel = l == selected;
                String name0 = l.name.toUpperCase();
                int sc0 = l.rank == 0 ? 2 : 1;
                int bx0 = sx - 4, by0 = sy - 3 * sc0 - 3, bx1 = sx + 8 + PixelFont.width(name0, sc0) + 2, by1 = sy + 4 * sc0 + 3;
                if (!sel && overlaps(bx0, by0, bx1, by1)) continue;
                taken.add(new int[]{bx0, by0, bx1, by1});
                int dot = l.rank == 0 ? 0xFFFFD34E : l.rank == 1 ? 0xFFFFE9A8 : l.rank == 2 ? 0xFFF4F1E8 : 0xFFC9C4B8;
                int r = l.rank == 0 ? 3 : l.rank <= 2 ? 2 : 1;
                disc(sx, sy, r + 1, 0xFF0A0C10, 255);
                disc(sx, sy, r, sel ? 0xFF53E7F3 : dot, 255);
                if (sel) ring(sx, sy, 6 + (int) (frame / 4 % 4), 0xFF53E7F3, 200);
                String name = l.name.toUpperCase();
                int sc = l.rank == 0 ? 2 : 1;
                int tw = PixelFont.width(name, sc);
                int lx = sx + r + 4, ly = sy - 3 * sc - 1;
                panelRect(lx - 2, ly - 2, lx + tw + 2, ly + 7 * sc + 2, 0xFF07090C, 150);
                text(name, lx, ly, sc, sel ? 0xFF53E7F3 : (l.rank == 0 ? 0xFFFFD34E : 0xFFF4F1E8));
            }
        // you are here, and the cursor
        float ppx = cx + (g.mapX() - viewX) * s, ppy = cy + (g.mapZ() - viewZ) * s;
        if (frame / 8 % 2 == 0) ring(Math.round(ppx), Math.round(ppy), 9, 0xFF53E7F3, 120);
        arrow(ppx, ppy, g.mapHeading(), 7f, true);
        int kx = Math.round(cx + (cursorX - viewX) * s), ky = Math.round(cy + (cursorZ - viewZ) * s);
        for (int k = 3; k <= 8; k++) {
            plot(kx - k, ky, 0xFFFFFFFF, 230); plot(kx + k, ky, 0xFFFFFFFF, 230);
            plot(kx, ky - k, 0xFFFFFFFF, 230); plot(kx, ky + k, 0xFFFFFFFF, 230);
        }
        clip(0, 0, w, h);
        drawLegend(s, bottom);
        drawHeader(g, w);
        drawFooter(h);
        if (dialog && selected != null) drawDialog(g);
    }

    private void drawHeader(Game g, int w) {
        gradientBar(0, 0, w, HEADER, 0xFF0A1016, 0xFF121C24);
        hline(0, HEADER - 1, w, 0xFFE0263A, 255);
        text("MAP", 8, 7, 1, 0xFFFFC870);
        String here = g.placeName.isEmpty() ? "OPEN COUNTRY" : g.placeName.toUpperCase();
        if (!g.regionName.isEmpty() && !g.regionName.equals(g.placeName)) here += "  -  " + g.regionName.toUpperCase();
        text(here, 30, 7, 1, 0xFFE8E4DA);
        // buttons on the right: [<] [>] [CENTRE] [+] [-] [X]
        int x = w - 6;
        x = button(closeBox, x, "X", 0xFFE0263A);
        x = button(zoomOutBox, x - 4, "-", 0xFF2A3640);
        x = button(zoomInBox, x - 2, "+", 0xFF2A3640);
        x = button(centreBox, x - 4, "ME", 0xFF2A3640);
        x = button(nextBox, x - 4, ">", 0xFF2A3640);
        button(prevBox, x - 2, "<", 0xFF2A3640);
    }

    private int button(int[] box, int right, String label, int colour) {
        int bw = Math.max(16, PixelFont.width(label, 1) + 8);
        box[0] = right - bw; box[1] = 2; box[2] = right; box[3] = HEADER - 3;
        panelRect(box[0], box[1], box[2], box[3], colour, 255);
        frameRect(box[0], box[1], box[2], box[3], 0xFF5A6670);
        text(label, box[0] + (bw - PixelFont.width(label, 1)) / 2, box[1] + 5, 1, 0xFFF4F1E8);
        return box[0];
    }

    private void drawFooter(int h) {
        gradientBar(0, h - FOOTER, w, h, 0xFF121C24, 0xFF0A1016);
        hline(0, h - FOOTER, w, 0xFF2A3640, 255);
        text(hints.toUpperCase(), 8, h - FOOTER + 6, 1, 0xFFB9C4C8);
        String z = Math.round(zoom * 100f / 6f) + "%";
        text(z, w - 8 - PixelFont.width(z, 1), h - FOOTER + 6, 1, 0xFFFFC870);
    }

    private void drawLegend(float s, int bottom) {
        // scale bar: a round distance about 50 px long
        float metres = 50f / s;
        float[] steps = {100, 250, 500, 1000, 2000, 5000, 10000, 20000};
        float pick = steps[0];
        for (float st : steps) if (st <= metres) pick = st;
        int len = Math.round(pick * s);
        int x0 = w - 12 - len, y = bottom - 10;
        panelRect(x0 - 6, y - 12, w - 6, y + 5, 0xFF07090C, 170);
        hline(x0, y, len, 0xFFF4F1E8, 255);
        hline(x0, y + 1, len, 0xFFF4F1E8, 255);
        for (int k = -3; k <= 1; k++) { plot(x0, y + k, 0xFFF4F1E8, 255); plot(x0 + len - 1, y + k, 0xFFF4F1E8, 255); }
        String lbl = pick >= 1000 ? (int) (pick / 1000) + " KM" : (int) pick + " M";
        text(lbl, x0 + (len - PixelFont.width(lbl, 1)) / 2, y - 10, 1, 0xFFF4F1E8);
        // road key
        int lx = 8, ly = bottom - 46;
        panelRect(lx - 4, ly - 4, lx + 78, bottom - 4, 0xFF07090C, 170);
        String[] names = {"MOTORWAY", "HIGHWAY", "ROAD", "STREET"};
        RoadClass[] cls = {RoadClass.FREEWAY, RoadClass.HIGHWAY, RoadClass.COUNTRY, RoadClass.STREET};
        for (int i = 0; i < 4; i++) {
            int yy = ly + i * 10;
            for (int k = 0; k < 3; k++) hline(lx, yy + 2 + k, 14, k == 1 ? roadColour(cls[i]) : 0xFF101216, 255);
            text(names[i], lx + 20, yy, 1, 0xFFD8D4CA);
        }
    }

    private void drawDialog(Game g) {
        MapData.Label l = selected;
        int dw = Math.min(w - 40, 260), dh = 92;
        int x0 = (w - dw) / 2, y0 = (h - dh) / 2;
        panelRect(0, HEADER, w, h - FOOTER, 0xFF000000, 90);
        panelRect(x0, y0, x0 + dw, y0 + dh, 0xFF0B1218, 240);
        frameRect(x0, y0, x0 + dw, y0 + dh, 0xFF3A4852);
        hline(x0, y0, dw, 0xFFE0263A, 255);
        hline(x0, y0 + 1, dw, 0xFFE0263A, 255);
        text("FAST TRAVEL", x0 + 10, y0 + 9, 1, 0xFFFFC870);
        String name = l.name.toUpperCase();
        int sc = PixelFont.width(name, 2) <= dw - 20 ? 2 : 1;
        text(name, x0 + 10, y0 + 22, sc, 0xFFFFFFFF);
        float km = (float) Math.sqrt(dist2(l, g.mapX(), g.mapZ())) / 1000f;
        String kind = l.rank == 0 ? "CITY CENTRE" : l.rank == 1 ? "TOWN" : l.rank == 2 ? "SUBURB / VILLAGE" : "HAMLET";
        text(kind + "  -  " + String.format("%.1f", km) + " KM AWAY", x0 + 10, y0 + 42, 1, 0xFFB9C4C8);
        int bw = (dw - 30) / 2, by0 = y0 + dh - 30, by1 = y0 + dh - 10;
        travelBox[0] = x0 + 10; travelBox[1] = by0; travelBox[2] = x0 + 10 + bw; travelBox[3] = by1;
        cancelBox[0] = x0 + 20 + bw; cancelBox[1] = by0; cancelBox[2] = x0 + 20 + 2 * bw; cancelBox[3] = by1;
        panelRect(travelBox[0], by0, travelBox[2], by1, 0xFFE0263A, 255);
        frameRect(travelBox[0], by0, travelBox[2], by1, 0xFFFF8A8A);
        panelRect(cancelBox[0], by0, cancelBox[2], by1, 0xFF26323A, 255);
        frameRect(cancelBox[0], by0, cancelBox[2], by1, 0xFF5A6670);
        String t1 = "TRAVEL", t2 = "CANCEL";
        text(t1, travelBox[0] + (bw - PixelFont.width(t1, 1)) / 2, by0 + 7, 1, 0xFFFFFFFF);
        text(t2, cancelBox[0] + (bw - PixelFont.width(t2, 1)) / 2, by0 + 7, 1, 0xFFE8E4DA);
    }

    // ------------------------------------------------------------------ minimap

    /**
     * A heading-up round minimap centred at (cx, cy) with radius r pixels, drawn over the game
     * frame. It zooms out a little with speed and shows streets at their real width.
     */
    public void drawMinimap(int[] fb, int w, int h, int cx, int cy, int r, Game g) {
        begin(fb, w, h);
        MapData m = g.map;
        float speed = g.driving() && g.vehicle != null ? Math.abs(g.vehicle.speed) : 0f;
        float mpp = 3.2f + Math.min(5f, speed * 0.16f);        // metres per pixel
        float s = 1f / mpp;
        float px = g.mapX(), pz = g.mapZ(), yaw = g.mapHeading();
        float fx = (float) Math.sin(yaw), fz = (float) Math.cos(yaw);
        float rx = -fz, rz = fx;                               // screen right in world space
        float rpx = MapData.SIZE / m.extent;
        int r2 = r * r;
        clip(cx - r, cy - r, cx + r + 1, cy + r + 1);
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++) {
                int d2 = dx * dx + dy * dy;
                if (d2 > r2) continue;
                int x = cx + dx, y = cy + dy;
                if (x < 0 || y < 0 || x >= w || y >= h) continue;
                float wx = px + (rx * dx - fx * dy) * mpp, wz = pz + (rz * dx - fz * dy) * mpp;
                int c = relief(m, (wx - m.x0) * rpx - 0.5f, (wz - m.z0) * rpx - 0.5f);
                int u = m.urbanAt(wx, wz);
                if (u > 0) c = mix(c, u == 1 ? 0xFF55524F : 0xFF6E685E, 0.8f);
                c = mix(c, 0xFF0B1418, 0.38f);
                // soft vignette toward the rim
                float edge = (float) Math.sqrt(d2) / r;
                if (edge > 0.8f) c = mix(c, 0xFF05080A, (edge - 0.8f) * 2.5f);
                fb[y * w + x] = mix(fb[y * w + x], c, 0.92f);
            }
        roadIds.clear();
        if (roadMarks == null || roadMarks.length < m.roads.size()) roadMarks = new boolean[m.roads.size()];
        float reach = r * mpp * 1.5f;
        m.roadsIn(px - reach, pz - reach, px + reach, pz + reach, roadIds, roadMarks);
        circleClip = r;
        circleX = cx;
        circleY = cy;
        for (int pass = 0; pass < 2; pass++)
            for (int tier = 0; tier < 4; tier++)
                for (int id : roadIds) {
                    MapData.Road rd = m.roads.get(id);
                    if (tier(rd.cls) != tier) continue;
                    int width = Math.max(1, Math.round(rd.cls.halfWidth() * 2f * s));
                    int col = pass == 0 ? 0xFF0E1013 : roadColour(rd.cls);
                    for (int i = 1; i < rd.x.length; i++) {
                        float ax = rd.x[i - 1] - px, az = rd.z[i - 1] - pz, bx = rd.x[i] - px, bz = rd.z[i] - pz;
                        line(cx + (ax * rx + az * rz) * s, cy - (ax * fx + az * fz) * s,
                                cx + (bx * rx + bz * rz) * s, cy - (bx * fx + bz * fz) * s,
                                col, pass == 0 ? width + 2 : width, pass == 0 ? 160 : 235);
                    }
                }
        circleClip = 0;
        clip(0, 0, w, h);
        // rim, north marker and the player arrow
        ring(cx, cy, r, 0xFF0A0C10, 255);
        ring(cx, cy, r + 1, 0xFF5A6670, 255);
        ring(cx, cy, r + 2, 0xFF0A0C10, 200);
        // north is world -z
        float nx = -rz, ny = fz;                               // screen direction of world north (0, -1)
        float nl = (float) Math.hypot(nx, ny);
        int nX = Math.round(cx + nx / nl * r), nY = Math.round(cy + ny / nl * r);
        disc(nX, nY, 5, 0xFFE0263A, 255);
        ring(nX, nY, 5, 0xFF0A0C10, 255);
        text("N", nX - 2, nY - 3, 1, 0xFFFFFFFF);
        arrow(cx, cy, (float) Math.PI, 5f, false);
    }

    // ------------------------------------------------------------------ HUD text

    /** Place name, region and (when driving) a speedometer, top left of the game frame. */
    public void drawHud(int[] fb, int w, int h, Game g, int x, int y) {
        begin(fb, w, h);
        String place = g.placeName.isEmpty() ? "OPEN COUNTRY" : g.placeName.toUpperCase();
        String region = g.regionName.toUpperCase();
        int tw = Math.max(PixelFont.width(place, 1), PixelFont.width(region, 1));
        panelRect(x, y, x + tw + 14, y + (region.isEmpty() ? 14 : 24), 0xFF05080A, 150);
        vline(x, y, region.isEmpty() ? 14 : 24, 0xFFE0263A);
        vline(x + 1, y, region.isEmpty() ? 14 : 24, 0xFFE0263A);
        text(place, x + 7, y + 4, 1, 0xFFF4F1E8);
        if (!region.isEmpty()) text(region, x + 7, y + 14, 1, 0xFF9AA6AC);
        if (g.driving() && g.vehicle != null) {
            int kmh = Math.round(Math.abs(g.vehicle.speed) * 3.6f);
            String v = String.valueOf(kmh);
            int sy = y + (region.isEmpty() ? 18 : 28);
            panelRect(x, sy, x + 62, sy + 22, 0xFF05080A, 150);
            text(v, x + 6, sy + 4, 2, 0xFFFFC870);
            text("KM/H", x + 8 + PixelFont.width(v, 2), sy + 11, 1, 0xFF9AA6AC);
        }
    }

    // ------------------------------------------------------------------ menus and hints

    /** Clickable boxes of the menu items drawn by the last {@link #drawMenu} call. */
    public final List<int[]> menuBoxes = new ArrayList<>();

    /**
     * A pause-style menu over a darkened frame: title, items (one highlighted) and an optional
     * second column of help lines (controls).
     */
    public void drawMenu(int[] fb, int w, int h, String title, String[] items, int highlight, String[] help) {
        begin(fb, w, h);
        for (int i = 0; i < fb.length; i++) fb[i] = blend(fb[i], 0xFF05070A, 170);
        int x0 = 24, y0 = 30;
        hline(x0, y0 - 6, 80, 0xFFE0263A, 255);
        hline(x0, y0 - 5, 80, 0xFFE0263A, 255);
        text(title, x0, y0, 2, 0xFFFFFFFF);
        menuBoxes.clear();
        int y = y0 + 30;
        for (int i = 0; i < items.length; i++) {
            boolean on = i == highlight;
            int bw = 150, bh = 18;
            int[] box = {x0, y, x0 + bw, y + bh};
            menuBoxes.add(box);
            panelRect(box[0], box[1], box[2], box[3], on ? 0xFFE0263A : 0xFF121A20, on ? 255 : 200);
            if (on) frameRect(box[0], box[1], box[2], box[3], 0xFFFF8A8A);
            text(items[i], x0 + 10, y + 6, 1, on ? 0xFFFFFFFF : 0xFFC9C4B8);
            y += bh + 4;
        }
        if (help != null && help.length > 0) {
            int hx = x0 + 176, hy = y0 + 30, hw = w - hx - 16;
            panelRect(hx - 8, hy - 8, hx + hw, Math.min(h - 10, hy + help.length * 11 + 4), 0xFF0A1016, 200);
            for (int i = 0; i < help.length; i++) {
                String line = help[i];
                boolean head = line.startsWith("#");
                text(head ? line.substring(1) : line, hx, hy + i * 11, 1, head ? 0xFFFFC870 : 0xFFD8D4CA);
            }
        }
    }

    /** A strip of control hints along the bottom of the game frame, faded by alpha (0..1). */
    public void drawHints(int[] fb, int w, int h, String line, float alpha) {
        if (alpha <= 0f || line.isEmpty()) return;
        begin(fb, w, h);
        int tw = PixelFont.width(line, 1);
        int x = (w - tw) / 2, y = h - 18;
        panelRect(x - 8, y - 5, x + tw + 8, y + 12, 0xFF05080A, (int) (170 * alpha));
        if (alpha > 0.5f) text(line, x, y, 1, 0xFFE8E4DA);
    }

    // ------------------------------------------------------------------ helpers

    private static int tier(RoadClass c) {
        switch (c) {
            case FREEWAY: case RAMP: return 3;
            case HIGHWAY: return 2;
            case COUNTRY: case AVENUE: return 1;
            default: return 0;
        }
    }

    private static int roadColour(RoadClass c) {
        switch (c) {
            case FREEWAY: case RAMP: return 0xFFF08A3C;
            case HIGHWAY: return 0xFFF2CF63;
            case COUNTRY: return 0xFFE8DDBF;
            case AVENUE: return 0xFFF4F0E4;
            case LANE: return 0xFFB8AE92;
            default: return 0xFFDCD8CE;
        }
    }

    /** Line width on the full map for a scale (px per metre): minor roads appear as you zoom in. */
    private static int roadWidth(RoadClass c, float s) {
        float metres = c.halfWidth() * 2f * s;
        switch (c) {
            case FREEWAY: case RAMP: return Math.max(2, Math.round(metres));
            case HIGHWAY: return Math.max(s > 0.01f ? 2 : 1, Math.round(metres));
            case COUNTRY: return s > 0.006f ? Math.max(1, Math.round(metres)) : 0;
            case AVENUE: return s > 0.009f ? Math.max(1, Math.round(metres)) : 0;
            case TOWN: case STREET: return s > 0.028f ? Math.max(1, Math.round(metres)) : 0;
            case LANE: return s > 0.05f ? 1 : 0;
            default: return 1;
        }
    }

    private int relief(MapData m, float fx, float fz) {
        int n = MapData.SIZE;
        int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
        float tx = fx - ix, tz = fz - iz;
        if (ix < 0 || iz < 0 || ix >= n - 1 || iz >= n - 1) return 0xFF2E6A94;
        int[] r = m.relief;
        int a = r[iz * n + ix], b = r[iz * n + ix + 1], c = r[(iz + 1) * n + ix], d = r[(iz + 1) * n + ix + 1];
        return mix(mix(a, b, tx), mix(c, d, tx), tz);
    }

    private void begin(int[] fb, int w, int h) {
        this.fb = fb;
        this.w = w;
        this.h = h;
        clip(0, 0, w, h);
        circleClip = 0;
    }

    private int circleClip, circleX, circleY;

    private void clip(int x0, int y0, int x1, int y1) {
        clipX0 = Math.max(0, x0); clipY0 = Math.max(0, y0);
        clipX1 = Math.min(w, x1); clipY1 = Math.min(h, y1);
    }

    private void plot(int x, int y, int c, int a) {
        if (x < clipX0 || y < clipY0 || x >= clipX1 || y >= clipY1 || a <= 0) return;
        if (circleClip > 0) {
            int dx = x - circleX, dy = y - circleY;
            if (dx * dx + dy * dy > circleClip * circleClip) return;
        }
        int i = y * w + x;
        fb[i] = a >= 255 ? (c | 0xFF000000) : blend(fb[i], c, a);
    }

    private void line(float x0, float y0, float x1, float y1, int c, int width, int a) {
        // trivial reject against the clip rectangle
        float pad = width + 1;
        if (Math.max(x0, x1) < clipX0 - pad || Math.min(x0, x1) > clipX1 + pad
                || Math.max(y0, y1) < clipY0 - pad || Math.min(y0, y1) > clipY1 + pad) return;
        float dx = x1 - x0, dy = y1 - y0;
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
        if (steps > 4000) return;
        int lo = -(width - 1) / 2, hi = width / 2;
        for (int k = 0; k <= steps; k++) {
            float t = steps == 0 ? 0f : k / (float) steps;
            int x = Math.round(x0 + dx * t), y = Math.round(y0 + dy * t);
            for (int oy = lo; oy <= hi; oy++)
                for (int ox = lo; ox <= hi; ox++) plot(x + ox, y + oy, c, a);
        }
    }

    private void disc(int cx, int cy, int r, int c, int a) {
        for (int y = -r; y <= r; y++)
            for (int x = -r; x <= r; x++) if (x * x + y * y <= r * r + r) plot(cx + x, cy + y, c, a);
    }

    private void ring(int cx, int cy, int r, int c, int a) {
        int n = Math.max(12, (int) (r * 6.3f));
        int lx = Integer.MIN_VALUE, ly = 0;
        for (int i = 0; i < n; i++) {
            double t = i * 2 * Math.PI / n;
            int x = cx + (int) Math.round(Math.cos(t) * r), y = cy + (int) Math.round(Math.sin(t) * r);
            if (x == lx && y == ly) continue;
            plot(x, y, c, a);
            lx = x; ly = y;
        }
    }

    /** The player marker: heading 0 points down the screen (+z) on the north-up full map. */
    private void arrow(float x, float y, float heading, float size, boolean worldHeading) {
        float dx = (float) Math.sin(heading), dy = (float) Math.cos(heading);
        float sx = -dy, sy = dx;
        float tipX = x + dx * size, tipY = y + dy * size;
        float lX = x - dx * size * 0.7f + sx * size * 0.65f, lY = y - dy * size * 0.7f + sy * size * 0.65f;
        float rX = x - dx * size * 0.7f - sx * size * 0.65f, rY = y - dy * size * 0.7f - sy * size * 0.65f;
        float bX = x - dx * size * 0.3f, bY = y - dy * size * 0.3f;
        fillTri(tipX, tipY, lX, lY, bX, bY, 0xFF53E7F3);
        fillTri(tipX, tipY, bX, bY, rX, rY, 0xFF2FB5C4);
        line(tipX, tipY, lX, lY, 0xFF04121A, 1, 255);
        line(tipX, tipY, rX, rY, 0xFF04121A, 1, 255);
        line(lX, lY, bX, bY, 0xFF04121A, 1, 255);
        line(rX, rY, bX, bY, 0xFF04121A, 1, 255);
    }

    private void fillTri(float ax, float ay, float bx, float by, float cx, float cy, int c) {
        int x0 = (int) Math.floor(Math.min(ax, Math.min(bx, cx))), x1 = (int) Math.ceil(Math.max(ax, Math.max(bx, cx)));
        int y0 = (int) Math.floor(Math.min(ay, Math.min(by, cy))), y1 = (int) Math.ceil(Math.max(ay, Math.max(by, cy)));
        float area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if (Math.abs(area) < 1e-3f) return;
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float px = x + 0.5f, py = y + 0.5f;
                float w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area;
                float w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area;
                float w2 = 1f - w0 - w1;
                if (w0 >= 0 && w1 >= 0 && w2 >= 0) plot(x, y, c, 255);
            }
    }

    private void panelRect(int x0, int y0, int x1, int y1, int c, int a) {
        for (int y = Math.max(clipY0, y0); y < Math.min(clipY1, y1); y++)
            for (int x = Math.max(clipX0, x0); x < Math.min(clipX1, x1); x++) plot(x, y, c, a);
    }

    private void frameRect(int x0, int y0, int x1, int y1, int c) {
        hline(x0, y0, x1 - x0, c, 255);
        hline(x0, y1 - 1, x1 - x0, c, 255);
        vline(x0, y0, y1 - y0, c);
        vline(x1 - 1, y0, y1 - y0, c);
    }

    private void gradientBar(int x0, int y0, int x1, int y1, int top, int bottom) {
        for (int y = y0; y < y1; y++) {
            int c = mix(top, bottom, (y - y0) / (float) Math.max(1, y1 - y0 - 1));
            for (int x = x0; x < x1; x++) plot(x, y, c, 245);
        }
    }

    private void hline(int x, int y, int len, int c, int a) {
        for (int k = 0; k < len; k++) plot(x + k, y, c, a);
    }

    private void vline(int x, int y, int len, int c) {
        for (int k = 0; k < len; k++) plot(x, y + k, c, 255);
    }

    private void text(String s, int x, int y, int scale, int c) {
        for (int i = 0; i < s.length(); i++) {
            String gl = PixelFont.bits(s.charAt(i));
            for (int gy = 0; gy < 7; gy++)
                for (int gx = 0; gx < 5; gx++) {
                    if (gl.charAt(gy * 5 + gx) != '1') continue;
                    for (int sy = 0; sy < scale; sy++)
                        for (int sx = 0; sx < scale; sx++) {
                            int X = x + (i * 6 + gx) * scale + sx, Y = y + gy * scale + sy;
                            plot(X + 1, Y + 1, 0xFF000000, 160);
                            plot(X, Y, c, 255);
                        }
                }
        }
    }

    private static int blend(int d, int c, int a) {
        int r = ((d >> 16) & 255) + ((((c >> 16) & 255) - ((d >> 16) & 255)) * a >> 8);
        int g = ((d >> 8) & 255) + ((((c >> 8) & 255) - ((d >> 8) & 255)) * a >> 8);
        int b = (d & 255) + (((c & 255) - (d & 255)) * a >> 8);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return blend(a, b, (int) (t * 256f));
    }
}
