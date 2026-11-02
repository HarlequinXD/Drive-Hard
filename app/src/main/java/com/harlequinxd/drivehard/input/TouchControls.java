package com.harlequinxd.drivehard.input;

import android.view.MotionEvent;

/**
 * Multi-touch on-screen controls, written by the UI thread and read by the GL thread.
 *  - Left side: floating virtual joystick (appears where the thumb lands). Push past ~70% to run.
 *  - Right side: drag to orbit the camera.
 *  - Jump button bottom-right.
 */
public final class TouchControls {
    public final float density;
    private int width = 1, height = 1;

    // joystick
    private int joyId = -1;
    private float joyCX, joyCY, joyX, joyY;
    // camera drag
    private int camId = -1;
    private float camLastX, camLastY, camDX, camDY;
    // jump
    private int jumpId = -1;
    private boolean jumpQueued;

    /** Snapshot read by the game each frame. */
    public static final class State {
        public float stickX, stickY;      // -1..1, y up
        public float camDX, camDY;        // pixels since last frame
        public boolean jump, jumpHeld;
        public boolean joyActive;
        public float joyCX, joyCY, knobX, knobY;
    }

    public TouchControls(float density) { this.density = density; }

    public float joyRadius() { return 62f * density; }
    public float jumpRadius() { return 50f * density; }
    public float jumpX() { return width - 92f * density; }
    public float jumpY() { return height - 92f * density; }
    public float joyHomeX() { return 120f * density; }
    public float joyHomeY() { return height - 120f * density; }

    public synchronized void setSize(int w, int h) { width = w; height = h; }

    public synchronized void onTouch(MotionEvent e) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                down(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) move(e.getPointerId(i), e.getX(i), e.getY(i));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                up(e.getPointerId(i));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                joyId = camId = jumpId = -1;
                break;
            default:
                break;
        }
    }

    private void down(int id, float x, float y) {
        float jx = x - jumpX(), jy = y - jumpY();
        float jr = jumpRadius() * 1.25f;
        if (jumpId < 0 && jx * jx + jy * jy < jr * jr) {
            jumpId = id;
            jumpQueued = true;
            return;
        }
        if (joyId < 0 && x < width * 0.45f) {
            joyId = id;
            joyCX = x; joyCY = y; joyX = x; joyY = y;
            return;
        }
        if (camId < 0) {
            camId = id;
            camLastX = x; camLastY = y;
        }
    }

    private void move(int id, float x, float y) {
        if (id == joyId) {
            joyX = x; joyY = y;
            // drag the floating centre along if the thumb goes far past the rim
            float dx = x - joyCX, dy = y - joyCY, r = joyRadius();
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > r * 1.4f) {
                float k = (d - r * 1.4f) / d;
                joyCX += dx * k; joyCY += dy * k;
            }
        } else if (id == camId) {
            camDX += x - camLastX;
            camDY += y - camLastY;
            camLastX = x; camLastY = y;
        }
    }

    private void up(int id) {
        if (id == joyId) joyId = -1;
        if (id == camId) camId = -1;
        if (id == jumpId) jumpId = -1;
    }

    public synchronized void read(State s) {
        s.joyActive = joyId >= 0;
        if (s.joyActive) {
            float r = joyRadius();
            float dx = (joyX - joyCX) / r, dy = (joyY - joyCY) / r;
            float m = (float) Math.sqrt(dx * dx + dy * dy);
            if (m > 1f) { dx /= m; dy /= m; }
            s.stickX = dx;
            s.stickY = -dy;
            s.joyCX = joyCX; s.joyCY = joyCY;
            s.knobX = joyCX + dx * r; s.knobY = joyCY + dy * r;
        } else {
            s.stickX = s.stickY = 0f;
            s.joyCX = s.knobX = joyHomeX();
            s.joyCY = s.knobY = joyHomeY();
        }
        s.camDX = camDX; s.camDY = camDY;
        camDX = camDY = 0f;
        s.jump = jumpQueued;
        jumpQueued = false;
        s.jumpHeld = jumpId >= 0;
    }
}
