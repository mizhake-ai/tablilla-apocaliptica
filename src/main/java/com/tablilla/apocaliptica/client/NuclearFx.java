package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.lerp;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.NuclearStrike;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/** Hongo Nuclear: bomba que cae, destello cegador, hongo enorme que rueda sobre si mismo, onda sonica y barrido de devastacion. */
public class NuclearFx implements FxEffect {
    private static final int DET = NuclearStrike.DET;
    private static final int D = NuclearStrike.DURATION;

    private GroundMap ground;
    private List<Debris.Proto> protos;
    private boolean boom = false;

    @Override
    public int duration() {
        return D;
    }

    private static float stemH(float t, float q) {
        return 320.0F * q * smooth(t, DET, DET + 260);
    }

    private static float capR(float t, float q) {
        return 120.0F * q * (float) Math.pow(smooth(t, DET + 20, DET + 280), 0.8);
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        float q = s;
        if (t == 1) {
            float half = Math.min(280.0F, 270.0F * s);
            ground = new GroundMap(level, x, y, z, half, Math.max(5.0F, half / 45.0F));
            protos = Debris.sample(level, x, z, 10.0 * s, 160.0 * s, 300, 0.5F, r);
            FxUtil.snd(level, "meteor.approach", x, y, z, 16.0F, 0.9F);
        }
        // el sonido llega tarde, segun la distancia
        Minecraft mc = Minecraft.getInstance();
        double pd = mc.player == null ? 0.0 : Math.sqrt(mc.player.distanceToSqr(x, y, z));
        int delay = (int) Math.min(120.0, pd / 6.0);
        if (t == DET + delay) {
            FxUtil.snd(level, "mothership.boom", x, y, z, 16.0F, 0.6F);
            FxUtil.snd(level, "meteor.impact", x, y, z, 16.0F, 0.6F);
            FxUtil.snd(level, "plasma.collapse", x, y, z, 16.0F, 0.7F);
        }
        if (t == DET + delay + 20) FxUtil.snd(level, "plasma.rumble", x, y, z, 16.0F, 0.7F);
        if (t == DET + 150) FxUtil.snd(level, "meteor.rumble", x, y, z, 16.0F, 0.8F);

        // la bomba cae
        if (t >= 20 && t < DET) {
            float k = (t - 20) / (float) (DET - 20);
            double by = y + lerp(900.0F, 40.0F * q, k * k);
            for (int i = 0; i < 6; i++) {
                FxUtil.p(level, ParticleTypes.FLAME, x + r.nextGaussian() * 2, by + i * 8 + r.nextDouble() * 8, z + r.nextGaussian() * 2, 0, 0.1, 0);
                FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + r.nextGaussian() * 3, by + i * 14, z + r.nextGaussian() * 3, 0, 0.02, 0);
            }
        }

        if (t == DET) {
            if (protos != null) Debris.burst(protos, x, y, z, 3.6, 3.4, 30, 1.2F, 3.0F, 115.0 * s, 26.0 * s, r);
            BlockState[] hot = {Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.GLASS.defaultBlockState()};
            Debris.burst(Debris.synthetic(hot, x, y, z, 25.0 * s, 100, r), x, y, z, 2.0, 4.0, 8, 1.5F, 4.0F, 40.0 * s, 26.0 * s, r);
            for (int i = 0; i < 40; i++) {
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, x + r.nextGaussian() * 40 * q, y + 4 + r.nextDouble() * 60, z + r.nextGaussian() * 40 * q, 0, 0, 0);
            }
        }
        int since = t - DET;
        if (since > 1 && since < 24) Debris.ripple(level, x, z, since * 14.0 * s, 10.0 * s, 28, r);

        // el hongo: humo oscuro con forma de tallo y sombrero que rueda sobre si mismo
        if (t > DET && t < DET + 520) {
            float life = smooth(t, DET, DET + 60) * (1.0F - smooth(t, DET + 380, DET + 520));
            float sh = stemH(t, q);
            float cr = capR(t, q);
            int n = (int) (60 * life);
            for (int i = 0; i < n; i++) {
                if (r.nextFloat() < 0.5F) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double phi = r.nextDouble() * Math.PI * 2;
                    double minor = 46.0 * q * smooth(t, DET + 20, DET + 280);
                    double rad = cr + minor * Math.cos(phi);
                    double px = x + Math.cos(a) * rad;
                    double pz = z + Math.sin(a) * rad;
                    double py = y + sh + 22.0 * q + minor * 0.8 * Math.sin(phi);
                    double roll = Math.sin(phi) * 0.22;
                    FxUtil.p(level, ParticleTypes.LARGE_SMOKE, px, py, pz, Math.cos(a) * roll, Math.cos(phi) * 0.18, Math.sin(a) * roll);
                    if (i % 4 == 0 && t < DET + 260) FxUtil.p(level, ParticleTypes.FLAME, px, py, pz, 0, 0.1, 0);
                } else {
                    double h = r.nextDouble() * sh;
                    double w = (22.0 + 16.0 * (1.0 - h / Math.max(1.0, sh))) * q;
                    double a = r.nextDouble() * Math.PI * 2;
                    double dd = Math.sqrt(r.nextDouble()) * w;
                    FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + Math.cos(a) * dd, y + h, z + Math.sin(a) * dd, 0, 0.25, 0);
                    if (i % 3 == 0) FxUtil.p(level, ParticleTypes.LARGE_SMOKE, x + Math.cos(a) * dd, y + h, z + Math.sin(a) * dd, 0, 0.3, 0);
                }
            }
            // polvo que sube por el tallo desde el suelo
            for (int i = 0; i < 8; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double dd = r.nextDouble() * 70.0 * q;
                FxUtil.p(level, ParticleTypes.CLOUD, x + Math.cos(a) * dd, y + 2, z + Math.sin(a) * dd, Math.cos(a) * 0.3, 0.4, Math.sin(a) * 0.3);
            }
        }
        if (t == D - 60) FxUtil.snd(level, "meteor.shutdown", x, y, z, 16.0F, 1.0F);
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float q = s;
        // la bomba: estela blanca que se enfria y luego cae cada vez mas rapido
        if (t >= 20 && t < DET) {
            float k = (t - 20) / (float) (DET - 20);
            float by = lerp(900.0F, 40.0F * q, k * k);
            float heat = 1.0F - 0.6F * smooth(t, 60, 140);
            FxGeo.tube(vc, m, 0, by, 0, 0, by + 260.0F, 0, 3.0F * q, 9.0F * q, 10, 1.0F, 0.6F + 0.3F * heat, 0.3F * heat + 0.1F, 0.8F, 0.0F);
            FxGeo.sphere(vc, m, 0, by, 0, 6.0F * q, 5, 8, 1.0F, 0.9F, 0.7F, 0.9F);
        }
        if (t < DET) return;
        float since = t - DET;

        // destello y bola de fuego
        float fk = smooth(t, DET, DET + 50);
        float fa = 1.0F - smooth(t, DET + 40, DET + 200);
        if (fk > 0.0F && fa > 0.01F) {
            FxGeo.sphere(vc, m, 0, 40.0F * q, 0, 80.0F * q * fk, 10, 22, 1.0F, 1.0F, 1.0F, 0.85F * fa);
            FxGeo.sphere(vc, m, 0, 40.0F * q, 0, 130.0F * q * fk, 10, 26, 1.0F, 0.75F, 0.35F, 0.45F * fa);
            FxGeo.sphere(vc, m, 0, 40.0F * q, 0, 190.0F * q * fk, 10, 28, 1.0F, 0.45F, 0.1F, 0.22F * fa);
        }

        // el hongo incandescente (se apaga mientras el humo oscuro toma forma)
        float hot = 1.0F - smooth(t, DET + 60, DET + 260);
        if (hot > 0.01F) {
            float sh = stemH(t, q);
            float cr = capR(t, q);
            FxGeo.tube(vc, m, 0, 0, 0, 0, sh, 0, 34.0F * q, 20.0F * q, 14, 1.0F, 0.55F, 0.15F, 0.55F * hot, 0.35F * hot);
            for (int i = 0; i < 14; i++) {
                float a = i * TAU / 14 + t * 0.004F;
                float roll = sin(t * 0.03F + i);
                FxGeo.sphere(vc, m, cos(a) * cr * 0.9F, sh + 20.0F * q + roll * 10.0F * q, sin(a) * cr * 0.9F, 44.0F * q * (0.4F + 0.6F * smooth(t, DET + 20, DET + 200)),
                        6, 10, 1.0F, 0.6F, 0.2F, 0.35F * hot);
            }
            FxGeo.sphere(vc, m, 0, sh + 20.0F * q, 0, cr * 0.8F, 8, 16, 1.0F, 0.7F, 0.3F, 0.22F * hot);
        }

        if (ground == null) return;
        // onda sonica (rapida) y barrido de devastacion (lento)
        float sonic = since * 18.0F * q;
        float sweepR = since * 7.0F * q;
        float maxS = 300.0F * q;
        if (sonic > 1.0F && sonic < maxS) {
            float fade = 1.0F - sonic / maxS;
            int sides = Math.max(64, Math.min(180, (int) (sonic * 0.5F)));
            FxGeo.terrainRing(vc, m, ground, 0, 0, sonic - 10.0F, sonic, sides, 0.9F, 1.0F, 1.0F, 1.0F, 0.0F, 0.8F * fade);
            FxGeo.terrainWall(vc, m, ground, 0, 0, sonic, 36.0F, sides, 0.6F, 0.9F, 0.9F, 1.0F, 0.4F * fade, 0.0F);
        }
        if (sweepR > 1.0F && sweepR < maxS) {
            float fade = 1.0F - sweepR / maxS;
            int sides = Math.max(64, Math.min(180, (int) (sweepR * 0.5F)));
            FxGeo.terrainRing(vc, m, ground, 0, 0, sweepR - 14.0F, sweepR, sides, 0.9F, 1.0F, 0.7F, 0.3F, 0.0F, 0.8F * fade);
            FxGeo.terrainRing(vc, m, ground, 0, 0, sweepR, sweepR + 14.0F, sides, 0.9F, 1.0F, 0.7F, 0.3F, 0.8F * fade, 0.0F);
            FxGeo.terrainWall(vc, m, ground, 0, 0, sweepR, 40.0F + sweepR * 0.12F, sides, 0.6F, 1.0F, 0.6F, 0.25F, 0.55F * fade, 0.0F);
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.0F;
        if (t >= DET) a = 6.0F * (float) Math.exp(-(t - DET) / 70.0F) + 1.2F * (1.0F - smooth(t, DET + 100, DET + 400));
        return Math.min(a, 7.0F) * (1.0F - smooth(t, D - 120, D));
    }

    @Override
    public float flash(float t) {
        if (t >= DET && t < DET + 100) {
            float k = 1.0F - (t - DET) / 100.0F;
            return k * k;
        }
        return 0.0F;
    }

    @Override
    public int tint(float t) {
        // cielo ocre y oscurecido por el hongo
        int al = (int) (135.0F * smooth(t, DET, DET + 80) * (1.0F - smooth(t, D - 260, D - 20)));
        return (al << 24) | 0x4A3010;
    }
}
