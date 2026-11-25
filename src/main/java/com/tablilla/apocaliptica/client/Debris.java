package com.tablilla.apocaliptica.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Bloques y arboles voladores con su textura real. Es solo visual (cliente): se copian bloques reales del
 * terreno antes de que el servidor los destruya y se lanzan por el aire girando.
 */
public final class Debris {
    private static final int MAX = 900;
    private static final List<Piece> PIECES = new ArrayList<>();
    private static final List<Island> ISLANDS = new ArrayList<>();
    private static boolean broken = false;

    private Debris() {}

    /** Un bloque copiado del terreno (aun sin lanzar). */
    public static final class Proto {
        public final BlockState st;
        public final double x;
        public final double y;
        public final double z;
        public final double floor;

        Proto(BlockState st, double x, double y, double z, double floor) {
            this.st = st;
            this.x = x;
            this.y = y;
            this.z = z;
            this.floor = floor;
        }
    }

    private static final class Piece {
        BlockState st;
        double x;
        double y;
        double z;
        double vx;
        double vy;
        double vz;
        float rx;
        float ry;
        float rz;
        float vrx;
        float vry;
        float vrz;
        float size;
        int delay;
        int age;
        int life;
        int mode; // 0 = balistico, 1 = espiral hacia el agujero, 3 = recto
        double floorY;
        double hx;
        double hy;
        double hz;
    }

    private static final class Island {
        final List<int[]> offs = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        double x;
        double y;
        double z;
        double vy;
        float yaw;
        float yawV;
        int delay;
        int age;
        int life;
    }

    public static void clear() {
        PIECES.clear();
        ISLANDS.clear();
    }

    public static boolean any() {
        return !PIECES.isEmpty() || !ISLANDS.isEmpty();
    }

    // ------------------------------------------------------------------ copiar bloques del terreno

    /** Copia bloques reales (suelo y, con probabilidad treeChance, partes de arboles) entre rMin y rMax del centro. */
    public static List<Proto> sample(ClientLevel level, double cx, double cz, double rMin, double rMax,
                                     int count, float treeChance, RandomSource r) {
        List<Proto> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(rMin * rMin + r.nextDouble() * (rMax * rMax - rMin * rMin));
            int wx = (int) Math.floor(cx + Math.cos(a) * d);
            int wz = (int) Math.floor(cz + Math.sin(a) * d);
            if (!level.hasChunk(wx >> 4, wz >> 4)) continue;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz);
            int y = ground - 1 - r.nextInt(2);
            if (r.nextFloat() < treeChance) {
                int topAll = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;
                if (topAll > ground) y = ground + r.nextInt(topAll - ground + 1);
            }
            BlockPos pos = new BlockPos(wx, y, wz);
            BlockState st = level.getBlockState(pos);
            if (st.isAir() || !st.getFluidState().isEmpty() || st.getRenderShape() != RenderShape.MODEL) continue;
            out.add(new Proto(st, wx + 0.5, y + 0.5, wz + 0.5, ground));
        }
        return out;
    }

    /** Bloques inventados (magma, obsidiana...) agrupados alrededor de un punto. */
    public static List<Proto> synthetic(BlockState[] states, double cx, double cy, double cz, double rad,
                                        int count, RandomSource r) {
        List<Proto> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            double d = Math.sqrt(r.nextDouble()) * rad;
            out.add(new Proto(states[r.nextInt(states.length)], cx + Math.cos(a) * d, cy + 0.5 + r.nextDouble() * 2.0,
                    cz + Math.sin(a) * d, cy));
        }
        return out;
    }

    // ------------------------------------------------------------------ lanzar

    private static Piece base(Proto p, float sizeMin, float sizeMax, RandomSource r) {
        Piece q = new Piece();
        q.st = p.st;
        q.x = p.x;
        q.y = p.y;
        q.z = p.z;
        q.size = sizeMin + r.nextFloat() * (sizeMax - sizeMin);
        q.rx = r.nextFloat() * 360.0F;
        q.ry = r.nextFloat() * 360.0F;
        q.rz = r.nextFloat() * 360.0F;
        q.vrx = (r.nextFloat() - 0.5F) * 14.0F;
        q.vry = (r.nextFloat() - 0.5F) * 14.0F;
        q.vrz = (r.nextFloat() - 0.5F) * 14.0F;
        q.floorY = p.floor;
        q.life = 400;
        return q;
    }

    /** Todos salen disparados hacia afuera y hacia arriba (los mas cercanos al centro, primero y mas fuerte). */
    public static void burst(List<Proto> list, double cx, double cy, double cz, double power, double up,
                             int maxDelay, float sizeMin, float sizeMax, double craterR, double craterDepth,
                             RandomSource r) {
        double reach = Math.max(8.0, craterR * 1.6);
        for (Proto p : list) {
            if (PIECES.size() >= MAX) return;
            Piece q = base(p, sizeMin, sizeMax, r);
            double dx = p.x - cx;
            double dz = p.z - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            double nx = dist < 0.01 ? Math.cos(r.nextDouble() * 6.283) : dx / dist;
            double nz = dist < 0.01 ? Math.sin(r.nextDouble() * 6.283) : dz / dist;
            double near = Math.max(0.0, 1.0 - dist / reach);
            double speed = power * (0.35 + 0.9 * near + 0.4 * r.nextDouble());
            q.vx = nx * speed;
            q.vz = nz * speed;
            q.vy = up * (0.35 + 0.9 * near + 0.5 * r.nextDouble());
            q.delay = (int) (Math.min(1.0, dist / reach) * maxDelay) + r.nextInt(4);
            double dn = Math.min(1.0, dist / Math.max(1.0, craterR));
            q.floorY = Math.min(p.floor, cy - craterDepth * Math.sqrt(Math.max(0.0, 1.0 - dn * dn))) - 1.0;
            PIECES.add(q);
        }
    }


    /** Ola de terreno: bloques del anillo que pasa la onda de choque saltan un momento y vuelven a su sitio. */
    public static void ripple(ClientLevel level, double cx, double cz, double radius, double thick, int count, RandomSource r) {
        if (radius < 2.0) return;
        List<Proto> list = sample(level, cx, cz, Math.max(0.0, radius - thick), radius + thick, count, 0.2F, r);
        for (Proto p : list) {
            if (PIECES.size() >= MAX) return;
            Piece q = base(p, 0.95F, 1.15F, r);
            q.vx = 0.0;
            q.vz = 0.0;
            q.vy = 0.30 + r.nextDouble() * 0.40;
            q.delay = r.nextInt(3);
            q.floorY = p.y - 0.6;
            q.life = 50;
            q.vrx = (r.nextFloat() - 0.5F) * 4.0F;
            q.vry = (r.nextFloat() - 0.5F) * 4.0F;
            q.vrz = (r.nextFloat() - 0.5F) * 4.0F;
            PIECES.add(q);
        }
    }


    /** Losas que se hunden: el bloque cae rigido hacia abajo hasta floorY (todos con la misma aceleracion, como una sola losa). */
    public static void fall(List<Proto> list, double floorY, int delay, RandomSource r) {
        for (Proto p : list) {
            if (PIECES.size() >= MAX) return;
            Piece q = base(p, 1.0F, 1.0F, r);
            q.vx = 0.0;
            q.vz = 0.0;
            q.vy = -0.12;
            q.delay = delay;
            q.floorY = floorY;
            q.life = 400;
            q.vrx = 0.0F;
            q.vry = 0.0F;
            q.vrz = 0.0F;
            q.rx = 0.0F;
            q.ry = 0.0F;
            q.rz = 0.0F;
            PIECES.add(q);
        }
    }

    /** Los bloques giran en espiral y suben hacia un punto (el agujero negro). */
    public static void spiral(List<Proto> list, double hx, double hy, double hz, int maxDelay, int life,
                              float sizeMin, float sizeMax, RandomSource r) {
        double far = 1.0;
        for (Proto p : list) {
            far = Math.max(far, Math.sqrt((p.x - hx) * (p.x - hx) + (p.z - hz) * (p.z - hz)));
        }
        for (Proto p : list) {
            if (PIECES.size() >= MAX) return;
            Piece q = base(p, sizeMin, sizeMax, r);
            double d = Math.sqrt((p.x - hx) * (p.x - hx) + (p.z - hz) * (p.z - hz));
            q.mode = 1;
            q.hx = hx;
            q.hy = hy;
            q.hz = hz;
            q.vy = 0.05;
            q.delay = (int) (d / far * maxDelay) + r.nextInt(6);
            q.life = life;
            PIECES.add(q);
        }
    }

    /** Un bloque que vuela en linea recta (cuerpo de un meteoro). */
    public static void linear(BlockState st, double sx, double sy, double sz, double vx, double vy, double vz,
                              float size, double floorY, int life, RandomSource r) {
        if (PIECES.size() >= MAX) return;
        Piece q = new Piece();
        q.st = st;
        q.x = sx;
        q.y = sy;
        q.z = sz;
        q.vx = vx;
        q.vy = vy;
        q.vz = vz;
        q.size = size;
        q.mode = 3;
        q.floorY = floorY;
        q.life = life;
        q.rx = r.nextFloat() * 360.0F;
        q.ry = r.nextFloat() * 360.0F;
        q.rz = r.nextFloat() * 360.0F;
        q.vrx = 4.0F + r.nextFloat() * 6.0F;
        q.vry = 3.0F + r.nextFloat() * 6.0F;
        q.vrz = 2.0F + r.nextFloat() * 5.0F;
        PIECES.add(q);
    }

    /** Islas flotantes de tierra con cesped y oro que suben al cielo. */
    public static void islands(ClientLevel level, double cx, double cy, double cz, int count, double minD, double maxD,
                               int maxDelay, RandomSource r) {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        for (int n = 0; n < count; n++) {
            Island is = new Island();
            double a = Math.PI * 2.0 * (n + r.nextDouble() * 0.6) / count;
            double d = minD + r.nextDouble() * (maxD - minD);
            is.x = cx + Math.cos(a) * d;
            is.z = cz + Math.sin(a) * d;
            int wx = (int) Math.floor(is.x);
            int wz = (int) Math.floor(is.z);
            double gy = level.hasChunk(wx >> 4, wz >> 4)
                    ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) : cy;
            is.y = gy + 1.0;
            is.vy = 0.10 + r.nextDouble() * 0.08;
            is.yaw = r.nextFloat() * 360.0F;
            is.yawV = (r.nextFloat() - 0.5F) * 1.6F;
            is.delay = r.nextInt(Math.max(1, maxDelay));
            is.life = 260;
            int rad = 2 + r.nextInt(3);
            for (int dx = -rad; dx <= rad; dx++) {
                for (int dz = -rad; dz <= rad; dz++) {
                    double dd = Math.sqrt(dx * dx + dz * dz);
                    if (dd > rad + 0.3) continue;
                    is.offs.add(new int[] {dx, 0, dz});
                    is.states.add(grass);
                    is.offs.add(new int[] {dx, -1, dz});
                    is.states.add(r.nextFloat() < 0.10F ? gold : dirt);
                    if (dd <= rad * 0.75) {
                        is.offs.add(new int[] {dx, -2, dz});
                        is.states.add(r.nextFloat() < 0.12F ? gold : dirt);
                    }
                    if (dd <= rad * 0.45) {
                        is.offs.add(new int[] {dx, -3, dz});
                        is.states.add(stone);
                    }
                    if (dd <= 0.5) {
                        is.offs.add(new int[] {dx, -4, dz});
                        is.states.add(r.nextFloat() < 0.5F ? gold : stone);
                    }
                }
            }
            ISLANDS.add(is);
        }
    }

    // ------------------------------------------------------------------ fisica

    private static void puff(ClientLevel level, BlockState st, double x, double y, double z) {
        for (int i = 0; i < 2; i++) {
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, st), x, y, z, 0.0, 0.1, 0.0);
        }
    }

    public static void tick(ClientLevel level) {
        Iterator<Piece> it = PIECES.iterator();
        while (it.hasNext()) {
            Piece p = it.next();
            p.age++;
            if (p.age <= p.delay) continue;
            if (p.age - p.delay > p.life) {
                it.remove();
                continue;
            }
            double ox = p.x;
            double oz = p.z;
            if (p.mode == 0) {
                p.vy -= 0.05;
                p.vx *= 0.995;
                p.vz *= 0.995;
                p.x += p.vx;
                p.y += p.vy;
                p.z += p.vz;
                if (p.vy < 0.0 && p.y < p.floorY) {
                    puff(level, p.st, p.x, p.floorY + 0.5, p.z);
                    it.remove();
                    continue;
                }
            } else if (p.mode == 1) {
                double dx = p.x - p.hx;
                double dz = p.z - p.hz;
                double rad = Math.sqrt(dx * dx + dz * dz);
                double ang = Math.atan2(dz, dx) + 0.03 + 1.7 / (rad + 10.0);
                double nr = Math.max(0.0, rad - (0.22 + 0.012 * rad));
                p.x = p.hx + Math.cos(ang) * nr;
                p.z = p.hz + Math.sin(ang) * nr;
                p.vy = Math.min(p.vy + 0.004, 0.55);
                p.y += (p.hy - p.y) * 0.018 + p.vy;
                p.vx = p.x - ox;
                p.vz = p.z - oz;
                if (nr < 3.0) {
                    it.remove();
                    continue;
                }
            } else {
                p.x += p.vx;
                p.y += p.vy;
                p.z += p.vz;
                if (p.y < p.floorY) {
                    puff(level, p.st, p.x, p.floorY + 0.5, p.z);
                    it.remove();
                    continue;
                }
            }
            if (p.mode == 1) p.vy = Math.min(p.vy, 0.55);
            p.rx += p.vrx;
            p.ry += p.vry;
            p.rz += p.vrz;
            if (p.mode == 1) {
                // en espiral, la velocidad guardada para dibujar es la real del ultimo paso
                p.vx = p.x - ox;
                p.vz = p.z - oz;
            }
        }

        Iterator<Island> ii = ISLANDS.iterator();
        while (ii.hasNext()) {
            Island is = ii.next();
            is.age++;
            if (is.age <= is.delay) continue;
            if (is.age - is.delay > is.life) {
                ii.remove();
                continue;
            }
            is.vy = Math.min(is.vy + 0.0035, 0.9);
            is.y += is.vy;
            is.yaw += is.yawV;
        }
    }

    // ------------------------------------------------------------------ dibujo

    public static void render(PoseStack ps, Vec3 cam, float pt, MultiBufferSource.BufferSource buf) {
        if (broken || !any()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        try {
            for (Piece p : PIECES) {
                if (p.age <= p.delay) continue;
                double x = p.x + p.vx * pt;
                double y = p.y + (p.mode == 1 ? 0.0 : p.vy * pt);
                double z = p.z + p.vz * pt;
                ps.pushPose();
                ps.translate(x - cam.x, y - cam.y, z - cam.z);
                ps.mulPose(Axis.YP.rotationDegrees(p.ry + p.vry * pt));
                ps.mulPose(Axis.XP.rotationDegrees(p.rx + p.vrx * pt));
                ps.mulPose(Axis.ZP.rotationDegrees(p.rz + p.vrz * pt));
                ps.scale(p.size, p.size, p.size);
                ps.translate(-0.5, -0.5, -0.5);
                mc.getBlockRenderer().renderSingleBlock(p.st, ps, buf, light(mc, x, y, z), OverlayTexture.NO_OVERLAY);
                ps.popPose();
            }
            for (Island is : ISLANDS) {
                if (is.age <= is.delay) continue;
                double y = is.y + is.vy * pt;
                int light = light(mc, is.x, y + 4.0, is.z);
                for (int i = 0; i < is.offs.size(); i++) {
                    int[] o = is.offs.get(i);
                    ps.pushPose();
                    ps.translate(is.x - cam.x, y - cam.y, is.z - cam.z);
                    ps.mulPose(Axis.YP.rotationDegrees(is.yaw + is.yawV * pt));
                    ps.translate(o[0] - 0.5, o[1] - 0.5, o[2] - 0.5);
                    mc.getBlockRenderer().renderSingleBlock(is.states.get(i), ps, buf, light, OverlayTexture.NO_OVERLAY);
                    ps.popPose();
                }
            }
            buf.endBatch();
        } catch (Throwable e) {
            broken = true;
            PIECES.clear();
            ISLANDS.clear();
        }
    }

    private static int light(Minecraft mc, double x, double y, double z) {
        int l = LevelRenderer.getLightColor(mc.level, BlockPos.containing(x, y, z));
        int sky = Math.max((l >> 20) & 15, 9);
        int blk = Math.max((l >> 4) & 15, 5);
        return LightTexture.pack(blk, sky);
    }
}
