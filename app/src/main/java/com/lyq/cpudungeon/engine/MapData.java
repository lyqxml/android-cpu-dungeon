package com.lyq.cpudungeon.engine;

import java.util.Arrays;

/** Static level geometry: a tile grid plus spawn/exit cells. */
public final class MapData {
    public final int w;
    public final int h;
    public final byte[] tiles;
    public final int startX;
    public final int startY;
    public final int exitX;
    public final int exitY;

    public MapData(int w, int h, byte[] tiles, int startX, int startY, int exitX, int exitY) {
        this.w = w;
        this.h = h;
        this.tiles = tiles;
        this.startX = startX;
        this.startY = startY;
        this.exitX = exitX;
        this.exitY = exitY;
    }

    public boolean solid(int cx, int cy) {
        if (cx < 0 || cy < 0 || cx >= w || cy >= h) {
            return true;
        }
        return tiles[cy * w + cx] != 0;
    }

    public boolean solidAt(float x, float y) {
        return solid((int) Math.floor(x), (int) Math.floor(y));
    }

    /** Circle vs. grid collision, tested on the four AABB corners. */
    public boolean hitsCircle(float x, float y, float r) {
        return solidAt(x - r, y - r) || solidAt(x + r, y - r)
                || solidAt(x - r, y + r) || solidAt(x + r, y + r);
    }

    /** BFS distance field (in cells) from a cell; -1 marks unreachable/wall. */
    public void bfs(int fromX, int fromY, int[] out, int[] queue) {
        Arrays.fill(out, -1);
        int head = 0;
        int tail = 0;
        int start = fromY * w + fromX;
        if (solid(fromX, fromY)) {
            return;
        }
        out[start] = 0;
        queue[tail++] = start;
        while (head < tail) {
            int cell = queue[head++];
            int cx = cell % w;
            int cy = cell / w;
            int d = out[cell] + 1;
            if (cx > 0) {
                int n = cell - 1;
                if (out[n] < 0 && tiles[n] == 0) { out[n] = d; queue[tail++] = n; }
            }
            if (cx < w - 1) {
                int n = cell + 1;
                if (out[n] < 0 && tiles[n] == 0) { out[n] = d; queue[tail++] = n; }
            }
            if (cy > 0) {
                int n = cell - w;
                if (out[n] < 0 && tiles[n] == 0) { out[n] = d; queue[tail++] = n; }
            }
            if (cy < h - 1) {
                int n = cell + w;
                if (out[n] < 0 && tiles[n] == 0) { out[n] = d; queue[tail++] = n; }
            }
        }
    }
}
