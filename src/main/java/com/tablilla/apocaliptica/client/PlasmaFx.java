package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.lerp;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.PlasmaStrike;
import java.util.List;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Lanza de Ra: una lanza de luz solar que se clava, con relampagos, sigilo, ondas que siguen el relieve y bloques volando. */
public class PlasmaFx implements FxEffect {
    private static final int C = PlasmaStrike.CHARGE;
    private static final int E = PlasmaStrike.BEAM_END;
    private static final int D = PlasmaStrike.DURATION;
    private static final float TOP = 420.0F;

    private static final DustParticleOptions AMBER = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.15F), 3.0F);

    private GroundMap ground;
    private List<Debris.Proto> protos;

    @Override
    public int duration() {
        return D;
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        if (t == 1) {
            float half = Math.min(280.0F, 250.0F * s);
            ground = new GroundMap(level, x, y, z, half, Math.max(4.0F, half / 45.0F));
            protos = Debris.sample(level, x, z, 3.0 * s, 62.0 * s, 260, 0.45F, r);
            FxUtil.snd(level, "plasma.lockon", x, y, z, 16.0F, 1.0F);
        }
        if (t == 6) FxUtil.snd(level, "plasma.charge", x, y, z, 16.0F, 1.0F);
        if (t == C) {
            FxUtil.snd(level, "plasma.fire", x, y, z, 16.0F, 1.0F);
            FxUtil.snd(level, "plasma.beam", x, y, z, 16.0F, 1.0F);
        }
        if (t == C + 1 && protos != null) {
            // el suelo y los arboles salen disparados
            Debris.burst(protos, x, y, z, 1.7, 2.1, 22, 0.9F, 1.7F, 40.0 * s, 70.0 * s, r);
            BlockState[] hot = {Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(),
                    Blocks.BLACKSTONE.defaultBlockState(), Blocks.NETHERRACK.defaultBlockState()};
            Debris.burst(Debris.synthetic(hot, x, y, z, 7.0 * s, 90, r), x, y, z, 1.1, 3.4, 6, 0.8F, 1.8F, 20.0 * s, 70.0 * s, r);
        }
        if (t > C + 1 && t < C + 62) Debris.ripple(level, x, z, (t - C) * 3.4 * s, 4.0 * s, 16, r);
        if (t == C + 8) FxUtil.snd(level, "plasma.collapse", x, y, z, 16.0F, 1.0F);
        if (t == C + 12) FxUtil.snd(level, "plasma.rumble", x, y, z, 16.0F, 1.0F);
        if (t > C && t < E && t % 40 == 0) FxUtil.snd(level, "plasma.crackle", x, y, z, 16.0F, 1.0F);
        if (t == E - 6) FxUtil.snd(level, "plasma.shutdown", x, y, z, 16.0F, 1.0F);

        if (t < C) {
            charge(level, x, y, z, t, r, s);
        } else if (t < E) {
            beam(level, x, y, z, t, r, s);
        } else if (t % 2 == 0) {
            FxUtil.p(level, ParticleTypes.LARGE_SMOKE, x + r.nextGaussian() * 6 * s, y + 1, z + r.nextGaussian() * 6 * s, 0, 0.3, 0);
            FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + r.nextGaussian() * 5 * s, y + 1, z + r.nextGaussian() * 5 * s, 0, 0.12, 0);
        }
    }

    private void charge(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s) {
        float k = t / (float) C;
        for (int i = 0; i < 8; i++) {
            double a = t * 0.3 + i * 0.785 + r.nextDouble() * 0.3;
            double rad = 45.0 * s * (1.0 - k) + 4.0;
            double c = Math.cos(a);
            double sn = Math.sin(a);
            FxUtil.p(level, ParticleTypes.END_ROD, x + c * rad, y + 0.3 + r.nextDouble() * 2.0, z + sn * rad, -c * 0.35, 0.12, -sn * 0.35);
            if (i % 2 == 0) FxUtil.p(level, AMBER, x + c * rad, y + 0.5, z + sn * rad, -c * 0.2, 0.05, -sn * 0.2);
        }
        for (int i = 0; i < 3; i++) {
            FxUtil.p(level, ParticleTypes.END_ROD, x + r.nextGaussian() * 1.5, y, z + r.nextGaussian() * 1.5, 0, 0.5 + r.nextDouble() * 0.5, 0);
        }
    }

    private void beam(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s) {
        double big = 7.0 * s;
        for (int i = 0; i < 14; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = r.nextDouble() * big * 2.2;
            FxUtil.p(level, ParticleTypes.FLAME, x + Math.cos(a) * d, y + 0.2, z + Math.sin(a) * d,
                    Math.cos(a) * 0.1, 0.25 + r.nextDouble() * 0.3, Math.sin(a) * 0.1);
        }
        for (int i = 0; i < 8; i++) {
            FxUtil.p(level, ParticleTypes.LARGE_SMOKE, x + r.nextGaussian() * big * 1.5, y + 0.5, z + r.nextGaussian() * big * 1.5, 0, 0.3, 0);
        }
        // columna gruesa de humo alrededor del rayo
        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = big * (1.6 + r.nextDouble() * 1.6);
            FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + Math.cos(a) * d, y + r.nextDouble() * 100.0, z + Math.sin(a) * d, 0, 0.2, 0);
            FxUtil.p(level, ParticleTypes.LARGE_SMOKE, x + Math.cos(a) * d, y + r.nextDouble() * 60.0, z + Math.sin(a) * d, 0, 0.25, 0);
        }
        for (int i = 0; i < 4; i++) {
            FxUtil.p(level, ParticleTypes.LAVA, x + r.nextGaussian() * big, y + 1, z + r.nextGaussian() * big, 0, 0, 0);
        }
        for (int i = 0; i < 6; i++) {
            FxUtil.p(level, ParticleTypes.ELECTRIC_SPARK, x + r.nextGaussian() * big * 0.6, y + r.nextDouble() * 60, z + r.nextGaussian() * big * 0.6,
                    r.nextGaussian() * 0.2, r.nextGaussian() * 0.2, r.nextGaussian() * 0.2);
        }
        for (int i = 0; i < 4; i++) {
            FxUtil.p(level, ParticleTypes.END_ROD, x + r.nextGaussian() * big * 0.4, y + r.nextDouble() * 300, z + r.nextGaussian() * big * 0.4, 0, -0.2, 0);
        }
        if (t % 8 == 0) {
            FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, x + r.nextGaussian() * big * 2, y + 1, z + r.nextGaussian() * big * 2, 0, 0, 0);
        }
        int bt = t - C;
        if (bt <= 60) {
            double rr = bt * 3.2 * s;
            int n = (int) Math.min(70, rr * 0.7 + 10);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                FxUtil.p(level, ParticleTypes.CLOUD, x + Math.cos(a) * rr, y + 0.5, z + Math.sin(a) * rr, Math.cos(a) * 0.25, 0.08, Math.sin(a) * 0.25);
            }
        }
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float sg = smooth(t, 0, 50) * (1.0F - smooth(t, C, C + 14));
        if (sg > 0.01F && ground != null) sigil(vc, m, t, s, sg);

        float rad;
        float yLow;
        if (t < C) {
            float k = t / (float) C;
            yLow = lerp(TOP, 0.0F, k * k * k);
            rad = (0.5F + 1.6F * k) * s;
        } else {
            yLow = 0.0F;
            rad = 7.0F * s * smooth(t, C, C + 6) * (1.0F - smooth(t, E - 10, E + 5)) * (1.0F + 0.07F * sin(t * 1.9F));
        }

        if (rad > 0.05F) {
            // la lanza: nucleo blanco, halo solar y halo azulado
            FxGeo.glowBeam(vc, m, 0, yLow - 2, 0, 0, TOP, 0, rad, 1.0F, 0.72F, 0.28F, 1.0F, 14);
            FxGeo.glowBeam(vc, m, 0, yLow - 2, 0, 0, TOP, 0, rad * 2.6F, 0.55F, 0.78F, 1.0F, 0.30F, 14);
            if (t < C) {
                // punta afilada de la lanza
                FxGeo.tube(vc, m, 0, yLow - 26.0F * s, 0, 0, yLow - 1.0F, 0, 0.0F, rad * 1.1F, 12, 1.0F, 0.95F, 0.8F, 0.95F, 0.95F);
                FxGeo.tube(vc, m, 0, yLow - 40.0F * s, 0, 0, yLow - 1.0F, 0, 0.0F, rad * 2.2F, 12, 1.0F, 0.7F, 0.3F, 0.35F, 0.35F);
            }
        }

        if (t >= C && rad > 0.5F) {
            for (int i = 0; i < 6; i++) {
                float yy = TOP - ((t * 7.0F + i * 70.0F) % TOP);
                FxGeo.ring(vc, m, 0, yy, 0, rad, rad * 2.4F, 24, 1.0F, 0.75F, 0.4F, 0.55F, 0.0F);
            }
            for (int j = 0; j < 6; j++) {
                Random rr = new Random(seed * 131L + (long) (t / 3.0F) * 17L + j * 7919L);
                float py = 30.0F + rr.nextFloat() * 260.0F;
                float ang = rr.nextFloat() * TAU;
                float[][] main = bolt(rr, 0.0F, py, 0.0F, ang, 17, s, 1.2F, 1.8F, 2.6F, true);
                FxGeo.bolt(vc, m, main[0], main[1], main[2], 17, 0.5F, 0.85F, 0.9F, 1.0F, 0.95F);
                FxGeo.bolt(vc, m, main[0], main[1], main[2], 17, 1.6F, 0.55F, 0.75F, 1.0F, 0.25F);
                // ramas del relampago
                for (int f = 0; f < 2; f++) {
                    int at = 4 + rr.nextInt(8);
                    float[][] br = bolt(rr, main[0][at], main[1][at], main[2][at], ang + (rr.nextFloat() - 0.5F) * 2.4F, 9, s, 0.9F, 1.2F, 2.4F, true);
                    FxGeo.bolt(vc, m, br[0], br[1], br[2], 9, 0.3F, 0.85F, 0.9F, 1.0F, 0.85F);
                    FxGeo.bolt(vc, m, br[0], br[1], br[2], 9, 1.0F, 0.55F, 0.75F, 1.0F, 0.2F);
                }
            }
        }

        // ondas de choque pegadas al relieve
        if (ground != null) {
            float since = t - C;
            float maxR = 230.0F * s;
            for (int k = 0; k < 3; k++) {
                float rr = (since - k * 9.0F) * 3.4F * s;
                if (rr > 1.0F && rr < maxR) {
                    float fade = 1.0F - rr / maxR;
                    float w = 2.0F + rr * 0.04F;
                    int sides = Math.max(40, Math.min(150, (int) (rr * 0.6F)));
                    FxGeo.terrainRing(vc, m, ground, 0, 0, rr - w, rr, sides, 0.8F + k, 1.0F, 0.8F, 0.5F, 0.0F, 0.7F * fade);
                    FxGeo.terrainRing(vc, m, ground, 0, 0, rr, rr + w, sides, 0.8F + k, 1.0F, 0.8F, 0.5F, 0.7F * fade, 0.0F);
                    FxGeo.terrainWall(vc, m, ground, 0, 0, rr, 10.0F + rr * 0.07F, sides, 0.6F, 1.0F, 0.7F, 0.4F, 0.5F * fade, 0.0F);
                }
            }
        }

        // domo de choque y bola de fuego
        float dk = smooth(t, C, C + 30);
        float da = 0.30F * (1.0F - smooth(t, C + 20, C + 80));
        if (dk > 0.0F && da > 0.01F) {
            FxGeo.sphere(vc, m, 0, 0, 0, 20.0F * s * dk, 8, 18, 1.0F, 1.0F, 0.9F, da * 1.5F);
            FxGeo.sphere(vc, m, 0, 0, 0, 38.0F * s * dk, 10, 22, 1.0F, 0.55F, 0.2F, da);
            FxGeo.sphere(vc, m, 0, 0, 0, 66.0F * s * dk, 10, 26, 0.7F, 0.8F, 1.0F, da * 0.45F);
        }
        float fb = smooth(t, C, C + 40) * (1.0F - smooth(t, C + 60, C + 150));
        if (fb > 0.01F) {
            FxGeo.sphere(vc, m, 0, 4.0F * s, 0, 15.0F * s * fb, 8, 16, 1.0F, 0.85F, 0.5F, 0.55F * fb);
            FxGeo.sphere(vc, m, 0, 4.0F * s, 0, 24.0F * s * fb, 8, 16, 1.0F, 0.45F, 0.1F, 0.3F * fb);
        }
    }

    /** Relampago zigzagueante (n puntos) desde un punto; baja si down. */
    private static float[][] bolt(Random rr, float x0, float y0, float z0, float ang, int n, float s,
                                  float dyMin, float dyVar, float step, boolean down) {
        float[] xs = new float[n];
        float[] ys = new float[n];
        float[] zs = new float[n];
        xs[0] = x0;
        ys[0] = y0;
        zs[0] = z0;
        for (int k = 1; k < n; k++) {
            ang += (rr.nextFloat() - 0.5F) * 0.9F;
            xs[k] = xs[k - 1] + cos(ang) * step * s;
            zs[k] = zs[k - 1] + sin(ang) * step * s;
            ys[k] = ys[k - 1] - (dyMin + rr.nextFloat() * dyVar);
        }
        return new float[][] {xs, ys, zs};
    }

    /** Sigilo en el suelo: anillos blancos concentricos y trazos, pegados al relieve. */
    private void sigil(VertexConsumer vc, Matrix4f m, float t, float s, float sg) {
        float rv = smooth(t, 0, 40);
        float[] radii = {10.0F, 18.0F, 26.0F, 34.0F};
        for (int i = 0; i < radii.length; i++) {
            float rr = radii[i] * s;
            float w = (i == 3 ? 1.6F : 1.0F) * Math.max(1.0F, s);
            FxGeo.polyCircle(vc, m, ground, 0, 0, rr, 64, w, 0.35F, 1.0F, 1.0F, 1.0F, 0.75F * sg, rv);
            FxGeo.polyCircle(vc, m, ground, 0, 0, rr, 64, w * 3.0F, 0.3F, 1.0F, 0.75F, 0.35F, 0.25F * sg, rv);
        }
        for (int i = 0; i < 24; i++) {
            float a = i * TAU / 24 + t * 0.012F;
            float r0 = (i % 2 == 0 ? 6.0F : 12.0F) * s;
            float r1 = (i % 2 == 0 ? 36.0F : 28.0F) * s;
            FxGeo.terrainLine(vc, m, ground, cos(a) * r0, sin(a) * r0, cos(a) * r1, sin(a) * r1,
                    0.9F * Math.max(1.0F, s), 0.35F, 1.0F, 0.95F, 0.85F, 0.7F * sg, 0.1F * sg);
        }
        for (int i = 0; i < 12; i++) {
            float a = -i * TAU / 12 - t * 0.02F;
            float rr = 30.0F * s;
            FxGeo.terrainLine(vc, m, ground, cos(a) * rr, sin(a) * rr, cos(a + 0.18F) * rr, sin(a + 0.18F) * rr,
                    2.0F * Math.max(1.0F, s), 0.4F, 0.7F, 0.8F, 1.0F, 0.7F * sg, 0.7F * sg);
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.5F * smooth(t, 20, C);
        if (t >= C) a += 2.8F * (float) Math.exp(-(t - C) / 40.0F) + 1.0F * (1.0F - smooth(t, 150, E));
        return a * (1.0F - smooth(t, E, D));
    }

    @Override
    public float flash(float t) {
        if (t >= C && t < C + 14) {
            float k = 1.0F - (t - C) / 14.0F;
            return k * k;
        }
        return 0.0F;
    }

    @Override
    public float flare(float t) {
        return smooth(t, C - 6, C + 4) * (1.0F - smooth(t, E - 30, E));
    }

    @Override
    public int flareColor() {
        return 0x6FA8FF;
    }

    @Override
    public float flareHeight() {
        return 70.0F;
    }

    @Override
    public int tint(float t) {
        int al = (int) (70.0F * smooth(t, 30, C + 10) * (1.0F - smooth(t, E, D)));
        return (al << 24) | 0x6A2DB5;
    }
}
