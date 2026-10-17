package com.lyq.cpudungeon.engine3d;

/** Pinhole camera with yaw/pitch. Basis is rebuilt once per frame. */
public final class Camera3D {
    public static final float HORIZONTAL_TAN = 0.82f;

    public float x;
    public float y;
    public float z;
    public float yaw;
    public float pitch;
    public float focal;
    public float fovTan = HORIZONTAL_TAN;

    public float fx;
    public float fy;
    public float fz;
    public float rx;
    public float ry;
    public float rz;
    public float ux;
    public float uy;
    public float uz;

    public void setFovDegrees(float degrees) {
        fovTan = (float) Math.tan(Math.toRadians(degrees) * 0.5);
    }

    public void update(int viewWidth) {
        float cp = (float) Math.cos(pitch);
        float sp = (float) Math.sin(pitch);
        float cy = (float) Math.cos(yaw);
        float sy = (float) Math.sin(yaw);
        fx = cy * cp;
        fy = sy * cp;
        fz = sp;

        float horizontal = (float) Math.sqrt(fx * fx + fy * fy);
        if (horizontal < 1e-5f) {
            horizontal = 1e-5f;
        }
        rx = -fy / horizontal;
        ry = fx / horizontal;
        rz = 0f;

        ux = fy * rz - fz * ry;
        uy = fz * rx - fx * rz;
        uz = fx * ry - fy * rx;

        focal = (viewWidth * 0.5f) / Math.max(0.2f, fovTan);
    }
}
