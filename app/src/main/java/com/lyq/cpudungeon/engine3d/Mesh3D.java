package com.lyq.cpudungeon.engine3d;

import java.util.ArrayList;

/** Static level geometry and the reusable per-frame enemy mesh. */
public final class Mesh3D {
    public Quad3D[] quads = new Quad3D[0];
    public int count;

    public void add(Quad3D quad) {
        ensure(count + 1);
        quads[count++] = quad;
    }

    /** Reuse pooled quads to avoid per-frame allocation. */
    public Quad3D next() {
        ensure(count + 1);
        if (quads[count] == null) {
            quads[count] = new Quad3D();
        }
        return quads[count++];
    }

    public void beginFrame() {
        count = 0;
    }

    public void clear() {
        count = 0;
    }

    private void ensure(int size) {
        if (size > quads.length) {
            Quad3D[] bigger = new Quad3D[Math.max(64, quads.length * 2)];
            System.arraycopy(quads, 0, bigger, 0, quads.length);
            quads = bigger;
        }
    }

    public ArrayList<Quad3D> toList() {
        ArrayList<Quad3D> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(quads[i]);
        }
        return list;
    }
}
