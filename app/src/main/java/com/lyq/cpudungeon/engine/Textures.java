package com.lyq.cpudungeon.engine;

/**
 * Every texel and sprite in the game is generated procedurally at startup:
 * the APK contains no image assets at all. Textures are 64x64 ARGB tiles.
 */
public final class Textures {
    public static final int TS = 64;

    public static final int[] WALL_A = new int[TS * TS];
    public static final int[] WALL_B = new int[TS * TS];
    public static final int[] WALL_C = new int[TS * TS];
    public static final int[] WALL_D = new int[TS * TS];
    public static final int[][] WALLS = {WALL_A, WALL_B, WALL_C, WALL_D};
    public static final int[] FLOOR = new int[TS * TS];
    public static final int[] CEIL = new int[TS * TS];

    public static final Sprite DRONE;
    public static final Sprite DRONE_HIT;
    public static final Sprite CORE;
    public static final Sprite CORE_DIM;
    public static final Sprite PORTAL_OFF;
    public static final Sprite PORTAL_ON;
    public static final Sprite GLOW;

    static {
        buildWallA();
        buildWallB();
        buildWallC();
        buildWallD();
        buildFloor();
        buildCeil();

        Sprite drone = art2x(new String[]{
                "................",
                ".....BBBBBB.....",
                "....BBLLLLBB....",
                "...BBLBBBBLBB...",
                "..BBLBBBBBBLBB..",
                "..BLBBEEEEBBLB..",
                ".BBLBBEeeEBBLBB.",
                ".BLBBBEEEEBBBLB.",
                ".BLLBBBBBBBBLLB.",
                ".BBLLBBBBBBLLBB.",
                "..BBBLLBBLLBBB..",
                "...BBBBBBBBBB...",
                "....BBBBBBBB....",
                ".....BB..BB.....",
                "....DD....DD....",
                "................"
        }, "DBLEe", new int[]{
                0xFF141C26, 0xFF3C5168, 0xFF7E96AC, 0xFFFF3E8A, 0xFFFFB3D2
        });
        DRONE = drone;
        DRONE_HIT = tint(drone, 0xFFFFFFFF, 0.72f);

        CORE = art(new String[]{
                "............",
                ".....CC.....",
                "....CWWC....",
                "...CWWWWC...",
                "..CWWWWWWC..",
                ".CWWWWWWWWC.",
                ".CWWWWWWWWC.",
                "..CWWWWWWC..",
                "...CWWWWC...",
                "....CWWC....",
                ".....CC.....",
                "............"
        }, "CW", new int[]{0xFF23C9BB, 0xFFE9FFFB}).scale(2);

        CORE_DIM = tint(CORE, 0xFF2A4550, 0.8f);

        PORTAL_OFF = art(new String[]{
                "......PPPP......",
                "....PP....PP....",
                "...P........P...",
                "..P..........P..",
                ".P............P.",
                ".P............P.",
                "P..............P",
                "P..............P",
                "P..............P",
                "P..............P",
                ".P............P.",
                ".P............P.",
                "..P..........P..",
                "...P........P...",
                "....PP....PP....",
                "......PPPP......"
        }, "P", new int[]{0xFF7A3CA8}).scale(2);

        PORTAL_ON = art(new String[]{
                "......PPPP......",
                "....PP....PP....",
                "...P........P...",
                "..P..........P..",
                ".P............P.",
                ".P............P.",
                "P..............P",
                "P..............P",
                "P..............P",
                "P..............P",
                ".P............P.",
                ".P............P.",
                "..P..........P..",
                "...P........P...",
                "....PP....PP....",
                "......PPPP......"
        }, "P", new int[]{0xFF44FFE2}).scale(2);

        GLOW = buildGlow(16);
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static int hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    private static void buildWallA() {
        for (int y = 0; y < TS; y++) {
            for (int x = 0; x < TS; x++) {
                int n = hash(x >> 1, y >> 1, 11) & 15;
                int r = 26 + n;
                int g = 32 + n;
                int b = 45 + n;
                int px = x & 31;
                int py = y & 31;
                if (px == 0 || py == 0) {
                    r = 9;
                    g = 13;
                    b = 20;
                } else if (px == 1 || py == 1) {
                    r += 16;
                    g += 20;
                    b += 24;
                }
                if ((px == 4 || px == 5) && (py == 4 || py == 5)) {
                    r += 34;
                    g += 40;
                    b += 46;
                }
                // vertical energy conduit, broken by the panel seams
                if (x >= 27 && x <= 29 && (y & 31) > 3 && (y & 31) < 29) {
                    int pulse = 90 + (hash(0, y >> 1, 3) & 63);
                    r = 14;
                    g = pulse + 40;
                    b = pulse + 60;
                }
                WALL_A[y * TS + x] = rgb(r, g, b);
            }
        }
    }

    private static void buildWallB() {
        for (int y = 0; y < TS; y++) {
            for (int x = 0; x < TS; x++) {
                int n = hash(x, y, 23) & 7;
                int r = 20 + n;
                int g = 25 + n;
                int b = 34 + n;
                if (x % 16 == 0 || y % 16 == 0) {
                    r = 10;
                    g = 14;
                    b = 21;
                }
                boolean traceH = (y & 15) == 4 && (x & 7) < 6;
                boolean traceV = (x & 15) == 9 && (y & 7) < 6;
                if (traceH || traceV) {
                    r = 18;
                    g = 88;
                    b = 112;
                }
                if (((x & 15) == 9 && (y & 15) == 4)) {
                    r = 30;
                    g = 170;
                    b = 190;
                }
                WALL_B[y * TS + x] = rgb(r, g, b);
            }
        }
    }

    private static void buildWallC() {
        for (int y = 0; y < TS; y++) {
            for (int x = 0; x < TS; x++) {
                int n = hash(x, y >> 1, 41) & 15;
                int r = 52 + n + (hash(x >> 2, y, 7) & 7);
                int g = 36 + (n >> 1);
                int b = 27 + (n >> 2);
                if (hash(x >> 2, y >> 4, 5) % 5 == 0) {
                    r -= 14;
                    g -= 10;
                    b -= 8;
                }
                if ((y & 31) == 0) {
                    r = 20;
                    g = 16;
                    b = 14;
                }
                WALL_C[y * TS + x] = rgb(r, g, b);
            }
        }
    }

    private static void buildWallD() {
        for (int y = 0; y < TS; y++) {
            for (int x = 0; x < TS; x++) {
                int n = hash(x, y, 63) & 7;
                int r = 16 + n;
                int g = 12 + n;
                int b = 24 + n;
                if ((x & 31) == 0) {
                    r = 7;
                    g = 6;
                    b = 12;
                }
                // magenta hazard chevrons
                int d = (x + y) % 24;
                if (d < 4 && (x > 8 && x < 56)) {
                    r = 120 + (d * 12);
                    g = 24;
                    b = 70 + (d * 8);
                }
                if ((y & 31) == 16 && x > 12 && x < 52) {
                    r = 60;
                    g = 18;
                    b = 44;
                }
                WALL_D[y * TS + x] = rgb(r, g, b);
            }
        }
    }

    private static void buildFloor() {
        for (int y = 0; y < TS; y++) {
            for (int x = 0; x < TS; x++) {
                int n = hash(x >> 1, y >> 1, 71) & 15;
                int r = 12 + (n >> 1);
                int g = 17 + (n >> 1);
                int b = 26 + (n >> 1);
                if ((x & 15) == 0 || (y & 15) == 0) {
                    r = 16;
                    g = 58;
                    b = 68;
                } else if ((x & 15) == 1 && (y & 15) != 0) {
                    r = 18;
                    g = 34;
                    b = 42;
                }
                if (((x * 7 + y * 13) & 255) == 0) {
                    r = 40;
                    g = 120;
                    b = 130;
                }
                FLOOR[y * TS + x] = rgb(r, g, b);
            }
        }
    }

    private static void buildCeil() {
        for (int y = 0; y < TS; y++) {
            for (int x = 0; x < TS; x++) {
                int n = hash(x, y, 97) & 7;
                int r = 7 + (n >> 1);
                int g = 10 + (n >> 1);
                int b = 17 + (n >> 1);
                if ((x & 31) == 0 || (y & 31) == 0) {
                    r = 4;
                    g = 6;
                    b = 10;
                }
                if ((x & 15) == 8 && (y & 15) == 8) {
                    int halo = 3;
                    r = 8;
                    g = 20 + halo;
                    b = 26 + halo;
                }
                if ((x & 31) == 16 && (y & 31) == 16 && (hash(x, y, 3) & 3) == 0) {
                    r = 150;
                    g = 92;
                    b = 26;
                }
                CEIL[y * TS + x] = rgb(r, g, b);
            }
        }
    }

    /** 1x art scaled 2x so the drone reads well at close range. */
    private static Sprite art2x(String[] rows, String keys, int[] colors) {
        return art(rows, keys, colors).scale(2);
    }

    private static Sprite art(String[] rows, String keys, int[] colors) {
        int h = rows.length;
        int w = rows[0].length();
        int[] palette = new int[128];
        for (int i = 0; i < keys.length(); i++) {
            palette[keys.charAt(i)] = colors[i];
        }
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            String row = rows[y];
            for (int x = 0; x < w; x++) {
                char c = x < row.length() ? row.charAt(x) : '.';
                px[y * w + x] = c == '.' ? 0 : palette[c];
            }
        }
        return new Sprite(w, h, px);
    }

    private static Sprite tint(Sprite src, int color, float amount) {
        int tr = (color >> 16) & 255;
        int tg = (color >> 8) & 255;
        int tb = color & 255;
        int[] out = new int[src.px.length];
        for (int i = 0; i < out.length; i++) {
            int c = src.px[i];
            if ((c >>> 24) == 0) {
                continue;
            }
            int r = (int) (((c >> 16) & 255) * (1 - amount) + tr * amount);
            int g = (int) (((c >> 8) & 255) * (1 - amount) + tg * amount);
            int b = (int) ((c & 255) * (1 - amount) + tb * amount);
            out[i] = (c & 0xFF000000) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
        }
        return new Sprite(src.w, src.h, out);
    }

    private static Sprite buildGlow(int size) {
        int[] px = new int[size * size];
        float half = (size - 1) * 0.5f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x - half) / half;
                float dy = (y - half) / half;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                int a = (int) (255 * Math.max(0f, 1f - d));
                a = a * a / 255;
                px[y * size + x] = (a << 24) | 0xFFFFFF;
            }
        }
        return new Sprite(size, size, px);
    }

    private Textures() {
    }
}
