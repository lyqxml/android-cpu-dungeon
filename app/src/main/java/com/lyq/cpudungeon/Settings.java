package com.lyq.cpudungeon;

import android.content.Context;
import android.content.SharedPreferences;

/** User preferences: look sensitivity and draggable touch-button offsets (dp). */
public final class Settings {
    public static final float DEFAULT_SENSITIVITY = 1.0f;
    public static final float MIN_SENSITIVITY = 0.4f;
    public static final float MAX_SENSITIVITY = 2.8f;

    public static final float DEFAULT_FIRE_RIGHT = 80f;
    public static final float DEFAULT_FIRE_BOTTOM = 86f;
    public static final float DEFAULT_JUMP_RIGHT = 176f;
    public static final float DEFAULT_JUMP_BOTTOM = 78f;
    public static final float DEFAULT_CROUCH_RIGHT = 80f;
    public static final float DEFAULT_CROUCH_BOTTOM = 176f;

    private final SharedPreferences prefs;
    public float lookSensitivity = DEFAULT_SENSITIVITY;
    public float fireRight = DEFAULT_FIRE_RIGHT;
    public float fireBottom = DEFAULT_FIRE_BOTTOM;
    public float jumpRight = DEFAULT_JUMP_RIGHT;
    public float jumpBottom = DEFAULT_JUMP_BOTTOM;
    public float crouchRight = DEFAULT_CROUCH_RIGHT;
    public float crouchBottom = DEFAULT_CROUCH_BOTTOM;

    public Settings(Context context) {
        prefs = context.getSharedPreferences("cpu_dungeon", Context.MODE_PRIVATE);
        load();
    }

    public void load() {
        lookSensitivity = clamp(prefs.getFloat("sens", DEFAULT_SENSITIVITY),
                MIN_SENSITIVITY, MAX_SENSITIVITY);
        fireRight = prefs.getFloat("fire_right", DEFAULT_FIRE_RIGHT);
        fireBottom = prefs.getFloat("fire_bottom", DEFAULT_FIRE_BOTTOM);
        jumpRight = prefs.getFloat("jump_right", DEFAULT_JUMP_RIGHT);
        jumpBottom = prefs.getFloat("jump_bottom", DEFAULT_JUMP_BOTTOM);
        crouchRight = prefs.getFloat("crouch_right", DEFAULT_CROUCH_RIGHT);
        crouchBottom = prefs.getFloat("crouch_bottom", DEFAULT_CROUCH_BOTTOM);
    }

    public void save() {
        prefs.edit()
                .putFloat("sens", lookSensitivity)
                .putFloat("fire_right", fireRight)
                .putFloat("fire_bottom", fireBottom)
                .putFloat("jump_right", jumpRight)
                .putFloat("jump_bottom", jumpBottom)
                .putFloat("crouch_right", crouchRight)
                .putFloat("crouch_bottom", crouchBottom)
                .apply();
    }

    public void resetLayout() {
        fireRight = DEFAULT_FIRE_RIGHT;
        fireBottom = DEFAULT_FIRE_BOTTOM;
        jumpRight = DEFAULT_JUMP_RIGHT;
        jumpBottom = DEFAULT_JUMP_BOTTOM;
        crouchRight = DEFAULT_CROUCH_RIGHT;
        crouchBottom = DEFAULT_CROUCH_BOTTOM;
    }

    public void nudgeSensitivity(float delta) {
        lookSensitivity = clamp(lookSensitivity + delta, MIN_SENSITIVITY, MAX_SENSITIVITY);
        save();
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
