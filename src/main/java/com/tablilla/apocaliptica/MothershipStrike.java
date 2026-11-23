package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

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
    private BowlCrater crater;
    private BlastWave wave;
    private int perTick = 1;

    public MothershipStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.waveR = Math.max(40, Math.min(190, Math.round(130 * scale)));
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
            int r = Math.min(150, Math.round(88 * scale));
            crater = new BowlCrater(level, center, r, Math.round(46 * scale), LINER);
            perTick = (int) Math.ceil(crater.total() / 180.0);
            wave = new BlastWave(level, center, waveR, 0.5F, 0.015F);
        }
        if (crater != null && !crater.done() && t > FIRE + 2) crater.step(perTick);
        if (wave != null && t > FIRE && !wave.done()) wave.sweep(waveRadius());
        if (t > FIRE && t <= FIRE + 120 && t % 6 == 0) {
            Terrain.hurtArea(level, cx(), cy(), cz(), waveRadius(), 300, 25F, 6, 1.5);
        }
    }

    private double waveRadius() {
        double k = Math.min(1.0, (t - FIRE) / 90.0);
        return waveR * (1.0 - (1.0 - k) * (1.0 - k));
    }
}
