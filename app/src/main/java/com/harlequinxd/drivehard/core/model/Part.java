package com.harlequinxd.drivehard.core.model;

/**
 * A rigid piece of mesh attached to one skeleton bone. Vertices are stored in bone space.
 * Each triangle carries its own flat base colour; normals are smooth per vertex.
 */
public final class Part {
    public final int bone;
    public final float[] pos;    // xyz per vertex
    public final float[] nrm;    // xyz per vertex
    public final int[] tris;     // 3 indices per triangle
    public final int[] colors;   // 0xRRGGBB per triangle
    public final float shine;    // 0..1 specular amount
    /** True for feet/shoes: used to ground grounded poses. */
    public final boolean isFoot;

    public Part(int bone, float[] pos, float[] nrm, int[] tris, int[] colors, float shine, boolean isFoot) {
        this.bone = bone;
        this.pos = pos;
        this.nrm = nrm;
        this.tris = tris;
        this.colors = colors;
        this.shine = shine;
        this.isFoot = isFoot;
    }

    public int vertexCount() { return pos.length / 3; }
    public int triangleCount() { return tris.length / 3; }
}
