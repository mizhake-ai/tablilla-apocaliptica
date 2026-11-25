package com.tablilla.apocaliptica.client;

import static com.tablilla.apocaliptica.client.FxGeo.TAU;
import static com.tablilla.apocaliptica.client.FxGeo.cos;
import static com.tablilla.apocaliptica.client.FxGeo.lerp;
import static com.tablilla.apocaliptica.client.FxGeo.sin;
import static com.tablilla.apocaliptica.client.FxGeo.smooth;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tablilla.apocaliptica.MothershipStrike;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * La Barca Celeste: una nave-asteroide (roca, motores y sculk) de un kilometro. Llega, perfora un pozo hasta la
 * bedrock, corta una zanja fundida con su rayo verde mientras avanza y se va en un parpadeo.
 */
public class MothershipFx implements FxEffect {
    private static final int ARRIVE = MothershipStrike.ARRIVE;
    private static final int CHARGE = MothershipStrike.CHARGE;
    private static final int FIRE = MothershipStrike.FIRE;
    private static final int SWEEP = MothershipStrike.SWEEP_START;
    private static final int FIRE_END = MothershipStrike.FIRE_END;
    private static final int DEPART = MothershipStrike.DEPART;
    private static final int D = MothershipStrike.DURATION;
    private static final float HOVER = 270.0F;
    private static final int GROUPS = 7;

    private final ShipModel ship = new ShipModel();
    private GroundMap ground;
    private List<Debris.Proto> protos;
    @SuppressWarnings("unchecked")
    private final List<Debris.Proto>[] trench = new List[GROUPS];
    private final boolean[] trenchDone = new boolean[GROUPS];

    @Override
    public int duration() {
        return D;
    }

    private static float ease(float k) {
        return 1.0F - (1.0F - k) * (1.0F - k);
    }

    /** Posicion X de la nave (local). */
    private static float sx(float t, float q) {
        float k = ease(smooth(t, 0, ARRIVE));
        float x = lerp(-700.0F, 0.0F, k);
        x += MothershipStrike.SWEEP_LEN * q * ease(smooth(t, SWEEP, FIRE_END));
        float dep = Math.max(0.0F, t - DEPART);
        return x + dep * dep * 9.0F;
    }

    private static float sy(float t) {
        float k = ease(smooth(t, 0, ARRIVE));
        return lerp(520.0F, HOVER, k);
    }

    private static float roll(float t) {
        return 0.03F * sin(t * 0.05F);
    }

    private static boolean visible(float t) {
        return t < DEPART + 10;
    }

    @Override
    public void tick(ClientLevel level, double x, double y, double z, int t, RandomSource r, float s, long seed) {
        float q = s;
        if (t == 1) {
            float half = Math.min(280.0F, 270.0F * s);
            ground = new GroundMap(level, x, y, z, half, Math.max(5.0F, half / 45.0F));
            protos = Debris.sample(level, x, z, 4.0 * s, 100.0 * s, 300, 0.5F, r);
            for (int k = 0; k < GROUPS; k++) {
                trench[k] = Debris.sample(level, x + k * 45.0 * s, z, 0.0, 18.0 * s, 24, 0.5F, r);
            }
            FxUtil.snd(level, "mothership.arrival", x, y, z, 16.0F, 1.0F);
        }
        if (t == 100 || t == 400) FxUtil.snd(level, "mothership.hum", x, y, z, 16.0F, 1.0F);
        if (t == 130) FxUtil.snd(level, "mothership.sweep1", x, y, z, 16.0F, 1.0F);
        if (t == 190) FxUtil.snd(level, "mothership.sweep2", x, y, z, 16.0F, 1.0F);
        if (t == CHARGE) FxUtil.snd(level, "mothership.charge", x, y, z, 16.0F, 1.0F);
        if (t == CHARGE + 10) FxUtil.snd(level, "mothership.rumble", x, y, z, 16.0F, 1.0F);
        if (t == FIRE) {
            FxUtil.snd(level, "mothership.boom", x, y, z, 16.0F, 1.0F);
            FxUtil.snd(level, "mothership.fire", x, y, z, 16.0F, 1.0F);
        }
        if (t == FIRE + 1 && protos != null) {
            Debris.burst(protos, x, y, z, 2.3, 2.6, 40, 1.0F, 2.2F, 40.0 * s, 14.0 * s, r);
            BlockState[] hot = {Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.OBSIDIAN.defaultBlockState(),
                    Blocks.BLACKSTONE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState()};
            Debris.burst(Debris.synthetic(hot, x, y, z, 10.0 * s, 70, r), x, y, z, 1.4, 3.8, 8, 1.0F, 2.4F, 20.0 * s, 14.0 * s, r);
        }
        if (t == FIRE + 4) FxUtil.snd(level, "mothership.beam1", x, y, z, 16.0F, 1.0F);
        if (t == SWEEP) FxUtil.snd(level, "mothership.beam2", x, y, z, 16.0F, 1.0F);
        if (t == FIRE_END) FxUtil.snd(level, "mothership.cutoff", x, y, z, 16.0F, 1.0F);
        if (t == DEPART) FxUtil.snd(level, "mothership.depart", x, y, z, 16.0F, 1.0F);

        double px = x + sx(t, q);
        double py = y + sy(t);

        if (t >= CHARGE && t < FIRE) {
            double cyp = py + (ShipModel.PORT_Y - 8.0F) * q;
            for (int i = 0; i < 6; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double b = (r.nextDouble() - 0.5) * Math.PI;
                double dx = Math.cos(a) * Math.cos(b);
                double dy = Math.sin(b);
                double dz = Math.sin(a) * Math.cos(b);
                FxUtil.p(level, ParticleTypes.END_ROD, px + dx * 30 * q, cyp + dy * 30 * q, z + dz * 30 * q, -dx * 0.8, -dy * 0.8, -dz * 0.8);
            }
        }

        // el rayo en el suelo
        if (t >= FIRE && t < FIRE_END) {
            double fx = x + sx(t, q);
            double fz = z;
            double big = 9.0 * q;
            for (int i = 0; i < 14; i++) {
                FxUtil.p(level, ParticleTypes.END_ROD, fx + r.nextGaussian() * big * 0.5, y + 2 + r.nextDouble() * 200.0, fz + r.nextGaussian() * big * 0.5, 0, -0.3, 0);
            }
            for (int i = 0; i < 12; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double d = r.nextDouble() * big * 2.5;
                FxUtil.p(level, ParticleTypes.FLAME, fx + Math.cos(a) * d, y + 0.2, fz + Math.sin(a) * d, 0, 0.3 + r.nextDouble() * 0.4, 0);
            }
            for (int i = 0; i < 8; i++) {
                FxUtil.p(level, ParticleTypes.LARGE_SMOKE, fx + r.nextGaussian() * 14 * q, y + 0.5, fz + r.nextGaussian() * 14 * q, 0, 0.35, 0);
            }
            for (int i = 0; i < 4; i++) {
                FxUtil.p(level, ParticleTypes.LAVA, fx + r.nextGaussian() * 8 * q, y + 1, fz + r.nextGaussian() * 8 * q, 0, 0, 0);
            }
            if (t % 3 == 0) {
                FxUtil.p(level, ParticleTypes.EXPLOSION_EMITTER, fx + r.nextGaussian() * 10 * q, y + 1, fz + r.nextGaussian() * 10 * q, 0, 0, 0);
            }
            // escombros a lo largo de la zanja
            double rel = fx - x;
            for (int k = 0; k < GROUPS; k++) {
                if (!trenchDone[k] && rel >= k * 45.0 * q && t >= SWEEP && trench[k] != null) {
                    trenchDone[k] = true;
                    Debris.burst(trench[k], x + k * 45.0 * q, y, z, 1.4, 2.2, 5, 0.9F, 1.8F, 16.0 * q, 14.0 * q, r);
                }
            }
        }
        if (t >= FIRE && t < FIRE + 40) {
            for (int i = 0; i < 40; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                double b = r.nextDouble() * Math.PI * 0.5;
                double sp = 1.5 + r.nextDouble() * 2.5;
                FxUtil.p(level, ParticleTypes.FLAME, x, y + 2, z, Math.cos(a) * Math.cos(b) * sp, Math.sin(b) * sp, Math.sin(a) * Math.cos(b) * sp);
            }
        }
        if (t > FIRE + 1 && t < FIRE + 56) Debris.ripple(level, x, z, (t - FIRE) * 5.0 * s, 5.0 * s, 18, r);
        int since = t - FIRE;
        if (since >= 1 && since <= 60) {
            double rr = since * 4.6 * q;
            int n = (int) Math.min(120, rr * 0.6 + 10);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                FxUtil.p(level, ParticleTypes.CLOUD, x + Math.cos(a) * rr, y + 0.5, z + Math.sin(a) * rr, Math.cos(a) * 0.5, 0.1, Math.sin(a) * 0.5);
                if (i % 4 == 0) FxUtil.p(level, ParticleTypes.FLAME, x + Math.cos(a) * rr, y + 0.5, z + Math.sin(a) * rr, Math.cos(a) * 0.3, 0.3, Math.sin(a) * 0.3);
            }
        }
    }

    // ------------------------------------------------------------------ nave solida

    @Override
    public boolean usesHull() {
        return true;
    }

    @Override
    public void renderHull(VertexConsumer vc, PoseStack.Pose pose, float t, long seed, float s) {
        if (!visible(t)) return;
        float ox = sx(t, s);
        float oy = sy(t);
        ShipModel.push(pose, ox, oy, 0, 0, roll(t));
        ship.hull(vc, pose, seed, s, 0.0F);
        ShipModel.pop(pose, ox, oy, 0, 0, roll(t));
    }

    @Override
    public void renderSolid(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        if (!visible(t)) return;
        float ox = sx(t, s);
        float shadow = 0.38F * smooth(t, 20, ARRIVE) * (1.0F - smooth(t, DEPART - 10, DEPART));
        if (shadow > 0.01F) FxGeo.ring(vc, m, ox, 0.35F, 0, 0.0F, 150.0F * s, 48, 0.0F, 0.0F, 0.0F, shadow, shadow * 0.5F);
    }

    // ------------------------------------------------------------------ luces

    @Override
    public void renderGlow(VertexConsumer vc, Matrix4f m, float t, long seed, float s) {
        float q = s;
        float ox = sx(t, q);
        float oy = sy(t);
        float footX = ox;
        float groundY = 0.0F;
        if (ground != null) groundY = ground.at(Math.max(-270.0F, Math.min(270.0F, footX)), 0.0F);

        if (visible(t)) {
            float engine = 0.45F + 0.25F * smooth(t, ARRIVE - 20, ARRIVE) + 0.3F * smooth(t, DEPART - 4, DEPART);
            ShipModel.push(m, ox, oy, 0, 0, roll(t));
            ship.lights(vc, m, t, q, 0.3F, 1.0F, 0.55F, engine, 0.0F);
            ShipModel.pop(m, ox, oy, 0, 0, roll(t));

            float portY = oy + (ShipModel.PORT_Y - 4.0F) * q;
            // carga del canon
            float cs = smooth(t, CHARGE, FIRE) * (1.0F - smooth(t, FIRE_END, FIRE_END + 20));
            float sr = 20.0F * q * cs + (t >= FIRE && t < FIRE_END ? 5.0F * q * sin(t * 0.5F) : 0.0F);
            if (sr > 0.5F) {
                FxGeo.sphere(vc, m, ox, portY - 6.0F * q, 0, sr * 0.5F, 8, 16, 1.0F, 1.0F, 1.0F, 0.9F);
                FxGeo.sphere(vc, m, ox, portY - 6.0F * q, 0, sr * 0.8F, 8, 16, 0.4F, 1.0F, 0.6F, 0.5F);
                FxGeo.sphere(vc, m, ox, portY - 6.0F * q, 0, sr * 1.15F, 8, 16, 0.25F, 0.9F, 0.45F, 0.2F);
            }

            // rayo verde (taladro y luego corte)
            if (t >= FIRE && t < FIRE_END + 25) {
                float drill = t < SWEEP ? 1.35F : 1.0F;
                float b = 9.0F * q * drill * smooth(t, FIRE, FIRE + 10) * (1.0F - smooth(t, FIRE_END, FIRE_END + 25)) * (1.0F + 0.06F * sin(t * 1.5F));
                if (b > 0.1F) {
                    FxGeo.glowBeam(vc, m, ox, groundY, 0, ox, portY, 0, b, 0.45F, 1.0F, 0.6F, 1.0F, 16);
                    FxGeo.glowBeam(vc, m, ox, groundY, 0, ox, portY, 0, b * 2.2F, 0.25F, 1.0F, 0.45F, 0.38F, 16);
                    float len = portY - groundY;
                    for (int i = 0; i < 8; i++) {
                        float yy = portY - ((t * 9.0F + i * 60.0F) % Math.max(60.0F, len));
                        FxGeo.ring(vc, m, ox, yy, 0, b * 1.1F, b * 2.6F, 24, 0.6F, 1.0F, 0.75F, 0.6F, 0.0F);
                    }
                    // brillo del impacto del rayo
                    FxGeo.sphere(vc, m, ox, groundY + 2.0F, 0, b * 2.0F, 6, 12, 1.0F, 0.95F, 0.7F, 0.6F);
                    FxGeo.sphere(vc, m, ox, groundY + 2.0F, 0, b * 3.4F, 6, 12, 1.0F, 0.55F, 0.2F, 0.28F);
                }
            }
        }

        // destello del parpadeo: la nave se va disparada hacia adelante
        if (t >= DEPART && t < DEPART + 24) {
            float dt = t - DEPART;
            float fade = 1.0F - dt / 24.0F;
            float x0 = ox;
            FxGeo.tube(vc, m, x0 - 600.0F, oy, 0, x0 + 40.0F, oy, 0, 3.0F * q, 40.0F * q, 12, 0.6F, 1.0F, 0.8F, 0.0F, 0.55F * fade);
            FxGeo.tube(vc, m, x0 - 400.0F, oy, 0, x0 + 40.0F, oy, 0, 1.0F * q, 18.0F * q, 10, 1.0F, 1.0F, 1.0F, 0.0F, 0.8F * fade);
        }

        if (ground == null) return;

        // brillo del pozo
        float well = smooth(t, FIRE, FIRE + 60) * (1.0F - smooth(t, FIRE_END + 80, D));
        if (well > 0.01F) {
            FxGeo.polyCircle(vc, m, ground, 0, 0, 9.0F * q, 24, 2.5F * q, 0.6F, 1.0F, 0.55F, 0.15F, 0.8F * well, 1.0F);
            FxGeo.glowBeam(vc, m, 0, ground.at(0, 0), 0, 0, ground.at(0, 0) + 50.0F * well, 0, 3.0F * q, 1.0F, 0.6F, 0.2F, 0.5F * well, 10);
        }

        // la zanja fundida
        if (t > SWEEP) {
            float cool = 1.0F - smooth(t, FIRE_END + 60, D);
            float xb = Math.min(footX, (float) (MothershipStrike.SWEEP_LEN * q));
            int segs = (int) Math.max(1, xb / 9.0F);
            for (int i = 0; i < segs; i++) {
                float xa = i * 9.0F;
                float xe = Math.min(xb, xa + 9.0F);
                float hot = Math.max(0.25F, 1.0F - (xb - xa) / (140.0F * q));
                float w = 10.0F * q;
                FxGeo.terrainLine(vc, m, ground, xa, 0, xe, 0, w * 1.7F, 0.5F, 1.0F, 0.4F, 0.08F, 0.28F * cool * hot, 0.28F * cool * hot);
                FxGeo.terrainLine(vc, m, ground, xa, 0, xe, 0, w * 0.7F, 0.6F, 1.0F, 0.75F, 0.3F, 0.8F * cool * hot, 0.8F * cool * hot);
            }
        }

        // ondas de choque y pared de fuego pegadas al relieve
        float since = t - FIRE;
        float maxR = 250.0F * q;
        if (since > 0.0F) {
            for (int k = 0; k < 4; k++) {
                float rr = (since - k * 14.0F) * 5.0F * q;
                if (rr > 1.0F && rr < maxR) {
                    float fade = 1.0F - rr / maxR;
                    float w = 3.0F + rr * 0.05F;
                    int sides = Math.max(48, Math.min(160, (int) (rr * 0.6F)));
                    FxGeo.terrainRing(vc, m, ground, 0, 0, rr - w, rr, sides, 0.9F + k, 1.0F, 0.75F, 0.4F, 0.0F, 0.75F * fade);
                    FxGeo.terrainRing(vc, m, ground, 0, 0, rr, rr + w, sides, 0.9F + k, 1.0F, 0.75F, 0.4F, 0.75F * fade, 0.0F);
                    FxGeo.terrainWall(vc, m, ground, 0, 0, rr, 20.0F + rr * 0.12F, sides, 0.6F, 1.0F, 0.6F, 0.3F, 0.55F * fade, 0.0F);
                }
            }
        }
        float fk = smooth(t, FIRE, FIRE + 90);
        float fa = 1.0F - smooth(t, FIRE + 60, FIRE + 210);
        if (fk > 0.0F && fa > 0.01F) {
            FxGeo.sphere(vc, m, 0, 0, 0, 38.0F * q * fk, 8, 20, 1.0F, 0.95F, 0.8F, 0.5F * fa);
            FxGeo.sphere(vc, m, 0, 0, 0, 60.0F * q * fk, 8, 24, 1.0F, 0.7F, 0.3F, 0.3F * fa);
            FxGeo.sphere(vc, m, 0, 0, 0, 80.0F * q * fk, 8, 28, 1.0F, 0.4F, 0.1F, 0.16F * fa);
        }
    }

    @Override
    public float shake(float t) {
        float a = 0.45F * smooth(t, 30, ARRIVE);
        a += 0.9F * smooth(t, CHARGE, FIRE);
        if (t >= FIRE) a += 4.0F * (float) Math.exp(-(t - FIRE) / 50.0F) + 1.6F * (1.0F - smooth(t, FIRE_END, FIRE_END + 60));
        if (t >= DEPART && t < DEPART + 40) a += 2.5F * (1.0F - (t - DEPART) / 40.0F);
        return a * (1.0F - smooth(t, DEPART + 80, D));
    }

    @Override
    public float flash(float t) {
        if (t >= FIRE && t < FIRE + 20) {
            float k = 1.0F - (t - FIRE) / 20.0F;
            return k * k;
        }
        if (t >= DEPART + 8 && t < DEPART + 20) {
            float k = 1.0F - (t - DEPART - 8) / 12.0F;
            return 0.9F * k * k;
        }
        return 0.0F;
    }

    @Override
    public float flare(float t) {
        return smooth(t, FIRE, FIRE + 8) * (1.0F - smooth(t, FIRE_END - 20, FIRE_END));
    }

    @Override
    public int flareColor() {
        return 0x66FF99;
    }

    @Override
    public float flareHeight() {
        return 100.0F;
    }

    @Override
    public int tint(float t) {
        if (t < FIRE) {
            int al = (int) (90.0F * smooth(t, 10, ARRIVE));
            return (al << 24) | 0x050810;
        }
        int hot = (int) (70.0F * (1.0F - smooth(t, FIRE + 100, FIRE + 260)));
        int dark = (int) (90.0F * (1.0F - smooth(t, FIRE, FIRE + 60)) * (1.0F - smooth(t, DEPART - 20, DEPART + 80)));
        if (hot >= dark) return (hot << 24) | 0xC03A10;
        return (dark << 24) | 0x050810;
    }
}
