package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.lerp;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.GodlyStrike;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Juicio Divino: columna de luz dorada con halos ascendentes, rayos de luz y estrella en el suelo. */
public class GodlyFx implements FxEffect {
    private static final int C = GodlyStrike.CHARGE;
    private static final int E = GodlyStrike.BEAM_END;
    private static final int D = GodlyStrike.DURATION;
    private static final float TOP = 420.0F;

    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0F, 0.82F, 0.3F), 3.0F);

    @Override
    public int duration() {
        return D;
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        if (t == 1) FxUtil.snd(level, "godly.lockon", x, y, z, 16.0F, 1.0F);
        if (t == 6) FxUtil.snd(level, "godly.charge", x, y, z, 16.0F, 1.0F);
        if (t == C) {
            FxUtil.snd(level, "godly.fire", x, y, z, 16.0F, 1.0F);
            FxUtil.snd(level, "godly.beam", x, y, z, 16.0F, 1.0F);
        }
        if (t == C + 14) FxUtil.snd(level, "godly.rumble", x, y, z, 16.0F, 1.0F);
        if (t == C + 40) FxUtil.snd(level, "godly.accent", x, y, z, 16.0F, 1.0F);
        if (t == E - 8) FxUtil.snd(level, "godly.shutdown", x, y, z, 16.0F, 1.0F);

        if (t < C) {
            float k = t / (float) C;
            for (int i = 0; i < 8; i++) {
                double a = t * 0.25 + i * 0.785;
                double rad = 40.0 * s * (1.0 - k) + 3.0;
                double c = Math.cos(a);
                double sn = Math.sin(a);
                FxUtil.p(level, ParticleTypes.END_ROD, x + c * rad, y + 0.3 + r.nextDouble() * 3, z + sn * rad, -c * 0.3, 0.2, -sn * 0.3);
                FxUtil.p(level, GOLD, x + c * rad, y + 0.5, z + sn * rad, -c * 0.2, 0.15, -sn * 0.2);
            }
        } else if (t < E) {
            double big = 6.0 * s;
            for (int i = 0; i < 10; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = r.nextDouble() * big * 2.0;
                FxUtil.p(level, ParticleTypes.END_ROD, x + Math.cos(a) * d, y + 0.3, z + Math.sin(a) * d, 0, 0.5 + r.nextDouble() * 0.7, 0);
                FxUtil.p(level, GOLD, x + Math.cos(a) * d, y + 0.3, z + Math.sin(a) * d, 0, 0.4 + r.nextDouble() * 0.5, 0);
            }
            for (int i = 0; i < 4; i++) {
                FxUtil.p(level, ParticleTypes.WAX_ON, x + r.nextGaussian() * big, y + r.nextDouble() * 40, z + r.nextGaussian() * big, 0, 0.1, 0);
            }
            if (t % 6 == 0) {
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, x + r.nextGaussian() * big * 2, y + 1, z + r.nextGaussian() * big * 2, 0, 0, 0);
            }
            int bt = t - C;
            if (bt <= 55) {
                double rr = bt * 3.4 * s;
                int n = (int) Math.min(70, rr * 0.7 + 10);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    FxUtil.p(level, ParticleTypes.END_ROD, x + Math.cos(a) * rr, y + 0.5, z + Math.sin(a) * rr, Math.cos(a) * 0.3, 0.1, Math.sin(a) * 0.3);
                }
            }
        }
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float sg = smooth(t, 0, 50) * (1.0F - smooth(t, E, E + 35));
        if (sg > 0.01F) {
            FxGeo.ring(vc, m, 0, 0.15F, 0, 30 * s, 32 * s, 48, 1.0F, 0.85F, 0.35F, 0.6F * sg, 0.6F * sg);
            FxGeo.ring(vc, m, 0, 0.15F, 0, 20 * s, 21.2F * s, 40, 1.0F, 0.95F, 0.7F, 0.5F * sg, 0.5F * sg);
            for (int i = 0; i < 8; i++) {
                float a = i * TAU / 8 + t * 0.01F;
                FxGeo.sector(vc, m, 0, 0.17F, 0, 0, 56 * s, a - 0.035F, a + 0.035F, 1.0F, 0.9F, 0.5F, 0.7F * sg, 0.0F);
            }
        }

        float rad;
        float yLow;
        if (t < C) {
            float k = t / (float) C;
            yLow = lerp(TOP, 0.0F, k * k);
            rad = (0.6F + 1.6F * k) * s;
        } else {
            yLow = 0.0F;
            rad = 6.0F * s * smooth(t, C, C + 8) * (1.0F - smooth(t, E - 10, E + 5)) * (1.0F + 0.05F * sin(t * 1.5F));
        }
        if (rad > 0.05F) {
            FxGeo.glowBeam(vc, m, 0, yLow - 2, 0, 0, TOP, 0, rad, 1.0F, 0.84F, 0.35F, 1.0F, 14);
            if (t >= C) {
                FxGeo.glowBeam(vc, m, 0, -2, 0, 0, TOP, 0, rad * 2.8F, 1.0F, 0.9F, 0.6F, 0.3F, 14);
                // rayos de luz abriendose hacia el suelo
                float ca = 0.20F * smooth(t, C, C + 20) * (1.0F - smooth(t, E - 10, E + 20));
                FxGeo.tube(vc, m, 0, 0, 0, 0, TOP, 0, 70.0F * s, 1.0F, 24, 1.0F, 0.9F, 0.55F, ca, ca * 0.2F);
            }
        }

        if (t >= C && rad > 0.5F) {
            for (int i = 0; i < 5; i++) {
                float yy = ((t * 4.0F + i * 84.0F) % TOP);
                float fade = 1.0F - yy / TOP;
                FxGeo.ring(vc, m, 0, yy, 0, 24.0F * s * (0.6F + 0.4F * fade), 28.0F * s * (0.6F + 0.4F * fade), 48,
                        1.0F, 0.9F, 0.5F, 0.65F * fade, 0.65F * fade);
                for (int k = 0; k < 12; k++) {
                    float a = k * TAU / 12 + t * 0.03F * (i % 2 == 0 ? 1 : -1);
                    FxGeo.sector(vc, m, 0, yy + 0.3F, 0, 19.0F * s, 23.0F * s, a, a + 0.08F, 1.0F, 1.0F, 0.8F, 0.7F * fade, 0.7F * fade);
                }
            }
        }

        float since = t - C;
        float maxR = 200.0F * s;
        for (int k = 0; k < 3; k++) {
            float rr = (since - k * 10.0F) * 3.2F * s;
            if (rr > 1.0F && rr < maxR) {
                float fade = 1.0F - rr / maxR;
                float w = 2.0F + rr * 0.04F;
                FxGeo.ring(vc, m, 0, 0.8F + k, 0, rr - w, rr, 48, 1.0F, 0.92F, 0.6F, 0.0F, 0.7F * fade);
                FxGeo.ring(vc, m, 0, 0.8F + k, 0, rr, rr + w, 48, 1.0F, 0.92F, 0.6F, 0.7F * fade, 0.0F);
                FxGeo.tube(vc, m, 0, 0.5F, 0, 0, 12.0F + rr * 0.04F, 0, rr, rr + 3.0F, 48, 1.0F, 0.9F, 0.5F, 0.4F * fade, 0.0F);
            }
        }

        float dk = smooth(t, C, C + 26);
        float da = 0.3F * (1.0F - smooth(t, C + 16, C + 66));
        if (dk > 0.0F && da > 0.01F) {
            FxGeo.sphere(vc, m, 0, 0, 0, 16.0F * s * dk, 6, 16, 1.0F, 1.0F, 1.0F, da * 1.5F);
            FxGeo.sphere(vc, m, 0, 0, 0, 32.0F * s * dk, 8, 20, 1.0F, 0.88F, 0.45F, da);
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.35F * smooth(t, 20, C);
        if (t >= C) a += 1.6F * (float) Math.exp(-(t - C) / 35.0F) + 0.5F * (1.0F - smooth(t, 140, E));
        return a * (1.0F - smooth(t, E, D));
    }

    @Override
    public float flash(float t) {
        if (t >= C && t < C + 18) {
            float k = 1.0F - (t - C) / 18.0F;
            return k * k;
        }
        return 0.0F;
    }

    @Override
    public int tint(float t) {
        int al = (int) (55.0F * smooth(t, 30, C + 10) * (1.0F - smooth(t, E, D)));
        return (al << 24) | 0xFFD670;
    }
}
