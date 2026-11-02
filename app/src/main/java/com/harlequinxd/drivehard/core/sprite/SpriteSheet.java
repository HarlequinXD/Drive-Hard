package com.harlequinxd.drivehard.core.sprite;

/**
 * A baked directional sprite atlas. Rows are view directions, columns are animation frames.
 * Pixels are ARGB (0xAARRGGBB).
 */
public final class SpriteSheet {
    public final int[] pixels;
    public final int width, height;
    public final int frameW, frameH;
    public final int frames, directions;
    /** Pixel inside each frame that sits on the character's ground position. */
    public final int anchorX, anchorY;
    public final float pixelsPerMeter;

    public SpriteSheet(int[] pixels, int frameW, int frameH, int frames, int directions,
                       int anchorX, int anchorY, float pixelsPerMeter) {
        this.pixels = pixels;
        this.frameW = frameW;
        this.frameH = frameH;
        this.frames = frames;
        this.directions = directions;
        this.width = frameW * frames;
        this.height = frameH * directions;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.pixelsPerMeter = pixelsPerMeter;
    }

    /**
     * Picks the view direction row: 0 = facing the camera, increasing counter-clockwise
     * as seen from above (the character turning toward her left).
     *
     * @param facingYaw  character yaw (radians), facing vector = (sin, 0, cos)
     * @param cameraYaw  yaw of the camera's position around the character, same convention
     */
    public int directionFor(float facingYaw, float cameraYaw) {
        double rel = facingYaw - cameraYaw;
        double step = Math.PI * 2 / directions;
        int d = (int) Math.round(rel / step);
        d %= directions;
        if (d < 0) d += directions;
        return d;
    }
}
