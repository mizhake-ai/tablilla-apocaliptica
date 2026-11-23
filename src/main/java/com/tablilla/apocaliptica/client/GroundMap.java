package com.tablilla.apocaliptica.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/** Mapa de alturas del terreno alrededor del impacto (relativo al punto de impacto). Sirve para pegar anillos y lineas al relieve. */
public final class GroundMap {
    private final int n;
    private final float step;
    private final float half;
    private final float[] h;

    public GroundMap(ClientLevel level, double cx, double cy, double cz, float half, float step) {
        this.half = half;
        this.step = step;
        this.n = (int) Math.ceil(2.0F * half / step) + 1;
        this.h = new float[n * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int wx = Mth.floor(cx - half + i * step);
                int wz = Mth.floor(cz - half + j * step);
                float v = Float.NaN;
                if (level.hasChunk(wx >> 4, wz >> 4)) {
                    v = (float) (level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - cy);
                }
                h[i * n + j] = v;
            }
        }
        float last = 0.0F;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float v = h[i * n + j];
                if (Float.isNaN(v)) {
                    h[i * n + j] = last;
                } else {
                    last = v;
                }
            }
        }
    }

    /** Altura relativa de la superficie en el punto local (lx, lz). */
    public float at(double lx, double lz) {
        double fx = (lx + half) / step;
        double fz = (lz + half) / step;
        int i0 = Mth.clamp((int) Math.floor(fx), 0, n - 1);
        int j0 = Mth.clamp((int) Math.floor(fz), 0, n - 1);
        int i1 = Math.min(i0 + 1, n - 1);
        int j1 = Math.min(j0 + 1, n - 1);
        float tx = Mth.clamp((float) (fx - i0), 0.0F, 1.0F);
        float tz = Mth.clamp((float) (fz - j0), 0.0F, 1.0F);
        float a = h[i0 * n + j0] + (h[i1 * n + j0] - h[i0 * n + j0]) * tx;
        float b = h[i0 * n + j1] + (h[i1 * n + j1] - h[i0 * n + j1]) * tx;
        return a + (b - a) * tz;
    }
}
