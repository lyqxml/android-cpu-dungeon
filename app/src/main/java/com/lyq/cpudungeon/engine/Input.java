package com.lyq.cpudungeon.engine;

/** Per-frame input intent, written by the Android layer and consumed by Game. */
public final class Input {
    public float moveX;
    public float moveY;
    public float look;
    public boolean fireHeld;
    public boolean fireTap;
    public boolean restartTap;

    public void clearTransient() {
        look = 0f;
        fireTap = false;
        restartTap = false;
    }
}
