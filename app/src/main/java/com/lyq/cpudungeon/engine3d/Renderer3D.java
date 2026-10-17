package com.lyq.cpudungeon.engine3d;

import com.lyq.cpudungeon.engine.Sprite;

import java.util.Arrays;
import java.util.List;

/**
 * Software 3D rasterizer: camera transform -> near-plane clip -> projection ->
 * edge-function triangles -> perspective-correct texture + z-buffer.
 * Everything runs on the CPU and writes directly into an ARGB int[].
 */
public final class Renderer3D {
    private static final float NEAR = 0.08f;
    private static final int FOG_R = 6;
    private static final int FOG_G = 9;
    private static final int FOG_B = 16;
    private static final float FOG_NEAR = 2.5f;
    private static final float FOG_FAR = 15.0f;

    private float[] zbuf = new float[0];
    private float[] zbufXray = new float[0];
    private boolean xrayCleared;

    private final float[] cx = new float[4];
    private final float[] cy = new float[4];
    private final float[] cz = new float[4];
    private final float[] cu = new float[4];
    private final float[] cv = new float[4];

    private final float[] ox = new float[8];
    private final float[] oy = new float[8];
    private final float[] oz = new float[8];
    private final float[] ou = new float[8];
    private final float[] ov = new float[8];

    private final float[] bx = new float[4];
    private final float[] by = new float[4];
    private final float[] bz = new float[4];

    private final float[] sx = new float[8];
    private final float[] sy = new float[8];
    private final float[] siz = new float[8];
    private final float[] su = new float[8];
    private final float[] sv = new float[8];

    public void render(Mesh3D mesh, Camera3D cam, int[] fb, int w, int h, float time) {
        renderAll(mesh, null, null, 0, cam, fb, w, h, time);
    }

    public void render(Mesh3D mesh, Camera3D cam, List<Billboard3D> billboards,
                       int[] fb, int w, int h, float time) {
        renderAll(mesh, null, billboards, billboards == null ? 0 : billboards.size(), cam, fb, w, h, time);
    }

    public void render(Mesh3D mesh, Camera3D cam, List<Billboard3D> billboards, int billboardCount,
                       int[] fb, int w, int h, float time) {
        renderAll(mesh, null, billboards, billboardCount, cam, fb, w, h, time);
    }

    /** Static level geometry + optional per-frame enemy mesh + billboards. */
    public void renderAll(Mesh3D staticMesh, Mesh3D dynamicMesh, List<Billboard3D> billboards,
                          int billboardCount, Camera3D cam, int[] fb, int w, int h, float time) {
        int pixels = w * h;
        if (zbuf.length < pixels || zbufXray.length < pixels) {
            zbuf = new float[pixels];
            zbufXray = new float[pixels];
        }
        Arrays.fill(zbuf, 0, pixels, 0f);
        xrayCleared = false;
        int bg = 0xFF000000 | (FOG_R << 16) | (FOG_G << 8) | FOG_B;
        Arrays.fill(fb, 0, pixels, bg);

        for (int i = 0; i < staticMesh.count; i++) {
            drawQuad(staticMesh.quads[i], cam, fb, w, h);
        }
        if (dynamicMesh != null) {
            for (int i = 0; i < dynamicMesh.count; i++) {
                drawQuad(dynamicMesh.quads[i], cam, fb, w, h);
            }
        }
        if (billboards != null) {
            for (int i = 0; i < billboardCount; i++) {
                Billboard3D b = billboards.get(i);
                float dx = b.x - cam.x;
                float dy = b.y - cam.y;
                b.d2 = dx * dx + dy * dy;
            }
            for (int i = 1; i < billboardCount; i++) {
                Billboard3D key = billboards.get(i);
                int j = i - 1;
                while (j >= 0 && billboards.get(j).d2 < key.d2) {
                    billboards.set(j + 1, billboards.get(j));
                    j--;
                }
                billboards.set(j + 1, key);
            }
            for (int i = 0; i < billboardCount; i++) {
                drawBillboard(billboards.get(i), cam, fb, w, h);
            }
        }
    }

    private void drawBillboard(Billboard3D b, Camera3D cam, int[] fb, int w, int h) {
        float halfHeight = b.height * 0.5f;
        float halfWidth = halfHeight * (b.sprite.w / (float) b.sprite.h);
        float centerZ = b.z + halfHeight;
        float rx = cam.rx * halfWidth;
        float ry = cam.ry * halfWidth;
        float wx = b.x;
        float wy = b.y;

        float[] px = bx;
        float[] py = by;
        float[] pz = bz;
        px[0] = wx - rx; py[0] = wy - ry; pz[0] = centerZ - halfHeight;
        px[1] = wx + rx; py[1] = wy + ry; pz[1] = centerZ - halfHeight;
        px[2] = wx + rx; py[2] = wy + ry; pz[2] = centerZ + halfHeight;
        px[3] = wx - rx; py[3] = wy - ry; pz[3] = centerZ + halfHeight;

        for (int i = 0; i < 4; i++) {
            float dx = px[i] - cam.x;
            float dy = py[i] - cam.y;
            float dz = pz[i] - cam.z;
            cx[i] = dx * cam.rx + dy * cam.ry + dz * cam.rz;
            cy[i] = dx * cam.ux + dy * cam.uy + dz * cam.uz;
            cz[i] = dx * cam.fx + dy * cam.fy + dz * cam.fz;
            cu[i] = i == 1 || i == 2 ? 1f : 0f;
            cv[i] = i >= 2 ? 0f : 1f;
        }
        if (cz[0] < NEAR && cz[1] < NEAR && cz[2] < NEAR && cz[3] < NEAR) {
            return;
        }
        int n = clipNear();
        if (n < 3) {
            return;
        }
        float halfW = w * 0.5f;
        float halfH = h * 0.5f;
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float invZ = 1f / oz[i];
            float sxp = halfW + ox[i] * cam.focal * invZ;
            float syp = halfH - oy[i] * cam.focal * invZ;
            sx[i] = sxp;
            sy[i] = syp;
            siz[i] = invZ;
            su[i] = ou[i] * invZ;
            sv[i] = ov[i] * invZ;
            if (sxp < minX) minX = sxp;
            if (sxp > maxX) maxX = sxp;
            if (syp < minY) minY = syp;
            if (syp > maxY) maxY = syp;
        }
        if (maxX < 0f || minX > w || maxY < 0f || minY > h) {
            return;
        }
        for (int i = 1; i < n - 1; i++) {
            rasterSprite(0, i, i + 1, b, fb, w, h);
        }
    }

    private void rasterSprite(int ia, int ib, int ic, Billboard3D b, int[] fb, int w, int h) {
        float x0 = sx[ia], y0 = sy[ia], z0 = siz[ia], u0 = su[ia], v0 = sv[ia];
        float x1 = sx[ib], y1 = sy[ib], z1 = siz[ib], u1 = su[ib], v1 = sv[ib];
        float x2 = sx[ic], y2 = sy[ic], z2 = siz[ic], u2 = su[ic], v2 = sv[ic];
        float area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
        if (area > -1e-6f && area < 1e-6f) {
            return;
        }
        if (area < 0f) {
            float tx = x1; x1 = x2; x2 = tx;
            float ty = y1; y1 = y2; y2 = ty;
            float tz = z1; z1 = z2; z2 = tz;
            float tu = u1; u1 = u2; u2 = tu;
            float tv = v1; v1 = v2; v2 = tv;
            area = -area;
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
        if (minX > maxX || minY > maxY) {
            return;
        }
        float invArea = 1f / area;
        float a0 = y1 - y2, b0 = x2 - x1, c0 = x1 * y2 - x2 * y1;
        float a1 = y2 - y0, b1 = x0 - x2, c1 = x2 * y0 - x0 * y2;
        float a2 = y0 - y1, b2 = x1 - x0, c2 = x0 * y1 - x1 * y0;

        float izAvg = (z0 + z1 + z2) * (1f / 3f);
        float camDepth = izAvg > 1e-6f ? 1f / izAvg : FOG_FAR;
        float fog = 1f - (camDepth - FOG_NEAR) / (FOG_FAR - FOG_NEAR);
        if (fog < 0f) fog = 0f;
        if (fog > 1f) fog = 1f;
        float bright = b.brightness * (0.18f + 0.82f * fog);
        int tr = (int) (((b.tint >> 16) & 255) * bright);
        int tg = (int) (((b.tint >> 8) & 255) * bright);
        int tb = (int) ((b.tint & 255) * bright);
        int fr = (int) ((1f - fog) * FOG_R);
        int fg = (int) ((1f - fog) * FOG_G);
        int fbl = (int) ((1f - fog) * FOG_B);
        Sprite sprite = b.sprite;
        int sw = sprite.w;
        int sh = sprite.h;

        float w0Row = a0 * (minX + 0.5f) + b0 * (minY + 0.5f) + c0;
        float w1Row = a1 * (minX + 0.5f) + b1 * (minY + 0.5f) + c1;
        float w2Row = a2 * (minX + 0.5f) + b2 * (minY + 0.5f) + c2;
        for (int y = minY; y <= maxY; y++) {
            float w0 = w0Row, w1 = w1Row, w2 = w2Row;
            int row = y * w;
            for (int x = minX; x <= maxX; x++) {
                if (w0 >= 0f && w1 >= 0f && w2 >= 0f) {
                    float iz = (w0 * z0 + w1 * z1 + w2 * z2) * invArea;
                    int idx = row + x;
                    if (b.xray || iz > zbuf[idx]) {
                        float invIz = 1f / iz;
                        float u = (w0 * u0 + w1 * u1 + w2 * u2) * invArea * invIz;
                        float v = (w0 * v0 + w1 * v1 + w2 * v2) * invArea * invIz;
                        int tx = (int) (u * sw);
                        int ty = (int) (v * sh);
                        if (tx < 0) tx = 0;
                        if (ty < 0) ty = 0;
                        if (tx >= sw) tx = sw - 1;
                        if (ty >= sh) ty = sh - 1;
                        int c = sprite.px[ty * sw + tx];
                        int a = c >>> 24;
                        if (a < 24) {
                            w0 += a0; w1 += a1; w2 += a2;
                            continue;
                        }
                        int r = (((c >> 16) & 255) * tr >> 8) + fr;
                        int g = (((c >> 8) & 255) * tg >> 8) + fg;
                        int bl = ((c & 255) * tb >> 8) + fbl;
                        if (r > 255) r = 255;
                        if (g > 255) g = 255;
                        if (bl > 255) bl = 255;
                        if (a >= 248) {
                            fb[idx] = 0xFF000000 | (r << 16) | (g << 8) | bl;
                        } else {
                            int dst = fb[idx];
                            int ia2 = 255 - a;
                            int dr = (((dst >> 16) & 255) * ia2 + r * a) / 255;
                            int dg = (((dst >> 8) & 255) * ia2 + g * a) / 255;
                            int db = ((dst & 255) * ia2 + bl * a) / 255;
                            fb[idx] = 0xFF000000 | (dr << 16) | (dg << 8) | db;
                        }
                        if (!b.xray) {
                            zbuf[idx] = iz;
                        }
                    }
                }
                w0 += a0; w1 += a1; w2 += a2;
            }
            w0Row += b0; w1Row += b1; w2Row += b2;
        }
    }

    private void drawQuad(Quad3D q, Camera3D cam, int[] fb, int w, int h) {
        float[] depth = zbuf;
        if (q.xray) {
            if (!xrayCleared) {
                Arrays.fill(zbufXray, 0, w * h, 0f);
                xrayCleared = true;
            }
            depth = zbufXray;
        }
        float centerX = (q.px[0] + q.px[2]) * 0.5f - cam.x;
        float centerY = (q.py[0] + q.py[2]) * 0.5f - cam.y;
        float centerZ = (q.pz[0] + q.pz[2]) * 0.5f - cam.z;
        if (centerX * centerX + centerY * centerY + centerZ * centerZ > 16.5f * 16.5f) {
            return;
        }
        if (!q.solid && (q.nx != 0f || q.ny != 0f || q.nz != 0f)
                && q.nx * centerX + q.ny * centerY + q.nz * centerZ >= 0f) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            float dx = q.px[i] - cam.x;
            float dy = q.py[i] - cam.y;
            float dz = q.pz[i] - cam.z;
            cx[i] = dx * cam.rx + dy * cam.ry + dz * cam.rz;
            cy[i] = dx * cam.ux + dy * cam.uy + dz * cam.uz;
            cz[i] = dx * cam.fx + dy * cam.fy + dz * cam.fz;
            cu[i] = q.u[i];
            cv[i] = q.v[i];
        }
        if (cz[0] < NEAR && cz[1] < NEAR && cz[2] < NEAR && cz[3] < NEAR) {
            return;
        }
        int n = clipNear();
        if (n < 3) {
            return;
        }

        float halfW = w * 0.5f;
        float halfH = h * 0.5f;
        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float invZ = 1f / oz[i];
            float px = halfW + ox[i] * cam.focal * invZ;
            float py = halfH - oy[i] * cam.focal * invZ;
            sx[i] = px;
            sy[i] = py;
            siz[i] = invZ;
            su[i] = ou[i] * invZ;
            sv[i] = ov[i] * invZ;
            if (px < minX) minX = px;
            if (px > maxX) maxX = px;
            if (py < minY) minY = py;
            if (py > maxY) maxY = py;
        }
        if (maxX < 0f || minX > w || maxY < 0f || minY > h) {
            return;
        }
        for (int i = 1; i < n - 1; i++) {
            rasterTri(0, i, i + 1, q.tex, q.solid, q.solid ? q.color : 0,
                    q.shade, depth, fb, w, h);
        }
    }

    /** Sutherland-Hodgman against the camera near plane; writes ox/oy/oz/ou/ov. */
    private int clipNear() {
        int n = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            boolean inI = cz[i] >= NEAR;
            boolean inJ = cz[j] >= NEAR;
            if (inI) {
                ox[n] = cx[i];
                oy[n] = cy[i];
                oz[n] = cz[i];
                ou[n] = cu[i];
                ov[n] = cv[i];
                n++;
            }
            if (inI != inJ) {
                float t = (NEAR - cz[i]) / (cz[j] - cz[i]);
                ox[n] = cx[i] + (cx[j] - cx[i]) * t;
                oy[n] = cy[i] + (cy[j] - cy[i]) * t;
                oz[n] = NEAR;
                ou[n] = cu[i] + (cu[j] - cu[i]) * t;
                ov[n] = cv[i] + (cv[j] - cv[i]) * t;
                n++;
            }
        }
        return n;
    }

    private void rasterTri(int ia, int ib, int ic, int texId, boolean solid, int solidColor,
                           float shade, float[] depth, int[] fb, int w, int h) {
        float x0 = sx[ia];
        float y0 = sy[ia];
        float z0 = siz[ia];
        float u0 = su[ia];
        float v0 = sv[ia];
        float x1 = sx[ib];
        float y1 = sy[ib];
        float z1 = siz[ib];
        float u1 = su[ib];
        float v1 = sv[ib];
        float x2 = sx[ic];
        float y2 = sy[ic];
        float z2 = siz[ic];
        float u2 = su[ic];
        float v2 = sv[ic];

        float area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
        if (area > -1e-6f && area < 1e-6f) {
            return;
        }
        if (area < 0f) {
            float tx = x1; x1 = x2; x2 = tx;
            float ty = y1; y1 = y2; y2 = ty;
            float tz = z1; z1 = z2; z2 = tz;
            float tu = u1; u1 = u2; u2 = tu;
            float tv = v1; v1 = v2; v2 = tv;
            area = -area;
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
        if (minX > maxX || minY > maxY) {
            return;
        }

        float invArea = 1f / area;
        float a0 = y1 - y2;
        float b0 = x2 - x1;
        float c0 = x1 * y2 - x2 * y1;
        float a1 = y2 - y0;
        float b1 = x0 - x2;
        float c1 = x2 * y0 - x0 * y2;
        float a2 = y0 - y1;
        float b2 = x1 - x0;
        float c2 = x0 * y1 - x1 * y0;

        float izAvg = (z0 + z1 + z2) * (1f / 3f);
        float camDepth = izAvg > 1e-6f ? 1f / izAvg : FOG_FAR;
        float fog = 1f - (camDepth - FOG_NEAR) / (FOG_FAR - FOG_NEAR);
        if (fog < 0f) fog = 0f;
        if (fog > 1f) fog = 1f;
        int light = (int) (shade * fog * 300f);
        if (light > 300) light = 300;
        int addR = (int) ((1f - fog) * FOG_R + fog * 10f);
        int addG = (int) ((1f - fog) * FOG_G + fog * 14f);
        int addB = (int) ((1f - fog) * FOG_B + fog * 20f);
        int mip = 0;
        int[] tex = null;
        int size = 0;
        int solidPixel = 0;
        if (solid) {
            int sr = (((solidColor >> 16) & 255) * light >> 8) + addR;
            int sg = (((solidColor >> 8) & 255) * light >> 8) + addG;
            int sb = ((solidColor & 255) * light >> 8) + addB;
            if (sr > 255) sr = 255;
            if (sg > 255) sg = 255;
            if (sb > 255) sb = 255;
            solidPixel = 0xFF000000 | (sr << 16) | (sg << 8) | sb;
        } else {
            mip = camDepth < 2.2f ? 0 : camDepth < 4.5f ? 1 : camDepth < 9f ? 2 : 3;
            tex = MipTextures.TEXTURES[texId][mip];
            size = 64 >> mip;
        }

        float w0Row = a0 * (minX + 0.5f) + b0 * (minY + 0.5f) + c0;
        float w1Row = a1 * (minX + 0.5f) + b1 * (minY + 0.5f) + c1;
        float w2Row = a2 * (minX + 0.5f) + b2 * (minY + 0.5f) + c2;

        for (int y = minY; y <= maxY; y++) {
            float w0 = w0Row;
            float w1 = w1Row;
            float w2 = w2Row;
            int row = y * w;
            for (int x = minX; x <= maxX; x++) {
                if (w0 >= 0f && w1 >= 0f && w2 >= 0f) {
                    float iz = (w0 * z0 + w1 * z1 + w2 * z2) * invArea;
                    int idx = row + x;
                    if (iz > depth[idx]) {
                        if (solid) {
                            fb[idx] = solidPixel;
                        } else {
                            float invIz = 1f / iz;
                            float u = (w0 * u0 + w1 * u1 + w2 * u2) * invArea * invIz;
                            float v = (w0 * v0 + w1 * v1 + w2 * v2) * invArea * invIz;
                            int tu = ((int) (u * 64f)) & 63;
                            int tv = ((int) (v * 64f)) & 63;
                            int c = tex[(tv >> mip) * size + (tu >> mip)];
                            int r = (((c >> 16) & 255) * light >> 8) + addR;
                            int g = (((c >> 8) & 255) * light >> 8) + addG;
                            int b = ((c & 255) * light >> 8) + addB;
                            if (r > 255) r = 255;
                            if (g > 255) g = 255;
                            if (b > 255) b = 255;
                            fb[idx] = 0xFF000000 | (r << 16) | (g << 8) | b;
                        }
                        depth[idx] = iz;
                    }
                }
                w0 += a0;
                w1 += a1;
                w2 += a2;
            }
            w0Row += b0;
            w1Row += b1;
            w2Row += b2;
        }
    }
}
