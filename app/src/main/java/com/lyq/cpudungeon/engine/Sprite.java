package com.lyq.cpudungeon.engine;

/** Immutable ARGB bitmap used by the software sprite rasterizer. */
public final class Sprite {
    public final int w;
    public final int h;
    public final int[] px;

    public Sprite(int w, int h, int[] px) {
        this.w = w;
        this.h = h;
        this.px = px;
    }

    /** Nearest-neighbour hard upscale, keeps pixel art crisp. */
    public Sprite scale(int factor) {
        int nw = w * factor;
        int nh = h * factor;
        int[] out = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            int sy = y / factor;
            int src = sy * w;
            int dst = y * nw;
            for (int x = 0; x < nw; x++) {
                out[dst + x] = px[src + x / factor];
            }
        }
        return new Sprite(nw, nh, out);
    }
}
