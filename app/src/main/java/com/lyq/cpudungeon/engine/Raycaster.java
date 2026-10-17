package com.lyq.cpudungeon.engine;

/**
 * CPU renderer. No OpenGL, no Vulkan, no Canvas primitives for the 3D view:
 * every visible pixel is produced here by integer/float math into an ARGB
 * framebuffer (wall DDA raycaster + textured floor/ceiling + z-buffered
 * billboards), then blitted once per frame.
 */
public final class Raycaster {
    /** tan(half horizontal FOV); ~79 degrees. */
    private static final float PLANE = 0.82f;
    private static final int FOG_R = 6;
    private static final int FOG_G = 9;
    private static final int FOG_B = 16;
    private static final float FOG_START = 2.2f;
    private static final float FOG_END = 15.0f;

    private float[] zbuf = new float[0];
    private Billboard[] pool = new Billboard[0];
    private int count;

    private static final class Billboard {
        Sprite sprite;
        float x;
        float y;
        float elev;
        float height;
        float d2;
        int tint;
        float brightness;
        boolean additive;
    }

    public void render(Game game, int[] fb, int w, int h, float time) {
        if (zbuf.length < w) {
            zbuf = new float[w];
        }
        Player p = game.player;
        float camX = p.x;
        float camY = p.y;
        float dirX = (float) Math.cos(p.angle);
        float dirY = (float) Math.sin(p.angle);
        float planeX = -dirY * PLANE;
        float planeY = dirX * PLANE;

        float bobOffset = (float) Math.sin(p.bob) * 2.6f * p.moveSpeed
                + (float) Math.sin(p.bob * 0.5f) * 0.9f * p.moveSpeed;
        int horizon = (int) (h * 0.5f + bobOffset);
        if (horizon < 6) horizon = 6;
        if (horizon > h - 7) horizon = h - 7;

        drawCeiling(fb, w, h, camX, camY, dirX, dirY, planeX, planeY, horizon);
        drawFloor(fb, w, h, camX, camY, dirX, dirY, planeX, planeY, horizon);
        drawWalls(game, fb, w, h, camX, camY, dirX, dirY, planeX, planeY, horizon);
        queueBillboards(game, camX, camY, time);
        drawBillboards(fb, w, h, camX, camY, dirX, dirY, planeX, planeY, horizon);
    }

    private void drawCeiling(int[] fb, int w, int h, float camX, float camY,
                             float dirX, float dirY, float planeX, float planeY, int horizon) {
        float posZ = 0.5f * h;
        float rdx0 = dirX - planeX;
        float rdy0 = dirY - planeY;
        float rdx1 = dirX + planeX;
        float rdy1 = dirY + planeY;
        int[] tex = Textures.CEIL;
        for (int y = 0; y < horizon; y++) {
            int pRow = horizon - y;
            float rowDist = posZ / pRow;
            int row = y * w;
            if (rowDist > 22f) {
                fillFog(fb, row, w, 0.75f);
                continue;
            }
            float shadeF = fog(rowDist) * 0.86f;
            int shade = (int) (shadeF * 256f);
            int ambR = (FOG_R * (256 - shade)) >> 8;
            int ambG = (FOG_G * (256 - shade)) >> 8;
            int ambB = (FOG_B * (256 - shade)) >> 8;
            float stepX = rowDist * (rdx1 - rdx0) / w;
            float stepY = rowDist * (rdy1 - rdy0) / w;
            float fx = camX + rowDist * rdx0;
            float fy = camY + rowDist * rdy0;
            for (int x = 0; x < w; x++) {
                int tx = ((int) (fx * Textures.TS)) & (Textures.TS - 1);
                int ty = ((int) (fy * Textures.TS)) & (Textures.TS - 1);
                int c = tex[(ty << 6) + tx];
                int r = (((c >> 16) & 255) * shade >> 8) + ambR;
                int g = (((c >> 8) & 255) * shade >> 8) + ambG;
                int b = ((c & 255) * shade >> 8) + ambB;
                fb[row + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                fx += stepX;
                fy += stepY;
            }
        }
    }

    private void drawFloor(int[] fb, int w, int h, float camX, float camY,
                           float dirX, float dirY, float planeX, float planeY, int horizon) {
        float posZ = 0.5f * h;
        float rdx0 = dirX - planeX;
        float rdy0 = dirY - planeY;
        float rdx1 = dirX + planeX;
        float rdy1 = dirY + planeY;
        int[] tex = Textures.FLOOR;
        for (int y = horizon + 1; y < h; y++) {
            int pRow = y - horizon;
            float rowDist = posZ / pRow;
            int row = y * w;
            if (rowDist > 22f) {
                fillFog(fb, row, w, 1f);
                continue;
            }
            float shadeF = fog(rowDist);
            int shade = (int) (shadeF * 256f);
            int ambR = (FOG_R * (256 - shade)) >> 8;
            int ambG = (FOG_G * (256 - shade)) >> 8;
            int ambB = (FOG_B * (256 - shade)) >> 8;
            float stepX = rowDist * (rdx1 - rdx0) / w;
            float stepY = rowDist * (rdy1 - rdy0) / w;
            float fx = camX + rowDist * rdx0;
            float fy = camY + rowDist * rdy0;
            for (int x = 0; x < w; x++) {
                int tx = ((int) (fx * Textures.TS)) & (Textures.TS - 1);
                int ty = ((int) (fy * Textures.TS)) & (Textures.TS - 1);
                int c = tex[(ty << 6) + tx];
                int r = (((c >> 16) & 255) * shade >> 8) + ambR;
                int g = (((c >> 8) & 255) * shade >> 8) + ambG;
                int b = ((c & 255) * shade >> 8) + ambB;
                fb[row + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                fx += stepX;
                fy += stepY;
            }
        }
    }

    private void drawWalls(Game game, int[] fb, int w, int h, float camX, float camY,
                           float dirX, float dirY, float planeX, float planeY, int horizon) {
        MapData map = game.map;
        float texScale = Textures.TS;
        for (int x = 0; x < w; x++) {
            float cameraX = 2f * x / w - 1f;
            float rdx = dirX + planeX * cameraX;
            float rdy = dirY + planeY * cameraX;
            int mapX = (int) Math.floor(camX);
            int mapY = (int) Math.floor(camY);
            float deltaX = rdx == 0f ? 1e30f : Math.abs(1f / rdx);
            float deltaY = rdy == 0f ? 1e30f : Math.abs(1f / rdy);
            int stepX;
            int stepY;
            float sideDistX;
            float sideDistY;
            if (rdx < 0f) {
                stepX = -1;
                sideDistX = (camX - mapX) * deltaX;
            } else {
                stepX = 1;
                sideDistX = (mapX + 1f - camX) * deltaX;
            }
            if (rdy < 0f) {
                stepY = -1;
                sideDistY = (camY - mapY) * deltaY;
            } else {
                stepY = 1;
                sideDistY = (mapY + 1f - camY) * deltaY;
            }
            int side = 0;
            boolean hit = false;
            for (int step = 0; step < 96; step++) {
                if (sideDistX < sideDistY) {
                    sideDistX += deltaX;
                    mapX += stepX;
                    side = 0;
                } else {
                    sideDistY += deltaY;
                    mapY += stepY;
                    side = 1;
                }
                if (map.solid(mapX, mapY)) {
                    hit = true;
                    break;
                }
            }
            float perp;
            if (hit) {
                perp = side == 0 ? sideDistX - deltaX : sideDistY - deltaY;
            } else {
                perp = 40f;
            }
            if (perp < 0.02f) perp = 0.02f;
            zbuf[x] = perp;

            int lineHeight = (int) (h / perp);
            int drawStart = horizon - lineHeight / 2;
            int drawEnd = horizon + lineHeight / 2;

            if (hit) {
                float wallX = side == 0 ? camY + perp * rdy : camX + perp * rdx;
                wallX -= (float) Math.floor(wallX);
                int texX = (int) (wallX * Textures.TS);
                if (side == 0 && rdx > 0f) texX = Textures.TS - texX - 1;
                if (side == 1 && rdy < 0f) texX = Textures.TS - texX - 1;
                texX &= Textures.TS - 1;
                byte tile = map.tiles[mapY * map.w + mapX];
                int[] tex = Textures.WALLS[(tile - 1) & 3];

                float shadeF = fog(perp) * (side == 1 ? 0.68f : 1f);
                int shade = (int) (shadeF * 256f);
                int ambR = (FOG_R * (256 - shade)) >> 8;
                int ambG = (FOG_G * (256 - shade)) >> 8;
                int ambB = (FOG_B * (256 - shade)) >> 8;
                int y0 = Math.max(0, drawStart);
                int y1 = Math.min(h - 1, drawEnd);
                float step = texScale / lineHeight;
                float texPos = (y0 - (horizon - lineHeight * 0.5f)) * step;
                int col = texX;
                for (int y = y0; y <= y1; y++) {
                    int ty = ((int) texPos) & (Textures.TS - 1);
                    texPos += step;
                    int c = tex[(ty << 6) + col];
                    int r = (((c >> 16) & 255) * shade >> 8) + ambR;
                    int g = (((c >> 8) & 255) * shade >> 8) + ambG;
                    int b = ((c & 255) * shade >> 8) + ambB;
                    fb[y * w + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
                }
            } else {
                // no wall hit: fill the column with fog so sprites can still draw
                int y0 = Math.max(0, drawStart);
                int y1 = Math.min(h - 1, drawEnd);
                for (int y = y0; y <= y1; y++) {
                    fb[y * w + x] = 0xFF000000 | (FOG_R << 16) | (FOG_G << 8) | FOG_B;
                }
            }
        }
    }

    private void queueBillboards(Game game, float camX, float camY, float time) {
        count = 0;
        float px = game.map.exitX + 0.5f;
        float py = game.map.exitY + 0.5f;
        boolean active = game.exitActive;
        float pulse = active
                ? 1.0f + 0.28f * (float) Math.sin(time * 6.0f)
                : 0.45f + 0.06f * (float) Math.sin(time * 2.0f);
        addSprite(active ? Textures.PORTAL_ON : Textures.PORTAL_OFF, px, py, 0f, 1.75f,
                0xFFFFFFFF, pulse, true, camX, camY);

        for (Pickup p : game.pickups) {
            if (p.taken) {
                continue;
            }
            float bob = 0.16f + 0.05f * (float) Math.sin(p.spin * 1.7f);
            addSprite(Textures.CORE, p.x, p.y, bob, 0.34f,
                    0xFFFFFFFF, 1.05f + 0.15f * (float) Math.sin(p.spin * 2.2f), true, camX, camY);
        }
        for (Enemy e : game.enemies) {
            if (e.dead) {
                continue;
            }
            float hover = 0.10f + 0.05f * (float) Math.sin(e.phase);
            float bright = 0.9f + e.alert * 0.25f;
            Sprite s = e.hitFlash > 0f ? Textures.DRONE_HIT : Textures.DRONE;
            addSprite(s, e.x, e.y, hover, 0.60f, 0xFFFFFFFF, bright, false, camX, camY);
        }
        for (Particle p : game.particles) {
            float life = p.life / p.maxLife;
            addSprite(Textures.GLOW, p.x, p.y, p.z, p.size * 2.6f,
                    p.color, Math.min(1.2f, life + 0.25f), true, camX, camY);
        }
    }

    private void addSprite(Sprite sprite, float x, float y, float elev, float height,
                           int tint, float brightness, boolean additive, float camX, float camY) {
        if (count >= pool.length) {
            Billboard[] bigger = new Billboard[Math.max(32, pool.length * 2)];
            System.arraycopy(pool, 0, bigger, 0, pool.length);
            pool = bigger;
        }
        Billboard b = pool[count];
        if (b == null) {
            b = new Billboard();
            pool[count] = b;
        }
        b.sprite = sprite;
        b.x = x;
        b.y = y;
        b.elev = elev;
        b.height = height;
        b.tint = tint;
        b.brightness = brightness;
        b.additive = additive;
        float dx = x - camX;
        float dy = y - camY;
        b.d2 = dx * dx + dy * dy;
        count++;
    }

    private void drawBillboards(int[] fb, int w, int h, float camX, float camY,
                                float dirX, float dirY, float planeX, float planeY, int horizon) {
        // painter's order, farthest first
        for (int i = 1; i < count; i++) {
            Billboard key = pool[i];
            int j = i - 1;
            while (j >= 0 && pool[j].d2 < key.d2) {
                pool[j + 1] = pool[j];
                j--;
            }
            pool[j + 1] = key;
        }
        float invDet = 1f / (planeX * dirY - dirX * planeY);
        for (int i = 0; i < count; i++) {
            Billboard b = pool[i];
            float sx = b.x - camX;
            float sy = b.y - camY;
            float tx = invDet * (dirY * sx - dirX * sy);
            float ty = invDet * (-planeY * sx + planeX * sy);
            if (ty < 0.18f) {
                continue;
            }
            int screenX = (int) ((w * 0.5f) * (1f + tx / ty));
            int spriteH = (int) (b.height * h / ty);
            if (spriteH < 1) {
                continue;
            }
            int spriteW = (int) (spriteH * (b.sprite.w / (float) b.sprite.h));
            if (spriteW < 1) {
                continue;
            }
            int bottom = (int) (horizon + (0.5f - b.elev) * h / ty);
            int top = bottom - spriteH;
            int left = screenX - spriteW / 2;
            if (left + spriteW < 0 || left >= w) {
                continue;
            }
            float shadeF = fog(ty);
            int shade = (int) (shadeF * 256f);
            int tintR = ((b.tint >> 16) & 255) * shade >> 8;
            int tintG = ((b.tint >> 8) & 255) * shade >> 8;
            int tintB = (b.tint & 255) * shade >> 8;
            int br = (int) (b.brightness * 256f);
            tintR = tintR * br >> 8;
            tintG = tintG * br >> 8;
            tintB = tintB * br >> 8;

            int x0 = Math.max(0, left);
            int x1 = Math.min(w - 1, left + spriteW - 1);
            int y0 = Math.max(0, top);
            int y1 = Math.min(h - 1, bottom);
            for (int x = x0; x <= x1; x++) {
                if (ty >= zbuf[x]) {
                    continue;
                }
                int texX = (x - left) * b.sprite.w / spriteW;
                if (texX >= b.sprite.w) texX = b.sprite.w - 1;
                for (int y = y0; y <= y1; y++) {
                    int texY = (y - top) * b.sprite.h / spriteH;
                    if (texY >= b.sprite.h) texY = b.sprite.h - 1;
                    int c = b.sprite.px[texY * b.sprite.w + texX];
                    int a = c >>> 24;
                    if (a < 24) {
                        continue;
                    }
                    int r = (((c >> 16) & 255) * tintR) >> 8;
                    int g = (((c >> 8) & 255) * tintG) >> 8;
                    int bl = ((c & 255) * tintB) >> 8;
                    if (r > 255) r = 255;
                    if (g > 255) g = 255;
                    if (bl > 255) bl = 255;
                    int idx = y * w + x;
                    if (a >= 250) {
                        fb[idx] = 0xFF000000 | (r << 16) | (g << 8) | bl;
                    } else {
                        int dst = fb[idx];
                        int ia = 255 - a;
                        int dr = (((dst >> 16) & 255) * ia + r * a) / 255;
                        int dg = (((dst >> 8) & 255) * ia + g * a) / 255;
                        int db = ((dst & 255) * ia + bl * a) / 255;
                        fb[idx] = 0xFF000000 | (dr << 16) | (dg << 8) | db;
                    }
                }
            }
        }
    }

    private static float fog(float dist) {
        if (dist <= FOG_START) return 1f;
        float f = 1f - (dist - FOG_START) / (FOG_END - FOG_START);
        return f < 0f ? 0f : (f > 1f ? 1f : f);
    }

    private static void fillFog(int[] fb, int row, int w, float factor) {
        int r = (int) (FOG_R * factor);
        int g = (int) (FOG_G * factor);
        int b = (int) (FOG_B * factor);
        int color = 0xFF000000 | (r << 16) | (g << 8) | b;
        for (int x = 0; x < w; x++) {
            fb[row + x] = color;
        }
    }
}
