package com.harlequinxd.drivehard.input;

import android.view.MotionEvent;

/**
 * Multi-touch on-screen controls, written by the UI thread and read by the game thread.
 *
 * On foot:
 *  - Left side: floating virtual joystick (appears where the thumb lands). Push past ~70% to run.
 *  - Right side: drag to orbit the camera. Jump button bottom-right.
 * Driving:
 *  - Left side: D-pad (left/right steer; up = gas, down = brake/reverse). The thumb can slide
 *    between directions, diagonals press two at once.
 *  - Right side: GAS (big) and REV buttons; drag elsewhere to look around.
 * Both: the car button (get in / get out) above the jump/gas button. On foot it only responds
 * while it is shown, i.e. next to a car.
 */
public final class TouchControls {
    public final float density;
    private int width = 1, height = 1;

    private boolean driving;
    private boolean carButtonEnabled;

    // joystick / d-pad (same left-thumb pointer)
    private int joyId = -1;
    private float joyCX, joyCY, joyX, joyY;
    // camera drag
    private int camId = -1;
    private float camLastX, camLastY, camDX, camDY;
    // right-hand buttons
    private int jumpId = -1, revId = -1, carId = -1;
    private boolean jumpQueued, carQueued;

    /** Snapshot read by the game each frame. */
    public static final class State {
        public float stickX, stickY;      // -1..1, y up
        public float camDX, camDY;        // pixels since last frame
        public boolean jump, jumpHeld;
        public boolean joyActive;
        public float joyCX, joyCY, knobX, knobY;
        // driving
        public boolean driving;
        public boolean padLeft, padRight, padUp, padDown;
        public boolean gasHeld, revHeld;
        public float steer, gas, reverse;
        // car button
        public boolean car, carHeld;
    }

    public TouchControls(float density) { this.density = density; }

    public float joyRadius() { return 62f * density; }
    public float jumpRadius() { return 50f * density; }
    public float jumpX() { return width - 92f * density; }
    public float jumpY() { return height - 92f * density; }
    public float joyHomeX() { return 120f * density; }
    public float joyHomeY() { return height - 120f * density; }

    // driving layout
    public float padRadius() { return 74f * density; }
    public float padX() { return 124f * density; }
    public float padY() { return height - 124f * density; }
    public float gasX() { return jumpX(); }
    public float gasY() { return jumpY(); }
    public float gasRadius() { return 54f * density; }
    public float revX() { return width - 206f * density; }
    public float revY() { return height - 66f * density; }
    public float revRadius() { return 40f * density; }
    public float carX() { return width - 92f * density; }
    public float carY() { return height - 214f * density; }
    public float carRadius() { return 38f * density; }

    public synchronized void setSize(int w, int h) { width = w; height = h; }

    /** Switches the layout; touches in progress are released so nothing sticks. */
    public synchronized void setDriving(boolean d) {
        if (d == driving) return;
        driving = d;
        joyId = camId = jumpId = revId = -1;
        jumpQueued = false;
    }

    /** Whether the car button is showing (on foot it only appears next to a car). */
    public synchronized void setCarButtonEnabled(boolean e) { carButtonEnabled = e; }

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
                joyId = camId = jumpId = revId = carId = -1;
                break;
            default:
                break;
        }
    }

    private static boolean inCircle(float x, float y, float cx, float cy, float r) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy < r * r;
    }

    private void down(int id, float x, float y) {
        if (carId < 0 && (driving || carButtonEnabled) && inCircle(x, y, carX(), carY(), carRadius() * 1.25f)) {
            carId = id;
            carQueued = true;
            return;
        }
        if (driving) {
            if (jumpId < 0 && inCircle(x, y, gasX(), gasY(), gasRadius() * 1.3f)) { jumpId = id; return; }
            if (revId < 0 && inCircle(x, y, revX(), revY(), revRadius() * 1.3f)) { revId = id; return; }
            if (joyId < 0 && x < width * 0.45f) { joyId = id; joyX = x; joyY = y; return; }
        } else {
            if (jumpId < 0 && inCircle(x, y, jumpX(), jumpY(), jumpRadius() * 1.25f)) {
                jumpId = id;
                jumpQueued = true;
                return;
            }
            if (joyId < 0 && x < width * 0.45f) {
                joyId = id;
                joyCX = x; joyCY = y; joyX = x; joyY = y;
                return;
            }
        }
        if (camId < 0) {
            camId = id;
            camLastX = x; camLastY = y;
        }
    }

    private void move(int id, float x, float y) {
        if (id == joyId) {
            joyX = x; joyY = y;
            if (!driving) {
                // drag the floating centre along if the thumb goes far past the rim
                float dx = x - joyCX, dy = y - joyCY, r = joyRadius();
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > r * 1.4f) {
                    float k = (d - r * 1.4f) / d;
                    joyCX += dx * k; joyCY += dy * k;
                }
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
        if (id == revId) revId = -1;
        if (id == carId) carId = -1;
    }

    public synchronized void read(State s) {
        s.driving = driving;
        s.joyActive = joyId >= 0;
        s.padLeft = s.padRight = s.padUp = s.padDown = false;
        s.steer = s.gas = s.reverse = 0f;
        s.stickX = s.stickY = 0f;
        if (driving) {
            if (joyId >= 0) {
                // 8-way D-pad around a fixed centre, with a small dead zone
                float dx = joyX - padX(), dy = joyY - padY(), dead = padRadius() * 0.22f;
                if (dx * dx + dy * dy > dead * dead) {
                    double a = Math.atan2(-dy, dx);           // 0 = right, +90 = up
                    int sector = (int) Math.floor((a + Math.PI / 8) / (Math.PI / 4)) & 7;
                    s.padRight = sector == 0 || sector == 1 || sector == 7;
                    s.padUp = sector == 1 || sector == 2 || sector == 3;
                    s.padLeft = sector == 3 || sector == 4 || sector == 5;
                    s.padDown = sector == 5 || sector == 6 || sector == 7;
                }
            }
            s.gasHeld = jumpId >= 0;
            s.revHeld = revId >= 0;
            s.steer = (s.padRight ? 1f : 0f) - (s.padLeft ? 1f : 0f);
            s.gas = s.gasHeld || s.padUp ? 1f : 0f;
            s.reverse = s.revHeld || s.padDown ? 1f : 0f;
            s.jump = false;
            s.jumpHeld = false;
            s.joyCX = s.knobX = padX();
            s.joyCY = s.knobY = padY();
        } else {
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
                s.joyCX = s.knobX = joyHomeX();
                s.joyCY = s.knobY = joyHomeY();
            }
            s.jump = jumpQueued;
            s.jumpHeld = jumpId >= 0;
            s.gasHeld = s.revHeld = false;
        }
        jumpQueued = false;
        s.camDX = camDX; s.camDY = camDY;
        camDX = camDY = 0f;
        s.car = carQueued;
        carQueued = false;
        s.carHeld = carId >= 0;
    }
}
