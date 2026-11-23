package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** La Caida (Downfall): una nave colosal en llamas cae del cielo, se estrella y estalla, dejando un crater gigante y restos ardiendo. */
public class DownfallStrike extends BaseStrike {
    public static final int FALL_START = 20;
    public static final int CRASH = 300;
    public static final int EXPLODE = 430;
    public static final int DURATION = 580;

    private static final BowlCrater.Liner LINER1 = (d, r) -> {
        if (d < 0.30) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (d < 0.65) return r.nextFloat() < 0.5F ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        return r.nextFloat() < 0.4F ? Blocks.NETHERRACK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    };

    private static final BowlCrater.Liner LINER2 = (d, r) -> {
        if (d < 0.22) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (d < 0.55) return r.nextFloat() < 0.6F ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.CRYING_OBSIDIAN.defaultBlockState();
        return r.nextFloat() < 0.5F ? Blocks.GLASS.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    };

    private static final Block[] WRECK = {Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.DEEPSLATE_TILES,
            Blocks.IRON_BLOCK, Blocks.POLISHED_DEEPSLATE, Blocks.GRAY_CONCRETE};

    private BowlCrater crater1;
    private BowlCrater crater2;
    private BlastWave wave1;
    private BlastWave wave2;
    private int perTick1 = 1;
    private int perTick2 = 1;
    private final int waveR1;
    private final int waveR2;

    public DownfallStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.waveR1 = Mth.clamp(Math.round(130 * scale), 40, 200);
        this.waveR2 = Mth.clamp(Math.round(190 * scale), 60, 240);
    }

    @Override
    protected Laser laser() {
        return Laser.DOWNFALL;
    }

    @Override
    protected int duration() {
        return DURATION;
    }

    private static double wave(double since, double len, int radius) {
        double k = Math.min(1.0, since / len);
        return radius * (1.0 - (1.0 - k) * (1.0 - k));
    }

    @Override
    protected void serverTick() {
        if (!StrikeManager.terrainDamage) return;
        if (t == CRASH) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 80 * scale, 500, 200F, 10, 4.0);
            crater1 = new BowlCrater(level, center, Mth.clamp(Math.round(62 * scale), 12, 120), Mth.clamp(Math.round(30 * scale), 8, 80), LINER1);
            perTick1 = (int) Math.ceil(crater1.total() / 140.0);
            wave1 = new BlastWave(level, center, waveR1, 0.5F, 0.015F);
        }
        if (crater1 != null && !crater1.done() && t > CRASH + 2) crater1.step(perTick1);
        if (wave1 != null && t > CRASH && !wave1.done()) wave1.sweep(wave(t - CRASH, 80, waveR1));

        if (t == EXPLODE) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 120 * scale, 500, 250F, 12, 5.0);
            crater2 = new BowlCrater(level, center, Mth.clamp(Math.round(96 * scale), 16, 150), Mth.clamp(Math.round(48 * scale), 10, 110), LINER2);
            perTick2 = (int) Math.ceil(crater2.total() / 120.0);
            wave2 = new BlastWave(level, center, waveR2, 0.5F, 0.02F);
        }
        if (crater2 != null && !crater2.done() && t > EXPLODE + 2) crater2.step(perTick2);
        if (wave2 != null && t > EXPLODE && !wave2.done()) wave2.sweep(wave(t - EXPLODE, 90, waveR2));
        if (t > CRASH && t <= EXPLODE + 100 && t % 8 == 0) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 40 * scale, 300, 8F, 6, 0.8);
        }
        if (t == EXPLODE + 140) scatterWreck();
    }

    /** Restos de la nave esparcidos alrededor del crater: pilas de bloques oscuros con fuego. */
    private void scatterWreck() {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 90; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = (45.0 + Math.sqrt(rnd.nextDouble()) * 85.0) * scale;
            int x = center.getX() + (int) Math.round(Math.cos(a) * d);
            int z = center.getZ() + (int) Math.round(Math.sin(a) * d);
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int top = Terrain.surfaceTop(level, x, z);
            int h = 1 + rnd.nextInt(4);
            int w = rnd.nextInt(3);
            Block b = WRECK[rnd.nextInt(WRECK.length)];
            BlockState st = b.defaultBlockState();
            for (int dx = 0; dx <= w; dx++) {
                for (int dz = 0; dz <= w; dz++) {
                    int hh = Math.max(1, h - rnd.nextInt(2));
                    for (int y = 1; y <= hh; y++) {
                        p.set(x + dx, top + y, z + dz);
                        if (level.getBlockState(p).isAir() || Terrain.flimsy(level, level.getBlockState(p), p)) {
                            level.setBlock(p, st, Terrain.FLAGS);
                        }
                    }
                    if (rnd.nextFloat() < 0.35F) {
                        p.set(x + dx, top + hh + 1, z + dz);
                        if (level.getBlockState(p).isAir()) level.setBlock(p, Blocks.FIRE.defaultBlockState(), Terrain.FLAGS);
                    }
                }
            }
        }
    }
}
