package com.harlequinxd.drivehard.core.model;

/** Per-bone Euler rotations (applied Y * X * Z) plus a root offset. */
public final class Pose {
    public final float[] rx = new float[CharacterModel.BONES];
    public final float[] ry = new float[CharacterModel.BONES];
    public final float[] rz = new float[CharacterModel.BONES];
    public float rootX, rootY, rootZ;
    /** If true the baker shifts the body so the lowest foot touches y = 0. */
    public boolean grounded = true;
}
