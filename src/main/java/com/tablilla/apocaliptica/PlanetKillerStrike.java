package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;

/**
 * El Fin de los Mundos: siete obeliscos de piedra antigua emergen en el horizonte y descargan rayos sobre un anillo en el
 * cielo; el rayo del anillo abre un crater de unos 480 bloques de ancho y arrasa todo alrededor.
 */
public class PlanetKillerStrike extends BaseStrike {
    public static final int RISE = 20;
    public static final int LIGHTNING = 260;
    public static final int BEAM = 340;
    public static final int BEAM_END = 700;
    public static final int DURATION = 1200;

    private static final BowlCrater.Liner LINER = (d, r) -> {
        float f = r.nextFloat();
        if (d < 0.20) return f < 0.35F ? Blocks.LAVA.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
        if (d < 0.55) return f < 0.5F ? Blocks.OBSIDIAN.defaultBlockState() : (f < 0.75F ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.GLASS.defaultBlockState());
        if (f < 0.003F) return Blocks.DIAMOND_BLOCK.defaultBlockState();
        return f < 0.5F ? Blocks.BLACKSTONE.defaultBlockState() : (f < 0.85F ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
    };

    private BowlCrater crater;
    private BlastWave wave;
    private int perTick = 1;
    private final int craterR;
    private final int waveR;

    public PlanetKillerStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.craterR = Mth.clamp(Math.round(240 * scale), 60, 320);
        this.waveR = Mth.clamp(Math.round(340 * scale), 100, 450);
    }

    @Override
    protected Laser laser() {
        return Laser.PLANETKILLER;
    }

    @Override
    protected int duration() {
        return DURATION;
    }

    @Override
    protected void serverTick() {
        if (!StrikeManager.terrainDamage) return;
        if (t == BEAM) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 300 * scale, 2000, 5000F, 25, 10.0);
            int dep = Mth.clamp(Math.round(40 * scale), 14, 90);
            Terrain.raiseRim(level, center.getX(), center.getZ(), craterR, 0.12, rnd);
            crater = new BowlCrater(level, center, craterR, dep, LINER).walls(LINER);
            perTick = (int) Math.ceil(crater.total() / 800.0);
            wave = new BlastWave(level, center, waveR, 0.85F, 0.04F).withPush(6.5, waveR * 0.9).flatten(center.getY() + 12, 0.55);
            Terrain.scatterRuin(level, center.getX(), center.getZ(), craterR, (int) (craterR * 1.7), 16.0, 0.0006, true, rnd);
        }
        if (crater != null && !crater.done() && t > BEAM + 2) crater.step(perTick);
        if (wave != null && !wave.done()) {
            double k = Math.min(1.0, (t - BEAM) / 260.0);
            wave.sweep(waveR * (1.0 - (1.0 - k) * (1.0 - k)));
        }
        if (t > BEAM && t <= BEAM_END && t % 10 == 0) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 60 * scale, 1000, 40F, 15, 2.0);
        }
    }
}
