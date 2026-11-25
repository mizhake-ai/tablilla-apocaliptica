package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.SinkholePlan;
import com.tablilla.apocaliptica.SinkholeStrike;
import java.util.List;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/** La Sima: grietas en el suelo y losas reales que se hunden una tras otra (con su tierra y sus arboles) en una sima irregular. */
public class SinkholeFx implements FxEffect {
    private static final int START = SinkholePlan.START;
    private static final int D = SinkholeStrike.DURATION;

    private final SinkholePlan plan;
    private final float scale;
    private GroundMap ground;
    @SuppressWarnings("unchecked")
    private final List<Debris.Proto>[] slabs;

    public SinkholeFx(long seed, float scale) {
        this.scale = scale;
        this.plan = new SinkholePlan(seed, scale);
        this.slabs = new List[plan.count];
    }

    @Override
    public int duration() {
        return D;
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        if (t == 1) {
            float half = (float) Math.min(280.0, plan.radius * 1.5 + 20.0);
            ground = new GroundMap(level, x, y, z, half, Math.max(4.0F, half / 45.0F));
            FxUtil.snd(level, "meteor.rumble", x, y, z, 16.0F, 0.5F);
        }
        if (t == 60 || t == 200 || t == 340 || t == 480) FxUtil.snd(level, "plasma.rumble", x, y, z, 16.0F, 0.5F);
        // grietas que se abren: polvo y chispas de tierra
        if (t > 10 && t < START + 40 && t % 2 == 0) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = r.nextDouble() * plan.radius * 1.1;
            FxUtil.p(level, ParticleTypes.LARGE_SMOKE, x + Math.cos(a) * d, y + 1, z + Math.sin(a) * d, 0, 0.1, 0);
        }
        int minY = level.getMinBuildHeight() + 2;
        for (int i = 0; i < plan.count; i++) {
            int tk = plan.tick[i];
            if (t == tk - 12) {
                slabs[i] = Debris.sample(level, x + plan.px[i], z + plan.pz[i], 0.0, 7.0, 34, 0.5F, r);
            }
            if (t == tk) {
                double sx = x + plan.px[i];
                double sz = z + plan.pz[i];
                if (slabs[i] != null) Debris.fall(slabs[i], minY, 0, r);
                for (int k = 0; k < 6; k++) {
                    FxUtil.p(level, ParticleTypes.CLOUD, sx + r.nextGaussian() * 5, y + 1, sz + r.nextGaussian() * 5, 0, 0.15, 0);
                }
                for (int k = 0; k < 3; k++) {
                    FxUtil.p(level, ParticleTypes.LARGE_SMOKE, sx + r.nextGaussian() * 5, y + 1, sz + r.nextGaussian() * 5, 0, 0.25, 0);
                }
                if (i % 14 == 0) FxUtil.snd(level, "meteor.hit" + (1 + i % 3), sx, y, sz, 12.0F, 0.45F);
                slabs[i] = null;
            }
        }
    }

    /** Una grieta: camino zigzagueante de segmentos. */
    private void crack(VertexConsumer vc, Matrix4f m, long seed, int c, float prog, float w, float cr, float cg, float cb, float a, float lift) {
        if (ground == null) return;
        Random r = new Random(seed * 13L + c * 7717L);
        float R = (float) plan.radius;
        float ang = r.nextFloat() * TAU;
        float rad = R * (0.15F + r.nextFloat() * 0.9F);
        float px = cos(ang) * rad;
        float pz = sin(ang) * rad;
        float dir = r.nextFloat() * TAU;
        int pieces = 9;
        for (int i = 0; i < pieces; i++) {
            if (i > pieces * prog) break;
            dir += (r.nextFloat() - 0.5F) * 1.1F;
            float len = (9.0F + r.nextFloat() * 10.0F) * scale;
            float nx = px + cos(dir) * len;
            float nz = pz + sin(dir) * len;
            float fade = 1.0F - 0.5F * i / pieces;
            FxGeo.terrainLine(vc, m, ground, px, pz, nx, nz, w * scale, lift, cr, cg, cb, a * fade, a * fade);
            px = nx;
            pz = nz;
        }
    }

    @Override
    public void renderSolid(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float prog = smooth(t, 6, START);
        if (prog <= 0.0F) return;
        float fade = 1.0F - smooth(t, START + 300, START + 560);
        for (int c = 0; c < 26; c++) crack(vc, m, seed, c, prog, 1.8F, 0.04F, 0.025F, 0.015F, 0.75F * fade, 0.35F);
    }

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float prog = smooth(t, 6, START);
        if (prog <= 0.0F) return;
        float fade = 1.0F - smooth(t, START + 300, START + 560);
        float pulse = 0.5F + 0.5F * sin(t * 0.12F);
        for (int c = 0; c < 26; c++) crack(vc, m, seed, c, prog, 0.7F, 1.0F, 0.45F, 0.12F, 0.28F * pulse * fade, 0.5F);
    }

    @Override
    public float shake(float t) {
        float a = 0.5F * smooth(t, 10, START) + 1.6F * smooth(t, START, START + 80) * (1.0F - smooth(t, START + SinkholePlan.SPAN, D));
        return a;
    }
}
