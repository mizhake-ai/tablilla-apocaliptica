package com.tablilla.apocaliptica;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Corona de agujas de obsidiana y cristal: nacen del borde quemado del crater y se curvan hacia adentro y hacia arriba
 * hasta casi tocarse sobre el centro del ojo del impacto.
 */
public final class SpikeCrown {
    private static final class Spike {
        double angle;
        double rBase;
        double rTip;
        double height;
        double w0;
        double done = 0.0;
    }

    private final ServerLevel level;
    private final BlockPos center;
    private final RandomSource rnd;
    private final List<Spike> spikes = new ArrayList<>();
    private double progress = 0.0;

    public SpikeCrown(ServerLevel level, BlockPos center, int craterRadius, int count, double heightMin, double heightMax, double thick) {
        this.level = level;
        this.center = center;
        this.rnd = level.getRandom();
        for (int i = 0; i < count; i++) {
            Spike s = new Spike();
            s.angle = Math.PI * 2 * (i + rnd.nextDouble() * 0.5) / count;
            s.rBase = craterRadius * 1.05;
            s.rTip = craterRadius * (0.05 + rnd.nextDouble() * 0.22);
            s.height = heightMin + rnd.nextDouble() * (heightMax - heightMin);
            s.w0 = (2.6 + rnd.nextDouble() * 1.4) * thick;
            spikes.add(s);
        }
    }

    public boolean done() {
        return progress >= 1.0;
    }

    /** Hace crecer todas las agujas un poco mas (du = avance de 0 a 1). */
    public void grow(double du) {
        double next = Math.min(1.0, progress + du);
        for (Spike s : spikes) {
            double len = Math.abs(s.rBase - s.rTip) + s.height;
            double stepU = 0.7 / Math.max(10.0, len);
            for (double u = s.done; u < next; u += stepU) place(s, u);
            s.done = next;
        }
        progress = next;
    }

    private void place(Spike s, double u) {
        double rad = s.rBase + (s.rTip - s.rBase) * Math.pow(u, 0.85);
        int px = center.getX() + (int) Math.round(Math.cos(s.angle) * rad);
        int pz = center.getZ() + (int) Math.round(Math.sin(s.angle) * rad);
        if (!level.hasChunk(px >> 4, pz >> 4)) return;
        double baseY = Terrain.surfaceTop(level, center.getX() + (int) Math.round(Math.cos(s.angle) * s.rBase),
                center.getZ() + (int) Math.round(Math.sin(s.angle) * s.rBase)) + 1;
        double y = baseY + s.height * Math.pow(Math.sin(u * Math.PI / 2.0), 1.15);
        double w = Math.max(0.6, s.w0 * (1.0 - 0.82 * Math.pow(u, 0.9)));
        int wc = (int) Math.ceil(w);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -wc; dx <= wc; dx++) {
            for (int dy = -wc; dy <= wc; dy++) {
                for (int dz = -wc; dz <= wc; dz++) {
                    if (dx * dx + dy * dy + dz * dz > w * w) continue;
                    p.set(px + dx, (int) Math.round(y) + dy, pz + dz);
                    BlockState cur = level.getBlockState(p);
                    if (!cur.isAir() && !Terrain.breakable(level, p, cur)) continue;
                    if (!cur.isAir() && cur.getDestroySpeed(level, p) > 20.0F) continue;
                    level.setBlock(p, pick(u), Terrain.FLAGS);
                }
            }
        }
    }

    private BlockState pick(double u) {
        float f = rnd.nextFloat();
        if (f < 0.50F) return Blocks.OBSIDIAN.defaultBlockState();
        if (f < 0.80F) return Blocks.GLASS.defaultBlockState();
        if (f < 0.92F) return Blocks.CRYING_OBSIDIAN.defaultBlockState();
        return Blocks.BLACKSTONE.defaultBlockState();
    }
}
