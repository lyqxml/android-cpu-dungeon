package com.lyq.cpudungeon.engine3d;

import com.lyq.cpudungeon.engine.Sprite;

/** Camera-facing quad used for drones, cores, the portal and particles. */
public final class Billboard3D {
    public float x;
    public float y;
    public float z;
    public float height;
    public Sprite sprite;
    public int tint = 0xFFFFFFFF;
    public float brightness = 1f;
    public boolean glow;
    /** When true the sprite ignores the depth buffer (OVERCLOCK x-ray). */
    public boolean xray;
    public float d2;

    public void set(Sprite sprite, float x, float y, float z, float height,
                    int tint, float brightness, boolean glow) {
        this.sprite = sprite;
        this.x = x;
        this.y = y;
        this.z = z;
        this.height = height;
        this.tint = tint;
        this.brightness = brightness;
        this.glow = glow;
        this.xray = false;
    }
}
