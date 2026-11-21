package com.tablilla.apocaliptica;

import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** La Barca Celeste (Mothership): una nave colosal, un rayo devastador, un crater enorme y una onda de fuego. */
public class MothershipStrike extends BaseStrike {
    public static final int ARRIVE = 110;
    public static final int CHARGE = 250;
    public static final int FIRE = 330;
    public static final int FIRE_END = 480;
    public static final int DEPART = 530;
    public static final int DURATION = 780;

    private static final BowlCrater.Liner LINER = (d, r) -> {
        if (d < 0.25) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (d < 0.50) return r.nextFloat() < 0.6F ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.CRYING_OBSIDIAN.defaultBlockState();
        return r.nextFloat() < 0.5F ? Blocks.GLASS.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    };

    private final int waveR;
    private final int waveN;
    private BowlCrater crater;
    private int perTick = 1;
    private long[] wave;
    private int waveCursor = 0;

    public MothershipStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.waveR = Math.max(40, Math.min(180, Math.round(120 * scale)));
        this.waveN = 2 * waveR + 1;
    }

    @Override
    protected Laser laser() {
        return Laser.MOTHERSHIP;
    }

    @Override
    protected int duration() {
        return DURATION;
    }

    @Override
    protected void serverTick() {
        if (!StrikeManager.terrainDamage) return;
        if (t == FIRE) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 70 * scale, 500, 200F, 10, 4.0);
            int r = Math.min(150, Math.round(80 * scale));
            crater = new BowlCrater(level, center, r, Math.round(40 * scale), LINER);
            perTick = (int) Math.ceil(crater.total() / 180.0);
            buildWave();
        }
        if (crater != null && !crater.done() && t > FIRE + 2) crater.step(perTick);
        if (wave != null && t > FIRE) sweepWave();
        if (t > FIRE && t <= FIRE + 120 && t % 6 == 0) {
            Terrain.hurtArea(level, cx(), cy(), cz(), waveRadius(), 300, 25F, 6, 1.5);
        }
    }

    private double waveRadius() {
        double k = Math.min(1.0, (t - FIRE) / 90.0);
        return waveR * (1.0 - (1.0 - k) * (1.0 - k));
    }

    private void buildWave() {
        int r = waveR;
        long[] tmp = new long[(int) (3.2 * r * r) + 64];
        int cnt = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r * r) continue;
                long idx = (long) (dx + r) * waveN + (dz + r);
                tmp[cnt++] = ((long) d2 << 32) | idx;
            }
        }
        wave = Arrays.copyOf(tmp, cnt);
        Arrays.sort(wave);
    }

    private void sweepWave() {
        double rad = waveRadius();
        long lim = (long) (rad * rad);
        int done = 0;
        while (waveCursor < wave.length && (wave[waveCursor] >>> 32) <= lim && done < 2500) {
            int idx = (int) (wave[waveCursor] & 0xFFFFFFFFL);
            int dz = idx % waveN - waveR;
            int dx = idx / waveN - waveR;
            waveCursor++;
            done++;
            sweepColumn(center.getX() + dx, center.getZ() + dz);
        }
    }

    private void sweepColumn(int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        int top = Terrain.surfaceTop(level, x, z);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = top + 22; y >= top - 14; y--) {
            p.set(x, y, z);
            BlockState st = level.getBlockState(p);
            if (flimsy(st, p)) Terrain.clear(level, p);
        }
        // suelo chamuscado y algo de fuego
        p.set(x, top, z);
        BlockState ground = level.getBlockState(p);
        if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.PODZOL) || ground.is(Blocks.MYCELIUM)) {
            if (rnd.nextFloat() < 0.5F) level.setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), Terrain.FLAGS);
        }
        if (rnd.nextFloat() < 0.015F) {
            p.set(x, top + 1, z);
            if (level.getBlockState(p).isAir()) level.setBlock(p, Blocks.FIRE.defaultBlockState(), Terrain.FLAGS);
        }
    }

    private boolean flimsy(BlockState st, BlockPos p) {
        if (st.isAir()) return false;
        if (st.getDestroySpeed(level, p) < 0) return false;
        if (!st.getFluidState().isEmpty()) return false;
        return st.is(BlockTags.LEAVES) || st.is(BlockTags.LOGS) || st.is(BlockTags.PLANKS)
                || st.is(BlockTags.WOODEN_STAIRS) || st.is(BlockTags.WOODEN_SLABS) || st.is(BlockTags.WOODEN_DOORS)
                || st.is(BlockTags.WOODEN_FENCES) || st.is(BlockTags.WOOL) || st.is(BlockTags.BEDS)
                || st.getCollisionShape(level, p).isEmpty();
    }
}
