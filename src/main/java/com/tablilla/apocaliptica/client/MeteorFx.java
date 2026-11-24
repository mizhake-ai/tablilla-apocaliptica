package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.MeteorPlan;
import com.tablilla.apocaliptica.MeteorStrike;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;

/** Lluvia de Estrellas: meteoros de roca con estelas de fuego enormes que arrasan la zona; el suelo y los arboles salen volando. */
public class MeteorFx implements FxEffect {
    private static final int D = MeteorStrike.DURATION;
    private static final double HORIZ = MeteorPlan.HORIZ;
    private static final double HEIGHT = MeteorPlan.HEIGHT;

    private static final BlockState[] ROCKS = {
            Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(),
            Blocks.NETHERRACK.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(),
            Blocks.MAGMA_BLOCK.defaultBlockState()};

    private final MeteorPlan plan;
    private final float[] gy = new float[MeteorPlan.COUNT];
    @SuppressWarnings("unchecked")
    private final List<Debris.Proto>[] protos = new List[MeteorPlan.COUNT];

    public MeteorFx(long seed, float scale) {
        this.plan = new MeteorPlan(seed, scale);
    }

    @Override
    public int duration() {
        return D;
    }

    private void groundAt(ClientLevel level, double x, double y, double z, int i) {
        int wx = (int) Math.floor(x + plan.ox[i]);
        int wz = (int) Math.floor(z + plan.oz[i]);
        int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
        gy[i] = (h < y - 80) ? 0.0F : (float) (h - y);
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        if (t == 1) {
            for (int i = 0; i < MeteorPlan.COUNT; i++) {
                groundAt(level, x, y, z, i);
                boolean bigI = plan.big[i];
                protos[i] = Debris.sample(level, x + plan.ox[i], z + plan.oz[i], 0.0,
                        Math.max(4.0, plan.crater[i] * 1.5), plan.giant(i) ? 240 : (bigI ? 34 : 12), 0.5F, r);
            }
            FxUtil.snd(level, "meteor.lockon", x, y, z, 16.0F, 1.0F);
        }
        if (t == 6) FxUtil.snd(level, "meteor.charge", x, y, z, 16.0F, 1.0F);
        if (t == 30) {
            FxUtil.snd(level, "meteor.fire", x, y, z, 16.0F, 1.0F);
            FxUtil.snd(level, "meteor.approach", x, y, z, 16.0F, 1.0F);
        }
        if (t == 36) FxUtil.snd(level, "meteor.storm", x, y, z, 16.0F, 1.0F);
        if (t == 150 || t == 400) FxUtil.snd(level, "meteor.rumble", x, y, z, 16.0F, 1.0F);
        if (t == D - 60) FxUtil.snd(level, "meteor.shutdown", x, y, z, 16.0F, 1.0F);

        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < MeteorPlan.COUNT; i++) {
            int ts = plan.start[i];
            int ti = plan.impactTick(i);
            if (t == ts) {
                groundAt(level, x, y, z, i);
                // el cuerpo de roca del meteoro (bloque real girando)
                double ix = x + plan.ox[i];
                double iy = y + gy[i];
                double iz = z + plan.oz[i];
                int f = plan.flight[i];
                Debris.linear(ROCKS[i % ROCKS.length], ix + plan.dirX[i] * HORIZ, iy + HEIGHT, iz + plan.dirZ[i] * HORIZ,
                        -plan.dirX[i] * HORIZ / f, -HEIGHT / f, -plan.dirZ[i] * HORIZ / f,
                        (float) (plan.rad[i] * 1.3), iy - 1.0, f + 6, r);
            }
            if (t < ts || t > ti) continue;

            double tau = (t - ts) / (double) plan.flight[i];
            double k = 1.0 - tau;
            double mx = x + plan.ox[i] + plan.dirX[i] * HORIZ * k;
            double my = y + gy[i] + HEIGHT * k;
            double mz = z + plan.oz[i] + plan.dirZ[i] * HORIZ * k;

            if (t == ts + plan.flight[i] / 2) {
                FxUtil.snd(level, (i % 2 == 0) ? "meteor.whoosh1" : "meteor.whoosh2", mx, my, mz, 8.0F, 0.9F + r.nextFloat() * 0.3F);
            }

            double tl = Math.sqrt(HORIZ * HORIZ + HEIGHT * HEIGHT);
            double bx = plan.dirX[i] * HORIZ / tl;
            double by = HEIGHT / tl;
            double bz = plan.dirZ[i] * HORIZ / tl;
            double rd = plan.rad[i];
            for (int q = 0; q < 7; q++) {
                double off = q * rd * 1.8;
                FxUtil.p(level, ParticleTypes.FLAME, mx + bx * off, my + by * off, mz + bz * off,
                        r.nextGaussian() * 0.06, r.nextGaussian() * 0.06, r.nextGaussian() * 0.06);
            }
            for (int q = 0; q < 2; q++) {
                FxUtil.p(level, ParticleTypes.LARGE_SMOKE, mx + bx * rd * (3 + q * 4), my + by * rd * (3 + q * 4), mz + bz * rd * (3 + q * 4), 0, 0, 0);
            }
            FxUtil.p(level, ParticleTypes.LAVA, mx, my, mz, 0, 0, 0);

            if (t == ti) {
                double ix = x + plan.ox[i];
                double iy = y + gy[i];
                double iz = z + plan.oz[i];
                double d2 = mc.player == null ? 0.0 : mc.player.distanceToSqr(ix, iy, iz);
                String nm = (d2 > 90.0 * 90.0 ? "meteor.hit_far" : "meteor.hit") + (1 + i % 3);
                FxUtil.snd(level, nm, ix, iy, iz, 12.0F, 0.9F + r.nextFloat() * 0.2F);
                boolean big = plan.big[i];
                boolean giantI = plan.giant(i);
                if (big) FxUtil.snd(level, "meteor.impact", ix, iy, iz, 16.0F, 1.0F);
                if (giantI) {
                    FxUtil.snd(level, "plasma.collapse", ix, iy, iz, 16.0F, 1.0F);
                    FxUtil.snd(level, "mothership.boom", ix, iy, iz, 16.0F, 1.0F);
                }
                int rc = Math.max(3, plan.crater[i]);
                if (protos[i] != null) {
                    Debris.burst(protos[i], ix, iy, iz, giantI ? 3.0 : (big ? 1.6 : 1.1), giantI ? 3.4 : (big ? 2.2 : 1.5), giantI ? 40 : 3,
                            big ? 1.2F : 0.8F, giantI ? 3.0F : (big ? 2.4F : 1.5F), rc, rc * 0.8, r);
                }
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, ix, iy + 1, iz, 0, 0, 0);
                if (big) {
                    for (int e = 0; e < (giantI ? 24 : 5); e++) {
                        FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, ix + r.nextGaussian() * rc * 0.4, iy + 1, iz + r.nextGaussian() * rc * 0.4, 0, 0, 0);
                    }
                }
                for (int e = 0; e < 60; e++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double sp = 0.3 + r.nextDouble() * 1.0;
                    FxUtil.p(level, ParticleTypes.FLAME, ix, iy + 1, iz, Math.cos(a) * sp, 0.3 + r.nextDouble() * 0.9, Math.sin(a) * sp);
                }
                for (int e = 0; e < 36; e++) {
                    FxUtil.p(level, ParticleTypes.LARGE_SMOKE, ix + r.nextGaussian() * rc * 0.5, iy + 1, iz + r.nextGaussian() * rc * 0.5, 0, 0.28, 0);
                }
                for (int e = 0; e < 8; e++) {
                    FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, ix + r.nextGaussian() * rc * 0.4, iy + 1, iz + r.nextGaussian() * rc * 0.4, 0, 0.12, 0);
                }
                for (int e = 0; e < 40; e++) {
                    double a = Math.PI * 2 * e / 40;
                    FxUtil.p(level, ParticleTypes.CLOUD, ix + Math.cos(a) * rc * 0.5, iy + 0.5, iz + Math.sin(a) * rc * 0.5, Math.cos(a) * 0.5, 0.1, Math.sin(a) * 0.5);
                }
            }
        }
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        double tl = Math.sqrt(HORIZ * HORIZ + HEIGHT * HEIGHT);
        for (int i = 0; i < MeteorPlan.COUNT; i++) {
            int ts = plan.start[i];
            int ti = plan.impactTick(i);
            float life = plan.giant(i) ? 170.0F : 36.0F;
            if (t < ts || t > ti + life) continue;
            float ox = (float) plan.ox[i];
            float oz = (float) plan.oz[i];
            float rd = (float) plan.rad[i];

            if (t <= ti) {
                float tau = (t - ts) / (float) plan.flight[i];
                float k = 1.0F - tau;
                float mx = ox + (float) (plan.dirX[i] * HORIZ) * k;
                float my = gy[i] + (float) HEIGHT * k;
                float mz = oz + (float) (plan.dirZ[i] * HORIZ) * k;
                float tx = (float) (plan.dirX[i] * HORIZ / tl);
                float ty = (float) (HEIGHT / tl);
                float tz = (float) (plan.dirZ[i] * HORIZ / tl);
                float len = rd * 22.0F;

                FxGeo.tube(vc, m, mx, my, mz, mx + tx * len * 0.6F, my + ty * len * 0.6F, mz + tz * len * 0.6F,
                        rd * 0.45F, rd * 0.05F, 10, 1.0F, 1.0F, 0.92F, 0.95F, 0.0F);
                FxGeo.tube(vc, m, mx, my, mz, mx + tx * len * 0.85F, my + ty * len * 0.85F, mz + tz * len * 0.85F,
                        rd * 0.9F, rd * 0.1F, 10, 1.0F, 0.75F, 0.3F, 0.55F, 0.0F);
                FxGeo.tube(vc, m, mx, my, mz, mx + tx * len, my + ty * len, mz + tz * len,
                        rd * 1.5F, rd * 0.2F, 10, 1.0F, 0.42F, 0.1F, 0.26F, 0.0F);
                FxGeo.tube(vc, m, mx, my, mz, mx + tx * len * 1.3F, my + ty * len * 1.3F, mz + tz * len * 1.3F,
                        rd * 2.4F, rd * 0.3F, 10, 1.0F, 0.3F, 0.05F, 0.10F, 0.0F);
                FxGeo.sphere(vc, m, mx, my, mz, rd * 1.1F, 6, 10, 1.0F, 0.75F, 0.3F, 0.45F);
                FxGeo.sphere(vc, m, mx, my, mz, rd * 1.8F, 6, 10, 1.0F, 0.4F, 0.1F, 0.20F);
                FxGeo.sphere(vc, m, mx, my, mz, rd * 3.0F, 6, 10, 1.0F, 0.3F, 0.05F, 0.08F);
            } else {
                float since = t - ti;
                float fade = 1.0F - since / life;
                float rc = Math.max(3, plan.crater[i]);
                float gr = smooth(since, 0, 8);
                FxGeo.sphere(vc, m, ox, gy[i], oz, rc * 0.7F * gr, 6, 12, 1.0F, 0.9F, 0.6F, 0.55F * fade);
                FxGeo.sphere(vc, m, ox, gy[i], oz, rc * 1.3F * gr, 6, 12, 1.0F, 0.5F, 0.15F, 0.3F * fade);
                float rr = since * rc * (plan.giant(i) ? 1.1F : 0.4F);
                FxGeo.ring(vc, m, ox, gy[i] + 0.6F, oz, Math.max(0.0F, rr - 1.8F), rr, 28, 1.0F, 0.7F, 0.3F, 0.0F, 0.6F * fade);
                FxGeo.ring(vc, m, ox, gy[i] + 0.6F, oz, rr, rr + 1.8F, 28, 1.0F, 0.7F, 0.3F, 0.6F * fade, 0.0F);
            }
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.0F;
        for (int i = 0; i < MeteorPlan.COUNT; i++) {
            float dt = t - plan.impactTick(i);
            if (dt >= 0.0F && dt < 160.0F) a += (plan.giant(i) ? 6.0F : (plan.big[i] ? 2.2F : 0.8F)) * (float) Math.exp(-dt / (plan.giant(i) ? 40.0F : 10.0F));
        }
        return Math.min(a, 7.0F) * (1.0F - smooth(t, D - 60, D));
    }

    @Override
    public float flash(float t) {
        float f = 0.0F;
        for (int i = 0; i < MeteorPlan.COUNT; i++) {
            if (!plan.big[i]) continue;
            float len = plan.giant(i) ? 40.0F : 14.0F;
            float dt = t - plan.impactTick(i);
            if (dt >= 0.0F && dt < len) {
                float k = 1.0F - dt / len;
                f = Math.max(f, (plan.giant(i) ? 1.0F : 0.55F) * k * k);
            }
        }
        return f;
    }

    @Override
    public int tint(float t) {
        int al = (int) (58.0F * smooth(t, 10, 60) * (1.0F - smooth(t, D - 80, D - 25)));
        return (al << 24) | 0xB03800;
    }
}
