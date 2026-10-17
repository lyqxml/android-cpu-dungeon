package com.lyq.cpudungeon.engine3d;

import com.lyq.cpudungeon.engine.Enemy;

/**
 * Hand-built 3D spider drone: body box, head, emissive eye and six two-segment
 * legs. Every quad is transformed and rotated on the CPU per frame.
 */
public final class EnemyModel {
    private static final float PI = (float) Math.PI;

    private EnemyModel() {
    }

    public static void build(Enemy e, Level3D level, Mesh3D out, boolean xray) {
        float base = level.floorAt(e.x, e.y);
        float yaw = e.yaw;
        float bob = (float) Math.sin(e.phase * 0.9f) * 0.035f;
        float bodyZ = base + 0.95f + bob;
        float flash = Math.min(1f, e.hitFlash * 5f);

        int bodyColor = mix(0xFF465C74, 0xFFFFFFFF, flash);
        int plateColor = mix(0xFF6C8AA8, 0xFFFFFFFF, flash);
        int headColor = mix(0xFF587491, 0xFFFFFFFF, flash);
        int legColor = mix(0xFF3E5468, 0xFFE8F4FF, flash * 0.6f);
        int eyeColor = mix(0xFFFF3E8A, 0xFFFFFFFF, flash);

        float fx = (float) Math.cos(yaw);
        float fy = (float) Math.sin(yaw);
        float rx = -fy;
        float ry = fx;

        addBox(out, e.x, e.y, bodyZ, 0.30f, 0.22f, 0.13f, fx, fy, rx, ry,
                bodyColor, plateColor, xray);

        float hx = e.x + fx * 0.34f;
        float hy = e.y + fy * 0.34f;
        addBox(out, hx, hy, bodyZ + 0.02f, 0.12f, 0.15f, 0.10f, fx, fy, rx, ry,
                headColor, plateColor, xray);

        // emissive eye on the front of the head
        float ex = hx + fx * 0.13f;
        float ey = hy + fy * 0.13f;
        Quad3D eye = out.next();
        float eyeHalf = 0.055f;
        eye.setSolid(
                ex - rx * eyeHalf, ey - ry * eyeHalf, bodyZ + 0.025f,
                ex + rx * eyeHalf, ey + ry * eyeHalf, bodyZ + 0.025f,
                ex + rx * eyeHalf, ey + ry * eyeHalf, bodyZ + 0.085f,
                ex - rx * eyeHalf, ey - ry * eyeHalf, bodyZ + 0.085f,
                eyeColor, 1.4f);
        eye.xray = xray;

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < 3; i++) {
                float localForward = (i - 1) * 0.19f;
                float phase = e.phase * 1.7f + i * 1.1f + (side > 0 ? 0.6f : 0f);
                float lift = Math.max(0f, (float) Math.sin(phase)) * 0.10f;
                float swing = (float) Math.cos(phase) * 0.06f;

                float hipX = e.x + fx * localForward + rx * side * 0.19f;
                float hipY = e.y + fy * localForward + ry * side * 0.19f;
                float hipZ = bodyZ - 0.05f;

                float footForward = localForward + swing;
                float footDist = 0.54f + (i == 1 ? 0.04f : 0f);
                float footX = e.x + fx * footForward + rx * side * footDist;
                float footY = e.y + fy * footForward + ry * side * footDist;
                float footZ = base + 0.03f + lift;

                float kneeX = (hipX + footX) * 0.5f + rx * side * 0.10f;
                float kneeY = (hipY + footY) * 0.5f + ry * side * 0.10f;
                float kneeZ = Math.max(footZ + 0.12f, bodyZ - 0.18f);

                addBeam(out, hipX, hipY, hipZ, kneeX, kneeY, kneeZ, 0.037f, legColor, xray);
                addBeam(out, kneeX, kneeY, kneeZ, footX, footY, footZ, 0.028f, legColor, xray);
            }
        }
    }

    private static void addBox(Mesh3D out, float cx, float cy, float cz,
                               float halfF, float halfR, float halfH,
                               float fx, float fy, float rx, float ry,
                               int color, int topColor, boolean xray) {
        float x0 = cx - fx * halfF - rx * halfR;
        float y0 = cy - fy * halfF - ry * halfR;
        float x1 = cx + fx * halfF - rx * halfR;
        float y1 = cy + fy * halfF - ry * halfR;
        float x2 = cx + fx * halfF + rx * halfR;
        float y2 = cy + fy * halfF + ry * halfR;
        float x3 = cx - fx * halfF + rx * halfR;
        float y3 = cy - fy * halfF + ry * halfR;

        float zt = cz + halfH;
        float zb = cz - halfH;

        addQuad(out, x0, y0, zt, x1, y1, zt, x2, y2, zt, x3, y3, zt, topColor, 1.05f, xray);
        addQuad(out, x3, y3, zb, x2, y2, zb, x1, y1, zb, x0, y0, zb, color, 0.45f, xray);
        addQuad(out, x0, y0, zb, x1, y1, zb, x1, y1, zt, x0, y0, zt, color, 0.85f, xray);
        addQuad(out, x1, y1, zb, x2, y2, zb, x2, y2, zt, x1, y1, zt, color, 0.72f, xray);
        addQuad(out, x2, y2, zb, x3, y3, zb, x3, y3, zt, x2, y2, zt, color, 0.85f, xray);
        addQuad(out, x3, y3, zb, x0, y0, zb, x0, y0, zt, x3, y3, zt, color, 0.72f, xray);
    }

    private static void addBeam(Mesh3D out, float ax, float ay, float az,
                                float bx, float by, float bz, float t,
                                int color, boolean xray) {
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5f) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;
        float ux = 0f;
        float uy = 0f;
        float uz = 1f;
        if (Math.abs(dz) > 0.92f) {
            ux = 1f;
            uz = 0f;
        }
        float sx = dy * uz - dz * uy;
        float sy = dz * ux - dx * uz;
        float sz = dx * uy - dy * ux;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl;
        sy /= sl;
        sz /= sl;
        float wx = sy * dz - sz * dy;
        float wy = sz * dx - sx * dz;
        float wz = sx * dy - sy * dx;

        float ax1 = ax + sx * t + wx * t;
        float ay1 = ay + sy * t + wy * t;
        float az1 = az + sz * t + wz * t;
        float ax2 = ax - sx * t + wx * t;
        float ay2 = ay - sy * t + wy * t;
        float az2 = az - sz * t + wz * t;
        float ax3 = ax - sx * t - wx * t;
        float ay3 = ay - sy * t - wy * t;
        float az3 = az - sz * t - wz * t;
        float ax4 = ax + sx * t - wx * t;
        float ay4 = ay + sy * t - wy * t;
        float az4 = az + sz * t - wz * t;

        float bx1 = bx + sx * t + wx * t;
        float by1 = by + sy * t + wy * t;
        float bz1 = bz + sz * t + wz * t;
        float bx2 = bx - sx * t + wx * t;
        float by2 = by - sy * t + wy * t;
        float bz2 = bz - sz * t + wz * t;
        float bx3 = bx - sx * t - wx * t;
        float by3 = by - sy * t - wy * t;
        float bz3 = bz - sz * t - wz * t;
        float bx4 = bx + sx * t - wx * t;
        float by4 = by + sy * t - wy * t;
        float bz4 = bz + sz * t - wz * t;

        addQuad(out, ax1, ay1, az1, ax2, ay2, az2, bx2, by2, bz2, bx1, by1, bz1, color, 0.95f, xray);
        addQuad(out, ax2, ay2, az2, ax3, ay3, az3, bx3, by3, bz3, bx2, by2, bz2, color, 0.72f, xray);
        addQuad(out, ax3, ay3, az3, ax4, ay4, az4, bx4, by4, bz4, bx3, by3, bz3, color, 0.95f, xray);
        addQuad(out, ax4, ay4, az4, ax1, ay1, az1, bx1, by1, bz1, bx4, by4, bz4, color, 0.72f, xray);
        addQuad(out, ax1, ay1, az1, bx1, by1, bz1, bx2, by2, bz2, ax2, ay2, az2, color, 0.8f, xray);
        addQuad(out, ax3, ay3, az3, bx3, by3, bz3, bx4, by4, bz4, ax4, ay4, az4, color, 0.8f, xray);
    }

    private static void addQuad(Mesh3D out, float x0, float y0, float z0,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                int color, float shade, boolean xray) {
        Quad3D q = out.next();
        q.setSolid(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, color, shade);
        q.xray = xray;
    }

    private static int mix(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
