package com.lyq.cpudungeon.engine3d;

import java.util.Arrays;

/**
 * Edge-directed anti-alias pass (FXAA-lite). Luma is precomputed once, then
 * only high-contrast pixels are blended with their 4-neighbour average.
 */
public final class PostFx {
    private int[] temp = new int[0];
    private short[] luma = new short[0];

    public void antiAlias(int[] fb, int w, int h) {
        int pixels = w * h;
        if (temp.length < pixels) {
            temp = new int[pixels];
        }
        if (luma.length < pixels) {
            luma = new short[pixels];
        }
        System.arraycopy(fb, 0, temp, 0, pixels);

        final short[] lum = luma;
        for (int i = 0; i < pixels; i++) {
            int c = temp[i];
            lum[i] = (short) ((((c >> 16) & 255) * 77 + ((c >> 8) & 255) * 151 + (c & 255) * 28) >> 8);
        }

        final int threshold = 22;
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int i = row + x;
                int c0 = lum[i];
                int l1 = lum[i - 1];
                int l2 = lum[i + 1];
                int l3 = lum[i - w];
                int l4 = lum[i + w];
                int max = c0;
                if (l1 > max) max = l1;
                if (l2 > max) max = l2;
                if (l3 > max) max = l3;
                if (l4 > max) max = l4;
                int min = c0;
                if (l1 < min) min = l1;
                if (l2 < min) min = l2;
                if (l3 < min) min = l3;
                if (l4 < min) min = l4;
                if (max - min < threshold) {
                    continue;
                }
                int a = temp[i - 1];
                int b = temp[i + 1];
                int d = temp[i - w];
                int e = temp[i + w];
                int p1 = ((a & 0xFEFEFE) + (b & 0xFEFEFE)) >> 1;
                int p2 = ((d & 0xFEFEFE) + (e & 0xFEFEFE)) >> 1;
                int avg = ((p1 & 0xFEFEFE) + (p2 & 0xFEFEFE)) >> 1;
                int c = temp[i];
                // stronger edge blend so the effect survives the final upscale
                int r = (((c >> 16) & 255) * 2 + ((avg >> 16) & 255) * 3) / 5;
                int g = (((c >> 8) & 255) * 2 + ((avg >> 8) & 255) * 3) / 5;
                int bl = ((c & 255) * 2 + (avg & 255) * 3) / 5;
                fb[i] = 0xFF000000 | (r << 16) | (g << 8) | bl;
            }
        }
    }
}
