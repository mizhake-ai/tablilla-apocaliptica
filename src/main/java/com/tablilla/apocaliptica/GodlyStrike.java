package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Juicio Divino (Godly): columna de luz dorada que deja un crater de oro y cuarzo. */
public class GodlyStrike extends BaseStrike {
    public static final int CHARGE = 60;
    public static final int BEAM_END = 190;
    public static final int DURATION = 260;

    private static final BowlCrater.Liner LINER = (d, r) -> {
        if (d < 0.28) return (r.nextFloat() < 0.04F ? Blocks.DIAMOND_BLOCK : Blocks.GOLD_BLOCK).defaultBlockState();
        if (d < 0.62) return r.nextFloat() < 0.5F ? Blocks.QUARTZ_BLOCK.defaultBlockState() : Blocks.SMOOTH_QUARTZ.defaultBlockState();
        float f = r.nextFloat();
        if (f < 0.05F) return Blocks.GOLD_BLOCK.defaultBlockState();
        return f < 0.22F ? Blocks.GLOWSTONE.defaultBlockState() : Blocks.QUARTZ_BRICKS.defaultBlockState();
    };

    private BowlCrater crater;
    private BlastWave wave;
    private int perTick = 1;
    private java.util.List<double[]> lines;
    private int lineCursor = 0;
    private final int craterR;
    private final int waveR;

    public GodlyStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.craterR = Math.round(52 * scale);
        this.waveR = Math.max(40, Math.min(200, Math.round(130 * scale)));
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
        if (t == 1) lines = MagicCircle.blockLines(MagicCircle.RADIUS * scale, seed);
        // el circulo magico se va dibujando en la tierra con lineas de bloques de oro (y diamante en los cruces)
        if (lines != null && t >= CHARGE + 3 && lineCursor < lines.size()) {
            int perTickLines = (int) Math.ceil(lines.size() / 90.0);
            drawLines(perTickLines);
        }
        if (t == CHARGE) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 90 * scale, 200, 60F, 0, 3.6);
            Terrain.raiseRim(level, center.getX(), center.getZ(), craterR, 0.14, rnd);
            crater = new BowlCrater(level, center, craterR, Math.round(28 * scale), LINER);
            perTick = (int) Math.ceil(crater.total() / 110.0);
            wave = new BlastWave(level, center, waveR, 0.4F, 0.0F).withPush(3.0, waveR * 1.1);
        }
        if (t > CHARGE && t <= BEAM_END && t % 5 == 0) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 14 * scale, 300, 12F, 0, 1.0);
        }
        if (crater != null && !crater.done() && t > CHARGE + 2) crater.step(perTick);
        if (wave != null && t > CHARGE && !wave.done()) {
            double k = Math.min(1.0, (t - CHARGE) / 70.0);
            wave.sweep(waveR * (1.0 - (1.0 - k) * (1.0 - k)));
        }
    }

    private void drawLines(int count) {
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        BlockState diamond = Blocks.DIAMOND_BLOCK.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int k = 0; k < count && lineCursor < lines.size(); k++) {
            double[] l = lines.get(lineCursor++);
            if (l[0] * l[0] + l[1] * l[1] <= (craterR + 2.0) * (craterR + 2.0)) continue;
            int x = center.getX() + (int) l[0];
            int z = center.getZ() + (int) l[1];
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int top = Terrain.surfaceTop(level, x, z);
            p.set(x, top, z);
            BlockState cur = level.getBlockState(p);
            if (!Terrain.breakable(level, p, cur) || !cur.getFluidState().isEmpty()) continue;
            level.setBlock(p, l[2] > 0.5 ? diamond : gold, Terrain.FLAGS);
        }
    }
}
