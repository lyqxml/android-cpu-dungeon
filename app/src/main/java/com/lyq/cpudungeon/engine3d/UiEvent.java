package com.lyq.cpudungeon.engine3d;

/** Lightweight event consumed by the HUD (floating damage / score text). */
public final class UiEvent {
    public final String text;
    public final int color;

    public UiEvent(String text, int color) {
        this.text = text;
        this.color = color;
    }
}
