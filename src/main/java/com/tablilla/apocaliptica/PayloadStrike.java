package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fuego del Cielo: una roca inmensa e incandescente baja lenta desde la orbita. A unos 150 bloques empieza a empujar y
 * arrasar el suelo por la presion; al chocar abre un crater casi hasta la bedrock y una onda de extincion barre kilometros,
 * dejando solo tierra quemada, montanas derribadas, obsidiana, cristal, magma, lava y fuego.
 */
public class PayloadStrike extends BaseStrike {
    public static final int START = 40;
    public static final int PRESSURE = 368;
    public static final int IMPACT = 420;
    public static final int DURATION = 1120;
    public static final double D0 = 1500.0;

    private static final BowlCrater.Liner LINER = (d, r) -> {
        float f = r.nextFloat();
        if (d < 0.25) return f < 0.4F ? Blocks.LAVA.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
        if (d < 0.60) return f < 0.5F ? Blocks.OBSIDIAN.defaultBlockState() : (f < 0.75F ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.GLASS.defaultBlockState());
        if (f < 0.004F) return Blocks.DIAMOND_BLOCK.defaultBlockState();
        if (f < 0.07F) return Blocks.IRON_BLOCK.defaultBlockState();
        if (f < 0.12F) return Blocks.COAL_BLOCK.defaultBlockState();
        return f < 0.55F ? Blocks.BLACKSTONE.defaultBlockState() : (f < 0.85F ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
    };

    /** Distancia de la roca al impacto en el tick t (igual en el cliente). */
    public static double distance(double t) {
        double tau = Math.max(0.0, Math.min(1.0, (t - START) / (double) (IMPACT - START)));
        return D0 * Math.pow(1.0 - tau, 1.15);
    }

    private BowlCrater crater;
    private BlastWave pre;
    private BlastWave wave;
    private int perTick = 1;
    private final int craterR;
    private final int waveR;
    private final int preR;

    public PayloadStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.craterR = Mth.clamp(Math.round(110 * scale), 30, 160);
        this.waveR = Mth.clamp(Math.round(450 * scale), 150, 700);
        this.preR = Mth.clamp(Math.round(150 * scale), 60, 260);
    }

    @Override
    protected Laser laser() {
        return Laser.PAYLOAD;
    }

    @Override
    protected int duration() {
        return DURATION;
    }

    @Override
    protected void serverTick() {
        if (!StrikeManager.terrainDamage) return;

        // la presion de la roca ya empuja y barre el suelo cuando esta a ~150 bloques
        if (t == PRESSURE) pre = new BlastWave(level, center, preR, 0.5F, 0.02F).withPush(2.2, preR * 1.2);
        if (pre != null && !pre.done() && t < IMPACT + 20) {
            double k = Math.min(1.0, (t - PRESSURE) / (double) (IMPACT - PRESSURE));
            pre.sweep(preR * k);
        }

        if (t == IMPACT) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 260 * scale, 2000, 5000F, 25, 9.0);
            int dep = Math.max(40, center.getY() - (level.getMinBuildHeight() + 2));
            Terrain.raiseRim(level, center.getX(), center.getZ(), craterR, 0.2, rnd);
            crater = new BowlCrater(level, center, craterR, dep, LINER).walls(LINER);
            perTick = (int) Math.ceil(crater.total() / 520.0);
            wave = new BlastWave(level, center, waveR, 0.9F, 0.05F).withPush(7.0, waveR * 0.8).flatten(center.getY() + 14, 0.6);
            Terrain.scatterRuin(level, center.getX(), center.getZ(), craterR, (int) (craterR * 2.6), 22.0, 0.0006, true, rnd);
            Terrain.scatterRuin(level, center.getX(), center.getZ(), (int) (craterR * 2.6), (int) (craterR * 4.5), 9.0, 0.0004, true, rnd);
        }
        if (crater != null && !crater.done() && t > IMPACT + 2) crater.step(perTick);
        if (wave != null && !wave.done()) {
            double k = Math.min(1.0, (t - IMPACT) / 240.0);
            wave.sweep(waveR * (1.0 - (1.0 - k) * (1.0 - k)));
        }
        if (t > IMPACT && t < IMPACT + 300 && t % 10 == 0) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 80 * scale, 1000, 40F, 15, 2.0);
        }
    }
}
