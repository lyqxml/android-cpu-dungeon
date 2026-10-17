package com.lyq.cpudungeon.engine;

public final class Player {
    public float x;
    public float y;
    public float angle;
    public float health = 100f;
    public float maxHealth = 100f;
    public float bob;
    public float fireCooldown;
    public float recoil;
    public float damageFlash;
    public float muzzle;
    public float moveSpeed;

    public void reset(float x, float y, float angle) {
        this.x = x;
        this.y = y;
        this.angle = angle;
        health = maxHealth;
        bob = 0f;
        fireCooldown = 0f;
        recoil = 0f;
        damageFlash = 0f;
        muzzle = 0f;
    }
}
