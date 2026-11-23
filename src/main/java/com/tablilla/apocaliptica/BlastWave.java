package com.tablilla.apocaliptica;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/** Onda de choque que se expande: arranca arboles, hojas y cosas fragiles y chamusca el suelo, columna por columna. */
public final class BlastWave {
    private final ServerLevel level;
    private final BlockPos center;
    private final int radius;
    private final int n;
    private final float groundChance;
    private final float fireChance;
    private final RandomSource rnd;
    private final long[] wave;
    private int cursor = 0;

    public BlastWave(ServerLevel level, BlockPos center, int radius, float groundChance, float fireChance) {
        this.level = level;
        this.center = center;
        this.radius = radius;
        this.n = 2 * radius + 1;
        this.groundChance = groundChance;
        this.fireChance = fireChance;
        this.rnd = level.getRandom();
        long[] tmp = new long[(int) (3.2 * radius * radius) + 64];
        int cnt = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > radius * radius) continue;
                long idx = (long) (dx + radius) * n + (dz + radius);
                tmp[cnt++] = ((long) d2 << 32) | idx;
            }
        }
        wave = Arrays.copyOf(tmp, cnt);
        Arrays.sort(wave);
    }

    public boolean done() {
        return cursor >= wave.length;
    }

    /** Procesa todas las columnas que ya alcanzo la onda (hasta un maximo por tick). */
    public void sweep(double currentRadius) {
        long lim = (long) (currentRadius * currentRadius);
        int done = 0;
        while (cursor < wave.length && (wave[cursor] >>> 32) <= lim && done < 2500) {
            int idx = (int) (wave[cursor] & 0xFFFFFFFFL);
            int dz = idx % n - radius;
            int dx = idx / n - radius;
            cursor++;
            done++;
            Terrain.scorchColumn(level, center.getX() + dx, center.getZ() + dz, groundChance, fireChance, rnd);
        }
    }
}
