package com.harlequinxd.drivehard.core.game;

import com.harlequinxd.drivehard.core.model.Cars;

import static com.harlequinxd.drivehard.core.model.Cars.AWD;
import static com.harlequinxd.drivehard.core.model.Cars.FWD;
import static com.harlequinxd.drivehard.core.model.Cars.RWD;

import java.util.List;

/**
 * A car the player can drive (or one left parked after getting out). A small rigid-body model:
 * the car has mass and a yaw inertia, and moves only through the forces its four tyres (two
 * axles) can make. Each axle's grip comes from its load — braking shifts weight onto the front,
 * accelerating onto the rear — and a tyre that is braking or spinning has less sideways grip
 * left (friction circle). So the nose dips and bites under braking, the tail goes light, power
 * can step a rear-drive car out, and heavy trucks lean on their tyres and wash wide. The body
 * is a row of circles along its length that collide with the city's boxes, with impulses that
 * spin the car when it is hit off-centre.
 *
 * Heading: forward = (sin yaw, cos yaw); right = (-cos yaw, sin yaw). Turning right lowers yaw.
 * Japanese cars are right-hand drive, so the driver's door is on the right.
 */
public final class Vehicle {
    public static final float MAX_REVERSE = 8f;
    private static final float G = 9.81f;

    public final int style;
    public final float len, wid;
    public float x, y, z, yaw;
    /** Signed speed along the heading (m/s), for HUD, camera and sprites. */
    public float speed;
    /** World velocity (m/s) and turn rate toward the right (rad/s). */
    public float vx, vz, spin;
    /** Last frame's steering (-1..1) and pedal state, for HUD and sprites. */
    public float steer;
    public boolean braking;
    /** Parking brake: set while nobody is driving, so an abandoned car pulls up instead of rolling on. */
    public boolean parkingBrake;
    /** Handbrake held: locks the rear wheels so the tail lets go (flick it into a corner to slide). */
    public boolean handbrake;
    /** Seconds since the handbrake was let go — the stability assist stays off while you hold the slide. */
    private float sinceHandbrake = 99f;
    /** Body pitch (+ nose down) and roll (+ leaning left, i.e. turning right), in m/s² of load shift. */
    public float pitch, roll;
    /** How hard the front / rear tyres are sliding (0 grip .. 1 sliding), for skid marks and smoke. */
    public float slipFront, slipRear;
    /** Seconds since the last hard impact (for a little camera shake / HUD). */
    public float sinceCrash = 99f;
    /** Every impact over a light bump counts up here, with its closing speed (m/s), for sounds. */
    public int impacts;
    public float impact;

    private final float mass, inertia, wheelbase, a, b, cgH, mu, power, drag, top;
    private final int drive;
    /** 0..1 how strongly the stability assist catches slides (drift cars get less). */
    private final float assist;
    /** Front wheel angle (rad) — the steering rack moves at a finite rate. */
    private float wheel;
    /** Last step's longitudinal acceleration (for weight transfer). */
    private float accLong, accLat;

    public Vehicle(int style, float x, float z, float yaw, float speed) {
        this.style = style;
        Cars.Spec s = Cars.spec(style);
        this.len = s.L;
        this.wid = s.W;
        this.x = x; this.z = z; this.yaw = yaw;
        mass = s.mass;
        inertia = mass * (len * len + wid * wid) / 12f * 1.1f;
        wheelbase = len * 0.62f;
        a = wheelbase * 0.48f;            // CG slightly toward the front (engine)
        b = wheelbase - a;
        cgH = s.cgHeight;
        mu = s.grip;
        power = s.power * 1000f * mass;
        top = s.top;
        // pick the drag so that full power balances drag + rolling at the top speed
        drag = Math.max(0.1f, (power - 0.015f * mass * G * top) / (top * top * top));
        drive = s.drive;
        assist = s.name.equals("Drift coupe") ? 0.45f : s.drive == RWD && s.grip > 1.03f ? 0.6f : 1f;
        setSpeed(speed);
    }

    public float fx() { return (float) Math.sin(yaw); }
    public float fz() { return (float) Math.cos(yaw); }

    /** Sets the car moving straight along its heading (0 = parked). */
    public void setSpeed(float s) {
        vx = fx() * s; vz = fz() * s; speed = s; spin = 0f; wheel = 0f;
        accLong = accLat = 0f;
    }

    /**
     * @param steerIn  -1 (left) .. +1 (right)
     * @param gas      0..1 accelerate forward
     * @param rev      0..1 brake, then reverse once stopped
     * @param self     colliders belonging to this car (skipped), may be null
     */
    public void update(float dt, float steerIn, float gas, float rev, World world, Object self) {
        steer = steerIn;
        sinceCrash += dt;
        int n = Math.max(1, (int) Math.ceil(dt / (1f / 120f)));
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            physics(h, steerIn, gas, rev);
            move(h, world, self);
        }
        speed = vx * fx() + vz * fz();
        y = world.surface(x, z, y + 0.6f);
        // body motion lags the load shift a little, like springs
        float k = Math.min(1f, dt * 9f);
        pitch += (-accLong - pitch) * k;
        roll += (accLat - roll) * k;
    }

    private void physics(float h, float steerIn, float gas, float rev) {
        sinceHandbrake = handbrake ? 0f : sinceHandbrake + h;
        float fx = fx(), fz = fz(), rx = -fz, rz = fx;
        float vf = vx * fx + vz * fz, vr = vx * rx + vz * rz, av = Math.abs(vf);

        // --- steering rack: turns in at a steady rate, self-centres faster; less lock at speed so
        // a full stick asks for about the tyres' limit rather than an instant spin
        float lock = Math.min(0.6f, 1.25f * mu * G * wheelbase / Math.max(1f, vf * vf));
        float want = steerIn * lock;
        float rate = Math.abs(want) < Math.abs(wheel) || want * wheel < 0 ? 3.2f : 1.9f;
        wheel += Math.max(-rate * h, Math.min(rate * h, want - wheel));

        // --- pedals -> engine force (on driven wheels) and brake force (60/40 front/rear)
        float engine = 0f, brake = 0f;
        braking = false;
        if (gas > 0.05f && rev < 0.05f) {
            if (vf < -0.5f) { brake = gas; braking = true; }
            else engine = gas * power / Math.max(av, 4f);
        } else if (rev > 0.05f) {
            if (vf > 0.5f) { brake = rev; braking = true; }
            else if (vf > -MAX_REVERSE) engine = -rev * Math.min(power * 0.5f / Math.max(av, 3f), mass * 3.5f);
        } else if (parkingBrake || (handbrake && av < 2f)) { brake = 0.7f; }

        // --- loads with longitudinal weight transfer
        float wf = mass * G * b / wheelbase - mass * accLong * cgH * 0.75f / wheelbase;
        float wr = mass * G - wf;
        wf = Math.max(mass * G * 0.12f, wf);
        wr = Math.max(mass * G * 0.12f, wr);
        // rears grip a touch more than fronts (a road car's safe understeer balance)
        float capF = mu * wf, capR = mu * wr * 1.12f;

        // --- longitudinal tyre forces
        float shareF = drive == FWD ? 1f : drive == RWD ? 0f : 0.45f;
        float dirv = Math.signum(vf);
        float stopF = mass * av / h;      // never more braking than it takes to stop this step
        float fxF = engine * shareF, fxR = engine * (1f - shareF);
        if (brake > 0f) {
            float bf = Math.min(brake * capF * 0.95f, stopF * 0.6f), br = Math.min(brake * capR * 0.9f, stopF * 0.4f);
            fxF -= dirv * bf;
            fxR -= dirv * br;
        }
        boolean locked = handbrake && av > 0.3f;
        if (handbrake) {
            // clutch in, rear wheels locked: they drag the car back and have little side grip left
            fxR = -dirv * Math.min(capR * 0.8f, stopF * 0.5f);
            if (drive == AWD) fxF *= 0.5f;
        }
        // driven wheels spin past the grip they have
        float spinR = Math.abs(fxR) > capR ? Math.min(1f, (Math.abs(fxR) - capR) / capR + 0.4f) : 0f;
        float spinF = Math.abs(fxF) > capF ? Math.min(1f, (Math.abs(fxF) - capF) / capF + 0.4f) : 0f;
        fxF = Math.max(-capF, Math.min(capF, fxF));
        fxR = Math.max(-capR, Math.min(capR, fxR));

        // --- lateral tyre forces from slip angles (friction circle: what's left after fx)
        float cw = (float) Math.cos(wheel), sw = (float) Math.sin(wheel);
        float vden = Math.max(av, 2.5f);
        // front axle: velocity at the axle in body frame, then in the wheel's frame
        float vfF = vf, vrF = vr + a * spin;
        float wl = vfF * cw + vrF * sw, wn = -vfF * sw + vrF * cw;
        float alphaF = (float) Math.atan2(wn, Math.max(Math.abs(wl), vden));
        float latCapF = (float) Math.sqrt(Math.max(0f, capF * capF - fxF * fxF));
        float fyF = -latCapF * tyre(alphaF);
        float alphaR = (float) Math.atan2(vr - b * spin, vden);
        float latCapR = (float) Math.sqrt(Math.max(0f, capR * capR - fxR * fxR)) * (1f - 0.45f * spinR);
        if (locked) latCapR = capR * 0.3f;
        float fyR = -latCapR * tyre(alphaR);
        slipFront = Math.max(spinF, Math.min(1f, Math.max(0f, Math.abs(alphaF) - 0.09f) * 6f));
        slipRear = Math.max(spinR, Math.min(1f, Math.max(0f, Math.abs(alphaR) - 0.09f) * 6f));
        if (brake > 0.9f && av > 3f && slipFront < 0.5f) slipFront = Math.max(slipFront, 0.35f * brake);
        if (locked && av > 2f) slipRear = 1f;
        if (av < 1.5f) slipFront = slipRear = Math.max(spinR, spinF) * Math.min(1f, av);

        // wheel forces -> body frame (front wheel forces turn with the wheel)
        float bodyF = fxF * cw - fyF * sw + fxR;
        float bodyR = fxF * sw + fyF * cw + fyR;
        float torque = a * (fxF * sw + fyF * cw) - b * fyR;

        // --- resistance: rolling + engine braking when coasting, aero drag
        float resist = 0.015f * mass * G + drag * vf * vf;
        if (engine == 0f && brake == 0f) resist += mass * (av < 3f ? 2.4f : vf < 0f ? 1.5f : 0.8f);   // lift-off engine braking
        bodyF -= dirv * Math.min(resist, stopF);

        accLong = bodyF / mass;
        accLat = bodyR / mass;
        vx += (bodyF * fx + bodyR * rx) / mass * h;
        vz += (bodyF * fz + bodyR * rz) / mass * h;
        spin += torque / inertia * h;
        // stability assist: once the tail is well out, ease the spin back toward what the front
        // wheels are asking for — enough to catch a slide, not enough to stop a committed one
        float sp = (float) Math.sqrt(vx * vx + vz * vz);
        if (sp > 3f) {
            float beta = Math.abs((float) Math.atan2(vr, Math.max(av, 0.1f)));
            float target = vf * (float) Math.tan(wheel) / wheelbase;
            float lim = mu * G * 1.1f / sp;
            target = Math.max(-lim, Math.min(lim, target));
            float over = Math.max(0f, beta - 0.07f) / 0.25f;
            float assistK = Math.min(1f, over) * assist * 6f * Math.min(1f, Math.max(0f, sinceHandbrake - 0.4f) / 0.8f);
            spin += (target - spin) * Math.min(1f, assistK * h);
        }
        // at walking pace the tyres just hold the car (no creeping sideways or spinning in place)
        if (av < 0.6f && engine == 0f) {
            float d = Math.min(1f, h * 12f);
            float nvr = vr * (1f - d);
            vx += (nvr - vr) * rx; vz += (nvr - vr) * rz;
            spin *= 1f - d;
        }
        float s2 = vx * vx + vz * vz;
        if (s2 < 1e-6f && engine == 0f) { vx = vz = 0f; spin = 0f; }

        yaw -= spin * h;
        if (yaw > Math.PI) yaw -= (float) (2 * Math.PI);
        if (yaw < -Math.PI) yaw += (float) (2 * Math.PI);
    }

    /** Simplified Pacejka curve: rises to full grip near 7° of slip, then fades a little. */
    private static float tyre(float alpha) {
        return (float) Math.sin(1.55 * Math.atan(11.0 * alpha));
    }

    /** Moves in short hops so fast cars don't tunnel through poles. */
    private void move(float h, World world, Object self) {
        float dx = vx * h, dz = vz * h;
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(dx, dz) / 0.4f));
        for (int i = 0; i < steps; i++) {
            float ox = x, oz = z;
            x += dx / steps;
            z += dz / steps;
            x = Math.max(world.minX + 2f, Math.min(world.maxX - 2f, x));
            z = Math.max(world.minZ + 2f, Math.min(world.maxZ - 2f, z));
            if (world.blocked(x, z)) {
                x = ox; z = oz;
                float hs = (float) Math.hypot(vx, vz);
                if (hs > 6f) sinceCrash = 0f;
                if (hs > 1.2f) { impacts++; impact = hs; }
                vx *= -0.15f; vz *= -0.15f; spin *= 0.3f;
                break;
            }
            if (collide(world, self)) break;
        }
    }

    private final float[] push = new float[3];

    /** Resolves the body circles against nearby boxes, with a bounce impulse. Returns true on a hit. */
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
                for (int bi = 0; bi < boxes.size(); bi++) {
                    Box box = boxes.get(bi);
                    if (box.owner != null && box.owner == self) continue;
                    if (box.maxY < y + 0.35f || box.minY > y + 1.6f) continue;   // curbs, overhead wires
                    if (!circleBox(cx, cz, r, box, push)) continue;
                    x += push[0];
                    z += push[1];
                    cx += push[0];
                    cz += push[1];
                    float nx = push[0] / push[2], nz = push[1] / push[2];
                    impulse(cx - nx * r - x, cz - nz * r - z, nx, nz);
                    hit = any = true;
                }
            }
            if (!any) break;
        }
        return hit;
    }

    /**
     * Bounce off a wall with normal (nx, nz) at contact offset (ox, oz) from the centre: kills the
     * velocity into the wall (a little restitution), scrubs some along it, and spins the car
     * when the hit is off-centre — clip a pole with a corner and the car swings round.
     */
    private void impulse(float ox, float oz, float nx, float nz) {
        // spin turns the forward axis toward 'right', i.e. counter-clockwise in (x, z)
        float pvx = vx - spin * oz, pvz = vz + spin * ox;
        float vn = pvx * nx + pvz * nz;
        if (vn >= 0f) return;
        float rn = ox * nz - oz * nx;
        float invM = 1f / mass + rn * rn / inertia;
        float j = -(1f + 0.2f) * vn / invM;
        vx += j * nx / mass;
        vz += j * nz / mass;
        spin += rn * j / inertia;
        // friction along the wall
        float tx = -nz, tz = nx, vt = pvx * tx + pvz * tz;
        float jt = Math.max(-0.35f * j, Math.min(0.35f * j, -vt / (1f / mass + (ox * tz - oz * tx) * (ox * tz - oz * tx) / inertia)));
        vx += jt * tx / mass;
        vz += jt * tz / mass;
        spin += (ox * tz - oz * tx) * jt / inertia;
        if (-vn > 6f) sinceCrash = 0f;
        if (-vn > 1.2f) { impacts++; impact = -vn; }
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

    /**
     * The body as a chain of small squares along its length, so a car parked at an angle
     * doesn't block a big square of pavement around it (people can stand at its doors).
     */
    public void boxes(List<Box> out) {
        float half = wid * 0.4f;
        int n = Math.max(2, (int) Math.ceil(len / (half * 1.6f)));
        float fx = fx(), fz = fz();
        for (int k = 0; k < n; k++) {
            float t = -len * 0.5f + half + (len - 2 * half) * k / (n - 1);
            float cx = x + fx * t, cz = z + fz * t;
            Box bx = new Box(cx - half, y, cz - half, cx + half, y + 1.5f, cz + half);
            bx.owner = this;
            out.add(bx);
        }
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
