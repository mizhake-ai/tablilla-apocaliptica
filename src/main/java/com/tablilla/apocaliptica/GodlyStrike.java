package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Juicio Divino (Godly): columna de luz dorada que deja un crater de oro y cuarzo. */
public class GodlyStrike extends BaseStrike {
    public static final int CHARGE = 60;
    public static final int BEAM_END = 190;
    public static final int DURATION = 260;

    private static final BowlCrater.Liner LINER = (d, r) -> {
        if (d < 0.28) return Blocks.GOLD_BLOCK.defaultBlockState();
        if (d < 0.62) return r.nextFloat() < 0.5F ? Blocks.QUARTZ_BLOCK.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState();
        return r.nextFloat() < 0.2F ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.QUARTZ_BRICKS.defaultBlockState();
    };

    private BowlCrater crater;
    private int perTick = 1;

    public GodlyStrike(ServerLevel level, BlockPos center) {
        super(level, center);
    }

    @Override
    protected Laser laser() {
        return Laser.GODLY;
    }

    @Override
    protected int duration() {
        return DURATION;
    }

    @Override
    protected void serverTick() {
        if (!StrikeManager.terrainDamage) return;
        if (t == CHARGE) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 45 * scale, 200, 60F, 0, 3.5);
            crater = new BowlCrater(level, center, Math.round(52 * scale), Math.round(28 * scale), LINER);
            perTick = (int) Math.ceil(crater.total() / 110.0);
        }
        if (t > CHARGE && t <= BEAM_END && t % 5 == 0) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 14 * scale, 300, 12F, 0, 1.0);
        }
        if (crater != null && !crater.done() && t > CHARGE + 2) crater.step(perTick);
    }
}
