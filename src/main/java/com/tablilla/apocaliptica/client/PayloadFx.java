package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.PayloadStrike;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/** Fuego del Cielo: una roca inmensa e incandescente con una estela de humo y fuego, presion, impacto de extincion y nube. */
public class PayloadFx implements FxEffect {
    private static final int START = PayloadStrike.START;
    private static final int PRESSURE = PayloadStrike.PRESSURE;
    private static final int IMPACT = PayloadStrike.IMPACT;
    private static final int D = PayloadStrike.DURATION;
    private static final float HF = 0.75F; // fraccion horizontal de la distancia
    private static final float VF = 0.66F; // fraccion vertical
    private static final Block[] BURN = {Blocks.MAGMA_BLOCK, Blocks.NETHERRACK, Blocks.BLACKSTONE, Blocks.OBSIDIAN};

    private GroundMap ground;
    private RockBody body;
    private List<Debris.Proto> protos;
    private float dx = 1.0F;
    private float dz = 0.0F;

    @Override
    public int duration() {
        return D;
    }

    private float dist(float t) {
        return (float) PayloadStrike.distance(t);
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        float q = s;
        if (t == 1) {
            double az = (seed % 628) / 100.0;
            dx = (float) Math.cos(az);
            dz = (float) Math.sin(az);
            float half = Math.min(280.0F, 260.0F * s);
            ground = new GroundMap(level, x, y, z, half, Math.max(5.0F, half / 45.0F));
            protos = Debris.sample(level, x, z, 15.0 * s, 210.0 * s, 300, 0.5F, r);
            FxUtil.snd(level, "meteor.approach", x, y, z, 16.0F, 0.6F);
        }
        if (t == 140) FxUtil.snd(level, "meteor.approach", x, y, z, 16.0F, 0.8F);
        if (t == PRESSURE - 20) FxUtil.snd(level, "meteor.storm", x, y, z, 16.0F, 0.7F);

        // posicion de la roca
        float d = dist(t);
        double mxp = x + dx * d * HF;
        double myp = y + d * VF;
        double mzp = z + dz * d * HF;
        float d1 = dist(t + 1);
        double vx = dx * (d1 - d) * HF;
        double vy = (d1 - d) * VF;
        double vz = dz * (d1 - d) * HF;

        if (t >= START && t < IMPACT) {
            double bs = 60.0 * q;
            // estela de humo grumoso (queda colgada en el cielo) y fuego
            int n = (int) (6 + 10 * smooth(t, START, IMPACT));
            double vl = Math.max(0.001, Math.sqrt(vx * vx + vy * vy + vz * vz));
            for (int i = 0; i < n; i++) {
                double back = r.nextDouble() * 520.0 * q;
                double ox = -vx / vl * back + r.nextGaussian() * bs * (0.3 + back / (700.0 * q));
                double oy = -vy / vl * back + r.nextGaussian() * bs * (0.3 + back / (700.0 * q));
                double oz = -vz / vl * back + r.nextGaussian() * bs * (0.3 + back / (700.0 * q));
                FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, mxp + ox, myp + oy, mzp + oz, 0, 0.02, 0);
                if (i % 2 == 0) FxUtil.p(level, ParticleTypes.LARGE_SMOKE, mxp + ox, myp + oy, mzp + oz, 0, 0, 0);
                if (back < 160.0 * q) FxUtil.p(level, ParticleTypes.FLAME, mxp + ox * 0.4, myp + oy * 0.4, mzp + oz * 0.4, 0, 0, 0);
            }
            if (t % 5 == 0) {
                // pedazos ardiendo que se desprenden
                BlockState bs2 = BURN[r.nextInt(BURN.length)].defaultBlockState();
                double ox = r.nextGaussian() * bs * 0.7;
                double oy = r.nextGaussian() * bs * 0.7;
                double oz = r.nextGaussian() * bs * 0.7;
                Debris.linear(bs2, mxp + ox, myp + oy, mzp + oz, vx * 0.85 + r.nextGaussian() * 0.5, vy * 0.85 - r.nextDouble() * 0.6,
                        vz * 0.85 + r.nextGaussian() * 0.5, 3.0F + r.nextFloat() * 5.0F, y, 500, r);
            }
        }

        // presion sobre el suelo cuando se acerca
        if (t >= PRESSURE && t < IMPACT) {
            double rr = 150.0 * q * (t - PRESSURE) / (double) (IMPACT - PRESSURE);
            Debris.ripple(level, x, z, rr, 8.0 * s, 22, r);
            for (int i = 0; i < 20; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double dd = r.nextDouble() * rr;
                FxUtil.p(level, ParticleTypes.CLOUD, x + Math.cos(a) * dd, y + 1, z + Math.sin(a) * dd, Math.cos(a) * 0.6, 0.15, Math.sin(a) * 0.6);
            }
        }

        // impacto
        if (t == IMPACT) {
            FxUtil.snd(level, "meteor.impact", x, y, z, 16.0F, 0.7F);
            FxUtil.snd(level, "mothership.boom", x, y, z, 16.0F, 0.6F);
            FxUtil.snd(level, "plasma.collapse", x, y, z, 16.0F, 0.6F);
            if (protos != null) Debris.burst(protos, x, y, z, 3.4, 3.8, 50, 1.5F, 3.6F, 110.0 * s, 100.0 * s, r);
            BlockState[] hot = new BlockState[BURN.length];
            for (int i = 0; i < hot.length; i++) hot[i] = BURN[i].defaultBlockState();
            Debris.burst(Debris.synthetic(hot, x, y, z, 30.0 * s, 120, r), x, y, z, 2.0, 4.0, 10, 2.0F, 6.0F, 60.0 * s, 100.0 * s, r);
            for (int i = 0; i < 40; i++) {
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, x + r.nextGaussian() * 50 * q, y + 2 + r.nextDouble() * 30, z + r.nextGaussian() * 50 * q, 0, 0, 0);
            }
        }
        if (t == IMPACT + 12) FxUtil.snd(level, "plasma.rumble", x, y, z, 16.0F, 0.7F);
        if (t == IMPACT + 100) FxUtil.snd(level, "meteor.rumble", x, y, z, 16.0F, 0.8F);
        int since = t - IMPACT;
        if (since > 1 && since < 110) Debris.ripple(level, x, z, since * 8.0 * s, 8.0 * s, 24, r);

        // columna de humo y fuego que luego se deshace en bruma
        if (t > IMPACT && t < IMPACT + 420) {
            double life = smooth(t, IMPACT, IMPACT + 120) * (1.0 - smooth(t, IMPACT + 300, IMPACT + 420));
            int n = (int) (36 * life);
            for (int i = 0; i < n; i++) {
                double h = r.nextDouble() * 420.0 * q * smooth(t, IMPACT, IMPACT + 140);
                double w = (40.0 + 0.12 * h) * q;
                double a = r.nextDouble() * Math.PI * 2;
                double dd = Math.sqrt(r.nextDouble()) * w;
                FxUtil.p(level, ParticleTypes.LARGE_SMOKE, x + Math.cos(a) * dd, y + h, z + Math.sin(a) * dd, 0, 0.3, 0);
                if (i % 3 == 0) FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + Math.cos(a) * dd, y + h, z + Math.sin(a) * dd, 0, 0.2, 0);
                if (i % 4 == 0 && t < IMPACT + 160) FxUtil.p(level, ParticleTypes.FLAME, x + Math.cos(a) * dd * 0.6, y + h * 0.5, z + Math.sin(a) * dd * 0.6, 0, 0.4, 0);
            }
            if (t < IMPACT + 400 && t % 3 == 0) {
                FxUtil.p(level, ParticleTypes.LAVA, x + r.nextGaussian() * 70 * q, y + 2, z + r.nextGaussian() * 70 * q, 0, 0, 0);
            }
        }
        if (t == D - 60) FxUtil.snd(level, "meteor.shutdown", x, y, z, 16.0F, 1.0F);
    }

    @Override
    public boolean usesHull() {
        return true;
    }

    @Override
    public void renderHull(VertexConsumer vc, PoseStack.Pose pose, float t, long seed, float s) {
        if (t < START || t >= IMPACT) return;
        if (body == null) body = new RockBody(60.0F, seed);
        float d = dist(t);
        float px = dx * d * HF;
        float py = d * VF;
        float pz = dz * d * HF;
        ShipModel.push(pose, px, py, pz, t * 0.011F, t * 0.017F);
        body.draw(vc, pose, s, smooth(t, START, IMPACT));
        ShipModel.pop(pose, px, py, pz, t * 0.011F, t * 0.017F);
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float q = s;
        if (t >= START && t < IMPACT) {
            float d = dist(t);
            float px = dx * d * HF;
            float py = d * VF;
            float pz = dz * d * HF;
            float heat = 0.35F + 0.65F * smooth(t, START, IMPACT - 40);
            float rb = 64.0F * q;
            // halo incandescente y estela de fuego
            FxGeo.sphere(vc, m, px, py, pz, rb * 1.08F, 8, 16, 1.0F, 0.55F, 0.12F, 0.30F * heat);
            FxGeo.sphere(vc, m, px, py, pz, rb * 1.4F, 8, 16, 1.0F, 0.40F, 0.08F, 0.16F * heat);
            FxGeo.sphere(vc, m, px, py, pz, rb * 2.0F, 8, 14, 1.0F, 0.30F, 0.05F, 0.07F * heat);
            float len = 560.0F * q * (0.3F + 0.7F * heat);
            float bx = -dx * HF;
            float by = -VF;
            float bz = -dz * HF;
            FxGeo.tube(vc, m, px, py, pz, px + bx * len, py + by * len, pz + bz * len, rb * 0.9F, rb * 0.15F, 12, 1.0F, 0.75F, 0.3F, 0.55F * heat, 0.0F);
            FxGeo.tube(vc, m, px, py, pz, px + bx * len * 0.7F, py + by * len * 0.7F, pz + bz * len * 0.7F, rb * 1.6F, rb * 0.2F, 12, 1.0F, 0.40F, 0.08F, 0.28F * heat, 0.0F);
        }

        if (ground == null) return;
        float since = t - IMPACT;

        // presion previa y ondas del impacto pegadas al relieve
        if (t >= PRESSURE && t < IMPACT) {
            float rr = 150.0F * q * (t - PRESSURE) / (IMPACT - PRESSURE);
            FxGeo.terrainRing(vc, m, ground, 0, 0, Math.max(0.0F, rr - 8.0F), rr, 96, 0.8F, 1.0F, 0.6F, 0.25F, 0.0F, 0.5F);
        }
        if (since > 0.0F) {
            float maxR = 450.0F * q;
            for (int k = 0; k < 4; k++) {
                float rr = (since - k * 16.0F) * 8.0F * q;
                if (rr > 1.0F && rr < maxR) {
                    float fade = 1.0F - rr / maxR;
                    float w = 4.0F + rr * 0.05F;
                    int sides = Math.max(64, Math.min(180, (int) (rr * 0.5F)));
                    FxGeo.terrainRing(vc, m, ground, 0, 0, rr - w, rr, sides, 0.9F + k, 1.0F, 0.7F, 0.35F, 0.0F, 0.8F * fade);
                    FxGeo.terrainRing(vc, m, ground, 0, 0, rr, rr + w, sides, 0.9F + k, 1.0F, 0.7F, 0.35F, 0.8F * fade, 0.0F);
                    FxGeo.terrainWall(vc, m, ground, 0, 0, rr, 30.0F + rr * 0.14F, sides, 0.6F, 1.0F, 0.55F, 0.25F, 0.6F * fade, 0.0F);
                }
            }
            // bola de fuego y columna
            float fk = smooth(t, IMPACT, IMPACT + 70);
            float fa = 1.0F - smooth(t, IMPACT + 60, IMPACT + 240);
            if (fk > 0.0F && fa > 0.01F) {
                FxGeo.sphere(vc, m, 0, 0, 0, 90.0F * q * fk, 10, 22, 1.0F, 0.95F, 0.8F, 0.55F * fa);
                FxGeo.sphere(vc, m, 0, 0, 0, 150.0F * q * fk, 10, 26, 1.0F, 0.6F, 0.2F, 0.30F * fa);
                FxGeo.sphere(vc, m, 0, 0, 0, 210.0F * q * fk, 10, 28, 1.0F, 0.35F, 0.08F, 0.15F * fa);
                float colH = 420.0F * q * smooth(t, IMPACT, IMPACT + 140);
                FxGeo.glowBeam(vc, m, 0, 0, 0, 0, colH, 0, 50.0F * q * fa, 1.0F, 0.55F, 0.2F, 0.5F * fa, 14);
            }
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.7F * smooth(t, PRESSURE - 30, IMPACT);
        if (t >= IMPACT) a += 7.0F * (float) Math.exp(-(t - IMPACT) / 90.0F) + 1.0F * (1.0F - smooth(t, IMPACT + 100, IMPACT + 400));
        return Math.min(a, 8.0F);
    }

    @Override
    public float flash(float t) {
        if (t >= IMPACT && t < IMPACT + 90) {
            float k = 1.0F - (t - IMPACT) / 90.0F;
            return k * k;
        }
        return 0.0F;
    }

    @Override
    public float flare(float t) {
        return smooth(t, IMPACT, IMPACT + 8) * (1.0F - smooth(t, IMPACT + 60, IMPACT + 140));
    }

    @Override
    public int flareColor() {
        return 0xFFAA55;
    }

    @Override
    public float flareHeight() {
        return 60.0F;
    }

    @Override
    public int tint(float t) {
        // el cielo se oscurece con la nube y las cenizas
        int al = (int) (150.0F * smooth(t, IMPACT, IMPACT + 150) * (1.0F - smooth(t, D - 300, D - 20)));
        return (al << 24) | 0x1C0F08;
    }
}
