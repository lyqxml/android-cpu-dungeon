package com.lyq.cpudungeon.engine3d;

import java.util.ArrayList;

/** Static level geometry, built once per level and re-rendered every frame. */
public final class Mesh3D {
    public Quad3D[] quads = new Quad3D[0];
    public int count;

    public void add(Quad3D quad) {
        if (count == quads.length) {
            Quad3D[] bigger = new Quad3D[Math.max(64, quads.length * 2)];
            System.arraycopy(quads, 0, bigger, 0, quads.length);
            quads = bigger;
        }
        quads[count++] = quad;
    }

    public void clear() {
        count = 0;
    }

    public ArrayList<Quad3D> toList() {
        ArrayList<Quad3D> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(quads[i]);
        }
        return list;
    }
}
