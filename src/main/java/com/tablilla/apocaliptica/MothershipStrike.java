package com.tablilla.apocaliptica;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La Barca Celeste: una nave-asteroide de un kilometro llega sobre el objetivo, perfora un pozo hasta la bedrock,
 * corta una zanja fundida con su rayo mientras avanza, y se va en un parpadeo. Arrasa un area enorme.
 */
public class MothershipStrike extends BaseStrike {
    public static final int ARRIVE = 110;
    public static final int CHARGE = 210;
    public static final int FIRE = 290;
    public static final int SWEEP_START = 370;
    public static final int FIRE_END = 560;
    public static final int DEPART = 580;
    public static final int DURATION = 720;
    /** Largo de la zanja (sin escala). La nave avanza este tramo en +X. */
    public static final double SWEEP_LEN = 270.0;

    private static final BowlCrater.Liner LINER = (d, r) -> {
        if (d < 0.30) return Blocks.MAGMA_BLOCK.defaultBlockState();
        if (d < 0.60) return r.nextFloat() < 0.6F ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.CRYING_OBSIDIAN.defaultBlockState();
        return r.nextFloat() < 0.5F ? Blocks.GLASS.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    };

    private final int waveR;
    private final int shaftR;
    private final int trenchW;
    private final int trenchDepth;
    private final int trenchLen;
    private BowlCrater mouth;
    private BlastWave wave;
    private int mouthPerTick = 1;
    private List<int[]> shaft;
    private int shaftNext = 0;
    private int slice = -1;

    public MothershipStrike(ServerLevel level, BlockPos center) {
        super(level, center);
        this.waveR = Mth.clamp(Math.round(170 * scale), 50, 260);
        this.shaftR = Mth.clamp(Math.round(9 * scale), 5, 20);
        this.trenchW = Mth.clamp(Math.round(5 * scale), 3, 12);
        this.trenchDepth = Mth.clamp(Math.round(14 * scale), 6, 30);
        this.trenchLen = (int) Math.round(SWEEP_LEN * scale);
    }

    @Override
    protected Laser laser() {
        return Laser.MOTHERSHIP;
    }

    @Override
    protected int duration() {
        return DURATION;
    }

    /** Fraccion del recorrido de la nave (igual que en el cliente). */
    public static double sweepProgress(int t) {
        double k = Math.max(0.0, Math.min(1.0, (t - SWEEP_START) / (double) (FIRE_END - SWEEP_START)));
        double sm = k * k * (3.0 - 2.0 * k);
        return 1.0 - (1.0 - sm) * (1.0 - sm);
    }

    private double waveRadius() {
        double k = Math.min(1.0, (t - FIRE) / 110.0);
        return waveR * (1.0 - (1.0 - k) * (1.0 - k));
    }

    @Override
    protected void serverTick() {
        if (!StrikeManager.terrainDamage) return;
        if (t == FIRE) {
            Terrain.hurtArea(level, cx(), cy(), cz(), 120 * scale, 500, 200F, 10, 4.5);
            mouth = new BowlCrater(level, center, Mth.clamp(Math.round(40 * scale), 12, 90), Mth.clamp(Math.round(14 * scale), 6, 40), LINER);
            mouthPerTick = (int) Math.ceil(mouth.total() / 100.0);
            wave = new BlastWave(level, center, waveR, 0.6F, 0.02F).withPush(4.0, waveR * 1.1);
            Terrain.raiseRim(level, center.getX(), center.getZ(), Mth.clamp(Math.round(40 * scale), 12, 90), 0.16, rnd);
            shaft = new ArrayList<>();
            for (int dx = -shaftR; dx <= shaftR; dx++) {
                for (int dz = -shaftR; dz <= shaftR; dz++) {
                    if (dx * dx + dz * dz <= shaftR * shaftR) shaft.add(new int[] {dx, dz});
                }
            }
            shaft.sort(Comparator.comparingInt((int[] c) -> c[0] * c[0] + c[1] * c[1]));
        }
        if (mouth != null && !mouth.done() && t > FIRE + 2) mouth.step(mouthPerTick);
        if (wave != null && t > FIRE && !wave.done()) wave.sweep(waveRadius());

        // el pozo: baja hasta la bedrock
        if (shaft != null && t > FIRE + 4 && shaftNext < shaft.size()) {
            int perTick = (int) Math.ceil(shaft.size() / 70.0);
            for (int k = 0; k < perTick && shaftNext < shaft.size(); k++) {
                int[] c = shaft.get(shaftNext++);
                int x = center.getX() + c[0];
                int z = center.getZ() + c[1];
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int top = Terrain.surfaceTop(level, x, z) + 20;
                Terrain.cutColumn(level, x, z, top, level.getMinBuildHeight() + 1);
            }
        }

        // la zanja fundida: sigue a la nave
        if (t >= SWEEP_START && t <= FIRE_END + 2) {
            int target = (int) Math.floor(trenchLen * sweepProgress(t));
            while (slice < target) {
                slice++;
                cutSlice(slice);
            }
            if (t % 6 == 0) {
                Terrain.hurtArea(level, cx() + slice, cy(), cz(), 40 * scale, 300, 25F, 6, 2.5);
            }
        }
    }

    private void cutSlice(int sx) {
        int x = center.getX() + sx;
        int ridge = Math.max(4, Math.round(9 * scale));
        int burn = Math.max(8, Math.round(22 * scale));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dz = -(trenchW + burn); dz <= trenchW + burn; dz++) {
            int z = center.getZ() + dz;
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int ad = Math.abs(dz);
            if (ad <= trenchW) {
                double k = (double) dz / (trenchW + 0.5);
                int surface = Terrain.surfaceTop(level, x, z);
                int dep = (int) Math.round(trenchDepth * (1.0 - k * k));
                int floorY = surface - dep;
                Terrain.cutColumn(level, x, z, surface + 22, floorY);
                BlockPos fp = new BlockPos(x, floorY - 1, z);
                BlockState st = level.getBlockState(fp);
                if (!Terrain.breakable(level, fp, st)) continue;
                BlockState lining;
                if (dz == 0 && sx % 4 == 0) lining = Blocks.LAVA.defaultBlockState();
                else if (Math.abs(dz) <= 1) lining = Blocks.MAGMA_BLOCK.defaultBlockState();
                else lining = rnd.nextFloat() < 0.55F ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
                level.setBlock(fp, lining, Terrain.FLAGS);
            } else {
                // junto a la zanja: tierra levantada por la presion y, mas alla, tierra quemada
                if (ad <= trenchW + ridge) {
                    double k = 1.0 - (double) (ad - trenchW) / ridge;
                    int h = (int) Math.round(4.0 * scale * k * (0.5 + rnd.nextDouble()));
                    if (h >= 1) {
                        int surface = Terrain.surfaceTop(level, x, z);
                        p.set(x, surface, z);
                        BlockState base = level.getBlockState(p);
                        if (Terrain.breakable(level, p, base) && base.getFluidState().isEmpty()) {
                            for (int y = 1; y <= h; y++) {
                                p.set(x, surface + y, z);
                                if (level.getBlockState(p).isAir()) level.setBlock(p, base, Terrain.FLAGS);
                            }
                        }
                    }
                }
                if (rnd.nextFloat() < 0.8F) Terrain.scorchColumn(level, x, z, 0.65F, 0.01F, rnd);
            }
        }
    }
}
