package com.lyq.cpudungeon.engine3d;

import java.util.Arrays;

/**
 * Maze generation for the 3D renderer: same carving rules as v1, plus a
 * quantized height field. Heights are assigned along the BFS spanning tree in
 * 0.25 steps and then smoothed so every walkable edge is a real stair step.
 */
public final class MapGen3 {

    private MapGen3() {
    }

    public static Level3D generate(int level, long seed) {
        long state = seed != 0 ? seed : 0x9E3779B97F4A7C15L;
        int size = 17 + Math.min(14, (level - 1) * 2);
        if ((size & 1) == 0) {
            size++;
        }
        int w = size;
        int h = size;
        byte[] solid = new byte[w * h];
        byte[] wallTex = new byte[w * h];
        Arrays.fill(solid, (byte) 1);
        Rng rng = new Rng(state);
        for (int i = 0; i < wallTex.length; i++) {
            wallTex[i] = (byte) rng.nextInt(4);
        }

        carveMaze(solid, w, h, rng);
        braid(solid, w, h, rng);
        widen(solid, w, h, rng);
        carveRooms(solid, w, h, rng, level);
        repairConnectivity(solid, w, h);

        int startX = 1;
        int startY = 1;
        solid[startY * w + startX] = 0;

        float[] floorZ = new float[w * h];
        int[] parent = new int[w * h];
        Arrays.fill(parent, -1);
        int[] queue = new int[w * h];
        int head = 0;
        int tail = 0;
        int startIdx = startY * w + startX;
        parent[startIdx] = startIdx;
        queue[tail++] = startIdx;
        while (head < tail) {
            int cell = queue[head++];
            int cx = cell % w;
            int cy = cell / w;
            for (int dir = 0; dir < 4; dir++) {
                int nx = cx + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                int ny = cy + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                int n = ny * w + nx;
                if (solid[n] != 0 || parent[n] >= 0) {
                    continue;
                }
                int roll = rng.nextInt(100);
                float step = roll < 14 ? -Level3D.STEP : (roll < 28 ? Level3D.STEP : 0f);
                floorZ[n] = clamp(Math.round((floorZ[cell] + step) * 4f) / 4f, 0f, 1f);
                parent[n] = cell;
                queue[tail++] = n;
            }
        }

        smoothHeights(solid, floorZ, w, h);

        int[] dist = new int[w * h];
        bfs(solid, w, h, startX, startY, dist);
        int exitIdx = startIdx;
        int best = -1;
        for (int i = 0; i < dist.length; i++) {
            if (dist[i] > best) {
                best = dist[i];
                exitIdx = i;
            }
        }
        int exitX = exitIdx % w;
        int exitY = exitIdx / w;
        return new Level3D(w, h, solid, floorZ, wallTex, startX, startY, exitX, exitY);
    }

    private static void smoothHeights(byte[] solid, float[] z, int w, int h) {
        for (int pass = 0; pass < 24; pass++) {
            boolean changed = false;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (solid[i] != 0) {
                        continue;
                    }
                    if (x + 1 < w && solid[i + 1] == 0 && Math.abs(z[i] - z[i + 1]) > Level3D.STEP + 0.01f) {
                        float mid = Math.round((z[i] + z[i + 1]) * 2f) / 4f;
                        z[i] = mid;
                        z[i + 1] = mid;
                        changed = true;
                    }
                    if (y + 1 < h && solid[i + w] == 0 && Math.abs(z[i] - z[i + w]) > Level3D.STEP + 0.01f) {
                        float mid = Math.round((z[i] + z[i + w]) * 2f) / 4f;
                        z[i] = mid;
                        z[i + w] = mid;
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
        for (int i = 0; i < z.length; i++) {
            z[i] = clamp(z[i], 0f, 1f);
        }
    }

    private static void carveMaze(byte[] solid, int w, int h, Rng rng) {
        boolean[] seen = new boolean[w * h];
        int[] stack = new int[w * h];
        int sp = 0;
        int start = w + 1;
        solid[start] = 0;
        seen[start] = true;
        stack[sp++] = start;
        int[] dirs = new int[4];
        while (sp > 0) {
            int cell = stack[sp - 1];
            int cx = cell % w;
            int cy = cell / w;
            int count = 0;
            if (cy > 1 && !seen[cell - 2 * w]) dirs[count++] = -2 * w;
            if (cy < h - 2 && !seen[cell + 2 * w]) dirs[count++] = 2 * w;
            if (cx > 1 && !seen[cell - 2]) dirs[count++] = -2;
            if (cx < w - 2 && !seen[cell + 2]) dirs[count++] = 2;
            if (count == 0) {
                sp--;
                continue;
            }
            int step = dirs[rng.nextInt(count)];
            int next = cell + step;
            solid[cell + step / 2] = 0;
            solid[next] = 0;
            seen[next] = true;
            stack[sp++] = next;
        }
    }

    private static void braid(byte[] solid, int w, int h, Rng rng) {
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                if (solid[idx] != 0) {
                    continue;
                }
                int open = 0;
                if (solid[idx - 1] == 0) open++;
                if (solid[idx + 1] == 0) open++;
                if (solid[idx - w] == 0) open++;
                if (solid[idx + w] == 0) open++;
                if (open != 1 || rng.nextFloat() > 0.55f) {
                    continue;
                }
                int[] walls = new int[4];
                int n = 0;
                if (x > 2 && solid[idx - 1] != 0) walls[n++] = idx - 1;
                if (x < w - 3 && solid[idx + 1] != 0) walls[n++] = idx + 1;
                if (y > 2 && solid[idx - w] != 0) walls[n++] = idx - w;
                if (y < h - 3 && solid[idx + w] != 0) walls[n++] = idx + w;
                if (n > 0) {
                    solid[walls[rng.nextInt(n)]] = 0;
                }
            }
        }
    }

    /** Opens ~20% of side walls so 1-cell corridors are not claustrophobic in 3D. */
    private static void widen(byte[] solid, int w, int h, Rng rng) {
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                if (solid[idx] != 0 || rng.nextFloat() > 0.20f) {
                    continue;
                }
                int dir = rng.nextInt(4);
                int nx = x + (dir == 0 ? -1 : dir == 1 ? 1 : 0);
                int ny = y + (dir == 2 ? -1 : dir == 3 ? 1 : 0);
                if (nx > 0 && ny > 0 && nx < w - 1 && ny < h - 1) {
                    solid[ny * w + nx] = 0;
                }
            }
        }
    }

    private static void carveRooms(byte[] solid, int w, int h, Rng rng, int level) {
        int rooms = Math.min(5, 2 + level / 2);
        for (int i = 0; i < rooms; i++) {
            int rw = 3 + 2 * rng.nextInt(2);
            int rh = 3 + 2 * rng.nextInt(2);
            int x = 1 + rng.nextInt(Math.max(1, w - rw - 2));
            int y = 1 + rng.nextInt(Math.max(1, h - rh - 2));
            for (int yy = y; yy < y + rh && yy < h - 1; yy++) {
                for (int xx = x; xx < x + rw && xx < w - 1; xx++) {
                    solid[yy * w + xx] = 0;
                }
            }
            int doorX = Math.min(w - 2, Math.max(1, x + rw / 2));
            int doorY = Math.min(h - 2, Math.max(1, y + rh));
            solid[doorY * w + doorX] = 0;
        }
    }

    private static void repairConnectivity(byte[] solid, int w, int h) {
        int[] dist = new int[w * h];
        bfs(solid, w, h, 1, 1, dist);
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                if (solid[idx] != 0 || dist[idx] >= 0) {
                    continue;
                }
                int cx = x;
                int cy = y;
                int guard = 0;
                while (cx != 1 && guard++ < w) {
                    cx--;
                    solid[cy * w + cx] = 0;
                }
                guard = 0;
                while (cy != 1 && guard++ < h) {
                    cy--;
                    solid[cy * w + cx] = 0;
                }
                bfs(solid, w, h, 1, 1, dist);
            }
        }
    }

    private static void bfs(byte[] solid, int w, int h, int sx, int sy, int[] dist) {
        Arrays.fill(dist, -1);
        int[] queue = new int[w * h];
        int head = 0;
        int tail = 0;
        int start = sy * w + sx;
        if (solid[start] != 0) {
            return;
        }
        dist[start] = 0;
        queue[tail++] = start;
        while (head < tail) {
            int cell = queue[head++];
            int cx = cell % w;
            int cy = cell / w;
            int d = dist[cell] + 1;
            if (cx > 0 && solid[cell - 1] == 0 && dist[cell - 1] < 0) {
                dist[cell - 1] = d;
                queue[tail++] = cell - 1;
            }
            if (cx < w - 1 && solid[cell + 1] == 0 && dist[cell + 1] < 0) {
                dist[cell + 1] = d;
                queue[tail++] = cell + 1;
            }
            if (cy > 0 && solid[cell - w] == 0 && dist[cell - w] < 0) {
                dist[cell - w] = d;
                queue[tail++] = cell - w;
            }
            if (cy < h - 1 && solid[cell + w] == 0 && dist[cell + w] < 0) {
                dist[cell + w] = d;
                queue[tail++] = cell + w;
            }
        }
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static final class Rng {
        private long state;

        Rng(long seed) {
            state = seed != 0 ? seed : 0x2545F4914F6CDD1DL;
        }

        int nextInt(int bound) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            int v = (int) (state & 0x7FFFFFFFL);
            return bound <= 0 ? 0 : v % bound;
        }

        float nextFloat() {
            return nextInt(1 << 24) / (float) (1 << 24);
        }
    }
}
