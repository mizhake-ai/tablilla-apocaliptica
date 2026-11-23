package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;

/** Lanza de Ra (Plasma): una lanza de luz solar que se clava y deja un pozo muy profundo de magma, obsidiana y vidrio. */
public class PlasmaStrike extends BaseStrike {
    public static final int CHARGE = 70;
    public static final int BEAM_END = 200;
    public static final int DURATION = 270;

    private static final BowlCrater.Liner LINER = (d, r) -> {
        if (d < 0.30) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (d < 0.65) return Blocks.OBSIDIAN.defaultBlockState();
        return r.nextFloat() < 0.55F ? Blocks.GLASS.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    };

    /** Paredes del pozo: obsidiana, piedra negra, vidrio y vetas de magma. */
    private static final BowlCrater.Liner WALLS = (d, r) -> {
        float f = r.nextFloat();
        if (f < 0.45F) return Blocks.OBSIDIAN.defaultBlockState();
        if (f < 0.70F) return Blocks.BLACKSTONE.defaultBlockState();
        if (f < 0.86F) return Blocks.GLASS.defaultBlockState();
        return Blocks.MAGMA_BLOCK.defaultBlockState();
    };

    private BowlCrater crater;
    private BlastWave wave;
    private int perTick = 1;
    private final int waveR;

    public PlasmaStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.waveR = Mth.clamp(Math.round(95 * scale), 30, 200);
    }

    @Override
    protected Laser laser() {
        return Laser.PLASMA;
    }

    @Override
    protected int duration() {
        return DURATION;
    }

    private double waveRadius() {
        double k = Math.min(1.0, (t - CHARGE) / 60.0);
        return waveR * (1.0 - (1.0 - k) * (1.0 - k));
    }

    @Override
    protected void serverTick() {
        if (!StrikeManager.terrainDamage) return;
        if (t == CHARGE) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 60 * scale, 250, 60F, 8, 3.0);
            int r = Mth.clamp(Math.round(40 * scale), 12, 110);
            int dep = Mth.clamp(Math.round(70 * scale), 16, 170);
            crater = new BowlCrater(level, center, r, dep, LINER).walls(WALLS);
            perTick = (int) Math.ceil(crater.total() / 120.0);
            wave = new BlastWave(level, center, waveR, 0.45F, 0.012F);
        }
        if (t > CHARGE && t <= BEAM_END && t % 5 == 0) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 16 * scale, 300, 10F, 6, 0.8);
        }
        if (crater != null && !crater.done() && t > CHARGE + 2) crater.step(perTick);
        if (wave != null && t > CHARGE && !wave.done()) wave.sweep(waveRadius());
    }
}
