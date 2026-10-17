package com.lyq.cpudungeon.engine;

/**
 * Recursive-backtracker maze + braiding (removes dead ends) + a few carved
 * rooms. A repair pass guarantees every floor cell is reachable from spawn.
 */
public final class MapGen {

    private MapGen() {
    }

    public static MapData generate(int level, long seed) {
        Rng rng = new Rng(seed);
        int size = 17 + Math.min(14, (level - 1) * 2);
        if ((size & 1) == 0) {
            size++;
        }
        int w = size;
        int h = size;
        byte[] tiles = new byte[w * h];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = 1;
        }

        carveMaze(tiles, w, h, rng);
        braid(tiles, w, h, rng);
        carveRooms(tiles, w, h, rng, level);
        repairConnectivity(tiles, w, h);

        int startX = 1;
        int startY = 1;
        tiles[startY * w + startX] = 0;

        int[] dist = new int[w * h];
        int[] queue = new int[w * h];
        MapData probe = new MapData(w, h, tiles, startX, startY, 1, 1);
        probe.bfs(startX, startY, dist, queue);

        int exitIndex = startY * w + startX;
        int best = -1;
        for (int i = 0; i < dist.length; i++) {
            if (dist[i] > best) {
                best = dist[i];
                exitIndex = i;
            }
        }
        int exitX = exitIndex % w;
        int exitY = exitIndex / w;

        return new MapData(w, h, tiles, startX, startY, exitX, exitY);
    }

    private static void carveMaze(byte[] tiles, int w, int h, Rng rng) {
        boolean[] seen = new boolean[w * h];
        int[] stack = new int[w * h];
        int sp = 0;
        int start = w + 1;
        tiles[start] = 0;
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
            tiles[cell + step / 2] = 0;
            tiles[next] = 0;
            seen[next] = true;
            stack[sp++] = next;
        }
    }

    /** Remove most dead ends so the maze has loops and chases stay dynamic. */
    private static void braid(byte[] tiles, int w, int h, Rng rng) {
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                if (tiles[idx] != 0) {
                    continue;
                }
                int open = 0;
                if (tiles[idx - 1] == 0) open++;
                if (tiles[idx + 1] == 0) open++;
                if (tiles[idx - w] == 0) open++;
                if (tiles[idx + w] == 0) open++;
                if (open != 1 || rng.nextFloat() > 0.55f) {
                    continue;
                }
                int[] walls = new int[4];
                int n = 0;
                if (x > 2 && tiles[idx - 1] != 0) walls[n++] = idx - 1;
                if (x < w - 3 && tiles[idx + 1] != 0) walls[n++] = idx + 1;
                if (y > 2 && tiles[idx - w] != 0) walls[n++] = idx - w;
                if (y < h - 3 && tiles[idx + w] != 0) walls[n++] = idx + w;
                if (n > 0) {
                    tiles[walls[rng.nextInt(n)]] = 0;
                }
            }
        }
    }

    private static void carveRooms(byte[] tiles, int w, int h, Rng rng, int level) {
        int rooms = Math.min(5, 2 + level / 2);
        for (int i = 0; i < rooms; i++) {
            int rw = 3 + 2 * rng.nextInt(2);
            int rh = 3 + 2 * rng.nextInt(2);
            int x = 1 + rng.nextInt(Math.max(1, w - rw - 2));
            int y = 1 + rng.nextInt(Math.max(1, h - rh - 2));
            for (int yy = y; yy < y + rh && yy < h - 1; yy++) {
                for (int xx = x; xx < x + rw && xx < w - 1; xx++) {
                    tiles[yy * w + xx] = 0;
                }
            }
            // punch a doorway so rooms feel connected even before the repair pass
            int doorX = Math.min(w - 2, Math.max(1, x + rw / 2));
            int doorY = Math.min(h - 2, Math.max(1, y + rh));
            tiles[doorY * w + doorX] = 0;
        }
    }

    /** Carve an L-shaped corridor from every isolated pocket back to spawn. */
    private static void repairConnectivity(byte[] tiles, int w, int h) {
        int[] dist = new int[w * h];
        int[] queue = new int[w * h];
        MapData probe = new MapData(w, h, tiles, 1, 1, 1, 1);
        probe.bfs(1, 1, dist, queue);
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int idx = y * w + x;
                if (tiles[idx] != 0 || dist[idx] >= 0) {
                    continue;
                }
                int cx = x;
                int cy = y;
                int guard = 0;
                while (cx != 1 && guard++ < w) {
                    cx--;
                    tiles[cy * w + cx] = 0;
                }
                guard = 0;
                while (cy != 1 && guard++ < h) {
                    cy--;
                    tiles[cy * w + cx] = 0;
                }
                probe.bfs(1, 1, dist, queue);
            }
        }
    }
}
