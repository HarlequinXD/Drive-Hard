package com.harlequinxd.drivehard.core.sprite;

/**
 * A baked directional sprite atlas. Frame index = dir * frames + frame, packed row-major,
 * {@code perRow} frames per atlas row. Atlas width is a power of two. Pixels are ARGB.
 */
public final class SpriteSheet {
    public final int[] pixels;
    public final int width, height;
    public final int frameW, frameH;
    public final int frames, directions, perRow;
    /** Pixel inside each frame that sits on the object's ground position. */
    public final int anchorX, anchorY;
    public final float pixelsPerMeter;

    public SpriteSheet(int[] pixels, int width, int height, int frameW, int frameH, int frames, int directions,
                       int perRow, int anchorX, int anchorY, float pixelsPerMeter) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.frameW = frameW;
        this.frameH = frameH;
        this.frames = frames;
        this.directions = directions;
        this.perRow = perRow;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.pixelsPerMeter = pixelsPerMeter;
    }

    public int frameX(int dir, int frame) { return ((dir * frames + frame) % perRow) * frameW; }
    public int frameY(int dir, int frame) { return ((dir * frames + frame) / perRow) * frameH; }

    /**
     * Picks the view direction: 0 = facing the camera, increasing counter-clockwise seen from above.
     *
     * @param facingYaw  object yaw (radians), facing vector = (sin, 0, cos)
     * @param viewYaw    yaw of the direction from the object toward the camera
     */
    public int directionFor(float facingYaw, float viewYaw) {
        double rel = facingYaw - viewYaw;
        double step = Math.PI * 2 / directions;
        int d = (int) Math.round(rel / step);
        d %= directions;
        if (d < 0) d += directions;
        return d;
    }

    // billboard extents in metres
    public float left() { return -anchorX / pixelsPerMeter; }
    public float right() { return (frameW - anchorX) / pixelsPerMeter; }
    public float bottom() { return -(frameH - anchorY) / pixelsPerMeter; }
    public float top() { return anchorY / pixelsPerMeter; }
}
