package com.harlequinxd.drivehard.core.road;

/**
 * Road categories. Lane widths and cross-sections in metres, speeds in m/s. Traffic keeps left.
 * Priority decides who gives way at unsignalled junctions (higher goes first).
 */
public enum RoadClass {
    //        lanes laneW median shoulder speed prio city  smooth step grade
    STREET(   1, 3.5f, 0.25f, 2.75f, 12f, 1, true,   0f, 0f, 1f),
    AVENUE(   2, 3.5f, 1.3f,  2.7f, 14f, 2, true,   0f, 0f, 1f),
    /** Narrow farm access lane (two-way, unmarked). */
    LANE(     1, 1.8f, 0f,    0.4f,  8f, 0, false,  30f, 5f, 0.14f),
    COUNTRY(  1, 3.0f, 0.1f,  0.8f, 16f, 2, false, 90f, 10f, 0.09f),
    HIGHWAY(  2, 3.25f, 0.3f, 1.5f, 21f, 3, false, 170f, 10f, 0.07f),
    FREEWAY(  2, 3.5f, 1.4f,  2.5f, 27f, 5, false, 340f, 10f, 0.05f),
    RAMP(     1, 3.75f, 0f,   1.5f, 15f, 4, false, 70f, 5f, 0.06f);

    public final int lanes, priority;
    public final float laneW, median, shoulder, speed, smoothWindow, step, grade;
    public final boolean city;

    RoadClass(int lanes, float laneW, float median, float shoulder, float speed, int priority, boolean city,
              float smoothWindow, float step, float grade) {
        this.lanes = lanes; this.laneW = laneW; this.median = median; this.shoulder = shoulder;
        this.speed = speed; this.priority = priority; this.city = city;
        this.smoothWindow = smoothWindow; this.step = step; this.grade = grade;
    }

    public boolean oneWay() { return this == RAMP; }

    /** Half the paved width, centreline to outer edge. */
    public float halfWidth() {
        return oneWay() ? laneW * lanes * 0.5f + shoulder : median + lanes * laneW + shoulder;
    }

    /** Freeway system (grade separated): other roads bridge over it. */
    public boolean motorway() { return this == FREEWAY || this == RAMP; }
}
