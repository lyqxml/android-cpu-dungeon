package com.lyq.cpudungeon.engine3d;

import com.lyq.cpudungeon.engine.Textures;

/** Procedural textures plus a 4-level box-filtered mip chain. */
public final class MipTextures {
    public static final int MIP_LEVELS = 4;
    /** 0..3 walls, 4 floor, 5 ceiling. */
    public static final int[][][] TEXTURES = new int[6][][];

    static {
        for (int i = 0; i < 4; i++) {
            TEXTURES[i] = mips(Textures.WALLS[i]);
        }
        TEXTURES[4] = mips(Textures.FLOOR);
        TEXTURES[5] = mips(Textures.CEIL);
    }

    private static int[][] mips(int[] base) {
        int[][] levels = new int[MIP_LEVELS][];
        levels[0] = base;
        int size = 64;
        int[] src = base;
        for (int level = 1; level < MIP_LEVELS; level++) {
            size >>= 1;
            int[] dst = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int r = 0;
                    int g = 0;
                    int b = 0;
                    int sx = x * 2;
                    int sy = y * 2;
                    for (int dy = 0; dy < 2; dy++) {
                        for (int dx = 0; dx < 2; dx++) {
                            int c = src[(sy + dy) * (size * 2) + sx + dx];
                            r += (c >> 16) & 255;
                            g += (c >> 8) & 255;
                            b += c & 255;
                        }
                    }
                    dst[y * size + x] = 0xFF000000 | ((r >> 2) << 16) | ((g >> 2) << 8) | (b >> 2);
                }
            }
            levels[level] = dst;
            src = dst;
        }
        return levels;
    }

    private MipTextures() {
    }
}
