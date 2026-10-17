package com.lyq.cpudungeon.engine3d;

/** A real travelling projectile (no hitscan). */
public final class Bullet3D {
    public float x;
    public float y;
    public float z;
    public float px;
    public float py;
    public float pz;
    public float vx;
    public float vy;
    public float vz;
    public float life = 2f;
    public boolean alive = true;
}
