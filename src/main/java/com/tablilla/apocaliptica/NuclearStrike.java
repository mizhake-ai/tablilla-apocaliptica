package com.tablilla.apocaliptica;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hongo Nuclear (estilo Fallout): una bomba cae desde la orbita y estalla: destello, hongo enorme, onda de choque y un barrido
 * de devastacion. Deja un crater vitrificado con lava y obsidiana al centro, 4 bloques de diamante, arena alrededor, arboles
 * arrancados cerca y sin hojas mas lejos. Cerca mata; a media distancia lanza por los aires.
 */
public class NuclearStrike extends BaseStrike {
    public static final int DET = 200;
    public static final int DURATION = 900;

    private static final BowlCrater.Liner LINER = (d, r) -> {
        float f = r.nextFloat();
        if (d < 0.18) return f < 0.45F ? Blocks.LAVA.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
        if (d < 0.55) return f < 0.5F ? Blocks.GLASS.defaultBlockState() : (f < 0.8F ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.TINTED_GLASS.defaultBlockState());
        return f < 0.5F ? Blocks.GLASS.defaultBlockState() : (f < 0.8F ? Blocks.SAND.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState());
    };

    private BowlCrater crater;
    private BlastWave wave;
    private int perTick = 1;
    private boolean diamonds = false;
    private final int craterR;
    private final int waveR;

    public NuclearStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.craterR = Mth.clamp(Math.round(115 * scale), 40, 170);
        this.waveR = Mth.clamp(Math.round(300 * scale), 100, 420);
    }

    @Override
    protected Laser laser() {
        return Laser.NUCLEAR;
    }

    @Override
    protected int duration() {
        return DURATION;
    }

    @Override
    protected void serverTick() {
        if (!StrikeManager.terrainDamage) return;
        if (t == DET) {
            // cerca mata de golpe; a media distancia la onda lanza por los aires
            Terrain.hurtArea(level, cx(), cy(), cz(), 150 * scale, 3000, 8000F, 30, 8.0);
            sandRing();
            Terrain.raiseRim(level, center.getX(), center.getZ(), craterR, 0.08, rnd);
            int dep = Mth.clamp(Math.round(26 * scale), 10, 60);
            crater = new BowlCrater(level, center, craterR, dep, LINER).walls(LINER);
            perTick = (int) Math.ceil(crater.total() / 200.0);
            wave = new BlastWave(level, center, waveR, 0.5F, 0.012F).withPush(7.0, waveR * 0.9);
        }
        if (crater != null && !crater.done() && t > DET + 2) crater.step(perTick);
        if (crater != null && crater.done() && !diamonds) {
            diamonds = true;
            // 4 bloques de diamante en el centro del cráter
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    p.set(x, Terrain.surfaceTop(level, x, z), z);
                    level.setBlock(p, Blocks.DIAMOND_BLOCK.defaultBlockState(), Terrain.FLAGS);
                }
            }
        }
        if (wave != null && !wave.done()) {
            double k = Math.min(1.0, (t - DET) / 160.0);
            wave.sweep(waveR * (1.0 - (1.0 - k) * (1.0 - k)));
        }
        if (t > DET && t < DET + 200 && t % 10 == 0) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 50 * scale, 1000, 40F, 20, 2.0);
        }
    }

    /** Arena alrededor del crater y un poco por fuera (unos 15 bloques). */
    private void sandRing() {
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState red = Blocks.RED_SAND.defaultBlockState();
        int in = (int) (craterR * 0.85);
        int out = craterR + 15;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -out; dx <= out; dx++) {
            for (int dz = -out; dz <= out; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < in || d > out) continue;
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int top = Terrain.surfaceTop(level, x, z);
                p.set(x, top, z);
                BlockState cur = level.getBlockState(p);
                if (!Terrain.breakable(level, p, cur) || !cur.getFluidState().isEmpty()) continue;
                level.setBlock(p, rnd.nextFloat() < 0.65F ? sand : red, Terrain.FLAGS);
            }
        }
    }
}
