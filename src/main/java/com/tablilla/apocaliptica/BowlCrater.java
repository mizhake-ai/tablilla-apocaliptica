package com.tablilla.apocaliptica;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Excava un crater en forma de cuenco, columna por columna desde el centro, y reviste el fondo. */
public final class BowlCrater {
    /** Elige el bloque del fondo segun la distancia al centro (0 = centro, 1 = borde). Null = no tocar. */
    public interface Liner {
        BlockState pick(double d, RandomSource rnd);
    }

    private final ServerLevel level;
    private final BlockPos center;
    private final int radius;
    private final int depth;
    private final Liner liner;
    private final RandomSource rnd;
    private final List<int[]> columns = new ArrayList<>();
    private int next = 0;

    public BowlCrater(ServerLevel level, BlockPos center, int radius, int depth, Liner liner) {
        this.level = level;
        this.center = center;
        this.radius = Math.max(4, radius);
        this.depth = Math.max(2, depth);
        this.liner = liner;
        this.rnd = level.getRandom();
        int r = this.radius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= r * r) columns.add(new int[] {dx, dz});
            }
        }
        columns.sort(Comparator.comparingInt((int[] c) -> c[0] * c[0] + c[1] * c[1]));
    }

    public int total() {
        return columns.size();
    }

    public boolean done() {
        return next >= columns.size();
    }

    public void step(int count) {
        for (int k = 0; k < count && next < columns.size(); k++) {
            int[] c = columns.get(next++);
            carve(c[0], c[1]);
        }
    }

    private void carve(int dx, int dz) {
        int cx = center.getX() + dx;
        int cz = center.getZ() + dz;
        if (!level.hasChunk(cx >> 4, cz >> 4)) return;

        double d = Math.sqrt((double) (dx * dx + dz * dz)) / radius;
        int dep = (int) Math.round(depth * Math.sqrt(Math.max(0.0, 1.0 - d * d)));
        int floorY = center.getY() - dep;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        int topY = Math.min(level.getMaxBuildHeight() - 1, Math.max(center.getY(), surface) + 24);

        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = topY; y >= floorY; y--) {
            p.set(cx, y, cz);
            BlockState st = level.getBlockState(p);
            if (!Terrain.breakable(level, p, st)) continue;
            Terrain.clear(level, p);
        }

        if (d > 0.9 || liner == null) return;
        p.set(cx, floorY - 1, cz);
        BlockState below = level.getBlockState(p);
        if (!Terrain.breakable(level, p, below)) return;
        BlockState fused = liner.pick(d, rnd);
        if (fused != null) level.setBlock(p, fused, Terrain.FLAGS);
    }
}
