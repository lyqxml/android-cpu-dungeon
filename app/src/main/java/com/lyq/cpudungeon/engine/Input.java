package com.lyq.cpudungeon.engine;

/** Per-frame input intent, written by the Android layer and consumed by Game. */
public final class Input {
    public float moveX;
    public float moveY;
    public float look;
    public float pitch;
    public boolean fireHeld;
    public boolean fireTap;
    public boolean jumpTap;
    public boolean crouch;
    public boolean restartTap;
    public boolean cheatTap;

    public void clearTransient() {
        look = 0f;
        pitch = 0f;
        fireTap = false;
        jumpTap = false;
        restartTap = false;
    }
}
