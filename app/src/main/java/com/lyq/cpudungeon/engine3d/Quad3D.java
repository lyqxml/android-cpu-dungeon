package com.lyq.cpudungeon.engine3d;

/** World-space quad. Either textured, or flat-shaded solid (enemy models). */
public final class Quad3D {
    public final float[] px = new float[4];
    public final float[] py = new float[4];
    public final float[] pz = new float[4];
    public final float[] u = new float[4];
    public final float[] v = new float[4];
    public int tex;
    public float shade;
    public boolean solid;
    public int color;
    /** Cheat x-ray: tested against a separate depth buffer so walls do not hide it. */
    public boolean xray;
    public float nx;
    public float ny;
    public float nz;

    public void set(float x0, float y0, float z0, float u0, float v0,
                    float x1, float y1, float z1, float u1, float v1,
                    float x2, float y2, float z2, float u2, float v2,
                    float x3, float y3, float z3, float u3, float v3,
                    int tex, float shade) {
        px[0] = x0; py[0] = y0; pz[0] = z0; u[0] = u0; v[0] = v0;
        px[1] = x1; py[1] = y1; pz[1] = z1; u[1] = u1; v[1] = v1;
        px[2] = x2; py[2] = y2; pz[2] = z2; u[2] = u2; v[2] = v2;
        px[3] = x3; py[3] = y3; pz[3] = z3; u[3] = u3; v[3] = v3;
        this.tex = tex;
        this.shade = shade;
        this.solid = false;
        this.color = 0;
        this.xray = false;
        this.nx = 0f;
        this.ny = 0f;
        this.nz = 0f;
    }

    public void setNormal(float nx, float ny, float nz) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
    }

    public void setSolid(float x0, float y0, float z0,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float x3, float y3, float z3,
                         int color, float shade) {
        px[0] = x0; py[0] = y0; pz[0] = z0;
        px[1] = x1; py[1] = y1; pz[1] = z1;
        px[2] = x2; py[2] = y2; pz[2] = z2;
        px[3] = x3; py[3] = y3; pz[3] = z3;
        u[0] = 0f; u[1] = 0f; u[2] = 0f; u[3] = 0f;
        v[0] = 0f; v[1] = 0f; v[2] = 0f; v[3] = 0f;
        this.tex = -1;
        this.shade = shade;
        this.solid = true;
        this.color = color;
        this.xray = false;
    }
}
