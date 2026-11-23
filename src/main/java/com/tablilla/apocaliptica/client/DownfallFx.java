package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.lerp;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.DownfallStrike;
import java.util.List;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * La Caida: la nave colosal, ya en llamas y destrozada, cae del cielo dejando una estela de fuego y humo y soltando
 * pedazos de casco; se estrella, arde un rato y termina estallando.
 */
public class DownfallFx implements FxEffect {
    private static final int FALL = DownfallStrike.FALL_START;
    private static final int CRASH = DownfallStrike.CRASH;
    private static final int EXPLODE = DownfallStrike.EXPLODE;
    private static final int D = DownfallStrike.DURATION;

    private static final Block[] SHIP_BLOCKS = {Blocks.DEEPSLATE_TILES, Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.BLACKSTONE,
            Blocks.IRON_BLOCK, Blocks.POLISHED_DEEPSLATE, Blocks.GRAY_CONCRETE};

    private final ShipModel ship = new ShipModel();
    private GroundMap ground;
    private List<Debris.Proto> protos;
    private List<Debris.Proto> protos2;

    @Override
    public int duration() {
        return D;
    }

    private static float prog(float t) {
        return FxGeo.clamp01((t - FALL) / (float) (CRASH - FALL));
    }

    private static float px(float t) {
        float k = (float) Math.pow(prog(t), 1.6);
        return lerp(-700.0F, 0.0F, k);
    }

    private static float pz(float t) {
        float k = (float) Math.pow(prog(t), 1.6);
        return lerp(160.0F, 0.0F, k);
    }

    private static float py(float t, float q) {
        float k = (float) Math.pow(prog(t), 1.6);
        float y = lerp(700.0F, 30.0F * q, k);
        if (t > CRASH) y -= 24.0F * q * smooth(t, CRASH, CRASH + 70);
        return y;
    }

    private static float tilt(float t) {
        float k = prog(t);
        float a = -(0.50F - 0.30F * k);
        if (t > CRASH) a = -0.20F - 0.03F * sin(t * 0.5F) * (float) Math.exp(-(t - CRASH) / 30.0F);
        return a;
    }

    private static float roll(float t) {
        return 0.12F * sin(t * 0.04F) * (1.0F - prog(t));
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        if (t == 1) {
            float half = Math.min(280.0F, 250.0F * s);
            ground = new GroundMap(level, x, y, z, half, Math.max(4.5F, half / 45.0F));
            protos = Debris.sample(level, x, z, 4.0 * s, 100.0 * s, 300, 0.5F, r);
            FxUtil.snd(level, "mothership.arrival", x, y, z, 16.0F, 1.0F);
        }
        if (t == FALL + 60) FxUtil.snd(level, "mothership.rumble", x, y, z, 16.0F, 1.0F);
        if (t == FALL + 140) FxUtil.snd(level, "meteor.approach", x, y, z, 16.0F, 1.0F);
        if (t == EXPLODE - 1) protos2 = Debris.sample(level, x, z, 20.0 * s, 130.0 * s, 260, 0.4F, r);

        float q = s;
        float tt = t;
        double sxp = x + px(tt);
        double syp = y + py(tt, q);
        double szp = z + pz(tt);

        // ---------------------------------------------------------- caida
        if (t >= FALL && t < CRASH) {
            double vx = px(tt + 1) - px(tt);
            double vy = py(tt + 1, q) - py(tt, q);
            double vz = pz(tt + 1) - pz(tt);
            double vl = Math.max(0.001, Math.sqrt(vx * vx + vy * vy + vz * vz));
            for (int i = 0; i < 12; i++) {
                double ox = r.nextGaussian() * 45.0 * q;
                double oz = r.nextGaussian() * 45.0 * q;
                double oy = -22.0 * q + r.nextGaussian() * 8.0 * q;
                FxUtil.p(level, ParticleTypes.FLAME, sxp + ox, syp + oy, szp + oz, 0, 0.1, 0);
                FxUtil.p(level, ParticleTypes.LARGE_SMOKE, sxp + ox, syp + oy, szp + oz, 0, 0.05, 0);
                if (i % 3 == 0) FxUtil.p(level, ParticleTypes.LAVA, sxp + ox, syp + oy, szp + oz, 0, 0, 0);
            }
            for (int i = 0; i < 4; i++) {
                double back = r.nextDouble() * 260.0;
                FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, sxp - vx / vl * back + r.nextGaussian() * 15, syp - vy / vl * back + r.nextGaussian() * 15,
                        szp - vz / vl * back + r.nextGaussian() * 15, 0, 0.1, 0);
            }
            // pedazos de casco que se desprenden y caen
            if (t >= FALL + 20 && t % 6 == 0) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = (30.0 + r.nextDouble() * 80.0) * q;
                double fx = sxp + Math.cos(a) * d;
                double fz = szp + Math.sin(a) * d;
                double fy = syp - 10.0 * q;
                double gx = fx - x;
                double gz = fz - z;
                float gh = 0.0F;
                if (ground != null && Math.abs(gx) < 240.0 && Math.abs(gz) < 240.0) gh = ground.at(gx, gz);
                BlockState st = SHIP_BLOCKS[r.nextInt(SHIP_BLOCKS.length)].defaultBlockState();
                Debris.linear(st, fx, fy, fz, vx * 0.7 + r.nextGaussian() * 0.5, -1.6 - r.nextDouble() * 1.4 + vy * 0.3,
                        vz * 0.7 + r.nextGaussian() * 0.5, 3.0F + r.nextFloat() * 4.0F, y + gh, 400, r);
            }
        }

        // ---------------------------------------------------------- choque
        if (t == CRASH) {
            FxUtil.snd(level, "meteor.impact", x, y, z, 16.0F, 1.0F);
            FxUtil.snd(level, "mothership.boom", x, y, z, 16.0F, 1.0F);
            FxUtil.snd(level, "plasma.collapse", x, y, z, 16.0F, 1.0F);
            if (protos != null) Debris.burst(protos, x, y, z, 2.4, 2.8, 30, 1.0F, 2.4F, 62.0 * s, 30.0 * s, r);
            BlockState[] parts = new BlockState[SHIP_BLOCKS.length + 2];
            for (int i = 0; i < SHIP_BLOCKS.length; i++) parts[i] = SHIP_BLOCKS[i].defaultBlockState();
            parts[SHIP_BLOCKS.length] = Blocks.MAGMA_BLOCK.defaultBlockState();
            parts[SHIP_BLOCKS.length + 1] = Blocks.OBSIDIAN.defaultBlockState();
            Debris.burst(Debris.synthetic(parts, x, y, z, 14.0 * s, 100, r), x, y, z, 1.5, 3.4, 8, 1.5F, 4.0F, 30.0 * s, 30.0 * s, r);
            for (int i = 0; i < 14; i++) {
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, x + r.nextGaussian() * 30 * q, y + 2, z + r.nextGaussian() * 30 * q, 0, 0, 0);
            }
            for (int i = 0; i < 120; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double b = r.nextDouble() * Math.PI * 0.5;
                double sp = 1.5 + r.nextDouble() * 2.5;
                FxUtil.p(level, ParticleTypes.FLAME, x, y + 2, z, Math.cos(a) * Math.cos(b) * sp, Math.sin(b) * sp, Math.sin(a) * Math.cos(b) * sp);
            }
        }
        int sc = t - CRASH;
        if (sc >= 1 && sc <= 50) {
            double rr = sc * 6.0 * q;
            int n = (int) Math.min(110, rr * 0.6 + 10);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                FxUtil.p(level, ParticleTypes.CLOUD, x + Math.cos(a) * rr, y + 0.5, z + Math.sin(a) * rr, Math.cos(a) * 0.5, 0.1, Math.sin(a) * 0.5);
                if (i % 4 == 0) FxUtil.p(level, ParticleTypes.FLAME, x + Math.cos(a) * rr, y + 0.5, z + Math.sin(a) * rr, Math.cos(a) * 0.3, 0.3, Math.sin(a) * 0.3);
            }
        }

        // ---------------------------------------------------------- restos ardiendo
        if (t > CRASH && t < EXPLODE) {
            for (int i = 0; i < 10; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(r.nextDouble()) * 90.0 * q;
                double fy = y + 10.0 * q + r.nextDouble() * 30.0 * q;
                FxUtil.p(level, ParticleTypes.FLAME, x + Math.cos(a) * d, fy, z + Math.sin(a) * d, 0, 0.2, 0);
                FxUtil.p(level, ParticleTypes.LARGE_SMOKE, x + Math.cos(a) * d, fy, z + Math.sin(a) * d, 0, 0.3, 0);
                if (i % 3 == 0) FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + Math.cos(a) * d, fy, z + Math.sin(a) * d, 0, 0.15, 0);
            }
            if (t % 22 == 0) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(r.nextDouble()) * 80.0 * q;
                double ex = x + Math.cos(a) * d;
                double ez = z + Math.sin(a) * d;
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, ex, y + 20.0 * q, ez, 0, 0, 0);
                FxUtil.snd(level, (t / 22) % 2 == 0 ? "meteor.hit1" : "meteor.hit2", ex, y + 20.0 * q, ez, 12.0F, 0.8F);
            }
        }

        // ---------------------------------------------------------- explosion final
        if (t == EXPLODE) {
            FxUtil.snd(level, "mothership.boom", x, y, z, 16.0F, 0.8F);
            FxUtil.snd(level, "mothership.fire", x, y, z, 16.0F, 0.8F);
            FxUtil.snd(level, "meteor.impact", x, y, z, 16.0F, 0.8F);
            FxUtil.snd(level, "plasma.collapse", x, y, z, 16.0F, 0.9F);
            if (protos2 != null) Debris.burst(protos2, x, y, z, 2.8, 3.0, 40, 1.0F, 2.6F, 96.0 * s, 48.0 * s, r);
            BlockState[] parts = new BlockState[SHIP_BLOCKS.length];
            for (int i = 0; i < parts.length; i++) parts[i] = SHIP_BLOCKS[i].defaultBlockState();
            Debris.burst(Debris.synthetic(parts, x, y + 10.0 * q, z, 40.0 * s, 120, r), x, y, z, 2.0, 3.6, 10, 2.0F, 5.0F, 60.0 * s, 48.0 * s, r);
            for (int i = 0; i < 20; i++) {
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, x + r.nextGaussian() * 45 * q, y + 4 + r.nextDouble() * 30 * q, z + r.nextGaussian() * 45 * q, 0, 0, 0);
            }
            for (int i = 0; i < 160; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double b = r.nextDouble() * Math.PI * 0.5;
                double sp = 2.0 + r.nextDouble() * 3.0;
                FxUtil.p(level, ParticleTypes.FLAME, x, y + 6, z, Math.cos(a) * Math.cos(b) * sp, Math.sin(b) * sp, Math.sin(a) * Math.cos(b) * sp);
            }
        }
        int se = t - EXPLODE;
        if (se >= 1 && se <= 60) {
            double rr = se * 6.5 * q;
            int n = (int) Math.min(130, rr * 0.5 + 10);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                FxUtil.p(level, ParticleTypes.CLOUD, x + Math.cos(a) * rr, y + 0.5, z + Math.sin(a) * rr, Math.cos(a) * 0.5, 0.1, Math.sin(a) * 0.5);
            }
        }
        if (t > EXPLODE && t < D - 30) {
            for (int i = 0; i < 6; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = Math.sqrt(r.nextDouble()) * 70.0 * q;
                FxUtil.p(level, ParticleTypes.LARGE_SMOKE, x + Math.cos(a) * d, y + 2, z + Math.sin(a) * d, 0, 0.3, 0);
                if (i % 2 == 0) FxUtil.p(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + Math.cos(a) * d, y + 2, z + Math.sin(a) * d, 0, 0.15, 0);
                if (i % 3 == 0) FxUtil.p(level, ParticleTypes.FLAME, x + Math.cos(a) * d, y + 1, z + Math.sin(a) * d, 0, 0.1, 0);
            }
        }
        if (t == D - 60) FxUtil.snd(level, "meteor.shutdown", x, y, z, 16.0F, 1.0F);
    }

    // ------------------------------------------------------------------ nave

    @Override
    public boolean usesHull() {
        return true;
    }

    @Override
    public void renderHull(VertexConsumer vc, PoseStack.Pose pose, float t, long seed, float s) {
        if (t >= EXPLODE) return;
        float burn = 0.85F * smooth(t, FALL, FALL + 100);
        ShipModel.push(pose, px(t), py(t, s), pz(t), tilt(t), roll(t));
        ship.hull(vc, pose, seed, s, burn);
        ShipModel.pop(pose, px(t), py(t, s), pz(t), tilt(t), roll(t));
    }

    private void shock(VertexConsumer vc, Matrix4f m, float since, float q, float maxR, float speed, float cr, float cg, float cb) {
        for (int k = 0; k < 4; k++) {
            float rr = (since - k * 14.0F) * speed * q;
            if (rr > 1.0F && rr < maxR) {
                float fade = 1.0F - rr / maxR;
                float w = 3.0F + rr * 0.05F;
                int sides = Math.max(48, Math.min(160, (int) (rr * 0.6F)));
                FxGeo.terrainRing(vc, m, ground, 0, 0, rr - w, rr, sides, 0.9F + k, cr, cg, cb, 0.0F, 0.75F * fade);
                FxGeo.terrainRing(vc, m, ground, 0, 0, rr, rr + w, sides, 0.9F + k, cr, cg, cb, 0.75F * fade, 0.0F);
                FxGeo.terrainWall(vc, m, ground, 0, 0, rr, 22.0F + rr * 0.12F, sides, 0.6F, cr, cg * 0.8F, cb * 0.7F, 0.55F * fade, 0.0F);
            }
        }
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float q = s;
        if (t < EXPLODE) {
            float x = px(t);
            float y = py(t, q);
            float z = pz(t);
            float burn = smooth(t, FALL, FALL + 100);

            // estela de fuego y humo detras de la nave
            if (t < CRASH + 20) {
                float fade = 1.0F - smooth(t, CRASH - 10, CRASH + 20);
                float dx = -700.0F;
                float dy = 700.0F - 30.0F * q;
                float dz = 160.0F;
                float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= dl;
                dy /= dl;
                dz /= dl;
                float len = 380.0F * smooth(t, FALL, FALL + 40);
                FxGeo.tube(vc, m, x, y - 10.0F * q, z, x + dx * len, y - 10.0F * q + dy * len, z + dz * len, 62.0F * q, 6.0F * q, 14, 1.0F, 0.45F, 0.1F, 0.30F * fade, 0.0F);
                FxGeo.tube(vc, m, x, y - 10.0F * q, z, x + dx * len * 0.7F, y - 10.0F * q + dy * len * 0.7F, z + dz * len * 0.7F, 34.0F * q, 3.0F * q, 12, 1.0F, 0.75F, 0.3F, 0.45F * fade, 0.0F);
            }

            // luces y llamas sobre el casco (centrado en la nave, inclinado)
            ShipModel.push(m, x, y, z, tilt(t), roll(t));
            ship.lights(vc, m, t, q, 1.0F, 0.3F, 0.1F, 1.0F + 1.2F * burn, burn);
            Random fr = new Random(seed * 3L + 5L);
            for (int i = 0; i < 14; i++) {
                float a = fr.nextFloat() * TAU;
                float rr = 20.0F + fr.nextFloat() * 85.0F;
                float fl = 0.55F + 0.45F * sin(t * 0.3F + i * 2.1F);
                float fy = ShipModel.topY(rr) * q + 4.0F * q;
                float rad = (6.0F + fr.nextFloat() * 9.0F) * q * fl * burn;
                FxGeo.sphere(vc, m, cos(a) * rr * q, fy, sin(a) * rr * q, rad, 6, 10, 1.0F, 0.55F, 0.12F, 0.40F);
                FxGeo.sphere(vc, m, cos(a) * rr * q, fy, sin(a) * rr * q, rad * 0.5F, 6, 10, 1.0F, 0.9F, 0.5F, 0.55F);
            }
            ShipModel.pop(m, x, y, z, tilt(t), roll(t));
        }

        // bolas de fuego del choque y de la explosion final
        float fk = smooth(t, CRASH, CRASH + 60);
        float fa = 1.0F - smooth(t, CRASH + 40, CRASH + 150);
        if (t < EXPLODE && fk > 0.0F && fa > 0.01F) {
            FxGeo.sphere(vc, m, 0, 0, 0, 46.0F * q * fk, 8, 20, 1.0F, 0.95F, 0.8F, 0.5F * fa);
            FxGeo.sphere(vc, m, 0, 0, 0, 78.0F * q * fk, 8, 24, 1.0F, 0.6F, 0.2F, 0.3F * fa);
            FxGeo.sphere(vc, m, 0, 0, 0, 110.0F * q * fk, 8, 28, 1.0F, 0.35F, 0.08F, 0.15F * fa);
        }
        float ek = smooth(t, EXPLODE, EXPLODE + 50);
        float ea = 1.0F - smooth(t, EXPLODE + 50, EXPLODE + 170);
        if (ek > 0.0F && ea > 0.01F) {
            FxGeo.sphere(vc, m, 0, 10.0F * q, 0, 70.0F * q * ek, 10, 22, 1.0F, 0.97F, 0.85F, 0.55F * ea);
            FxGeo.sphere(vc, m, 0, 10.0F * q, 0, 120.0F * q * ek, 10, 26, 1.0F, 0.65F, 0.25F, 0.32F * ea);
            FxGeo.sphere(vc, m, 0, 10.0F * q, 0, 180.0F * q * ek, 10, 30, 1.0F, 0.35F, 0.08F, 0.16F * ea);
        }

        if (ground != null) {
            if (t > CRASH && t < EXPLODE + 200) shock(vc, m, t - CRASH, q, 260.0F * q, 6.0F, 1.0F, 0.75F, 0.4F);
            if (t > EXPLODE) shock(vc, m, t - EXPLODE, q, 320.0F * q, 6.8F, 1.0F, 0.7F, 0.35F);
            // resplandor de brasas en el suelo alrededor de los restos
            float glowFloor = smooth(t, CRASH, CRASH + 80) * (1.0F - smooth(t, EXPLODE + 100, EXPLODE + 260));
            if (glowFloor > 0.01F) {
                FxGeo.terrainRing(vc, m, ground, 0, 0, 0.0F, 110.0F * q, 48, 0.6F, 1.0F, 0.45F, 0.12F, 0.30F * glowFloor, 0.08F * glowFloor);
            }
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.6F * smooth(t, 150, CRASH);
        if (t >= CRASH && t < EXPLODE) a += 4.0F * (float) Math.exp(-(t - CRASH) / 45.0F) + 0.5F;
        if (t >= EXPLODE) a += 5.0F * (float) Math.exp(-(t - EXPLODE) / 50.0F);
        return a * (1.0F - smooth(t, EXPLODE + 120, D));
    }

    @Override
    public float flash(float t) {
        if (t >= CRASH && t < CRASH + 20) {
            float k = 1.0F - (t - CRASH) / 20.0F;
            return 0.9F * k * k;
        }
        if (t >= EXPLODE && t < EXPLODE + 30) {
            float k = 1.0F - (t - EXPLODE) / 30.0F;
            return k * k;
        }
        return 0.0F;
    }

    @Override
    public float flare(float t) {
        float a = smooth(t, CRASH, CRASH + 6) * (1.0F - smooth(t, CRASH + 40, CRASH + 90));
        float b = smooth(t, EXPLODE, EXPLODE + 6) * (1.0F - smooth(t, EXPLODE + 50, EXPLODE + 110));
        return Math.max(a, b);
    }

    @Override
    public int flareColor() {
        return 0xFFAA55;
    }

    @Override
    public float flareHeight() {
        return 30.0F;
    }

    @Override
    public int tint(float t) {
        int al = (int) (85.0F * smooth(t, 0, 90) * (1.0F - smooth(t, EXPLODE + 90, D)));
        return (al << 24) | 0x3A1406;
    }
}
