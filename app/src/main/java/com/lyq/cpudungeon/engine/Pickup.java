package com.lyq.cpudungeon.engine;

public final class Pickup {
    public final float x;
    public final float y;
    public float phase;
    public float spin;
    public boolean taken;

    public Pickup(float x, float y, float phase) {
        this.x = x;
        this.y = y;
        this.phase = phase;
    }
}
