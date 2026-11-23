package com.tablilla.apocaliptica;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Excava un crater en forma de cuenco, columna por columna desde el centro, y reviste el fondo (y opcionalmente las paredes). */
public final class BowlCrater {
    /** Elige el bloque del fondo segun la distancia al centro (0 = centro, 1 = borde). Null = no tocar. */
    public interface Liner {
        BlockState pick(double d, RandomSource rnd);
    }

    private static final int[][] NEIGHBORS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final ServerLevel level;
    private final BlockPos center;
    private final int radius;
    private final int depth;
    private final Liner liner;
    private Liner wallLiner;
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

    /** Activa el forrado de las paredes empinadas con este revestimiento. */
    public BowlCrater walls(Liner w) {
        this.wallLiner = w;
        return this;
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

    /** Profundidad del cuenco en esa columna, o -1 si queda fuera del crater. */
    private int depthAt(int dx, int dz) {
        double d = Math.sqrt((double) (dx * dx + dz * dz)) / radius;
        if (d >= 1.0) return -1;
        return (int) Math.round(depth * Math.sqrt(Math.max(0.0, 1.0 - d * d)));
    }

    private void carve(int dx, int dz) {
        int cx = center.getX() + dx;
        int cz = center.getZ() + dz;
        if (!level.hasChunk(cx >> 4, cz >> 4)) return;

        double d = Math.sqrt((double) (dx * dx + dz * dz)) / radius;
        int minY = level.getMinBuildHeight() + 1;
        int dep = Math.max(0, depthAt(dx, dz));
        int floorY = Math.max(minY, center.getY() - dep);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        int topY = Math.min(level.getMaxBuildHeight() - 1, Math.max(center.getY(), surface) + 24);

        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = topY; y >= floorY; y--) {
            p.set(cx, y, cz);
            BlockState st = level.getBlockState(p);
            if (!Terrain.breakable(level, p, st)) continue;
            Terrain.clear(level, p);
        }

        if (wallLiner != null) lineWalls(dx, dz, floorY, minY, p);

        if (d > 0.9 || liner == null) return;
        p.set(cx, floorY - 1, cz);
        BlockState below = level.getBlockState(p);
        if (!Terrain.breakable(level, p, below)) return;
        BlockState fused = liner.pick(d, rnd);
        if (fused != null) level.setBlock(p, fused, Terrain.FLAGS);
    }

    /** Forra la cara vertical que queda entre esta columna y las vecinas mas altas. */
    private void lineWalls(int dx, int dz, int floorY, int minY, BlockPos.MutableBlockPos p) {
        for (int[] o : NEIGHBORS) {
            int ndx = dx + o[0];
            int ndz = dz + o[1];
            int nx = center.getX() + ndx;
            int nz = center.getZ() + ndz;
            if (!level.hasChunk(nx >> 4, nz >> 4)) continue;
            int nd = depthAt(ndx, ndz);
            int nFloor;
            if (nd >= 0) {
                nFloor = Math.max(minY, center.getY() - nd);
            } else {
                nFloor = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, nx, nz);
            }
            if (nFloor <= floorY) continue;
            double nDist = Math.min(1.0, Math.sqrt((double) (ndx * ndx + ndz * ndz)) / radius);
            int yTop = Math.min(nFloor - 1, floorY + 90);
            for (int y = floorY; y <= yTop; y++) {
                p.set(nx, y, nz);
                BlockState st = level.getBlockState(p);
                if (!Terrain.breakable(level, p, st)) continue;
                BlockState w = wallLiner.pick(nDist, rnd);
                if (w != null) level.setBlock(p, w, Terrain.FLAGS);
            }
        }
    }
}
