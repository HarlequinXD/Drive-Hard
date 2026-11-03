package com.harlequinxd.drivehard.core.game;

import com.harlequinxd.drivehard.core.model.Cars;

import java.util.List;

/**
 * A car the player can drive (or one left parked after getting out). Arcade handling in the
 * spirit of Driver 3: quick acceleration, speed-sensitive steering, handbrake-free sliding is
 * left out; the body is a row of circles along its length that collide with the city's boxes.
 *
 * Heading: forward = (sin yaw, cos yaw); right = (-cos yaw, sin yaw). Japanese cars are
 * right-hand drive, so the driver's door is on the right.
 */
public final class Vehicle {
    public static final float MAX_FORWARD = 30f, MAX_REVERSE = 9f;

    public final int style;
    public final float len, wid;
    public float x, y, z, yaw, speed;
    /** Last frame's steering (-1..1) and pedal state, for HUD and sprites. */
    public float steer;
    public boolean braking;
    /** Seconds since the last hard impact (for a little camera shake / HUD). */
    public float sinceCrash = 99f;

    public Vehicle(int style, float x, float z, float yaw, float speed) {
        this.style = style;
        Cars.Spec s = Cars.spec(style);
        this.len = s.L;
        this.wid = s.W;
        this.x = x; this.z = z; this.yaw = yaw; this.speed = speed;
    }

    public float fx() { return (float) Math.sin(yaw); }
    public float fz() { return (float) Math.cos(yaw); }

    /**
     * @param steerIn  -1 (left) .. +1 (right)
     * @param gas      0..1 accelerate forward
     * @param rev      0..1 brake, then reverse once stopped
     * @param self     colliders belonging to this car (skipped), may be null
     */
    public void update(float dt, float steerIn, float gas, float rev, World world, Object self) {
        // --- pedals
        float accel;
        braking = false;
        if (gas > 0.05f && rev < 0.05f) {
            accel = speed < -0.3f ? 16f * gas : (speed < 12f ? 9.5f : (speed < 22f ? 6f : 3.2f)) * gas;
            if (speed < -0.3f) braking = true;
        } else if (rev > 0.05f) {
            if (speed > 0.3f) { accel = -16f * rev; braking = true; }
            else accel = -5.5f * rev;
        } else {
            // coast: rolling resistance and engine braking
            accel = -Math.signum(speed) * Math.min(Math.abs(speed) / Math.max(dt, 1e-3f), 2.2f + Math.abs(speed) * 0.05f);
        }
        speed += accel * dt;
        speed = Math.max(-MAX_REVERSE, Math.min(MAX_FORWARD, speed));

        // --- steering: rate grows with speed up to a point, then eases off for stability
        steer += (steerIn - steer) * Math.min(1f, dt * 10f);
        float a = Math.abs(speed);
        float grip = Math.min(1f, a / 5f) * (a > 18f ? 18f / a * 0.35f + 0.65f : 1f);
        float rate = 2.3f * grip;
        yaw -= steer * rate * Math.signum(speed) * dt;
        if (yaw > Math.PI) yaw -= (float) (2 * Math.PI);
        if (yaw < -Math.PI) yaw += (float) (2 * Math.PI);

        // --- move in a few sub-steps so fast cars don't tunnel through poles
        float dist = speed * dt;
        int steps = Math.max(1, (int) Math.ceil(Math.abs(dist) / 0.4f));
        float fx = fx(), fz = fz();
        sinceCrash += dt;
        for (int i = 0; i < steps; i++) {
            float ox = x, oz = z;
            x += fx * dist / steps;
            z += fz * dist / steps;
            x = Math.max(world.minX + 2f, Math.min(world.maxX - 2f, x));
            z = Math.max(world.minZ + 2f, Math.min(world.maxZ - 2f, z));
            if (world.blocked(x, z)) { x = ox; z = oz; impact(1f); break; }
            if (collide(world, self)) break;
        }
        y = world.surface(x, z, y + 0.6f);
    }

    private final float[] push = new float[3];

    /** Resolves the body circles against nearby boxes. Returns true on a hit. */
    private boolean collide(World world, Object self) {
        float fx = fx(), fz = fz();
        float r = wid * 0.5f;
        int n = Math.max(2, (int) Math.ceil(len / wid) + 1);
        boolean hit = false;
        for (int pass = 0; pass < 2; pass++) {
            boolean any = false;
            for (int k = 0; k < n; k++) {
                float t = -len * 0.5f + r + (len - 2 * r) * k / (n - 1);
                float cx = x + fx * t, cz = z + fz * t;
                List<Box> boxes = world.near(cx, cz);
                for (int b = 0; b < boxes.size(); b++) {
                    Box box = boxes.get(b);
                    if (box.owner != null && box.owner == self) continue;
                    if (box.maxY < y + 0.35f || box.minY > y + 1.6f) continue;   // curbs, overhead wires
                    if (!circleBox(cx, cz, r, box, push)) continue;
                    x += push[0];
                    z += push[1];
                    cx += push[0];
                    cz += push[1];
                    float nx = push[0] / push[2], nz = push[1] / push[2];
                    float into = -(fx * nx + fz * nz) * Math.signum(speed);   // 1 = head-on
                    if (!hit) impact(into);
                    hit = any = true;
                }
            }
            if (!any) break;
        }
        return hit;
    }

    private void impact(float headOn) {
        float h = Math.max(0f, Math.min(1f, headOn));
        if (Math.abs(speed) > 6f && h > 0.5f) sinceCrash = 0f;
        // glancing blows scrub a little speed; head-on stops the car with a small bounce
        speed *= h > 0.75f ? -0.18f : (1f - 0.75f * h);
    }

    /** Circle vs axis-aligned box: writes push-out (x, z, length). */
    private static boolean circleBox(float cx, float cz, float r, Box b, float[] out) {
        float qx = Math.max(b.minX, Math.min(cx, b.maxX)), qz = Math.max(b.minZ, Math.min(cz, b.maxZ));
        float dx = cx - qx, dz = cz - qz, d2 = dx * dx + dz * dz;
        if (d2 >= r * r) return false;
        if (d2 > 1e-8f) {
            float d = (float) Math.sqrt(d2), p = r - d;
            out[0] = dx / d * p; out[1] = dz / d * p; out[2] = p;
        } else {
            // centre inside the box: leave by the nearest face
            float l = cx - b.minX, rr = b.maxX - cx, t = cz - b.minZ, bt = b.maxZ - cz;
            float m = Math.min(Math.min(l, rr), Math.min(t, bt));
            out[0] = m == l ? -(l + r) : (m == rr ? rr + r : 0f);
            out[1] = m == t ? -(t + r) : (m == bt ? bt + r : 0f);
            out[2] = m + r;
        }
        return true;
    }

    /** World-axis bounds as a collider (for people walking into parked cars). */
    public Box box() {
        float s = Math.abs((float) Math.sin(yaw)), c = Math.abs((float) Math.cos(yaw));
        float hx = (len * s + wid * c) * 0.5f, hz = (len * c + wid * s) * 0.5f;
        Box b = new Box(x - hx, y, z - hz, x + hx, y + 1.5f, z + hz);
        b.owner = this;
        return b;
    }

    /** Point beside the car: side +1 = right (driver's door), -1 = left. */
    public void doorPoint(int side, float out, float[] p) {
        float rx = -fz(), rz = fx();
        float d = wid * 0.5f + out;
        p[0] = x + rx * d * side + fx() * len * 0.08f;
        p[1] = z + rz * d * side + fz() * len * 0.08f;
    }

    /** Distance from a point to the car's body rectangle (0 inside). */
    public float distanceTo(float px, float pz) {
        float dx = px - x, dz = pz - z;
        float along = dx * fx() + dz * fz(), side = dx * -fz() + dz * fx();
        float ea = Math.max(0f, Math.abs(along) - len * 0.5f), es = Math.max(0f, Math.abs(side) - wid * 0.5f);
        return (float) Math.sqrt(ea * ea + es * es);
    }
}
