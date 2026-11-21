package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Lanza de Ra (Plasma): rayo de plasma gigante que funde un enorme crater en magma, obsidiana y vidrio. */
public class PlasmaStrike extends BaseStrike {
    public static final int CHARGE = 70;
    public static final int BEAM_END = 200;
    public static final int DURATION = 270;

    private static final BowlCrater.Liner LINER = (d, r) -> {
        if (d < 0.30) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (d < 0.65) return Blocks.OBSIDIAN.defaultBlockState();
        return r.nextFloat() < 0.55F ? Blocks.GLASS.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    };

    private BowlCrater crater;
    private int perTick = 1;

    public PlasmaStrike(ServerLevel level, BlockPos center) {
        super(level, center);
    }

    @Override
    protected Laser laser() {
        return Laser.PLASMA;
    }

    @Override
    protected int duration() {
        return DURATION;
    }

    @Override
    protected void serverTick() {
        if (!StrikeManager.terrainDamage) return;
        if (t == CHARGE) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 50 * scale, 200, 60F, 8, 3.0);
            crater = new BowlCrater(level, center, Math.round(58 * scale), Math.round(30 * scale), LINER);
            perTick = (int) Math.ceil(crater.total() / 120.0);
        }
        if (t > CHARGE && t <= BEAM_END && t % 5 == 0) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 16 * scale, 300, 10F, 6, 0.8);
        }
        if (crater != null && !crater.done() && t > CHARGE + 2) crater.step(perTick);
    }
}
