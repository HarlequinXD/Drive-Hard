package com.harlequinxd.drivehard.core.game;

import com.harlequinxd.drivehard.core.model.Animations;
import com.harlequinxd.drivehard.core.model.Animations.Anim;

/**
 * On-foot character controller: camera-relative movement, walk/run by stick deflection,
 * jumping with coyote time and buffering, box collision with auto step-up, and animation
 * frame selection for the baked sprite sheet.
 */
public final class Player {
    public static final float RADIUS = 0.28f, HEIGHT = 1.7f, STEP = 0.36f;
    public static final float WALK_SPEED = 2.35f, RUN_SPEED = 6.9f, RUN_THRESHOLD = 0.72f;
    public static final float GRAVITY = 22f, JUMP_VELOCITY = 6.8f;

    /** In the sea: wading below this depth, swimming above it (m of water at the feet). */
    public static final float SWIM_DEPTH = 1.25f, STAND_DEPTH = 1.05f;
    /** Breaststroke (walk), front crawl (run). */
    public static final float BREAST_SPEED = 1.05f, CRAWL_SPEED = 2.1f;
    /** Top speed pushing through waist-deep water; deeper than WADE_DEPTH she uses the wading walk. */
    public static final float WADE_SPEED = 1.05f, WADE_DEPTH = 0.55f;

    public float x, y, z, vx, vy, vz;
    /** Swimming in deep water (y is then the water surface). */
    public boolean swimming;
    /** Depth of water where she stands or swims (0 on dry land). */
    public float waterDepth;
    /** Vertical speed when she hit the water this frame (negative), 0 otherwise: for the big splash. */
    public float splashImpact;
    /** Animation cycle phase 0..1 of the current loop (strokes, strides), for effects. */
    public float cyclePhase() { return phase; }
    public float facing;               // yaw, facing vector = (sin, 0, cos)
    public boolean onGround = true;

    public Anim anim = Anim.IDLE;
    public int animFrame;              // frame within anim
    private float phase;               // 0..1 cycle phase for loops
    private float idleTime;
    private float coyote, jumpBuffer, landTimer;

    public Player(float x, float z) { this.x = x; this.z = z; }

    /**
     * @param mx,mz  desired world move direction scaled by stick deflection (length 0..1)
     * @param jump   true on the frame the jump button was pressed
     */
    public void update(float dt, float mx, float mz, boolean jump, World world) {
        float mag = (float) Math.sqrt(mx * mx + mz * mz);
        if (mag > 1f) { mx /= mag; mz /= mag; mag = 1f; }
        splashImpact = 0f;
        if (swimming) { swim(dt, mx, mz, mag, world); return; }

        // --- horizontal velocity
        float target = 0f;
        if (mag > 0.08f) target = mag >= RUN_THRESHOLD ? RUN_SPEED : WALK_SPEED * Math.max(0.6f, mag / RUN_THRESHOLD);
        // wading: no running once the water is past the knees; waist deep it is a slow push
        if (waterDepth > 0.25f && onGround && target > 0f) {
            float k = Math.min(1f, (waterDepth - 0.25f) / 0.5f);
            float cap = WALK_SPEED + (WADE_SPEED - WALK_SPEED) * k;
            target = Math.min(target, cap * Math.max(0.6f, Math.min(1f, mag / RUN_THRESHOLD)));
        } else if (waterDepth > 0.05f && onGround) target *= 1f - waterDepth * 0.8f;
        float dx = mag > 1e-4f ? mx / mag : 0f, dz = mag > 1e-4f ? mz / mag : 0f;
        float accel = onGround ? 22f : 7f;
        float tvx = dx * target, tvz = dz * target;
        vx = approach(vx, tvx, accel * dt);
        vz = approach(vz, tvz, accel * dt);

        // --- facing turns toward the stick direction
        if (mag > 0.08f) {
            float want = (float) Math.atan2(dx, dz);
            float diff = wrap(want - facing);
            float turn = (onGround ? 14f : 7f) * dt;
            facing = wrap(facing + Math.max(-turn, Math.min(turn, diff)));
        }

        // --- jumping
        coyote = onGround ? 0.1f : coyote - dt;
        jumpBuffer = jump ? 0.12f : jumpBuffer - dt;
        if (jumpBuffer > 0f && coyote > 0f) {
            vy = JUMP_VELOCITY * (1f - Math.min(0.5f, waterDepth * 0.5f));
            onGround = false;
            coyote = 0f;
            jumpBuffer = 0f;
        }
        vy -= GRAVITY * dt;

        // --- move & collide
        boolean wasGround = onGround;
        float px = x, pz = z;
        x += vx * dt;
        collideHorizontal(world, true);
        if (world.swimBlocked(x, z)) { x = px; vx = 0f; }
        z += vz * dt;
        collideHorizontal(world, false);
        if (world.swimBlocked(x, z)) { z = pz; vz = 0f; }
        x = Math.max(world.minX + RADIUS, Math.min(world.maxX - RADIUS, x));
        z = Math.max(world.minZ + RADIUS, Math.min(world.maxZ - RADIUS, z));

        float floor = world.groundHeight(x, z, RADIUS, y, wasGround ? STEP : 0.02f);
        y += vy * dt;
        if (floor < -SWIM_DEPTH && y < 0f) {
            // dropped or walked into deep water: surface and swim
            splashImpact = Math.min(-1f, vy);
            swimming = true;
            waterDepth = -floor;
            y = 0f;
            vy = 0f;
            onGround = false;
            anim = Anim.SWIM_TREAD;
            animFrame = 0;
            return;
        }
        // ceiling
        if (vy > 0f) {
            for (Box b : world.near(x, z)) {
                if (y + HEIGHT > b.minY && y < b.minY && World.circleOverlaps(b, x, z, RADIUS * 0.9f)) {
                    y = b.minY - HEIGHT;
                    vy = 0f;
                }
            }
        }
        if (y <= floor) {
            if (!wasGround && vy < -4f) landTimer = 0.14f;
            y = floor;
            vy = 0f;
            onGround = true;
        } else if (wasGround && vy <= 0f && y - floor < STEP + (float) Math.sqrt(vx * vx + vz * vz) * dt * 0.6f && y > floor) {
            // stick to the ground when walking down small steps
            y = floor;
            vy = 0f;
            onGround = true;
        } else {
            onGround = false;
        }
        // the sea: wade in the shallows, swim once it is deep enough (or when she falls into it)
        waterDepth = Math.max(0f, -floor);
        if (y < 0f && !wasGround && vy <= 0f && waterDepth > 0.2f && prevVy < -2.5f) splashImpact = prevVy;
        if (waterDepth > SWIM_DEPTH && y < 0.05f) {
            swimming = true;
            y = 0f;
            vy = 0f;
            onGround = false;
        }
        prevVy = vy;
        animate(dt);
    }

    private float prevVy, bob;

    /** Swimming at the surface: breaststroke when walking, front crawl when running, treading water. */
    private void swim(float dt, float mx, float mz, float mag, World world) {
        float target = 0f;
        if (mag > 0.08f) target = mag >= RUN_THRESHOLD ? CRAWL_SPEED : BREAST_SPEED * Math.max(0.55f, mag / RUN_THRESHOLD);
        float dx = mag > 1e-4f ? mx / mag : 0f, dz = mag > 1e-4f ? mz / mag : 0f;
        // water drag: slow to speed up, glides to a stop
        float accel = target > 0f ? 2.6f : 1.4f;
        vx = approach(vx, dx * target, accel * dt);
        vz = approach(vz, dz * target, accel * dt);
        if (mag > 0.08f) {
            float want = (float) Math.atan2(dx, dz);
            float diff = wrap(want - facing);
            float turn = 3.2f * dt;
            facing = wrap(facing + Math.max(-turn, Math.min(turn, diff)));
        }
        float px = x, pz = z;
        x += vx * dt;
        collideHorizontal(world, true);
        if (world.swimBlocked(x, z)) { x = px; vx = 0f; }
        z += vz * dt;
        collideHorizontal(world, false);
        if (world.swimBlocked(x, z)) { z = pz; vz = 0f; }
        x = Math.max(world.minX + RADIUS, Math.min(world.maxX - RADIUS, x));
        z = Math.max(world.minZ + RADIUS, Math.min(world.maxZ - RADIUS, z));
        // float at the surface with a gentle bob
        bob += dt;
        y = 0.03f * (float) Math.sin(bob * 2.1f);
        vy = 0f;
        onGround = false;
        float floor = world.groundHeight(x, z, RADIUS, 0.5f, 0.5f);
        waterDepth = Math.max(0f, -floor);
        if (waterDepth < STAND_DEPTH) {
            // her feet touch the bottom: stand up and wade out
            swimming = false;
            y = floor;
            onGround = true;
            animate(dt);
            return;
        }
        float speed = (float) Math.sqrt(vx * vx + vz * vz);
        if (speed < 0.3f) {
            anim = Anim.SWIM_TREAD;
            phase += dt / 1.5f;
        } else if (speed > (BREAST_SPEED + CRAWL_SPEED) * 0.5f) {
            anim = Anim.SWIM_FREE;
            phase += speed * dt / 2.3f;            // metres per full arm cycle
        } else {
            anim = Anim.SWIM_BREAST;
            phase += speed * dt / 1.6f;            // metres per stroke
        }
        phase -= (float) Math.floor(phase);
        animFrame = (int) (phase * anim.frames) % anim.frames;
    }

    private void collideHorizontal(World world, boolean axisX) {
        for (Box b : world.near(x, z)) {
            // ignore boxes we are standing on or that we can step onto
            if (b.maxY <= y + (onGround ? STEP : 0.02f)) continue;
            if (b.minY >= y + HEIGHT) continue;
            float cx = Math.max(b.minX, Math.min(x, b.maxX));
            float cz = Math.max(b.minZ, Math.min(z, b.maxZ));
            float dx = x - cx, dz = z - cz;
            float d2 = dx * dx + dz * dz;
            if (d2 >= RADIUS * RADIUS) continue;
            if (d2 > 1e-8f) {
                float d = (float) Math.sqrt(d2), push = RADIUS - d;
                x += dx / d * push;
                z += dz / d * push;
            } else {
                // centre inside the box: push out along the movement axis
                if (axisX) x = (x - b.minX < b.maxX - x) ? b.minX - RADIUS : b.maxX + RADIUS;
                else z = (z - b.minZ < b.maxZ - z) ? b.minZ - RADIUS : b.maxZ + RADIUS;
            }
        }
    }

    private void animate(float dt) {
        float speed = (float) Math.sqrt(vx * vx + vz * vz);
        if (landTimer > 0f) landTimer -= dt;
        if (!onGround) {
            anim = Anim.JUMP;
            animFrame = vy > 1.8f ? Animations.JUMP_RISE : (vy > -2.2f ? Animations.JUMP_APEX : Animations.JUMP_FALL);
            return;
        }
        if (landTimer > 0f && speed < 3f) {
            anim = Anim.JUMP;
            animFrame = Animations.JUMP_LAND;
            return;
        }
        if (speed < 0.25f) {
            if (anim != Anim.IDLE) idleTime = 0f;
            anim = Anim.IDLE;
            idleTime += dt;
            animFrame = (int) (idleTime * 1.6f) % Anim.IDLE.frames;
            return;
        }
        if (waterDepth > WADE_DEPTH) {
            // short, heavy strides through the water
            phase += speed * dt / 1.05f;
            phase -= (float) Math.floor(phase);
            anim = Anim.WADE;
            animFrame = (int) (phase * anim.frames) % anim.frames;
            return;
        }
        boolean run = speed > (WALK_SPEED + RUN_SPEED) * 0.5f && waterDepth < 0.25f;
        // one cycle = two strides
        float cycleLen = run ? 2.75f : 1.4f;   // matched to the baked stride length
        phase += speed * dt / cycleLen;
        phase -= (float) Math.floor(phase);
        anim = run ? Anim.RUN : Anim.WALK;
        animFrame = (int) (phase * anim.frames) % anim.frames;
    }

    private static float approach(float v, float t, float d) {
        if (v < t) return Math.min(t, v + d);
        return Math.max(t, v - d);
    }

    private static float wrap(float a) {
        while (a > Math.PI) a -= (float) (Math.PI * 2);
        while (a < -Math.PI) a += (float) (Math.PI * 2);
        return a;
    }
}
