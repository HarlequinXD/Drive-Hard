package com.harlequinxd.drivehard;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;

import com.harlequinxd.drivehard.core.Game;
import com.harlequinxd.drivehard.core.map.MapData;
import com.harlequinxd.drivehard.core.road.RoadClass;

import java.util.ArrayList;
import java.util.List;

/**
 * The minimap in the top-right corner (north up, centred on you) and the full-screen island map
 * it opens when tapped: swipe to pan, pinch to zoom, names of the city, towns, villages and
 * regions appear as you zoom in. Touches arrive on the UI thread, drawing happens on the game
 * loop thread, so the view state is guarded by this object's lock.
 */
final class MapOverlay {
    private final float d;
    private MapData map;
    private Bitmap relief;
    private boolean[] mark;
    private final List<Integer> visible = new ArrayList<>();
    private final Matrix matrix = new Matrix();
    private final Path path = new Path();
    private final Paint bmp = new Paint(), road = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Full map open; its view: centre (world metres) and scale (pixels per metre). */
    volatile boolean open;
    private float cx, cz, scale;
    // gesture tracking
    private float lastX, lastY, lastDist;
    private int lastPointers;

    MapOverlay(float density) {
        d = density;
        bmp.setFilterBitmap(true);
        road.setStyle(Paint.Style.STROKE);
        road.setStrokeCap(Paint.Cap.ROUND);
        road.setStrokeJoin(Paint.Join.ROUND);
        label.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        label.setShadowLayer(3f * density, 0f, 0f, Color.BLACK);
    }

    private boolean ready(Game g) {
        if (map != null) return true;
        if (g == null || g.map == null) return false;
        map = g.map;
        relief = Bitmap.createBitmap(map.relief, MapData.SIZE, MapData.SIZE, Bitmap.Config.ARGB_8888);
        mark = new boolean[map.roads.size()];
        return true;
    }

    // ------------------------------------------------------------------ minimap

    RectF miniRect(int w, int h) {
        float s = Math.min(w, h) * 0.3f, m = 12f * d;
        return new RectF(w - s - m, m, w - m, m + s);
    }

    boolean hitMini(float x, float y, int w, int h) { return miniRect(w, h).contains(x, y); }

    void drawMini(Canvas c, Game g, int w, int h) {
        if (!ready(g)) return;
        RectF r = miniRect(w, h);
        float span = g.driving() ? 1800f : 900f;
        c.save();
        path.reset();
        path.addRoundRect(r, 14f * d, 14f * d, Path.Direction.CW);
        c.clipPath(path);
        synchronized (this) {
            drawView(c, g, r, g.mapX(), g.mapZ(), r.width() / span, true);
        }
        c.restore();
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(2f * d);
        fill.setColor(0xCCFFFFFF);
        c.drawRoundRect(r, 14f * d, 14f * d, fill);
        fill.setStyle(Paint.Style.FILL);
        label.setTextSize(11f * d);
        label.setColor(Color.WHITE);
        label.setTextAlign(Paint.Align.CENTER);
        c.drawText("N", r.centerX(), r.top + 14f * d, label);
        c.drawText("MAP", r.centerX(), r.bottom - 6f * d, label);
    }

    // ------------------------------------------------------------------ full map

    synchronized void openAt(Game g, int w, int h) {
        if (!ready(g)) return;
        cx = g.mapX();
        cz = g.mapZ();
        scale = Math.min(w, h) / 9000f;
        lastPointers = 0;
        open = true;
    }

    private RectF closeRect(int w) {
        float s = 44f * d, m = 14f * d;
        return new RectF(w - s - m, m, w - m, m + s);
    }

    private RectF centreRect(int w) {
        float s = 44f * d, m = 14f * d;
        return new RectF(w - 2 * s - 2 * m, m, w - s - 2 * m, m + s);
    }

    void drawFull(Canvas c, Game g, int w, int h) {
        if (!ready(g)) return;
        RectF r = new RectF(0, 0, w, h);
        synchronized (this) {
            drawView(c, g, r, cx, cz, scale, false);
        }
        // buttons: close, back to me
        RectF x = closeRect(w), me = centreRect(w);
        fill.setColor(0xB0000000);
        c.drawRoundRect(x, 10f * d, 10f * d, fill);
        c.drawRoundRect(me, 10f * d, 10f * d, fill);
        road.setColor(Color.WHITE);
        road.setStrokeWidth(3f * d);
        float k = 11f * d;
        c.drawLine(x.centerX() - k, x.centerY() - k, x.centerX() + k, x.centerY() + k, road);
        c.drawLine(x.centerX() - k, x.centerY() + k, x.centerX() + k, x.centerY() - k, road);
        c.drawCircle(me.centerX(), me.centerY(), k * 0.8f, road);
        fill.setColor(Color.WHITE);
        c.drawCircle(me.centerX(), me.centerY(), k * 0.3f, fill);
        label.setTextAlign(Paint.Align.LEFT);
        label.setTextSize(12f * d);
        label.setColor(0xDDFFFFFF);
        c.drawText("Drag to move, pinch to zoom", 14f * d, h - 14f * d, label);
    }

    /** Pan with one finger, pinch-zoom with two (about the pinch point); buttons close or recentre. */
    synchronized boolean onTouch(MotionEvent e, Game g, int w, int h) {
        int act = e.getActionMasked(), n = e.getPointerCount();
        if (act == MotionEvent.ACTION_DOWN) {
            if (closeRect(w).contains(e.getX(), e.getY())) { open = false; return true; }
            if (centreRect(w).contains(e.getX(), e.getY()) && g != null) { cx = g.mapX(); cz = g.mapZ(); return true; }
        }
        float mx = 0f, my = 0f;
        for (int i = 0; i < n; i++) { mx += e.getX(i); my += e.getY(i); }
        mx /= n;
        my /= n;
        float dist = n >= 2 ? (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1)) : 0f;
        boolean up = act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL || act == MotionEvent.ACTION_POINTER_UP;
        if (act == MotionEvent.ACTION_MOVE && n == lastPointers) {
            // pan by the movement of the touch centre
            cx -= (mx - lastX) / scale;
            cz -= (my - lastY) / scale;
            if (n >= 2 && lastDist > 1f && dist > 1f) {
                // zoom about the pinch centre
                float wx = cx + (mx - w * 0.5f) / scale, wz = cz + (my - h * 0.5f) / scale;
                scale = Math.max(Math.min(w, h) / 160000f, Math.min(0.8f, scale * dist / lastDist));
                cx = wx - (mx - w * 0.5f) / scale;
                cz = wz - (my - h * 0.5f) / scale;
            }
        }
        if (up) {
            // the remaining fingers become the new reference
            int left = n - 1, skip = act == MotionEvent.ACTION_POINTER_UP ? e.getActionIndex() : -1;
            float sx = 0f, sy = 0f;
            int k = 0;
            for (int i = 0; i < n; i++) if (i != skip) { sx += e.getX(i); sy += e.getY(i); k++; }
            lastPointers = act == MotionEvent.ACTION_POINTER_UP ? left : 0;
            if (k > 0) { lastX = sx / k; lastY = sy / k; }
            lastDist = 0f;
            if (lastPointers >= 2) {
                int a = -1, b = -1;
                for (int i = 0; i < n; i++) if (i != skip) { if (a < 0) a = i; else if (b < 0) b = i; }
                lastDist = (float) Math.hypot(e.getX(a) - e.getX(b), e.getY(a) - e.getY(b));
            }
            return true;
        }
        lastX = mx;
        lastY = my;
        lastDist = dist;
        lastPointers = n;
        // keep the view over the island
        cx = Math.max(map.x0, Math.min(map.x0 + map.extent, cx));
        cz = Math.max(map.z0, Math.min(map.z0 + map.extent, cz));
        return true;
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the map into rectangle r centred on world (vx, vz) at s pixels per metre. */
    private void drawView(Canvas c, Game g, RectF r, float vx, float vz, float s, boolean mini) {
        c.drawColor(0xFF2E6A94);
        float ox = r.centerX() - (vx - map.x0) * s, oz = r.centerY() - (vz - map.z0) * s;
        float px = map.extent / MapData.SIZE;
        matrix.setScale(px * s, px * s);
        matrix.postTranslate(ox, oz);
        c.drawBitmap(relief, matrix, bmp);
        // roads visible in this view, minor ones only when zoomed in enough to tell them apart
        float hw = r.width() * 0.5f / s, hh = r.height() * 0.5f / s;
        visible.clear();
        map.roadsIn(vx - hw, vz - hh, vx + hw, vz + hh, visible, mark);
        for (int pass = 0; pass < 4; pass++)
            for (int idx : visible) {
                MapData.Road rd = map.roads.get(idx);
                int p = pass(rd.cls);
                if (p != pass) continue;
                float minScale = minScale(rd.cls);
                if (s < minScale) continue;
                road.setColor(color(rd.cls));
                road.setStrokeWidth(width(rd.cls, s) * d);
                path.reset();
                path.moveTo(ox + (rd.x[0] - map.x0) * s, oz + (rd.z[0] - map.z0) * s);
                for (int k = 1; k < rd.x.length; k++) path.lineTo(ox + (rd.x[k] - map.x0) * s, oz + (rd.z[k] - map.z0) * s);
                c.drawPath(path, road);
            }
        // names
        label.setTextAlign(Paint.Align.LEFT);
        for (MapData.Label l : map.labels) {
            float sx = ox + (l.x - map.x0) * s, sy = oz + (l.z - map.z0) * s;
            if (sx < r.left - 200 || sx > r.right + 50 || sy < r.top - 20 || sy > r.bottom + 20) continue;
            if (l.rank == 4) {
                if (mini || s > 0.012f) continue;
                label.setTextAlign(Paint.Align.CENTER);
                label.setTextSize(15f * d);
                label.setColor(0xB0FFFFFF);
                c.drawText(l.name.toUpperCase(java.util.Locale.US), sx, sy, label);
                label.setTextAlign(Paint.Align.LEFT);
                continue;
            }
            float need = l.rank == 0 ? 0f : (l.rank == 1 ? 0.0035f : (l.rank == 2 ? 0.009f : 0.02f));
            if (mini) need = l.rank <= 1 ? 0f : (l.rank == 2 ? 0.05f : 0.12f);
            if (s < need) continue;
            float dot = (l.rank == 0 ? 6f : (l.rank == 1 ? 4.5f : (l.rank == 2 ? 3.5f : 2.5f))) * d;
            fill.setColor(0xFF000000);
            c.drawCircle(sx, sy, dot + d, fill);
            fill.setColor(l.rank == 0 ? 0xFFFFD040 : (l.coastal ? 0xFF8FE0FF : Color.WHITE));
            c.drawCircle(sx, sy, dot, fill);
            label.setTextSize((mini ? 10f : (l.rank == 0 ? 16f : (l.rank == 1 ? 13f : (l.rank == 2 ? 11.5f : 10f)))) * d);
            label.setColor(Color.WHITE);
            c.drawText(l.name, sx + dot + 3f * d, sy + 4f * d, label);
        }
        // you
        float yx = ox + (g.mapX() - map.x0) * s, yz = oz + (g.mapZ() - map.z0) * s;
        float hd = g.mapHeading(), fx = (float) Math.sin(hd), fz = (float) Math.cos(hd);
        float a = (mini ? 9f : 11f) * d;
        path.reset();
        path.moveTo(yx + fx * a, yz + fz * a);
        path.lineTo(yx - fx * a * 0.6f + fz * a * 0.6f, yz - fz * a * 0.6f - fx * a * 0.6f);
        path.lineTo(yx - fx * a * 0.25f, yz - fz * a * 0.25f);
        path.lineTo(yx - fx * a * 0.6f - fz * a * 0.6f, yz - fz * a * 0.6f + fx * a * 0.6f);
        path.close();
        fill.setColor(0xFFFF3040);
        c.drawPath(path, fill);
        road.setColor(Color.WHITE);
        road.setStrokeWidth(1.5f * d);
        c.drawPath(path, road);
    }

    private static int pass(RoadClass c) {
        switch (c) {
            case LANE: case TOWN: case STREET: return 0;
            case COUNTRY: case AVENUE: return 1;
            case HIGHWAY: return 2;
            default: return 3;
        }
    }

    private static float minScale(RoadClass c) {
        switch (c) {
            case LANE: return 0.08f;
            case TOWN: case STREET: return 0.03f;
            case COUNTRY: case AVENUE: return 0.004f;
            default: return 0f;
        }
    }

    private static int color(RoadClass c) {
        switch (c) {
            case FREEWAY: case RAMP: return 0xFF4F86E8;
            case HIGHWAY: return 0xFFF08A3C;
            case COUNTRY: return 0xFFF8F0CC;
            case LANE: return 0xFFD8C8A0;
            default: return 0xFFFFFFFF;
        }
    }

    private static float width(RoadClass c, float s) {
        float zoom = Math.max(1f, Math.min(3f, s / 0.02f));
        switch (c) {
            case FREEWAY: return 3.2f * zoom;
            case RAMP: return 1.6f * zoom;
            case HIGHWAY: return 2.6f * zoom;
            case COUNTRY: case AVENUE: return 1.8f * zoom;
            case LANE: return 0.8f * zoom;
            default: return 1.2f * zoom;
        }
    }
}
