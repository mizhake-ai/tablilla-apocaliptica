package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.lerp;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.PlasmaStrike;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Lanza de Ra: un pilar de plasma ambar con relampagos, sigilo en el suelo, ondas y domo de fuego. */
public class PlasmaFx implements FxEffect {
    private static final int C = PlasmaStrike.CHARGE;
    private static final int E = PlasmaStrike.BEAM_END;
    private static final int D = PlasmaStrike.DURATION;
    private static final float TOP = 420.0F;

    private static final DustParticleOptions AMBER = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.15F), 3.0F);

    @Override
    public int duration() {
        return D;
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        if (t == 1) FxUtil.snd(level, "plasma.lockon", x, y, z, 16.0F, 1.0F);
        if (t == 6) FxUtil.snd(level, "plasma.charge", x, y, z, 16.0F, 1.0F);
        if (t == C) {
            FxUtil.snd(level, "plasma.fire", x, y, z, 16.0F, 1.0F);
            FxUtil.snd(level, "plasma.beam", x, y, z, 16.0F, 1.0F);
        }
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
        if (t % 3 == 0) {
            FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + r.nextGaussian() * big, y + 1, z + r.nextGaussian() * big, 0, 0.12, 0);
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
        float sg = smooth(t, 0, 50) * (1.0F - smooth(t, E, E + 35));
        if (sg > 0.01F) sigil(vc, m, t, s, sg);

        float rad;
        float yLow;
        if (t < C) {
            float k = t / (float) C;
            k = k * k;
            yLow = lerp(TOP, 0.0F, k);
            rad = (0.5F + 1.5F * k) * s;
        } else {
            yLow = 0.0F;
            rad = 7.0F * s * smooth(t, C, C + 8) * (1.0F - smooth(t, E - 10, E + 5)) * (1.0F + 0.07F * sin(t * 1.9F));
        }

        if (rad > 0.05F) {
            FxGeo.glowBeam(vc, m, 0, yLow - 2, 0, 0, TOP, 0, rad, 1.0F, 0.6F, 0.2F, 1.0F, 14);
            if (t >= C) FxGeo.glowBeam(vc, m, 0, -2, 0, 0, TOP, 0, rad * 2.4F, 0.7F, 0.4F, 1.0F, 0.35F, 14);
        }

        if (t >= C && rad > 0.5F) {
            for (int i = 0; i < 6; i++) {
                float yy = TOP - ((t * 7.0F + i * 70.0F) % TOP);
                FxGeo.ring(vc, m, 0, yy, 0, rad, rad * 2.4F, 24, 1.0F, 0.75F, 0.4F, 0.55F, 0.0F);
            }
            for (int j = 0; j < 6; j++) {
                Random rr = new Random(seed * 131L + (long) (t / 3.0F) * 17L + j * 7919L);
                float[] xs = new float[17];
                float[] ys = new float[17];
                float[] zs = new float[17];
                float py = 30.0F + rr.nextFloat() * 260.0F;
                float ang = rr.nextFloat() * TAU;
                xs[0] = 0;
                ys[0] = py;
                zs[0] = 0;
                for (int k = 1; k < 17; k++) {
                    ang += (rr.nextFloat() - 0.5F) * 0.9F;
                    xs[k] = xs[k - 1] + cos(ang) * 2.6F * s;
                    zs[k] = zs[k - 1] + sin(ang) * 2.6F * s;
                    ys[k] = ys[k - 1] - (1.2F + rr.nextFloat() * 1.8F);
                }
                FxGeo.bolt(vc, m, xs, ys, zs, 17, 0.5F, 0.9F, 0.8F, 1.0F, 0.95F);
                FxGeo.bolt(vc, m, xs, ys, zs, 17, 1.6F, 0.6F, 0.4F, 1.0F, 0.25F);
            }
        }

        float since = t - C;
        float maxR = 230.0F * s;
        for (int k = 0; k < 3; k++) {
            float rr = (since - k * 9.0F) * 3.4F * s;
            if (rr > 1.0F && rr < maxR) {
                float fade = 1.0F - rr / maxR;
                float w = 2.0F + rr * 0.04F;
                FxGeo.ring(vc, m, 0, 0.8F + k, 0, rr - w, rr, 48, 1.0F, 0.8F, 0.5F, 0.0F, 0.7F * fade);
                FxGeo.ring(vc, m, 0, 0.8F + k, 0, rr, rr + w, 48, 1.0F, 0.8F, 0.5F, 0.7F * fade, 0.0F);
                FxGeo.tube(vc, m, 0, 0.5F, 0, 0, 14.0F + rr * 0.05F, 0, rr, rr + 4.0F, 48, 1.0F, 0.7F, 0.4F, 0.45F * fade, 0.0F);
            }
        }

        float dk = smooth(t, C, C + 30);
        float da = 0.28F * (1.0F - smooth(t, C + 20, C + 70));
        if (dk > 0.0F && da > 0.01F) {
            FxGeo.sphere(vc, m, 0, 0, 0, 18.0F * s * dk, 6, 16, 1.0F, 1.0F, 0.9F, da * 1.4F);
            FxGeo.sphere(vc, m, 0, 0, 0, 34.0F * s * dk, 8, 20, 1.0F, 0.55F, 0.2F, da);
        }
    }

    private void sigil(VertexConsumer vc, Matrix4f m, float t, float s, float sg) {
        FxGeo.ring(vc, m, 0, 0.15F, 0, 26 * s, 28 * s, 48, 1.0F, 0.62F, 0.2F, 0.6F * sg, 0.6F * sg);
        FxGeo.ring(vc, m, 0, 0.15F, 0, 18 * s, 19.2F * s, 40, 0.65F, 0.35F, 1.0F, 0.5F * sg, 0.5F * sg);
        FxGeo.ring(vc, m, 0, 0.15F, 0, 10 * s, 11 * s, 32, 1.0F, 0.75F, 0.4F, 0.45F * sg, 0.45F * sg);
        for (int i = 0; i < 36; i++) {
            float a = i * TAU / 36 + t * 0.012F;
            FxGeo.sector(vc, m, 0, 0.16F, 0, 20.5F * s, 24.5F * s, a, a + 0.045F, 1.0F, 0.7F, 0.25F, 0.55F * sg, 0.55F * sg);
        }
        for (int i = 0; i < 12; i++) {
            float a = -i * TAU / 12 - t * 0.02F;
            FxGeo.sector(vc, m, 0, 0.16F, 0, 12.5F * s, 16.5F * s, a, a + 0.09F, 0.65F, 0.35F, 1.0F, 0.5F * sg, 0.5F * sg);
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.5F * smooth(t, 20, C);
        if (t >= C) a += 2.6F * (float) Math.exp(-(t - C) / 40.0F) + 1.0F * (1.0F - smooth(t, 150, E));
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
    public int tint(float t) {
        int al = (int) (70.0F * smooth(t, 30, C + 10) * (1.0F - smooth(t, E, D)));
        return (al << 24) | 0x6A2DB5;
    }
}
