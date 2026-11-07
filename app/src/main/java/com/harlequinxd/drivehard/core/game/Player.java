package com.harlequinxd.drivehard.core.game;

import com.harlequinxd.drivehard.core.model.Animations;
import com.harlequinxd.drivehard.core.model.Animations.Anim;
import com.harlequinxd.drivehard.core.model.Pose;

import static com.harlequinxd.drivehard.core.model.CharacterModel.*;

/**
 * On-foot character controller with momentum-based locomotion.
 *
 * Movement: she runs where she faces. The stick turns her (quickly when walking, in wider
 * arcs when running); her velocity follows her facing through grip, so fast turns carve and
 * drift a little. She builds up to a run, slides to a stop, and plants and skids when told to
 * reverse at speed. In the air she keeps her momentum with only a little steering.
 *
 * Reactions: she leans into turns, pitches forward when she speeds up and back when she
 * brakes; running into a wall, a car or a box knocks her back with a stagger (harder hits,
 * longer stagger); pushing against a wall she braces with her hands; a car driving into her
 * shoves her away. Landings dip her knees by how hard she came down, and a big drop costs her
 * a moment. Swimming, strokes surge and glide, she rolls into turns and bumps off the shore.
 *
 * The animation is a blend: idle into walk into run on one stride phase, scaled by speed, with
 * all of the above layered on as a procedural pose ({@link #pose()}).
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

    /** Walk to run blend range (m/s). */
    private static final float BLEND_LO = 3.0f, BLEND_HI = 5.4f;

    public float x, y, z, vx, vy, vz;
    /** Swimming in deep water (y is then the water surface). */
    public boolean swimming;
    /** Depth of water where she stands or swims (0 on dry land). */
    public float waterDepth;
    /** Vertical speed when she hit the water this frame (negative), 0 otherwise: for the big splash. */
    public float splashImpact;
    /** How hard she ran into something this frame (m/s), 0 otherwise: for the thump and the camera. */
    public float bumpImpact;
    /** True on the frame she starts a skid (planting her feet to turn or stop at speed). */
    public boolean skidStart;
    /** Animation cycle phase 0..1 of the current loop (strokes, strides), for effects. */
    public float cyclePhase() { return phase; }

    /** Where she is in the current animation, 0..1 (continuous, for the sprite puppet). */
    public float animPhase() {
        float p = anim == Anim.IDLE ? idleTime * 1.6f / Anim.IDLE.frames : phase;
        return p - (float) Math.floor(p);
    }
    public float facing;               // yaw, facing vector = (sin, 0, cos)
    public boolean onGround = true;

    public Anim anim = Anim.IDLE;
    public int animFrame;              // frame within anim
    private float phase;               // 0..1 cycle phase for loops
    private float idleTime;
    private float coyote, jumpBuffer;

    // --- the locomotion layer
    private float lastFacing, yawRate;          // rad/s, smoothed (+ = turning to her left)
    private float lean, pitch;                  // body roll into turns, pitch from acceleration
    private float alongPrev;
    private float skid;                         // 0..1 planting / sliding
    private boolean skidding;
    private float bumpT, bumpDur, bumpStr;      // the stagger after a knock
    private float bumpFwd, bumpSide;            // which way the knock pushed her (her frame)
    private float brace;                        // 0..1 hands against a wall
    private float dip;                          // 0..1 knees bending from a landing
    private float heavy;                        // 0..1 a hard landing's lost control
    private float airTime;
    private float runBlend;                     // 0 walk .. 1 run
    private float pushX, pushZ;                 // collision push this frame
    private float surge;                        // swim stroke surge 0..1

    public Player(float x, float z) { this.x = x; this.z = z; }

    /**
     * @param mx,mz  desired world move direction scaled by stick deflection (length 0..1)
     * @param jump   true on the frame the jump button was pressed
     */
    public void update(float dt, float mx, float mz, boolean jump, World world) {
        float mag = (float) Math.sqrt(mx * mx + mz * mz);
        if (mag > 1f) { mx /= mag; mz /= mag; mag = 1f; }
        splashImpact = 0f;
        bumpImpact = 0f;
        skidStart = false;
        if (dt <= 0f) return;
        if (swimming) { swim(dt, mx, mz, mag, world); return; }

        float speed = (float) Math.sqrt(vx * vx + vz * vz);
        bumpT = Math.max(0f, bumpT - dt);
        heavy = Math.max(0f, heavy - dt * 2.2f);
        // how much say she has over her legs: none at the start of a knock, back as it fades
        float stagger = bumpT > 0f ? bumpStr * (bumpT / bumpDur) : 0f;
        float control = Math.max(0f, 1f - stagger * 1.3f) * (1f - heavy * 0.85f);

        // --- target speed from the stick
        float target = 0f;
        if (mag > 0.08f) target = mag >= RUN_THRESHOLD ? RUN_SPEED : WALK_SPEED * Math.max(0.6f, mag / RUN_THRESHOLD);
        // wading: no running once the water is past the knees; waist deep it is a slow push
        if (waterDepth > 0.25f && onGround && target > 0f) {
            float k = Math.min(1f, (waterDepth - 0.25f) / 0.5f);
            float cap = WALK_SPEED + (WADE_SPEED - WALK_SPEED) * k;
            target = Math.min(target, cap * Math.max(0.6f, Math.min(1f, mag / RUN_THRESHOLD)));
        } else if (waterDepth > 0.05f && onGround) target *= 1f - waterDepth * 0.8f;
        target *= control;
        float dx = mag > 1e-4f ? mx / mag : 0f, dz = mag > 1e-4f ? mz / mag : 0f;

        // --- turning: fast on her feet when slow, wide arcs at a run, little in the air
        float diff = 0f;
        if (mag > 0.08f) {
            float want = (float) Math.atan2(dx, dz);
            diff = wrap(want - facing);
            // reversing at speed: plant the feet and skid before turning round
            if (onGround && !skidding && speed > 4.2f && Math.abs(diff) > 2.1f && control > 0.5f) {
                skidding = true;
                skidStart = true;
            }
            float sp = Math.min(1f, speed / RUN_SPEED);
            float rate = onGround ? 14f - 7.5f * sp : 2.5f;
            if (skidding) rate = speed > 2f ? 1.2f : 18f;          // committed until she has stopped
            rate *= 0.4f + 0.6f * control;
            float turn = rate * dt;
            facing = wrap(facing + Math.max(-turn, Math.min(turn, diff)));
        }
        if (skidding && (speed < 1.6f || !onGround)) skidding = false;

        // --- velocity follows her facing: along it she speeds up or slows, sideways grip pulls
        // the velocity round (wet ground and the air grip less)
        float fx = (float) Math.sin(facing), fz = (float) Math.cos(facing);
        float along = vx * fx + vz * fz, side = vx * fz - vz * fx;
        float aligned = mag > 0.08f ? (float) Math.cos(wrap((float) Math.atan2(dx, dz) - facing)) : 0f;
        float tAlong = skidding ? 0f : target * Math.max(0f, aligned) * Math.max(0f, aligned);
        float up, down, grip;
        if (onGround) {
            boolean wet = waterDepth > 0.05f;
            up = (along > WALK_SPEED ? 10.5f : 15f) * (wet ? 0.55f : 1f);
            down = skidding ? 17f : along > BLEND_LO ? 14f : 20f;
            if (wet) down *= 1.2f;
            grip = wet ? 18f : 30f - 8f * Math.min(1f, speed / RUN_SPEED);
        } else {
            up = 3.5f; down = 1.5f; grip = 2.5f;
        }
        if (stagger > 0.05f) { grip *= 0.25f; down *= 0.5f; }     // knocked: sliding on her momentum
        along = approach(along, tAlong, (tAlong > along ? up : down) * dt);
        side = approach(side, 0f, grip * dt);
        vx = fx * along + fz * side;
        vz = fz * along - fx * side;

        // --- jumping
        coyote = onGround ? 0.1f : coyote - dt;
        jumpBuffer = jump ? 0.12f : jumpBuffer - dt;
        if (jumpBuffer > 0f && coyote > 0f && control > 0.35f) {
            vy = JUMP_VELOCITY * (1f - Math.min(0.5f, waterDepth * 0.5f)) * (0.85f + 0.15f * Math.min(1f, speed / RUN_SPEED));
            onGround = false;
            coyote = 0f;
            jumpBuffer = 0f;
            skidding = false;
        }
        vy -= GRAVITY * dt;

        // --- move & collide
        boolean wasGround = onGround;
        float px = x, pz = z, pvx = vx, pvz = vz;
        pushX = pushZ = 0f;
        x += vx * dt;
        collideHorizontal(world, true);
        if (world.swimBlocked(x, z)) { pushX += px - x; x = px; }
        z += vz * dt;
        collideHorizontal(world, false);
        if (world.swimBlocked(x, z)) { pushZ += pz - z; z = pz; }
        x = Math.max(world.minX + RADIUS, Math.min(world.maxX - RADIUS, x));
        z = Math.max(world.minZ + RADIUS, Math.min(world.maxZ - RADIUS, z));
        react(dt, pvx, pvz, mx, mz, mag);

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
            skidding = false;
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
                    knock(0f, -1f, 0.25f, 0.25f);                  // a knock on the head
                }
            }
        }
        float fallSpeed = -vy;
        if (y <= floor) {
            if (!wasGround) land(fallSpeed);
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
        airTime = onGround ? 0f : airTime + dt;
        // the sea: wade in the shallows, swim once it is deep enough (or when she falls into it)
        waterDepth = Math.max(0f, -floor);
        if (y < 0f && !wasGround && vy <= 0f && waterDepth > 0.2f && prevVy < -2.5f) splashImpact = prevVy;
        if (waterDepth > SWIM_DEPTH && y < 0.05f) {
            swimming = true;
            y = 0f;
            vy = 0f;
            onGround = false;
            skidding = false;
        }
        prevVy = vy;
        body(dt, along);
        animate(dt);
    }

    private float prevVy, bob;

    /** Touching down: knees take the fall; a long drop costs her a moment. */
    private void land(float fallSpeed) {
        dip = Math.max(dip, Math.min(1f, Math.max(0.2f, (fallSpeed - 2f) / 11f)));
        if (fallSpeed > 12.5f) {
            heavy = Math.min(1f, (fallSpeed - 12.5f) / 6f + 0.5f);
            vx *= 0.45f;
            vz *= 0.45f;
        }
    }

    /**
     * After moving: what she ran into. The push out of the scenery is the normal of what she hit;
     * her speed into it (or the speed of whatever shoved her) sets how hard she is knocked back.
     */
    private void react(float dt, float pvx, float pvz, float mx, float mz, float mag) {
        float pl = (float) Math.sqrt(pushX * pushX + pushZ * pushZ);
        if (pl < 1e-5f || pl > 0.9f) { brace = Math.max(0f, brace - dt * 4f); return; }
        float nx = pushX / pl, nz = pushZ / pl;                      // away from what she hit
        float into = -(pvx * nx + pvz * nz);                         // her speed into it
        float shove = pl / dt - Math.max(0f, into);                  // something moving into her
        if (shove > 2.5f) {
            // a car (or anything moving) ran into her: thrown off her feet a little
            float k = Math.min(9f, shove);
            vx += nx * k * 0.9f;
            vz += nz * k * 0.9f;
            vy = Math.max(vy, Math.min(4f, k * 0.35f));
            onGround = false;
            knockWorld(nx, nz, Math.min(1f, 0.4f + k / 10f), 0.5f + k * 0.06f);
            bumpImpact = k;
            return;
        }
        if (into > 2.8f) {
            // ran into it: bounce back a little, the rest of her speed scrubbed along it
            float vn = vx * nx + vz * nz;
            float rebound = into * 0.32f;
            vx += nx * (rebound - vn);
            vz += nz * (rebound - vn);
            vx *= 0.6f;
            vz *= 0.6f;
            float str = Math.min(1f, (into - 2.8f) / 4f + 0.22f);
            knockWorld(nx, nz, str, 0.28f + 0.6f * str);
            bumpImpact = into;
            skidding = false;
        } else if (onGround && mag > 0.3f && -(mx * nx + mz * nz) / Math.max(1e-3f, mag) > 0.55f) {
            // walking into a wall: hands up against it
            brace = Math.min(1f, brace + dt * 5f);
            braceFwd = -(nx * (float) Math.sin(facing) + nz * (float) Math.cos(facing));
            return;
        }
        brace = Math.max(0f, brace - dt * 4f);
    }

    private float braceFwd;

    /** A knock along world (nx, nz) (the direction she is pushed), strength 0..1 for dur seconds. */
    private void knockWorld(float nx, float nz, float str, float dur) {
        float fx = (float) Math.sin(facing), fz = (float) Math.cos(facing);
        // her frame: forward = facing, left = (fz, -fx) (+ yaw turns to her left)
        knock(nx * fx + nz * fz, nx * fz - nz * fx, str, dur);
    }

    private void knock(float fwd, float left, float str, float dur) {
        if (bumpT > 0f && bumpStr * (bumpT / bumpDur) > str) return;   // already reeling harder
        bumpFwd = fwd;
        bumpSide = left;
        bumpStr = str;
        bumpDur = dur;
        bumpT = dur;
    }

    /** The lean, pitch, skid and landing layers, eased toward where her motion puts them. */
    private void body(float dt, float along) {
        float yr = wrap(facing - lastFacing) / dt;
        lastFacing = facing;
        yawRate += (yr - yawRate) * Math.min(1f, dt * 12f);
        float speed = (float) Math.sqrt(vx * vx + vz * vz);
        // into the turn: the angle a runner leans at, softened
        float tLean = onGround ? (float) Math.atan(speed * yawRate / 9.8f) * 0.65f : yawRate * 0.04f;
        tLean = Math.max(-0.42f, Math.min(0.42f, tLean));
        lean += (tLean - lean) * Math.min(1f, dt * 9f);
        // forward when speeding up, back when braking
        float acc = (along - alongPrev) / dt;
        alongPrev = along;
        float tPitch = onGround ? Math.max(-0.3f, Math.min(0.26f, acc * 0.03f)) : 0f;
        pitch += (tPitch - pitch) * Math.min(1f, dt * 7f);
        float tSkid = skidding ? 1f : 0f;
        skid += (tSkid - skid) * Math.min(1f, dt * (skidding ? 14f : 6f));
        dip = Math.max(0f, dip - dt * (dip > 0.5f ? 2.2f : 3.5f));
        float tRun = Math.max(0f, Math.min(1f, (speed - BLEND_LO) / (BLEND_HI - BLEND_LO)));
        if (waterDepth > 0.25f) tRun = 0f;
        runBlend += (tRun - runBlend) * Math.min(1f, dt * 8f);
    }

    /** Swimming at the surface: breaststroke when walking, front crawl when running, treading water. */
    private void swim(float dt, float mx, float mz, float mag, World world) {
        bumpT = Math.max(0f, bumpT - dt);
        dip = 0f; skid = 0f; skidding = false; brace = 0f; heavy = 0f;
        float target = 0f;
        if (mag > 0.08f) target = mag >= RUN_THRESHOLD ? CRAWL_SPEED : BREAST_SPEED * Math.max(0.55f, mag / RUN_THRESHOLD);
        float dx = mag > 1e-4f ? mx / mag : 0f, dz = mag > 1e-4f ? mz / mag : 0f;
        // each stroke drives her on and she glides between them
        float s;
        if (anim == Anim.SWIM_BREAST) s = 0.45f + 0.75f * bell(phase, 0.22f, 0.1f) + 0.95f * bell(phase, 0.7f, 0.08f);
        else if (anim == Anim.SWIM_FREE) s = 0.85f + 0.3f * Math.abs((float) Math.sin(phase * Math.PI * 2));
        else s = 1f;
        surge += (s - surge) * Math.min(1f, dt * 10f);
        float accel = target > 0f ? 2.6f * surge : 1.4f;
        float speedNow = (float) Math.sqrt(vx * vx + vz * vz);
        // she goes where she faces in the water too, turning with a sweep of the arms
        if (mag > 0.08f) {
            float want = (float) Math.atan2(dx, dz);
            float diff = wrap(want - facing);
            float turn = (3.4f - Math.min(1.2f, speedNow)) * dt;
            facing = wrap(facing + Math.max(-turn, Math.min(turn, diff)));
        }
        float fx = (float) Math.sin(facing), fz = (float) Math.cos(facing);
        float along = vx * fx + vz * fz, side = vx * fz - vz * fx;
        float aligned = mag > 0.08f ? Math.max(0f, (float) Math.cos(wrap((float) Math.atan2(dx, dz) - facing))) : 0f;
        along = approach(along, target * aligned * Math.min(1.25f, surge), accel * dt);
        side = approach(side, 0f, 2.2f * dt);
        vx = fx * along + fz * side;
        vz = fz * along - fx * side;
        float px = x, pz = z, pvx = vx, pvz = vz;
        pushX = pushZ = 0f;
        x += vx * dt;
        collideHorizontal(world, true);
        if (world.swimBlocked(x, z)) { pushX += px - x; x = px; }
        z += vz * dt;
        collideHorizontal(world, false);
        if (world.swimBlocked(x, z)) { pushZ += pz - z; z = pz; }
        x = Math.max(world.minX + RADIUS, Math.min(world.maxX - RADIUS, x));
        z = Math.max(world.minZ + RADIUS, Math.min(world.maxZ - RADIUS, z));
        float pl = (float) Math.sqrt(pushX * pushX + pushZ * pushZ);
        if (pl > 1e-5f && pl < 0.9f) {
            float nx = pushX / pl, nz = pushZ / pl, into = -(pvx * nx + pvz * nz);
            float vn = vx * nx + vz * nz;
            vx += nx * (into * 0.25f - vn);
            vz += nz * (into * 0.25f - vn);
            if (into > 0.7f) { knockWorld(nx, nz, Math.min(0.7f, into * 0.35f), 0.45f); bumpImpact = into; }
        }
        // float at the surface with a gentle bob (a little lift with each kick)
        bob += dt;
        y = 0.03f * (float) Math.sin(bob * 2.1f) + (anim == Anim.SWIM_BREAST ? 0.03f * bell(phase, 0.25f, 0.12f) : 0f);
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
        body(dt, along);
        float speed = (float) Math.sqrt(vx * vx + vz * vz);
        if (speed < 0.3f) {
            anim = Anim.SWIM_TREAD;
            phase += dt / 1.5f;
        } else if (target > (BREAST_SPEED + CRAWL_SPEED) * 0.5f || speed > (BREAST_SPEED + CRAWL_SPEED) * 0.55f) {
            if (anim != Anim.SWIM_FREE) anim = Anim.SWIM_FREE;
            phase += Math.max(0.6f, speed) * dt / 2.3f;           // metres per full arm cycle
        } else {
            anim = Anim.SWIM_BREAST;
            phase += Math.max(0.5f, speed) * dt / 1.6f;           // a stroke even from a standstill
        }
        phase -= (float) Math.floor(phase);
        animFrame = (int) (phase * anim.frames) % anim.frames;
    }

    private static float bell(float p, float c, float w) {
        float d = p - c;
        d -= Math.round(d);
        return (float) Math.exp(-(d * d) / (2 * w * w));
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
            float ox = x, oz = z;
            if (d2 > 1e-8f) {
                float d = (float) Math.sqrt(d2), push = RADIUS - d;
                x += dx / d * push;
                z += dz / d * push;
            } else {
                // centre inside the box: push out along the movement axis
                if (axisX) x = (x - b.minX < b.maxX - x) ? b.minX - RADIUS : b.maxX + RADIUS;
                else z = (z - b.minZ < b.maxZ - z) ? b.minZ - RADIUS : b.maxZ + RADIUS;
            }
            pushX += x - ox;
            pushZ += z - oz;
        }
    }

    private void animate(float dt) {
        float speed = (float) Math.sqrt(vx * vx + vz * vz);
        if (!onGround) {
            anim = Anim.JUMP;
            animFrame = vy > 1.8f ? Animations.JUMP_RISE : (vy > -2.2f ? Animations.JUMP_APEX : Animations.JUMP_FALL);
            return;
        }
        if (speed < 0.25f && skid < 0.3f) {
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
        // one cycle = two strides, its length eased from the walk's to the run's so the feet
        // keep planted through the blend; a skid freezes the stride with the feet out front
        float cycleLen = 1.4f + (2.75f - 1.4f) * runBlend;
        if (skid < 0.5f) phase += speed * dt / cycleLen;
        phase -= (float) Math.floor(phase);
        anim = runBlend > 0.5f ? Anim.RUN : Anim.WALK;
        animFrame = (int) (phase * anim.frames) % anim.frames;
    }

    // ------------------------------------------------------------------ the pose

    /**
     * Her pose this frame: the blended cycle (idle, walk, run, or the swim and jump poses) with
     * the leans, the skid, the stagger, the brace and the landing dip layered on.
     */
    public Pose pose() {
        Pose o;
        float speed = (float) Math.sqrt(vx * vx + vz * vz);
        if (swimming) {
            o = Animations.poseAt(anim, animPhase(), animFrame);
            // roll into turns: about the body's length when stroking, sideways when upright
            float roll = Math.max(-0.5f, Math.min(0.5f, yawRate * 0.35f));
            if (anim == Anim.SWIM_TREAD) { o.rz[PELVIS] -= roll * 0.6f; o.ry[HEAD] += yawRate * 0.15f; }
            else { o.ry[PELVIS] += roll; o.ry[CHEST] += roll * 0.4f; o.rz[HEAD] += yawRate * 0.1f; }
            stagger(o, 0.6f);
            return o;
        }
        if (anim == Anim.JUMP) {
            if (onGround) return Animations.poseAt(anim, 0f, animFrame);     // scripted (hopping into a car)
            o = air();
        } else if (anim == Anim.WADE) {
            o = Animations.poseAt(anim, animPhase(), animFrame);
        } else {
            float p = phase;
            o = Animations.blend(Animations.walkPose(p), Animations.runPose(p), runBlend);
            // starting off and coming to a stop: the stride grows out of standing still
            float still = 1f - Math.min(1f, speed / 1.1f);
            if (anim == Anim.IDLE) still = 1f;
            if (still > 0f) o = Animations.blend(o, Animations.poseAt(Anim.IDLE, (idleTime * 1.6f / Anim.IDLE.frames) % 1f, 0), still);
        }
        // turns: lean the whole body into them (feet swing out under her), head stays level
        // (pivoting at the feet, not the hips: the pelvis moves over into the turn)
        o.rz[PELVIS] -= lean;
        o.rootX += 0.75f * (float) Math.sin(lean);
        o.rz[HEAD] += lean * 0.55f;
        o.ry[HEAD] += Math.max(-0.35f, Math.min(0.35f, yawRate * 0.06f));        // looking into the turn
        // the outside arm swings wider in a hard turn, for balance
        float hard = Math.min(1f, Math.abs(lean) / 0.35f);
        if (lean > 0f) o.rz[UARM_R] -= 0.4f * hard; else o.rz[UARM_L] += 0.4f * hard;
        // speeding up and braking
        o.rx[CHEST] += pitch * 0.9f;
        o.rx[PELVIS] += pitch * 0.35f;
        o.rx[HEAD] -= pitch * 0.5f;
        // the skid: leaning right back, the leading leg braced out in front, arms out
        if (skid > 0.01f) {
            float k = skid;
            o.rx[PELVIS] -= 0.18f * k;
            o.rx[CHEST] -= 0.2f * k;
            o.rx[HEAD] += 0.2f * k;
            o.rx[THIGH_L] = mix(o.rx[THIGH_L], -0.75f, k); o.rx[SHIN_L] = mix(o.rx[SHIN_L], 0.15f, k);
            o.rx[FOOT_L] = mix(o.rx[FOOT_L], 0.3f, k);
            o.rx[THIGH_R] = mix(o.rx[THIGH_R], 0.1f, k); o.rx[SHIN_R] = mix(o.rx[SHIN_R], 0.9f, k);
            o.rx[UARM_L] = mix(o.rx[UARM_L], -0.6f, k); o.rx[UARM_R] = mix(o.rx[UARM_R], -0.5f, k);
            o.rz[UARM_L] = mix(o.rz[UARM_L], 0.9f, k); o.rz[UARM_R] = mix(o.rz[UARM_R], -0.9f, k);
        }
        // landing: the knees take it (a hard one folds her right down)
        if (dip > 0.01f && onGround) {
            float k = (float) Math.sin(Math.min(1f, dip) * Math.PI * 0.5f);
            if (speed < 2.5f && dip > 0.35f) {
                Pose crouch = Animations.poseAt(Anim.JUMP, 0f, Animations.JUMP_LAND);
                o = Animations.blend(o, crouch, Math.min(1f, (dip - 0.2f) * 1.4f));
            } else {
                o.rx[THIGH_L] -= 0.45f * k; o.rx[THIGH_R] -= 0.45f * k;
                o.rx[SHIN_L] += 0.85f * k; o.rx[SHIN_R] += 0.85f * k;
                o.rx[FOOT_L] -= 0.4f * k; o.rx[FOOT_R] -= 0.4f * k;
                o.rx[CHEST] += 0.3f * k;
                o.rz[UARM_L] += 0.35f * k; o.rz[UARM_R] -= 0.35f * k;
            }
        }
        if (heavy > 0.01f) {
            o.rx[CHEST] += 0.35f * heavy;
            o.rx[HEAD] += 0.2f * heavy;
        }
        // hands up against a wall
        if (brace > 0.01f && braceFwd > 0.3f) {
            float k = brace * Math.min(1f, (braceFwd - 0.3f) * 2f);
            o.rx[UARM_L] = mix(o.rx[UARM_L], -1.35f, k); o.rx[UARM_R] = mix(o.rx[UARM_R], -1.35f, k);
            o.rz[UARM_L] = mix(o.rz[UARM_L], 0.25f, k); o.rz[UARM_R] = mix(o.rz[UARM_R], -0.25f, k);
            o.rx[FARM_L] = mix(o.rx[FARM_L], -0.7f, k); o.rx[FARM_R] = mix(o.rx[FARM_R], -0.7f, k);
            o.rx[CHEST] += 0.12f * k;
            o.rx[HEAD] -= 0.1f * k;
        }
        stagger(o, 1f);
        return o;
    }

    /** In the air: the jump poses blended by vertical speed, leaning with the run, arms flailing on a long drop. */
    private Pose air() {
        Pose rise = Animations.poseAt(Anim.JUMP, 0f, Animations.JUMP_RISE);
        Pose apex = Animations.poseAt(Anim.JUMP, 0f, Animations.JUMP_APEX);
        Pose fall = Animations.poseAt(Anim.JUMP, 0f, Animations.JUMP_FALL);
        Pose o = vy > 0f ? Animations.blend(apex, rise, Math.min(1f, vy / 4f))
                : Animations.blend(apex, fall, Math.min(1f, -vy / 5f));
        float speed = (float) Math.sqrt(vx * vx + vz * vz);
        float fwd = Math.min(1f, speed / RUN_SPEED);
        // a running leap stretches out: leading leg forward, trailing leg back, body over it
        o.rx[THIGH_L] -= 0.35f * fwd; o.rx[THIGH_R] += 0.35f * fwd;
        o.rx[CHEST] += 0.18f * fwd;
        if (airTime > 0.55f) {
            // falling a long way: arms windmilling, legs cycling
            float t = (airTime - 0.55f) * 9f, k = Math.min(1f, (airTime - 0.55f) * 3f);
            o.rx[UARM_L] = mix(o.rx[UARM_L], -1.6f + 1.1f * (float) Math.sin(t), k);
            o.rx[UARM_R] = mix(o.rx[UARM_R], -1.6f - 1.1f * (float) Math.sin(t), k);
            o.rz[UARM_L] = mix(o.rz[UARM_L], 1.0f, k); o.rz[UARM_R] = mix(o.rz[UARM_R], -1.0f, k);
            o.rx[THIGH_L] = mix(o.rx[THIGH_L], -0.5f + 0.4f * (float) Math.sin(t * 0.8f), k);
            o.rx[THIGH_R] = mix(o.rx[THIGH_R], -0.5f - 0.4f * (float) Math.sin(t * 0.8f), k);
            o.rx[CHEST] = mix(o.rx[CHEST], -0.15f, k);
        }
        return o;
    }

    /** The knock: thrown along the push, arms up, head whipping, a stagger step back. */
    private void stagger(Pose o, float scale) {
        if (bumpT <= 0f) return;
        float t = 1f - bumpT / bumpDur;                           // 0 at the hit .. 1 recovered
        // a quick snap then a wobble back to upright
        float env = (float) Math.sin(Math.min(1f, t * 4f) * Math.PI * 0.5f) * (1f - t) * (1f - t);
        float wob = (float) Math.sin(t * 13f) * (1f - t) * 0.25f;
        float k = bumpStr * scale * (env + wob);
        // pushed back (bumpFwd < 0) leans her back, pushed sideways tips her the other way
        o.rx[CHEST] += bumpFwd * 0.6f * k;
        o.rx[PELVIS] += bumpFwd * 0.25f * k;
        o.rx[HEAD] -= bumpFwd * 0.7f * k;                         // the head lags the body
        o.rz[PELVIS] -= bumpSide * 0.45f * k;
        o.rz[CHEST] -= bumpSide * 0.3f * k;
        o.rz[HEAD] += bumpSide * 0.4f * k;
        float arms = Math.min(1f, k * 1.6f);
        if (bumpFwd < -0.3f) {
            // hit something in front: hands up from the impact
            o.rx[UARM_L] = mix(o.rx[UARM_L], -1.2f, arms); o.rx[UARM_R] = mix(o.rx[UARM_R], -1.0f, arms);
            o.rx[FARM_L] = mix(o.rx[FARM_L], -1.3f, arms); o.rx[FARM_R] = mix(o.rx[FARM_R], -1.4f, arms);
            o.rz[UARM_L] = mix(o.rz[UARM_L], 0.5f, arms); o.rz[UARM_R] = mix(o.rz[UARM_R], -0.5f, arms);
        } else {
            // knocked from behind or the side: arms thrown out to catch her balance
            o.rz[UARM_L] = mix(o.rz[UARM_L], 1.1f, arms); o.rz[UARM_R] = mix(o.rz[UARM_R], -1.1f, arms);
            o.rx[UARM_L] = mix(o.rx[UARM_L], -0.5f, arms); o.rx[UARM_R] = mix(o.rx[UARM_R], -0.5f, arms);
        }
        if (!swimming && onGround) {
            // a stagger step: one leg goes the way she is pushed
            float step = k * 0.6f;
            o.rx[THIGH_R] += -bumpFwd * step;
            o.rx[SHIN_R] += Math.abs(bumpFwd) * step * 0.8f;
            o.rz[THIGH_L] += bumpSide * step * 0.5f;
        }
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }

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
