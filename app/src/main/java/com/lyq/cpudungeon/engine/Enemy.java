package com.lyq.cpudungeon.engine;

public final class Enemy {
    public float x;
    public float y;
    /** Vertical centre of the model (drones hover). */
    public float z;
    /** Facing angle, smoothed toward the player. */
    public float yaw;
    public int hp = 3;
    public float phase;
    public float hitFlash;
    public float alert;
    public float attackCooldown;
    public float targetX;
    public float targetY;
    public float wanderTimer;
    public float spawnX;
    public float spawnY;
    public boolean dead;

    public Enemy(float x, float y, float phase) {
        this.x = x;
        this.y = y;
        this.spawnX = x;
        this.spawnY = y;
        this.phase = phase;
    }
}
