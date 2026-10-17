package com.lyq.cpudungeon.engine3d;

/** First-person body with gravity, jumping, crouching and a free-look head. */
public final class Player3D {
    public static final float RADIUS = 0.24f;
    public static final float STAND_EYE = 1.62f;
    public static final float CROUCH_EYE = 1.02f;
    public static final float STAND_HEIGHT = 1.72f;
    public static final float CROUCH_HEIGHT = 1.16f;
    public static final float STEP_UP = 0.30f;
    public static final float GRAVITY = 18f;
    public static final float JUMP_SPEED = 5.6f;

    public float x;
    public float y;
    public float z;
    public float vz;
    public float yaw;
    public float pitch;
    public float health = 100f;
    public float maxHealth = 100f;
    public boolean grounded;
    public boolean crouch;
    public float eyeHeight = STAND_EYE;
    public float bob;
    public float moveSpeed;
    public float fireCooldown;
    public float recoil;
    public float muzzle;
    public float damageFlash;
    public float landDip;

    public void reset(float x, float y, float z, float yaw) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.vz = 0f;
        this.yaw = yaw;
        this.pitch = 0f;
        health = maxHealth;
        grounded = true;
        crouch = false;
        eyeHeight = STAND_EYE;
        bob = 0f;
        moveSpeed = 0f;
        fireCooldown = 0f;
        recoil = 0f;
        muzzle = 0f;
        damageFlash = 0f;
        landDip = 0f;
    }

    public float height() {
        return crouch ? CROUCH_HEIGHT : STAND_HEIGHT;
    }
}
