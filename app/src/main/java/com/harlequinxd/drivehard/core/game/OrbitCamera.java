package com.harlequinxd.drivehard.core.game;

/**
 * Third-person orbit camera. {@code yaw} is the camera's angle around the target
 * (camera sits at target + dist * (sin(yaw)cos(pitch), sin(pitch), cos(yaw)cos(pitch))).
 */
public final class OrbitCamera {
    public float yaw = 0f;
    public float pitch = (float) Math.toRadians(22);
    public float distance = 5.5f;
    public float targetHeight = 1.1f;
    public float minPitch = (float) Math.toRadians(-5), maxPitch = (float) Math.toRadians(70);
    public float maxDistance = 60f;

    public float eyeX, eyeY, eyeZ, tx, ty, tz;
    private boolean init;

    public void orbit(float dYaw, float dPitch) {
        yaw += dYaw;
        pitch = Math.max(minPitch, Math.min(maxPitch, pitch + dPitch));
    }

    /** Smoothly follows the player position. */
    public void update(float px, float py, float pz, float dt) {
        float goalY = py + targetHeight;
        if (!init) { tx = px; ty = goalY; tz = pz; init = true; }
        float k = 1f - (float) Math.exp(-14f * dt);
        float ky = 1f - (float) Math.exp(-6f * dt);   // softer vertically so jumps feel weighty
        tx += (px - tx) * k;
        tz += (pz - tz) * k;
        ty += (goalY - ty) * ky;
        float cp = (float) Math.cos(pitch);
        eyeX = tx + distance * (float) Math.sin(yaw) * cp;
        eyeY = ty + distance * (float) Math.sin(pitch);
        eyeZ = tz + distance * (float) Math.cos(yaw) * cp;
        if (eyeY < 0.3f) eyeY = 0.3f;
    }

    /** Converts stick input (x right, y up) into a world-space move vector relative to the view. */
    public void toWorld(float sx, float sy, float[] out) {
        float s = (float) Math.sin(yaw), c = (float) Math.cos(yaw);
        // forward (away from camera) = (-s, -c), right = (c, -s)
        out[0] = c * sx - s * sy;
        out[1] = -s * sx - c * sy;
    }
}
