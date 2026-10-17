package com.lyq.cpudungeon.engine3d;

/** Grid level with per-cell floor height (0.25 steps) and wall texture ids. */
public final class Level3D {
    public static final float CEIL_Z = 3.2f;
    public static final float STEP = 0.25f;

    public final int w;
    public final int h;
    public final byte[] solid;
    public final float[] floorZ;
    public final byte[] wallTex;
    public final int startX;
    public final int startY;
    public final int exitX;
    public final int exitY;

    public Level3D(int w, int h, byte[] solid, float[] floorZ, byte[] wallTex,
                   int startX, int startY, int exitX, int exitY) {
        this.w = w;
        this.h = h;
        this.solid = solid;
        this.floorZ = floorZ;
        this.wallTex = wallTex;
        this.startX = startX;
        this.startY = startY;
        this.exitX = exitX;
        this.exitY = exitY;
    }

    public boolean solid(int cx, int cy) {
        if (cx < 0 || cy < 0 || cx >= w || cy >= h) {
            return true;
        }
        return solid[cy * w + cx] != 0;
    }

    public boolean solidAt(float x, float y) {
        return solid((int) Math.floor(x), (int) Math.floor(y));
    }

    public float floorAt(float x, float y) {
        int cx = (int) Math.floor(x);
        int cy = (int) Math.floor(y);
        if (cx < 0 || cy < 0 || cx >= w || cy >= h) {
            return 0f;
        }
        return solid[cy * w + cx] != 0 ? 0f : floorZ[cy * w + cx];
    }

    public int wallTexAt(int cx, int cy) {
        if (cx < 0 || cy < 0 || cx >= w || cy >= h) {
            return 0;
        }
        return wallTex[cy * w + cx] & 3;
    }

    /** BFS over walkable cells; -1 marks unreachable or solid. */
    public void bfs(int fromX, int fromY, int[] out, int[] queue) {
        java.util.Arrays.fill(out, -1);
        if (solid(fromX, fromY)) {
            return;
        }
        int head = 0;
        int tail = 0;
        int start = fromY * w + fromX;
        out[start] = 0;
        queue[tail++] = start;
        while (head < tail) {
            int cell = queue[head++];
            int cx = cell % w;
            int cy = cell / w;
            int d = out[cell] + 1;
            if (cx > 0 && solid[cell - 1] == 0 && out[cell - 1] < 0) {
                out[cell - 1] = d;
                queue[tail++] = cell - 1;
            }
            if (cx < w - 1 && solid[cell + 1] == 0 && out[cell + 1] < 0) {
                out[cell + 1] = d;
                queue[tail++] = cell + 1;
            }
            if (cy > 0 && solid[cell - w] == 0 && out[cell - w] < 0) {
                out[cell - w] = d;
                queue[tail++] = cell - w;
            }
            if (cy < h - 1 && solid[cell + w] == 0 && out[cell + w] < 0) {
                out[cell + w] = d;
                queue[tail++] = cell + w;
            }
        }
    }
}
