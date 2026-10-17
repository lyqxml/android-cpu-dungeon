package com.lyq.cpudungeon.engine3d;

/** Converts the grid + height field into world-space quads. */
public final class MeshBuilder3D {
    private static final int TEX_FLOOR = 4;
    private static final int TEX_CEIL = 5;
    private static final float UV_FLOOR = 0.5f;
    private static final float UV_WALL = 0.6f;

    private MeshBuilder3D() {
    }

    public static Mesh3D build(Level3D level) {
        Mesh3D mesh = new Mesh3D();
        for (int cy = 0; cy < level.h; cy++) {
            for (int cx = 0; cx < level.w; cx++) {
                if (level.solid(cx, cy)) {
                    continue;
                }
                float h = level.floorZ[cy * level.w + cx];
                float ceil = Level3D.CEIL_Z;
                addFloor(mesh, cx, cy, h);
                addCeiling(mesh, cx, cy, ceil);

                // west
                addEdge(mesh, level, cx, cy, cx - 1, cy, h,
                        cx, cy, cx, cy + 1, 0.85f);
                // east
                addEdge(mesh, level, cx, cy, cx + 1, cy, h,
                        cx + 1, cy, cx + 1, cy + 1, 0.85f);
                // north
                addEdge(mesh, level, cx, cy, cx, cy - 1, h,
                        cx, cy, cx + 1, cy, 0.72f);
                // south
                addEdge(mesh, level, cx, cy, cx, cy + 1, h,
                        cx, cy + 1, cx + 1, cy + 1, 0.72f);
            }
        }
        return mesh;
    }

    private static void addFloor(Mesh3D mesh, int cx, int cy, float z) {
        Quad3D q = new Quad3D();
        float u0 = cx * UV_FLOOR;
        float v0 = cy * UV_FLOOR;
        q.set(cx, cy, z, u0, v0,
                cx + 1, cy, z, u0 + UV_FLOOR, v0,
                cx + 1, cy + 1, z, u0 + UV_FLOOR, v0 + UV_FLOOR,
                cx, cy + 1, z, u0, v0 + UV_FLOOR,
                TEX_FLOOR, 1.0f);
        mesh.add(q);
    }

    private static void addCeiling(Mesh3D mesh, int cx, int cy, float z) {
        Quad3D q = new Quad3D();
        float u0 = cx * UV_FLOOR;
        float v0 = cy * UV_FLOOR;
        q.set(cx, cy, z, u0, v0,
                cx, cy + 1, z, u0, v0 + UV_FLOOR,
                cx + 1, cy + 1, z, u0 + UV_FLOOR, v0 + UV_FLOOR,
                cx + 1, cy, z, u0 + UV_FLOOR, v0,
                TEX_CEIL, 0.95f);
        mesh.add(q);
    }

    /**
     * Emits the vertical surface on the shared edge. Solid neighbours become
     * full-height walls; walkable neighbours with a higher floor become the
     * visible stair riser.
     */
    private static void addEdge(Mesh3D mesh, Level3D level, int cx, int cy, int nx, int ny,
                                float h, int ax, int ay, int bx, int by, float shade) {
        boolean solid = level.solid(nx, ny);
        float otherZ;
        int tex;
        if (solid) {
            otherZ = Level3D.CEIL_Z;
            tex = level.wallTexAt(nx, ny);
        } else {
            float nh = level.floorZ[ny * level.w + nx];
            if (nh <= h + 0.001f) {
                return;
            }
            otherZ = nh;
            tex = level.wallTexAt(cx, cy);
            shade *= 0.88f;
        }
        Quad3D q = new Quad3D();
        if (ax == bx) {
            // wall runs along Y
            q.set(ax, ay, h, ay * UV_WALL, h * UV_WALL,
                    bx, by, h, by * UV_WALL, h * UV_WALL,
                    bx, by, otherZ, by * UV_WALL, otherZ * UV_WALL,
                    ax, ay, otherZ, ay * UV_WALL, otherZ * UV_WALL,
                    tex, shade);
        } else {
            // wall runs along X
            q.set(ax, ay, h, ax * UV_WALL, h * UV_WALL,
                    bx, by, h, bx * UV_WALL, h * UV_WALL,
                    bx, by, otherZ, bx * UV_WALL, otherZ * UV_WALL,
                    ax, ay, otherZ, ax * UV_WALL, otherZ * UV_WALL,
                    tex, shade);
        }
        mesh.add(q);
    }
}
