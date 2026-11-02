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
    public static final float WALK_SPEED = 1.9f, RUN_SPEED = 5.6f, RUN_THRESHOLD = 0.72f;
    public static final float GRAVITY = 22f, JUMP_VELOCITY = 6.8f;

    public float x, y, z, vx, vy, vz;
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

        // --- horizontal velocity
        float target = 0f;
        if (mag > 0.08f) target = mag >= RUN_THRESHOLD ? RUN_SPEED : WALK_SPEED * Math.max(0.6f, mag / RUN_THRESHOLD);
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
            vy = JUMP_VELOCITY;
            onGround = false;
            coyote = 0f;
            jumpBuffer = 0f;
        }
        vy -= GRAVITY * dt;

        // --- move & collide
        boolean wasGround = onGround;
        x += vx * dt;
        collideHorizontal(world, true);
        z += vz * dt;
        collideHorizontal(world, false);
        float lim = world.halfSize - RADIUS;
        x = Math.max(-lim, Math.min(lim, x));
        z = Math.max(-lim, Math.min(lim, z));

        float floor = world.groundHeight(x, z, RADIUS, y, wasGround ? STEP : 0.02f);
        y += vy * dt;
        // ceiling
        if (vy > 0f) {
            for (Box b : world.boxes) {
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
        } else if (wasGround && vy <= 0f && y - floor < STEP && y > floor) {
            // stick to the ground when walking down small steps
            y = floor;
            vy = 0f;
            onGround = true;
        } else {
            onGround = false;
        }

        animate(dt);
    }

    private void collideHorizontal(World world, boolean axisX) {
        for (Box b : world.boxes) {
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
        boolean run = speed > (WALK_SPEED + RUN_SPEED) * 0.5f;
        // one cycle = two strides
        float cycleLen = run ? 2.7f : 1.45f;
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
