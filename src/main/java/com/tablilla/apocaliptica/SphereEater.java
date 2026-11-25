package com.tablilla.apocaliptica;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Se come el terreno en una esfera que crece desde el centro hacia afuera (para los agujeros negros pequenos). */
public final class SphereEater {
    private final ServerLevel level;
    private final BlockPos center;
    private final int r;
    private final int n;
    private final long[] sorted;
    private int cursor = 0;
    private int lineY;
    private boolean lineInit = false;

    public SphereEater(ServerLevel level, BlockPos center, int radius) {
        this.level = level;
        this.center = center;
        this.r = Math.max(4, Math.min(60, radius));
        this.n = 2 * r + 1;
        long[] tmp = new long[(int) (4.3 * r * r * r) + 64];
        int cnt = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 > r * r) continue;
                    long idx = ((long) (dx + r) * n + (dy + r)) * n + (dz + r);
                    tmp[cnt++] = ((long) d2 << 32) | idx;
                }
            }
        }
        sorted = Arrays.copyOf(tmp, cnt);
        Arrays.sort(sorted);
    }

    public boolean done() {
        return cursor >= sorted.length;
    }

    /** Se come todo lo que cae dentro del radio actual (con un maximo por tick). */
    public void eat(double currentRadius) {
        long lim = (long) (currentRadius * currentRadius);
        int done = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        while (cursor < sorted.length && (sorted[cursor] >>> 32) <= lim && done < 7000) {
            int idx = (int) (sorted[cursor] & 0xFFFFFFFFL);
            int dz = idx % n - r;
            int dy = (idx / n) % n - r;
            int dx = idx / (n * n) - r;
            cursor++;
            done++;
            p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
            if (!level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
            BlockState st = level.getBlockState(p);
            if (Terrain.breakable(level, p, st)) Terrain.clear(level, p);
        }
    }

    /** Forra la superficie cortada con magma, obsidiana y piedra negra: queda incandescente (como lo que corta una bola de teletransporte). */
    public void line(int slicesPerTick) {
        if (!lineInit) {
            lineInit = true;
            lineY = -r - 2;
        }
        double in2 = (double) r * r;
        double out2 = (r + 1.8) * (r + 1.8);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        net.minecraft.util.RandomSource rnd = level.getRandom();
        for (int k = 0; k < slicesPerTick && lineY <= r + 2; k++) {
            int dy = lineY++;
            for (int dx = -r - 2; dx <= r + 2; dx++) {
                for (int dz = -r - 2; dz <= r + 2; dz++) {
                    double d2 = dx * dx + dy * dy + dz * dz;
                    if (d2 <= in2 || d2 > out2) continue;
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
                    BlockState st = level.getBlockState(p);
                    if (!Terrain.breakable(level, p, st) || !st.getFluidState().isEmpty()) continue;
                    if (!(level.getBlockState(p.above()).isAir() || level.getBlockState(p.below()).isAir()
                            || level.getBlockState(p.north()).isAir() || level.getBlockState(p.south()).isAir()
                            || level.getBlockState(p.east()).isAir() || level.getBlockState(p.west()).isAir())) continue;
                    float f = rnd.nextFloat();
                    net.minecraft.world.level.block.Block b = f < 0.55F ? net.minecraft.world.level.block.Blocks.MAGMA_BLOCK
                            : (f < 0.78F ? net.minecraft.world.level.block.Blocks.OBSIDIAN
                            : (f < 0.92F ? net.minecraft.world.level.block.Blocks.BLACKSTONE : net.minecraft.world.level.block.Blocks.GLASS));
                    level.setBlock(p, b.defaultBlockState(), Terrain.FLAGS);
                }
            }
        }
    }

    public boolean lineDone() {
        return lineInit && lineY > r + 2;
    }
}
